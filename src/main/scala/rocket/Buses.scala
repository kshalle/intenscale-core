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

//**************************************************************************
// Define the buses that connect between different units.
//--------------------------------------------------------------------------
//
// Armia Salib
// modified by Sean Halle
// 2016 Oct 16
// See LICENSE.txt for license details.
//
//
package superThread

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.tile.FPConstants._
import freechips.rocketchip.rocket._

// Define the signals between the context table and the integer pipeline used inside superThread.
// The direction is set from Integer pipeline prospective; for INPUT signals, it is input to the integer
// pipeline and output from the context table.
class CtxtUPipeUBundle()(implicit p: Parameters) extends IntenBundle  {
  // *************** CYCLE 0 ***************
  // when the context unit has no instr to send, it generates a nop, so no "instr valid" signal

  // The context unit sends the ID of the context that this instr comes from.
  // This signal emerges from latch at the start of the cycle
  val inst        = UInt(width = INSTR_LEN)
  val pc          = UInt(width = ADDR_LEN)
  val idc_validI  = Bool()
  val ctxtId      = UInt(width = CTXT_ID_LEN)
  val isRvc       = Bool() // A flag to mark compressed instruction.

  val instF       = UInt(width = INSTR_LEN)
  val idc_validF  = Bool()
  val ctxtIdF     = UInt(width = CTXT_ID_LEN)

  val instM       = Vec(NUM_MPS, UInt(width = INSTR_LEN))
  val idc_validM  = Vec(NUM_MPS, Bool())
  val ctxtIdM     = Vec(NUM_MPS, UInt(width = CTXT_ID_LEN))
  val pcM         = Vec(NUM_MPS, UInt(width = ADDR_LEN))

  val pf          = Bool()
  val ae          = Bool()
  val pfS         = Bool()
  val aeS         = Bool()
  override def cloneType = { new CtxtUPipeUBundle().asInstanceOf[this.type] }
}

class PipeUCtxtUBundle()(implicit p: Parameters) extends IntenBundle  {
  // *************** CYCLE 0 ***************
  val sfenceFlush   = Bool()
  val sfence_rs1    = Vec(NUM_MPS, Bool())
  val sfence_rs2    = Vec(NUM_MPS, Bool())

  // *************** TIMING of Signals ***************
  // the rest of the signals, below, appear during the cycle, with enough time for ctxt
  // unit to react before the end of the cycle

  // *************** CYCLE 1 ***************
  // isLdSt='1' for load/store instruction. And '0' for otherwise.
  val isLdStM        = Vec(NUM_MPS, Bool())

  // isSt='1' for store instruction. And '0' for otherwise.
  val isStM          = Vec(NUM_MPS, Bool())
  val isAmoLrScM     = Vec(NUM_MPS, Bool())

  // Mark fenc instructions.
  val isFenciM       = Vec(NUM_MPS, Bool())
  val isFencM        = Vec(NUM_MPS, Bool())
  val isFenc         = Bool()

  // Check if we hit an infinte loop to slow down.
  val isInfLoop      = Bool()

  // Mark the instructions that will change the registers.
  val checkRegHazard  = Bool()
  val checkFpuHazard  = Bool()
  val checkRegHazardM = Vec(NUM_MPS, Bool())
  val checkFpuHazardM = Vec(NUM_MPS, Bool())

  // Mark the instructions that requires much wait to end of the execution in the CTXT.
  val isWaitEndExec   = Bool()
  val isWaitEndExecM  = Vec(NUM_MPS, Bool())

  // Mark conditioning branching.
  val isCondBranch    = Bool()
  val isBackBranch    = Bool()

  // Context ID
  val ctxtIdDec      = UInt(width = CTXT_ID_LEN)

  // *************** CYCLE A ***************
  // Only in the case of taken branch or jump, the integer pipeline sends the new PC address
  // along with the context ID it's for, and asserts flowAltered.
  val flowAltered     = Bool()
  val possibleCached  = Bool()
  val alteredPc       = UInt(width = ADDR_LEN)

  val flowAlteredM    = Vec(NUM_MPS, Bool())
  val alteredPcM      = Vec(NUM_MPS, UInt(width = ADDR_LEN))

  val endExecValidEarly   = Bool()
  val ctxtIdEndExecEarly  = UInt(width = CTXT_ID_LEN)

  // Flush ICache and TLB commands.
  val flush_icache   = Bool()

