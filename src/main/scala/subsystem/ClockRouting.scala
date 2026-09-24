/*
 * SPDX-FileCopyrightText: 2018-2026 Intensivate, Inc.
 * SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0
 *
 * This file is part of the Intensivate CPU Core.
 *
 * Licensed under the Intensivate Non-Commercial Hardware Source
 * License v1.0. Commercial use requires a separate written license
 * from Intensivate, Inc.
 *
 * Full license: LICENSE.md
 * Patent notice: PATENTS.md
 */

package freechips.rocketchip.subsystem

import chisel3._
import chisel3.util.log2Ceil
import freechips.rocketchip.util.ResetCatchAndSync

/** Exact divide-by-2, 50% duty cycle: toggles on every rising edge of the
  * module's own (input) clock.
  */
class ClockDivider2 extends Module {
  val io = IO(new Bundle {
    val clockOut = Output(Clock())
  })

  val toggle = RegInit(false.B)
  toggle := !toggle
  io.clockOut := toggle.asClock
}

/** Exact divide-by-n (n >= 2) for arbitrary n, including odd values that
  * ClockDivider2 cannot produce by cascading. Counts input rising edges and
  * flips the output level after ceil(n/2) of them while high and floor(n/2)
  * while low, giving a period of exactly n input cycles. For even n this is
  * an exact 50% duty cycle (equivalent to cascading ClockDivider2); for odd
  * n the duty cycle is off by one input cycle, which is fine here since
  * every consumer of these clocks crosses to them via a fully asynchronous
  * TLAsyncCrossing that makes no assumption about duty cycle.
  */
class ClockDividerN(n: Int) extends Module {
  require(n >= 2, "ClockDividerN requires n >= 2")

  val io = IO(new Bundle {
    val clockOut = Output(Clock())
  })

  val highCycles = (n + 1) / 2
  val lowCycles  = n - highCycles
  val level = RegInit(true.B)
  val cnt   = RegInit(0.U(log2Ceil(n).W))
  val target = Mux(level, (highCycles - 1).U, (lowCycles - 1).U)
  when (cnt === target) {
    cnt   := 0.U
    level := !level
  } .otherwise {
    cnt := cnt + 1.U
  }
  io.clockOut := level.asClock
}

/** Dummy stand-in for a real clock control unit (CCU). The module's own
  * (input) clock is a 7GHz reference with no domain of its own;
  * clk_3_5GHz, clk_1_75GHZ and clk_500MHz are all divided down from it (by
  * 2, 4 and 14 respectively) via cascaded ClockDivider2/ClockDividerN
  * stages -- clk_500MHz's divider (div14) is itself cascaded from an
  * internal /7 stage (div7), so div7 stays even though no domain of its
  * own is exposed anymore (see subsystem/TLCacheCork.scala).
  *
  * Each of the three exposed domains gets its own reset, synchronized to
  * that domain's own clock via ResetCatchAndSync (async assert, sync
  * de-assert). A domain's reset must be synchronous to its own clock -- any
  * consumer bridging two domains (e.g. the ICache's TLRationalCrossing, see
  * L1L2CacheWiring.scala/L2Macro.scala) relies on its "in"-side reset
  * releasing at a point meaningful to that side's own clock edges.
  *
  * cleanReset latches the first assertion of the plain top-level reset on
  * the undivided (7GHz) clock and holds it for as long as that top-level
  * reset stays asserted. It feeds the async-assert side of the three
  * ResetCatchAndSync stages below, never the dividers themselves (wired to
  * a constant false.B instead, see their instantiation below): the dividers
  * are pure free-running clock generators with no state worth protecting,
  * and holding them in reset for cleanReset's full (possibly long) span
  * would freeze every divided clock for that long, starving the three
  * ResetCatchAndSync stages of any clock edge to synchronize against.
  *
  * Reset RELEASE order across the three domains is the reverse of clock
  * generation, i.e. slowest first: rest-of-chip (clk_500MHz), then L2
  * (clk_1_75GHZ), then the tile (clk_3_5GHz). This ordering is required
  * because the tile<->L2 and L2<->uncore TLAsyncCrossings each tie one
  * side's clock/reset to the slower domain of the pair; if a faster domain
  * released first, it could issue or accept TileLink traffic through a
  * crossing whose other side is still held in reset, feeding garbage into
  * already-live logic. Each stage below implements this by taking
  * "cleanReset OR the next-slower domain's own still-asserted reset" as its
  * ResetCatchAndSync input, so a domain's release is gated on both its own
  * local synchronization and the domain below it having already released.
  */
class ClockRouting extends Module {
  val io = IO(new Bundle {
    val clk_3_5GHz  = Output(Clock()) // tile / CPU logic
    val clk_1_75GHZ = Output(Clock()) // L2 cache
    val clk_500MHz  = Output(Clock()) // everything else
    val rst_3_5GHz  = Output(Bool())  // reset for clk_3_5GHz's domain
    val rst_1_75GHZ = Output(Bool())  // reset for clk_1_75GHZ's domain
    val rst_500MHz  = Output(Bool())  // reset for clk_500MHz's domain
  })

  val cleanReset = ResetCatchAndSync(clock, reset.asBool, name = "clockRouting_reset_sync")

  val div2  = Module(new ClockDivider2)  // clock/2  -> 3.5GHz
  val div4  = Module(new ClockDivider2)  // clock/4  -> 1.75GHz
  val div7  = Module(new ClockDividerN(7)) // clock/7  -> internal only, feeds div14
  val div14 = Module(new ClockDivider2)  // clock/14 -> 500MHz

  // Dividers are never reset (tied to false.B, not cleanReset) -- see class
  // doc above for why.
  div2.reset := false.B
  div4.clock := div2.io.clockOut
  div4.reset := false.B
  div7.reset := false.B
  div14.clock := div7.io.clockOut
  div14.reset := false.B

  io.clk_3_5GHz  := div2.io.clockOut
  io.clk_1_75GHZ := div4.io.clockOut
  io.clk_500MHz  := div14.io.clockOut

  // Slowest domain releases first: depends only on the common cleanReset.
  val rst_500MHz = ResetCatchAndSync(div14.io.clockOut, cleanReset, name = "rst_500MHz_sync")
  io.rst_500MHz := rst_500MHz

  // L2 must not release before uncore has, for the same reason (the
  // L2<->uncore TLAsyncCrossing that data takes between L2 and the rest of
  // the chip, see L2BaseTile.scala). rst_500MHz is async relative to
  // clk_1_75GHZ, but ResetCatchAndSync supports an async-assert input by
  // design -- only the release edge gets resynchronized here, to
  // clk_1_75GHZ.
  val rst_1_75GHZ = ResetCatchAndSync(div4.io.clockOut, cleanReset || rst_500MHz, name = "rst_1_75GHZ_sync")
  io.rst_1_75GHZ := rst_1_75GHZ

  // Tile must not release before L2 has, for the same reason (tile<->L2
  // TLAsyncCrossings: masterNodeRatCross/icacheRatCross/mmioSlaveNode/PrefetcherNode).
  io.rst_3_5GHz := ResetCatchAndSync(div2.io.clockOut, cleanReset || rst_1_75GHZ, name = "rst_3_5GHz_sync")
}
