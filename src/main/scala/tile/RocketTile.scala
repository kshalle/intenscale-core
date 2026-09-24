// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package freechips.rocketchip.tile

import Chisel._
import chisel3.{VecInit}
import chisel3.util.{RRArbiter}
import chisel3.util.random.LFSR
import scala.collection.mutable.ListBuffer

import freechips.rocketchip.config._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{DCacheLogicalTreeNode, LogicalModuleTree, LogicalTreeNode, RocketLogicalTreeNode}
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{SubsystemResetSchemeKey, ResetSynchronous, RocketCrossingParams, HartPrefixKey}
import freechips.rocketchip.util._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem.CacheBlockBytes

import superThread._
import freechips.rocketchip.rocket.constants.StConfiguration._

case class RocketTileParams(
    core: RocketCoreParams = RocketCoreParams(),
    icache: Option[ICacheParams] = Some(ICacheParams()),
    dcache: Option[DCacheParams] = Some(DCacheParams()),
    btb: Option[BTBParams] = Some(BTBParams()),
    dataScratchpadBytes: Int = 0,
    name: Option[String] = Some("tile"),
    hartId: Int = 0,
    beuAddr: Option[BigInt] = None,
    blockerCtrlAddr: Option[BigInt] = None,
    boundaryBuffers: Boolean = false // if synthesized with hierarchical PnR, cut feed-throughs?
    ) extends TileParams {
  require(icache.isDefined)
  require(dcache.isDefined)
}

class RocketTile private(
      val rocketParams: RocketTileParams,
      crossing: ClockCrossingType,
      lookup: LookupByHartIdImpl,
      q: Parameters,
      logicalTreeNode: LogicalTreeNode)
    extends BaseTile(rocketParams, crossing, lookup, q)
    with SinksExternalInterrupts
    with SourcesExternalNotifications
    with HasLazyRoCC  // implies CanHaveSharedFPU with CanHavePTW with HasHellaCache
    with HasHellaCache
    with HasICacheFrontend
{
  // Private constructor ensures altered LazyModule.p is used implicitly
  def this(params: RocketTileParams, crossing: RocketCrossingParams, lookup: LookupByHartIdImpl, logicalTreeNode: LogicalTreeNode)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p, logicalTreeNode)

  val intOutwardNode = ListBuffer[IntOutwardNode]()
  for(row <- 0 until NUM_PUS*NUM_CTXT) {
    intOutwardNode += IntIdentityNode()
  }

  val slaveNode  = TLIdentityNode()
  val masterNode = visibilityNode

	val PrefetcherClientNode = TLClientNode(Seq(TLMasterPortParameters.v1(
		Seq(TLMasterParameters.v1(
			name = "PrefetcherClientNode",
      //visibility = Seq(AddressSet(x"7_8000_0000", x"1_0000_0000" - x"1")),
			sourceId = IdRange(0, 255))))))

  val device = new SimpleDevice("MemPerfMon", Seq("Intensivate, MemPerfMon"))
  val regNode = TLRegisterNode(
      address = Seq(AddressSet(0x8000000L + (tileParams.hartId*0x10000L), 0xfff)),
      device = device,
      beatBytes = 8)
  
  regNode := mmioSlaveNode.node
  PrefetcherNode.node := PrefetcherClientNode

  val rocketLogicalTree = new RocketLogicalTreeNode(this, p(XLen), pgLevels)

  val dtim_adapter = tileParams.dcache.flatMap { d => d.scratch.map { s =>
    val coreParams = {
      class C(implicit val p: Parameters) extends HasCoreParameters
      new C
    }
    LazyModule(new ScratchpadSlavePort(AddressSet.misaligned(s, d.dataScratchpadBytes), coreParams.coreDataBytes, tileParams.core.useAtomics && !tileParams.core.useAtomicsOnlyForIO))
  }}
  dtim_adapter.foreach(lm => connectTLSlave(lm.node, lm.node.portParams.head.beatBytes))

  val tile_master_blocker =
    tileParams.blockerCtrlAddr
      .map(BasicBusBlockerParams(_, xBytes, masterPortBeatBytes, deadlock = true))
      .map(bp => LazyModule(new BasicBusBlocker(bp)))

  tile_master_blocker.foreach(lm => connectTLSlave(lm.controlNode, xBytes))

  val Snooper = TLNexusNode(
    clientFn = { seq => seq(0).copy(clients = seq.flatMap(_.clients)) },
    managerFn = { seq => seq(0) }
  )

  // TODO: this doesn't block other masters, e.g. RoCCs
  // Note that, the ICache is connected to all tile-links, the ICache may need to access any of the MMIO, e.g., RAM, hence,
  // we have to consider connecting all the output tile-links from the core to both catchable and non-catchable.
  for(ips <- 0 until NUM_PUS) {
    masterNodecachable(ips) := tile_master_blocker.map { _.node :=* icacheNode(ips) } getOrElse { icacheNode(ips) }
  }
  val xbarPrefetchMergeDcacheIn  = LazyModule(new TLXbar)
  val xbarPrefetchMergeDcacheOut = LazyModule(new TLXbar)

  for(i <- 0 until NUM_L2BANKS) {
    xbarPrefetchMergeDcacheIn.node :=* TLWidthWidget(8) := tile_master_blocker.map { _.node :=* dcacheNode(i) } getOrElse { dcacheNode(i) }
    masterNode  := TLWidthWidget(64) := TLFilter(TLFilter.mSelectIntersect( AddressSet(i * p(CacheBlockBytes), ~BigInt((NUM_L2BANKS-1)*p(CacheBlockBytes))))) :*= xbarPrefetchMergeDcacheOut.node
  }

  // TLFIFOFixer must read xbarPrefetchMergeDcacheIn only after the loop above has
  // finished binding every bank into it. Instantiating it once per bank inside
  // that loop instead would give each instance the full, already-merged client
  // set rather than just its own bank's contribution, so every instance would
  // materialize a `flight`/SourceIdFIFOed register array sized to the entire
  // merged endSourceId -- NUM_L2BANKS redundant copies of it. Placing it here,
  // after the merge, keeps Snooper's merge-for-observation behavior intact
  // while giving a single TLFIFOFixer instance the correctly-sized view.
  xbarPrefetchMergeDcacheOut.node := Snooper := TLFIFOFixer() := xbarPrefetchMergeDcacheIn.node

  for(miniId <- 0 until NUM_L2BANKS) {
    masterNodeRatCross(miniId).node := masterNode
  }

  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  nDCachePorts += 1 /*core */ + (dtim_adapter.isDefined).toInt

  val dtimProperty = dtim_adapter.map(d => Map(
    "sifive,dtim" -> d.device.asProperty)).getOrElse(Nil)

  val itimProperty = frontend.icache(0).itimProperty.toSeq.flatMap(p => Map("sifive,itim" -> p))

  val cpuDevice = ListBuffer[Device]()
  for(row <- 0 until NUM_PUS*NUM_CTXT) {
    cpuDevice += new SimpleDevice("cpu", Seq("sifive,rocket0", "riscv")) {
        override def parent = Some(ResourceAnchors.cpus)
        override def describe(resources: ResourceBindings): Description = {
          val Description(name, mapping) = super.describe(resources)
          Description(name, mapping ++ cpuProperties ++ nextLevelCacheProperty
                      ++ tileProperties ++ dtimProperty ++ itimProperty)
        }
      }
  }
  for(row <- 0 until NUM_PUS*NUM_CTXT) {
    ResourceBinding {
      Resource(cpuDevice(row), "reg").bind(ResourceAddress(hartId*NUM_PUS*NUM_CTXT+row))
    }
  }

  override lazy val module = new RocketTileModuleImp(this)

  override def makeMasterBoundaryBuffers(implicit p: Parameters) = {
    if (!rocketParams.boundaryBuffers) super.makeMasterBoundaryBuffers
    else TLBuffer(BufferParams.none, BufferParams.flow, BufferParams.none, BufferParams.flow, BufferParams(1))
  }

  override def makeSlaveBoundaryBuffers(implicit p: Parameters) = {
    if (!rocketParams.boundaryBuffers) super.makeSlaveBoundaryBuffers
    else TLBuffer(BufferParams.flow, BufferParams.none, BufferParams.none, BufferParams.none, BufferParams.none)
  }

  val dCacheLogicalTreeNode = new DCacheLogicalTreeNode(dcache, dtim_adapter.map(_.device), rocketParams.dcache.get)
  LogicalModuleTree.add(rocketLogicalTree, iCacheLogicalTreeNode)
  LogicalModuleTree.add(rocketLogicalTree, dCacheLogicalTreeNode)
}

