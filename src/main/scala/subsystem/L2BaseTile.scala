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
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.HasLogicalTreeNode
import freechips.rocketchip.diplomaticobjectmodel.logicaltree._
import freechips.rocketchip.prci._
import freechips.rocketchip.tilelink.TLBusWrapper
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tile.HasTileParameters

abstract class L2BaseTile (n: Int)(val crossing: ClockCrossingType, q: Parameters) extends LazyModule()(q) with CrossesToOnlyOneClockDomain{
  
  def module: L2BaseTileModuleImp[L2BaseTile]
  val L2num = n
  val device: SimpleDevice = new SimpleDevice("l2macro", Seq("riscv,l2macro")) {

  }
  val IntenParam = new IntenParamC()(p)

  def skipMMIOY(x: TLClientParameters) = {
    val dcacheMMIO =
      (x.requestFifo) &&
      x.sourceId.start % 2 == 1 && // 1 => dcache issues acquires from another master
      x.nodePath.last.name == "dcache.node"
    if (dcacheMMIO) None else Some(x)
  }
  def MMIOY(x: TLClientParameters) = {
    val dcacheMMIO =
      (!x.requestFifo) &&
      x.sourceId.start % 2 == 1 && // 1 => dcache issues acquires from another master
      x.nodePath.last.name == "dcache.node"
    if (dcacheMMIO) None else Some(x)
  }

  val l1l2xbar         = 0 to (IntenParam.NUM_L2BANKS) - 1 map {x => LazyModule(new TLXbar)}
  val PrefetcherL3Xbar = LazyModule(new TLXbar)
  val l2l3xbar         = 0 to (IntenParam.NUM_L2BANKS) - 1 map {x => LazyModule(new TLXbar)}
  val tlSlaveXbar      = LazyModule(new TLXbar)
  val tlSlaveXbar1     = LazyModule(new TLXbar)
  val mmioXbar         = LazyModule(new TLXbar)
  val IntInXbarDebug   = 0 to (IntenParam.NUM_PHY_CORES*IntenParam.NUM_PUS*IntenParam.NUM_CTXT) - 1 map { x => LazyModule(new IntXbar)}
  val IntInXbar        = 0 to (IntenParam.NUM_PHY_CORES*IntenParam.NUM_PUS*IntenParam.NUM_CTXT) - 1 map { x => LazyModule(new IntXbar)}
  val tileHaltXbarNode = 0 to (IntenParam.NUM_PHY_CORES) - 1 map { x => IntXbar(q)}
  val tileWFIXbarNode  = 0 to (IntenParam.NUM_PHY_CORES) - 1 map { x => IntXbar(q)}
  val tileCeaseXbarNode= 0 to (IntenParam.NUM_PHY_CORES) - 1 map { x => IntXbar(q)}
  val ibus             = LazyModule(new IntXbar)
  val l2_inner_buffers = 0 to (IntenParam.NUM_L2BANKS) - 1 map {x => IntenParam.bufInnerExterior()}
  val filtermmio       = LazyModule(new TLFilter(cfilter = MMIOY)(p))
  val BeToCtxt_buffer  = LazyModule(new TLBuffer)

  val async_SlaveNode1      = LazyModule(new TLAsyncCrossing()(p))

  // Manually-instantiated crossing for l2l3xbar -> the outer (RocketSubsystem-
  // level) l2l3xbar, rather than the automatic `this.crossOut(...)(crossing)`
  // helper used by crossfiltermmio/crossSlaveNode below: for this call site,
  // that helper's auto-generated sink would live inside L2Macro's own module
  // hierarchy and inherit L2Macro's ambient clock instead of the uncore's,
  // collapsing the crossing to a same-clock connection with no real domain
  // boundary. See L2Macro.scala for where in_clock/out_clock get wired.
  val l2l3RatCrossings = 0 to (IntenParam.NUM_L2BANKS) - 1 map {x => LazyModule(new TLAsyncCrossing()(p))}

  protected def makeMasterBoundaryBuffers(implicit p: Parameters) = TLBuffer(BufferParams.none)
  def crossl2l3xbar(n:Int): TLOutwardNode = {
    l2l3RatCrossings(n).node := l2l3xbar(n).node
    l2l3RatCrossings(n).node
  }
  def crossfiltermmio(): TLOutwardNode = {
    val tlMasterXing = this.crossOut(crossing match {
      case RationalCrossing(_) => this { makeMasterBoundaryBuffers } :=* filtermmio.node
      case _ => filtermmio.node
    })
    tlMasterXing(crossing)
  }
  def crossSlaveNode(): TLInwardNode = {
    val tlSlaveXing = this.crossIn(crossing match {
      case RationalCrossing(_) => this { makeMasterBoundaryBuffers } :=* tlSlaveXbar.node
      case _ => tlSlaveXbar.node
    })
    tlSlaveXing(crossing)
  }
  def crossIbus(): IntOutwardNode = {
    val IntXing = this.crossOut(crossing match {
      case _ => ibus.intnode
    })
    IntXing(crossing)
  }
  def crossInterrupts(n:Int): IntInwardNode = {
    val IntXing = this.crossIn(crossing match {
      case _ => IntInXbar(n).intnode
    })
    IntXing(crossing)
  }
  def crossInterruptsDebug(n:Int): IntInwardNode = {
    val IntXing = this.crossIn(crossing match {
      case _ => IntInXbarDebug(n).intnode
    })
    IntXing(crossing)
  }
  lazy val logicalTreeNode = new L2BaseTileLogicalTreeNode()
  

}

abstract class L2BaseTileModuleImp[+L <: L2BaseTile](val outer: L) extends LazyModuleImp(outer) {
  val IntenParam = new IntenParamC()(p)
  val tile_constants =  0 to (IntenParam.NUM_PHY_CORES) - 1 map { x => IO(new TileInputConstants()(p))}
}
/** Some other non-tilelink but still standard inputs */
trait HasExternallyDrivenTileConstants1 extends Bundle with HasIntenParameters{
  val hartid = UInt(INPUT, NUM_CTXT * NUM_PUS * NUM_PHY_CORES * NUM_L2MACRO)
  val reset_vector = UInt(INPUT, NUM_PHYS_ADDR_BITS)
}

class TileInputConstants(implicit val p: Parameters) extends Bundle with HasExternallyDrivenTileConstants1