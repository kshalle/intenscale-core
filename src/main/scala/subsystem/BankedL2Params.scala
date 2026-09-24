// See LICENSE.SiFive for license details.

package freechips.rocketchip.subsystem

import Chisel._
import chisel3.util.isPow2
import freechips.rocketchip.config._
import freechips.rocketchip.devices.tilelink.BuiltInDevices
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import CoherenceManagerWrapper._

import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._

/** Global cache coherence granularity, which applies to all caches, for now. */
case object CacheBlockBytes extends Field[Int](64)

/** L2 Broadcast Hub configuration */
case object BroadcastKey extends Field(BroadcastParams())

case class BroadcastParams(
  nTrackers:  Int     = 4,
  bufferless: Boolean = false)

/** L2 memory subsystem configuration */
case object BankedL2Key extends Field(BankedL2Params())

case class BankedL2Params(
  nBanks: Int = 1,
  coherenceManager: CoherenceManagerInstantiationFn = broadcastManager
) {
  require (isPow2(nBanks) || nBanks == 0)
}

case class CoherenceManagerWrapperParams(
    blockBytes: Int,
    beatBytes: Int,
    nBanks: Int,
    name: String)
  (val coherenceManager: CoherenceManagerInstantiationFn)
  extends HasTLBusParams 
  with TLBusWrapperInstantiationLike
{
  val dtsFrequency = None
  def instantiate(context: HasTileLinkLocations, loc: Location[TLBusWrapper])(implicit p: Parameters): CoherenceManagerWrapper = {
    val cmWrapper = LazyModule(new CoherenceManagerWrapper(this, context))
    cmWrapper.suggestName(loc.name + "_wrapper")
    cmWrapper.halt.foreach { context.anyLocationMap += loc.halt(_) }
    context.tlBusWrapperLocationMap += (loc -> cmWrapper)
    cmWrapper
  }
}

class CoherenceManagerWrapper(params: CoherenceManagerWrapperParams, context: HasTileLinkLocations)(implicit p: Parameters) extends TLBusWrapper(params, params.name) {
  val (halt) = params.coherenceManager(context)

  val IntenParam = new IntenParamC()(p)
  val numChannel = if (IntenParam.NUM_L3BANKS < 4) {IntenParam.NUM_L3BANKS} else{4}
  val l3eccxbar = LazyModule(new TLXbar())

  def busView: TLEdge = l3eccxbar.node.edges.in.head

  val inwardNode = l3eccxbar.node

  val builtInDevices = BuiltInDevices.none
  val prefixNode = None

  private def banked(node: TLOutwardNode): TLOutwardNode =
    if (IntenParam.NUM_L3BANKS == 0) node else { TLTempNode() :=* BankBinder(IntenParam.NUM_L3BANKS, params.blockBytes) :*= node }
  val outwardNode = l3eccxbar.node
}

object CoherenceManagerWrapper {
  type CoherenceManagerInstantiationFn = HasTileLinkLocations => (Option[IntOutwardNode])