  // *************** CYCLE B ***************
  val divSafe        = Bool()
  val mulLSafe       = Bool()
  val mulHSafe       = Bool()
  val ctxtIdDiv      = UInt(width = CTXT_ID_LEN)

  override def cloneType = { new PipeUCtxtUBundle().asInstanceOf[this.type] }
}

// Bundle used to check the readiness of the registerFile at certain cycle.
class ReadyCheckBundle()(implicit p: Parameters) extends IntenBundle  {
  val validM    = Bool(OUTPUT)
  val validFpuM = Bool(OUTPUT)
  val ctxtIdM   = UInt(OUTPUT, CTXT_ID_LEN)
  val valid0    = Bool(OUTPUT)
  val validFpu0 = Bool(OUTPUT)
  val ctxtId0   = UInt(OUTPUT, CTXT_ID_LEN)
  val valid1    = Bool(OUTPUT)
  val validFpu1 = Bool(OUTPUT)
  val ctxtId1   = UInt(OUTPUT, CTXT_ID_LEN)
  val valid2    = Bool(OUTPUT)
  val validFpu2 = Bool(OUTPUT)
  val ctxtId2   = UInt(OUTPUT, CTXT_ID_LEN)

  val validFpu1F = Bool(OUTPUT)
  val ctxtId1F   = UInt(OUTPUT, CTXT_ID_LEN)
  val validFpu2F = Bool(OUTPUT)
  val ctxtId2F   = UInt(OUTPUT, CTXT_ID_LEN)
  val validFpu3F = Bool(OUTPUT)
  val ctxtId3F   = UInt(OUTPUT, CTXT_ID_LEN)
}

// Define the signals between the pipe and data unit controller (Without data bus).
class PipeUDataUBundleWOD(addrLen : Int = ADDR_LEN)(implicit p: Parameters) extends IntenBundle  {
  // Early one cycle.
  val validEarly            = Bool()
  val addr                  = UInt(width = addrLen)
  val ctxtIdEarly           = UInt(width = CTXT_ID_LEN)
  val isRealEarly           = Bool()

  // Define signals to the DMEM.
  val justTlbValid          = Bool()    // Validate access to the memory for either read or write.
  val valid                 = Bool()    // Validate access to the memory for either read or write.
  val ctxtId                = UInt(width = CTXT_ID_LEN)
  val pipeId                = UInt(width = PIPE_ID_LEN)
  val changeReg             = Bool()
  val destReg               = UInt(width = REG_ID_LEN)
  val isFpuAccess           = Bool()
  val memOp                 = UInt(width = M_SZ)  // Select between read or write (M_XRD or M_XWR).
  val size                  = UInt(width = MT_SZ) // Access size (MT_W, MT_WU, MT_HU or MT_BU)
  val isReal                = Bool()
  val nack_cause            = Bool()

  override def cloneType = { new PipeUDataUBundleWOD(addrLen).asInstanceOf[this.type] }
}

// Define the signals between the pipe and data unit controller.
class PipeUDataUBundle(addrLen : Int = ADDR_LEN)(implicit p: Parameters) extends PipeUDataUBundleWOD(addrLen)  {
  // Delayed one cycle.
  val s1_dataToStore        = UInt(width = DATA_LEN) // In some cases it is one cycle delayed.

  override def cloneType = { new PipeUDataUBundle(addrLen).asInstanceOf[this.type] }
}

// Define the bus used to write to the register file.
class WriteToRegfsetBundle()(implicit p: Parameters) extends IntenBundle  {
  val isReal                 = Bool(OUTPUT)
  val dataToWrite            = UInt(OUTPUT, DATA_LEN)
  val destReg                = UInt(OUTPUT, REG_ID_LEN)
  val ctxtId                 = UInt(OUTPUT, CTXT_ID_LEN)
  val size                   = UInt(OUTPUT, MT_SZ) // Access size (MT_W, MT_WU, MT_HU or MT_BU)
}

// Each row to RF.
class RowToRfIBundle()(implicit p: Parameters) extends IntenBundle  {
  val rdEna1       = Bool(OUTPUT)
  val rdEna2       = Bool(OUTPUT)
  val addr1        = UInt(OUTPUT, REG_ID_LEN)
  val addr2        = UInt(OUTPUT, REG_ID_LEN)
  override def cloneType: this.type = (new RowToRfIBundle).asInstanceOf[this.type]
}

