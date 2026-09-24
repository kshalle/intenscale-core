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
// CSR Controller.
//--------------------------------------------------------------------------
//
// Armia Salib
// 2016 Oct 24
//
// This module acts as an actuator for the controller of the CSR.
//
//

package superThread

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import scala.collection.mutable.LinkedHashMap

import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants.Util._

// Define the I/O ports.
class CsrUnitIo()(implicit p: Parameters) extends CoreBundle
    with HasCoreParameters
{
  val interrupts      = Vec(NUM_CTXT, new TileInterrupts()).asInput
  val ptw             = Vec(NUM_CTXT, new DatapathPTWIO()).asOutput
  val csrUnitIntpipe  = new CsrUnitIntpipeBundle()
  val csrStatusT      = UInt(width = 4)
  val csrStatus       = Vec(NUM_CTXT, new MStatus().asOutput)
  val dataUnitToCsr   = Vec(NUM_MPS, new DataUnitToCsrBundle().flip())
  val csrToDataUnit   = Vec(NUM_MPS, new CsrToDataUnitBundle().asOutput)
  val csr_data        = UInt(OUTPUT, DATA_LEN)
  val csrUnitCtxtUnit = Vec(NUM_CTXT, new CsrUnitCtxtUnitBundle()).asOutput

  val sys_dump        = Bool(INPUT)
  val hartid          = UInt(INPUT, hartIdLen)
}

class CsrUnit(perfEventSets: EventSets = new EventSets(Seq()), nBreakpoints : Int)(implicit p: Parameters) extends CoreModule()(p) with HasRocketCoreParameters
{
  // Define I/O connections.
  val io   = new CsrUnitIo()

  // Control Status Registers.. The CSRFile can redirect the PC so it's easiest to put this in Execute for now.
  // TODO: Do we need to connect perfEventSets for each CSR instance??
  val csr = 0 to NUM_CTXT - 1 map {x => Module(new CSRFile(perfEventSets, coreParams.customCSRs.decls)).io}

  val ex2_ctxtId  = io.csrUnitIntpipe.ex2_ctxtId
  val mem_ctxtId  = io.csrUnitIntpipe.mem_ctxtId

  val wb_csrStatus                   = Reg(init=Vec.fill(8) {Bool(false)})
  wb_csrStatus(0)                   := io.dataUnitToCsr(0).tlbMiss
  wb_csrStatus(1)                   := (io.dataUnitToCsr(0).xcptMaSt || io.dataUnitToCsr(0).xcptMaLd || io.dataUnitToCsr(0).xcptPfSt || io.dataUnitToCsr(0).xcptPfLd || io.dataUnitToCsr(0).xcptAeSt || io.dataUnitToCsr(0).xcptAeLd || io.dataUnitToCsr(0).breakpoint || io.dataUnitToCsr(0).debug_breakpoint)
  wb_csrStatus(2)                   := csr(0).pendIntrFlag(0)
  wb_csrStatus(3)                   := csr(0).pendIntrFlag(1)
  wb_csrStatus(4)                   := csr(0).pendIntrFlag(2)
  wb_csrStatus(5)                   := csr(0).pendIntrFlag(3)
  wb_csrStatus(6)                   := csr(0).pendIntrFlag(4)
  wb_csrStatus(7)                   := csr(0).pendIntrFlag(5)
  io.csrUnitIntpipe.wb_csrStatus    := wb_csrStatus.asUInt()

  val wb_pcM_reg   = Reg(Vec(NUM_MPS,UInt(width=vaddrBitsExtended)))
  val wb_addr_reg  = Reg(Vec(NUM_MPS,UInt(width=vaddrBitsExtended)))
  val wb_pcME_reg  = Reg(Vec(NUM_MPS,UInt(width=vaddrBitsExtended)))
  val wb_addrE_reg = Reg(Vec(NUM_MPS,UInt(width=vaddrBitsExtended)))
  for (i <- 0 until NUM_MPS) {
    when(io.csrUnitIntpipe.retireM(i)) {
      wb_pcME_reg(i)  := io.csrUnitIntpipe.mem_pcM(i)
      wb_addrE_reg(i) := encodeVirtualAddress(io.csrUnitIntpipe.mem_addrM(i), io.csrUnitIntpipe.mem_addrM(i))
    }
    when(Reg(next=io.csrUnitIntpipe.retireM(i))) {
      wb_pcM_reg(i)  := wb_pcME_reg(i)
      wb_addr_reg(i) := wb_addrE_reg(i)
    }
  }

  // Count the cycle ID.
  val cycleId_reg     = WideCounter(64).value

