/*
 * SPDX-FileCopyrightText: 2016-2026 Intensivate, Inc.
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
import scala.collection.mutable.ListBuffer
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.{HasPeripheryDebug, HasPeripheryDebugModuleImp}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._

trait L1L2CacheWiring extends HasTiles
  with HasIntenParameters{ this: L2BaseTile =>

  // ICache-side tile->uncore crossings, one per (tile, PU). Exposed here so
  // L2MacroModuleImp can wire their clock/reset.
  //
  // Uses TLAsyncCrossing, not TLRationalCrossing: the rational crossing (see
  // util/RationalCrossing.scala) requires a fixed, known phase relationship
  // between the two clocks, with no synchronizer on the feedback path.
  val icacheRatCrossings = ListBuffer[TLAsyncCrossing]()

  // D-cache tile->uncore crossings: one independent TLAsyncCrossing per L2
  // bank, owned by the tile (see BaseTile.scala's masterNodeRatCross). Keeping
  // each bank on its own crossing avoids funneling all bank traffic through a
  // single serialized merge/fan-out point, which would cause head-of-line
  // blocking across banks under load.

  protected def connectL1L2Cache(rocket: BaseTile, crossing: RocketCrossingParams, coreNum: Int) {
    val mmioXbarBuffer   = LazyModule(new TLXbar)
    val dcacheIcacheXbar = 0 to IntenParam.NUM_L2BANKS - 1 map { x => LazyModule(new TLXbar)}
    // Dedicated per-bank fan-out point for this tile's dcache traffic only (see below).
    val dcacheFanout     = 0 to IntenParam.NUM_L2BANKS - 1 map { x => LazyModule(new TLXbar)}

    for(miniId <- 0 until NUM_L2BANKS) {
      val filtercache    = LazyModule(new TLFilter(cfilter = skipMMIOY)(p))
      // masterNodeRatCross is a point-to-point TLAsyncCrossing, so it can only
      // feed one consumer directly. dcacheFanout(miniId) is a small per-bank
      // TLXbar that fans this bank's traffic out to both dcacheIcacheXbar and
      // mmioXbarBuffer below.
      //
      // icacheXbar (bound further below) already has its own direct path to
      // mmioXbar, so mmioXbarBuffer reads from dcacheFanout rather than from
      // dcacheIcacheXbar, to avoid routing icache traffic into mmioXbar twice.
      //
      // mmioXbarBuffer is bound once per bank to merge every bank's
      // MMIO-eligible traffic into one point. Each L2 bank has its own
      // independent dcache client/MSHR pool, so binding mmioXbarBuffer through
      // dcacheFanout (one clean, single client per bank) keeps the merged
      // client count linear in NUM_L2BANKS, rather than squaring it.
      dcacheFanout(miniId).node     := TLBuffer(BufferParams.default) := rocket.masterNodeRatCross(miniId).node
      dcacheIcacheXbar(miniId).node := dcacheFanout(miniId).node
      mmioXbarBuffer.node           := dcacheFanout(miniId).node
      filtercache.node              := dcacheIcacheXbar(miniId).node
      l1l2xbar(NUM_L2BANKS*(coreNum/NUM_OF_CORES_PER_L2)+miniId).node := filtercache.node
      // PrefetcherL3Xbar is a single Xbar shared across all cores in this
      // L2BaseTile, while dcacheIcacheXbar is allocated fresh per call to
      // connectL1L2Cache (i.e. per core). Cores sharing an L2 group
      // (coreNum/NUM_OF_CORES_PER_L2) would each attach their own
      // dcacheIcacheXbar using the same miniId-only address filter, which
      // AddressDecoder rejects as an unroutable overlap once more than one
      // core exists. Only the group's first core makes this connection.
      if (coreNum % NUM_OF_CORES_PER_L2 == 0) {
        dcacheIcacheXbar(miniId).node := TLFilter(TLFilter.mSelectIntersect( AddressSet(miniId * p(CacheBlockBytes), ~BigInt((NUM_L2BANKS-1)*p(CacheBlockBytes))))) := PrefetcherL3Xbar.node     //L2 connection
      }
      //l2l3xbar(miniId).node := TLFilter(TLFilter.mSelectIntersect( AddressSet(miniId * p(CacheBlockBytes), ~BigInt((NUM_L2BANKS-1)*p(CacheBlockBytes))))) := PrefetcherL3Xbar.node           //L3 connection
    }
    PrefetcherL3Xbar.node := rocket.PrefetcherNode.node
    mmioXbar.node   := TLBuffer(BufferParams.default) := mmioXbarBuffer.node
    rocket.mmioSlaveNode.node := tlSlaveXbar1.node

    for(ips <- 0 until NUM_PUS) {
      val icacheXbar    = LazyModule(new TLXbar)
      val icacheRatCross = LazyModule(new TLAsyncCrossing()(p))
      icacheRatCrossings += icacheRatCross
      icacheRatCross.node := rocket.crossMasterCachablePort(ips, crossing)
      icacheXbar.node  := icacheRatCross.node
      mmioXbar.node    := icacheXbar.node

      for(miniId <- 0 until NUM_L2BANKS) {
        dcacheIcacheXbar(miniId).node := TLFilter(TLFilter.mSelectIntersect( AddressSet(miniId * p(CacheBlockBytes), ~BigInt((NUM_L2BANKS-1)*p(CacheBlockBytes))))) := icacheXbar.node
      }
    }
  }
}