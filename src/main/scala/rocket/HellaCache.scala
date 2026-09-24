// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package freechips.rocketchip.rocket

import Chisel._
import chisel3.dontTouch
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.amba._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.model.OMSRAM
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import scala.collection.mutable.ListBuffer
import superThread._

import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.Util._

case class DCacheParams(
    nSets        : Int = 256,
    nWays        : Int = 8,
    rowBits      : Int = 64,
    nTLBEntries  : Int = 64, // Per ROW.
    tagECC       : Option[String] = None,
    dataECC      : Option[String] = None,
    dataECCBytes : Int = 1,
    nMMIOs       : Int = 2,
    nSDQ         : Int = 4,  // Number of SDQ per MSHR.
    nRPQ         : Int = 8,
    blockBytes   : Int = 64,
    separateUncachedResp: Boolean = false,
    acquireBeforeRelease: Boolean = false,
    pipelineWayMux: Boolean = false,
    clockGate: Boolean = false,
    scratch: Option[BigInt] = None) extends L1CacheParams {

  def tagCode  : Code = Code.fromString(tagECC)
  def dataCode : Code = Code.fromString(dataECC)

  def dataScratchpadBytes: Int = scratch.map(_ => nSets*blockBytes).getOrElse(0)

  def replacement = new RandomReplacement(nWays)

  require((!scratch.isDefined || nWays == 1),
    "Scratchpad only allowed in direct-mapped cache.")
  if (scratch.isEmpty)
    require(isPow2(nSets), s"nSets($nSets) must be pow2")
}

trait HasL1HellaCacheParameters extends HasL1CacheParameters with HasCoreParameters {
  val cacheParams = tileParams.dcache.get
  val cfg     = cacheParams
  val nMSHRs  = L1_NUM_MSHR * NUM_L2BANKS // Total number of MSHRs.
  val nSDQ_DC = ((cfg.nSDQ * nMSHRs) / NumDCCmds) + 1 // Number of SDQ for each DCCmd.

  def wordBits = coreDataBits
  def wordBytes = coreDataBytes
  def wordOffBits = log2Ceil(wordBytes)
  def beatBytes = cacheBlockBytes / cacheDataBeats
  def beatWords = beatBytes / wordBytes
  def beatOffBits = log2Ceil(beatBytes)
  def idxMSB = untagBits-1
  def idxLSB = blockOffBits
  def offsetmsb = idxLSB-1
  def offsetlsb = wordOffBits
  def rowWords = rowBits/wordBits
  def doNarrowRead = coreDataBits * nWays % rowBits == 0
  def eccBytes = cacheParams.dataECCBytes
  val eccBits = cacheParams.dataECCBytes * 8
  val encBits = cacheParams.dataCode.width(eccBits)
  val encWordBits = encBits * (wordBits / eccBits)
  def encDataBits = cacheParams.dataCode.width(coreDataBits) // NBDCache only
  def encRowBits = encDataBits*rowWords
  def lrscCycles = coreParams.lrscCycles // ISA requires 16-insn LRSC sequences to succeed
  def lrscBackoff = 3 // disallow LRSC reacquisition briefly
  def blockProbeAfterGrantCycles = 8 // give the processor some time to issue a request after a grant
  def nIOMSHRs = cacheParams.nMMIOs
  def maxUncachedInFlight = cacheParams.nMMIOs
  def dataScratchpadSize = cacheParams.dataScratchpadBytes

  //  Get number of memories per bank.
  val CACHE_BLOCK_BITS       = cacheBlockBytes * 8
  val NUM_MEMS_PER_BANK      = CACHE_BLOCK_BITS / DATA_LEN
  val NUM_MEMS_PER_BANK_LOG2 = log2Up(NUM_MEMS_PER_BANK)

  // Get number of banks in the system.
  def NUM_BANKS     = nWays * NUM_BANKS_PER_WAY

  // Find the scrambling bit to be used to scramble the tag.
  // This is to relax the issue when multiple ROWs try to access the same tag sequence.
  def tagScramble(addr: UInt) = {
    val scarambleBits   = Reverse(addr(idxBits + untagBits - 1 - log2Up(NUM_BANKS), untagBits))
    Cat(scarambleBits, UInt(0, log2Up(NUM_BANKS)))
    UInt(0, idxBits)
  }

  require(rowBits >= coreDataBits, s"rowBits($rowBits) < coreDataBits($coreDataBits)")
  if (!usingDataScratchpad)
    require(rowBits == cacheDataBits, s"rowBits($rowBits) != cacheDataBits($cacheDataBits)")
  // would need offset addr for puts if data width < xlen
  require(xLen <= cacheDataBits, s"xLen($xLen) > cacheDataBits($cacheDataBits)")
}

abstract class L1HellaCacheModule(implicit val p: Parameters) extends Module
  with HasL1HellaCacheParameters

abstract class L1HellaCacheBundle(implicit val p: Parameters) extends ParameterizedBundle()(p)
  with HasL1HellaCacheParameters

/** Bundle definitions for HellaCache interfaces */
trait HasCoreMemOp extends HasCoreParameters {
  def addrWidth : Int
  val addr = UInt(width = (if(addrWidth >= 0) addrWidth else coreMaxAddrBits))
  val tag  = Bits(width = coreParams.dcacheReqTagBits)
  val cmd  = Bits(width = M_SZ)
  val typ  = Bits(width = MT_SZ)

  val isReal    = Bool()
  val ctxtId    = UInt(width = CTXT_ID_LEN)
  val destReg   = UInt(width = REG_ID_LEN)
  val pipeId    = Bool()
  val changeReg = Bool()
  val needAck   = Bool()
}

trait HasCoreData extends HasCoreParameters {
  val data = Bits(width = coreDataBits)
}

case class HellaCacheReqInternal(addrWidth : Int = -1)(implicit p: Parameters) extends CoreBundle()(p) with HasCoreMemOp with HasL1HellaCacheParameters{
  val phys       = Bool()
  val way_en     = Bits(width = nWays)
  val coh_state  = new ClientMetadata
  val tagWrEna   = Bool()
  val dataAccEna = Bool()
}

class HellaCacheReq(addrWidth : Int = -1)(implicit p: Parameters) extends HellaCacheReqInternal(addrWidth)(p) with HasCoreData

case class HellaCacheResp(addrWidth : Int = -1)(implicit p: Parameters) extends CoreBundle()(p)
    with HasCoreMemOp
    with HasCoreData {
  val replay           = Bool()
  val has_data         = Bool()
  val data_word_bypass = Bits(width = coreDataBits)
  val data_raw         = Bits(width = coreDataBits) // Used by the scratchpad, however it is not connected here.
  val fpuPending       = Bool()
}

class AlignmentExceptions extends Bundle {
  val ld = Bool()
  val st = Bool()
}

class HellaCacheExceptions extends Bundle {
  val ma = new AlignmentExceptions
  val pf = new AlignmentExceptions
  val ae = new AlignmentExceptions
}

class HellaCacheWriteData(implicit p: Parameters) extends CoreBundle()(p) {
  val data = UInt(width = coreDataBits)
  val mask = UInt(width = coreDataBytes)
}