class RocketTileModuleImp(outer: RocketTile) extends BaseTileModuleImp(outer)
    with HasFpuOpt
    with HasLazyRoCCModule
    with HasICacheFrontendModule {

  val io = IO(new Bundle {
    val l3In_a_v        = Input(UInt(4.W))
    val l3In_d_v        = Input(UInt(4.W))
    val l3In_a_r        = Input(UInt(4.W))
    val l3In_d_r        = Input(UInt(4.W))
    val l3In_a_addr     = Input(UInt(35.W))
    val l3In_a_src      = Input(UInt(6.W))
    val l3In_d_src      = Input(UInt(6.W))

    val l2In_a_v        = Input(UInt(2.W))
    val l2In_d_v        = Input(UInt(2.W))
    val l2In_a_r        = Input(UInt(2.W))
    val l2In_d_r        = Input(UInt(2.W))
    val l2In_a_addr     = Input(UInt(35.W))
    val l2In_a_src      = Input(UInt(6.W))
    val l2In_d_src      = Input(UInt(6.W))

    val l3MemSys_v      = Input(UInt(2.W))
    val l3MemSys_r      = Input(UInt(2.W))
    val l3MemSys_a_addr = Input(UInt(35.W))
    val l3MemSys_a_src  = Input(UInt(4.W))
    val l3MemSys_d_src  = Input(UInt(4.W))

    val eccDRAM_v       = Input(UInt(2.W))
    val eccDRAM_r       = Input(UInt(2.W))
    val eccDRAM_a_addr  = Input(UInt(35.W))
    val eccDRAM_a_src   = Input(UInt(7.W))
    val eccDRAM_d_src   = Input(UInt(7.W))

    // mmioSlaveNode's source side is fed by tlSlaveXbar1 (L1L2CacheWiring.scala:
    // rocket.mmioSlaveNode.node := tlSlaveXbar1.node), and PrefetcherNode's sink
    // side feeds PrefetcherL3Xbar (PrefetcherL3Xbar.node := rocket.PrefetcherNode.node)
    // -- both live in L2Macro's own ambient domain (clk_1_75GHZ), not this tile's
    // own ambient (clk_3_5GHz).
    val l2_clock = Input(Clock())
    val l2_reset = Input(Bool())
	})
  //TODO Get boundary addresses from parameters
  val (tl0, edge0) = outer.PrefetcherClientNode.out(0)
  tl0.a.valid := 0.U
  tl0.d.ready := 1.U
  val prefetcher_0_sequenceInstanceCounter = RegInit(UInt(0, width = 64))
  val prefetcher_0_sequenceCounterCollect  = RegInit(UInt(0, width = 64))

  val prefetcher_1_sequenceInstanceCounter = RegInit(UInt(0, width = 64))
  val prefetcher_1_sequenceCounterCollect  = RegInit(UInt(0, width = 64))

  (outer.Snooper.in zip outer.Snooper.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
  //     val MaxAddressBits = in.a.bits.address.getWidth
  //     require((out.a.bits.data).getWidth == 512 && (in.a.bits.data).getWidth == 512)
  //     val PrefetcherInst = 0 to ((NUM_PUS*NUM_CTXT)- 1) map {x => Module(new Prefetcher(MaxAddressBits)(outer.p))}
  //     prefetcher_0_sequenceCounterCollect  := PrefetcherInst(0).io.sequenceCounterCollect       
  //     prefetcher_0_sequenceInstanceCounter := PrefetcherInst(0).io.sequenceInstanceCounter

  //     if (NUM_PUS*NUM_CTXT > 1){
  //       prefetcher_1_sequenceCounterCollect   := PrefetcherInst(1).io.sequenceCounterCollect       
  //       prefetcher_1_sequenceInstanceCounter  := PrefetcherInst(1).io.sequenceInstanceCounter
  //     }
  //     val rrarb = Module(new RRArbiter(UInt(), NUM_PUS*NUM_CTXT))
  //     tl0.a.valid := rrarb.io.out.valid
  //     rrarb.io.out.ready := tl0.a.ready
  //     tl0.a.bits.address := rrarb.io.out.bits
  //     tl0.a.bits.source := LFSR(8, rrarb.io.out.valid)
  //     tl0.a.bits.opcode := 4.U
  //     tl0.a.bits.size   := 6.U
  //     tl0.d.ready := 1.U

  //     when(in.a.valid & in.a.ready && (in.a.bits.opcode === 6.U || in.a.bits.opcode === 4.U ) && (in.a.bits.address > BigInt(2147483648L).U(MaxAddressBits.W)) && (in.a.bits.address < (((BigInt(1) << MaxAddressBits) - 10).U(MaxAddressBits.W)) ) ) {
  //       for (i <- 0 until (NUM_PUS*NUM_CTXT)) {
  //         PrefetcherInst(i).io.inAddress := in.a.bits.address
  //         rrarb.io.in(i).valid := PrefetcherInst(i).io.l3_out_valid
  //         rrarb.io.in(i).bits  := PrefetcherInst(i).io.l3_out_address
  //         PrefetcherInst(i).io.l3_out_ready := rrarb.io.in(i).ready

  //         when(in.a.bits.user1 === i.U) {
  //           PrefetcherInst(i).io.valid := 1.U
  //         }
  //         .otherwise{
  //           PrefetcherInst(i).io.valid := 0.U
  //         }
  //       }
  //     }
  //     .otherwise{
  //       for (i <- 0 until (NUM_PUS*NUM_CTXT)) {
  //          PrefetcherInst(i).io.valid := 0.U
  //       }
  //     }
      out := in
  }

  // TODO: Connect ROCC to the CTXT.
  val ctxt   = Module(new CtxtUnit()(outer.p))
  val pu     = 0 to NUM_PUS - 1 map {x => Module(new PipeUnit()(outer.p)).io}
  val rf     = 0 to NUM_PUS - 1 map {x => Module(new Regfset()(outer.p)).io}

  Annotated.params(this, outer.rocketParams)

  require(p(SubsystemResetSchemeKey)  == ResetSynchronous,
    "Rocket only supports synchronous reset at  this time")

  // dcache perfmon
  val l2_hit_aggr         = RegInit(UInt(0, width = 64))
  val l2_avg_round_aggr   = RegInit(UInt(0, width = 64))
  val l3_hit_aggr         = RegInit(UInt(0, width = 64))
  val l3_avg_round_aggr   = RegInit(UInt(0, width = 64))
  val dram_hit_aggr       = RegInit(UInt(0, width = 64))
  val dram_avg_round_aggr = RegInit(UInt(0, width = 64))
  val dram_threshold      = RegInit(UInt(60, width = 64))
  val l3_threshold        = RegInit(UInt(15, width = 64))
  val stall               = RegInit(UInt(0, width = 64))

  // icache perfmon
  val l2_hit_aggr_ic         = RegInit(UInt(0, width = 64))
  val l2_avg_round_aggr_ic   = RegInit(UInt(0, width = 64))
  val l3_hit_aggr_ic         = RegInit(UInt(0, width = 64))
  val l3_avg_round_aggr_ic   = RegInit(UInt(0, width = 64))
  val dram_hit_aggr_ic       = RegInit(UInt(0, width = 64))
  val dram_avg_round_aggr_ic = RegInit(UInt(0, width = 64))
  val quarter_ctxt_stall_ic  = RegInit(UInt(0, width = 64))
  val full_ctxt_stall_ic     = RegInit(UInt(0, width = 64))
  val half_ctxt_stall_ic     = RegInit(UInt(0, width = 64))
  val cycleCountH            = RegInit(UInt(0, width = 64))
  val cycleCountL            = RegInit(UInt(0, width = 64))
  val cycleCount             = RegInit(UInt(0, width = 128))
  val ipipeutil              = RegInit(UInt(0, width = 64))
  val ipipeutil1             = RegInit(UInt(0, width = 64))
  val mpipeutil              = RegInit(UInt(0, width = 64))
  val mpipeutil1             = RegInit(UInt(0, width = 64))
  val mpipeutil2             = RegInit(UInt(0, width = 64))
  val mpipeutil3             = RegInit(UInt(0, width = 64))
  val fpipeutil              = RegInit(UInt(0, width = 64))
  val fpipeutil1             = RegInit(UInt(0, width = 64))
  val hazard_status          = RegInit(UInt(0, width = 64))

  val C0ReadyNotReady               = RegInit(UInt(0, width = 64))
  val C0ReadyNotReadyCauseDU        = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyLWfiStall      = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyLOpnotReady    = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyLOpnotReadyFPU = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyLflowaltered   = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyLrowSel        = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyNoLInstrFetch  = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyNoLnoPrevDep   = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyNoLnoHazard7   = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyNoLfence       = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyNoLtlbSafeR    = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyNoLinfSlow     = RegInit(UInt(0, width = 64))
  val C0IR_instrReadyNoLctlFlow     = RegInit(UInt(0, width = 64))
  val C0RowselNotpickedup           = RegInit(UInt(0, width = 64))

  val C1ReadyNotReady               = RegInit(UInt(0, width = 64))
  val C1ReadyNotReadyCauseDU        = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyLWfiStall      = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyLOpnotReady    = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyLOpnotReadyFPU = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyLflowaltered   = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyLrowSel        = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyNoLInstrFetch  = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyNoLnoPrevDep   = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyNoLnoHazard7   = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyNoLfence       = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyNoLtlbSafeR    = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyNoLinfSlow     = RegInit(UInt(0, width = 64))
  val C1IR_instrReadyNoLctlFlow     = RegInit(UInt(0, width = 64))
  val C1RowselNotpickedup           = RegInit(UInt(0, width = 64))


  outer.regNode.regmap(
    // dcache perfmon
    0x000 -> Seq(RegField(64,dram_threshold)),
    0x008 -> Seq(RegField(64,l3_threshold)),
    0x010 -> Seq(RegField(64,l2_hit_aggr)),
    0x018 -> Seq(RegField(64,l2_avg_round_aggr)),
    0x020 -> Seq(RegField(64,l3_hit_aggr)),
    0x028 -> Seq(RegField(64,l3_avg_round_aggr)),
    0x030 -> Seq(RegField(64,dram_hit_aggr)),
    0x038 -> Seq(RegField(64,dram_avg_round_aggr)),
    0x040 -> Seq(RegField(64,stall)),

    //icache perfmon
    0x048 -> Seq(RegField(64,l2_hit_aggr_ic)),
    0x050 -> Seq(RegField(64,l2_avg_round_aggr_ic)),
    0x058 -> Seq(RegField(64,l3_hit_aggr_ic)),
    0x060 -> Seq(RegField(64,l3_avg_round_aggr_ic)),
    0x068 -> Seq(RegField(64,dram_hit_aggr_ic)),
    0x070 -> Seq(RegField(64,dram_avg_round_aggr_ic)),

    //core perfmon
    0x078 -> Seq(RegField(64,quarter_ctxt_stall_ic)),
    0x080 -> Seq(RegField(64,half_ctxt_stall_ic)),
    0x088 -> Seq(RegField(64,full_ctxt_stall_ic)),
    0x090 -> Seq(RegField(64,cycleCountH)),
    0x098 -> Seq(RegField(64,cycleCountL)),  
    0x100 -> Seq(RegField(64,ipipeutil)),
    0x108 -> Seq(RegField(64,ipipeutil1)),
    0x110 -> Seq(RegField(64,mpipeutil)),
    0x118 -> Seq(RegField(64,mpipeutil1)),
    0x120 -> Seq(RegField(64,mpipeutil2)),
    0x128 -> Seq(RegField(64,mpipeutil3)),
    0x130 -> Seq(RegField(64,fpipeutil)),
    0x138 -> Seq(RegField(64,fpipeutil1)),
    0x140 -> Seq(RegField(64,C0ReadyNotReady              )),
    0x148 -> Seq(RegField(64,C0ReadyNotReadyCauseDU       )),
    0x150 -> Seq(RegField(64,C0IR_instrReadyLWfiStall     )),
    0x158 -> Seq(RegField(64,C0IR_instrReadyLOpnotReady   )),
    0x160 -> Seq(RegField(64,C0IR_instrReadyLOpnotReadyFPU)),
    0x168 -> Seq(RegField(64,C0IR_instrReadyLflowaltered  )),
    0x170 -> Seq(RegField(64,C0IR_instrReadyLrowSel       )),
    0x178 -> Seq(RegField(64,C0IR_instrReadyNoLInstrFetch )),
    0x180 -> Seq(RegField(64,C0IR_instrReadyNoLnoPrevDep  )),
    0x188 -> Seq(RegField(64,C0IR_instrReadyNoLnoHazard7  )),
    0x190 -> Seq(RegField(64,C0IR_instrReadyNoLfence      )),
    0x198 -> Seq(RegField(64,C0IR_instrReadyNoLtlbSafeR   )),
    0x200 -> Seq(RegField(64,C0IR_instrReadyNoLinfSlow    )),
    0x208 -> Seq(RegField(64,C0IR_instrReadyNoLctlFlow    )),
    0x210 -> Seq(RegField(64,C0RowselNotpickedup          )),
    0x218 -> Seq(RegField(64,C1ReadyNotReady              )),
    0x220 -> Seq(RegField(64,C1ReadyNotReadyCauseDU       )),
    0x228 -> Seq(RegField(64,C1IR_instrReadyLWfiStall     )),
    0x230 -> Seq(RegField(64,C1IR_instrReadyLOpnotReady   )),
    0x238 -> Seq(RegField(64,C1IR_instrReadyLOpnotReadyFPU)),
    0x240 -> Seq(RegField(64,C1IR_instrReadyLflowaltered  )),
    0x248 -> Seq(RegField(64,C1IR_instrReadyLrowSel       )),
    0x250 -> Seq(RegField(64,C1IR_instrReadyNoLInstrFetch )),
    0x258 -> Seq(RegField(64,C1IR_instrReadyNoLnoPrevDep  )),
    0x260 -> Seq(RegField(64,C1IR_instrReadyNoLnoHazard7  )),
    0x268 -> Seq(RegField(64,C1IR_instrReadyNoLfence      )),
    0x270 -> Seq(RegField(64,C1IR_instrReadyNoLtlbSafeR   )),
    0x278 -> Seq(RegField(64,C1IR_instrReadyNoLinfSlow    )),
    0x280 -> Seq(RegField(64,C1IR_instrReadyNoLctlFlow    )),
    0x288 -> Seq(RegField(64,C1RowselNotpickedup          )),
    0x290 -> Seq(RegField(64,prefetcher_0_sequenceInstanceCounter )),
    0x298 -> Seq(RegField(64,prefetcher_0_sequenceCounterCollect )),
    0x300 -> Seq(RegField(64,prefetcher_1_sequenceInstanceCounter )),
    0x308 -> Seq(RegField(64,prefetcher_1_sequenceCounterCollect))
    )

  outer.mmioSlaveNode.module.io.in_clock := io.l2_clock
  outer.mmioSlaveNode.module.io.in_reset := io.l2_reset
  outer.mmioSlaveNode.module.io.out_clock := clock
  outer.mmioSlaveNode.module.io.out_reset := reset

  outer.PrefetcherNode.module.io.in_clock := clock
  outer.PrefetcherNode.module.io.in_reset := reset
  outer.PrefetcherNode.module.io.out_clock := io.l2_clock
  outer.PrefetcherNode.module.io.out_reset := io.l2_reset

  // Per-bank dcache master crossings (see BaseTile.scala/RocketTile.scala): same
  // tile-outward direction/domains as PrefetcherNode above.
  for(miniId <- 0 until NUM_L2BANKS) {
    outer.masterNodeRatCross(miniId).module.io.in_clock  := clock
    outer.masterNodeRatCross(miniId).module.io.in_reset  := reset
    outer.masterNodeRatCross(miniId).module.io.out_clock := io.l2_clock
    outer.masterNodeRatCross(miniId).module.io.out_reset := io.l2_reset
  }

  when(ctxt.io.ctxtUPipeU(0).idc_validI){
    ipipeutil := ipipeutil + 1.U
  }
  when(ctxt.io.ctxtUPipeU(0).idc_validF){
    fpipeutil := fpipeutil + 1.U
  }
  when(ctxt.io.ctxtUPipeU(0).idc_validM(0)){
    mpipeutil := mpipeutil + 1.U
  }

  if(NUM_PUS > 1){
    when(ctxt.io.ctxtUPipeU(0).idc_validM(1)){
      mpipeutil2 := mpipeutil2 + 1.U
    }
    when(ctxt.io.ctxtUPipeU(1).idc_validF){
      fpipeutil1 := fpipeutil1 + 1.U
    }
    when(ctxt.io.ctxtUPipeU(1).idc_validI){
      ipipeutil1 := ipipeutil1 + 1.U
    }
    when(ctxt.io.ctxtUPipeU(1).idc_validM(0)){
      mpipeutil1 := mpipeutil1 + 1.U
    }
    when(ctxt.io.ctxtUPipeU(1).idc_validM(1)){
      mpipeutil3 := mpipeutil3 + 1.U
    }
  }

  cycleCount := cycleCount + 1.U
  cycleCountH := cycleCount(127,64)
  cycleCountL := cycleCount(63,0)

  val C0ctxtRowOld = RegInit(0.U(1.W))
  val C1ctxtRowOld = RegInit(0.U(1.W))
  C0ctxtRowOld := ctxt.io.IR_instrReadyLrowSel(0)
  when((C0ctxtRowOld === 1.U) && (ctxt.io.IR_instrReadyLrowSel(0) === 1.U)){
    C0RowselNotpickedup := C0RowselNotpickedup + 1.U
  }
  when(C0ctxtRowOld === 0.U && ctxt.io.IR_instrReadyLrowSel(0) === 1.U){
    C0IR_instrReadyLrowSel := C0IR_instrReadyLrowSel + 1.U
  }

  when(ctxt.io.ReadyNotReady(0)){
    C0ReadyNotReady := C0ReadyNotReady + 1.U
  }
  when(ctxt.io.ReadyNotReadyCauseDU(0)){
    C0ReadyNotReadyCauseDU := C0ReadyNotReadyCauseDU + 1.U
  }
  when(ctxt.io.IR_instrReadyLWfiStall(0)){
    C0IR_instrReadyLWfiStall := C0IR_instrReadyLWfiStall + 1.U
  }
  when(ctxt.io.IR_instrReadyLOpnotReady(0)){
    C0IR_instrReadyLOpnotReady := C0IR_instrReadyLOpnotReady + 1.U
  }
  when(ctxt.io.IR_instrReadyLOpnotReadyFPU(0)){
    C0IR_instrReadyLOpnotReadyFPU := C0IR_instrReadyLOpnotReadyFPU + 1.U
  }
  when(ctxt.io.IR_instrReadyLflowaltered(0)){
    C0IR_instrReadyLflowaltered := C0IR_instrReadyLflowaltered + 1.U
  }
  when(ctxt.io.IR_instrReadyNoLInstrFetch(0)){
    C0IR_instrReadyNoLInstrFetch := C0IR_instrReadyNoLInstrFetch + 1.U
  }
  when(ctxt.io.IR_instrReadyNoLnoPrevDep(0)){
    C0IR_instrReadyNoLnoPrevDep := C0IR_instrReadyNoLnoPrevDep + 1.U
  }
  when(ctxt.io.IR_instrReadyNoLnoHazard7(0)){
    C0IR_instrReadyNoLnoHazard7 := C0IR_instrReadyNoLnoHazard7 + 1.U
  }
  when(ctxt.io.IR_instrReadyNoLfence(0)){
    C0IR_instrReadyNoLfence := C0IR_instrReadyNoLfence + 1.U
  }
  when(ctxt.io.IR_instrReadyNoLtlbSafeR(0)){
    C0IR_instrReadyNoLtlbSafeR := C0IR_instrReadyNoLtlbSafeR + 1.U
  }
  when(ctxt.io.IR_instrReadyNoLinfSlow(0)){
    C0IR_instrReadyNoLinfSlow := C0IR_instrReadyNoLinfSlow + 1.U
  }
  when(ctxt.io.IR_instrReadyNoLctlFlow(0)){
    C0IR_instrReadyNoLctlFlow := C0IR_instrReadyNoLctlFlow + 1.U
  }
  
  if(NUM_PUS > 1){
    C1ctxtRowOld := ctxt.io.IR_instrReadyLrowSel(1)
    when((C1ctxtRowOld === 1.U) && (ctxt.io.IR_instrReadyLrowSel(1) === 1.U)){
      C1RowselNotpickedup := C1RowselNotpickedup + 1.U
    }
    when(C1ctxtRowOld === 0.U && ctxt.io.IR_instrReadyLrowSel(1) === 1.U){
      C1IR_instrReadyLrowSel := C1IR_instrReadyLrowSel + 1.U
    }
    when(ctxt.io.ReadyNotReady(1)){
      C1ReadyNotReady := C1ReadyNotReady + 1.U
    }
    when(ctxt.io.ReadyNotReadyCauseDU(1)){
      C1ReadyNotReadyCauseDU := C1ReadyNotReadyCauseDU + 1.U
    }
    when(ctxt.io.IR_instrReadyLWfiStall(1)){
      C1IR_instrReadyLWfiStall := C1IR_instrReadyLWfiStall + 1.U
    }
    when(ctxt.io.IR_instrReadyLOpnotReady(1)){
      C1IR_instrReadyLOpnotReady := C1IR_instrReadyLOpnotReady + 1.U
    }
    when(ctxt.io.IR_instrReadyLOpnotReadyFPU(1)){
      C1IR_instrReadyLOpnotReadyFPU := C1IR_instrReadyLOpnotReadyFPU + 1.U
    }
    when(ctxt.io.IR_instrReadyLflowaltered(1)){
      C1IR_instrReadyLflowaltered := C1IR_instrReadyLflowaltered + 1.U
    }
    when(ctxt.io.IR_instrReadyNoLInstrFetch(1)){
      C1IR_instrReadyNoLInstrFetch := C1IR_instrReadyNoLInstrFetch + 1.U
    }
    when(ctxt.io.IR_instrReadyNoLnoPrevDep(1)){
      C1IR_instrReadyNoLnoPrevDep := C1IR_instrReadyNoLnoPrevDep + 1.U
    }
    when(ctxt.io.IR_instrReadyNoLnoHazard7(1)){
      C1IR_instrReadyNoLnoHazard7 := C1IR_instrReadyNoLnoHazard7 + 1.U
    }
    when(ctxt.io.IR_instrReadyNoLfence(1)){
      C1IR_instrReadyNoLfence := C1IR_instrReadyNoLfence + 1.U
    }
    when(ctxt.io.IR_instrReadyNoLtlbSafeR(1)){
      C1IR_instrReadyNoLtlbSafeR := C1IR_instrReadyNoLtlbSafeR + 1.U
    }
    when(ctxt.io.IR_instrReadyNoLinfSlow(1)){
      C1IR_instrReadyNoLinfSlow := C1IR_instrReadyNoLinfSlow + 1.U
    }
    when(ctxt.io.IR_instrReadyNoLctlFlow(1)){
      C1IR_instrReadyNoLctlFlow := C1IR_instrReadyNoLctlFlow + 1.U
    }
  }

  val perfEvents = new EventSets(Seq())
  val cu         = 0 to NUM_PUS - 1 map {x => Module(new CsrUnit(perfEvents, nBreakpoints)).io}

  // Connect FPU ports
  fpuOpt foreach { fpu =>
    for(ips <- 0 until NUM_PUS) {
      pu(ips).pipeFpu <> fpu.io.pipeFpu(ips)
    }
  }

  // Alias for DU.
  val du = outer.dcache.module.io

  for(mps <- 0 until NUM_MPS) {
    ptwPortsH0  += du.ptw(mps)
    if(NUM_PUS == 2) {
      ptwPortsH1  += du.ptw(NUM_MPS+mps)
    }
  }

  val permMshr_l2_hit_aggr         =   RegInit(VecInit(Seq.fill(du.mshr_busy.length)(0.U(64.W))))
  val permMshr_l2_avg_round_aggr   =   RegInit(VecInit(Seq.fill(du.mshr_busy.length)(0.U(64.W))))      
  val permMshr_l3_hit_aggr         =   RegInit(VecInit(Seq.fill(du.mshr_busy.length)(0.U(64.W))))
  val permMshr_l3_avg_round_aggr   =   RegInit(VecInit(Seq.fill(du.mshr_busy.length)(0.U(64.W))))      
  val permMshr_dram_hit_aggr       =   RegInit(VecInit(Seq.fill(du.mshr_busy.length)(0.U(64.W))))  
  val permMshr_dram_avg_round_aggr =   RegInit(VecInit(Seq.fill(du.mshr_busy.length)(0.U(64.W))))        

  l2_hit_aggr         :=  permMshr_l2_hit_aggr.reduce(_ +& _)        
  l2_avg_round_aggr   :=  permMshr_l2_avg_round_aggr.reduce(_ +& _)  
  l3_hit_aggr         :=  permMshr_l3_hit_aggr.reduce(_ +& _)        
  l3_avg_round_aggr   :=  permMshr_l3_avg_round_aggr.reduce(_ +& _)  
  dram_hit_aggr       :=  permMshr_dram_hit_aggr.reduce(_ +& _)      
  dram_avg_round_aggr :=  permMshr_dram_avg_round_aggr.reduce(_ +& _)

  when(du.mshr_busy.reduce(_ & _)){
    stall := stall + 1.U
  }

  // dcache perf monitors
  for ((m, idx) <- du.mshr_busy.zipWithIndex) {
    val latency_count_reg = RegInit(0.U(20.W))

    when((!m) & latency_count_reg > 4.U){
      when(latency_count_reg < l3_threshold){
        permMshr_l2_hit_aggr(idx) := permMshr_l2_hit_aggr(idx) + 1.U
        permMshr_l2_avg_round_aggr(idx) := latency_count_reg + permMshr_l2_avg_round_aggr(idx)
      }
      .elsewhen(latency_count_reg < dram_threshold){
        permMshr_l3_hit_aggr(idx) := permMshr_l3_hit_aggr(idx) + 1.U
        permMshr_l3_avg_round_aggr(idx) := latency_count_reg + permMshr_l3_avg_round_aggr(idx)
      }
      .elsewhen(latency_count_reg > dram_threshold){
        permMshr_dram_hit_aggr(idx) := permMshr_dram_hit_aggr(idx) + 1.U
        permMshr_dram_avg_round_aggr(idx) := latency_count_reg + permMshr_dram_avg_round_aggr(idx)
      }
      latency_count_reg := 0.U
    }
    .elsewhen(m & latency_count_reg < 1048576.U){
      latency_count_reg := latency_count_reg + 1.U
    }
    .elsewhen(!m){
      latency_count_reg := 0.U
    }
  }

  // icache perf monitors
  val permMshr_l2_hit_aggr_ic         =   RegInit(VecInit(Seq.fill(NUM_PUS)(0.U(64.W))))
  val permMshr_l2_avg_round_aggr_ic   =   RegInit(VecInit(Seq.fill(NUM_PUS)(0.U(64.W))))      
  val permMshr_l3_hit_aggr_ic         =   RegInit(VecInit(Seq.fill(NUM_PUS)(0.U(64.W))))
  val permMshr_l3_avg_round_aggr_ic   =   RegInit(VecInit(Seq.fill(NUM_PUS)(0.U(64.W))))      
  val permMshr_dram_hit_aggr_ic       =   RegInit(VecInit(Seq.fill(NUM_PUS)(0.U(64.W))))  
  val permMshr_dram_avg_round_aggr_ic =   RegInit(VecInit(Seq.fill(NUM_PUS)(0.U(64.W))))  

  val count_ready = PopCount(ctxt.io.instrFetchPerf)
  when(count_ready < ((NUM_PUS*NUM_CTXT) - ((NUM_PUS*NUM_CTXT)/4)).U) {
    quarter_ctxt_stall_ic := quarter_ctxt_stall_ic + 1.U
  }
  when(count_ready < ((NUM_PUS*NUM_CTXT)/2).U){
    half_ctxt_stall_ic := half_ctxt_stall_ic + 1.U
  }
  when(count_ready === 0.U){
    full_ctxt_stall_ic := full_ctxt_stall_ic + 1.U
  }
  
  for(ips <- 0 until NUM_PUS) { 
    val count_start = RegInit(false.B)
    val local_latency_count = RegInit(0.U(20.W))
    when(outer.frontend.module.io.aFirePerf(ips)){
      count_start := true.B
    }
    .elsewhen(outer.frontend.module.io.dFirePerf(ips)){
      count_start := false.B
    }
    when(count_start){
      local_latency_count := local_latency_count + 1.U
    }

    when(outer.frontend.module.io.dFirePerf(ips) && count_start){
      local_latency_count := 0.U
      when(local_latency_count < l3_threshold && local_latency_count > 1.U ){
        permMshr_l2_hit_aggr_ic(ips) := permMshr_l2_hit_aggr_ic(ips) + 1.U
        permMshr_l2_avg_round_aggr_ic(ips) := local_latency_count + permMshr_l2_avg_round_aggr_ic(ips)
      }
      .elsewhen(local_latency_count < dram_threshold && local_latency_count > 1.U ){
        permMshr_l3_hit_aggr_ic(ips) := permMshr_l3_hit_aggr_ic(ips) + 1.U
        permMshr_l3_avg_round_aggr_ic(ips) := local_latency_count + permMshr_l3_avg_round_aggr_ic(ips)
      }
      .elsewhen(local_latency_count > dram_threshold && local_latency_count > 1.U  ){
        permMshr_dram_hit_aggr_ic(ips) := permMshr_dram_hit_aggr_ic(ips) + 1.U
        permMshr_dram_avg_round_aggr_ic(ips) := local_latency_count + permMshr_dram_avg_round_aggr_ic(ips)
      }
    }
  }

  l2_hit_aggr_ic          := permMshr_l2_hit_aggr_ic.reduce(_ +& _)        
  l2_avg_round_aggr_ic    := permMshr_l2_avg_round_aggr_ic.reduce(_ +& _)  
  l3_hit_aggr_ic          := permMshr_l3_hit_aggr_ic.reduce(_ +& _)        
  l3_avg_round_aggr_ic    := permMshr_l3_avg_round_aggr_ic.reduce(_ +& _)  
  dram_hit_aggr_ic        := permMshr_dram_hit_aggr_ic.reduce(_ +& _)      
  dram_avg_round_aggr_ic  := permMshr_dram_avg_round_aggr_ic.reduce(_ +& _)

  //Note: whenever see ".module" in the reference of a variable, then it is probably a reference to a lazy module
  // Be aware that if you can't find the variable, it is probably mixed in -- search the inheritance graph
  // and mixins are usually via cake pattern (but many lazy modules are not mixins and many mixins are not cake pattern)
  //Cake pattern: Lazy module + impl of that lazy module + bundle
  outer.frontend.module.io.reset_vector := constants.reset_vector
  outer.frontend.module.io.hartid       := constants.hartid
  du.hartid                             := constants.hartid

  // Connect the coprocessor interfaces
  // TODO: Connect other ips.
  if (outer.roccs.size > 0) {
    cmdRouter.get.io.in  <> pu(0).rocc.cmd
    outer.roccs.foreach(_.module.io.exception := pu(0).rocc.exception)
    pu(0).rocc.resp      <> respArb.get.io.out
    pu(0).rocc.busy      <> (cmdRouter.get.io.busy || outer.roccs.map(_.module.io.busy).reduce(_ || _))
    pu(0).rocc.interrupt := outer.roccs.map(_.module.io.interrupt).reduce(_ || _)
  }

  // CSR connections.
  for(ips <- 0 until NUM_PUS) {
    pu(ips).hartid  := (constants.hartid*UInt(NUM_PUS)) + UInt(ips)
    rf(ips).hartid  := (constants.hartid*UInt(NUM_PUS)) + UInt(ips)

    for(row <- 0 until NUM_CTXT) {
      ptw(ips).io.dpath(row)   := cu(ips).ptw(row)
      outer.decodeCoreInterrupts(cu(ips).interrupts(row), row * NUM_PUS + ips) // Decode the interrupt vector
    }

    cu(ips).hartid       := UInt(ips) + constants.hartid * UInt(NUM_PUS * NUM_CTXT)
  }

  // Connect PTW sfence bus.
  for(ips <- 0 until NUM_PUS) {
    val sfenceValid                 = ctxt.io.ctxtUnitInstrUnit(ips).sfenceReq_valid && ctxt.io.ctxtUnitInstrUnit(ips).reqValid
    ptw(ips).io.sfence.valid       := Reg(next=sfenceValid, init=Bool(false))
    ptw(ips).io.sfence.bits.rs1    := RegEnable(ctxt.io.ctxtUnitInstrUnit(ips).sfenceReq_rs1, sfenceValid)
    ptw(ips).io.sfence.bits.rs2    := RegEnable(ctxt.io.ctxtUnitInstrUnit(ips).sfenceReq_rs2, sfenceValid)
    ptw(ips).io.sfence.bits.ctxtId := RegEnable(ctxt.io.ctxtUnitInstrUnit(ips).reqCtxtId, sfenceValid)
    ptw(ips).io.sfence.bits.addr   := RegEnable(ctxt.io.ctxtUnitInstrUnit(ips).reqAddr, sfenceValid)

    ctxt.io.sfenceFinish(ips) := ptw(ips).io.sfenceFinish
  }

  // Reset vector.
  ctxt.io.reset_vector := constants.reset_vector

  if(NUM_PUS == 1) {
    outer.frontend.module.io.flush_icache      := pu(0).pipeUCtxtU.flush_icache
  }
  else {
    outer.frontend.module.io.flush_icache      := pu(0).pipeUCtxtU.flush_icache || pu(1).pipeUCtxtU.flush_icache
  }

  outer.frontend.module.io.ctxtUnitInstrUnit <> ctxt.io.ctxtUnitInstrUnit
  outer.frontend.module.io.halfSel           := ctxt.io.halfSel

  // TODO eliminate this redundancy
  val h = dcachePorts.size
  require(h == NUM_PUS, s"port list size was $h, defined counted $NUM_PUS")
  du.dcPorts           <> dcachePorts

  ptw(0).io.requestor     <> ptwPortsH0
  if(NUM_PUS == 2) {
    ptw(1).io.requestor   <> ptwPortsH1
  }

  for(ips <- 0 until NUM_PUS) {
    // We notify all the PTWs that there will receive the read data request.
    // This can be inefficient as this read target only one PTW.
    ptw(ips).io.ptwEarlyNotif := du.ptwEarlyNotif
  }

  for(ips <- 0 until NUM_PUS) {
    // Connect CTXT to the RF.
    rf(ips).ctxtToRfI             := ctxt.io.ctxtToRfI(ips)
    rf(ips).earlyRfWrValid        := pu(ips).earlyRfWrValid
    rf(ips).earlyRfWrCtxtId       := pu(ips).earlyRfWrCtxtId
    pu(ips).csrUnitIntpipe        <> cu(ips).csrUnitIntpipe
    pu(ips).csrStatus             := cu(ips).csrStatus
    pu(ips).csr_data              := cu(ips).csr_data
    ctxt.io.csrUnitCtxtUnit(ips)  := cu(ips).csrUnitCtxtUnit
    rf(ips).writeToRegfset(0)     <> pu(ips).writeToRegfset(0)
    rf(ips).writeToRegfset(1)     <> du.dataUnitToRegfset(ips)
    rf(ips).writeToRegfset(2)     <> pu(ips).writeToRegfset(1)
    rf(ips).writeToRegfset(3)     <> pu(ips).writeToRegfset(2)
    rf(ips).earlyDuWrFlag         <> du.earlyDuWrFlag(ips)
    pu(ips).regfsetToIntpipe      := rf(ips).regfsetToIntpipe

    for(mps <- 0 until NUM_MPS) {
      cu(ips).dataUnitToCsr(mps)          := du.dataUnitToCsr(ips*NUM_MPS+mps)
      du.mpipeDataUnit(ips*NUM_MPS+mps)   := pu(ips).mpipeDataUnit(mps)
      du.csrToDataUnit(ips*NUM_MPS+mps)   := cu(ips).csrToDataUnit(mps)
    }

    du.duReadyCheck(ips)              := pu(ips).duReadyCheck
    ctxt.io.dataUnitToCtxt(ips)       := du.dataUnitToCtxt(ips)
    ctxt.io.regfsetToCtxt(ips)        := rf(ips).regfsetToCtxt

    // Connect CtxtUnit to the PipeUnit.
    pu(ips).ctxtUPipeU      := ctxt.io.ctxtUPipeU(ips)
    ctxt.io.pipeUCtxtU(ips) := pu(ips).pipeUCtxtU

    if(usingFPU == false) {
      for(ips <- 0 until NUM_PUS) {
        du.dataUnitToFpu(ips).ready           := Bool(true)
        ctxt.io.fpuToCtxtUnit(ips).fpuCtxtId0 := UInt(0)
        ctxt.io.fpuToCtxtUnit(ips).fpuSafe0   := Bool(false)
        ctxt.io.fpuToCtxtUnit(ips).fpuRegId0  := UInt(0)
        ctxt.io.fpuToCtxtUnit(ips).fpuCtxtId1 := UInt(0)
        ctxt.io.fpuToCtxtUnit(ips).fpuSafe1   := Bool(false)
        ctxt.io.fpuToCtxtUnit(ips).fpuRegId1  := UInt(0)
      }
    }
    else {
      // Connect FPU ports.
      fpuOpt foreach{ fpu => fpu.io.dataUnitToFpu      <> du.dataUnitToFpu}
      fpuOpt foreach{ fpu => fpu.io.fpuPending         <> du.fpuPending}
      fpuOpt foreach{ fpu => fpu.io.earlyFpuWrFlag     <> du.earlyFpuWrFlag}
      fpuOpt foreach{ fpu => fpu.io.unCacheFpuNotif    := du.unCacheFpuNotif}
      fpuOpt foreach{ fpu => fpu.io.earlyCtxtFpuWr     := du.earlyCtxtFpuWr}
      fpuOpt foreach{ fpu => ctxt.io.fpuToCtxtUnit     := fpu.io.fpuToCtxtUnit}
      fpuOpt foreach{ fpu => fpu.io.ctxtToFpu          := ctxt.io.ctxtToFpu}
    }
  }

  val trcX = Wire(new TracedInstruction())
  trcX      := pu(0).ipTrace

  // trcX.occupCntr  := du.duTrace.occupCntr
  // trcX.statusDU0  := du.duTrace.statusDU0
  //trcX.addrVDU0   := du.duTrace.addrVDU0
  //trcX.addrPDU0   := du.duTrace.addrPDU0
  //trcX.dataDU0    := du.duTrace.dataDU0

  // trcX.statusDU1  := du.duTrace.statusDU1
  //trcX.addrVDU1   := du.duTrace.addrVDU1
  //trcX.addrPDU1   := du.duTrace.addrPDU1
  //trcX.dataDU1    := du.duTrace.dataDU1

  //trcX.isRealDU0  := du.duTrace.isRealDU0
  //trcX.ipipDU0    := du.duTrace.ipipDU0

  trcX.ptbrMode  := du.duTrace.ptbrMode
  trcX.dprv      := du.duTrace.dprv
  trcX.ptwValid  := du.duTrace.ptwValid
  trcX.ptwLevel  := du.duTrace.ptwLevel
  trcX.sfenceVa  := du.duTrace.sfenceVa
  trcX.dtlbReady := du.duTrace.dtlbReady
  trcX.ptwReady  := ptw(0).io.requestor(0).req.ready

  trcX.iptwValid     := outer.frontend.module.io.ptw(0).resp.valid
  trcX.itlbReady     := ctxt.io.ctxtUnitInstrUnit(0).itlbReady
  trcX.icacheReady   := ctxt.io.ctxtUnitInstrUnit(0).icacheReady
  trcX.icacheValid   := ctxt.io.ctxtUnitInstrUnit(0).reqValid

  if(true) {
    val tlbCtxtId       = Wire(UInt(width=3))
    tlbCtxtId          := ctxt.io.ctxtUnitInstrUnit(0).reqCtxtId
    trcX.tlbCtxtId     := Cat(ctxt.io.ctxtUnitInstrUnit(0).reqValid, tlbCtxtId)
    trcX.vInstrAddr    := ctxt.io.ctxtUnitInstrUnit(0).reqAddr

    if(NUM_PUS == 2) {
      trcX.icacheState   := Cat(ctxt.io.ctxtUnitInstrUnit(0).icacheReady, ctxt.io.ctxtUnitInstrUnit(0).reqValid, ctxt.io.ctxtUnitInstrUnit(1).icacheReady, ctxt.io.ctxtUnitInstrUnit(1).reqValid)
    }
    else {
      trcX.icacheState   := Cat(ctxt.io.ctxtUnitInstrUnit(0).icacheReady, ctxt.io.ctxtUnitInstrUnit(0).reqValid)
    }
    trcX.pInstrAddr    := outer.frontend.module.io.pInstrAddr
  }
  else {
    // A workaround to monitor the register updates.
    when(rf(0).writeToRegfset(0).valid) {
      trcX.tlbCtxtId    := Cat(rf(0).writeToRegfset(0).valid && rf(0).writeToRegfset(0).bits.isReal, rf(0).writeToRegfset(0).bits.ctxtId)
      trcX.vInstrAddr   := Cat(rf(0).writeToRegfset(0).bits.destReg(4), ctxt.io.ctxtUPipeU(0).idc_validI, ctxt.io.ctxtUPipeU(0).idc_validF, ctxt.io.ctxtUPipeU(0).idc_validM(0), pu(0).pipeUCtxtU.flowAltered, pu(0).pipeUCtxtU.flowAlteredM(0), ctxt.io.csrUnitCtxtUnit(0)(0).csrFlowAlt, ctxt.io.csrUnitCtxtUnit(0)(0).muteD2_reg, rf(0).writeToRegfset(0).bits.dataToWrite(63, 32))
      trcX.icacheState  := rf(0).writeToRegfset(0).bits.destReg(3, 0)
      trcX.pInstrAddr   := rf(0).writeToRegfset(0).bits.dataToWrite(31, 0)
    }
    .otherwise {
      trcX.tlbCtxtId   := Cat(rf(0).writeToRegfset(1).valid && rf(0).writeToRegfset(1).bits.isReal, rf(0).writeToRegfset(1).bits.ctxtId)
      trcX.vInstrAddr  := Cat(rf(0).writeToRegfset(1).bits.destReg(4), ctxt.io.ctxtUPipeU(0).idc_validI, ctxt.io.ctxtUPipeU(0).idc_validF, ctxt.io.ctxtUPipeU(0).idc_validM(0), pu(0).pipeUCtxtU.flowAltered, pu(0).pipeUCtxtU.flowAlteredM(0), ctxt.io.csrUnitCtxtUnit(0)(0).csrFlowAlt, ctxt.io.csrUnitCtxtUnit(0)(0).muteD2_reg, rf(0).writeToRegfset(1).bits.dataToWrite(63, 32))
      trcX.icacheState := rf(0).writeToRegfset(1).bits.destReg(3, 0)
      trcX.pInstrAddr  := rf(0).writeToRegfset(1).bits.dataToWrite(31, 0)
    }
  }


  if(true) {
    // trcX.ptwStatus   := ptw(0).io.ptwStatus
    // trcX.ptwCtxtId   := ptw(0).io.ptwCtxtId
    // trcX.ptwVAddr    := ptw(0).io.ptwVAddr
    // trcX.ptwPAddr    := ptw(0).io.ptwPAddr
    // trcX.ptwFenceC   := ptw(0).io.ptwFenceC
    // trcX.ptwFenceA   := ptw(0).io.ptwFenceA
  }
  else {
    // A workaround to monitor the register updates.
    // trcX.ptwStatus   := Cat(du.dataUnitToRegfset(0).valid, du.dataUnitToRegfset(0).bits.ctxtId)
    // trcX.ptwCtxtId   := du.dataUnitToRegfset(0).bits.destReg
    // trcX.ptwVAddr    := du.dataUnitToRegfset(0).bits.dataToWrite(51, 24)
    // trcX.ptwPAddr    := du.dataUnitToRegfset(0).bits.dataToWrite(23, 0)

    val duCtxtId1     = Wire(UInt(width=3))
    duCtxtId1        := du.dataUnitToRegfset(1).bits.ctxtId
    // trcX.ptwFenceC   := Cat(du.dataUnitToRegfset(1).valid, duCtxtId1)
    // trcX.ptwFenceA   := du.dataUnitToRegfset(1).bits.dataToWrite(27, 0)
  }

  //trcX.hazardStatus0 := ctxt.io.hazardStatus0
  //trcX.hazardStatus1 := ctxt.io.hazardStatus1
  //trcX.hazardStatus2 := ctxt.io.hazardStatus2
  trcX.hazardStatus3 := ctxt.io.hazardStatus3


  // trcX.l3In_a_valid  := io.l3In_a_v
  // trcX.l3In_d_valid  := io.l3In_d_v
  // trcX.l3In_a_ready  := io.l3In_a_r
  // trcX.l3In_d_ready  := io.l3In_d_r
  // trcX.l3In_a_address   := io.l3In_a_addr
  // trcX.l3In_a_source    := io.l3In_a_src
  // trcX.l3In_d_source    := io.l3In_d_src

  trcX.l2In_a_valid  := 0.U //io.l2In_a_v
  trcX.l2In_d_valid  := 0.U //io.l2In_d_v
  trcX.l2In_a_ready  := 0.U //io.l2In_a_r
  trcX.l2In_d_ready  := 0.U //io.l2In_d_r
  trcX.l2In_a_address   := 0.U //io.l2In_a_addr
  trcX.l2In_a_source    := 0.U //io.l2In_a_src
  trcX.l2In_d_source    := 0.U //io.l2In_d_src

  //L3MemSysxBar TL Out
  trcX.l3MemSys_valid  := 0.U //io.l3MemSys_v 
  trcX.l3MemSys_ready  := 0.U //io.l3MemSys_r

  trcX.l3MemSys_a_address := 0.U //io.l3MemSys_a_addr
  trcX.l3MemSys_a_source  := 0.U //io.l3MemSys_a_src
  trcX.l3MemSys_d_source  := 0.U //io.l3MemSys_d_src
  
  // ECCDramCombXbar TL Out
  trcX.eccDRAM_valid  := 0.U //io.eccDRAM_v
  trcX.eccDRAM_ready  := 0.U //io.eccDRAM_r

  trcX.eccDRAM_a_address  := 0.U //io.eccDRAM_a_addr
  trcX.eccDRAM_a_source   := 0.U //io.eccDRAM_a_src
  trcX.eccDRAM_d_source   := 0.U //io.eccDRAM_d_src

  if(NUM_PUS > 1){
    trcX.valid2 := du.req_trace_valid(1)
    trcX.iaddr2 := du.req_trace_addr(1)
    trcX.insn   := Cat(14.U(4.W),du.req_trace_ctxt(0),15.U(4.W),du.req_trace_ctxt(1),14.U(4.W))
  }
  else{
    trcX.insn   := Cat(14.U(4.W),du.req_trace_ctxt(0),15.U(4.W),du.req_trace_ctxt(0),14.U(4.W))
  }
  trcX.valid  := du.req_trace_valid(0)
  trcX.iaddr  := du.req_trace_addr(0)
  trcX.insn2  := 15.U(32.W)
  
  // if(NUM_PUS == 2) {
  //   trcX.valid2    := pu(1).ipTrace.valid
  //   trcX.iaddr2    := pu(1).ipTrace.iaddr
  //   trcX.insn2     := pu(1).ipTrace.insn
  //   // trcX.valid2F   := pu(1).ipTrace.valid1F
  //   // trcX.valid1M1  := pu(1).ipTrace.valid1M0
  //   // trcX.valid2M1  := pu(1).ipTrace.valid2M0
  //   //trcX.insn2F    := pu(1).ipTrace.insn1F
  // }

  // Count number of executed instructions.
  val retire   = Vec.fill(NUM_PUS*(2+NUM_MPS)) {Wire(Bool())}
  for(ips <- 0 until NUM_PUS) {
    retire((2+NUM_MPS)*ips+0) := pu(ips).csrUnitIntpipe.retire
    retire((2+NUM_MPS)*ips+1) := pu(ips).csrUnitIntpipe.retireF
    for(mps <- 0 until NUM_MPS) {
      retire((2+NUM_MPS)*ips+2+mps) := pu(ips).csrUnitIntpipe.retireM(mps)
    }
  }
  trcX.numInstr  := PopCount(retire.asUInt())

  //trcX.validRF0  := rf(0).writeToRegfset(0).fire() || rf(0).writeToRegfset(1).fire() || rf(0).writeToRegfset(2).fire() || rf(0).writeToRegfset(3).fire()
  //trcX.isRealRF0 := Mux(rf(0).writeToRegfset(0).fire(), rf(0).writeToRegfset(0).bits.isReal,
  //                      Mux(rf(0).writeToRegfset(1).fire(), rf(0).writeToRegfset(1).bits.isReal,
  //                        Mux(rf(0).writeToRegfset(2).fire(), rf(0).writeToRegfset(2).bits.isReal,
  //                          Mux(rf(0).writeToRegfset(3).fire(), rf(0).writeToRegfset(3).bits.isReal, Bool(false)))))
  //trcX.addrRF0   := Mux(rf(0).writeToRegfset(0).fire(), rf(0).writeToRegfset(0).bits.destReg,
  //                      Mux(rf(0).writeToRegfset(1).fire(), rf(0).writeToRegfset(1).bits.destReg,
  //                        Mux(rf(0).writeToRegfset(2).fire(), rf(0).writeToRegfset(2).bits.destReg,
  //                          Mux(rf(0).writeToRegfset(3).fire(), rf(0).writeToRegfset(3).bits.destReg, UInt(0)))))
  //trcX.rowRF0    := Mux(rf(0).writeToRegfset(0).fire(), rf(0).writeToRegfset(0).bits.ctxtId,
  //                      Mux(rf(0).writeToRegfset(1).fire(), rf(0).writeToRegfset(1).bits.ctxtId,
  //                        Mux(rf(0).writeToRegfset(2).fire(), rf(0).writeToRegfset(2).bits.ctxtId,
  //                          Mux(rf(0).writeToRegfset(3).fire(), rf(0).writeToRegfset(3).bits.ctxtId, UInt(0)))))
  //trcX.dataRF0   := Mux(rf(0).writeToRegfset(0).fire(), rf(0).writeToRegfset(0).bits.dataToWrite,
  //                      Mux(rf(0).writeToRegfset(1).fire(), rf(0).writeToRegfset(1).bits.dataToWrite,
  //                        Mux(rf(0).writeToRegfset(2).fire(), rf(0).writeToRegfset(2).bits.dataToWrite,
  //                          Mux(rf(0).writeToRegfset(3).fire(), rf(0).writeToRegfset(3).bits.dataToWrite, UInt(0)))))


  traceOut      := trcX

  // Pass through various external constants and reports
  if(NUM_PHY_CORES == 1) {
    for(i <- 0 until retireWidth) {
      outer.traceSourceNode.bundle(i) := trcX
    }
  }
  // else {
  //   val traceInExt = Reg(UInt(width=(trcX.asUInt).getWidth))
  //   traceInExt    := traceIn

  //   for(i <- 0 until retireWidth) {
  //     outer.traceSourceNode.bundle(i) := (new TracedInstruction()).fromBits(traceInExt)
  //   }
  // }
  //core.io.traceStall := outer.traceAuxSinkNode.bundle.stall
  //outer.bpwatchSourceNode.bundle <> core.io.bpwatch

  // Rocket has higher priority to DTIM than other TileLink clients
  outer.dtim_adapter.foreach { lm => dcachePorts += lm.module.io.dmem }
}

trait HasFpuOpt { this: RocketTileModuleImp =>
  val fpuOpt = outer.tileParams.core.fpu.map(params => Module(new FPU(params)(outer.p)))
}