  for (i <- 0 until NUM_CTXT) {
    val mpsX     = i / (NUM_CTXT / NUM_MPS)

    // evaluate performance counters
    if(ENA_PERF == 1) {
      csr(i).counters foreach { c => c.inc := RegNext(perfEventSets.evaluate(c.eventSel)) }
    }

    // Connect the hart ID.
    csr(i).hartid      := io.hartid + UInt(i*NUM_PUS)

    // Check the selection of the CSR.
    csr(i).retire      := Mux(io.csrUnitIntpipe.ctxtIdRetire === UInt(i), io.csrUnitIntpipe.retire, Bool(false)) || Mux(io.csrUnitIntpipe.ctxtIdRetireF === UInt(i), io.csrUnitIntpipe.retireF, Bool(false)) || Mux(io.csrUnitIntpipe.ctxtIdRetireM(mpsX) === UInt(i), io.csrUnitIntpipe.retireM(mpsX), Bool(false))

    // Connect the signals between the dataUnit and CSR controller.
    csr(i).flagsValid       := Reg(next=Reg(next=(io.dataUnitToCsr(mpsX).ctxtIdEarly === UInt(i)) && io.dataUnitToCsr(mpsX).isRealEarly, init=Bool(false)), init=Bool(false))
    csr(i).tlbMiss          := io.dataUnitToCsr(mpsX).tlbMiss
    csr(i).xcptMaSt         := io.dataUnitToCsr(mpsX).xcptMaSt
    csr(i).xcptMaLd         := io.dataUnitToCsr(mpsX).xcptMaLd
    csr(i).xcptPfSt         := io.dataUnitToCsr(mpsX).xcptPfSt
    csr(i).xcptPfLd         := io.dataUnitToCsr(mpsX).xcptPfLd
    csr(i).xcptAeSt         := io.dataUnitToCsr(mpsX).xcptAeSt
    csr(i).xcptAeLd         := io.dataUnitToCsr(mpsX).xcptAeLd
    csr(i).breakpoint       := io.dataUnitToCsr(mpsX).breakpoint
    csr(i).debug_breakpoint := io.dataUnitToCsr(mpsX).debug_breakpoint
    csr(i).tlbSafe          := io.dataUnitToCsr(mpsX).tlbSafe

    // Connect CsrUnit and PipeUnit.
    io.csrUnitIntpipe.interrupt(i)       := csr(i).interrupt
    io.csrUnitIntpipe.interrupt_cause(i) := csr(i).interrupt_cause

    // Enable the selection of one CSR.
    csr(i).mem_csr_ena                   := Reg(next=(ex2_ctxtId === UInt(i)), init=Bool(false))

    io.csrStatus(i)                      := csr(i).status
    io.csrUnitIntpipe.exx_muteInstr(i)   := csr(i).exx_muteInstr

    csr(i).mem_exception   := (mem_ctxtId === UInt(i)) && io.csrUnitIntpipe.mem_exception
    csr(i).cause           := io.csrUnitIntpipe.cause

    // Read/write from/to the CSR.
    csr(i).rw.ex2_addr        := io.csrUnitIntpipe.ex2_addr
    csr(i).rw.wdata           := io.csrUnitIntpipe.wdata
    csr(i).rw.ex2_cmd         := Mux(ex2_ctxtId === UInt(i), io.csrUnitIntpipe.ex2_cmd, CSR.N)
    csr(i).pc                 := io.csrUnitIntpipe.pc
    csr(i).pcD1               := wb_pcM_reg(mpsX)
    csr(i).wb_addr            := wb_addr_reg(mpsX)
    csr(i).mem_interruptAck   := Mux(mem_ctxtId === UInt(i), io.csrUnitIntpipe.mem_interruptAck, Bool(false))

    // Connect bus to CTXT.
    io.csrUnitCtxtUnit(i)     := csr(i).csrUnitCtxtUnit

    // Connect PTW.
    io.ptw(i).ptbr               := csr(i).ptbr
    io.ptw(i).status             := csr(i).status
    io.ptw(i).pmp                := csr(i).pmp

    // Connect CSR to the FPU signals
    csr(i).fcsr_flags.bits       := Mux((io.csrUnitIntpipe.fcsr_ctxtId0 === UInt(i)) && io.csrUnitIntpipe.fcsr_flags0.valid, io.csrUnitIntpipe.fcsr_flags0.bits, UInt(0)) |
                                    Mux((io.csrUnitIntpipe.fcsr_ctxtId1 === UInt(i)) && io.csrUnitIntpipe.fcsr_flags1.valid, io.csrUnitIntpipe.fcsr_flags1.bits, UInt(0)) |
                                    Mux((io.csrUnitIntpipe.fcsr_ctxtId2 === UInt(i)) && io.csrUnitIntpipe.fcsr_flags2.valid, io.csrUnitIntpipe.fcsr_flags2.bits, UInt(0)) |
                                    Mux((io.csrUnitIntpipe.fcsr_ctxtId3 === UInt(i)) && io.csrUnitIntpipe.fcsr_flags3.valid, io.csrUnitIntpipe.fcsr_flags3.bits, UInt(0))

    csr(i).fcsr_flags.valid      := Mux((io.csrUnitIntpipe.fcsr_ctxtId0 === UInt(i)), io.csrUnitIntpipe.fcsr_flags0.valid, Bool(false)) ||
                                    Mux((io.csrUnitIntpipe.fcsr_ctxtId1 === UInt(i)), io.csrUnitIntpipe.fcsr_flags1.valid, Bool(false)) ||
                                    Mux((io.csrUnitIntpipe.fcsr_ctxtId2 === UInt(i)), io.csrUnitIntpipe.fcsr_flags2.valid, Bool(false)) ||
                                    Mux((io.csrUnitIntpipe.fcsr_ctxtId3 === UInt(i)), io.csrUnitIntpipe.fcsr_flags3.valid, Bool(false))

    // Connect CSR decode bus.
    csr(i).decode(0).idc_addr    := io.csrUnitIntpipe.idc_addr
    csr(i).idc_sel               := (UInt(i) === io.csrUnitIntpipe.idc_ctxtId) && io.csrUnitIntpipe.idc_validCSR

    // Connect more CSR interface.
    csr(i).sys_dump              := io.sys_dump
    csr(i).interrupts            := io.interrupts(i)
    csr(i).cycleId               := cycleId_reg

    // TODO: Do we need to connect the rocc_interrupt to all CSR??
    csr(i).rocc_interrupt        := io.csrUnitIntpipe.rocc_interrupt

    // Connect the CSR ungated clock.
    csr(i).ungated_clock         := clock
  }