class HellaCachePerfEvents extends Bundle {
  val acquire = Bool()
  val release = Bool()
  val grant = Bool()
  val tlbMiss = Bool()
}

// interface between D$ and processor/DTLB
class HellaCacheIO(addrWidth : Int = -1)(implicit p: Parameters) extends CoreBundle()(p) {
  val req               = Decoupled(new HellaCacheReq(addrWidth))
  val s1_kill           = Bool(OUTPUT) // kill previous cycle's req
  val s1_data           = new HellaCacheWriteData().asOutput // data for previous cycle's req
  val s2_nack           = Bool(INPUT) // req from two cycles ago is rejectedv
  val s2_nack_cause_raw = Bool(INPUT) // reason for nack is store-load RAW hazard (performance hint)
  val s2_kill           = Bool(OUTPUT) // kill req from two cycles ago
  val s2_nack_cause1    = Bool(INPUT) // It is set to one when nack is asserted because mshr is not ready
  val s2_nack_cause2    = Bool(INPUT) // It is set to one when nack is asserted because mshr is not ready
  val s2_nack_cause3    = Bool(INPUT) // It is set to one when nack is asserted because mshr is not ready

  val resp              = Valid(new HellaCacheResp(addrWidth)).flip
  val s2_xcpt           = (new HellaCacheExceptions).asInput
  val ordered           = Bool(INPUT)

  val keep_clock_enabled = Bool(OUTPUT) // should D$ avoid clock-gating itself?
  val clock_enabled = Bool(INPUT) // is D$ currently being clocked?

  override def cloneType = { new HellaCacheIO(addrWidth).asInstanceOf[this.type] }
}

/** Base classes for Diplomatic TL2 HellaCaches */
//Still a twin pair, even though this is not used in an inheritance chain -- rather a HellaCache instance
// is created explicitly inside HasHellaCache trait -- and HasHellaCache trait is at the end of an inheritance chain
// that started inside class RocketTile
abstract class HellaCache(hartid: Int)(implicit p: Parameters) extends LazyModule with HasNonDiplomaticTileParameters {
  protected val cfg = tileParams.dcache.get

  protected def cacheClientParameters = cfg.scratch.map(x => Seq()).getOrElse(Seq(TLMasterParameters.v1(
    name          = s"Core ${hartid} DCache",
    sourceId      = IdRange(0, 1 max (scala.math.ceil(L1_NUM_MSHR.toDouble)).toInt), // This range is for the MSHRs assigned for only a mini-cache.
    supportsProbe = TransferSizes(cfg.blockBytes, cfg.blockBytes))))

  protected def mmioClientParameters = Seq(TLMasterParameters.v1(
    name          = s"Core ${hartid} DCache MMIO",
    sourceId      = IdRange(firstMMIO, firstMMIO + (scala.math.ceil(cfg.nMMIOs/(NUM_L2BANKS).toDouble)).toInt),
    requestFifo   = true))

  def firstMMIO = (cacheClientParameters.map(_.sourceId.end) :+ 0).max

  val node = 0 to NUM_L2BANKS - 1 map { x => TLClientNode(Seq(TLMasterPortParameters.v1(
    clients       = cacheClientParameters ++ mmioClientParameters,
    minLatency    = 1,
    requestFields = tileParams.core.useVM.option(Seq()).getOrElse(Seq(AMBAProtField())))))}

  val module: HellaCacheModule

  def flushOnFenceI = cfg.scratch.isEmpty && !node(0).edges.out(0).manager.managers.forall(m => !m.supportsAcquireT || !m.executable || m.regionType >= RegionType.TRACKED || m.regionType <= RegionType.IDEMPOTENT)

  def canSupportCFlushLine = !usingVM || cfg.blockBytes * cfg.nSets <= (1 << pgIdxBits)

  require(!tileParams.core.haveCFlush || cfg.scratch.isEmpty, "CFLUSH_D_L1 instruction requires a D$")

  def getOMSRAMs(): Seq[OMSRAM] = Nil
}

class HellaCacheBundle(val outer: HellaCache)(implicit p: Parameters) extends CoreBundle()(p) {
  val mshr_busy          = Vec(L1_NUM_MSHR * NUM_L2BANKS, Bool(OUTPUT))
  val hartid             = UInt(INPUT, hartIdLen)
  val ptw                = Vec(NUM_MPS * NUM_PUS, new TLBPTWIO())
  val errors             = new DCacheErrors
  val mpipeDataUnit      = Vec(NUM_PUS * NUM_MPS, new PipeUDataUBundle(ADDR_LEN)).asInput
  val dataUnitToRegfset  = Vec(NUM_PUS, Decoupled(new WriteToRegfsetBundle()))
  val dataUnitToFpu      = Vec(NUM_PUS, Decoupled(new WriteToRegfsetBundle()))
  val fpuPending         = Vec(NUM_PUS, Bool(OUTPUT))
  val dataUnitToCtxt     = Vec(NUM_PUS, new DataUnitToCtxtBundle()).asOutput
  val dataUnitToCsr      = Vec(NUM_PUS * NUM_MPS, new DataUnitToCsrBundle())
  val csrToDataUnit      = Vec(NUM_PUS * NUM_MPS, new CsrToDataUnitBundle()).asInput
  val duReadyCheck       = Vec(NUM_PUS, (new ReadyCheckBundle())).asInput
  val earlyDuWrFlag      = Vec(NUM_PUS, Bool(OUTPUT))
  val unCacheFpuNotif    = Bool(OUTPUT)
  val earlyFpuWrFlag     = Vec(NUM_PUS, Bool(OUTPUT))
  val earlyCtxtFpuWr     = Vec(NUM_PUS, UInt(OUTPUT, CTXT_ID_LEN))
  val dcPorts            = Vec(NUM_PUS, new HellaCacheIO(paddrBits)).flip
  val req_trace_valid    = Vec(NumDCCmds, Bool(OUTPUT))
  val req_trace_addr     = Vec(NumDCCmds, UInt(OUTPUT, paddrBits))
  val req_trace_ctxt     = Vec(NumDCCmds, UInt(OUTPUT, 8))
  val ptwEarlyNotif      = Bool(OUTPUT)

  val cpu                = (new HellaCacheIO(paddrBits)).flip // Only for back comptability.

  // Trace bus
  val duTrace              = new TracedInstruction().asOutput
}

class HellaCacheModule(outer: HellaCache) extends LazyModuleImp(outer)
    with HasL1HellaCacheParameters {
  implicit val edge = outer.node(0).edges.out(0)
  val tl_out = 0 to NUM_L2BANKS - 1 map { x =>
    val (tl_outSingle, _)  = outer.node(x).out(0)
    tl_outSingle
  }

  val io = IO(new HellaCacheBundle(outer))
  dontTouch(io.cpu.resp) // Users like to monitor these fields even if the core ignores some signals
  dontTouch(io.cpu.s1_data)

  private val fifoManagers = edge.manager.managers.filter(TLFIFOFixer.allVolatile)
  fifoManagers.foreach { m =>
    require (m.fifoId == fifoManagers.head.fifoId,
      s"IOMSHRs must be FIFO for all regions with effects, but HellaCache sees\n"+
      s"${m.nodePath.map(_.name)}\nversus\n${fifoManagers.head.nodePath.map(_.name)}")
  }
}

