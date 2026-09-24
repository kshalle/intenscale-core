// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import chisel3.withClock
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import scala.collection.mutable.LinkedHashMap
import Instructions._
import superThread._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants.Util._

class MStatus extends Bundle {
  // not truly part of mstatus, but convenient
  val debug   = Bool()
  val cease   = Bool()
  val wfi     = Bool()
  val isa     = UInt(width = 32)

  val dprv    = UInt(width = PRV.SZ) // effective privilege for data accesses
  val prv     = UInt(width = PRV.SZ) // not truly part of mstatus, but convenient
  val sd      = Bool()
  val zero2   = UInt(width = 27)
  val sxl     = UInt(width = 2)
  val uxl     = UInt(width = 2)
  val sd_rv32 = Bool()
  val zero1   = UInt(width = 8)
  val tsr     = Bool()
  val tw      = Bool()
  val tvm     = Bool()
  val mxr     = Bool()
  val sum     = Bool()
  val mprv    = Bool()
  val xs      = UInt(width = 2)
  val fs      = UInt(width = 2)
  val mpp     = UInt(width = 2)
  val vs      = UInt(width = 2)
  val spp     = UInt(width = 1)
  val mpie    = Bool()
  val hpie    = Bool()
  val spie    = Bool()
  val upie    = Bool()
  val mie     = Bool()
  val hie     = Bool()
  val sie     = Bool()
  val uie     = Bool()
}

class DCSR extends Bundle {
  val xdebugver = UInt(width = 2)
  val zero4     = UInt(width=2)
  val zero3     = UInt(width = 12)
  val ebreakm   = Bool()
  val ebreakh   = Bool()
  val ebreaks   = Bool()
  val ebreaku   = Bool()
  val zero2     = Bool()
  val stopcycle = Bool()
  val stoptime  = Bool()
  val cause     = UInt(width = 3)
  val zero1     = UInt(width=3)
  val step      = Bool()
  val prv       = UInt(width = PRV.SZ)
}

class MIP(implicit p: Parameters) extends CoreBundle()(p)
    with HasCoreParameters {
  val lip   = Vec(coreParams.nLocalInterrupts, Bool())
  val zero2 = Bool()
  val debug = Bool() // keep in sync with CSR.debugIntCause
  val zero1 = Bool()
  val dump  = Bool()
  val rocc  = Bool()
  val meip  = Bool()
  val heip  = Bool()
  val seip  = Bool()
  val ueip  = Bool()
  val mtip  = Bool()
  val htip  = Bool()
  val stip  = Bool()
  val utip  = Bool()
  val msip  = Bool()
  val hsip  = Bool()
  val ssip  = Bool()
  val usip  = Bool()
}

class PTBR(implicit p: Parameters) extends CoreBundle()(p) {
  def additionalPgLevels = mode.extract(log2Ceil(pgLevels-minPgLevels+1)-1, 0)
  def pgLevelsToMode(i: Int) = (xLen, i) match {
    case (32, 2) => 1
    case (64, x) if x >= 3 && x <= 6 => x + 5
  }
  val (modeBits, maxASIdBits) = xLen match {
    case 32 => (1, 9)
    case 64 => (4, 16)
  }
  require(modeBits + maxASIdBits + maxPAddrBits - pgIdxBits == xLen)

  val mode = UInt(width = modeBits)
  val asid = UInt(width = maxASIdBits)
  val ppn  = UInt(width = maxPAddrBits - pgIdxBits)
}

object PRV
{
  val SZ = 2
  val U = 0
  val S = 1
  val H = 2
  val M = 3
}

object CSR
{
  // commands
  val SZ = 3
  def X = BitPat.dontCare(SZ)
  def N = UInt(0,SZ)
  def R = UInt(2,SZ)
  def I = UInt(4,SZ)
  def W = UInt(5,SZ)
  def S = UInt(6,SZ)
  def C = UInt(7,SZ)

  // mask a CSR cmd with a valid bit
  def maskCmd(valid: Bool, cmd: UInt): UInt = {
    // all commands less than CSR.I are treated by CSRFile as NOPs
    cmd & ~Mux(valid, 0.U, CSR.I)
  }

  val ADDRSZ = 12
  def busErrorIntCause = 128
  def debugIntCause = 14 // keep in sync with MIP.debug
  def debugTriggerCause = {
    val res = debugIntCause
    require(!(Causes.all contains res))
    res
  }

  val firstCtr   = CSRs.cycle
  val firstCtrH  = CSRs.cycleh
  val firstHPC   = CSRs.hpmcounter3
  val firstHPCH  = CSRs.hpmcounter3h
  val firstHPE   = CSRs.mhpmevent3
  val firstMHPC  = CSRs.mhpmcounter3
  val firstMHPCH = CSRs.mhpmcounter3h
  val firstHPM   = 3
  val nCtr       = 2
  val nHPM       = nCtr - firstHPM
  val hpmWidth   = 40

  val maxPMPs = 16
}

class PerfCounterIO(implicit p: Parameters) extends CoreBundle
    with HasCoreParameters {
  val eventSel = UInt(OUTPUT, xLen)
  val inc = UInt(INPUT, log2Ceil(1+retireWidth))
}

class TracedInstruction extends Bundle {
  val priv       = UInt(width = 3) // A placeholder for compatability.
  val valid      = UInt(width = 4)
  val iaddr      = UInt(width = 40) // coreMaxAddrBits
  val insn       = UInt(width = 32) // iLen

  val valid2     = UInt(width = 4)
  val iaddr2     = UInt(width = 40)
  val insn2      = UInt(width = 32)

  // L3 TL Out
  // val l3Out_valid   = UInt(width = 2) // {tl.d.valid, tl.a.valid}
  // val l3Out_ready   = UInt(width = 2) // {tl.d.ready, tl.a.ready}

  // L3 TL In
  val l3In_a_valid        = UInt(width = 4)
  val l3In_d_valid        = UInt(width = 4)
  val l3In_a_ready        = UInt(width = 4)
  val l3In_d_ready        = UInt(width = 4)

  val l3In_a_address      = UInt(width = 35)
  val l3In_a_source       = UInt(width = 6)
  val l3In_d_source       = UInt(width = 6)

  // L2 TL In
  val l2In_a_valid        = UInt(width = 2)
  val l2In_d_valid        = UInt(width = 2)
  val l2In_a_ready        = UInt(width = 2)
  val l2In_d_ready        = UInt(width = 2)

  val l2In_a_address      = UInt(width = 35)
  val l2In_a_source       = UInt(width = 6)
  val l2In_d_source       = UInt(width = 6)

  // L3MemSysxBar TL Out
  val l3MemSys_valid      = UInt(width = 2) // {tl.d.valid, tl.a.valid}
  val l3MemSys_ready      = UInt(width = 2) // {tl.d.ready, tl.a.ready}
  
  val l3MemSys_a_address  = UInt(width = 35)
  val l3MemSys_a_source   = UInt(width = 4)
  val l3MemSys_d_source   = UInt(width = 4)
  
  // ECCDramCombXbar TL Out
  val eccDRAM_valid       = UInt(width = 2) // {tl.d.valid, tl.a.valid}
  val eccDRAM_ready       = UInt(width = 2) // {tl.d.ready, tl.a.ready}

  val eccDRAM_a_address   = UInt(width = 35)
  val eccDRAM_a_source    = UInt(width = 7)
  val eccDRAM_d_source    = UInt(width = 7)
  
  val dummy               = UInt(width = 15) // adjust to complete 512 bits <= checked from generated Verilog
  

  // val valid1F    = UInt(width = 4)
  //val insn1F     = UInt(width = 32)
  // val valid2F    = UInt(width = 4)
  //val insn2F     = UInt(width = 32)
  // val valid1M0   = UInt(width = 4)
  // val valid1M1   = UInt(width = 4)
  // val valid2M0   = UInt(width = 4)
  // val valid2M1   = UInt(width = 4)

  val exception    = Bool()
  val interrupt    = Bool()
  val cause        = Bool()
  val tval         = Bool()

  // val occupCntr  = UInt(width = 8)
  // val statusDU0  = UInt(width = 16+40)
  //val addrVDU0   = UInt(width = 40)
  //val addrPDU0   = UInt(width = 32)
  //val dataDU0    = UInt(width = 64)

  // val statusDU1  = UInt(width = 16+40)
  //val addrVDU1   = UInt(width = 40)
  //val addrPDU1   = UInt(width = 32)
  //val dataDU1    = UInt(width = 64)

  //val ipipDU0   = Bool()
  //val isRealDU0 = Bool()

  //val validRF0  = Bool()
  //val isRealRF0 = Bool()
  //val rowRF0    = UInt(width = 3)
  //val addrRF0   = UInt(width = 5)
  //val dataRF0   = UInt(width = 64)

  // val ptwStatus   = UInt(width = 4)
  // val ptwCtxtId   = UInt(width = 4)
  // val ptwVAddr    = UInt(width = 28)
  // val ptwPAddr    = UInt(width = 24)
  // val ptwFenceC   = UInt(width = 4)
  // val ptwFenceA   = UInt(width = 28)

