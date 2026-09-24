// See LICENSE.SiFive for license details.

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
import CoherenceManagerWrapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._

case object HartPrefixKey extends Field[Boolean](false)

// TODO: how specific are these to RocketTiles?
case class TileMasterPortParams(
  buffers: Int = 0,
  cork: Option[Boolean] = None,
  where: TLBusWrapperLocation = SBUS)

case class TileSlavePortParams(
  buffers: Int = 0,
  blockerCtrlAddr: Option[BigInt] = None,
  where: TLBusWrapperLocation = CBUS)

case class RocketCrossingParams(
  crossingType: ClockCrossingType = SynchronousCrossing(),
  master: TileMasterPortParams = TileMasterPortParams(),
  slave: TileSlavePortParams = TileSlavePortParams())

case object RocketTilesKey extends Field[Seq[RocketTileParams]](Nil)
case object RocketCrossingKey extends Field[Seq[RocketCrossingParams]](List(RocketCrossingParams()))

trait HasRocketTiles extends HasTiles
    with L1L2CacheWiring { this: L2BaseTile =>
  val module: HasRocketTilesModuleImp

  protected val rocketTileParams = p(RocketTilesKey)
  private val crossings = perTileOrGlobalSetting(p(RocketCrossingKey), rocketTileParams.size)

  // Make a tile and wire its nodes into the system,
  // according to the specified type of clock crossing.
  // Note that we also inject new nodes into the tile itself,
  // also based on the crossing type.

  filtermmio.node := TLBuffer(BufferParams.default) := mmioXbar.node

  val l2s = LazyModule(new InclusiveCache(
  cache.CacheParameters(
    level = 2,
    ways = IntenParam.L2_WAYS,
    sets = IntenParam.L2_SETS_ALL_BANKS/IntenParam.NUM_L2BANKS,
    blockBytes = p(SystemBusKey).blockBytes,
    beatBytes = p(SystemBusKey).beatBytes,
    hintsSkipProbe = false),
  cache.InclusiveCacheMicroParameters(
    writeBytes = IntenParam.WRITE_BYTES,
    portFactor = IntenParam.PORT_FACTOR_L2,
    memCycles = IntenParam.DRAM_CYCLES,
    innerBuf = IntenParam.bufInnerInterior,
    outerBuf = IntenParam.bufOuterInterior),
  Some(cache.InclusiveCacheControlParameters(
    address = cache.InclusiveCacheParameters.L2ControlAddress + L2num*0x10000, //expanding address
    beatBytes = p(SystemBusKey).beatBytes))))

  for (i <- 0 to (IntenParam.NUM_L2BANKS)-1){
    l2_inner_buffers(i).node    := l1l2xbar(i).node
    l2s.node             := l2_inner_buffers(i).node //12 for 650 cycle
    l2l3xbar(i%IntenParam.NUM_L2BANKS).node := l2s.node
  }

  // InclusiveCache has no interrupt-source node, so there's no real source for
  // this tile's ibus -- but ibus's output is still unconditionally consumed by
  // RocketSubsystem's own top-level ibus (`ibus.fromAsync := L2MacroTile(i).crossIbus`),
  // which requires exactly one output and thus at least one possible input.
  // Tie it off with NullIntSource, the same idiom used elsewhere in this file
  // for optional interrupt sources (e.g. CLINT/PLIC's `.getOrElse { NullIntSource() }`).
  ibus.intnode := NullIntSource()
  l2s.ctlnode.get := tlSlaveXbar.node
  //l2 loop ends here
  val rocketTiles = rocketTileParams.zipWithIndex.zip(crossings).slice(0,rocketTileParams.zip(crossings).length / (NUM_CTXT * NUM_PUS)).map { case ((tp, i),crossing) =>
    val rocket = LazyModule(new RocketTile(tp, crossing, PriorityMuxHartIdFromSeq(rocketTileParams), logicalTreeNode))

    connectL1L2Cache(rocket, crossing, i)

    for(row <- 0 until NUM_PUS*NUM_CTXT) { 
      tileHaltXbarNode(i)  := rocket.haltNode(row)
      tileWFIXbarNode(i)   := rocket.wfiNode(row)
      tileCeaseXbarNode(i) := rocket.ceaseNode(row)
      rocket.intInwardNode(row)   := IntInXbarDebug(row + ((NUM_PUS*NUM_CTXT)*i)).intnode
      //crossIntOut(row).intnode := rocket.crossIntOut(row)
      rocket.crossIntIn(row)      := IntInXbar(row + ((NUM_PUS*NUM_CTXT)*i)).intnode
    }
    rocket
  }

  rocketTiles.map {
    r =>
      def treeNode: RocketTileLogicalTreeNode = new RocketTileLogicalTreeNode(r.rocketLogicalTree.getOMInterruptTargets)
      LogicalModuleTree.add(logicalTreeNode, r.rocketLogicalTree)
  }
}