/** Mix-ins for constructing tiles that have a HellaCache */
trait HasHellaCache { this: BaseTile =>
  val module: HasHellaCacheModule
  implicit val p: Parameters
  var nDCachePorts = 0
  lazy val dcache: HellaCache = LazyModule(new NonBlockingDCache(hartId))

  for(i <- 0 until NUM_L2BANKS) {
    dcacheNode(i) := dcache.node(i)
  }
}

trait HasHellaCacheModule {
  val outer: HasHellaCache with HasTileParameters
  val dcachePorts = ListBuffer[HellaCacheIO]()
}

/** Metadata array used for all HellaCaches */

class L1Metadata(implicit p: Parameters) extends L1HellaCacheBundle()(p) {
  val coh = new ClientMetadata
  val tag = UInt(width = tagBits)
}

object L1Metadata {
  def apply(tag: Bits, coh: ClientMetadata)(implicit p: Parameters) = {
    val meta  = Wire(new L1Metadata)
    meta.tag := tag
    meta.coh := coh
    meta
  }
}

class L1MetaRdWrReq(implicit p: Parameters) extends L1HellaCacheBundle()(p) {
  val idx        = UInt(width = idxBits)
  val tag        = UInt(width = tagBits)
  val way_en     = UInt(width = nWays)
  val tagWrEna   = Bool()
  val coh_state  = new ClientMetadata
}

class L1MetaWriteReq(implicit p: Parameters) extends L1MetaRdWrReq()(p)

class QueueRowMIo()(implicit p: Parameters) extends CoreBundle()(p)
{
  val mshrs_ready_ch = Vec(NumDCCmds, Bool(INPUT))
  val prober_ready   = Vec(NUM_L2BANKS, Bool(INPUT))
  val lrsc_ready     = Vec(NumDCCmds, Bool(INPUT))
  val occupCntr      = UInt(OUTPUT, 8)

  val ldStSafeEarly     = Bool(OUTPUT)
  val ldStSafeEarlyArr  = UInt(OUTPUT, NUM_CTXT)
}

class QueuePrioIo(val nRec: Int)(implicit p: Parameters) extends QueueRowMIo()(p)
{
  val enq     = Vec(nRec, Decoupled(new PipeUDataUBundle(pgIdxBits)).flip())
  val deq     = Vec(NumDCCmds, Decoupled(new PipeUDataUBundle(paddrBits)))
  val s1_ppn  = Vec(nRec, UInt(INPUT, ppnBits)) // A registered TLB output.
  val numReqs = Vec(NumDCCmds, UInt(OUTPUT, log2Up(NUM_CTXT+1)))
  val halfSel = Vec(NumDCCmds, Bool(INPUT))

  // Track previous validated memop.
  val valid_prev     = Vec(NumDCCmds, Bool(INPUT))
  val addr_prev      = Vec(NumDCCmds, UInt(INPUT))

  val s2_nack        = Vec(NumDCCmds, Bool(INPUT))
  val nack_cause1    = Vec(NumDCCmds, Bool(INPUT))
  val nack_cause2    = Vec(NumDCCmds, Bool(INPUT))
  val nack_cause3    = Vec(NumDCCmds, Bool(INPUT))
}

class QueueRowIo()(implicit p: Parameters) extends QueueRowMIo()(p)
{
  val enq     = Decoupled(new PipeUDataUBundle(pgIdxBits)).flip()
  val deq     = Decoupled(new PipeUDataUBundle(paddrBits))
  val s1_ppn  = UInt(INPUT, ppnBits) // A registered TLB output.

  val s3_nack        = Bool(INPUT)
  val nack_cause1    = Bool(INPUT)
  val nack_cause2    = Bool(INPUT)
  val nack_cause3    = Bool(INPUT)
  val randFlag       = Bool(INPUT)
}

class QueueRow(val entries: Int)(implicit p: Parameters) extends L1HellaCacheModule()(p)
{
  /** The I/O for this queue */
  val io                = new QueueRowIo()

  // Memories.
  val ram               = Mem(entries, UInt(width = new PipeUDataUBundleWOD(pgIdxBits).getWidth))
  val ramD1             = Mem(entries, UInt(width=DATA_LEN+ppnBits))

  // Registers.
  val wrAddr_reg        = Reg(init=UInt(0,log2Ceil(entries)))
  val rdAddr_reg        = Reg(init=UInt(0,log2Ceil(entries)))
  val rdAddrInc_reg     = Reg(init=UInt(1,log2Ceil(entries))) // Increment by one.
  val rdAddrPrev1_reg   = Reg(init=UInt(0,log2Ceil(entries)))
  val rdAddrPrev2_reg   = Reg(init=UInt(0,log2Ceil(entries)))
  val mayFull_reg       = Reg(init=Bool(false))
  val ldStSafeReqD1_reg = Reg(init=Bool(false))
  val ldStSafeReqD2_reg = Reg(init=Bool(false))
  val ldStSafeReqD3_reg = Reg(init=Bool(false))
  val validFD1_reg      = Reg(init=Bool(false))
  val validD1_reg       = Reg(init=Bool(false))
  val validD2_reg       = Reg(init=Bool(false))
  val validD3_reg       = Reg(init=Bool(false))
  val hlfD1_reg         = Reg(init=UInt(0, log2Up(NUM_L2BANKS)))
  val hlfD2_reg         = Reg(init=UInt(0, log2Up(NUM_L2BANKS)))
  val hlfD3_reg         = Reg(init=UInt(0, log2Up(NUM_L2BANKS)))
  val enqValidD1_reg    = Reg(next=io.enq.fire(), init=Bool(false))
  val enqValidD2_reg    = Reg(next=enqValidD1_reg, init=Bool(false))
  val forwardAddrD1_reg = Reg(init=Bool(false))
  val forwardAddrD2_reg = Reg(next=forwardAddrD1_reg, init=Bool(false))

  val succNacksCntr_reg= Reg(init=UInt(0,3))
  val addRandomWait_reg= Reg(init=Bool(false))
  val nackCause1_reg   = Reg(init=Bool(false))
  val nackCause2_reg   = Reg(init=Bool(false))
  val nackCause3_reg   = Reg(init=Bool(false))
  val nackCause4_reg   = Reg(init=Bool(false))
  val isReal_reg       = Reg(init=Bool(false))
  val isNackedD1_reg   = Reg(init=Bool(false))
  val transInfo_reg    = Reg(init=new PipeUDataUBundleWOD(pgIdxBits).fromBits(UInt(0)))

  // Increment a counter.
  def incCntr(cntr: UInt) = {
    val incN  = Wire(UInt(width=cntr.getWidth))
    incN := cntr + UInt(1)
    if(!isPow2(entries)) {
      when(cntr === UInt(entries - 1)) {
        incN := UInt(0)
      }
    }
    incN
  }
  val wrAddrInc  = incCntr(wrAddr_reg)

  val newInp           = {
    val tmp = Wire(new PipeUDataUBundleWOD(pgIdxBits))
    tmp    := io.enq.bits // Cast the input to the wanted format.
    tmp
  }