  val ptbrMode   = Bool()
  val dprv       = UInt(width = 3)
  val ptwLevel   = UInt(width = 4)
  val ptwValid   = Bool()
  val sfenceVa   = Bool()
  val dtlbReady  = Bool()
  val ptwReady   = Bool()

  val iptwValid   = Bool()
  val itlbReady   = Bool()
  val icacheReady = Bool()
  val icacheValid = Bool()

  val tlbCtxtId   = UInt(width = 4)
  val vInstrAddr  = UInt(width = 40)

  val icacheState = UInt(width = 4)
  val pInstrAddr  = UInt(width = 32)

  //val hazardStatus0 = UInt(width = 16)
  //val hazardStatus1 = UInt(width = 16)
  //val hazardStatus2 = UInt(width = 16)

  // Do not change position of the below signals.
  val hazardStatus3 = UInt(width = 16)

  val numInstr  = UInt(width = 8)
  override def cloneType = { new TracedInstruction().asInstanceOf[this.type] }
}

class TraceAux extends Bundle {
  val enable = Bool()
  val stall  = Bool()
}

class CSRDecodeIO extends Bundle {
  val idc_addr       = UInt(INPUT, CSR.ADDRSZ)
  val fp_illegal     = Bool(OUTPUT)
  val vector_illegal = Bool(OUTPUT)
  val fp_csr         = Bool(OUTPUT)
  val rocc_illegal   = Bool(OUTPUT)
  val read_illegal   = Bool(OUTPUT)
  val write_illegal  = Bool(OUTPUT)
  val write_flush    = Bool(OUTPUT)
  val system_illegal = Bool(OUTPUT)
}

class CSRFileIO(implicit p: Parameters) extends CoreBundle
    with HasCoreParameters {
  val ungated_clock   = Clock().asInput
  val csrUnitCtxtUnit = new CsrUnitCtxtUnitBundle().asOutput

  val interrupts      = new CoreInterrupts().asInput
  val hartid          = UInt(INPUT, hartIdLen)
  val rw = new Bundle {
    val ex2_addr     = UInt(INPUT, CSR.ADDRSZ)
    val ex2_cmd      = Bits(INPUT, CSR.SZ)
    val wdata        = Bits(INPUT, xLen)
    val rdata        = Bits(OUTPUT, xLen)
  }
  val wb_addr        = Bits(INPUT, vaddrBitsExtended)

  val decode             = Vec(1, new CSRDecodeIO)
  val idc_sel            = Bool(INPUT)

  val singleStep         = Bool(OUTPUT)

  val mem_csr_ena        = Bool(INPUT)
  val csr_flowAlter      = Bool(OUTPUT)

  val status             = new MStatus().asOutput
  val ptbr               = new PTBR().asOutput
  val mem_exception      = Bool(INPUT)
  val retire             = UInt(INPUT, log2Up(1+retireWidth))
  val cause              = UInt(INPUT, xLen)
  val pc                 = UInt(INPUT, vaddrBitsExtended)
  val pcD1               = UInt(INPUT, vaddrBitsExtended)
  val mem_interruptAck   = Bool(INPUT)
  val fcsr_rm            = Bits(OUTPUT, FPConstants.RM_SZ)
  val fcsr_flags         = Valid(Bits(width = FPConstants.FLAGS_SZ)).flip
  val set_fs_dirty       = coreParams.haveFSDirty.option(Bool(INPUT))
  val rocc_interrupt     = Bool(INPUT)
  val interrupt          = Bool(OUTPUT)
  val interrupt_cause    = UInt(OUTPUT, xLen)
  val bp              = Vec(nBreakpoints, new BP).asOutput
  val pmp             = Vec(nPMPs, new PMP).asOutput
  val counters        = Vec(nPerfCounters, new PerfCounterIO)

  // Dump interrupt flag.
  val sys_dump           = Bool(INPUT)

  // Cycle number.
  val cycleId            = UInt(INPUT, 64)

  // Exceptions received from the data unit.
  val flagsValid         = Bool(INPUT)
  val tlbMiss            = Bool(INPUT)
  val tlbSafe            = Bool(INPUT)
  val xcptMaSt           = Bool(INPUT)
  val xcptMaLd           = Bool(INPUT)
  val xcptPfSt           = Bool(INPUT)
  val xcptPfLd           = Bool(INPUT)
  val xcptAeSt           = Bool(INPUT)
  val xcptAeLd           = Bool(INPUT)
  val breakpoint         = Bool(INPUT)
  val debug_breakpoint   = Bool(INPUT)
  val exx_muteInstr      = Bool(OUTPUT)
  val pendIntrFlag       = UInt(OUTPUT, 6)

  val vector = usingVector.option(new Bundle {
    val vconfig = new VConfig().asOutput
    val vstart = UInt(maxVLMax.log2.W).asOutput
    val vxrm = UInt(2.W).asOutput
    val set_vs_dirty = Input(Bool())
    val set_vconfig = Valid(new VConfig).flip
    val set_vstart = Valid(vstart).flip
    val set_vxsat = Bool().asInput
  })
}

class VConfig(implicit p: Parameters) extends CoreBundle {
  val vl = UInt((maxVLMax.log2 + 1).W)
  val vtype = new VType
}

object VType {
  private def fromUInt(that: UInt, ignore_vill: Boolean)(implicit p: Parameters): VType = {
    val res = 0.U.asTypeOf(new VType)
    val in = that.asTypeOf(res)
    res.vill := (in.max_vsew < in.vsew) || in.reserved =/= 0 || in.vill
    when (!res.vill || ignore_vill) {
      res.vsew := in.vsew(log2Ceil(1 + in.max_vsew) - 1, 0)
      res.vlmul := in.vlmul
    }
    res
  }

  def fromUInt(that: UInt)(implicit p: Parameters): VType = fromUInt(that, false)

  def computeVL(avl: UInt, vtype: UInt, currentVL: UInt, useCurrentVL: Bool, useMax: Bool, useZero: Bool)(implicit p: Parameters): UInt =
    VType.fromUInt(vtype, true).vl(avl, currentVL, useCurrentVL, useMax, useZero)
}

class VType(implicit p: Parameters) extends CoreBundle {
  val vill = Bool()
  val reserved = UInt((xLen - 6).W)
  val vsew = UInt(3.W)
  val vlmul = UInt(2.W)

  val max_vsew = log2Ceil(eLen/8)

  def minVLMax = maxVLMax / eLen
  def vlMax: UInt = (maxVLMax >> (this.vsew +& ~this.vlmul)).andNot(minVLMax-1)
  def vlMaxInBytes: UInt = maxVLMax >> ~this.vlmul

  def vl(avl: UInt, currentVL: UInt, useCurrentVL: Bool, useMax: Bool, useZero: Bool): UInt = {
    val atLeastMaxVLMax = useMax || Mux(useCurrentVL, currentVL >= maxVLMax, avl >= maxVLMax)
    val avl_lsbs = Mux(useCurrentVL, currentVL, avl)(maxVLMax.log2 - 1, 0)

    val atLeastVLMax = atLeastMaxVLMax || (avl_lsbs & (-maxVLMax.S >> (this.vsew +& ~this.vlmul)).asUInt.andNot(minVLMax-1)).orR
    val isZero = vill || useZero
    Mux(!isZero && atLeastVLMax, vlMax, 0.U) | Mux(!isZero && !atLeastVLMax, avl_lsbs, 0.U)
  }
}

