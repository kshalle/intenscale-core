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
import scala.collection.mutable.ListBuffer
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.{HasPeripheryDebug,HasPeripheryDebugModuleImp}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree._
import freechips.rocketchip.diplomaticobjectmodel.model._
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.util._
import chisel3.{withReset,RequireAsyncReset,AsyncReset,withClockAndReset}
import CoherenceManagerWrapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.regmapper.{RegisterReadIO, RegField, HasRegMap}
import chisel3.experimental.{Analog}

// No L3 cache is instantiated here yet -- each of the NUM_L3BANKS slots below
// is just a TLCacheCork, terminating L2's coherent (AcquireB-capable) traffic
// into plain Get/Put before it reaches memory. This termination is required
// (nothing else in this design does it -- TLToAXI4 downstream does not accept
// TL-C traffic), it is not a placeholder. l2l3xbar (L2's output, already
// crossed into this subsystem's own ambient clock via L2BaseTile.scala's
// l2l3RatCrossings) and corkMemSysXbar/Coherencebus below are all on that
// same ambient clock (clk_500MHz), so no clock-domain crossing is needed
// here at all.
trait HasTLCacheCork extends HasIntenParameters{ this: BaseSubsystem =>
  val numOutChannel = if (IntenParam.NUM_L3BANKS < 4) {IntenParam.NUM_L3BANKS} else{4}

  val corks = 0 until IntenParam.NUM_L3BANKS map { _ => LazyModule(new TLCacheCork) }

  val corkMemSysXbar = 0 to (numOutChannel) - 1 map {x => LazyModule(new TLXbar)}

  val corkMemSysNode = (0 until IntenParam.NUM_L3BANKS).map { _ => TLIdentityNode() }

  for (i <- 0 to IntenParam.NUM_L3BANKS -1){
    corks(i).node := TLBuffer() := l2l3xbar(i%IntenParam.NUM_L2BANKS).node
  }
  for(channelout <- 0 until math.max(IntenParam.NUM_L3BANKS/4,1)) {
    for(channel <- 0 until numOutChannel) {
      corkMemSysXbar(channel).node :=* TLBuffer() :=* TLFilter(TLFilter.mSelectIntersect(AddressSet((channel + (numOutChannel*channelout)) * p(CacheBlockBytes), ~BigInt((IntenParam.NUM_L3BANKS-1)*p(CacheBlockBytes))))) :=* corks(channel + (numOutChannel*channelout)).node
    }
  }
  if (!IntenParam.BACKEND_ENA){
    val ECCDramCombXbar = LazyModule(new TLXbar)

    for(channel <- 0 until numOutChannel) {
    ECCDramCombXbar.node :=* TLFilter(TLFilter.mSelectIntersect(AddressSet(channel * p(CacheBlockBytes), ~BigInt((numOutChannel-1)*p(CacheBlockBytes))))) :=* corkMemSysNode(channel) :=* corkMemSysXbar(channel).node
    }
    Coherencebus.inwardNode  :=* TLBuffer() :=* TLWidthWidget(64) :=* TLBuffer() :=* TLWidthWidget(8) :=* ECCDramCombXbar.node //120 for 650 dram model
  }
  else{
    for(channel <- 0 until numOutChannel) {
      Coherencebus.inwardNode :=* TLFilter(TLFilter.mSelectIntersect(AddressSet(channel * p(CacheBlockBytes), ~BigInt((numOutChannel-1)*p(CacheBlockBytes))))) :=* corkMemSysXbar(channel).node
    }
  }

}