  val rdAddrNxt  = Wire(UInt(width=log2Ceil(entries)))
  rdAddrNxt     := rdAddr_reg
  when(io.deq.fire() && !isNackedD1_reg) {
    rdAddrNxt     := rdAddrInc_reg

    when(!(enqValidD1_reg && !forwardAddrD1_reg) && !(enqValidD2_reg && !forwardAddrD2_reg)) {
      mayFull_reg := Bool(false)
    }
  }

  // Update the transaction.
  when(io.deq.fire() || isNackedD1_reg) {
    transInfo_reg := ram(Mux(isNackedD1_reg, rdAddr_reg, rdAddrInc_reg)).asTypeOf(new PipeUDataUBundleWOD(pgIdxBits))
  }

  validFD1_reg   := io.deq.fire()
  validD1_reg    := io.deq.fire()
  validD2_reg    := validD1_reg
  validD3_reg    := validD2_reg
  hlfD1_reg      := io.deq.bits.addr(blockOffBits+log2Up(NUM_L2BANKS)-1, blockOffBits)

  ldStSafeReqD1_reg := isWrite(io.deq.bits.memOp) || isRead(io.deq.bits.memOp) || (io.deq.bits.memOp === M_SFENCE) || (io.deq.bits.memOp === M_FLUSH_ALL)
  ldStSafeReqD2_reg := ldStSafeReqD1_reg
  ldStSafeReqD3_reg := ldStSafeReqD2_reg
  io.ldStSafeEarly  := Reg(next=validD3_reg && !io.s3_nack && ldStSafeReqD3_reg, init=Bool(false))

  io.enq.ready       := Bool(true)
  when(io.enq.fire()) {
    ram(Mux(enqValidD2_reg && io.enq.bits.isReal, wrAddrInc, wrAddr_reg)) := newInp.asUInt
    mayFull_reg     := Bool(true)
  }

  when(enqValidD1_reg) {
    ramD1(wrAddr_reg) := Cat(io.s1_ppn, io.enq.bits.s1_dataToStore) // Register the delayed TLB response and the corresponding data.
  }

  when(enqValidD2_reg && io.enq.bits.isReal) {
    wrAddr_reg        := wrAddrInc
  }
  val transData               = ramD1(rdAddr_reg)
  io.deq.bits                := transInfo_reg
  io.deq.bits.addr           := Cat(Mux(forwardAddrD1_reg, io.s1_ppn, transData(DATA_LEN+ppnBits-1, DATA_LEN)), transInfo_reg.addr(pgIdxBits-1, 0))
  io.deq.bits.s1_dataToStore := transData(DATA_LEN-1, 0)
  io.deq.bits.isReal         := Mux(forwardAddrD2_reg, io.enq.bits.isReal, isReal_reg)
  io.deq.valid               := ((rdAddr_reg =/= wrAddr_reg) || mayFull_reg) && !nackCause1_reg && !nackCause2_reg && !nackCause3_reg && !nackCause4_reg && !isNackedD1_reg

  isReal_reg := Bool(true)
  when(enqValidD2_reg && !io.enq.bits.isReal && (wrAddr_reg === rdAddrNxt)) {
    mayFull_reg    := Bool(false)
  }
  when(enqValidD2_reg && !io.enq.bits.isReal && (wrAddr_reg === rdAddr_reg)) {
    // If there is no other entries, we de-assert isReal_reg as the non-real memop may have been issued.
    isReal_reg     := Bool(false)
    validD1_reg    := Bool(false)
    rdAddrNxt      := wrAddr_reg
  }

  // We early assign the nacked half to be used for clearing.
  hlfD2_reg := hlfD1_reg
  when(validD2_reg) {
    hlfD3_reg := hlfD2_reg
  }

  // Track the address for the just issued transaction. This is used in the case of a future NACK.
  when(io.deq.fire()) {
    rdAddrPrev1_reg := rdAddr_reg
    rdAddrPrev2_reg := rdAddrPrev1_reg
  }

  when((io.deq.fire() || (enqValidD2_reg && !io.enq.bits.isReal && (wrAddr_reg === rdAddr_reg))) && !isNackedD1_reg) {
    rdAddr_reg    := rdAddrNxt
    rdAddrInc_reg := incCntr(rdAddrNxt)
  }

  // Check that the FIFO is empty and a new transaction has been received, so it is forwarded.
  forwardAddrD1_reg := Bool(false)
  when(io.enq.fire() && (rdAddrNxt === Mux(enqValidD2_reg, wrAddrInc, wrAddr_reg))) {
    transInfo_reg     := newInp
    forwardAddrD1_reg := Bool(true)
  }

  // Clear nackCause4_reg after introducing random cycle delay.
  nackCause4_reg := Bool(false)

  when(validD3_reg) {
    when(io.s3_nack && !(io.nack_cause1 || io.nack_cause2 || io.nack_cause3)) {
      // Mark when a memop is nack'd for most probably bank busy.
      succNacksCntr_reg := succNacksCntr_reg + UInt(1)

      when(succNacksCntr_reg >= UInt(3)) {
        addRandomWait_reg := Bool(true)
      }
    }
    .otherwise {
      // The memop has been accepted, clear all NACK history.
      succNacksCntr_reg := UInt(0)
      addRandomWait_reg := Bool(false)
    }
  }

  val rdAddrPrev  = Mux(validFD1_reg, rdAddrPrev2_reg, rdAddrPrev1_reg)
  val isNacked    = validD3_reg && io.s3_nack
  isNackedD1_reg := isNacked
  when(isNacked) {
    nackCause1_reg   := io.nack_cause1
    nackCause2_reg   := io.nack_cause2
    nackCause3_reg   := io.nack_cause3
    mayFull_reg      := Bool(true)
    forwardAddrD1_reg:= Bool(false)
    forwardAddrD2_reg:= Bool(false)

    // Added a random cycle delay if we receive consecutive NACKs.
    nackCause4_reg   := addRandomWait_reg && io.randFlag

    // Revert back the address.
    rdAddr_reg    := rdAddrPrev
    rdAddrInc_reg := incCntr(rdAddrPrev)

    // Ignore what we may just issued.
    isReal_reg      := Bool(false)

    // Disable any memop being issued.
    validD1_reg    := Bool(false)
    validD2_reg    := Bool(false)
  }
  when(isNackedD1_reg) {
    validD1_reg    := Bool(false)
  }

  val mshrs_ready_chD1_reg = (0 until NumDCCmds).map(i => Reg(next=io.mshrs_ready_ch(i), init=Bool(false)))
  val mshrs_ready_chD2_reg = (0 until NumDCCmds).map(i => Reg(next=mshrs_ready_chD1_reg(i), init=Bool(false)))
  val mshrs_ready_chD3_reg = (0 until NumDCCmds).map(i => Reg(next=mshrs_ready_chD2_reg(i), init=Bool(false)))
  val mshrs_ready_chD4_reg = (0 until NumDCCmds).map(i => Reg(next=mshrs_ready_chD3_reg(i), init=Bool(false)))
  val mshrs_ready_chD5_reg = (0 until NumDCCmds).map(i => Reg(next=mshrs_ready_chD4_reg(i), init=Bool(false)))