// Define the signals between the CTXT to register file set for read the operands.
class CtxtToRfIBundle()(implicit p: Parameters) extends IntenBundle  {
  val ctxtId       = Vec(NUM_MPS+1, UInt(OUTPUT, CTXT_ID_LEN))
  val rdEna        = Vec(NUM_MPS+1, Bool(OUTPUT))
  val addr1        = Vec(NUM_MPS+1, UInt(OUTPUT, REG_ID_LEN))
  val addr2        = Vec(NUM_MPS+1, UInt(OUTPUT, REG_ID_LEN))

  val rowToRfI     = Vec(NUM_CTXT, new RowToRfIBundle())
  override def cloneType: this.type = (new CtxtToRfIBundle).asInstanceOf[this.type]
}

// Each row to RF.
class RowToRfFBundle()(implicit p: Parameters) extends IntenBundle  {
  val rdEna1       = Bool(OUTPUT)
  val rdEna2       = Bool(OUTPUT)
  val rdEna3       = Bool(OUTPUT)
  val addr1        = UInt(OUTPUT, REG_ID_LEN)
  val addr2        = UInt(OUTPUT, REG_ID_LEN)
  val addr3        = UInt(OUTPUT, REG_ID_LEN)
  override def cloneType: this.type = (new RowToRfFBundle).asInstanceOf[this.type]
}

// Define the signals between the CTXT to register file set for read the operands.
class CtxtToFpuBundle()(implicit p: Parameters) extends IntenBundle  {
  //val ctxtId       = UInt(OUTPUT, CTXT_ID_LEN)
  //val rdEna        = Bool(OUTPUT)
  //val addr1        = UInt(OUTPUT, REG_ID_LEN)
  //val addr2        = UInt(OUTPUT, REG_ID_LEN)
  //val addr3        = UInt(OUTPUT, REG_ID_LEN)

  val rowToRfF     = Vec(NUM_CTXT, new RowToRfFBundle())
  override def cloneType: this.type = (new CtxtToFpuBundle).asInstanceOf[this.type]
}

// Define the signals between the register file set to pipeUnit.
class RegfsetToIntpipeBundle()(implicit p: Parameters) extends IntenBundle  {
  val rdData1                = UInt(OUTPUT, DATA_LEN)
  val rdData2                = UInt(OUTPUT, DATA_LEN)
}


// Define the signals between the pipeUnit and CSR unit controller.
class CsrUnitIntpipeBundle()(implicit p: Parameters) extends IntenBundle  {
  // Define signals to the CSR.
  val mem_exception      = Bool(INPUT) //asserted by pipeUnit when instr throws exception
  val cause              = UInt(INPUT, DATA_LEN)
  val mem_valid          = Bool(INPUT)
  val pc                 = UInt(INPUT, ADDR_LEN) //program counter
  val wdata              = UInt(INPUT, DATA_LEN) //data to write
  val ex2_cmd            = UInt(INPUT, CSR.SZ) //operation to perform to CSR
  val idc_addr           = UInt(INPUT, CSR.ADDRSZ) //csr has several fields -- imm value in instr
  val ex2_addr           = UInt(INPUT, CSR.ADDRSZ) //csr has several fields -- imm value in instr
  val retire             = Bool(INPUT)
  val ctxtIdRetire       = UInt(INPUT, CTXT_ID_LEN)
  val ex1_ctxtId         = UInt(INPUT, CTXT_ID_LEN)
  val ex2_ctxtId         = UInt(INPUT, CTXT_ID_LEN)
  val mem_ctxtId         = UInt(INPUT, CTXT_ID_LEN)
  val mem_interruptAck   = Bool(INPUT)

  val retireF            = Bool(INPUT)
  val ctxtIdRetireF      = UInt(INPUT, CTXT_ID_LEN)
  val ex1_ctxtIdF        = UInt(INPUT, CTXT_ID_LEN)

  val retireM            = Vec(NUM_MPS, Bool(INPUT))
  val ctxtIdRetireM      = Vec(NUM_MPS, UInt(INPUT, CTXT_ID_LEN))
  val ex1_ctxtIdM        = Vec(NUM_MPS, UInt(INPUT, CTXT_ID_LEN))
  val mem_addrM          = Vec(NUM_MPS, UInt(INPUT, DATA_LEN))
  val mem_pcM            = Vec(NUM_MPS, UInt(INPUT, ADDR_LEN))

