// See LICENSE.SiFive for license details.

package freechips.rocketchip.subsystem

import Chisel._
import chisel3.dontTouch
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.debug.TLDebugModule
import freechips.rocketchip.devices.tilelink.{BasicBusBlocker, BasicBusBlockerParams, CLINT, CLINTConsts, TLPLIC, PLICKey}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tile.{BaseTile, LookupByHartId, LookupByHartIdImpl, TileParams, HasExternallyDrivenTileConstants}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._

import superThread._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._

trait HasTiles extends HasCoreMonitorBundles {
  implicit val p: Parameters
  val tiles: Seq[BaseTile]

  protected def tileParams: Seq[TileParams] = tiles.map(_.tileParams)
  def nTiles: Int = tileParams.size
  def hartIdList: Seq[Int] = tileParams.map(_.hartId)
  def localIntCounts: Seq[Int] = tileParams.map(_.core.nLocalInterrupts)

  // define some nodes that are useful for collecting or driving tile interrupts
  val meipNode = p(PLICKey) match {
    case Some(_) => None
    case None    => Some(IntNexusNode(
      sourceFn = { _ => IntSourcePortParameters(Seq(IntSourceParameters(1))) },
      sinkFn   = { _ => IntSinkPortParameters(Seq(IntSinkParameters())) },
      outputRequiresInput = false,
      inputRequiresOutput = false))
  }


  protected def perTileOrGlobalSetting[T](in: Seq[T], n: Int): Seq[T] = in.size match {
    case 1 => List.fill(n)(in.head)
    case x if x == n => in
    case _ => throw new Exception("must provide exactly 1 or #tiles of this key")
  }
}

trait HasTilesModuleImp extends LazyModuleImp with HasIntenParameters {
  val outer: HasTiles

  def resetVectorBits: Int = {
    // Consider using the minimum over all widths, rather than enforcing homogeneity
    val vectors = outer.tiles.map(_.module.constants.reset_vector)
    require(vectors.tail.forall(_.getWidth == vectors.head.getWidth))
    vectors.head.getWidth
  }

  val tile_inputs = outer.tiles.map(_.module.constants)


  // Count the total number of executed instructions per cycle.
  // val numInstr_reg  = Reg(UInt(width=outer.tiles(0).module.traceOut.numInstr.getWidth))
  // val numInstrE     = Wire(Vec(NUM_PHY_CORES, UInt(width=outer.tiles(0).module.traceOut.numInstr.getWidth)))
  // numInstrE(0)     := outer.tiles(0).module.traceOut.numInstr
  // for(ph <- 1 until NUM_PHY_CORES) {
  //   numInstrE(ph) := numInstrE(ph-1) + outer.tiles(ph).module.traceOut.numInstr
  // }
  // numInstr_reg := numInstrE(NUM_PHY_CORES-1)

  // val st2        = outer.tiles(0).module.traceOut.statusDU0.getWidth
  // val parts      = Wire(Vec(NUM_PHY_CORES, UInt(width=152)))
  // val parts2     = Wire(Vec(NUM_PHY_CORES, UInt(width=(2*(st2-8)))))
  // for(ph <- 0 until NUM_PHY_CORES) {
  //   val trcOut  = outer.tiles(ph).module.traceOut.asUInt
  //   parts(NUM_PHY_CORES - ph - 1)   := Cat(outer.tiles(ph).module.traceOut.valid, outer.tiles(ph).module.traceOut.iaddr, outer.tiles(ph).module.traceOut.insn, outer.tiles(ph).module.traceOut.valid2, outer.tiles(ph).module.traceOut.iaddr2, outer.tiles(ph).module.traceOut.insn2)
  //   parts2(NUM_PHY_CORES - ph - 1)  := Cat(outer.tiles(ph).module.traceOut.statusDU0(st2-5, 4), outer.tiles(ph).module.traceOut.statusDU1(st2-5, 4))
  // }
  // val traceIn = Reg(next=Cat(Reg(next=Cat(parts2.asUInt, parts.asUInt)), numInstr_reg))

  // // Connect back the combined trace bus.
  // outer.tiles.map(_.module).zip(tile_inputs).foreach { case(tile, wire) =>
  //   tile.traceIn := traceIn
  // }


  val meip = if(outer.meipNode.isDefined) Some(IO(Vec(outer.meipNode.get.out.size, Bool()).asInput)) else None
  meip.foreach { m =>
    m.zipWithIndex.foreach{ case (pin, i) =>
      (outer.meipNode.get.out(i)._1)(0) := pin
    }
  }
}