  val clearFlag  = Wire(Bool())
  val clearFlag0 = Wire(Bool())
  clearFlag0    := mshrs_ready_chD1_reg(0) || mshrs_ready_chD2_reg(0) || mshrs_ready_chD3_reg(0) || mshrs_ready_chD4_reg(0) || mshrs_ready_chD5_reg(0)
  if(NumDCCmds == 1) {
    clearFlag := clearFlag0
  }
  else {
    val clearFlag1  = mshrs_ready_chD1_reg(1) || mshrs_ready_chD2_reg(1) || mshrs_ready_chD3_reg(1) || mshrs_ready_chD4_reg(1) || mshrs_ready_chD5_reg(1)
    clearFlag      := Mux(hlfD3_reg(0), clearFlag1, clearFlag0)
  }
  when(clearFlag) {
    nackCause2_reg := Bool(false)
  }

  when(io.prober_ready(hlfD3_reg)) {
    nackCause3_reg := Bool(false)
  }

  val lrsc_ready = Wire(Bool())
  if(NumDCCmds == 1) {
    lrsc_ready := io.lrsc_ready(0)
  }
  else {
    lrsc_ready := Mux(hlfD3_reg(0), io.lrsc_ready(1), io.lrsc_ready(0))
  }
  when(lrsc_ready) {
    nackCause1_reg := Bool(false)
  }
}

class QueueCmd(val entries: Int)(implicit p: Parameters) extends L1HellaCacheModule()(p)
{
  /** The I/O for this queue */
  val io       = new QueuePrioIo(NUM_MPS)
  val queueRow = 0 to NUM_CTXT - 1 map { x => Module(new QueueRow(entries)).io }

  // To simplify the process, only one selector is used.
  val queueSel = 0 to NumDCCmds - 1 map { x => Module(new SelPrio(NUM_CTXT, (new PipeUDataUBundleWOD(paddrBits)).getWidth)).io }

  val rowSelD1_reg  = Reg(init=Vec.fill(NUM_CTXT){Bool(false)})
  val ducSelD1_reg  = Reg(init=Vec.fill(NUM_CTXT){Bool(false)})
  val ducSelD2_reg  = Reg(init=Vec.fill(NUM_CTXT){Bool(false)})
  val ducSelD3_reg  = Reg(init=Vec.fill(NUM_CTXT){Bool(false)})
  val ldStSafeEarly = Wire(Vec(NUM_CTXT, Bool()))
  val isReady       = Wire(Vec(NumDCCmds,Vec(NUM_CTXT, Bool())))

  // One cycle NACK delay.
  val s3_nack_reg         = Reg(init=Vec.fill(NumDCCmds){Bool(false)})
  val s3_nack_cause1_reg  = Reg(init=Vec.fill(NumDCCmds){Bool(false)})
  val s3_nack_cause2_reg  = Reg(init=Vec.fill(NumDCCmds){Bool(false)})
  val s3_nack_cause3_reg  = Reg(init=Vec.fill(NumDCCmds){Bool(false)})
  s3_nack_reg        := io.s2_nack
  s3_nack_cause1_reg := io.nack_cause1
  s3_nack_cause2_reg := io.nack_cause2
  s3_nack_cause3_reg := io.nack_cause3

  for(DC_ID <- 0 until NumDCCmds) {
    queueSel(DC_ID).holdOn       := io.halfSel(DC_ID)
  }

  // Generate random flag.
  val randFlag    = LFSR16(s3_nack_reg.reduce(_||_))(0,0)

  for(row <- 0 until NUM_CTXT) {
    val mpsX = row / (NUM_CTXT / NUM_MPS)

    queueRow(row).enq         <> io.enq(mpsX)
    queueRow(row).enq.valid   := io.enq(mpsX).valid && (Reg(next=io.enq(mpsX).bits.ctxtIdEarly, init=UInt(0, CTXT_ID_LEN)) === UInt(row))
    queueRow(row).s1_ppn      := io.s1_ppn(mpsX)
    queueRow(row).randFlag    := randFlag
    ldStSafeEarly(row)        := queueRow(row).ldStSafeEarly

    val tmp   = Wire(new PipeUDataUBundleWOD(paddrBits))
    tmp      := queueRow(row).deq.bits
    val sel   = Wire(Bool())
    if(NumDCCmds == 1) {
      // Check if the same bank was accessed in the previous cycle. IGNORE THIS CHECK..
      val freePrev = Bool(true) || !io.valid_prev(0) || (queueRow(row).deq.bits.addr(EN_INDX, ST_INDX) =/= io.addr_prev(0))

      isReady(0)(row)          := queueRow(row).deq.valid
      queueSel(0).isReady(row) := isReady(0)(row) && freePrev
      queueSel(0).tagIn(row)   := tmp.asUInt
      sel                      := queueSel(0).rowSel(row)
    }
    else {
      for(DC_ID <- 0 until NumDCCmds) {
        // Check if the same bank was accessed in the previous cycle. IGNORE THIS CHECK..
        val freePrev = Bool(true) || !io.valid_prev(DC_ID) || (queueRow(row).deq.bits.addr(EN_INDX, ST_INDX) =/= io.addr_prev(DC_ID))

        isReady(DC_ID)(row)          := queueRow(row).deq.valid && (queueRow(row).deq.bits.addr(blockOffBits) === Bool(DC_ID==1))
        queueSel(DC_ID).isReady(row) := isReady(DC_ID)(row) && freePrev
        queueSel(DC_ID).tagIn(row)   := tmp.asUInt
      }
      sel := OrTree(0 to NumDCCmds - 1 map { DC_ID => queueSel(DC_ID).rowSel(row) && !io.halfSel(DC_ID)})
    }
    rowSelD1_reg(row) := sel

    ducSelD1_reg(row) := queueRow(row).deq.bits.addr(blockOffBits)
    ducSelD2_reg(row) := ducSelD1_reg(row)
    ducSelD3_reg(row) := ducSelD2_reg(row)

    // Select s3_nack.
    val s3_nack     = Wire(Bool())
    val nack_cause1 = Wire(Bool())
    val nack_cause2 = Wire(Bool())
    val nack_cause3 = Wire(Bool())
    if(NumDCCmds==1) {
      s3_nack     := s3_nack_reg(0)
      nack_cause1 := io.nack_cause1(0)
      nack_cause2 := io.nack_cause2(0)
      nack_cause3 := io.nack_cause3(0)
    }
    else {
      s3_nack     := Mux(ducSelD3_reg(row), s3_nack_reg(1), s3_nack_reg(0))
      nack_cause1 := Mux(ducSelD3_reg(row), s3_nack_cause1_reg(1), s3_nack_cause1_reg(0))
      nack_cause2 := Mux(ducSelD3_reg(row), s3_nack_cause2_reg(1), s3_nack_cause2_reg(0))
      nack_cause3 := Mux(ducSelD3_reg(row), s3_nack_cause3_reg(1), s3_nack_cause3_reg(0))
    }

    if(NumDCCmds==1) {
      queueRow(row).deq.ready   := rowSelD1_reg(row) && io.deq(0).ready
    }
    else {
      queueRow(row).deq.ready   := rowSelD1_reg(row) && Mux(queueRow(row).deq.bits.addr(blockOffBits), io.deq(1).ready, io.deq(0).ready)
    }

    // Nack flags and status.
    queueRow(row).s3_nack        := s3_nack
    queueRow(row).nack_cause1    := nack_cause1
    queueRow(row).nack_cause2    := nack_cause2
    queueRow(row).nack_cause3    := nack_cause3
    queueRow(row).mshrs_ready_ch := io.mshrs_ready_ch
    queueRow(row).prober_ready   := io.prober_ready
    queueRow(row).lrsc_ready     := io.lrsc_ready
  }

