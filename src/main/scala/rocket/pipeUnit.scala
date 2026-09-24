// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import chisel3.withReset
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import scala.collection.immutable.ListMap
import scala.collection.mutable.ArrayBuffer

import freechips.rocketchip.rocket.ALU._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants.Util._
import superThread._

case class RocketCoreParams(
  bootFreqHz: BigInt = 0,
  useVM: Boolean = true,
  useUser: Boolean = false,
  useSupervisor: Boolean = false,
  useDebug: Boolean = true,
  useAtomics: Boolean = true,
  useAtomicsOnlyForIO: Boolean = false,
  useCompressed: Boolean = true,
  useRVE: Boolean = false,
  useSCIE: Boolean = false,
  nLocalInterrupts: Int = 0,
  nBreakpoints: Int = 0, // TODO: the BPU needs to be connected with the MPIPE and FPIPE
  useBPWatch: Boolean = false,
  nPMPs: Int = 0,
  nPerfCounters: Int = 0,
  haveBasicCounters: Boolean = true,
  haveCFlush: Boolean = false,
  misaWritable: Boolean = false,
  mtvecInit: Option[BigInt] = Some(BigInt(0)),
  mtvecWritable: Boolean = true,
  fastLoadWord: Boolean = true,
  fastLoadByte: Boolean = false,
  branchPredictionModeCSR: Boolean = false,
  clockGate: Boolean = false,
  mvendorid: Int = 0, // 0 means non-commercial implementation
  mimpid: Int = 0x20181004, // release date in BCD
  mulDiv: Option[MulDivParams] = Some(MulDivParams()),
  fpu: Option[FPUParams] = Some(FPUParams())
) extends CoreParams {
  val haveFSDirty = false
  val pmpGranularity: Int = 4
  val fetchWidth: Int = if (useCompressed) 2 else 1
  //  fetchWidth doubled, but coreInstBytes halved, for RVC:
  val decodeWidth: Int = fetchWidth / (if (useCompressed) 2 else 1)
  val retireWidth: Int = 1
  val instBits: Int = if (useCompressed) 16 else 32
  val lrscCycles: Int = 64 // worst case is 14 mispredicted branches + slop
  override def customCSRs(implicit p: Parameters) = new RocketCustomCSRs
}

trait HasRocketCoreParameters extends HasCoreParameters {
  lazy val rocketParams: RocketCoreParams = tileParams.core.asInstanceOf[RocketCoreParams]

  val fastLoadWord = rocketParams.fastLoadWord
  val fastLoadByte = rocketParams.fastLoadByte

  val mulDivParams = rocketParams.mulDiv.getOrElse(MulDivParams()) // TODO ask andrew about this

  require(!fastLoadByte || fastLoadWord)

  def encodeVirtualAddress(a0: UInt, ea: UInt) = if (vaddrBitsExtended == vaddrBits) ea else {
    // efficient means to compress 64-bit VA into vaddrBits+1 bits
    // (VA is bad if VA(vaddrBits) != VA(vaddrBits-1))
    val a = a0.asSInt >> vaddrBits
    val msb = Mux(a === 0.S || a === -1.S, ea(vaddrBits), !ea(vaddrBits-1))
    Cat(msb, ea(vaddrBits-1,0))
  }
}

class RocketCustomCSRs(implicit p: Parameters) extends CustomCSRs with HasRocketCoreParameters {
  override def bpmCSR = {
    rocketParams.branchPredictionModeCSR.option(CustomCSR(bpmCSRId, BigInt(1), Some(BigInt(0))))
  }

  override def chickenCSR = {
    val mask = BigInt(tileParams.dcache.get.clockGate.toInt << 0)
    Some(CustomCSR(chickenCSRId, mask, Some(mask)))
  }

  def marchid = CustomCSR.constant(CSRs.marchid, BigInt(1))

  override def decls = super.decls :+ marchid
}

object ImmGen {
  def apply(sel: UInt, inst: UInt) = {
    val sign = Mux(sel === IMM_Z, SInt(0), inst(31).asSInt)
    val b30_20 = Mux(sel === IMM_U, inst(30,20).asSInt, sign)
    val b19_12 = Mux(sel =/= IMM_U && sel =/= IMM_UJ, sign, inst(19,12).asSInt)
    val b11 = Mux(sel === IMM_U || sel === IMM_Z, SInt(0),
              Mux(sel === IMM_UJ, inst(20).asSInt,
              Mux(sel === IMM_SB, inst(7).asSInt, sign)))
    val b10_5 = Mux(sel === IMM_U || sel === IMM_Z, Bits(0), inst(30,25))
    val b4_1 = Mux(sel === IMM_U, Bits(0),
               Mux(sel === IMM_S || sel === IMM_SB, inst(11,8),
               Mux(sel === IMM_Z, inst(19,16), inst(24,21))))
    val b0 = Mux(sel === IMM_S, inst(7),
             Mux(sel === IMM_I, inst(20),
             Mux(sel === IMM_Z, inst(15), Bits(0))))

    Cat(sign, b30_20, b19_12, b11, b10_5, b4_1, b0).asSInt
  }
}

object ImmGenRVC {
  def apply(sel: UInt, inst: UInt) = {
    val sign   = Mux(sel === IMM_I4SP, SInt(0), inst(12).asSInt)
    val b30_18 = Fill(13, sign.asUInt).asSInt
    val b17_12 = Mux(sel === IMM_U, Cat(sign,inst(6,2)).asSInt, sign)
    val b11_10 = Mux(sel === IMM_UJ, Cat(sign,inst(8)).asSInt,
                 Mux(sel === IMM_U, SInt(0), sign))
    val b9     = Mux(sel === IMM_UJ || sel === IMM_I4SP, inst(10).asSInt,
                 Mux(sel === IMM_I16SP || sel === IMM_I || sel === IMM_SB, sign, SInt(0)))
    val b8_6   = Mux(sel === IMM_I, sign,
                 Mux(sel === IMM_UJ, Cat(inst(9),inst(6),inst(7)).asSInt,
                 Mux(sel === IMM_I16SP, Cat(inst(4,3),inst(5)).asSInt,
                 Mux(sel === IMM_I4SP, inst(9,7).asSInt,
                 Mux(sel === IMM_SB, Cat(inst(12),inst(6,5)).asSInt, SInt(0,3))))))
    val b5     = Mux(sel === IMM_I || sel === IMM_I4SP, inst(12).asSInt,
                 Mux(sel === IMM_U, SInt(0), inst(2).asSInt))
    val b4_0   = Mux(sel === IMM_I, inst(6,2).asSInt,
                 Mux(sel === IMM_UJ, Cat(inst(11),inst(5,3),UInt(0)).asSInt,
                 Mux(sel === IMM_I16SP, Cat(inst(6),UInt(0,4)).asSInt,
                 Mux(sel === IMM_I4SP, Cat(inst(11),inst(5),inst(6), UInt(0,2)).asSInt,
                 Mux(sel === IMM_SB, Cat(inst(11,10),inst(4,3),UInt(0)).asSInt, SInt(0,5))))))

    Cat(sign,b30_18, b17_12, b11_10, b9, b8_6, b5, b4_0).asSInt
}}

object ImmGenMRVC {
  def apply(sel: UInt, inst: UInt) = {
    val b8     = Mux(sel === IMM_I, inst(4), Mux(sel === IMM_UJ, inst(9), Bits(0)))
    val b7_6   = Mux(sel === IMM_I4SP, Cat(Bits(0),inst(5)),
                 Mux(sel === IMM_I || sel === IMM_I16SP, inst(3,2),
                 Mux(sel === IMM_UJ || sel === IMM_SB, inst(8,7), inst(6,5))))
    val b5     = inst(12)
    val b4_3   = Mux(sel === IMM_I || sel === IMM_I16SP, inst(6,5), inst(11,10))
    val b2     = Mux(sel === IMM_SB, inst(9),
                 Mux(sel === IMM_I4SP, inst(6),
                 Mux(sel === IMM_I16SP, inst(4), Bits(0))))
    val b1_0   = UInt(0,2)

    Cat(UInt(0,4),b8, b7_6, b5, b4_3, b2, b1_0)
  }
}


trait HasCoreIO extends HasTileParameters {
  implicit val p: Parameters
  val io = new CoreBundle()(p) with HasExternallyDrivenTileConstants {
    val ctxtUPipeU       = new CtxtUPipeUBundle().asInput
    val pipeUCtxtU       = new PipeUCtxtUBundle().asOutput
    val pipeFpu          = new FPUCoreIO().flip
    val rocc             = new RoCCCoreIO().flip

    val mpipeDataUnit  = Vec(NUM_MPS, new PipeUDataUBundle(ADDR_LEN).asOutput)
    val duReadyCheck     = (new ReadyCheckBundle()).asOutput
    val csr_data         = UInt(INPUT, DATA_LEN)
    val earlyRfWrValid   = Bool(OUTPUT)
    val earlyRfWrCtxtId  = UInt(OUTPUT, CTXT_ID_LEN)
    val regfsetToIntpipe = Vec(NUM_MPS+1, new RegfsetToIntpipeBundle().flip())
    val writeToRegfset   = Vec(3, Decoupled(new WriteToRegfsetBundle()))
    val csrUnitIntpipe   = new CsrUnitIntpipeBundle().flip()
    val csrStatus        = Vec(NUM_CTXT, new MStatus()).asInput

    // Signals to the control layer.
    val ex1_instr        = UInt(OUTPUT, INSTR_LEN)
    val mem_wdata        = UInt(OUTPUT, DATA_LEN)
    val mem_instr        = UInt(OUTPUT, INSTR_LEN)

    // Trace bus
    val ipTrace          = new TracedInstruction().asOutput

   // Flags.
    val bpwatch = Vec(coreParams.nBreakpoints, new BPWatch(coreParams.retireWidth)).asOutput
    val cease = Bool().asOutput
    val wfi = Bool().asOutput
    val traceStall = Bool().asInput
  }
}