  // Define the signals from the CSR to integer pipeline.
  val csr_flowAlter       = Bool(OUTPUT)  // when throws an exception/trap
  val interrupt           = Vec(NUM_CTXT , Bool(OUTPUT))  // inform about interrupt.
  val interrupt_cause     = Vec(NUM_CTXT , UInt(OUTPUT, DATA_LEN))

  // Control instruction mute, they are synchronized to the execution and memory stages.
  val exx_muteInstr       = Vec(NUM_CTXT , Bool(OUTPUT))
  val wb_csrStatus        = Bits(OUTPUT, 8)

  // Bus between CSR and FPU.
  val fcsr_flags0         = Valid(Bits(width = FLAGS_SZ)).flip
  val fcsr_ctxtId0        = Bits(INPUT, CTXT_ID_LEN)
  val fcsr_flags1         = Valid(Bits(width = FLAGS_SZ)).flip
  val fcsr_ctxtId1        = Bits(INPUT, CTXT_ID_LEN)
  val fcsr_flags2         = Valid(Bits(width = FLAGS_SZ)).flip
  val fcsr_ctxtId2        = Bits(INPUT, CTXT_ID_LEN)
  val fcsr_flags3         = Valid(Bits(width = FLAGS_SZ)).flip
  val fcsr_ctxtId3        = Bits(INPUT, CTXT_ID_LEN)
  val ex1_fcsr_rm         = Bits(OUTPUT, RM_SZ)
  val ex1_fcsr_rmF        = Bits(OUTPUT, RM_SZ)

  // BPU.
  val bpu_wdata           = Bits(INPUT, DATA_LEN)
  val bpu_valid           = Bool(INPUT)
  val bpu_ctxtId          = UInt(INPUT, CTXT_ID_LEN)
  val bpu_debug_if        = Bool(OUTPUT)
  val bpu_xcpt_if         = Bool(OUTPUT)

  // Decode output.
  val write_flush         = Bool(OUTPUT)
  val fp_illegal          = Bool(OUTPUT)
  val rocc_illegal        = Bool(OUTPUT)
  val read_illegal        = Bool(OUTPUT)
  val write_illegal       = Bool(OUTPUT)
  val system_illegal      = Bool(OUTPUT)

  // To the breakpoint controller.
  val idc_pc              = UInt(INPUT, ADDR_LEN)
  val idc_validI          = Bool(INPUT)
  val idc_ctxtId          = UInt(INPUT, CTXT_ID_LEN)

  val singleStep          = Bool(OUTPUT)
  val idc_validCSR        = Bool(INPUT)

  // TODO: MIGRATE: Do we need the following?
  val rocc_interrupt      = Bool(INPUT)
}

// Define the bus used to connect between the instruction memory and CTXT.
class CtxtUnitInstrUnitBundle()(implicit p: Parameters) extends IntenBundle  {
  // Request side.
  val reqValid        = Bool(OUTPUT)
  val reqAddr         = UInt(OUTPUT, ADDR_LEN)
  val reqCtxtId       = UInt(OUTPUT, CTXT_ID_LEN)
  val reqReady        = Bool(INPUT)
  val sfenceReq_valid = Bool(OUTPUT)
  val sfenceReq_rs1   = Bool(OUTPUT)
  val sfenceReq_rs2   = Bool(OUTPUT)

  // Response side.
  val ae              = Bool(INPUT)
  val pf              = Bool(INPUT)
  val respValidEarly  = Bool(INPUT)
  val instrMemResp    = Vec(CACHE_LINE_LEN / INSTR_LEN, UInt(INPUT, INSTR_LEN))
  val failCauseEarly  = Bool(INPUT)
  val failTlbMiss     = Bool(INPUT)
  val itlbReady       = Bool(INPUT)
  val icacheReady     = Bool(INPUT)

  // Flush the ICache.
  val flush_icache   = Bool(OUTPUT)

  override def clone = { new CtxtUnitInstrUnitBundle().asInstanceOf[this.type] }
}

// Define bus between CSR and CTXT.
class CsrUnitCtxtUnitBundle()(implicit p: Parameters) extends IntenBundle  {
  val tlbSafeR        = Bool()
  val wfi_stall       = Bool()
  val fpActive        = UInt(width = 3)

  val csrFlowAlt      = Bool()
  val csrFlowPc       = UInt(width = ADDR_LEN)
  val isTLBMiss       = Bool()
  val muteD2_reg      = Bool()
  val interruptSlow   = Bool()
  override def cloneType: this.type = (new CsrUnitCtxtUnitBundle).asInstanceOf[this.type]
}