  val idc_sel                       = MuxTree(io.csrUnitIntpipe.idc_ctxtId, 0 to NUM_CTXT - 1 map {x => Cat(csr(x).singleStep, csr(x).decode(0).write_flush, csr(x).decode(0).fp_illegal, csr(x).decode(0).rocc_illegal, csr(x).decode(0).read_illegal, csr(x).decode(0).write_illegal, csr(x).decode(0).system_illegal)})
  io.csrUnitIntpipe.singleStep     := idc_sel(6)
  io.csrUnitIntpipe.write_flush    := idc_sel(5)
  io.csrUnitIntpipe.fp_illegal     := idc_sel(4)
  io.csrUnitIntpipe.rocc_illegal   := idc_sel(3)
  io.csrUnitIntpipe.read_illegal   := idc_sel(2)
  io.csrUnitIntpipe.write_illegal  := idc_sel(1)
  io.csrUnitIntpipe.system_illegal := idc_sel(0)

  io.csrUnitIntpipe.ex1_fcsr_rm    := MuxTree(io.csrUnitIntpipe.ex1_ctxtId, csr.map(_.fcsr_rm))
  io.csrUnitIntpipe.ex1_fcsr_rmF   := MuxTree(io.csrUnitIntpipe.ex1_ctxtIdF, csr.map(_.fcsr_rm))

  io.csr_data                      := MuxTree(ex2_ctxtId, csr.map(_.rw.rdata))

  io.csrUnitIntpipe.csr_flowAlter  := OrTree(csr.map(_.csr_flowAlter))

  val bpu = 0 to NUM_CTXT - 1 map {x => Module(new BreakpointUnit(nBreakpoints)).io}
  for(row <- 0 until NUM_CTXT) {
    // TODO: Connect each ROW directly to the BPU to provide the PC.
    bpu(row).status   := csr(row).status
    bpu(row).bp       := csr(row).bp
    bpu(row).pc       := io.csrUnitIntpipe.idc_pc
    bpu(row).pc_valid := (io.csrUnitIntpipe.idc_ctxtId === UInt(row)) && io.csrUnitIntpipe.idc_validI
    bpu(row).ea       := io.csrUnitIntpipe.bpu_wdata
    bpu(row).ea_valid := (io.csrUnitIntpipe.bpu_ctxtId === UInt(row)) && io.csrUnitIntpipe.bpu_valid
  }
  io.csrUnitIntpipe.bpu_debug_if := OrTree((0 until NUM_CTXT).map((w: Int) => bpu(w).debug_if))
  io.csrUnitIntpipe.bpu_xcpt_if  := OrTree((0 until NUM_CTXT).map((w: Int) => bpu(w).xcpt_if))

  for(mps <- 0 until NUM_MPS) {
    io.csrToDataUnit(mps).bpu_xcpt_ld   := OrTree((mps*(NUM_CTXT/NUM_MPS) until (mps+1)*(NUM_CTXT/NUM_MPS)).map((w: Int) => bpu(w).xcpt_ld))
    io.csrToDataUnit(mps).bpu_xcpt_st   := OrTree((mps*(NUM_CTXT/NUM_MPS) until (mps+1)*(NUM_CTXT/NUM_MPS)).map((w: Int) => bpu(w).xcpt_st))
    io.csrToDataUnit(mps).bpu_debug_ld  := OrTree((mps*(NUM_CTXT/NUM_MPS) until (mps+1)*(NUM_CTXT/NUM_MPS)).map((w: Int) => bpu(w).debug_ld))
    io.csrToDataUnit(mps).bpu_debug_st  := OrTree((mps*(NUM_CTXT/NUM_MPS) until (mps+1)*(NUM_CTXT/NUM_MPS)).map((w: Int) => bpu(w).debug_st))
  }
}