  io.numReqs(0) := PopCount(isReady(0))
  if(NumDCCmds == 2) {
    io.numReqs(1) := PopCount(isReady(1))
  }

  val selId_reg      = Reg(init=Vec.fill(NumDCCmds){UInt(0, log2Up(NUM_CTXT - 1))})
  val selIdD1_reg    = Reg(init=Vec.fill(NumDCCmds){UInt(0, log2Up(NUM_CTXT - 1))})
  val selIdD2_reg    = Reg(init=Vec.fill(NumDCCmds){UInt(0, log2Up(NUM_CTXT - 1))})
  val selIdD3_reg    = Reg(init=Vec.fill(NumDCCmds){UInt(0, log2Up(NUM_CTXT - 1))})
  val deqValid_reg   = Reg(init=Vec.fill(NumDCCmds){Bool(false)})
  val deqValidD1_reg = Reg(init=Vec.fill(NumDCCmds){Bool(false)})
  val deqValidD2_reg = Reg(init=Vec.fill(NumDCCmds){Bool(false)})
  val deqValidD3_reg = Reg(init=Vec.fill(NumDCCmds){Bool(false)})

  val allData  = 0 to NUM_CTXT - 1 map { row => queueRow(row).deq.bits.s1_dataToStore }
  val allReal  = 0 to NUM_CTXT - 1 map { row => queueRow(row).deq.bits.isReal }
  for(DC_ID <- 0 until NumDCCmds) {
    val deqBits_reg        = Reg(init=new PipeUDataUBundle(paddrBits).fromBits(UInt(0)))
    val s1_dataToStore_reg = Reg(init=UInt(0,DATA_LEN))
    val s1_isReal_reg      = Reg(init=Bool(false))

    deqValid_reg(DC_ID) := Bool(false)
    when(queueSel(DC_ID).validSel) {
      deqValid_reg(DC_ID) := Bool(true)
      deqBits_reg         := (new PipeUDataUBundleWOD(paddrBits)).fromBits(queueSel(DC_ID).tagOut)
      selId_reg(DC_ID)    := queueSel(DC_ID).selId
    }

    // Delay.
    selIdD1_reg(DC_ID)    := selId_reg(DC_ID)
    selIdD2_reg(DC_ID)    := selIdD1_reg(DC_ID)
    selIdD3_reg(DC_ID)    := selIdD2_reg(DC_ID)
    deqValidD1_reg(DC_ID) := io.deq(DC_ID).fire()
    deqValidD2_reg(DC_ID) := deqValidD1_reg(DC_ID)
    deqValidD3_reg(DC_ID) := deqValidD2_reg(DC_ID)

    // Check if the same ROW issued another one two or three cycles ago that is nack'd.
    val nackBefore1 = (0 to NumDCCmds - 1 map { x =>  (io.s2_nack(x)  && deqValidD2_reg(x) && (selId_reg(DC_ID) === selIdD2_reg(x)))}).reduce(_||_)
    val nackBefore2 = (0 to NumDCCmds - 1 map { x =>  (s3_nack_reg(x) && deqValidD3_reg(x) && (selId_reg(DC_ID) === selIdD3_reg(x)))}).reduce(_||_)

    when(deqValid_reg(DC_ID)) {
      s1_dataToStore_reg := MuxTree(selId_reg(DC_ID), allData)
      s1_isReal_reg      := MuxTree(selId_reg(DC_ID), allReal) && !nackBefore1 && !nackBefore2
    }

    io.deq(DC_ID).valid               := deqValid_reg(DC_ID)
    io.deq(DC_ID).bits                := deqBits_reg
    io.deq(DC_ID).bits.s1_dataToStore := s1_dataToStore_reg
    io.deq(DC_ID).bits.isReal         := s1_isReal_reg
  }
  io.ldStSafeEarlyArr := ldStSafeEarly.asUInt
}

// Define the I/O ports.
class DataUnitCntrlIo()(implicit p: Parameters) extends CoreBundle()(p)
{
  val ptw                  = Vec(NUM_MPS, new TLBPTWIO())
  val mpipeDataUnit        = Vec(NUM_MPS, new PipeUDataUBundle(ADDR_LEN).asInput)
  val dataUnitToCtxt       = new DataUnitToCtxtBundle().asOutput
  val dataUnitToCsr        = Vec(NUM_MPS, new DataUnitToCsrBundle())
  val csrToDataUnit        = Vec(NUM_MPS, new CsrToDataUnitBundle().asInput)
  val halfSel              = Vec(NumDCCmds, Bool(INPUT))

  val mpipeDataUnitD0      = Vec(NumDCCmds, new PipeUDataUBundle(paddrBits).asOutput)
  val mpipeDataUnitReadyD0 = Vec(NumDCCmds, Bool(INPUT))
  val mshrs_ready_ch       = Vec(NumDCCmds, Bool(INPUT))
  val prober_ready         = Vec(NUM_L2BANKS, Bool(INPUT))
  val lrsc_ready           = Vec(NumDCCmds, Bool(INPUT))
  val dataToStoreD1        = Vec(NumDCCmds, UInt(OUTPUT))

  val s2_nack          = Vec(NumDCCmds, Bool(INPUT))
  val s2_nack_cause1   = Vec(NumDCCmds, Bool(INPUT))
  val s2_nack_cause2   = Vec(NumDCCmds, Bool(INPUT))
  val s2_nack_cause3   = Vec(NumDCCmds, Bool(INPUT))

  // Track previous validated memop.
  val valid_prev       = Vec(NumDCCmds, Bool(INPUT))
  val addr_prev        = Vec(NumDCCmds, UInt(INPUT))

  val stSafeLate       = Bool(INPUT)
  val ctxtIdLate       = UInt(INPUT)

  val stSafeLateUn     = Bool(INPUT)
  val ctxtIdLateUn     = UInt(INPUT)

  // Report the number of active requests.
  val numReqs = Vec(NumDCCmds, UInt(OUTPUT, log2Up(NUM_CTXT+1)))

  // Trace bus
  val duTrace = new TracedInstruction().asOutput

  // A constant input for the ID.
  val constants         = new Bundle {
    val hartid   = UInt(INPUT, hartIdLen)
  }
}

class DataUnitCntrl()(implicit p: Parameters, edge: TLEdgeOut) extends L1HellaCacheModule()(p)
{
  // Define I/O connections.
  val io    = new DataUnitCntrlIo()