trait HasRocketTilesModuleImp extends HasTilesModuleImp
     {
  val outer: HasRocketTiles
}

trait HasL2macro extends CanHavePeripheryPLIC
    with CanHavePeripheryCLINT
    with HasPeripheryDebug 
    { this: BaseSubsystem =>
  val module: HasL2macroModuleImp

  protected val rocketTileParams = p(RocketTilesKey)

  val sbus_innerXbar   = LazyModule(new TLXbar)
  sbus.inwardNode      := sbus_innerXbar.node

  val L2MacroTile = 0 to (IntenParam.NUM_L2MACRO) - 1 map {x => LazyModule(new L2Macro(x)(p))}

  for (i <- 0 to IntenParam.NUM_L2MACRO-1){
    sbus_innerXbar.node := L2MacroTile(i).crossfiltermmio
    ibus.fromAsync      := L2MacroTile(i).crossIbus 

    sbus.toVariableWidthSlaveNode(Some("l2_ctrl_B"+i.toString), buffer = BufferParams(1)) { L2MacroTile(i).crossSlaveNode }
    sbus.toVariableWidthSlaveNode(Some("memperf"+i.toString), buffer = BufferParams(1)) { L2MacroTile(i).async_SlaveNode1.node }
    // sbus.toVariableWidthSlaveNode(Some("BeToCtxt"), buffer = BufferParams(1)) { L2MacroTile(i).BeToCtxt_xbar.node}

    for (j <- 0 to IntenParam.NUM_L2BANKS-1){
      l2l3xbar(j).node := L2MacroTile(i).crossl2l3xbar(j)
    }

  val meipNode = p(PLICKey) match {
    case Some(_) => None
    case None    => Some(IntNexusNode(
      sourceFn = { _ => IntSourcePortParameters(Seq(IntSourceParameters(1))) },
      sinkFn   = { _ => IntSinkPortParameters(Seq(IntSinkParameters())) },
      outputRequiresInput = false,
      inputRequiresOutput = false))
  }
  for(tile <- 0 until IntenParam.NUM_PHY_CORES) {
    for(row <- 0 until NUM_PUS*NUM_CTXT) { 

      L2MacroTile(i).crossInterruptsDebug(row + ((NUM_PUS*NUM_CTXT)*tile)) :=
        debugOpt
          .map { IntSyncAsyncCrossingSink(3) := _.intnode }
          .getOrElse { NullIntSource() }


      L2MacroTile(i).crossInterrupts(row + ((NUM_PUS*NUM_CTXT)*tile)) :=
        clintOpt.map { _.intnode }
          .getOrElse { NullIntSource(sources = CLINTConsts.ints) }


      L2MacroTile(i).crossInterrupts(row + ((NUM_PUS*NUM_CTXT)*tile)) :=
        plicOpt .map { _.intnode }
          .getOrElse { meipNode.get }


      if (rocketTileParams.head.core.useSupervisor || rocketTileParams.head.core.useVM) {
        L2MacroTile(i).crossInterrupts(row + ((NUM_PUS*NUM_CTXT)*tile)) :=
          plicOpt .map { _.intnode }
            .getOrElse { NullIntSource() }
      }
    }
  }
  for(tile <- 0 until IntenParam.NUM_PHY_CORES) {
    val tileHaltSinkNode = IntSinkNode(IntSinkPortSimple())
    val tileWFISinkNode  = IntSinkNode(IntSinkPortSimple())
    val tileCeaseSinkNode = IntSinkNode(IntSinkPortSimple())

    tileHaltSinkNode  := L2MacroTile(i).tileHaltXbarNode(tile)
    tileWFISinkNode   := L2MacroTile(i).tileWFIXbarNode(tile)
    tileCeaseSinkNode := L2MacroTile(i).tileCeaseXbarNode(tile)
  }

  LogicalModuleTree.add(logicalTreeNode, L2MacroTile(i).logicalTreeNode)
  }
}