class CSRFile(
  perfEventSets: EventSets = new EventSets(Seq()),
  customCSRs: Seq[CustomCSR] = Nil)(implicit p: Parameters)
    extends CoreModule()(p)
    with HasRocketCoreParameters {
  val io = new CSRFileIO {
    val customCSRs = Vec(CSRFile.this.customCSRs.size, new CustomCSRIO).asOutput
  }

  val reset_mstatus = Wire(init=new MStatus().fromBits(0))
  reset_mstatus.mpp := PRV.M
  reset_mstatus.prv := PRV.M
  val reg_mstatus    = Reg(init=reset_mstatus)

  val new_prv      = Wire(init = reg_mstatus.prv)
  reg_mstatus.prv := legalizePrivilege(new_prv)

  val reset_dcsr = Wire(init=new DCSR().fromBits(0))
  reset_dcsr.xdebugver := 1
  reset_dcsr.prv := PRV.M
  val reg_dcsr = Reg(init=reset_dcsr)

  val (supported_interrupts, delegable_interrupts) = {
    val sup = Wire(new MIP)
    sup.usip  := false
    sup.ssip  := Bool(usingSupervisor)
    sup.hsip  := false
    sup.msip  := true
    sup.utip  := false
    sup.stip  := Bool(usingSupervisor)
    sup.htip  := false
    sup.mtip  := true
    sup.ueip  := false
    sup.seip  := Bool(usingSupervisor)
    sup.heip  := false
    sup.meip  := true
    sup.rocc  := usingRoCC
    sup.zero1 := false
    sup.debug := false
    sup.zero2 := false
    sup.lip foreach { _ := true }
    val supported_high_interrupts = if (io.interrupts.buserror.nonEmpty) UInt(BigInt(1) << CSR.busErrorIntCause) else 0.U

    val del = Wire(init=sup)
    del.msip := false
    del.mtip := false
    del.meip := false

    (sup.asUInt | supported_high_interrupts, del.asUInt)
  }
  val delegable_exceptions = UInt(Seq(
    Causes.misaligned_fetch,
    Causes.fetch_page_fault,
    Causes.breakpoint,
    Causes.load_page_fault,
    Causes.store_page_fault,
    Causes.misaligned_load,
    Causes.misaligned_store,
    Causes.illegal_instruction,
    Causes.user_ecall).map(1 << _).sum)

  // Debug control.
  val reg_deb_flag      = Reg(init=Bool(false))
  val reg_debug         = Reg(init=Bool(false))
  val reg_dpc           = Reg(UInt(width = vaddrBitsExtended))
  val reg_dscratch      = Reg(UInt(width = xLen))
  val reg_singleStepped = Reg(init=Bool(false))

  // Breakpoint control.
  val reg_tselect       = Reg(UInt(width = log2Up(nBreakpoints)))
  val reg_bp            = 0 until (1 << log2Up(nBreakpoints)) map {x => Reg(init=(new BP).fromBits(0))}
  val reg_pmp           = 0 until nPMPs map {x => Reg(init=(new PMPReg).fromBits(0))}

  val reset_mip           = Wire(init=new MIP().fromBits(0))
  val reg_mip             = Reg(init=reset_mip)

  // Maximum number of supported exceptions and interrupts.
  val NUM_XCPT_INTR  = reg_mip.getWidth

  val reg_mie             = Reg(init=UInt(0, NUM_XCPT_INTR))
  val (reg_mideleg, read_mideleg) = {
    val reg = Reg(UInt(NUM_XCPT_INTR.W))
    (reg, Mux(usingSupervisor, reg & delegable_interrupts, 0.U))
  }
  val (reg_medeleg, read_medeleg) = {
    val reg = Reg(UInt(NUM_XCPT_INTR.W))
    (reg, Mux(usingSupervisor, reg & delegable_exceptions, 0.U))
  }
  val reg_mepc            = Reg(init=UInt(0, vaddrBitsExtended))
  val reg_mcause          = Reg(init=UInt(0, xLen))
  val reg_mtval           = Reg(init=UInt(0, vaddrBitsExtended))
  val reg_mscratch        = Reg(init=UInt(0, xLen))
  val mtvecWidth          = paddrBits min xLen
  val reg_mtvec           = mtvecInit match {
    case Some(addr) => Reg(init=UInt(addr, mtvecWidth))
    case None => Reg(UInt(width = mtvecWidth))
  }
  val tlbReady_reg           = Reg(init=Bool(true))
  val muteCntr_reg           = Reg(init=UInt(0, 4))
  val muteP_reg              = Reg(init=Bool(false))
  val muteD2_reg             = Reg(init=Bool(false))
  val isXcptD1_reg           = Reg(init=Bool(false))
  val xcptMaStD1_reg         = Reg(init=Bool(false))
  val xcptMaLdD1_reg         = Reg(init=Bool(false))
  val xcptPfStD1_reg         = Reg(init=Bool(false))
  val xcptPfLdD1_reg         = Reg(init=Bool(false))
  val xcptAeStD1_reg         = Reg(init=Bool(false))
  val xcptAeLdD1_reg         = Reg(init=Bool(false))
  val breakpointD1_reg       = Reg(init=Bool(false))
  val debug_breakpointD1_reg = Reg(init=Bool(false))
  val tlbMissD1_reg          = Reg(init=Bool(false))
  val csrFlowPc_reg          = Reg(init=UInt(0, ADDR_LEN))
  val interruptInProg_reg    = Reg(init=Bool(false))
  val waitActiveInst_reg     = Reg(init=Bool(false))
  val interrupt_cause_reg    = Reg(init=UInt(0, xLen))
  val enaDump_reg            = Reg(init=Bool(true))

  val delegable_counters = ((BigInt(1) << (nPerfCounters + CSR.firstHPM)) - 1).U
  val (reg_mcounteren, read_mcounteren) = {
    val reg = Reg(UInt(32.W))
    (reg, Mux(usingUser, reg & delegable_counters, 0.U))
  }
  val (reg_scounteren, read_scounteren) = {
    val reg = Reg(UInt(32.W))
    (reg, Mux(usingSupervisor, reg & delegable_counters, 0.U))
  }

  val reg_sepc       = Reg(init=UInt(0, vaddrBitsExtended))
  val reg_scause     = Reg(init=UInt(0, xLen))
  val reg_stval      = Reg(UInt(width = vaddrBitsExtended))
  val reg_sscratch   = Reg(init=UInt(0, xLen))
  val reg_stvec      = Reg(init=UInt(0, vaddrBits))
  val reg_satp       = Reg(new PTBR)
  val reg_wfi        = withClock(io.ungated_clock) { Reg(init=Bool(false)) }
  val reg_isWait     = Reg(init=Bool(false))
  val reg_dontInt    = Reg(init=Bool(false))
  val reg_wait_cntr  = Reg(init=UInt(0, 16))
  val dbgDisable_reg = Reg(init=Bool(false))

  val reg_fflags         = Reg(init=UInt(0, 5))
  val reg_frm            = Reg(init=UInt(0, 3))
  val reg_vconfig        = usingVector.option(Reg(init={
    val r = new VConfig().fromBits(0)
    r.vtype.vill := true
    r
  }))
  val reg_vstart         = usingVector.option(Reg(UInt(maxVLMax.log2.W)))
  val reg_vxsat          = usingVector.option(Reg(init=Bool(false)))
  val reg_vxrm           = usingVector.option(Reg(UInt(io.vector.get.vxrm.getWidth.W)))

  val retireD1_reg       = Reg(next=io.retire, init=UInt(0))
  val reg_instret1       = WideCounter(64, Mux(muteD2_reg, UInt(0), retireD1_reg(log2Ceil(1+retireWidth)-1, 0)))
  val reg_instret        = Reg(next=reg_instret1.value, init=UInt(0))

  val reg_hpmevent = io.counters.map(c => Reg(init = UInt(0, xLen)))
  (io.counters zip reg_hpmevent) foreach { case (c, e) => c.eventSel := e }
  val reg_hpmcounter = io.counters.map(c => WideCounter(CSR.hpmWidth, c.inc, reset = false))

  val interruptsD1_reg  = Reg(next=io.interrupts, init=new CoreInterrupts().fromBits(UInt(0)))
  val mip               = Wire(init=reg_mip)
  mip.lip              := (interruptsD1_reg.lip: Seq[Bool])
  mip.mtip             := interruptsD1_reg.mtip
  mip.msip             := interruptsD1_reg.msip
  mip.meip             := interruptsD1_reg.meip
  // seip is the OR of reg_mip.seip and the actual line from the PLIC
  interruptsD1_reg.seip.foreach { mip.seip := reg_mip.seip || _ }
  mip.dump     := io.sys_dump && enaDump_reg
  mip.rocc     := io.rocc_interrupt
  val read_mip  = mip.asUInt & supported_interrupts
  val high_interrupts = interruptsD1_reg.buserror.map(_ << CSR.busErrorIntCause).getOrElse(0.U)

  val pending_interrupts = high_interrupts | (read_mip & reg_mie)
  val d_interrupts = Mux(dbgDisable_reg, UInt(0), interruptsD1_reg.debug << CSR.debugIntCause)
  val m_interrupts = Mux(reg_mstatus.prv <= PRV.S || reg_mstatus.mie, ~(~pending_interrupts | read_mideleg), UInt(0))
  val s_interrupts = Mux(reg_mstatus.prv < PRV.S || (reg_mstatus.prv === PRV.S && reg_mstatus.sie), pending_interrupts & read_mideleg, UInt(0))
  val (anyInterrupt, whichInterrupt) = chooseInterrupt(Seq(s_interrupts, m_interrupts, d_interrupts))
  val mem_exceptionG     = io.mem_exception && !muteD2_reg
  io.pendIntrFlag       := Cat(reg_mstatus.mie, reg_mstatus.sie, (m_interrupts =/= UInt(0)), (s_interrupts =/= UInt(0)), (read_mip =/= UInt(0)), (reg_mie =/= UInt(0)))

  val interruptMSB       = BigInt(1) << (xLen-1)
  val raiseInt           = ((anyInterrupt && !(reg_debug || io.status.cease) && (!io.singleStep || reg_singleStepped)))

  val ex1_sel_reg        = Reg(next=io.idc_sel, init=Bool(false))
  val ex2_sel_reg        = Reg(next=ex1_sel_reg, init=Bool(false))
  val ex1_addr_reg       = RegEnable(io.decode(0).idc_addr, io.idc_sel)
  val ex2_addr_reg       = RegEnable(ex1_addr_reg, ex1_sel_reg)

  // Wait for some time to make sure all instructions are slowed down.
  val intCntr_reg    = Reg(init=UInt(0, 3))
  when(intCntr_reg > UInt(0)) {
    intCntr_reg := intCntr_reg - UInt(1)
  }

  when(interruptInProg_reg && io.mem_interruptAck && waitActiveInst_reg) {
    // 3- Receive the second ACK.
    // An exception corresponding to the sent interrupt is received.
    interruptInProg_reg := Bool(false)
    interrupt_cause_reg := UInt(0)
    waitActiveInst_reg  := Bool(false)

    // De-assert enaDump_reg.
    when(interrupt_cause_reg(xLen-2, 0) === UInt(14)) {
      enaDump_reg := Bool(false)
    }
  }
  when(interruptInProg_reg && (intCntr_reg === UInt(0)) && !waitActiveInst_reg) {
    // 2- Receive the first ACK.
    waitActiveInst_reg  := Bool(true)
  }
  when(raiseInt && !interruptInProg_reg) {
    // 1- An interrupt is received.
    interruptInProg_reg   := Bool(true)
    intCntr_reg           := UInt(7)
    interrupt_cause_reg   := UInt(interruptMSB) + whichInterrupt
  }
  when(interruptInProg_reg && (!raiseInt || muteD2_reg)) {
    // De-assert the interrupt if anyInterrupt was de-asserted for any reason.
    // or the previous instruction was muted.
    interruptInProg_reg := Bool(false)
    waitActiveInst_reg  := Bool(false)
  }
  io.interrupt       := waitActiveInst_reg
  io.interrupt_cause := interrupt_cause_reg
  io.bp              := reg_bp take nBreakpoints
  io.pmp             := reg_pmp.map(PMP(_))

  val isaMaskString =
    (if (usingMulDiv) "M" else "") +
    (if (usingAtomics) "A" else "") +
    (if (fLen >= 32) "F" else "") +
    (if (fLen >= 64) "D" else "") +
    (if (usingVector) "V" else "") +
    (if (usingCompressed) "C" else "")
  val isaString = (if (coreParams.useRVE) "E" else "I") +
    isaMaskString +
    "X" + // Custom extensions always present (e.g. CEASE instruction)
    (if (usingSupervisor) "S" else "") +
    (if (usingUser) "U" else "")
  val isaMax = (BigInt(log2Ceil(xLen) - 4) << (xLen-2)) | isaStringToMask(isaString)
  val reg_misa = Reg(init=UInt(isaMax))
  val read_mstatus = io.status.asUInt()(xLen-1,0)
  val read_mtvec = formTVec(reg_mtvec).padTo(xLen)
  val read_stvec = formTVec(reg_stvec).sextTo(xLen)

  val read_mapping = LinkedHashMap[Int,Bits](
    CSRs.tselect          -> reg_tselect,
    CSRs.tdata1           -> reg_bp(reg_tselect).control.asUInt,
    CSRs.tdata2           -> reg_bp(reg_tselect).address.sextTo(xLen),
    CSRs.misa             -> reg_misa,
    CSRs.mstatus          -> read_mstatus,
    CSRs.mtvec            -> read_mtvec,
    CSRs.mip              -> read_mip,
    CSRs.mie              -> reg_mie,
    CSRs.mscratch         -> reg_mscratch,
    CSRs.mepc             -> readEPC(reg_mepc).sextTo(xLen),
    CSRs.mtval            -> reg_mtval.sextTo(xLen),
    CSRs.mcause           -> reg_mcause,
    CSRs.mhartid          -> io.hartid,
    CSRs.mhartidX         -> io.hartid,
    CSRs.ctxt_wait        -> UInt(0),
    CSRs.dbgDisable       -> dbgDisable_reg)

  val debug_csrs = if (!usingDebug) LinkedHashMap() else LinkedHashMap[Int,Bits](
    CSRs.dcsr     -> reg_dcsr.asUInt,
    CSRs.dpc      -> readEPC(reg_dpc).sextTo(xLen),
    CSRs.dscratch -> reg_dscratch.asUInt)

  val read_fcsr = Cat(reg_frm, reg_fflags)
  val fp_csrs = LinkedHashMap[Int,Bits]() ++
    usingFPU.option(CSRs.fflags -> reg_fflags) ++
    usingFPU.option(CSRs.frm -> reg_frm) ++
    (usingFPU || usingVector).option(CSRs.fcsr -> read_fcsr)

  val read_vcsr = Cat(reg_vxrm.getOrElse(0.U), reg_vxsat.getOrElse(0.U))
  val vector_csrs = if (!usingVector) LinkedHashMap() else LinkedHashMap[Int,Bits](
    CSRs.vxsat -> reg_vxsat.get,
    CSRs.vxrm -> reg_vxrm.get,
    CSRs.vcsr -> read_vcsr,
    CSRs.vstart -> reg_vstart.get,
    CSRs.vtype -> reg_vconfig.get.vtype.asUInt,
    CSRs.vl -> reg_vconfig.get.vl,
    CSRs.vlenb -> (vLen / 8).U)

  read_mapping ++= debug_csrs
  read_mapping ++= fp_csrs
  read_mapping ++= vector_csrs

  if (coreParams.haveBasicCounters) {
    read_mapping += CSRs.mcycle -> io.cycleId
    read_mapping += CSRs.minstret -> reg_instret

    for (((e, c), i) <- (reg_hpmevent.padTo(CSR.nHPM, UInt(0))
                         zip reg_hpmcounter.map(x => x: UInt).padTo(CSR.nHPM, UInt(0))) zipWithIndex) {
      read_mapping += (i + CSR.firstHPE) -> e // mhpmeventN
      read_mapping += (i + CSR.firstMHPC) -> c // mhpmcounterN
      if (usingUser) read_mapping += (i + CSR.firstHPC) -> c // hpmcounterN
      if (xLen == 32) {
        read_mapping += (i + CSR.firstMHPCH) -> (c >> 32) // mhpmcounterNh
        if (usingUser) read_mapping += (i + CSR.firstHPCH) -> (c >> 32) // hpmcounterNh
      }
    }

    if (usingUser) {
      read_mapping += CSRs.mcounteren -> read_mcounteren
      read_mapping += CSRs.cycle -> io.cycleId
      read_mapping += CSRs.instret -> reg_instret
    }

    if (xLen == 32) {
      read_mapping += CSRs.mcycleh -> (io.cycleId >> 32)
      read_mapping += CSRs.minstreth -> (reg_instret >> 32)
      if (usingUser) {
        read_mapping += CSRs.cycleh -> (io.cycleId >> 32)
        read_mapping += CSRs.instreth -> (reg_instret >> 32)
      }
    }
  }

  if (usingSupervisor) {
    val read_sie     = reg_mie & read_mideleg
    val read_sip     = read_mip & read_mideleg
    val read_sstatus = Wire(init = 0.U.asTypeOf(new MStatus))
    read_sstatus.sd      := io.status.sd
    read_sstatus.uxl     := io.status.uxl
    read_sstatus.sd_rv32 := io.status.sd_rv32
    read_sstatus.mxr     := io.status.mxr
    read_sstatus.sum     := io.status.sum
    read_sstatus.xs      := io.status.xs
    read_sstatus.fs      := io.status.fs
    read_sstatus.vs      := io.status.vs
    read_sstatus.spp     := io.status.spp
    read_sstatus.spie    := io.status.spie
    read_sstatus.sie     := io.status.sie

    read_mapping += CSRs.sstatus         -> (read_sstatus.asUInt())(xLen-1,0)
    read_mapping += CSRs.sip             -> read_sip.asUInt
    read_mapping += CSRs.sie             -> read_sie.asUInt
    read_mapping += CSRs.sscratch        -> reg_sscratch
    read_mapping += CSRs.scause          -> reg_scause
    read_mapping += CSRs.stval           -> reg_stval.sextTo(xLen)
    read_mapping += CSRs.satp            -> reg_satp.asUInt
    read_mapping += CSRs.sepc            -> readEPC(reg_sepc).sextTo(xLen)
    read_mapping += CSRs.stvec           -> read_stvec
    read_mapping += CSRs.scounteren      -> read_scounteren
    read_mapping += CSRs.mideleg         -> read_mideleg
    read_mapping += CSRs.medeleg         -> read_medeleg
  }

  val pmpCfgPerCSR = xLen / new PMPConfig().getWidth
  def pmpCfgIndex(i: Int) = (xLen / 32) * (i / pmpCfgPerCSR)
  if (reg_pmp.nonEmpty) {
    require(reg_pmp.size <= CSR.maxPMPs)
    val read_pmp = reg_pmp.padTo(CSR.maxPMPs, 0.U.asTypeOf(new PMP))
    for (i <- 0 until read_pmp.size by pmpCfgPerCSR)
      read_mapping += (CSRs.pmpcfg0 + pmpCfgIndex(i)) -> read_pmp.map(_.cfg).slice(i, i + pmpCfgPerCSR).asUInt
    for ((pmp, i) <- read_pmp zipWithIndex)
      read_mapping += (CSRs.pmpaddr0 + i) -> pmp.readAddr
  }

  // implementation-defined CSRs
  val reg_custom = customCSRs.map { csr =>
    require(csr.mask >= 0 && csr.mask.bitLength <= xLen)
    require(!read_mapping.contains(csr.id))
    val reg = csr.init.map(init => RegInit(init.U(xLen.W))).getOrElse(Reg(UInt(xLen.W)))
    read_mapping += csr.id -> reg
    reg
  }
  // mimpid, marchid, and mvendorid are 0 unless overridden by customCSRs
  Seq(CSRs.mimpid, CSRs.marchid, CSRs.mvendorid).foreach(id => read_mapping.getOrElseUpdate(id, 0.U))

  // CSR address decoding.
  val decoded_addr      = read_mapping map { case (k, v) => k -> RegEnable(next=(ex2_addr_reg === k), enable=ex2_sel_reg, init=Bool(false)) }
  val decoded_addrEarly = read_mapping map { case (k, v) => k -> (ex2_addr_reg === k) }
  val ex1_decoded_addr  = read_mapping map { case (k, v) => k -> (ex1_addr_reg === k) }

  val mem_cmdEff      = Mux(muteD2_reg, CSR.N, Reg(next=io.rw.ex2_cmd))
  val system_insn     = (io.rw.ex2_cmd === CSR.I)
  val cpu_ren         = (io.rw.ex2_cmd =/= CSR.N) && !system_insn
  val priv_sufficient = (reg_mstatus.prv >= ex2_addr_reg(9,8))
  val read_only       = ex2_addr_reg(11,10).andR
  val cpu_wen         = cpu_ren && (io.rw.ex2_cmd =/= CSR.R) && priv_sufficient

  val decode_table = Seq(        SCALL->       List(Y,N,N,N,N,N),
                                 SBREAK->      List(N,Y,N,N,N,N),
                                 MRET->        List(N,N,Y,N,N,N),
                                 CEASE->       List(N,N,N,Y,N,N),
                                 WFI->         List(N,N,N,N,Y,N)) ++
    usingDebug.option(           DRET->        List(N,N,Y,N,N,N)) ++
    coreParams.haveCFlush.option(CFLUSH_D_L1-> List(N,N,N,N,N,N)) ++
    usingSupervisor.option(      SRET->        List(N,N,Y,N,N,N)) ++
    usingVM.option(              SFENCE_VMA->  List(N,N,N,N,N,Y))

  val insn_call :: insn_break :: insn_ret :: insn_cease :: insn_wfi :: insn_sfence :: Nil =
    DecodeLogic(ex2_addr_reg << 20, decode_table(0)._2.map(x=>X), decode_table).map(system_insn && _.asBool)

  val mem_cause_reg          = Reg(next=Mux(insn_call, reg_mstatus.prv + Causes.user_ecall, Mux[UInt](insn_break, Causes.breakpoint, Causes.illegal_instruction)), init=UInt(0))
  val mem_cmd_s_reg          = Reg(next=(io.rw.ex2_cmd === CSR.S), init=Bool(false)) && !muteD2_reg
  val mem_cmd_c_reg          = Reg(next=(io.rw.ex2_cmd === CSR.C), init=Bool(false)) && !muteD2_reg
  val mem_csr_addr_priv_reg  = Reg(next=ex2_addr_reg(10,8), init=UInt(0))
  val mem_insn_ret_reg       = Reg(next=insn_ret, init=Bool(false)) && !muteD2_reg
  val mem_insn_sfence_vm_reg = Reg(next=insn_sfence, init=Bool(false)) && !muteD2_reg
  val mem_insn_wfi_reg       = Reg(next=insn_wfi, init=Bool(false)) && !muteD2_reg
  val mem_wen_reg            = Reg(next=cpu_wen && !read_only, init=Bool(false)) && !muteD2_reg

  val ex2_xcptP1         = ((cpu_wen && read_only) || (system_insn && !priv_sufficient) || (cpu_ren && !priv_sufficient) || insn_call || insn_break)
  val ex2_addr_invalid   = (cpu_ren && !decoded_addrEarly.values.reduce(_||_))
  val ex2_fp_csr         =
    if (usingFPU) decoded_addrEarly(CSRs.fflags) || decoded_addrEarly(CSRs.frm) || decoded_addrEarly(CSRs.fcsr)
    else Bool(false)
  val mem_xcptP1_reg     = Reg(next=(ex2_xcptP1 || ex2_addr_invalid || (cpu_ren && ex2_fp_csr && !reg_mstatus.fs.orR)), init=Bool(false)) && !muteD2_reg

  // Read from CSR.
  io.rw.rdata := RegEnable(next=Mux1H(for ((k, v) <- read_mapping) yield ex1_decoded_addr(k) -> v), enable=ex1_sel_reg)

  // Implementing enable to the rdataR signal (i. e. rdataR is disabled when cmd is neither CSR.S nor CSR.C)
  val rdataR   = Reg(init=UInt(0, xLen))
  when(io.rw.ex2_cmd === CSR.S || io.rw.ex2_cmd === CSR.C){
    rdataR     := io.rw.rdata
  }

  val wdataW   = Mux(mem_cmd_s_reg, rdataR | io.rw.wdata, Mux(mem_cmd_c_reg, rdataR & ~io.rw.wdata, io.rw.wdata))
  val csr_xcpt = mem_xcptP1_reg || isXcptD1_reg

  when(reg_isWait) {
    reg_wait_cntr := reg_wait_cntr - UInt(1)
  }
  when (((pending_interrupts.orR || interruptsD1_reg.debug) && !reg_isWait) || (((reg_wait_cntr === UInt(0)) || (raiseInt && !reg_dontInt)) && reg_isWait)) {
    reg_wfi       := Bool(false)
    reg_isWait    := Bool(false)
    reg_dontInt   := Bool(false)
  }
  when (mem_insn_wfi_reg && !io.singleStep && !reg_debug) {
    reg_wfi     := true
  }

  // Decode bus.
  val allow_sfence_vma = Bool(!usingVM) || reg_mstatus.prv > PRV.S || !reg_mstatus.tvm
  for (io_dec <- io.decode) {
    def decodeAny(m: LinkedHashMap[Int,Bits]): Bool = m.map { case(k: Int, _: Bits) => io_dec.idc_addr === k }.reduce(_||_)
    def decodeFast(s: Seq[Int]): Bool = DecodeLogic(io_dec.idc_addr, s.map(_.U), (read_mapping -- s).keys.toList.map(_.U))

    val _ :: is_break :: is_ret :: _ :: is_wfi :: is_sfence :: Nil =
      DecodeLogic(io_dec.idc_addr << 20, decode_table(0)._2.map(x=>X), decode_table).map(_.asBool)

    val allow_wfi = Bool(!usingSupervisor) || reg_mstatus.prv > PRV.S || !reg_mstatus.tw
    val allow_sret = Bool(!usingSupervisor) || reg_mstatus.prv > PRV.S || !reg_mstatus.tsr
    val counter_addr = io_dec.idc_addr(log2Ceil(read_mcounteren.getWidth)-1, 0)
    val allow_counter = (reg_mstatus.prv > PRV.S || read_mcounteren(counter_addr)) &&
      (!usingSupervisor || reg_mstatus.prv >= PRV.S || read_scounteren(counter_addr))
    io_dec.fp_illegal := io.status.fs === 0 || !reg_misa('f'-'a')
    io_dec.vector_illegal := io.status.vs === 0 || !reg_misa('v'-'a')
    io_dec.fp_csr := decodeFast(fp_csrs.keys.toList)
    io_dec.rocc_illegal := io.status.xs === 0 || !reg_misa('x'-'a')
    io_dec.read_illegal := reg_mstatus.prv < io_dec.idc_addr(9,8) ||
      !decodeAny(read_mapping) ||
      io_dec.idc_addr === CSRs.satp && !allow_sfence_vma ||
      (io_dec.idc_addr.inRange(CSR.firstCtr, CSR.firstCtr + CSR.nCtr) || io_dec.idc_addr.inRange(CSR.firstCtrH, CSR.firstCtrH + CSR.nCtr)) && !allow_counter ||
      decodeFast(debug_csrs.keys.toList) && !reg_debug ||
      decodeFast(vector_csrs.keys.toList) && io_dec.vector_illegal ||
      io_dec.fp_csr && io_dec.fp_illegal
    io_dec.write_illegal := io_dec.idc_addr(11,10).andR
    io_dec.write_flush := !(io_dec.idc_addr >= CSRs.mscratch && io_dec.idc_addr <= CSRs.mtval || io_dec.idc_addr >= CSRs.sscratch && io_dec.idc_addr <= CSRs.stval)
    io_dec.system_illegal := reg_mstatus.prv < io_dec.idc_addr(9,8) ||
      is_wfi && !allow_wfi ||
      is_ret && !allow_sret ||
      is_ret && io_dec.idc_addr(10) && !reg_debug ||
      is_sfence && !allow_sfence_vma
  }
  io.csrUnitCtxtUnit.fpActive := Reg(next=Cat(allow_sfence_vma, (io.fcsr_rm >= UInt(5)), (io.status.fs === 0 || !reg_misa('f'-'a'))))

  val cause =
    Mux(xcptMaStD1_reg,  UInt(Causes.misaligned_store),
    Mux(xcptMaLdD1_reg,  UInt(Causes.misaligned_load),
    Mux(xcptPfStD1_reg,  UInt(Causes.store_page_fault),
    Mux(xcptPfLdD1_reg,  UInt(Causes.load_page_fault),
    Mux(xcptAeStD1_reg,  UInt(Causes.store_access),
    Mux(xcptAeLdD1_reg,  UInt(Causes.load_access),
      if (nBreakpoints > 0) {
        Mux(debug_breakpointD1_reg,  UInt(CSR.debugTriggerCause),
        Mux(breakpointD1_reg,  UInt(Causes.breakpoint),
        Mux(mem_xcptP1_reg, mem_cause_reg, io.cause)))
      }
      else {
        Mux(mem_xcptP1_reg, mem_cause_reg, io.cause)
      }
    ))))))

  val cause_lsbs = cause(log2Ceil(1 + CSR.busErrorIntCause)-1, 0)

  val insn_breakD1_reg    = Reg(next=insn_break) && !muteD2_reg
  val causeIsDebugInt     = cause(xLen-1) && cause_lsbs === CSR.debugIntCause
  val causeIsDebugTrigger = !cause(xLen-1) && cause_lsbs === CSR.debugTriggerCause
  val causeIsDebugBreak   = !cause(xLen-1) && insn_breakD1_reg && Cat(reg_dcsr.ebreakm, reg_dcsr.ebreakh, reg_dcsr.ebreaks, reg_dcsr.ebreaku)(reg_mstatus.prv)
  val trapToDebug         = Bool(usingDebug) && (reg_singleStepped || causeIsDebugInt || causeIsDebugTrigger || causeIsDebugBreak || reg_debug)
  val debugTVec           = Mux(reg_debug, Mux(insn_breakD1_reg, UInt(0x800), UInt(0x808)), UInt(0x800))

  // Check the mask flags.
  val miMask    = 0x222
  val meMask    = 0xb15d
  assert(UInt(miMask) === delegable_interrupts)
  assert(UInt(meMask) === delegable_exceptions)

  val mi        = MuxTreeMask(cause_lsbs, read_mideleg, miMask)
  val me        = MuxTreeMask(cause_lsbs, read_medeleg, meMask)
  val delegate  = Bool(usingSupervisor) && reg_mstatus.prv <= PRV.S && Mux(cause(xLen-1), mi, me)

  def mtvecBaseAlign      = 2
  def mtvecInterruptAlign = {
    require(reg_mip.getWidth <= xLen)
    log2Ceil(xLen)
  }
  val notDebugTVec = {
    val base            = Mux(delegate, read_stvec, read_mtvec)
    val interruptOffset = cause(mtvecInterruptAlign-1, 0) << mtvecBaseAlign
    val interruptVec    = Cat(base >> (mtvecInterruptAlign + mtvecBaseAlign), interruptOffset)
    val doVector = base(0) && cause(io.cause.getWidth-1) && (cause_lsbs >> mtvecInterruptAlign) === 0
    Mux(doVector, interruptVec, base >> mtvecBaseAlign << mtvecBaseAlign)
  }

  val tvec          = Mux(trapToDebug, debugTVec, notDebugTVec)
  val epcSel        = Mux(Bool(usingSupervisor) && !mem_csr_addr_priv_reg(1), readEPC(reg_sepc), Mux(Bool(usingDebug) && mem_csr_addr_priv_reg(2), readEPC(reg_dpc), readEPC(reg_mepc)))
  val evec          = Mux(mem_exceptionG || csr_xcpt, tvec, epcSel)
  io.ptbr          := reg_satp
  io.csr_flowAlter := (csr_xcpt && !isXcptD1_reg && io.mem_csr_ena) || (mem_insn_ret_reg && io.mem_csr_ena)
  io.singleStep    := reg_dcsr.step && !reg_debug
  io.status        := reg_mstatus
  io.status.sd     := io.status.fs.andR || io.status.xs.andR || io.status.vs.andR
  io.status.debug  := reg_debug
  io.status.isa    := reg_misa
  io.status.uxl    := (if (usingUser) log2Ceil(xLen) - 4 else 0)
  io.status.sxl    := (if (usingSupervisor) log2Ceil(xLen) - 4 else 0)
  io.status.dprv   := Reg(next = Mux(reg_mstatus.mprv && !reg_debug, reg_mstatus.mpp, reg_mstatus.prv))
  if (xLen == 32)
    io.status.sd_rv32 := io.status.sd

  val exception = mem_exceptionG || csr_xcpt
  when ((io.retire(0) && !io.exx_muteInstr) || exception) { reg_singleStepped := true }
  when (!io.singleStep) { reg_singleStepped := false }
  assert(!io.singleStep || io.retire <= UInt(1))
  assert(!reg_singleStepped || io.retire === UInt(0))
  assert(asIdBits === 0)

  val epc      = formEPC(Mux(isXcptD1_reg, io.pcD1, io.pc))

  val ldst =
    cause === Causes.load_page_fault || cause === Causes.misaligned_load ||
    cause === Causes.store_page_fault || cause === Causes.misaligned_store

  val tval    = Mux(isXcptD1_reg, io.wb_addr, encodeVirtualAddress(io.rw.wdata, io.rw.wdata))
  when (exception) {
    when (trapToDebug) {
      when (!reg_debug) {
        reg_debug      := true
        reg_dpc        := epc
        reg_dcsr.cause := Mux(reg_singleStepped, 4, Mux(causeIsDebugInt, 3, Mux[UInt](causeIsDebugTrigger, 2, 1)))
        reg_dcsr.prv   := trimPrivilege(reg_mstatus.prv)
        new_prv        := PRV.M
      }
    }
    .elsewhen (delegate) {
      reg_sepc         := epc
      reg_scause       := cause
      reg_stval        := tval
      reg_mstatus.spie := reg_mstatus.sie
      reg_mstatus.spp  := reg_mstatus.prv
      reg_mstatus.sie  := false
      new_prv          := PRV.S
    }
    .otherwise {
      reg_mepc         := epc
      reg_mcause       := cause
      reg_mtval        := tval
      reg_mstatus.mpie := reg_mstatus.mie
      reg_mstatus.mpp  := trimPrivilege(reg_mstatus.prv)
      reg_mstatus.mie  := false
      new_prv          := PRV.M
    }
  }

  when (mem_insn_ret_reg) {
    when (Bool(usingSupervisor) && !mem_csr_addr_priv_reg(1)) {
      reg_mstatus.sie  := reg_mstatus.spie
      reg_mstatus.spie := true
      reg_mstatus.spp  := PRV.U
      new_prv          := reg_mstatus.spp
    }
    .elsewhen (Bool(usingDebug) && mem_csr_addr_priv_reg(2)) {
      new_prv   := reg_dcsr.prv
      reg_debug := false
    }
    .otherwise {
      reg_mstatus.mie  := reg_mstatus.mpie
      reg_mstatus.mpie := true
      reg_mstatus.mpp  := legalizePrivilege(PRV.U)
      new_prv          := reg_mstatus.mpp
    }
  }

  // Connect bus to the corresponding ROW.
  io.csrUnitCtxtUnit.wfi_stall   := reg_wfi || io.status.cease
  io.status.cease                := RegEnable(true.B, false.B, insn_cease)
  io.status.wfi                  := reg_wfi

  for ((io, reg) <- io.customCSRs zip reg_custom) {
    io.wen   := false
    io.wdata := wdataW
    io.value := reg
  }

  val set_fs_dirty = Wire(init = io.set_fs_dirty.getOrElse(false.B))
  if (coreParams.haveFSDirty) {
    when (set_fs_dirty) {
      assert(reg_mstatus.fs > 0)
      reg_mstatus.fs := 3
    }
  }

  val set_vs_dirty = Wire(init = io.vector.map(_.set_vs_dirty).getOrElse(false.B))
  io.vector.foreach { vio =>
    when (set_vs_dirty) {
      assert(reg_mstatus.vs > 0)
      reg_mstatus.vs := 3
    }
  }

  io.fcsr_rm := reg_frm
  when (io.fcsr_flags.valid) {
    reg_fflags := reg_fflags | io.fcsr_flags.bits
    set_fs_dirty := true
  }

  io.vector.foreach { vio =>
    when (vio.set_vxsat) {
      reg_vxsat.get := true
      set_vs_dirty := true
    }
  }

  when (mem_wen_reg) {
    when (decoded_addr(CSRs.mstatus)) {
      val new_mstatus   = new MStatus().fromBits(wdataW)
      reg_mstatus.mie  := new_mstatus.mie
      reg_mstatus.mpie := new_mstatus.mpie

      if (usingUser) {
        reg_mstatus.mprv := new_mstatus.mprv
        reg_mstatus.mpp := legalizePrivilege(new_mstatus.mpp)
        if (usingSupervisor) {
          reg_mstatus.spp  := new_mstatus.spp
          reg_mstatus.spie := new_mstatus.spie
          reg_mstatus.sie  := new_mstatus.sie
          reg_mstatus.tw   := new_mstatus.tw
          reg_mstatus.tsr  := new_mstatus.tsr
        }
        if (usingVM) {
          reg_mstatus.mxr := new_mstatus.mxr
          reg_mstatus.sum := new_mstatus.sum
          reg_mstatus.tvm := new_mstatus.tvm
        }
      }

      if (usingSupervisor || usingFPU) reg_mstatus.fs := formFS(new_mstatus.fs)
      reg_mstatus.vs := formVS(new_mstatus.vs)
      if (usingRoCC) reg_mstatus.xs := Fill(2, new_mstatus.xs.orR)
    }
    when (decoded_addr(CSRs.misa)) {
      val mask = UInt(isaStringToMask(isaMaskString), xLen)
      val f = wdataW('f' - 'a')
      // suppress write if it would cause the next fetch to be misaligned
      when (!usingCompressed || !io.pc(1) || wdataW('c' - 'a')) {
        if (coreParams.misaWritable)
          reg_misa := ~(~wdataW | (!f << ('d' - 'a'))) & mask | reg_misa & ~mask
      }
    }
    when (decoded_addr(CSRs.mip)) {
      // MIP should be modified based on the value in reg_mip, not the value
      // in read_mip, since read_mip.seip is the OR of reg_mip.seip and
      // interruptsD1_reg.seip.  We don't want the value on the PLIC line to
      // inadvertently be OR'd into read_mip.seip.
      val new_mip = readModifyWriteCSR(mem_cmdEff, reg_mip.asUInt, io.rw.wdata).asTypeOf(new MIP)
      if (usingSupervisor) {
        reg_mip.ssip := new_mip.ssip
        reg_mip.stip := new_mip.stip
        reg_mip.seip := new_mip.seip
      }
    }

    when (decoded_addr(CSRs.dbgDisable))   {
      dbgDisable_reg := wdataW(0)
    }

    when (decoded_addr(CSRs.ctxt_wait))   {
      reg_wait_cntr   := wdataW
      reg_wfi         := true
      reg_isWait      := true
      reg_dontInt     := wdataW(31)
    }
    when (decoded_addr(CSRs.mie))         { reg_mie         := wdataW & supported_interrupts }
    when (decoded_addr(CSRs.mepc))        { reg_mepc        := formEPC(wdataW) }
    when (decoded_addr(CSRs.mscratch))    { reg_mscratch    := wdataW }
    if (mtvecWritable)
      when (decoded_addr(CSRs.mtvec))  { reg_mtvec    := wdataW }
    when (decoded_addr(CSRs.mcause))   { reg_mcause   := wdataW & UInt((BigInt(1) << (xLen-1)) + (BigInt(1) << whichInterrupt.getWidth) - 1) }
    when (decoded_addr(CSRs.mtval))    { reg_mtval    := wdataW(vaddrBitsExtended-1,0) }

    if(ENA_PERF == 1) {
      require(nPerfCounters == 0, "We do not use performance counters.")
      for (((e, c), i) <- (reg_hpmevent zip reg_hpmcounter) zipWithIndex) {
        writeCounter(i + CSR.firstMHPC, c, wdataW)
        when (decoded_addr(i + CSR.firstHPE)) { e := perfEventSets.maskEventSelector(wdataW) }
      }
    }
    if (coreParams.haveBasicCounters) {
      //writeCounter(CSRs.mcycle, reg_cycle1, wdataW)
      writeCounter(CSRs.minstret, reg_instret1, wdataW)
    }

    if (usingFPU) {
      when (decoded_addr(CSRs.fflags)) { set_fs_dirty := true; reg_fflags := wdataW }
      when (decoded_addr(CSRs.frm))    { set_fs_dirty := true; reg_frm    := wdataW }
      when (decoded_addr(CSRs.fcsr)) {
        set_fs_dirty := true
        reg_fflags   := wdataW
        reg_frm      := wdataW >> reg_fflags.getWidth
      }
    }
    if (usingDebug) {
      when (decoded_addr(CSRs.dcsr)) {
        val new_dcsr = new DCSR().fromBits(wdataW)
        reg_dcsr.step := new_dcsr.step
        reg_dcsr.ebreakm := new_dcsr.ebreakm
        if (usingSupervisor) reg_dcsr.ebreaks := new_dcsr.ebreaks
        if (usingUser) reg_dcsr.ebreaku := new_dcsr.ebreaku
        if (usingUser) reg_dcsr.prv := legalizePrivilege(new_dcsr.prv)
      }
      when (decoded_addr(CSRs.dpc))      { reg_dpc := formEPC(wdataW) }
      when (decoded_addr(CSRs.dscratch)) { reg_dscratch := wdataW }
    }
    if (usingSupervisor) {
      when (decoded_addr(CSRs.sstatus)) {
        val new_sstatus   = new MStatus().fromBits(wdataW)
        reg_mstatus.sie  := new_sstatus.sie
        reg_mstatus.spie := new_sstatus.spie
        reg_mstatus.spp  := new_sstatus.spp
        reg_mstatus.fs   := formFS(new_sstatus.fs)
        reg_mstatus.vs   := formVS(new_sstatus.vs)
        if (usingVM) {
          reg_mstatus.mxr := new_sstatus.mxr
          reg_mstatus.sum := new_sstatus.sum
        }
        if (usingRoCC) reg_mstatus.xs := Fill(2, new_sstatus.xs.orR)
      }
      when (decoded_addr(CSRs.sip)) {
        val new_sip = new MIP().fromBits((read_mip & ~read_mideleg) | (wdataW & read_mideleg))
        reg_mip.ssip := new_sip.ssip
      }
      when (decoded_addr(CSRs.satp)) {
        if (usingVM) {
          val new_satp = new PTBR().fromBits(wdataW)
          val valid_modes = 0 +: (minPgLevels to pgLevels).map(new_satp.pgLevelsToMode(_))
          when (new_satp.mode.isOneOf(valid_modes.map(_.U))) {
            reg_satp.mode := new_satp.mode & valid_modes.reduce(_|_)
            reg_satp.ppn := new_satp.ppn(ppnBits-1,0)
            if (asIdBits > 0) reg_satp.asid := new_satp.asid(asIdBits-1,0)
          }
        }
      }
      when (decoded_addr(CSRs.sie))        { reg_mie        := (reg_mie & ~read_mideleg) | (wdataW & read_mideleg) }
      when (decoded_addr(CSRs.sscratch))   { reg_sscratch   := wdataW }
      when (decoded_addr(CSRs.sepc))       { reg_sepc       := formEPC(wdataW) }
      when (decoded_addr(CSRs.stvec))      { reg_stvec      := wdataW }
      when (decoded_addr(CSRs.scause))     { reg_scause     := wdataW & UInt((BigInt(1) << (xLen-1)) + 31) /* only implement 5 LSBs and MSB */ }
      when (decoded_addr(CSRs.stval))      { reg_stval      := wdataW(vaddrBitsExtended-1,0) }
      when (decoded_addr(CSRs.mideleg))    { reg_mideleg    := wdataW }
      when (decoded_addr(CSRs.medeleg))    { reg_medeleg    := wdataW }
      when (decoded_addr(CSRs.scounteren)) { reg_scounteren := wdataW }
    }
    if (usingUser) {
      when (decoded_addr(CSRs.mcounteren)) { reg_mcounteren := wdataW }
    }
    if (nBreakpoints > 0) {
      when (decoded_addr(CSRs.tselect)) { reg_tselect := wdataW }

      for ((bp, i) <- reg_bp.zipWithIndex) {
        when (i === reg_tselect && (!bp.control.dmode || reg_debug)) {
          when (decoded_addr(CSRs.tdata2)) { bp.address := wdataW }
          when (decoded_addr(CSRs.tdata1)) {
            bp.control := wdataW.asTypeOf(bp.control)

            val prevChain = if (i == 0) false.B else reg_bp(i-1).control.chain
            val prevDMode = if (i == 0) false.B else reg_bp(i-1).control.dmode
            val nextChain = if (i >= nBreakpoints-1) true.B else reg_bp(i+1).control.chain
            val nextDMode = if (i >= nBreakpoints-1) true.B else reg_bp(i+1).control.dmode
            val newBPC = readModifyWriteCSR(mem_cmdEff, bp.control.asUInt, io.rw.wdata).asTypeOf(bp.control)
            val dMode = newBPC.dmode && reg_debug && (prevDMode || !prevChain)
            bp.control.dmode := dMode
            when (dMode || (newBPC.action > 1.U)) { bp.control.action := newBPC.action }.otherwise { bp.control.action := 0.U }
            bp.control.chain := newBPC.chain && !(prevChain || nextChain) && (dMode || !nextDMode)
          }
        }
      }
    }
    if (reg_pmp.nonEmpty) for (((pmp, next), i) <- (reg_pmp zip (reg_pmp.tail :+ reg_pmp.last)) zipWithIndex) {
      require(xLen % pmp.cfg.getWidth == 0)
      when (decoded_addr(CSRs.pmpcfg0 + pmpCfgIndex(i)) && !pmp.cfgLocked) {
        val newCfg = new PMPConfig().fromBits(wdataW >> ((i * pmp.cfg.getWidth) % xLen))
        pmp.cfg := newCfg
        // disallow unreadable but writable PMPs
        pmp.cfg.w := newCfg.w && newCfg.r
        // can't select a=NA4 with coarse-grained PMPs
        if (pmpGranularity.log2 > PMP.lgAlign)
          pmp.cfg.a := Cat(newCfg.a(1), newCfg.a.orR)
      }
      when (decoded_addr(CSRs.pmpaddr0 + i) && !pmp.addrLocked(next)) {
        pmp.addr := wdataW
      }
    }
    for ((io, csr, reg) <- (io.customCSRs, customCSRs, reg_custom).zipped) {
      val mask = csr.mask.U(xLen.W)
      when (decoded_addr(csr.id)) {
        reg := (wdataW & mask) | (reg & ~mask)
        io.wen := true
      }
    }
    if (usingVector) {
      when (decoded_addr(CSRs.vstart)) { set_vs_dirty := true; reg_vstart.get := wdataW }
      when (decoded_addr(CSRs.vxrm))   { set_vs_dirty := true; reg_vxrm.get := wdataW }
      when (decoded_addr(CSRs.vxsat))  { set_vs_dirty := true; reg_vxsat.get := wdataW }
      when (decoded_addr(CSRs.vcsr))   {
        set_vs_dirty := true
        reg_vxsat.get := wdataW
        reg_vxrm.get := wdataW >> 1
      }
    }
  }

  // Control instruction mute. It starts one cycle after receiving the miss flag.
  val flagsValid          = io.flagsValid && !muteD2_reg
  isXcptD1_reg           := (io.xcptMaSt || io.xcptMaLd || io.xcptPfSt || io.xcptPfLd || io.xcptAeSt || io.xcptAeLd || io.breakpoint || io.debug_breakpoint) && flagsValid
  xcptMaStD1_reg         := io.xcptMaSt && flagsValid
  xcptMaLdD1_reg         := io.xcptMaLd && flagsValid
  xcptPfStD1_reg         := io.xcptPfSt && flagsValid
  xcptPfLdD1_reg         := io.xcptPfLd && flagsValid
  xcptAeStD1_reg         := io.xcptAeSt && flagsValid
  xcptAeLdD1_reg         := io.xcptAeLd && flagsValid
  breakpointD1_reg       := io.breakpoint && flagsValid
  debug_breakpointD1_reg := io.debug_breakpoint && flagsValid
  tlbMissD1_reg          := io.tlbMiss && flagsValid

  val muteE         = (io.tlbMiss || io.xcptMaSt || io.xcptMaLd || io.xcptPfSt || io.xcptPfLd || io.xcptAeSt || io.xcptAeLd || io.breakpoint || io.debug_breakpoint) && flagsValid
  muteP_reg        := muteE

  val NUM_MUTE_CYC = UInt(7)
  when(muteD2_reg) {
    muteCntr_reg     := muteCntr_reg + UInt(1)
  }
  when(muteCntr_reg === NUM_MUTE_CYC) {
    muteD2_reg     := Bool(false)
  }
  when(muteE) {
    muteD2_reg     := Bool(true)
    muteCntr_reg   := UInt(0)
  }
  io.exx_muteInstr := muteD2_reg || muteE

  // TLB control.
  tlbReady_reg                     := Mux(io.tlbMiss && flagsValid, Bool(false), Mux(io.tlbSafe, Bool(true), tlbReady_reg)) && !muteD2_reg
  io.csrUnitCtxtUnit.tlbSafeR      := tlbReady_reg

  // Repeat instruction.
  val csrFlowAltered = muteP_reg || mem_exceptionG || io.csr_flowAlter
  when(csrFlowAltered) {
    csrFlowPc_reg       := Mux(tlbMissD1_reg, io.pcD1, evec)
  }
  io.csrUnitCtxtUnit.csrFlowAlt    := Reg(next=csrFlowAltered, init=Bool(false))
  io.csrUnitCtxtUnit.csrFlowPc     := csrFlowPc_reg
  io.csrUnitCtxtUnit.isTLBMiss     := Reg(next=tlbMissD1_reg, init=Bool(false))
  io.csrUnitCtxtUnit.muteD2_reg    := muteD2_reg
  io.csrUnitCtxtUnit.interruptSlow := interruptInProg_reg

  io.vector.map { vio =>
    when (vio.set_vconfig.valid) {
      // user of CSRFile is responsible for set_vs_dirty in this case
      assert(vio.set_vconfig.bits.vl <= vio.set_vconfig.bits.vtype.vlMax)
      reg_vconfig.get := vio.set_vconfig.bits
    }
    when (vio.set_vstart.valid) {
      set_vs_dirty := true
      reg_vstart.get := vio.set_vstart.bits
    }
    vio.vstart := reg_vstart.get
    vio.vconfig := reg_vconfig.get
    vio.vxrm := reg_vxrm.get
  }

  reg_satp.asid := 0
  if (!usingVM) {
    reg_satp.mode := 0
    reg_satp.ppn := 0
  }

  if (nBreakpoints <= 1) reg_tselect := 0
  for (bpc <- reg_bp map {_.control}) {
    bpc.ttype := bpc.tType
    bpc.maskmax := bpc.maskMax
    bpc.reserved := 0
    bpc.zero := 0
    bpc.h := false
    if (!usingSupervisor) bpc.s := false
    if (!usingUser) bpc.u := false
    if (!usingSupervisor && !usingUser) bpc.m := true
  }
  for (bp <- reg_bp drop nBreakpoints)
    bp := new BP().fromBits(0)
  for (pmp <- reg_pmp) {
    pmp.cfg.res := 0
  }

  def chooseInterrupt(masksIn: Seq[UInt]): (Bool, UInt) = {
    val nonstandard = supported_interrupts.getWidth-1 to 12 by -1
    // MEI, MSI, MTI, SEI, SSI, STI, UEI, USI, UTI
    val standard = Seq(11, 3, 7, 9, 1, 5, 8, 0, 4)
    val priority = nonstandard ++ standard
    val masks = masksIn.reverse
    val any = masks.flatMap(m => priority.filter(_ < m.getWidth).map(i => m(i))).reduce(_||_)
    val which = PriorityMux(masks.flatMap(m => priority.filter(_ < m.getWidth).map(i => (m(i), i.U))))
    (any, which)
  }

  def readModifyWriteCSR(cmd: UInt, rdata: UInt, wdata: UInt) = {
    (Mux(cmd(1), rdata, UInt(0)) | wdata) & ~Mux(cmd(1,0).andR, wdata, UInt(0))
  }

  def legalizePrivilege(priv: UInt): UInt =
    if (usingSupervisor) Mux(priv === PRV.H, PRV.U, priv)
    else if (usingUser) Fill(2, priv(0))
    else PRV.M

  def trimPrivilege(priv: UInt): UInt =
    if (usingSupervisor) priv
    else legalizePrivilege(priv)

  def writeCounter(lo: Int, ctr: WideCounter, wdata: UInt) = {
    if (xLen == 32) {
      val hi = lo + CSRs.mcycleh - CSRs.mcycle
      when (decoded_addr(lo)) { ctr := Cat(ctr(ctr.getWidth-1, 32), wdata) }
      when (decoded_addr(hi)) { ctr := Cat(wdata(ctr.getWidth-33, 0), ctr(31, 0)) }
    } else {
      when (decoded_addr(lo)) { ctr := wdata(ctr.getWidth-1, 0) }
    }
  }
  def formEPC(x: UInt) = ~(~x | (if (usingCompressed) 1.U else 3.U))
  def readEPC(x: UInt) = ~(~x | Mux(reg_misa('c' - 'a'), 1.U, 3.U))
  def formTVec(x: UInt) = x andNot Mux(x(0), ((((BigInt(1) << mtvecInterruptAlign) - 1) << mtvecBaseAlign) | 2).U, 2)
  def isaStringToMask(s: String) = s.map(x => 1 << (x - 'A')).foldLeft(0)(_|_)
  def formFS(fs: UInt) = if (coreParams.haveFSDirty) fs else Fill(2, fs.orR)
  def formVS(vs: UInt) = if (usingVector) vs else 0.U
}