  // Define needed registers.
  val stCntr_reg   = Reg(init=Vec.fill(NUM_CTXT){UInt(0, 32)})

  // Queue for the commends.
  val quCmd        = Module(new QueueCmd(DU_QUEUE_DEPTH)).io
  io.numReqs      := quCmd.numReqs

  // address translation.
  // The TLB is connected to the PipeUnit requester only, other requesters do not want translation.
  val tlb = 0 to NUM_MPS - 1 map { x => Module(new TLB(false, log2Ceil(coreDataBytes), TLBConfig(nTLBEntries, 2, 4, NUM_CTXT / NUM_MPS))).io }

  for(mps <- 0 until NUM_MPS) {
    io.ptw(mps)       <> tlb(mps).ptw

    // Check for memory exceptions coming from BP.
    val breakpoint       = Reg(next=(isRead(io.mpipeDataUnit(mps).memOp) && io.csrToDataUnit(mps).bpu_xcpt_ld) || (isWrite(io.mpipeDataUnit(mps).memOp)&& io.csrToDataUnit(mps).bpu_xcpt_st), init=Bool(false))
    val debug_breakpoint = Reg(next=(isRead(io.mpipeDataUnit(mps).memOp) && io.csrToDataUnit(mps).bpu_debug_ld)|| (isWrite(io.mpipeDataUnit(mps).memOp)&& io.csrToDataUnit(mps).bpu_debug_st), init=Bool(false))

    // Early signals.
    tlb(mps).validEarly           := io.mpipeDataUnit(mps).validEarly

    //TODO: MIGRATION: The TLB is needed to be modified to accept an early cycle address; 'Reg(next=' is needed to be removed.
    tlb(mps).req.bits.vaddr       := RegEnable(next=io.mpipeDataUnit(mps).addr, enable=io.mpipeDataUnit(mps).validEarly, init=UInt(0, vaddrBitsExtended))
    for(c <- 0 until NUM_CTXT / NUM_MPS) {
      tlb(mps).req.bits.vaddrCtxt(c) := RegEnable(next=io.mpipeDataUnit(mps).addr(vaddrBits-1, pgIdxBits), enable=io.mpipeDataUnit(mps).validEarly && (io.mpipeDataUnit(mps).ctxtIdEarly === UInt(NUM_CTXT/NUM_MPS*mps+c)), init=UInt(0, vaddrBits-pgIdxBits))
    }

    tlb(mps).req.bits.ctxtId  := Reg(next=io.mpipeDataUnit(mps).ctxtIdEarly, init=UInt(0, CTXT_ID_LEN)) //TODO: MIGRATION: The TLB need to accept an early cycle ctxt
    tlb(mps).ctxtIdEarly      := io.mpipeDataUnit(mps).ctxtIdEarly

    tlb(mps).sfence.valid     := ((io.mpipeDataUnit(mps).memOp === M_SFENCE) && io.mpipeDataUnit(mps).justTlbValid)
    tlb(mps).sfence.bits.rs1  := io.mpipeDataUnit(mps).size(0)
    tlb(mps).sfence.bits.rs2  := io.mpipeDataUnit(mps).size(1)
    tlb(mps).sfence.bits.addr := UInt(0)
    tlb(mps).sfence.bits.asid := UInt(0)

    tlb(mps).req.bits.passthrough := Bool(false)
    tlb(mps).req.bits.size        := io.mpipeDataUnit(mps).size
    tlb(mps).req.bits.cmd         := io.mpipeDataUnit(mps).memOp

    // One cycle delayed.
    tlb(mps).req.valid            := io.mpipeDataUnit(mps).justTlbValid
    tlb(mps).req.bits.isReal      := io.mpipeDataUnit(mps).isReal

    val tlbMissD1  = Wire(Bool())
    tlbMissD1     := tlb(mps).respD1.miss

    if(false) {
      // Verify asserting a lot of TLB misses.
      val randXX    = LFSR16(io.mpipeDataUnit(mps).valid)(0,0)
      tlbMissD1      := tlb(mps).respD1.miss || ((randXX === UInt(0)) && Reg(next=io.mpipeDataUnit(mps).memOp =/= M_SFENCE, init=Bool(false)))
    }

    // Register TLB's output.
    val tlbOutValid_reg = Reg(next=(!tlbMissD1 && Reg(next=io.mpipeDataUnit(mps).isReal, init=Bool(false))), init=Bool(false))
    val xcptMaSt_reg    = Reg(next=tlb(mps).respD1.ma.st, init=Bool(false))
    val xcptMaLd_reg    = Reg(next=tlb(mps).respD1.ma.ld, init=Bool(false))
    val xcptPfSt_reg    = Reg(next=tlb(mps).respD1.pf.st, init=Bool(false))
    val xcptPfLd_reg    = Reg(next=tlb(mps).respD1.pf.ld, init=Bool(false))
    val xcptAeSt_reg    = Reg(next=tlb(mps).respD1.ae.st, init=Bool(false))
    val xcptAeLd_reg    = Reg(next=tlb(mps).respD1.ae.ld, init=Bool(false))

    // Exceptions bus.
    // TODO: MIGRATION: What about ae (access exception) used for PTW access?? PTW access is not connected to the TLB!!
    io.dataUnitToCsr(mps).xcptMaSt         := xcptMaSt_reg && tlbOutValid_reg
    io.dataUnitToCsr(mps).xcptMaLd         := xcptMaLd_reg && tlbOutValid_reg
    io.dataUnitToCsr(mps).xcptPfSt         := xcptPfSt_reg && tlbOutValid_reg
    io.dataUnitToCsr(mps).xcptPfLd         := xcptPfLd_reg && tlbOutValid_reg
    io.dataUnitToCsr(mps).xcptAeSt         := xcptAeSt_reg && tlbOutValid_reg
    io.dataUnitToCsr(mps).xcptAeLd         := xcptAeLd_reg && tlbOutValid_reg
    io.dataUnitToCsr(mps).breakpoint       := breakpoint
    io.dataUnitToCsr(mps).debug_breakpoint := debug_breakpoint

    // Connect the bus to the CSR.
    io.dataUnitToCsr(mps).ctxtIdEarly := Reg(next=io.mpipeDataUnit(mps).ctxtIdEarly, init=UInt(0, CTXT_ID_LEN))
    io.dataUnitToCsr(mps).isRealEarly := Reg(next=io.mpipeDataUnit(mps).isRealEarly, init=Bool(false))
    io.dataUnitToCsr(mps).tlbMiss     := Reg(next=tlbMissD1 && Reg(next=io.mpipeDataUnit(mps).valid && io.mpipeDataUnit(mps).isReal, init=Bool(false)), init=Bool(false))

    // TODO: Safe shall be generated from the TLB.
    io.dataUnitToCsr(mps).tlbSafe     := tlb(mps).req.ready

    // Register the TLB's output.
    quCmd.enq(mps).valid          := io.mpipeDataUnit(mps).valid
    quCmd.enq(mps).bits           := io.mpipeDataUnit(mps)
    quCmd.enq(mps).bits.addr      := tlb(mps).req.bits.vaddr(pgIdxBits-1, 0)
    quCmd.enq(mps).bits.isReal    := tlbOutValid_reg && !xcptMaSt_reg && !xcptMaLd_reg && !xcptPfSt_reg && !xcptPfLd_reg && !xcptAeSt_reg && !xcptAeLd_reg && !io.dataUnitToCsr(mps).breakpoint && !io.dataUnitToCsr(mps).debug_breakpoint
    quCmd.halfSel                 := io.halfSel
    quCmd.s1_ppn(mps)             := tlb(mps).respD1.paddr(paddrBits-1, pgIdxBits)
    assert(!quCmd.enq(mps).valid || quCmd.enq(mps).ready, "DU ERROR: quCmd is not ready.")

    // Report the executed NON-real mem-op.
    val ena1 = io.mpipeDataUnit(mps).valid
    val ena2 = Reg(next=ena1, init=Bool(false))
    val ena3 = Reg(next=ena2, init=Bool(false)) && !quCmd.enq(mps).bits.isReal
    io.dataUnitToCtxt.duCtxtEarlyNotifier3(mps).valid       := Reg(next=ena3, init=Bool(false))
    io.dataUnitToCtxt.duCtxtEarlyNotifier3(mps).ctxtId      := RegEnable(next=RegEnable(next=RegEnable(next=io.mpipeDataUnit(mps).ctxtId, enable=ena1, init=UInt(0, CTXT_ID_LEN)), enable=ena2, init=UInt(0, CTXT_ID_LEN)), enable=ena3, init=UInt(0, CTXT_ID_LEN))
    io.dataUnitToCtxt.duCtxtEarlyNotifier3(mps).destReg     := RegEnable(next=RegEnable(next=RegEnable(next=io.mpipeDataUnit(mps).destReg, enable=ena1, init=UInt(0, REG_ID_LEN)), enable=ena2, init=UInt(0, REG_ID_LEN)), enable=ena3, init=UInt(0, REG_ID_LEN))
    io.dataUnitToCtxt.duCtxtEarlyNotifier3(mps).isLd        := RegEnable(next=RegEnable(next=RegEnable(next=io.mpipeDataUnit(mps).changeReg, enable=ena1, init=Bool(false)), enable=ena2, init=Bool(false)), enable=ena3, init=Bool(false))
    io.dataUnitToCtxt.duCtxtEarlyNotifier3(mps).isSt        := RegEnable(next=RegEnable(next=RegEnable(next=((io.mpipeDataUnit(mps).memOp === M_XWR) || (io.mpipeDataUnit(mps).memOp === M_XSC)), enable=ena1, init=Bool(false)), enable=ena2, init=Bool(false)), enable=ena3, init=Bool(false))
    io.dataUnitToCtxt.duCtxtEarlyNotifier3(mps).isFpuAccess := RegEnable(next=RegEnable(next=RegEnable(next=io.mpipeDataUnit(mps).isFpuAccess, enable=ena1, init=Bool(false)), enable=ena2, init=Bool(false)), enable=ena3, init=Bool(false))

    // After once cycle.
    quCmd.enq(mps).bits.s1_dataToStore := io.mpipeDataUnit(mps).s1_dataToStore

    val intpipeDataD1_reg   = Reg(next=io.mpipeDataUnit(mps), init=new PipeUDataUBundle(ADDR_LEN).fromBits(UInt(0)))
    val intpipeDataD2_reg   = Reg(next=intpipeDataD1_reg, init=new PipeUDataUBundle(ADDR_LEN).fromBits(UInt(0)))
    val addrD2_reg          = Reg(next=tlb(mps).respD1.paddr, init=UInt(0, paddrBits))
    val finishReset_reg     = Reg(init=Bool(false))
    finishReset_reg        := Bool(true)
    if (DEBUG == 2) {
      when(quCmd.enq(mps).bits.isReal && isWrite(intpipeDataD2_reg.memOp) && intpipeDataD2_reg.valid && finishReset_reg) {
        printf("ST%x_%x: %x %x %x\n", io.constants.hartid, intpipeDataD2_reg.ctxtId, stCntr_reg(intpipeDataD2_reg.ctxtId * UInt(NUM_PUS)), addrD2_reg, Reg(next=io.mpipeDataUnit(mps).s1_dataToStore, init=UInt(0, DATA_LEN)))
        stCntr_reg(intpipeDataD2_reg.ctxtId * UInt(NUM_PUS))  := stCntr_reg(intpipeDataD2_reg.ctxtId * UInt(NUM_PUS)) + UInt(1)
      }
    }
  }