class PipeUnit()(implicit p: Parameters) extends CoreModule()(p)
    with HasRocketCoreParameters
    with HasCoreIO {

  val decode_tableI = {
    (if (usingMulDiv) new MDecode (false)+: (xLen > 32).option(new M64Decode (false)).toSeq else Nil) ++:
    (if (usingFPU) new FDecode +: (xLen > 32).option(new F64Decode).toSeq else Nil) ++:
    (if (usingFPU && xLen > 32) Seq(new DDecode, new D64Decode) else Nil) ++:
    (usingRoCC.option(new RoCCDecode)) ++:
    ((xLen > 32).option(new I64Decode)) ++:
    (usingSupervisor.option(new SDecode)) ++:
    (usingDebug.option(new DebugDecode)) ++:
    Seq(new IDecode)
  } flatMap(_.table)

  val decode_tableI_RVC = {
    ((xLen > 32).option(new I64DecodeR)) ++:
    Seq(new IDecodeR)
  } flatMap(_.table)

  val decode_tableM = {
    (if (usingAtomics) new ADecodeM +: (xLen > 32).option(new A64DecodeM).toSeq else Nil) ++:
    (if (usingFPU) Seq(new FDecodeM) else Nil) ++:
    (if (usingFPU && xLen > 32) Seq(new DDecodeM) else Nil) ++:
    ((xLen > 32).option(new I64DecodeM)) ++:
    (usingSupervisor.option(new SDecodeM)) ++:
    Seq(new IDecodeM)
  } flatMap(_.table)

  val decode_tableM_RVC = {
    (if (usingFPU && xLen > 32) Seq(new DDecodeMR) else Nil) ++:
    ((xLen > 32).option(new I64DecodeMR)) ++:
    Seq(new IDecodeMR)
  } flatMap(_.table)

  val decode_tableF = {
    (if (usingFPU) Seq(new FDecodeF) else Nil) ++:
    (if (usingFPU && xLen > 32) Seq(new DDecodeF) else Nil)
  } flatMap(_.table)

  val ex1_ctrl_reg           = Reg(init=new IntCtrlSigsBUN().fromBits(0))
  val ex1_ctxtId_reg         = Reg(init=UInt(0, CTXT_ID_LEN))
  val ex1_valid_reg          = Reg(init=Bool(false))
  val ex1_interruptAck_reg   = Reg(init=Bool(false))
  val ex1_rdData1_reg        = Reg(init=UInt(0, DATA_LEN))
  val ex1_rdData2_reg        = Reg(init=UInt(0, DATA_LEN))
  val ex1_rsMulDiv_reg       = Reg(init=Vec.fill(2) {UInt(0, DATA_LEN)})
  val ex1_rsMulL_reg         = Reg(init=Vec.fill(2) {UInt(0, DATA_LEN)})
  val ex1_immA_reg           = Reg(init=UInt(0, 13)) // Used for address offset.
  val ex1_addrBase_reg       = Reg(init=UInt(0, DATA_LEN))
  val ex1_pc_reg             = Reg(init=UInt(0, ADDR_LEN))
  val ex1_inst_reg           = Reg(init=UInt(0, INSTR_LEN))
  val ex1_instRVC_reg        = Reg(init=UInt(0, INSTR_LEN/2))
  val ex1_immB_reg           = Reg(init=UInt(0, 32))
  val ex1_illegal_reg        = Reg(init=UInt(0, 3))
  val ex1_rvc_reg            = Reg(init=Bool(false))
  val ex1_bpu_debug_if_reg   = Reg(init=Bool(false))
  val ex1_bpu_xcpt_if_reg    = Reg(init=Bool(false))
  val ex1_xcpt_pf_reg        = Reg(init=Bool(false))
  val ex1_xcpt_ae_reg        = Reg(init=Bool(false))
  val ex1_illegal_insn_reg   = Reg(init=Bool(false))
  val ex1_rfWrite_reg        = Reg(init=Bool(false))
  val ex1_validM_reg         = Reg(init=Vec.fill(NUM_MPS) {Bool(false)})
  val ex1_rdData1M_reg       = Reg(init=Vec.fill(NUM_MPS) {UInt(0, DATA_LEN)})
  val ex1_rdData2M_reg       = Reg(init=Vec.fill(NUM_MPS) {UInt(0, DATA_LEN)})
  val ex1_immM_reg           = Reg(init=Vec.fill(NUM_MPS) {UInt(0, 13)})
  val ex1_instM_reg          = Reg(init=Vec.fill(NUM_MPS) {UInt(0, INSTR_LEN)})
  val ex1_pcM_reg            = Reg(init=Vec.fill(NUM_MPS) {UInt(0, ADDR_LEN)})
  val ex1_ctxtIdM_reg        = Reg(init=Vec.fill(NUM_MPS) {UInt(0, CTXT_ID_LEN)})
  val ex1_ctrlM_reg          = Reg(init=Vec.fill(NUM_MPS) {new IntCtrlSigsBUN().fromBits(0)})
  val ex1_csrStatus_reg      = Reg(init=new MStatus().fromBits(0))

  val ex2_ctrl_reg           = Reg(init=new IntCtrlSigsBUN().fromBits(0))
  val ex2_ctxtId_reg         = Reg(init=UInt(0, CTXT_ID_LEN))
  val ex2_valid_reg          = Reg(init=Bool(false))
  val ex2_enaBypass_reg      = Reg(init=Bool(false))
  val ex2_xcpt_reg           = Reg(init=Bool(false))
  val ex2_cause_reg          = Reg(init=UInt(0, DATA_LEN))
  val ex2_pc_reg             = Reg(init=UInt(0, ADDR_LEN))
  val ex2_inst_reg           = Reg(init=UInt(0, INSTR_LEN))
  val ex2_op1_reg            = Reg(init=SInt(0, DATA_LEN))
  val ex2_op2_reg            = Reg(init=SInt(0, DATA_LEN))
  val ex2_addsub_p1_reg      = Reg(init=UInt(0, 23))
  val ex2_addsub_p2_reg      = Reg(init=UInt(0, 22))
  val ex2_addsub_p3_reg      = Reg(init=UInt(0, 21))
  val ex2_addSub_reg         = Reg(init=Bool(false))
  val ex2_cmpInv_reg         = Reg(init=Bool(false))
  val ex2_cmpEq_reg          = Reg(init=Bool(false))
  val ex2_isCmp_reg          = Reg(init=Bool(false))
  val ex2_jalX_reg           = Reg(init=Bool(false))
  val ex2_reg_shout_r        = Reg(init=UInt(0, xLen))
  val ex2_rs_reg             = Reg(init=Vec.fill(2) {UInt(0, DATA_LEN)})
  val ex2_br_misalign_reg    = Reg(init=Bool(false))
  val ex2_xor_reg            = Reg(init=UInt(0, DATA_LEN))
  val ex2_equal_reg          = Reg(init=Bool(false))
  val ex2_interruptAck_reg   = Reg(init=Bool(false))
  val ex2_op2Greater_reg     = Reg(init=Bool(false))
  val ex2_cmpUnsigned_reg    = Reg(init=Bool(false))
  val ex2_br_target1_reg     = Reg(init=UInt(0, ADDR_LEN))
  val ex2_csrAddr_reg        = Reg(init=UInt(0, CSR.ADDRSZ))
  val ex2_sfenceM_reg        = Reg(init=Vec.fill(NUM_MPS) {Bool(false)})
  val ex2_br_target2_reg     = Reg(init=SInt(0, ADDR_LEN))
  val ex2_alu_out            = Wire(UInt())
  val ex2_rfWrite_reg        = Reg(init=Bool(false))
  val ex2_validM_reg         = Reg(init=Vec.fill(NUM_MPS) {Bool(false)})
  val ex2_instM_reg          = Reg(init=Vec.fill(NUM_MPS) {UInt(0, INSTR_LEN)})
  val ex2_pcM_reg            = Reg(init=Vec.fill(NUM_MPS) {UInt(0, ADDR_LEN)})
  val ex2_ctxtIdM_reg        = Reg(init=Vec.fill(NUM_MPS) {UInt(0, CTXT_ID_LEN)})
  val ex2_ctrlM_reg          = Reg(init=Vec.fill(NUM_MPS) {new IntCtrlSigsBUN().fromBits(0)})
  val ex2_addrM_reg          = Reg(init=Vec.fill(NUM_MPS) {UInt(0, DATA_LEN)})
  val ex2_rdData2M_reg       = Reg(init=Vec.fill(NUM_MPS) {UInt(0, DATA_LEN)})
  val ex2_rvc_reg            = Reg(init=Bool(false))
  val ex2_csrStatus_reg      = Reg(init=new MStatus().fromBits(0))

  val mem_ctrl_reg           = Reg(init=new IntCtrlSigsBUN().fromBits(0))
  val mem_ctxtId_reg         = Reg(init=UInt(0, CTXT_ID_LEN))
  val mem_valid_reg          = Reg(init=Bool(false))
  val mem_enaBypass_reg      = Reg(init=Bool(false))
  val mem_xcpt_reg           = Reg(init=Bool(false))
  val mem_cause_reg          = Reg(init=UInt(0, DATA_LEN))
  val mem_pc_reg             = Reg(init=UInt(0, ADDR_LEN))
  val mem_inst_reg           = Reg(init=UInt(0, INSTR_LEN))
  val mem_dataToWrite        = Wire(UInt())
  val mem_instOrder_reg      = Reg(init=UInt(0, INSTR_LEN))
  val mem_wdata_reg          = Reg(init=UInt(0, DATA_LEN))
  val mem_rs2_reg            = Reg(init=UInt(0, DATA_LEN))
  val mem_rs_reg             = Reg(init=Vec.fill(2) {UInt(0, DATA_LEN)})
  val mem_br_target2_reg     = Reg(init=SInt(0, ADDR_LEN))
  val mem_interruptAck_reg   = Reg(init=Bool(false))
  val mem_cycle_cntr_reg     = Reg(init=UInt(0, 40))
  val mem_time_latch_reg     = Reg(init=Vec.fill(NUM_CTXT) {UInt(0, 32)})
  val mem_cntr_reg           = Reg(init=Vec.fill(NUM_CTXT) {UInt(0, 32)})
  val mem_rfWrite_reg        = Reg(init=Bool(false))
  val mem_flush_icache_reg   = Reg(init=Bool(false))
  val mem_validM_reg         = Reg(init=Vec.fill(NUM_MPS) {Bool(false)})
  val mem_instM_reg          = Reg(init=Vec.fill(NUM_MPS) {UInt(0, INSTR_LEN)})
  val mem_pcM_reg            = Reg(init=Vec.fill(NUM_MPS) {UInt(0, ADDR_LEN)})
  val mem_ctxtIdM_reg        = Reg(init=Vec.fill(NUM_MPS) {UInt(0, CTXT_ID_LEN)})
  val mem_ctrlM_reg          = Reg(init=Vec.fill(NUM_MPS) {new IntCtrlSigsBUN().fromBits(0)})
  val mem_addrM_reg          = Reg(init=Vec.fill(NUM_MPS) {UInt(0, DATA_LEN)})
  val mem_rdData1M_reg       = Reg(init=Vec.fill(NUM_MPS) {UInt(0, DATA_LEN)})
  val mem_csrStatus_reg      = Reg(init=new MStatus().fromBits(0))

  val wb_reg_ctrl            = Reg(init=new IntCtrlSigsBUN().fromBits(0))
  val wb_reg_ctxtId          = Reg(init=UInt(0, CTXT_ID_LEN))
  val wb_reg_valid           = Reg(init=Bool(false))
  val wb_reg_validP          = Reg(init=Bool(false))
  val wb_mute_reg            = Reg(init=Bool(false))

  // Monitor any flow alter to mute next instructions from the same CTXT.
  val flowAltMaskD1_reg      = Reg(init=UInt(0, NUM_CTXT))
  val flowAltMaskD2_reg      = Reg(next=flowAltMaskD1_reg, init=UInt(0, NUM_CTXT))
  val flowAltMaskD3_reg      = Reg(next=flowAltMaskD2_reg, init=UInt(0, NUM_CTXT))
  val flowAltMaskP2_reg      = Reg(next=flowAltMaskD1_reg | flowAltMaskD2_reg | flowAltMaskD3_reg, init=UInt(0, NUM_CTXT))
  flowAltMaskD1_reg := UInt(0)
  when(io.pipeUCtxtU.flowAltered) {
    val flowAltMaskD0 = Vec.fill(NUM_CTXT) {Wire(Bool(false))}
    flowAltMaskD0(ex2_ctxtId_reg) := Bool(true)
    flowAltMaskD1_reg             := flowAltMaskD0.asUInt
  }
  val flowAltMask   = flowAltMaskD1_reg | flowAltMaskP2_reg
  val exx_muteInstr = io.csrUnitIntpipe.exx_muteInstr.asUInt | flowAltMask

  val ex2_mute_reg           = Reg(next=exx_muteInstr(ex1_ctxtId_reg), init=Bool(false))
  val mem_mute_reg           = Reg(next=exx_muteInstr(ex2_ctxtId_reg), init=Bool(false))

  val ex2_muteME_reg        = Reg(init=Vec.fill(NUM_MPS) {Bool(false)})
  for(mps <- 0 until NUM_MPS) {
    ex2_muteME_reg(mps) := exx_muteInstr(ex1_ctxtIdM_reg(mps))
  }

  //**********************************
  // FPU connecting path.
  //**********************************
  val ex1_validF_reg  = Reg(next=io.ctxtUPipeU.idc_validF, init=Bool(false))
  val ex2_validF_reg  = Reg(next=ex1_validF_reg, init=Bool(false))
  val mem_validF_reg  = Reg(next=ex2_validF_reg, init=Bool(false))
  val ex1_instrF_reg  = RegEnable(next=io.ctxtUPipeU.instF, enable=io.ctxtUPipeU.idc_validF, init=UInt(0, INSTR_LEN))
  val ex2_instrF_reg  = RegEnable(next=ex1_instrF_reg, enable=ex1_validF_reg, init=UInt(0, INSTR_LEN))
  val mem_instrF_reg  = RegEnable(next=ex2_instrF_reg, enable=ex2_validF_reg, init=UInt(0, INSTR_LEN))
  val ex1_ctxtIdF_reg = RegEnable(next=io.ctxtUPipeU.ctxtIdF, enable=io.ctxtUPipeU.idc_validF, init=UInt(0, CTXT_ID_LEN))
  val ex2_ctxtIdF_reg = RegEnable(next=ex1_ctxtIdF_reg, enable=ex1_validF_reg, init=UInt(0, CTXT_ID_LEN))
  val mem_ctxtIdF_reg = RegEnable(next=ex2_ctxtIdF_reg, enable=ex2_validF_reg, init=UInt(0, CTXT_ID_LEN))

  //**********************************
  // Decode Stage 1
  //**********************************
  val idc_inst      = io.ctxtUPipeU.inst.asUInt
  val idc_instRVC   = Cat(UInt(0,15),io.ctxtUPipeU.inst(31,25),io.ctxtUPipeU.inst(14,12),io.ctxtUPipeU.inst(6,0))

  val idc_ctrl_32   = Wire(new IntCtrlSigs()).decode(idc_inst, decode_tableI)
  val idc_ctrl_RVC  = Wire(new IntCtrlSigs()).decode(idc_instRVC, decode_tableI_RVC)
  val idc_ctrl      = Mux(io.ctxtUPipeU.isRvc, idc_ctrl_RVC, idc_ctrl_32)

  // ID_DEC_TO_CTXT: Control signals to the Context table.
  io.pipeUCtxtU.ctxtIdDec      := io.ctxtUPipeU.ctxtId

  // Mark the instructions that will change the registers.
  io.pipeUCtxtU.checkRegHazard := idc_ctrl.wxd
  io.pipeUCtxtU.checkFpuHazard := idc_ctrl.wfd

  // Check for illegal instruction.
  val idc_illegal_insn = !idc_ctrl.legal || idc_ctrl.rocc && io.csrUnitIntpipe.rocc_illegal // TODO: MIGRATION: Move to ex2 when it is only valid.

  // Get branch direction; 0: for forward, 1: for backward.
  val idc_branchDir = Mux(io.ctxtUPipeU.isRvc, ImmGenRVC(IMM_SB, idc_instRVC), ImmGen(IMM_SB, idc_inst))(31)

  // Mark the instructions that requires much wait to end of the execution in the CTXT.
  // (this instruction is before the one that will be interrupted).
  // Note: ae and pf are considered inside CTXT.
  // We do not wait in the case of a conditional branching with a -ve direction.
  val idc_xptDet                = io.csrUnitIntpipe.bpu_debug_if || io.csrUnitIntpipe.bpu_xcpt_if || io.ctxtUPipeU.pf || io.ctxtUPipeU.ae
  io.pipeUCtxtU.isWaitEndExec  := (idc_ctrl.end_flag && !(idc_ctrl.isCondBranch && !idc_branchDir)) || io.csrUnitIntpipe.bpu_debug_if || io.csrUnitIntpipe.bpu_xcpt_if

  // Mark the conditional branching with backward direction
  io.pipeUCtxtU.isBackBranch   := idc_ctrl.isCondBranch && idc_branchDir
  io.pipeUCtxtU.isCondBranch   := idc_ctrl.isCondBranch

  // isLdSt='1' for load/store instruction. And '0' for otherwise.
  io.pipeUCtxtU.isInfLoop := (idc_inst === INF_LOOP_INSTRS) || (idc_instRVC(15,0) === INF_LOOP_RVC)
  io.pipeUCtxtU.isFenc    := idc_ctrl.fence

  //**********************************
  // Decode Stage
  //**********************************
  val idc_csr_en       = idc_ctrl.csr.isOneOf(CSR.S, CSR.C, CSR.W)
  val idc_system_insn  = idc_ctrl.csr === CSR.I
  val idc_csr_ren      = idc_ctrl.csr.isOneOf(CSR.S, CSR.C) && (idc_inst(19, 15) === UInt(0))

  // The last register bypass stage.
  val enaBypassA = ex2_enaBypass_reg && (ex2_ctxtId_reg === io.ctxtUPipeU.ctxtId) && !ex2_mute_reg
  val enaBypassB = mem_enaBypass_reg && (mem_ctxtId_reg === io.ctxtUPipeU.ctxtId) && !mem_mute_reg
  val rdData1    = Mux(enaBypassB && (mem_inst_reg(11,7) === idc_inst(19,15)), mem_dataToWrite, Mux(enaBypassA && (ex2_inst_reg(11,7) === idc_inst(19,15)), ex2_alu_out, io.regfsetToIntpipe(0).rdData1))
  val rdData2    = Mux(enaBypassB && (mem_inst_reg(11,7) === idc_inst(24,20)), mem_dataToWrite, Mux(enaBypassA && (ex2_inst_reg(11,7) === idc_inst(24,20)), ex2_alu_out, io.regfsetToIntpipe(0).rdData2))

  // Check if we need only to read from the CSR.
  val idc_csrRdOnly                = (idc_ctrl.csr === CSR.S || idc_ctrl.csr === CSR.C) && (idc_inst(19,15) === UInt(0))

  // Make sure not to return ack if 1) exception happens, or 2) a CSR instruction.
  ex1_interruptAck_reg  := io.ctxtUPipeU.idc_validI && io.csrUnitIntpipe.interrupt(io.ctxtUPipeU.ctxtId) && !idc_xptDet && (idc_ctrl.csr === CSR.N)

  ex1_valid_reg         := io.ctxtUPipeU.idc_validI
  when(io.ctxtUPipeU.idc_validI) {
    ex1_illegal_reg       := Cat(idc_ctrl.rocc && !io.csrStatus(io.ctxtUPipeU.ctxtId).xs.orR, idc_ctrl.fp   && !io.csrStatus(io.ctxtUPipeU.ctxtId).fs.orR, !idc_ctrl.legal)
    ex1_bpu_debug_if_reg  := io.csrUnitIntpipe.bpu_debug_if
    ex1_bpu_xcpt_if_reg   := io.csrUnitIntpipe.bpu_xcpt_if
    ex1_xcpt_pf_reg       := io.ctxtUPipeU.pf
    ex1_xcpt_ae_reg       := io.ctxtUPipeU.ae

    ex1_ctrl_reg          := idc_ctrl

    ex1_ctrl_reg.csr      := Mux(idc_csrRdOnly, CSR.R, idc_ctrl.csr)
    ex1_ctxtId_reg        := io.ctxtUPipeU.ctxtId
    ex1_inst_reg          := idc_inst
    ex1_instRVC_reg       := idc_instRVC
    ex1_pc_reg            := io.ctxtUPipeU.pc
    ex1_immB_reg          := Mux(io.ctxtUPipeU.isRvc, ImmGenRVC(idc_ctrl.sel_imm, idc_instRVC).asUInt, ImmGen(idc_ctrl.sel_imm, idc_inst).asUInt)
    ex1_rdData1_reg       := rdData1
    ex1_rdData2_reg       := rdData2
    ex1_csrStatus_reg     := io.csrStatus(io.ctxtUPipeU.ctxtId)

    // Double register the operands to be used for MUL/DIV.
    when(idc_ctrl.div) {
      when(idc_ctrl.alu_fn === FN_MUL) {
        ex1_rsMulL_reg(0) := rdData1
        ex1_rsMulL_reg(1) := rdData2
      }
      .otherwise {
        ex1_rsMulDiv_reg(0) := rdData1
        ex1_rsMulDiv_reg(1) := rdData2
      }
    }

    ex1_rvc_reg           := io.ctxtUPipeU.isRvc
    ex1_rfWrite_reg       := idc_ctrl.wxd && !idc_ctrl.mem && !idc_ctrl.div && !idc_ctrl.rocc
    ex1_illegal_insn_reg  := idc_illegal_insn || (idc_csr_en && (io.csrUnitIntpipe.read_illegal || !idc_csr_ren && io.csrUnitIntpipe.write_illegal)) ||
                             (!io.ctxtUPipeU.isRvc && (((idc_ctrl.mem && (idc_ctrl.mem_cmd === M_SFENCE)) || idc_system_insn) && io.csrUnitIntpipe.system_illegal)) ||
                             idc_ctrl.fp && (io.csrUnitIntpipe.fp_illegal)

    // pass PC down ALU writeback pipeline for tval
    when (io.csrUnitIntpipe.bpu_xcpt_if || io.csrUnitIntpipe.bpu_debug_if || io.ctxtUPipeU.pf || io.ctxtUPipeU.ae) {
      ex1_ctrl_reg.alu_fn   := ALU.FN_ADD
      ex1_ctrl_reg.alu_dw   := DW_XPR
      ex1_ctrl_reg.sel_alu1 := A1_RS1 // tval := instruction
      ex1_ctrl_reg.sel_alu2 := A2_ZERO
      when (io.csrUnitIntpipe.bpu_xcpt_if || io.ctxtUPipeU.pf || io.ctxtUPipeU.ae) { // tval := PC
        ex1_ctrl_reg.sel_alu1 := A1_PC
      }
      when (!io.csrUnitIntpipe.bpu_xcpt_if && (io.ctxtUPipeU.pfS || (!io.ctxtUPipeU.pf && io.ctxtUPipeU.aeS))) { // tval := PC+2
        ex1_ctrl_reg.sel_alu2 := A2_SIZE
        ex1_rvc_reg           := true
      }
    }
  }

  val imm2               = Mux(io.ctxtUPipeU.isRvc, SInt(0,13), ImmGen(idc_ctrl.sel_imm, idc_inst).asSInt)
  when(io.ctxtUPipeU.idc_validI && (idc_ctrl.mem || idc_ctrl.jalr)) {
    when(idc_ctrl.sel_alu2 === A2_IMM) {
      ex1_immA_reg           := imm2(12,0)
    }
    .otherwise {
      ex1_immA_reg           := UInt(0)
    }
    ex1_addrBase_reg      := rdData1
  }

  for(mps <- 0 until NUM_MPS) {
    val isRvcM        = io.ctxtUPipeU.instM(mps)(1,0) =/= UInt(3)
    val idc_instM     = io.ctxtUPipeU.instM(mps)

    val idc_instM_RVC = Cat(UInt(0,16),io.ctxtUPipeU.instM(mps)(30,25),io.ctxtUPipeU.instM(mps)(14,12),io.ctxtUPipeU.instM(mps)(6,0))

    val idc_ctrlM_32  = Wire(new IntCtrlSigs()).decode(idc_instM, decode_tableM)
    val idc_ctrlM_RVC = Wire(new IntCtrlSigs()).decode(idc_instM_RVC, decode_tableM_RVC)

    val idc_ctrlM     = Mux(isRvcM, idc_ctrlM_RVC, idc_ctrlM_32)

    io.pipeUCtxtU.isFencM(mps)         := idc_ctrlM.fence || (idc_ctrlM.amo && idc_instM(26)) // Acquire
    io.pipeUCtxtU.isLdStM(mps)         := idc_ctrlM.mem
    io.pipeUCtxtU.isStM(mps)           := ((idc_ctrlM.mem_cmd === M_XWR) || (idc_ctrlM.mem_cmd === M_XSC)) && idc_ctrlM.mem
    io.pipeUCtxtU.isAmoLrScM(mps)      := ((idc_ctrlM.mem_cmd =/= M_XWR) && (idc_ctrlM.mem_cmd =/= M_XRD)) && idc_ctrlM.mem
    io.pipeUCtxtU.isFenciM(mps)        := idc_ctrlM.fence_i
    io.pipeUCtxtU.checkRegHazardM(mps) := idc_ctrlM.wxd
    io.pipeUCtxtU.checkFpuHazardM(mps) := idc_ctrlM.wfd
    io.pipeUCtxtU.isWaitEndExecM(mps)  := idc_ctrlM.end_flag

    // Makes the FPU listen to the memory pipe (M), so it can serve the FP store instruction.
    io.pipeFpu.idc_validM(mps) := io.ctxtUPipeU.idc_validM(mps) && idc_ctrlM.fp
    io.pipeFpu.idc_ctxtIdM(mps):= io.ctxtUPipeU.ctxtIdM(mps)

    val imm2M   = Mux(isRvcM, ImmGenMRVC(idc_ctrlM.sel_imm, idc_instM_RVC).asSInt,
                  ImmGen(idc_ctrlM.sel_imm, idc_instM).asSInt)

    when(isRvcM){
      val fld_instM = Cat(imm2M, idc_instM(19,15), UInt(3,3), idc_instM(11,7), UInt(0x07,7))
      val fsd_instM = Cat(imm2M(11,5), idc_instM(24,15), UInt(3,3), imm2M(4,0), UInt(0x27,7))

      io.pipeFpu.idc_instM(mps)  := Mux(idc_ctrlM_RVC.wfd, fld_instM, fsd_instM)
    }
    .otherwise{
      io.pipeFpu.idc_instM(mps)  := idc_instM
    }

    ex1_validM_reg(mps) := io.ctxtUPipeU.idc_validM(mps)
    when(io.ctxtUPipeU.idc_validM(mps)) {
      val enaBypassAM = ex2_enaBypass_reg && (ex2_ctxtId_reg === io.ctxtUPipeU.ctxtIdM(mps)) && !ex2_mute_reg
      val enaBypassBM = mem_enaBypass_reg && (mem_ctxtId_reg === io.ctxtUPipeU.ctxtIdM(mps)) && !mem_mute_reg
      ex1_rdData1M_reg(mps)  := Mux(enaBypassBM && (mem_inst_reg(11,7) === idc_instM(19,15)), mem_dataToWrite, Mux(enaBypassAM && (ex2_inst_reg(11,7) === idc_instM(19,15)), ex2_alu_out, io.regfsetToIntpipe(1+mps).rdData1))
      ex1_rdData2M_reg(mps)  := Mux(enaBypassBM && (mem_inst_reg(11,7) === idc_instM(24,20)), mem_dataToWrite, Mux(enaBypassAM && (ex2_inst_reg(11,7) === idc_instM(24,20)), ex2_alu_out, io.regfsetToIntpipe(1+mps).rdData2))

      when(idc_ctrlM.sel_alu2 === A2_IMM) {
        ex1_immM_reg(mps) := imm2M(12, 0).asUInt
      }
      .otherwise {
        ex1_immM_reg(mps) := UInt(0)
      }

      ex1_ctrlM_reg(mps)   := idc_ctrlM
      ex1_instM_reg(mps)   := idc_instM
      ex1_pcM_reg(mps)     := io.ctxtUPipeU.pcM(mps)
      ex1_ctxtIdM_reg(mps) := io.ctxtUPipeU.ctxtIdM(mps)
    }
  }

  //**********************************
  // Execute Stage 1
  //**********************************
  val ex1_op1 = MuxLookup(ex1_ctrl_reg.sel_alu1, SInt(0), Seq(
    A1_RS1 -> ex1_rdData1_reg.asSInt,
    A1_PC  -> ex1_pc_reg.asSInt))
  val ex1_op2 = MuxLookup(ex1_ctrl_reg.sel_alu2, SInt(0), Seq(
    A2_RS2  -> ex1_rdData2_reg.asSInt,
    A2_IMM  -> ex1_immB_reg.asSInt,
    A2_SIZE -> Mux(ex1_rvc_reg, SInt(2), SInt(4))))
  val ex1_op2Shift    = Mux(ex1_ctrl_reg.sel_alu2 === A2_RS2, ex1_rdData2_reg, ex1_immB_reg)
  val ex1_in2_inv     = Mux(isSub(ex1_ctrl_reg.alu_fn), ~ex1_op2.asUInt, ex1_op2.asUInt)

  // Calculate the memory address access.
  // Note: we need to consider all the DATA_LEN bits before applying encodeVirtualAddress.
  val ex1_addrH = Mux(ex1_immA_reg(12), ex1_addrBase_reg(DATA_LEN - 1, 12) - UInt(1), ex1_addrBase_reg(DATA_LEN - 1, 12) + UInt(1))
  val ex1_addrL = ex1_immA_reg + ex1_addrBase_reg(11, 0)
  val ex1_addr  = Cat(Mux(ex1_addrL(12), ex1_addrH, ex1_addrBase_reg(DATA_LEN - 1, 12)), ex1_addrL(11,0))

  val ex1_illegal_insn = ex1_illegal_insn_reg ||
    ex1_ctrl_reg.div && !ex1_csrStatus_reg.isa('m'-'a') ||
    ex1_ctrl_reg.dp && !ex1_csrStatus_reg.isa('d'-'a') ||
    ex1_rvc_reg && !ex1_csrStatus_reg.isa('c'-'a')
  val ex1_illegal      = Cat(ex1_ctrl_reg.rocc && !ex1_csrStatus_reg.xs.orR, ex1_ctrl_reg.fp   && !ex1_csrStatus_reg.fs.orR, !ex1_ctrl_reg.legal)

  val (ex1_xcpt, ex1_cause) = checkExceptions(List(
    (ex1_interruptAck_reg,  io.csrUnitIntpipe.interrupt_cause(ex1_ctxtId_reg)),
    (ex1_bpu_debug_if_reg,  UInt(CSR.debugTriggerCause)),
    (ex1_bpu_xcpt_if_reg,   UInt(Causes.breakpoint)),
    (ex1_xcpt_pf_reg,       UInt(Causes.fetch_page_fault)),
    (ex1_xcpt_ae_reg,       UInt(Causes.fetch_access)),
    (ex1_illegal_insn || (ex1_illegal_reg =/= UInt(0)),  UInt(Causes.illegal_instruction))))

  // SLL, SRL, SRA
  val (shamt, shin_r) =
    if (xLen == 32) {
      (ex1_op2Shift(4,0).asUInt, ex1_rdData1_reg)
    }
    else {
      require(xLen == 64)
      val shin_hi_32 = Fill(32, isSub(ex1_ctrl_reg.alu_fn) && ex1_rdData1_reg(31))
      val shin_hi    = Mux(ex1_ctrl_reg.alu_dw === DW_64, ex1_rdData1_reg(63,32).asUInt, shin_hi_32)
      val shamt      = Cat(ex1_op2Shift(5).asUInt & (ex1_ctrl_reg.alu_dw === DW_64), ex1_op2Shift(4,0).asUInt)
      (shamt, Cat(shin_hi, ex1_rdData1_reg(31,0).asUInt))
    }
  val shin          = Mux(ex1_ctrl_reg.alu_fn === FN_SR  || ex1_ctrl_reg.alu_fn === FN_SRA, shin_r, Reverse(shin_r))

  val ex1_immJ      = Mux(ex1_rvc_reg, ImmGenRVC(IMM_UJ, ex1_instRVC_reg), ImmGen(IMM_UJ, ex1_inst_reg))
  val ex1_immBranch = Mux(ex1_rvc_reg, ImmGenRVC(IMM_SB, ex1_instRVC_reg), ImmGen(IMM_SB, ex1_inst_reg))
  val ex1_invVal    = Mux(ex1_ctrl_reg.branch, ex1_immBranch, ex1_immJ).asSInt

  ex2_valid_reg           := ex1_valid_reg
  ex2_xcpt_reg            := ex1_xcpt & ex1_valid_reg
  ex2_interruptAck_reg    := ex1_interruptAck_reg
  ex2_enaBypass_reg       := ex1_valid_reg && ex1_ctrl_reg.wxd && (ex1_inst_reg(11,7) =/= UInt(0))

  // Reset the branching when !ex1_valid_reg.
  ex2_ctrl_reg.branch := Bool(false)
  ex2_jalX_reg        := Bool(false)

  when(ex1_valid_reg) {
    ex2_reg_shout_r  := (Cat(isSub(ex1_ctrl_reg.alu_fn) & shin(xLen-1), shin).asSInt >> shamt)(xLen-1,0)

    // ID_1HALF_ADD: Add ex1_op1 + ex1_op2
    // This logic serves both the added and subtractor; "ex1_op2" is modified to consider subtraction operation.
    // To split the add, do both lower half and top half add in this cycle, then handle carry in next cycle
    ex2_addsub_p1_reg     := Cat(Fill(1, UInt(0)), ex1_op1(21,  0)) + Cat(Fill(1, UInt(0)), ex1_in2_inv(21,  0)) + isSub(ex1_ctrl_reg.alu_fn) //"Fill" makes empty spot for carry out
    ex2_addsub_p2_reg     := Cat(Fill(1, UInt(0)), ex1_op1(42, 22)) + Cat(Fill(1, UInt(0)), ex1_in2_inv(42, 22)) //"Fill" makes empty spot for carry out
    ex2_addsub_p3_reg     := ex1_op1(63, 43) + ex1_in2_inv(63, 43)

    // Check the ALU function
    ex2_addSub_reg        := ex1_ctrl_reg.alu_fn === FN_ADD || ex1_ctrl_reg.alu_fn === FN_SUB
    ex2_cmpInv_reg        := cmpInverted(ex1_ctrl_reg.alu_fn)
    ex2_cmpEq_reg         := cmpEq(ex1_ctrl_reg.alu_fn)
    ex2_isCmp_reg         := isCmp(ex1_ctrl_reg.alu_fn)

    // The increment value.
    ex2_br_misalign_reg:= ex1_invVal(1)

    ex2_xor_reg        := (ex1_op1.asUInt ^ ex1_in2_inv)
    ex2_equal_reg      := (ex1_op1.asUInt === ex1_in2_inv)
    ex2_ctxtId_reg     := ex1_ctxtId_reg
    ex2_cause_reg      := ex1_cause
    ex2_pc_reg         := ex1_pc_reg
    ex2_inst_reg       := ex1_inst_reg
    ex2_rs_reg(0)      := ex1_rdData1_reg
    ex2_rs_reg(1)      := ex1_rdData2_reg
    ex2_op1_reg        := ex1_op1
    ex2_op2_reg        := ex1_op2
    ex2_jalX_reg       := ex1_ctrl_reg.jalr || ex1_ctrl_reg.jal
    ex2_br_target2_reg := ex1_pc_reg.asSInt + Mux(ex1_rvc_reg, SInt(2), SInt(4))
    ex2_rfWrite_reg    := ex1_rfWrite_reg
    ex2_ctrl_reg       := ex1_ctrl_reg
    ex2_rvc_reg        := ex1_rvc_reg
    ex2_csrStatus_reg  := ex1_csrStatus_reg

    // Check which one is greater.
    ex2_op2Greater_reg     := (ex1_op2 > ex1_op1)

    ex2_cmpUnsigned_reg    := Mux(cmpUnsigned(ex1_ctrl_reg.alu_fn), ex1_op2(xLen-1).asUInt, ex1_op1(xLen-1).asUInt)

    when(ex1_ctrl_reg.branch || ex1_ctrl_reg.jal) {
      ex2_br_target1_reg      := (ex1_pc_reg.asSInt + ex1_invVal).asUInt
    }
    when(ex1_ctrl_reg.jalr) {
      ex2_br_target1_reg      := encodeVirtualAddress(ex1_addr, ex1_addr)
    }

    when(ex1_ctrl_reg.csr =/= CSR.N) {
      ex2_csrAddr_reg := ex1_inst_reg(31,20)
    }
  }

  ex2_validM_reg := ex1_validM_reg
  val ex1_addrM = Vec.fill(NUM_MPS) {Wire(UInt(width=DATA_LEN))}
  for(mps <- 0 until NUM_MPS) {
    val ex1_addrHM   = Mux(ex1_immM_reg(mps)(12), ex1_rdData1M_reg(mps)(DATA_LEN - 1, 12) - UInt(1), ex1_rdData1M_reg(mps)(DATA_LEN - 1, 12) + UInt(1))
    val ex1_addrLM   = ex1_immM_reg(mps) + ex1_rdData1M_reg(mps)(11, 0)
    ex1_addrM(mps)  := Cat(Mux(ex1_addrLM(12), ex1_addrHM, ex1_rdData1M_reg(mps)(DATA_LEN - 1, 12)), ex1_addrLM(11,0))

    when(ex1_validM_reg(mps)) {
      ex2_ctrlM_reg(mps)    := ex1_ctrlM_reg(mps)
      ex2_instM_reg(mps)    := ex1_instM_reg(mps)
      ex2_pcM_reg(mps)      := ex1_pcM_reg(mps)
      ex2_ctxtIdM_reg(mps)  := ex1_ctxtIdM_reg(mps)
      ex2_addrM_reg(mps)    := ex1_addrM(mps)
      ex2_rdData2M_reg(mps) := ex1_rdData2M_reg(mps)

      // Add a special case for sfence.
      ex2_sfenceM_reg(mps)    := Bool(false)
      when (Bool(usingSupervisor) && (ex1_ctrlM_reg(mps).mem_cmd === M_SFENCE)) {
        // (19,15) is the field of RS1, and (24,20) is RS2 field.
        ex2_ctrlM_reg(mps).mem_type := Cat(ex1_instM_reg(mps)(24,20) =/= UInt(0), ex1_instM_reg(mps)(19,15) =/= UInt(0))
        ex2_sfenceM_reg(mps)        := Bool(true)

        // If we are going to change the ptbr, we need to clear the TLB via an SFENCE.
        when(ex1_ctrlM_reg(mps).csr =/= CSR.N) {
          ex2_ctrlM_reg(mps).mem_type := UInt(0)
        }
      }
    }
  }

  //**********************************
  // Execute Stage 2
  //**********************************
  // ID_2HALF_ADD: Second half of the addition/subtraction -- here, just handle carry out from lower parts.
  val ex2_addsub_p2T  = ex2_addsub_p2_reg + UInt(1)
  val ex2_addsub_p2   = Mux(ex2_addsub_p1_reg(22), ex2_addsub_p2T, ex2_addsub_p2_reg)
  val ex2_addsub_p3T  = ex2_addsub_p3_reg + UInt(1)
  val ex2_addsub_p3   = Mux(ex2_addsub_p2_reg(21) || (ex2_addsub_p2_reg(20, 0).andR && ex2_addsub_p1_reg(22)), ex2_addsub_p3T, ex2_addsub_p3_reg)

  val ex2_adder_out   = Cat(ex2_addsub_p3(20, 0), ex2_addsub_p2(20, 0), ex2_addsub_p1_reg(21, 0))

  // SLT, SLTU
  val ex2_cmp_out     = ex2_cmpInv_reg ^
    Mux(ex2_cmpEq_reg, ex2_equal_reg,
    Mux(ex2_op1_reg(xLen-1).asUInt === ex2_op2_reg(xLen-1).asUInt, ex2_op2Greater_reg, ex2_cmpUnsigned_reg))

  val shout_l = Reverse(ex2_reg_shout_r)
  val shout   = Mux(ex2_ctrl_reg.alu_fn === FN_SR || ex2_ctrl_reg.alu_fn === FN_SRA, ex2_reg_shout_r, UInt(0)) |
                Mux(ex2_ctrl_reg.alu_fn === FN_SL,                     shout_l, UInt(0))

  // AND, OR, XOR
  val ex2_logic   = Mux(ex2_ctrl_reg.alu_fn === FN_XOR || ex2_ctrl_reg.alu_fn === FN_OR, ex2_xor_reg, UInt(0)) |
                    Mux(ex2_ctrl_reg.alu_fn === FN_OR || ex2_ctrl_reg.alu_fn === FN_AND, ex2_op1_reg.asUInt & ex2_op2_reg.asUInt, UInt(0))
  val out         = Mux(ex2_addSub_reg, ex2_adder_out, (ex2_isCmp_reg && ex2_cmp_out) | ex2_logic | shout)

  ex2_alu_out := out
  if (xLen > 32) {
    require(xLen == 64)
    when (ex2_ctrl_reg.alu_dw === DW_32) {
      ex2_alu_out := Cat(Fill(32, out(31)), out(31,0))
    }
  }

  // multiplier and divider
  val mulDivParam         = MulDivParams()
  val div                 = 0 to NUM_DIV - 1 map {x => Module(new Div(cfg = mulDivParam)).io} // NUM_DIV instances of the divider.
  val mulL                = Module(new MulL(cfg = mulDivParam)).io // Normal multiplier that has a pipelined architecture.
  val mulH                = Module(new MulH(cfg = mulDivParam)).io // Special multiplier that is has an iterative architecture.
  val isMulL              = (ex1_ctrl_reg.alu_fn === FN_MUL)
  val isMulH              = (ex1_ctrl_reg.alu_fn === FN_MULH) || (ex1_ctrl_reg.alu_fn === FN_MULHU) || (ex1_ctrl_reg.alu_fn === FN_MULHSU)
  for(i <- 0 until NUM_DIV) {
    val sel = Wire(Bool())
    sel := Bool(true)
    for(k <- 0 until i) {
      when(div(k).req.ready === Bool(true)) {
        sel := Bool(false)
      }
    }
    div(i).req.valid         := ex1_valid_reg && ex1_ctrl_reg.div && sel && !(isMulL || isMulH)
    div(i).req.bits.dw       := ex1_ctrl_reg.alu_dw
    div(i).req.bits.fn       := ex1_ctrl_reg.alu_fn
    div(i).req.bits.in1      := ex1_rsMulDiv_reg(0)
    div(i).req.bits.in2      := ex1_rsMulDiv_reg(1)
    div(i).req.bits.destReg  := ex1_inst_reg(11,7)
    div(i).req.bits.ctxtId   := ex1_ctxtId_reg
    div(i).req.bits.isRealD3 := Reg(next=(!mem_xcpt_reg && !mem_mute_reg), init=Bool(false)) && !wb_mute_reg
  }
  assert(!(div(NUM_DIV - 1).req.valid && !div(NUM_DIV - 1).req.ready), "MUL/DIV is not ready.")

  mulL.req.valid         := ex1_valid_reg && ex1_ctrl_reg.div && isMulL
  mulL.req.bits.dw       := ex1_ctrl_reg.alu_dw
  mulL.req.bits.fn       := ex1_ctrl_reg.alu_fn
  mulL.req.bits.in1      := ex1_rsMulL_reg(0)
  mulL.req.bits.in2      := ex1_rsMulL_reg(1)
  mulL.req.bits.destReg  := ex1_inst_reg(11,7)
  mulL.req.bits.ctxtId   := ex1_ctxtId_reg
  mulL.req.bits.isRealD3 := Reg(next=(!mem_xcpt_reg && !mem_mute_reg), init=Bool(false)) && !wb_mute_reg
  assert(!(mulL.req.valid && !mulL.req.ready), "MulL is not ready.")

  mulH.req.valid         := ex1_valid_reg && ex1_ctrl_reg.div && isMulH
  mulH.req.bits.dw       := ex1_ctrl_reg.alu_dw
  mulH.req.bits.fn       := ex1_ctrl_reg.alu_fn
  mulH.req.bits.in1      := ex1_rsMulDiv_reg(0)
  mulH.req.bits.in2      := ex1_rsMulDiv_reg(1)
  mulH.req.bits.destReg  := ex1_inst_reg(11,7)
  mulH.req.bits.ctxtId   := ex1_ctxtId_reg
  mulH.req.bits.isRealD3 := Reg(next=(!mem_xcpt_reg && !mem_mute_reg), init=Bool(false)) && !wb_mute_reg
  assert(!(mulH.req.valid && !mulH.req.ready), "MulH is not ready.")

  io.ex1_instr           := ex1_inst_reg

  val ex2_npc_misaligned = !ex2_csrStatus_reg.isa('c'-'a') && Mux(ex2_ctrl_reg.jalr, ex2_adder_out(1), ex2_br_misalign_reg)
  val ex2_taken_cfi      = (ex2_ctrl_reg.branch && ex2_cmp_out) || ex2_jalX_reg

  val (ex2_xcpt, ex2_cause) = checkExceptions(List(
    (ex2_xcpt_reg, ex2_cause_reg),
    (ex2_ctrl_reg.fp && io.pipeFpu.ex2_illegal_rm && usingFPU, UInt(Causes.illegal_instruction)),
    (ex2_valid_reg && ex2_taken_cfi && ex2_npc_misaligned, UInt(Causes.misaligned_fetch))
  ))

  // Send an early notification flag for not taken branch.
  io.pipeUCtxtU.endExecValidEarly   := ex2_valid_reg && ex2_ctrl_reg.branch && !ex2_cmp_out && !ex2_xcpt_reg
  io.pipeUCtxtU.ctxtIdEndExecEarly  := ex2_ctxtId_reg

  // Connect to DataUnit.
  for(mps <- 0 until NUM_MPS) {
    // One cycle early signals.
    io.mpipeDataUnit(mps).validEarly  := ex1_validM_reg(mps) && ex1_ctrlM_reg(mps).mem
    io.mpipeDataUnit(mps).isRealEarly := io.mpipeDataUnit(mps).validEarly && !flowAltMask(ex1_ctxtIdM_reg(mps)) // CAUTION: This signal does not consider exx_muteInstr, it shall be used with the CSR.
    io.mpipeDataUnit(mps).ctxtIdEarly := ex1_ctxtIdM_reg(mps)
    io.mpipeDataUnit(mps).addr        := encodeVirtualAddress(ex1_rdData1M_reg(mps), ex1_addrM(mps))

    // Signals synchronized with the valid signal.
    io.mpipeDataUnit(mps).justTlbValid := ex2_validM_reg(mps)
    io.mpipeDataUnit(mps).valid        := ex2_validM_reg(mps)
    io.mpipeDataUnit(mps).ctxtId       := ex2_ctxtIdM_reg(mps)
    io.mpipeDataUnit(mps).changeReg    := ex2_ctrlM_reg(mps).wxd || ex2_ctrlM_reg(mps).wfd
    io.mpipeDataUnit(mps).destReg      := ex2_instM_reg(mps)(11,7)
    io.mpipeDataUnit(mps).isFpuAccess  := ex2_ctrlM_reg(mps).fp && Bool(usingFPU)
    io.mpipeDataUnit(mps).memOp        := ex2_ctrlM_reg(mps).mem_cmd
    io.mpipeDataUnit(mps).size         := ex2_ctrlM_reg(mps).mem_type
    io.mpipeDataUnit(mps).isReal       := (!exx_muteInstr(ex2_ctxtIdM_reg(mps)))

    // Connect the data to DataUnit. It is one clock cycle delayed.
    io.mpipeDataUnit(mps).s1_dataToStore   := Mux(mem_ctrlM_reg(mps).fp && Bool(usingFPU), io.pipeFpu.mem_store_data(mps), mem_rdData1M_reg(mps))
  }

  // Inform CTXT about the flow alter.
  io.pipeUCtxtU.flowAltered  := ex2_taken_cfi && !ex2_mute_reg // It is guaranteed that this is 0 when !ex2_valid_reg. We ignore considering 1) any exception and 2) any "just" activated mute, as they will be ignored.
  io.pipeUCtxtU.alteredPc    := Cat(ex2_br_target1_reg(ADDR_LEN-1, 1), Bool(false))

  // Make sure that ex2_taken_cfi is asserted when ex2_valid_reg is valid.
  // This is to make sure io.pipeUCtxtU.flowAltered is asserted only when it is required.
  assert(!ex2_taken_cfi || ex2_valid_reg)

  // Connect fence signals to the CTXT.
  for(mps <- 0 until NUM_MPS) {
    io.pipeUCtxtU.flowAlteredM(mps)  := ex2_sfenceM_reg(mps) && !ex2_muteME_reg(mps) // No need to check the valid. We ignore considering 1) any exception and 2) any "just" activated mute, as they will be ignored.
    io.pipeUCtxtU.alteredPcM(mps)    := Cat(ex2_addrM_reg(mps)(ADDR_LEN-1, 1), Bool(false))
    io.pipeUCtxtU.sfence_rs1(mps)    := ex2_ctrlM_reg(mps).mem_type(0)
    io.pipeUCtxtU.sfence_rs2(mps)    := ex2_ctrlM_reg(mps).mem_type(1)
  }

  // We need to flush the PTW and iTLB when we write to satp.
  val ex2_isPtbr_reg = Reg(next=(ex1_ctrl_reg.csr =/= CSR.R) && (ex1_ctrl_reg.csr =/= CSR.N) && (ex1_inst_reg(31, 20) === CSRs.satp), init=Bool(false))
  io.pipeUCtxtU.sfenceFlush := !ex2_mute_reg && !ex2_xcpt_reg && ex2_isPtbr_reg // No need to check the valid. We ignore considering 1) any exception and 2) any "just" activated mute, as they will be ignored.

  mem_valid_reg              := ex2_valid_reg
  mem_xcpt_reg               := Bool(false)
  mem_interruptAck_reg       := ex2_interruptAck_reg
  mem_flush_icache_reg       := Bool(false)
  mem_enaBypass_reg          := ex2_enaBypass_reg
  when (ex2_valid_reg) {
    mem_ctrl_reg           := ex2_ctrl_reg
    mem_ctxtId_reg         := ex2_ctxtId_reg
    mem_pc_reg             := ex2_pc_reg
    mem_wdata_reg          := ex2_alu_out
    mem_rs_reg             := ex2_rs_reg
    mem_cause_reg          := ex2_cause
    mem_br_target2_reg     := ex2_br_target2_reg
    mem_rfWrite_reg        := ex2_rfWrite_reg
    mem_csrStatus_reg      := ex2_csrStatus_reg

    mem_inst_reg           := ex2_inst_reg
    mem_instOrder_reg      := Mux(ex2_rvc_reg, Cat(ex2_inst_reg(30, 25), ex2_inst_reg(14, 12), ex2_inst_reg(6, 0)), ex2_inst_reg)

    // Make sure not to produce exceptions if the instruction already muted.
    mem_xcpt_reg           := ex2_xcpt && !ex2_mute_reg

    when (ex2_ctrl_reg.rxs2 && ex2_ctrl_reg.rocc) {
      val typ = Mux(ex2_ctrl_reg.rocc, log2Ceil(xLen/8).U, ex2_ctrl_reg.mem_type)
      mem_rs2_reg := new StoreGen(typ, 0.U, ex2_rs_reg(1), coreDataBytes).data
    }

    when (ex2_ctrl_reg.jalr && ex2_csrStatus_reg.debug) {
      mem_flush_icache_reg := Bool(true)
    }
  }

  mem_validM_reg := ex2_validM_reg
  for(mps <- 0 until NUM_MPS) {
    when(ex2_validM_reg(mps)) {
      mem_ctrlM_reg(mps)    := ex2_ctrlM_reg(mps)
      mem_instM_reg(mps)    := ex2_instM_reg(mps)
      mem_pcM_reg(mps)      := ex2_pcM_reg(mps)
      mem_ctxtIdM_reg(mps)  := ex2_ctxtIdM_reg(mps)
      mem_addrM_reg(mps)    := ex2_addrM_reg(mps)
      mem_rdData1M_reg(mps) := new StoreGen(ex2_ctrlM_reg(mps).mem_type, 0.U, ex2_rdData2M_reg(mps), coreDataBytes).data
    }
  }

  //**********************************
  // Memory Stage
  //**********************************
  // hook up control/status regfile
  io.csrUnitIntpipe.idc_addr         := idc_inst(31,20)
  io.csrUnitIntpipe.ex1_ctxtId       := ex1_ctxtId_reg
  io.csrUnitIntpipe.ex1_ctxtIdF      := ex1_ctxtIdF_reg
  io.csrUnitIntpipe.ex2_ctxtId       := ex2_ctxtId_reg
  io.csrUnitIntpipe.ex2_addr         := ex2_csrAddr_reg
  io.csrUnitIntpipe.ex2_cmd          := Mux(ex2_valid_reg && !ex2_xcpt_reg && !flowAltMask(ex2_ctxtId_reg), ex2_ctrl_reg.csr, CSR.N) // CAUTION: the CSR considers exx_muteInstr internally.
  io.csrUnitIntpipe.wdata            := RegEnable(next=ex2_alu_out, enable=ex2_valid_reg && ((ex2_ctrl_reg.csr =/= CSR.N) || ex2_xcpt), init=UInt(0, DATA_LEN))

  // Used to inform the CSR about exception.
  io.csrUnitIntpipe.mem_ctxtId       := mem_ctxtId_reg
  io.csrUnitIntpipe.mem_exception    := mem_xcpt_reg && mem_valid_reg
  io.csrUnitIntpipe.pc               := mem_pc_reg
  io.csrUnitIntpipe.cause            := mem_cause_reg
  io.csrUnitIntpipe.mem_valid        := mem_valid_reg

  for(mps <- 0 until NUM_MPS) {
    io.csrUnitIntpipe.retireM(mps)        := (mem_validM_reg(mps) && !Reg(next=exx_muteInstr(ex2_ctxtIdM_reg(mps)), init=Bool(false)))
    io.csrUnitIntpipe.ctxtIdRetireM(mps)  := mem_ctxtIdM_reg(mps)
    io.csrUnitIntpipe.ex1_ctxtIdM(mps)    := ex1_ctxtIdM_reg(mps)
    io.csrUnitIntpipe.mem_addrM(mps)      := mem_addrM_reg(mps)
    io.csrUnitIntpipe.mem_pcM(mps)        := mem_pcM_reg(mps)
  }

  // Do not acknowledge the interrupt if the instruction is muted.
  io.csrUnitIntpipe.mem_interruptAck := mem_interruptAck_reg && !mem_mute_reg

  // Connect BP signals.
  io.csrUnitIntpipe.bpu_wdata    := ex2_alu_out // TODO: MIGRATION: This shall be aligned to mem cycle to relax timing. It is moved to create the xcpt earlier.
  io.csrUnitIntpipe.bpu_valid    := ex2_valid_reg
  io.csrUnitIntpipe.bpu_ctxtId   := ex2_ctxtId_reg
  io.csrUnitIntpipe.idc_pc       := io.ctxtUPipeU.pc
  io.csrUnitIntpipe.idc_validI   := io.ctxtUPipeU.idc_validI
  io.csrUnitIntpipe.idc_ctxtId   := io.ctxtUPipeU.ctxtId
  io.csrUnitIntpipe.idc_validCSR := io.ctxtUPipeU.idc_validI && (idc_ctrl.csr =/= CSR.N)

  // Connection to ROCC and FPU to CSR.
  io.csrUnitIntpipe.fcsr_flags0      := io.pipeFpu.fcsr_flags0
  io.csrUnitIntpipe.fcsr_ctxtId0     := io.pipeFpu.fcsr_ctxtId0
  io.csrUnitIntpipe.fcsr_flags1      := io.pipeFpu.fcsr_flags1
  io.csrUnitIntpipe.fcsr_ctxtId1     := io.pipeFpu.fcsr_ctxtId1
  io.csrUnitIntpipe.fcsr_flags2      := io.pipeFpu.fcsr_flags2
  io.csrUnitIntpipe.fcsr_ctxtId2     := io.pipeFpu.fcsr_ctxtId2
  io.csrUnitIntpipe.fcsr_flags3      := io.pipeFpu.fcsr_flags3
  io.csrUnitIntpipe.fcsr_ctxtId3     := io.pipeFpu.fcsr_ctxtId3
  io.csrUnitIntpipe.rocc_interrupt   := io.rocc.interrupt
  io.pipeFpu.ex1_fcsr_rm            := io.csrUnitIntpipe.ex1_fcsr_rm
  io.pipeFpu.ex1_fcsr_rmF           := io.csrUnitIntpipe.ex1_fcsr_rmF

  // Connect to the control layer.
  io.mem_wdata             := mem_wdata_reg
  io.mem_instr             := mem_instOrder_reg

  // For JALR, write the PC+4.
  val mem_int_wdata       = Mux(mem_ctrl_reg.jalr, mem_br_target2_reg.asSInt, mem_wdata_reg.asSInt).asUInt

  // Connect retire signal to the CSR for instruction counter.
  io.csrUnitIntpipe.retire        := (mem_valid_reg && !mem_mute_reg && !mem_xcpt_reg)
  io.csrUnitIntpipe.ctxtIdRetire  := mem_ctxtId_reg
  io.csrUnitIntpipe.retireF       := mem_validF_reg
  io.csrUnitIntpipe.ctxtIdRetireF := mem_ctxtIdF_reg

  wb_reg_valid          := mem_valid_reg
  wb_reg_validP         := Bool(false)
  when (mem_valid_reg) {
    wb_reg_ctrl           := mem_ctrl_reg
    wb_reg_ctxtId         := mem_ctxtId_reg
    wb_reg_validP         := Bool(true)
  }

  //**********************************
  // Write-back stage
  //**********************************
  io.pipeUCtxtU.flush_icache   := mem_flush_icache_reg

  //**********************************
  // Connect to the Register File Set.
  //**********************************
  // writeback arbitration the following (ordered according to the priority) can access the RF:
  //   1- PipeUnit.
  //   2- DMEM.
  //   3- ROCC.
  //   4- DIV.
  // The last three have a ready signal to control the output.
  // Connect the write bus to the register file.
  val mem_rfWrite                        = mem_valid_reg && mem_rfWrite_reg
  val mem_csr_data_reg                   = RegEnable(next=io.csr_data, enable=ex2_valid_reg && (ex2_ctrl_reg.csr =/= CSR.N), init=UInt(0, DATA_LEN))
  wb_mute_reg                           := io.csrUnitIntpipe.exx_muteInstr(mem_ctxtId_reg)
  mem_dataToWrite                       := Mux(mem_ctrl_reg.csr =/= CSR.N, mem_csr_data_reg, Mux(mem_ctrl_reg.fp && mem_ctrl_reg.wxd && usingFPU, io.pipeFpu.mem_toint_data, mem_int_wdata))

  io.writeToRegfset(0).valid            := !wb_mute_reg && Reg(next=mem_rfWrite && !mem_mute_reg, init=Bool(false)) // The mute needs to be considered here to track any instruction comes after a flow alteration.
  io.writeToRegfset(0).bits.isReal      := Reg(next=(!mem_xcpt_reg && !io.csrUnitIntpipe.csr_flowAlter), init=Bool(false))
  io.writeToRegfset(0).bits.dataToWrite := RegEnable(next=mem_dataToWrite, enable=mem_rfWrite, init=UInt(0, DATA_LEN))
  io.writeToRegfset(0).bits.destReg     := RegEnable(next=mem_inst_reg(11,7), enable=mem_rfWrite, init=UInt(0, REG_ID_LEN))
  io.writeToRegfset(0).bits.ctxtId      := RegEnable(next=mem_ctxtId_reg, enable=mem_rfWrite, init=UInt(0, CTXT_ID_LEN))

  // This is for early notification to prepare the RF.
  io.earlyRfWrValid                     := mem_valid_reg && Reg(next=ex2_ctrl_reg.wxd && !ex2_ctrl_reg.mem && !ex2_ctrl_reg.div && !ex2_ctrl_reg.rocc, init=Bool(false))
  io.earlyRfWrCtxtId                    := mem_ctxtId_reg

  if (usingRoCC) {
    io.writeToRegfset(2).valid            := io.rocc.resp.fire()
    io.writeToRegfset(2).bits.isReal      := Bool(true) //TODO
    io.writeToRegfset(2).bits.dataToWrite := io.rocc.resp.bits.data
    io.writeToRegfset(2).bits.destReg     := io.rocc.resp.bits.rd
    io.writeToRegfset(2).bits.ctxtId      := UInt(0) //TODO
    io.rocc.resp.ready                    := io.writeToRegfset(2).ready
  }
  else {
    io.writeToRegfset(2).valid          := Bool(false)
  }

  //**********************************
  // Connect DIV, FPU and ROCC.
  //**********************************
  val divMulArb = Module(new ArbiterTree(new WriteToRegfsetBundle(), NUM_DIV + 2))
  for(i <- 0 until NUM_DIV) {
    divMulArb.io.in(i) <> div(i).resp
  }
  divMulArb.io.in(NUM_DIV)   <> mulL.resp
  divMulArb.io.in(NUM_DIV+1) <> mulH.resp
  io.writeToRegfset(1)       <> divMulArb.io.out

  io.pipeUCtxtU.ctxtIdDiv   := divMulArb.io.out.bits.ctxtId
  io.pipeUCtxtU.divSafe     := OrTree((0 to NUM_DIV - 1) map { x =>  div(x).resp.fire()})
  io.pipeUCtxtU.mulLSafe    := mulL.resp.fire()
  io.pipeUCtxtU.mulHSafe    := mulH.resp.fire()

  // The FPU just listening and connects.
  // Makes the FPU listen to the integer pipe (I).
  io.pipeFpu.idc_validI       := io.ctxtUPipeU.idc_validI && idc_ctrl.fp
  io.pipeFpu.idc_ctxtId       := io.ctxtUPipeU.ctxtId
  io.pipeFpu.idc_inst         := idc_inst
  io.pipeFpu.ex2_fromint_data := ex2_rs_reg(0)

  // Makes the FPU connects to the its instructions (F).
  io.pipeFpu.idc_validF       := io.ctxtUPipeU.idc_validF
  io.pipeFpu.idc_ctxtIdF      := io.ctxtUPipeU.ctxtIdF
  io.pipeFpu.idc_instF        := io.ctxtUPipeU.instF
  io.pipeFpu.mxx_isReal       := Mux(mem_xcpt_reg, ~exx_muteInstr.asUInt & ~UIntToOH(mem_ctxtId_reg, NUM_CTXT), ~exx_muteInstr.asUInt)

  io.rocc.cmd.valid         := mem_valid_reg && mem_ctrl_reg.rocc
  io.rocc.exception         := Bool(false)
  io.rocc.cmd.bits.status   := mem_csrStatus_reg
  io.rocc.cmd.bits.inst     := new RoCCInstruction().fromBits(mem_instOrder_reg)
  io.rocc.cmd.bits.rs1      := mem_wdata_reg
  io.rocc.cmd.bits.rs2      := mem_rs2_reg

  //**********************************
  // Connect the bus needed to check the registerFile future access.
  //**********************************
  io.duReadyCheck.validM     := io.ctxtUPipeU.idc_validI && idc_ctrl.wxd && !idc_ctrl.mem && !idc_ctrl.div && !idc_ctrl.rocc
  io.duReadyCheck.validFpuM  := io.ctxtUPipeU.idc_validI && idc_ctrl.wfd && !idc_ctrl.mem
  io.duReadyCheck.ctxtIdM    := io.ctxtUPipeU.ctxtId
  io.duReadyCheck.valid0     := ex1_valid_reg && ex1_ctrl_reg.wxd && !ex1_ctrl_reg.mem && !ex1_ctrl_reg.div && !ex1_ctrl_reg.rocc
  io.duReadyCheck.validFpu0  := ex1_valid_reg && ex1_ctrl_reg.wfd && !ex1_ctrl_reg.mem
  io.duReadyCheck.ctxtId0    := ex1_ctxtId_reg
  io.duReadyCheck.valid1     := ex2_valid_reg && ex2_ctrl_reg.wxd && !ex2_ctrl_reg.mem && !ex2_ctrl_reg.div && !ex2_ctrl_reg.rocc
  io.duReadyCheck.validFpu1  := ex2_valid_reg && ex2_ctrl_reg.wfd && !ex2_ctrl_reg.mem
  io.duReadyCheck.ctxtId1    := ex2_ctxtId_reg
  io.duReadyCheck.valid2     := mem_valid_reg && mem_ctrl_reg.wxd && !mem_ctrl_reg.mem && !mem_ctrl_reg.div && !mem_ctrl_reg.rocc
  io.duReadyCheck.validFpu2  := mem_valid_reg && mem_ctrl_reg.wfd && !mem_ctrl_reg.mem
  io.duReadyCheck.ctxtId2    := mem_ctxtId_reg

  io.duReadyCheck.validFpu1F := io.ctxtUPipeU.idc_validF
  io.duReadyCheck.ctxtId1F   := io.ctxtUPipeU.ctxtIdF
  io.duReadyCheck.validFpu2F := ex1_validF_reg
  io.duReadyCheck.ctxtId2F   := ex1_ctxtIdF_reg
  io.duReadyCheck.validFpu3F := ex2_validF_reg
  io.duReadyCheck.ctxtId3F   := ex2_ctxtIdF_reg

  //**********************************
  // Generate debug log..
  //**********************************
  mem_cycle_cntr_reg := mem_cycle_cntr_reg + UInt(1)
  when(mem_valid_reg && !io.csrUnitIntpipe.csr_flowAlter && !mem_mute_reg && !mem_xcpt_reg) {
    if(DEBUG == 2) {
      printf("C%x_%x: %x %x %x  [%x %x] %d %x\n", io.hartid, mem_ctxtId_reg, mem_cntr_reg(mem_ctxtId_reg), mem_pc_reg, mem_instOrder_reg, Mux(mem_ctrl_reg.rxs1, mem_rs_reg(0), UInt(0, DATA_LEN)), Mux(mem_ctrl_reg.rxs2, mem_rs_reg(1), UInt(0, DATA_LEN)), mem_ctrl_reg.instrId, mem_cycle_cntr_reg)
      //printf("C%x_%x: %x %x %x  [%x %x] %d %d\n", io.hartid, mem_ctxtId_reg, mem_cntr_reg(mem_ctxtId_reg), mem_pc_reg, mem_instOrder_reg, Mux(mem_ctrl_reg.rxs1, mem_rs_reg(0), UInt(0)), Mux(mem_ctrl_reg.rxs2, mem_rs_reg(1), UInt(0)), mem_cycle_cntr_reg - mem_time_latch_reg(mem_ctxtId_reg), mem_ctrl_reg.instrId)
    }
    mem_time_latch_reg(mem_ctxtId_reg) := mem_cycle_cntr_reg
    mem_cntr_reg(mem_ctxtId_reg)       := mem_cntr_reg(mem_ctxtId_reg) + UInt(1)
  }

  for(mps <- 0 until NUM_MPS) {
    when(mem_validM_reg(mps) && !Reg(next=exx_muteInstr(ex2_ctxtIdM_reg(mps)), init=Bool(false))) {
      if(DEBUG == 2) {
        printf("C%x_%x: %x %x %x  [%x %x] %d %x\n", io.hartid, mem_ctxtIdM_reg(mps), mem_cntr_reg(mem_ctxtIdM_reg(mps)), mem_pcM_reg(mps), mem_instM_reg(mps), Mux(mem_ctrlM_reg(mps).rxs1, mem_addrM_reg(mps), UInt(0, DATA_LEN)), Mux(mem_ctrlM_reg(mps).rxs2, mem_rdData1M_reg(mps), UInt(0, DATA_LEN)), mem_ctrlM_reg(mps).instrId, mem_cycle_cntr_reg)
        //printf("C%x_%x: %x %x %x  [%x %x] %d %d\n", io.hartid, mem_ctxtIdM_reg(mps), mem_cntr_reg(mem_ctxtIdM_reg(mps)), mem_pcM_reg(mps), mem_instM_reg(mps), Mux(mem_ctrlM_reg(mps).rxs1, mem_addrM_reg(mps), UInt(0)), Mux(mem_ctrlM_reg(mps).rxs2, mem_rdData1M_reg(mps), UInt(0)), mem_cycle_cntr_reg - mem_time_latch_reg(mem_ctxtIdM_reg(mps)), mem_ctrlM_reg(mps).instrId)
      }
      mem_time_latch_reg(mem_ctxtIdM_reg(mps)) := mem_cycle_cntr_reg
      mem_cntr_reg(mem_ctxtIdM_reg(mps))       := mem_cntr_reg(mem_ctxtIdM_reg(mps)) + UInt(1)
    }
  }

  // Connect the trace bus.
  io.ipTrace.valid     := Mux(mem_valid_reg || !mem_validM_reg(0), Cat(mem_valid_reg, mem_ctxtId_reg), Cat(mem_validM_reg(0), mem_ctxtIdM_reg(0)))
  io.ipTrace.iaddr     := Mux(mem_valid_reg || !mem_validM_reg(0), mem_pc_reg, mem_pcM_reg(0))
  io.ipTrace.insn      := Mux(mem_valid_reg || !mem_validM_reg(0), mem_instOrder_reg, mem_instM_reg(0))
  // io.ipTrace.valid1F   := Cat(mem_validF_reg, mem_ctxtIdF_reg)
  //io.ipTrace.insn1F    := mem_instrF_reg
  // io.ipTrace.valid1M0  := Cat(mem_validM_reg(0) && !Reg(next=exx_muteInstr(ex2_ctxtIdM_reg(0)), init=Bool(false)), mem_ctxtIdM_reg(0))
  // if(NUM_MPS > 1) {
  //   io.ipTrace.valid2M0  := Cat(mem_validM_reg(1) && !Reg(next=exx_muteInstr(ex2_ctxtIdM_reg(1)), init=Bool(false)), mem_ctxtIdM_reg(1))
  // }
  io.ipTrace.exception := !io.csrUnitIntpipe.csr_flowAlter && !mem_mute_reg && !mem_xcpt_reg

  def coverExceptions(exceptionValid: Bool, cause: UInt, labelPrefix: String, coverCausesLabels: Seq[(Int, String)]): Unit = {
    for ((coverCause, label) <- coverCausesLabels) {
      cover(exceptionValid && (cause === UInt(coverCause)), s"${labelPrefix}_${label}")
    }
  }

  def checkExceptions(x: Seq[(Bool, UInt)]) =
    (OrTree(x.map(_._1)), PriorityMux(x))
}