  val broadcastManager: CoherenceManagerInstantiationFn = { context =>
    implicit val p = context.p


    def sbus  = context.tlBusWrapperLocationMap(SBUS)
    def cbus  = context.tlBusWrapperLocationMap(CBUS)
    def ibus  = context.ibus
    val IntenParam = new IntenParamC()(p)

    // val l2s = 0 to (IntenParam.NUM_L2CACHES)-1 map { x => 
    //   LazyModule(new ComposableCache(
    //   cache.CacheParameters(
    //     level = 2,
    //     ways = IntenParam.L2_WAYS,
    //     sets = IntenParam.L2_SETS_ALL_BANKS/IntenParam.NUM_L2BANKS,
    //     blockBytes = sbus.blockBytes,
    //     beatBytes = sbus.beatBytes),
    //   cache.ComposableCacheMicroParameters(
    //     writeBytes = IntenParam.WRITE_BYTES,
    //     portFactor = IntenParam.PORT_FACTOR_L2,
    //     memCycles = IntenParam.DRAM_CYCLES+IntenParam.LE_CYCLES,
    //     innerBuf = IntenParam.bufInnerInterior,
    //     outerBuf = IntenParam.bufOuterInterior),
    //   Some(cache.ComposableCacheControlParameters(
    //     address = cache.ComposableCacheParameters.L2ControlAddress,
    //     beatBytes = cbus.beatBytes))))
    // }

    // val l3 = 0 to (IntenParam.NUM_L3BANKS) - 1 map {x => LazyModule(new ComposableCache(
    // cache.CacheParameters(
    //   level = 3,
    //   cacheID = IntenParam.NUM_L2MACRO,
    //   ways = IntenParam.L3_WAYS,
    //   sets = IntenParam.L3_SETS_ALL_BANKS/IntenParam.NUM_L3BANKS,
    //   blockBytes = sbus.blockBytes,
    //   beatBytes  =   sbus.beatBytes),
    // cache.ComposableCacheMicroParameters(
    //   writeBytes = IntenParam.WRITE_BYTES,
    //   portFactor = IntenParam.PORT_FACTOR_L3,
    //   memCycles  = IntenParam.DRAM_CYCLES,
    //   innerBuf   = IntenParam.bufInnerInterior,
    //   outerBuf   = IntenParam.bufOuterInterior),
    // Some(cache.ComposableCacheControlParameters(
    //   address = cache.ComposableCacheParameters.L3ControlAddress + x*0x10000,
    //   beatBytes = cbus.beatBytes))))}

    // the inner and outer buffers are how the caches are connected to outside world
    // val l2_inner_buffers = 0 to (IntenParam.NUM_L2BANKS) - 1 map {x => IntenParam.bufInnerExterior()}
    
    // A cross bar for each mini-cache.
    // val l1l2xbar        = 0 to (IntenParam.NUM_L2BANKS) - 1 map {x => LazyModule(new TLXbar)}
    // val l2l3xbar        = 0 to (IntenParam.NUM_L2BANKS) - 1 map {x => LazyModule(new TLXbar)}


    // cork is put into the TL port that normally would go to higher level cache.  It is just a dead end to satisfy Diplomacy.
    //val cork         = LazyModule(new TLCacheCork)
    // val InNodes      = 0 to (IntenParam.NUM_L2BANKS) - 1 map { x => TLIdentityNode()} //had to add that because the return type of that class should be InwardNode

    // for (i <- 0 to (IntenParam.NUM_L2BANKS)-1){
    //   l2_inner_buffers(i).node                := InNodes(i)
    //   l2s(i/IntenParam.NUM_L2BANKS).node      := l2_inner_buffers(i).node
    //   l2l3xbar(i%IntenParam.NUM_L2BANKS).node := l2s(i/IntenParam.NUM_L2BANKS).node
    // }
  
    // for (i <- 0 to IntenParam.NUM_L3BANKS -1){
    //   l2_outer_buffer(i%IntenParam.NUM_L2BANKS).node := l2l3xbar(i%IntenParam.NUM_L2BANKS).node // connect "core" L2 to buffer
    //   l3_inner_buffer(i%IntenParam.NUM_L2BANKS).node := l2_outer_buffer(i%IntenParam.NUM_L2BANKS).node // connection from L2 to L3
    //   l3(i).node                                     := l3_inner_buffer(i%IntenParam.NUM_L2BANKS).node
    //   EccXbar.node := cork.node := TLFilter(TLFilter.mSelectIntersect( AddressSet(i * p(CacheBlockBytes), ~BigInt((IntenParam.NUM_L3BANKS-1)*p(CacheBlockBytes))))) := l3_outer_buffer(i%IntenParam.NUM_L2BANKS).node := l3(i).node
    // }

    // for(channel <- 0 until numOutChannel) {
    //     EccInXbar(channel).node := TLFilter(TLFilter.mSelectIntersect(AddressSet(channel * p(CacheBlockBytes), ~BigInt((numOutChannel-1)*p(CacheBlockBytes))))) := EccXbar.node
    // }

    //control node is used to set parameters inside composable cache instance -- connects to control bus, which in turn connects to sbus
    // We believe that there will be an error if these do not have a path to the sbus
    // Not clear how these are used, we guess that there may be code in the boot rom that sets configuration..?
    //l2s(i).intnode.foreach  { ibus.fromSync := _ }

    // connect the interrupts -- subsystem is a namespace, and ibus is the object -- this connects every interrupt node in cache to ibus
    // Note: fromSync is a synchronizer latch, to handle asynchrony
    // cache uses interrupts for ecc
    // for (i <- 0 to IntenParam.NUM_L3BANKS -1){
    //   l3(i).intnode.foreach  { ibus.fromSync := _ }
    // }

    // for (i <- 0 to IntenParam.NUM_L3BANKS -1){
    //   l3(i).ctlnode.foreach { c =>
    //   sbus.toVariableWidthSlave(Some("l3_ctrl"), buffer = BufferParams(1)) { c }
    // }
    // }
    (None)
  }

  val incoherentManager: CoherenceManagerInstantiationFn = { _ =>
    val node = TLNameNode("no_coherence_manager")
    ( None)
  }
}