  for(DC_ID <- 0 until NumDCCmds) {
    // Connect the dcache with the output of the queue.
    io.mpipeDataUnitD0(DC_ID)       := quCmd.deq(DC_ID).bits
    io.mpipeDataUnitD0(DC_ID).valid := quCmd.deq(DC_ID).valid
    quCmd.deq(DC_ID).ready          := io.mpipeDataUnitReadyD0(DC_ID)

    // Connect the data bus delayed one cycle.
    io.dataToStoreD1(DC_ID)         := quCmd.deq(DC_ID).bits.s1_dataToStore
  }
  quCmd.valid_prev := io.valid_prev
  quCmd.addr_prev  := io.addr_prev

  // Connect the nack status.
  quCmd.s2_nack        := io.s2_nack
  quCmd.nack_cause1    := io.s2_nack_cause1
  quCmd.nack_cause2    := io.s2_nack_cause2
  quCmd.nack_cause3    := io.s2_nack_cause3
  quCmd.mshrs_ready_ch := io.mshrs_ready_ch
  quCmd.prober_ready   := io.prober_ready
  quCmd.lrsc_ready     := io.lrsc_ready

  // Inform CTXT about the release of one FIFO location.
  io.dataUnitToCtxt.ldStSafeEarly   := quCmd.ldStSafeEarlyArr

  io.dataUnitToCtxt.stSafeLate    := io.stSafeLate
  io.dataUnitToCtxt.ctxtIdLate    := io.ctxtIdLate

  io.dataUnitToCtxt.stSafeLateUn  := io.stSafeLateUn
  io.dataUnitToCtxt.ctxtIdLateUn  := io.ctxtIdLateUn

  // Connect the trace bus.
  //io.duTrace.addrVDU0   := Reg(next=tlb(0).req.bits.vaddr, init=UInt(0, vaddrBitsExtended))
  //io.duTrace.addrPDU0   := addrD1_reg
  //io.duTrace.dataDU0    := io.mpipeDataUnit(0).s1_dataToStore
  //io.duTrace.isRealDU0  := quCmd.enq.bits.isReal
  //io.duTrace.ipipDU0    := UInt(ips)
  // io.duTrace.occupCntr  := quCmd.occupCntr

  io.duTrace.ptbrMode  := tlb(0).ptw.ptbr.mode(io.ptw(0).ptbr.mode.getWidth-1)
  io.duTrace.dprv      := tlb(0).ptw.status.dprv
  io.duTrace.ptwValid  := tlb(0).ptw.resp.valid
  io.duTrace.ptwLevel  := tlb(0).ptw.resp.bits.level

  io.duTrace.sfenceVa  := tlb(0).sfence.valid
  io.duTrace.dtlbReady := tlb(0).req.ready
}