trait HasL2macroModuleImp
    extends HasPeripheryDebugModuleImp 
    {
  val outer: HasL2macro
}

// Field for specifying MaskROM addition to subsystem
case object PeripheryMaskROMKey extends Field[Seq[MaskROMParams]](Nil)

class RocketSubsystem(implicit p: Parameters) extends BaseSubsystem
    with HasL2macro
    with HasTLCacheCork
    {

  //add Mask ROM devices
  val maskROMs = p(PeripheryMaskROMKey).map { MaskROM.attach(_, cbus) }

  override lazy val module = new RocketSubsystemModuleImp(this)
}

class RocketSubsystemModuleImp[+L <: RocketSubsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
    with HasResetVectorWire
    with HasL2macroModuleImp {

  def hartIdList: Seq[Int] =p(RocketTilesKey).map(_.hartId)

  // Fed from a ClockRouting instance one level up (see TestHarness.scala and
  // subsystem/ClockRouting.scala). This module's own ambient clock/reset
  // (BaseSubsystemModuleImp's, i.e. dut.clock/dut.reset from one level up)
  // is the 500MHz/"rest of chip" domain -- these ports are only for the
  // other domains that need to override specific children below.
  val clockRoutingIO = IO(new Bundle {
    val clk_3_5GHz  = Input(Clock())
    val clk_1_75GHZ = Input(Clock())
    val rst_3_5GHz  = Input(Bool()) // reset synchronized to clk_3_5GHz, see ClockRouting.scala
    val rst_1_75GHZ = Input(Bool()) // reset synchronized to clk_1_75GHZ, see ClockRouting.scala
  })

  for (j <- 0 until outer.IntenParam.NUM_L2MACRO) {
    outer.L2MacroTile(j).module.io.tileClock := clockRoutingIO.clk_3_5GHz
    outer.L2MacroTile(j).module.io.tileReset := clockRoutingIO.rst_3_5GHz
    outer.L2MacroTile(j).module.clock        := clockRoutingIO.clk_1_75GHZ
    outer.L2MacroTile(j).module.reset        := clockRoutingIO.rst_1_75GHZ
    outer.L2MacroTile(j).module.io.sbus_clock := clock
    outer.L2MacroTile(j).module.io.sbus_reset := reset

    for (i <- 0 until outer.IntenParam.NUM_PHY_CORES) {
    outer.L2MacroTile(j).module.tile_constants(i).hartid := UInt(((outer.IntenParam.NUM_PHY_CORES*j)+i))
    outer.L2MacroTile(j).module.tile_constants(i).reset_vector := global_reset_vector
    }
  }

  // outer.corks (subsystem/TLCacheCork.scala) need no explicit clock/reset
  // wiring here -- they aren't overridden to any other domain, so they
  // simply inherit this module's own ambient clock/reset (clk_500MHz),
  // the same domain l2l3xbar/corkMemSysXbar/Coherencebus are already on.

  def resetVectorBits: Int = {
    // Consider using the minimum over all widths, rather than enforcing homogeneity
    val vectors = outer.L2MacroTile(0).module.tile_constants.map(_.reset_vector)
    //require(vectors.tail.forall(_.getWidth == vectors.head.getWidth))
    vectors.head.getWidth
  }
}
