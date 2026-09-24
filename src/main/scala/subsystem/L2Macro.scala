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

import Chisel._
import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.{HasPeripheryDebug,HasPeripheryDebugModuleImp}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree._
import freechips.rocketchip.diplomaticobjectmodel.model._
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.{TracedInstruction}
import CoherenceManagerWrapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.regmapper.{RegisterReadIO, RegField, HasRegMap}

class L2Macro(n: Int)(q: Parameters) extends L2BaseTile(n)(AsynchronousCrossing(),q)
  with HasRocketTiles { 

  val tiles = rocketTiles

  val traceNode = BundleBroadcast[Vec[TracedInstruction]](Some("trace"))
  traceNode := tiles.head.traceNode
  
  tlSlaveXbar1.node     := TLFIFOFixer() :=  async_SlaveNode1.node
  
  val BeToCtxtRegnode : Option[TLRegisterNode] = if (IntenParam.HasBSYS){ Option(TLRegisterNode(
		  address = Seq(AddressSet(0x0, 0xf)),
		  device = device,
		  beatBytes = 1))} else None

  if (IntenParam.HasBSYS){
    BeToCtxtRegnode.get := BeToCtxt_buffer.node
  }

  override lazy val module = new L2MacroModuleImp(this)
}

class L2MacroModuleImp[+L <: L2Macro](_outer: L) extends L2BaseTileModuleImp(_outer)
    //with HasResetVectorWire
    with HasRocketTilesModuleImp {
  
  val io = IO(new Bundle {
		val tileClock = Input(Clock())
    val tileReset = Input(Bool())

    val l3In_a_v = Input(UInt(4.W))
    val l3In_d_v = Input(UInt(4.W))
    val l3In_a_r = Input(UInt(4.W))
    val l3In_d_r = Input(UInt(4.W))
    val l3In_a_addr   = Input(UInt(35.W))
    val l3In_a_src    = Input(UInt(6.W))
    val l3In_d_src    = Input(UInt(6.W))
    

    val l3MemSys_v = Input(UInt(2.W))
    val l3MemSys_r = Input(UInt(2.W))
    val l3MemSys_a_addr = Input(UInt(35.W))
    val l3MemSys_a_src  = Input(UInt(4.W))
    val l3MemSys_d_src  = Input(UInt(4.W))

    val eccDRAM_v  = Input(UInt(2.W))
    val eccDRAM_r  = Input(UInt(2.W))
    val eccDRAM_a_addr = Input(UInt(35.W))
    val eccDRAM_a_src  = Input(UInt(7.W))
    val eccDRAM_d_src  = Input(UInt(7.W))

    // async_SlaveNode1's source side is fed by sbus (RocketSubsystem.scala's
    // HasL2macro: sbus.toVariableWidthSlaveNode("memperf"+i, ...)), which is
    // on RocketSubsystemModuleImp's own ambient clock (clk_500MHz) -- not
    // this module's own ambient (clk_1_75GHZ). Same cross-domain pattern as
    // L3CtlNodeRatCross.
    val sbus_clock = Input(Clock())
    val sbus_reset = Input(Bool())
	})

  outer.tiles.foreach({ case tile =>
    tile.module.clock := io.tileClock
    tile.module.reset := io.tileReset
  })

  // ICache tile->uncore crossings (see L1L2CacheWiring.scala): "in" side is the
  // tile's own (fast) clock, "out" side is this module's own ambient clock
  // (L2Macro's, i.e. clk_1_75GHZ) that dcacheIcacheXbar/icacheXbar/mmioXbar live on.
  outer.icacheRatCrossings.foreach { rc =>
    rc.module.io.in_clock  := io.tileClock
    rc.module.io.in_reset  := io.tileReset
    rc.module.io.out_clock := clock
    rc.module.io.out_reset := reset
  }

  // D-cache tile->uncore crossings (see BaseTile.scala's masterNodeRatCross) are
  // owned per-tile and wired in RocketTile.scala's module impl, alongside
  // mmioSlaveNode/PrefetcherNode.

  outer.tiles.foreach({ case tile =>
    tile.module.io.l3In_a_v         := io.l3In_a_v
    tile.module.io.l3In_d_v         := io.l3In_d_v
    tile.module.io.l3In_a_r         := io.l3In_a_r
    tile.module.io.l3In_d_r         := io.l3In_d_r
    tile.module.io.l3In_a_addr      := io.l3In_a_addr
    tile.module.io.l3In_a_src       := io.l3In_a_src
    tile.module.io.l3In_d_src       := io.l3In_d_src


    tile.module.io.l3MemSys_v       := io.l3MemSys_v 
    tile.module.io.l3MemSys_r       := io.l3MemSys_r
    tile.module.io.l3MemSys_a_addr  := io.l3MemSys_a_addr
    tile.module.io.l3MemSys_a_src   := io.l3MemSys_a_src
    tile.module.io.l3MemSys_d_src   := io.l3MemSys_d_src    

    tile.module.io.eccDRAM_v        := io.eccDRAM_v
    tile.module.io.eccDRAM_r        := io.eccDRAM_r
    tile.module.io.eccDRAM_a_addr   := io.eccDRAM_a_addr
    tile.module.io.eccDRAM_a_src    := io.eccDRAM_a_src
    tile.module.io.eccDRAM_d_src    := io.eccDRAM_d_src

    // mmioSlaveNode/PrefetcherNode crossings (see RocketTile.scala) need this
    // module's own ambient clock/reset (clk_1_75GHZ), which is directly
    // available here as `clock`/`reset`.
    tile.module.io.l2_clock         := clock
    tile.module.io.l2_reset         := reset

  })

  _outer.async_SlaveNode1.module.io.out_clock := clock
  _outer.async_SlaveNode1.module.io.in_clock  := io.sbus_clock
  _outer.async_SlaveNode1.module.io.in_reset  := io.sbus_reset
  _outer.async_SlaveNode1.module.io.out_reset := reset

  // l2l3RatCrossings (see L2BaseTile.scala): "in" side (source) is this L2
  // bank's own cache core, on this module's own ambient clock (clk_1_75GHZ).
  // "out" side (sink) drains into RocketSubsystem's l2l3xbar/TLBuffer/
  // TLCacheCork (subsystem/TLCacheCork.scala), which run on
  // RocketSubsystemModuleImp's own ambient (clk_500MHz) -- already available
  // here as io.sbus_clock/io.sbus_reset, same domain async_SlaveNode1 above
  // crosses to.
  outer.l2l3RatCrossings.foreach { rc =>
    rc.module.io.in_clock  := clock
    rc.module.io.in_reset  := reset
    rc.module.io.out_clock := io.sbus_clock
    rc.module.io.out_reset := io.sbus_reset
  }

  val holdOnCtrl_regD1 = Reg(init=UInt(0, 3))
  for (i <- 0 until outer.tiles.size) {
    val wire = tile_inputs(i)
    wire.hartid       := tile_constants(i).hartid
    wire.reset_vector := tile_constants(i).reset_vector
  }

  if (outer.IntenParam.HasBSYS){
    outer.BeToCtxtRegnode.get.regmap(
		  0x00 -> Seq(RegField(4, holdOnCtrl_regD1))
  )}

}