// Define the bus between regfset to CTXT to inform on register write.
class RegfsetToCtxtBundle()(implicit p: Parameters) extends IntenBundle  {
  val regSafe0 = Bool(OUTPUT)
  val isReal0  = Bool(OUTPUT)
  val regId0   = UInt(OUTPUT, REG_ID_LEN)
  val ctxtId0  = UInt(OUTPUT, CTXT_ID_LEN)
}

// Define the bus between regfset to CTXT to inform on dmem write.
class DataUnitToCtxtBundle()(implicit p: Parameters) extends IntenBundle  {
  val ldStSafeEarly        = UInt(width=NUM_CTXT)
  val duHitEarlyX          = Bool()

  val stSafeLate           = Bool()
  val ctxtIdLate           = UInt(width=CTXT_ID_LEN)

  val stSafeLateUn         = Bool()
  val ctxtIdLateUn         = UInt(width=CTXT_ID_LEN)

  val duCtxtEarlyNotifier  = (new DuCtxtEarlyNotifierBundle)
  val duCtxtEarlyNotifier2 = (new DuCtxtEarlyNotifierBundle)
  val duCtxtEarlyNotifier3 = Vec(NUM_MPS, new DuCtxtEarlyNotifierBundle)
}


// Notify the CTXT earlier about the write to the RF.
class DuCtxtEarlyNotifierBundle()(implicit p: Parameters) extends IntenBundle  {
  val valid       = Bool()
  val isLd        = Bool()
  val isSt        = Bool()
  val isFpuAccess = Bool()
  val ctxtId      = UInt(width=CTXT_ID_LEN)
  val destReg     = UInt(width=REG_ID_LEN)
}

// Define the but between DataUnit and the CSR.
// It is used mainly to inform about any exception that may occur inside the dataUnit.
class DataUnitToCsrBundle()(implicit p: Parameters) extends IntenBundle  {
  // TLB Status.
  val ctxtIdEarly    = UInt(OUTPUT, CTXT_ID_LEN)
  val isRealEarly    = Bool(OUTPUT)
  val tlbMiss        = Bool(OUTPUT)
  val tlbSafe        = Bool(OUTPUT)

  // Data memory exceptions.
  val xcptMaSt         = Bool(OUTPUT)
  val xcptMaLd         = Bool(OUTPUT)
  val xcptPfSt         = Bool(OUTPUT)
  val xcptPfLd         = Bool(OUTPUT)
  val xcptAeSt         = Bool(OUTPUT)
  val xcptAeLd         = Bool(OUTPUT)
  val breakpoint       = Bool(OUTPUT)
  val debug_breakpoint = Bool(OUTPUT)
}

class CsrToDataUnitBundle()(implicit p: Parameters) extends IntenBundle  {
  val bpu_xcpt_ld         = Bool()
  val bpu_xcpt_st         = Bool()
  val bpu_debug_ld        = Bool()
  val bpu_debug_st        = Bool()
}

// Bus used to connect FPU to CTXT.
class FpuToCtxtUnitBundle()(implicit p: Parameters) extends IntenBundle  {
  val fpuCtxtId0       = UInt(OUTPUT, CTXT_ID_LEN)
  val fpuSafe0         = Bool(OUTPUT)
  val fpuRegId0        = UInt(OUTPUT, REG_ID_LEN)
  val fpuIsReal0       = Bool(OUTPUT)
  val fpuCtxtId1       = UInt(OUTPUT, CTXT_ID_LEN)
  val fpuSafe1         = Bool(OUTPUT)
  val fpuRegId1        = UInt(OUTPUT, REG_ID_LEN)
  val fpuIsReal1       = Bool(OUTPUT)
  val fpuCtxtId2       = UInt(OUTPUT, CTXT_ID_LEN)
  val fpuSafe2         = Bool(OUTPUT)
  val fpuRegId2        = UInt(OUTPUT, REG_ID_LEN)
  val fpuIsReal2       = Bool(OUTPUT)
  val fpuCtxtId3       = UInt(OUTPUT, CTXT_ID_LEN)
  val fpuSafe3         = Bool(OUTPUT)
  val fpuRegId3        = UInt(OUTPUT, REG_ID_LEN)
  val fpuIsReal3       = Bool(OUTPUT)
}

