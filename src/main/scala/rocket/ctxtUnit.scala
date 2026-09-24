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

// **************************************************************************
// Context Unit
//--------------------------------------------------------------------------
//
// Sean Halle
// with code borrowed from Armia Salib
// 2016 Oct 16
// See LICENSE.txt for license details.
//
// This module handles instruction fetch, instruction selection, and issue to the pipeline
//  It receives feedback from pipeline, which controls instr fetch and instr issue
//
//   - It calculates PC+4
//   - It controls the instrUnit bus.
//   - It fetches new instructions
//   - It selects instructions to issue and sends them to the integer pipeline.
//   - - It handles hazards among instructions from same context
//   - - It sends the context ID of the instruction and it's address, both together with instr
//   - It receives signals from pipe: isWaitEndExec, isLdSt, flowAltered
//   - It receives signals from data unit: LdStDone
//   - It receives signals from remote control unit: loadCtxt, saveCtxt
//
//   - If no instruction to issue then does not assert validInstr, causing bubble in the PipeUnit
//

package superThread

import Chisel._
import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.Util._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.CSRs._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.tilelink._



// Define the constants needed by CTXT.
object CtxtUnitConstants {
  // States of InstrReadyState
  val S_IR_READY_TO_ISSUE = UInt(0, 1)
  val S_IR_DEC_DONE       = UInt(1, 1)
}
import CtxtUnitConstants._


//===========================================================
//How the logic is organized:
// The context unit is a collection of independent circuits that all interact with a common
//  table of state, called the context table.
// One circuit scans the table, every cycle, and selects instructions to send to the PipeUnit.
// The rest of the circuits each handle a set of input wires.  When the wires have particular
//  values, then the circuit fires -- it may update a finite state machine, or trigger instr
//  fetch or similar activity
//
//A row of the context table contains:
// lifeState, readyState, readyBit, LdStState, IFState, Addr, instr
//
//IO consists of the common bus to the PipeUnit, a port to instruction memory (from existing sodor),
// plus remote control interface
//at reset, all context rows are set to no-op, except the first row, which is set to a fixed pattern, which
// indicates a valid instruction, which happens to be a jump to an immediate address where the bootstrap code
// resides in memory.  The bootstrap code then loads the OS or starts an application.

// Define the I/O ports.
class CtxtUnitIo()(implicit p: Parameters) extends IntenBundle
{
  // Reset vector.
  val ReadyNotReady               = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val ReadyNotReadyCauseDU        = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyLWfiStall      = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyLOpnotReady    = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyLOpnotReadyFPU = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyLflowaltered   = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyLrowSel        = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyNoLInstrFetch  = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyNoLnoPrevDep   = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyNoLnoHazard7   = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyNoLfence       = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyNoLtlbSafeR    = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyNoLinfSlow     = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))
  val IR_instrReadyNoLctlFlow     = Vec(NUM_PUS*NUM_CTXT, Bool(OUTPUT))

  val reset_vector = UInt(INPUT, ADDR_LEN)

  val ctxtUnitInstrUnit  = Vec(NUM_PUS, new CtxtUnitInstrUnitBundle())
  val ctxtUPipeU         = Vec(NUM_PUS, new CtxtUPipeUBundle()).asOutput
  val pipeUCtxtU         = Vec(NUM_PUS, new PipeUCtxtUBundle()).asInput
  val regfsetToCtxt      = Vec(NUM_PUS, new RegfsetToCtxtBundle()).flip()
  val dataUnitToCtxt     = Vec(NUM_PUS, new DataUnitToCtxtBundle()).asInput
  val fpuToCtxtUnit      = Vec(NUM_PUS, new FpuToCtxtUnitBundle()).asInput
  val ctxtToRfI          = Vec(NUM_PUS, new CtxtToRfIBundle())
  val ctxtToFpu          = Vec(NUM_PUS, new CtxtToFpuBundle())
  val csrUnitCtxtUnit    = Vec(NUM_PUS, Vec(NUM_CTXT, new CsrUnitCtxtUnitBundle())).asInput
  val sfenceFinish       = Vec(NUM_PUS, Bool(INPUT))
  val halfSel            = Vec(NUM_PUS, Bool(OUTPUT))
  val hazardStatus0      = Bits(OUTPUT, 16)
  val hazardStatus1      = Bits(OUTPUT, 16)
  val hazardStatus2      = Bits(OUTPUT, 16)
  val hazardStatus3      = Bits(OUTPUT, 16)
  val switches           = Bits(INPUT, 8)
  val instrFetchPerf     = Vec(NUM_CTXT*NUM_PUS, Bool(OUTPUT)).asOutput
}

//For those new to Chisel, like myself, some explanation of how the code works:
// Most of the code is actually in the constructors.  Chisel is about generating
// circuits, and that generation happens largely inside constructors.  So, defining
// a new class is mostly about writing the constructor for that class.  It will
// in turn call other constructors inside itself, and so on.  But Chisel doesn't
// indicate that you're defining a constructor, you just have to know that's
// what the syntax "class X() { code }" means -- "code" is constructor code
class CtxtUnit()(implicit p: Parameters) extends CoreModule()(p) with HasCoreParameters
{
  // Define I/O connections.
  val io   = new CtxtUnitIo()

  // Counter used to count ST and LD instructions.
  val disableDiv_reg          = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val disableMulL_reg         = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val disableMulH_reg         = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val disableFpuInstr_reg     = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val ldStSel_reg             = Reg(init=Bool(false))
  val sfenceInProg_reg        = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val halfSel_reg             = Wire(Vec(NUM_PUS, Bool()))
  val switches_reg            = Reg(next=io.switches, init=UInt(0, 8))
  val forwardFlowAlterD1_reg  = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val toBeSpeculatedD2_reg    = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val toBeSpeculatedD3_reg    = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})

  // Select the row that will issue the next instruction.
  //TODO: pipeline the instruction select and send logic -- 2 cycles or 3 cycles to select an instr and
  //       present it to the pipeline (latched into pipe at end of cycle)
  val pipeAccSel  = 0 to NUM_PUS - 1 map { x => Module(new SelPrio(NUM_CTXT, INSTR_LEN)).io }
  val pipeAccSelF = 0 to NUM_PUS - 1 map { x => Module(new SelPrio(NUM_CTXT, INSTR_LEN)).io }
  val pipeAccSelM = 0 to NUM_PUS*NUM_MPS - 1 map { x => Module(new SelPrio(NUM_CTXT/NUM_MPS, INSTR_LEN)).io }

  // Alternate between the two halves of the ICaches.
  halfSel_reg(0) := Reg(init=Bool(false), next=(!halfSel_reg(0)))
  if(NUM_PUS == 2) {
    halfSel_reg(1) := Reg(init=Bool(true), next=(!halfSel_reg(1)))
  }
  io.halfSel := halfSel_reg

  //**********************************
  // Instantiate the table rows.
  //**********************************
  //create a table -- each row is a collection of registers
  val ctxtRows = 0 to NUM_T_CTXT - 1 map { x => Module(new CtxtRow()).io }

  // Connect bus from CSR.
  for(ips <- 0 until NUM_PUS) {
    for(row <- 0 until NUM_CTXT) {
      ctxtRows(row * NUM_PUS + ips).csrUnitCtxtUnit <> io.csrUnitCtxtUnit(ips)(row)
      ctxtRows(row * NUM_PUS + ips).reset_vector    := io.reset_vector
    }
  }

  //**********************************
  // Control InstrUnit access.
  //**********************************
  // Selector to grant one row instrUnit access.
  val instrUnitAccSel = 0 to NUM_PUS - 1 map { x => Module(new SelPrio(NUM_CTXT, ADDR_LEN-log2Up(INSTR_LEN/8) + 4)).io }

  // Flush all the ICache if any ROW produces a flush.
  val flush_icache  = Wire(init=Bool(false))
  flush_icache     := Bool(false)
  for(row <- 0 until NUM_T_CTXT) {
    io.instrFetchPerf(row)  := ctxtRows(row).instrFetchPerf
    
    io.ReadyNotReady(row)               := ctxtRows(row).ReadyNotReady              
    io.ReadyNotReadyCauseDU(row)        := ctxtRows(row).ReadyNotReadyCauseDU       
    io.IR_instrReadyLWfiStall(row)      := ctxtRows(row).IR_instrReadyLWfiStall     
    io.IR_instrReadyLOpnotReady(row)    := ctxtRows(row).IR_instrReadyLOpnotReady   
    io.IR_instrReadyLOpnotReadyFPU(row) := ctxtRows(row).IR_instrReadyLOpnotReadyFPU
    io.IR_instrReadyLflowaltered(row)   := ctxtRows(row).IR_instrReadyLflowaltered  
    io.IR_instrReadyLrowSel(row)        := ctxtRows(row).IR_instrReadyLrowSel       
    io.IR_instrReadyNoLInstrFetch(row)  := ctxtRows(row).IR_instrReadyNoLInstrFetch 
    io.IR_instrReadyNoLnoPrevDep(row)   := ctxtRows(row).IR_instrReadyNoLnoPrevDep  
    io.IR_instrReadyNoLnoHazard7(row)   := ctxtRows(row).IR_instrReadyNoLnoHazard7  
    io.IR_instrReadyNoLfence(row)       := ctxtRows(row).IR_instrReadyNoLfence      
    io.IR_instrReadyNoLtlbSafeR(row)    := ctxtRows(row).IR_instrReadyNoLtlbSafeR   
    io.IR_instrReadyNoLinfSlow(row)     := ctxtRows(row).IR_instrReadyNoLinfSlow    
    io.IR_instrReadyNoLctlFlow(row)     := ctxtRows(row).IR_instrReadyNoLctlFlow  

    when(ctxtRows(row).flush_icache) {
      for(IC_ID <- 0 until NUM_T_CTXT) {
        flush_icache    := Bool(true)
      }
    }
  }

  // Track the selected way ID.
  val validWaySel = Vec.fill(NUM_PUS) {Wire(Bool())}
  val wayIdSel    = Vec.fill(NUM_PUS) {Wire(UInt(width=log2Up(nWaysIC)))}
  for(IC_ID <- 0 until NUM_PUS) {
    validWaySel(IC_ID)  := io.ctxtUnitInstrUnit(IC_ID).reqValid
    wayIdSel(IC_ID)     := io.ctxtUnitInstrUnit(IC_ID).reqAddr(CACHE_LINE_LOG2+log2Up(nWaysIC)-1, CACHE_LINE_LOG2)
  }

  var forwardFlowAlter   = Wire(Vec(NUM_PUS, Bool()))
  for(ips <- 0 until NUM_PUS) {
    if(ENA_IC_FORWARD) {
      if(NUM_PUS == 1) {
        forwardFlowAlter(ips) := io.pipeUCtxtU(ips).flowAltered && !toBeSpeculatedD3_reg(ips)
      }
      else {
        forwardFlowAlter(ips) := io.pipeUCtxtU(ips).flowAltered && (io.pipeUCtxtU(ips).alteredPc(IC_HLF_BIT) === halfSel_reg(ips)) && !toBeSpeculatedD3_reg(ips)
      }
      forwardFlowAlterD1_reg(ips)    := forwardFlowAlter(ips) && io.ctxtUnitInstrUnit(ips).reqReady
      instrUnitAccSel(ips).holdOn := !io.ctxtUnitInstrUnit(ips).reqReady || forwardFlowAlter(ips)
    }
    else {
      forwardFlowAlter(ips)       := Bool(false)
      forwardFlowAlterD1_reg(ips) := Bool(false)
      instrUnitAccSel(ips).holdOn := !io.ctxtUnitInstrUnit(ips).reqReady
    }

    when(io.sfenceFinish(ips)) {
      sfenceInProg_reg(ips) := Bool(false)
    }

    for(row <- 0 until NUM_CTXT) {
      if(NUM_PUS == 1) {
        instrUnitAccSel(ips).isReady(row) := ctxtRows(row * NUM_PUS + ips).instrMemReq && (!ctxtRows(row * NUM_PUS + ips).sfenceReq_valid || !sfenceInProg_reg(ips))
      }
      else {
        instrUnitAccSel(ips).isReady(row) := ctxtRows(row * NUM_PUS + ips).instrMemReq && (!ctxtRows(row * NUM_PUS + ips).sfenceReq_valid || !sfenceInProg_reg(ips)) && (ctxtRows(row * NUM_PUS + ips).pcMemReq(IC_HLF_BIT) === halfSel_reg(ips))
      }
      instrUnitAccSel(ips).tagIn(row)   := Cat(ctxtRows(row * NUM_PUS + ips).pcMemReq(ADDR_LEN-1, log2Up(INSTR_LEN / 8)), ctxtRows(row * NUM_PUS + ips).instrMemKeep, ctxtRows(row * NUM_PUS + ips).sfenceReq_rs2, ctxtRows(row * NUM_PUS + ips).sfenceReq_rs1, ctxtRows(row * NUM_PUS + ips).sfenceReq_valid)

      // Inform about the selection.
      ctxtRows(row * NUM_PUS + ips).instrMemStFetch := instrUnitAccSel(ips).rowSelP(row)

      if(ENA_IC_FORWARD) {
        ctxtRows(row * NUM_PUS + ips).instrMemFwFetch := forwardFlowAlterD1_reg(ips)
      }
      else {
        ctxtRows(row * NUM_PUS + ips).instrMemFwFetch := Bool(false)
      }
    }

    // Connect the instruction memory signals.
    val reqAddrMux    = Wire(UInt())
    val instrMemKeep  = instrUnitAccSel(ips).tagOut(3)
    if(ENA_IC_FORWARD) {
      val ctxtIdD1_reg = RegEnable(next=io.ctxtUPipeU(ips).ctxtId, enable=io.ctxtUPipeU(ips).idc_validI, init=UInt(0, CTXT_ID_LEN))
      val ctxtIdD2_reg = Reg(next=ctxtIdD1_reg, init=UInt(0, CTXT_ID_LEN))

      reqAddrMux                                := Cat(Mux(forwardFlowAlter(ips), io.pipeUCtxtU(ips).alteredPc(ADDR_LEN-1, log2Up(INSTR_LEN / 8)), instrUnitAccSel(ips).tagOut(ADDR_LEN + 3 - log2Up(INSTR_LEN / 8), 4)), UInt(0, log2Up(INSTR_LEN / 8)))
      io.ctxtUnitInstrUnit(ips).reqValid        := instrMemKeep && (instrUnitAccSel(ips).validSelP || forwardFlowAlter(ips))
      io.ctxtUnitInstrUnit(ips).reqCtxtId       := Mux(forwardFlowAlter(ips), ctxtIdD2_reg, instrUnitAccSel(ips).selId)
      io.ctxtUnitInstrUnit(ips).reqAddr         := Cat(reqAddrMux(ADDR_LEN-1, CACHE_LINE_LOG2), UInt(0, CACHE_LINE_LOG2))
      io.ctxtUnitInstrUnit(ips).sfenceReq_valid := instrUnitAccSel(ips).tagOut(0) && !forwardFlowAlter(ips)
    }
    else {
      reqAddrMux                                := Cat(instrUnitAccSel(ips).tagOut(ADDR_LEN + 3 - log2Up(INSTR_LEN / 8), 4), UInt(0, log2Up(INSTR_LEN / 8)))
      io.ctxtUnitInstrUnit(ips).reqValid        := instrMemKeep && instrUnitAccSel(ips).validSelP
      io.ctxtUnitInstrUnit(ips).reqCtxtId       := instrUnitAccSel(ips).selId
      io.ctxtUnitInstrUnit(ips).reqAddr         := Cat(reqAddrMux(ADDR_LEN-1, CACHE_LINE_LOG2), UInt(0, CACHE_LINE_LOG2))
      io.ctxtUnitInstrUnit(ips).sfenceReq_valid := instrUnitAccSel(ips).tagOut(0)
    }
    io.ctxtUnitInstrUnit(ips).sfenceReq_rs1   := instrUnitAccSel(ips).tagOut(1)
    io.ctxtUnitInstrUnit(ips).sfenceReq_rs2   := instrUnitAccSel(ips).tagOut(2)
    io.ctxtUnitInstrUnit(ips).flush_icache    := flush_icache

    // Prevent any new sfence until we finish the previous.
    when(instrUnitAccSel(ips).validSelP && io.ctxtUnitInstrUnit(ips).sfenceReq_valid) {
      sfenceInProg_reg(ips) := Bool(true)
    }

    val WORD_ADDR_W      = CACHE_LINE_LOG2 - log2Up(INSTR_LEN / 8)
    val wordAddrD1_reg   = RegEnable(next=reqAddrMux(CACHE_LINE_LOG2-1, log2Up(INSTR_LEN / 8)), enable=io.ctxtUnitInstrUnit(ips).reqValid, init=UInt(0, WORD_ADDR_W))
    val wordAddrD2_reg   = Reg(next=wordAddrD1_reg, init=UInt(0, WORD_ADDR_W))
    val wordAddrD3_reg   = Reg(next=wordAddrD2_reg, init=UInt(0, WORD_ADDR_W))
    val wordAddrD4_reg   = Reg(next=wordAddrD3_reg, init=UInt(0, WORD_ADDR_W))
    val wordAddrD5_reg   = Reg(init=UInt(0, 1<<WORD_ADDR_W))
    wordAddrD5_reg      := UIntToOH(wordAddrD4_reg)
    val instrMemRespWord = OrTree((0 until io.ctxtUnitInstrUnit(ips).instrMemResp.size).map(i => io.ctxtUnitInstrUnit(ips).instrMemResp(i) & Fill(io.ctxtUnitInstrUnit(ips).instrMemResp(i).getWidth, wordAddrD5_reg(i))))

    // Connect the instrUnit response to the selected row.
    for(row <- 0 until NUM_CTXT) {
      ctxtRows(row * NUM_PUS + ips).validWaySel              := validWaySel
      ctxtRows(row * NUM_PUS + ips).wayIdSel                 := wayIdSel

      ctxtRows(row * NUM_PUS + ips).instrMemValidEarly       := io.ctxtUnitInstrUnit(ips).respValidEarly
      ctxtRows(row * NUM_PUS + ips).instrMemResp             := io.ctxtUnitInstrUnit(ips).instrMemResp
      ctxtRows(row * NUM_PUS + ips).instrMemRespWord         := instrMemRespWord
      ctxtRows(row * NUM_PUS + ips).instrMemFailCauseEarly   := io.ctxtUnitInstrUnit(ips).failCauseEarly
      ctxtRows(row * NUM_PUS + ips).instrMemFailTlbMiss      := io.ctxtUnitInstrUnit(ips).failTlbMiss
      ctxtRows(row * NUM_PUS + ips).tlbReady                 := io.ctxtUnitInstrUnit(ips).itlbReady
      ctxtRows(row * NUM_PUS + ips).ae                       := io.ctxtUnitInstrUnit(ips).ae
      ctxtRows(row * NUM_PUS + ips).pf                       := io.ctxtUnitInstrUnit(ips).pf

      for(ipsX <- 0 until NUM_PUS) {
        ctxtRows(row * NUM_PUS + ips).icacheReady(ipsX)      := io.ctxtUnitInstrUnit(ipsX).icacheReady
      }
    }
  }

  //**********************************
  // holdOn generation.
  //**********************************
  val holdOn_reg     = Reg(init=Bool(false))
  val holdOnCtrl_reg = Reg(init=UInt(0, 3))
  val holdCntr_reg   = Reg(init=UInt(0, 3))

  // Add some pipeline.
  val holdOnCtrlD1_reg = Reg(init=UInt(0, 3))
  val holdOnCtrlD2_reg = Reg(init=UInt(0, 3))
  val holdOnCtrlD3_reg = Reg(init=UInt(0, 3))
  holdOnCtrlD1_reg := holdOnCtrl_reg
  holdOnCtrlD2_reg := holdOnCtrlD1_reg
  holdOnCtrlD3_reg := holdOnCtrlD2_reg

  // The decoding of holdOnCtrlD3_reg
  //   7 -- issue no instructions (wait until cools)
  //   6 -- one forced "dead" cycle out of every 4 cycles
  //   5 -- one forced "dead" cycle out of every 3 cycles
  //   4 -- four dead-cycles between issuing
  //   3 -- three dead cycles between issuing
  //   2 -- two dead cycles between issuing
  //   1 -- one out of 2
  //   0 -- issue every cycle
  when(holdOnCtrlD3_reg === UInt(0)) {
    holdOn_reg := Bool(false)
  }
  .elsewhen(holdOnCtrlD3_reg === UInt(7)) {
    holdOn_reg := Bool(true)
  }
  .otherwise {
    holdCntr_reg :=  holdCntr_reg + UInt(1)

    when(holdOnCtrlD3_reg < UInt(5)) {
      holdOn_reg := Bool(true)
      when(holdCntr_reg === holdOnCtrlD3_reg) {
        holdCntr_reg   := UInt(0)
        holdOn_reg     := Bool(false)
      }
    }
    .otherwise {
      holdOn_reg := Bool(false)
      when(((holdCntr_reg === UInt(2)) && (holdOnCtrlD3_reg === UInt(5))) || ((holdCntr_reg === UInt(3)) && (holdOnCtrlD3_reg === UInt(6)))) {
        holdCntr_reg   := UInt(0)
        holdOn_reg     := Bool(true)
      }
    }
  }

  //**********************************
  // Send instr to PipeUnit
  //**********************************
  val hazardSt0        = Vec.fill(NUM_T_CTXT) {Wire(Bool())}
  val hazardSt1        = Vec.fill(NUM_T_CTXT) {Wire(Bool())}
  val hazardSt2        = Vec.fill(NUM_T_CTXT) {Wire(Bool())}
  val hazardSt3        = Vec.fill(NUM_T_CTXT) {Wire(Bool())}
  for(ips <- 0 until NUM_PUS) {
    if(true) { // Enable/disable holdOn control.
      pipeAccSel(ips).holdOn  := holdOn_reg
      pipeAccSelF(ips).holdOn := holdOn_reg
      pipeAccSelM(ips).holdOn := holdOn_reg
    }
    else {
      pipeAccSel(ips).holdOn  := Bool(false)
      pipeAccSelF(ips).holdOn := Bool(false)
      pipeAccSelM(ips).holdOn := Bool(false)
    }
    for(row <- 0 until NUM_CTXT) {
      pipeAccSel(ips).isReady(row)  := ctxtRows(row * NUM_PUS + ips).instrReady
      pipeAccSel(ips).tagIn(row)    := ctxtRows(row * NUM_PUS + ips).instr
      pipeAccSelF(ips).isReady(row) := ctxtRows(row * NUM_PUS + ips).instrReadyF
      pipeAccSelF(ips).tagIn(row)   := ctxtRows(row * NUM_PUS + ips).instr
      pipeAccSelM(ips*NUM_MPS+row/(NUM_CTXT/NUM_MPS)).isReady(row%(NUM_CTXT/NUM_MPS)) := ctxtRows(row * NUM_PUS + ips).instrReadyM
      pipeAccSelM(ips*NUM_MPS+row/(NUM_CTXT/NUM_MPS)).tagIn(row%(NUM_CTXT/NUM_MPS))   := ctxtRows(row * NUM_PUS + ips).instr
      hazardSt0(row * NUM_PUS + ips) := ctxtRows(row * NUM_PUS + ips).rowStatus(0)
      hazardSt1(row * NUM_PUS + ips) := ctxtRows(row * NUM_PUS + ips).rowStatus(1)
      hazardSt2(row * NUM_PUS + ips) := ctxtRows(row * NUM_PUS + ips).rowStatus(2)
      hazardSt3(row * NUM_PUS + ips) := ctxtRows(row * NUM_PUS + ips).rowStatus(3) || io.csrUnitCtxtUnit(ips)(row).wfi_stall
    }

    // Issue an instruction to the PipeUnit from the selected row using pipeAccSel(ips).rowSelP(row) mask.
    val addrVec          = Vec.fill(NUM_CTXT) {Wire(UInt(width = ADDR_LEN))}
    val isNegOp2EarlyVec = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val isDivEarlyVec    = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val isMulLEarlyVec   = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val isMulHEarlyVec   = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val isFpuEarlyVecF   = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val isRvcEarlyVec    = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val isAeVec          = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val isPfVec          = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val isAeSVec         = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val isPfSVec         = Vec.fill(NUM_CTXT) {Wire(Bool())}
    val toBeSpeculatedD1Vec = Vec.fill(NUM_CTXT) {Wire(Bool())}
    for(row <- 0 until NUM_CTXT) {
      addrVec(row)                         := ctxtRows(row * NUM_PUS + ips).addr & Fill(ADDR_LEN, pipeAccSel(ips).rowSelP(row))
      isNegOp2EarlyVec(row)                := ctxtRows(row * NUM_PUS + ips).isNegOp2Early & pipeAccSel(ips).rowSelP(row)
      isDivEarlyVec(row)                   := ctxtRows(row * NUM_PUS + ips).isDivEarly & pipeAccSel(ips).rowSelP(row)
      isMulLEarlyVec(row)                  := ctxtRows(row * NUM_PUS + ips).isMulLEarly & pipeAccSel(ips).rowSelP(row)
      isMulHEarlyVec(row)                  := ctxtRows(row * NUM_PUS + ips).isMulHEarly & pipeAccSel(ips).rowSelP(row)
      isRvcEarlyVec(row)                   := ctxtRows(row * NUM_PUS + ips).isRvcEarly & pipeAccSel(ips).rowSelP(row)
      isAeVec(row)                         := ctxtRows(row * NUM_PUS + ips).aeOut & pipeAccSel(ips).rowSelP(row)
      isPfVec(row)                         := ctxtRows(row * NUM_PUS + ips).pfOut & pipeAccSel(ips).rowSelP(row)
      isAeSVec(row)                        := ctxtRows(row * NUM_PUS + ips).aeSOut & pipeAccSel(ips).rowSelP(row)
      isPfSVec(row)                        := ctxtRows(row * NUM_PUS + ips).pfSOut & pipeAccSel(ips).rowSelP(row)
      isFpuEarlyVecF(row)                  := ctxtRows(row * NUM_PUS + ips).isChkFpuRdyEarly & pipeAccSelF(ips).rowSelP(row)
      ctxtRows(row * NUM_PUS + ips).rowSel := pipeAccSel(ips).rowSelP(row) || pipeAccSelF(ips).rowSelP(row) || pipeAccSelM(ips*NUM_MPS+row/(NUM_CTXT/NUM_MPS)).rowSelP(row%(NUM_CTXT/NUM_MPS))

      // valid one cycle after rowSel.
      toBeSpeculatedD1Vec(row)             := ctxtRows(row * NUM_PUS + ips).toBeSpeculatedD1 & Reg(next=pipeAccSel(ips).rowSelP(row), init=Bool(false))

      // Connect the control of the RF array for each ROW.
      io.ctxtToRfI(ips).rowToRfI(row)      := ctxtRows(row * NUM_PUS + ips).rowToRfI
      io.ctxtToFpu(ips).rowToRfF(row)      := ctxtRows(row * NUM_PUS + ips).rowToRfF
    }

    val ctxtUPipeU_reg = Reg(new CtxtUPipeUBundle(), init=new CtxtUPipeUBundle().fromBits(0))

    val isActive = pipeAccSel(ips).validSelP
    ctxtUPipeU_reg.idc_validI := isActive
    when(isActive) {
      ctxtUPipeU_reg.inst       := pipeAccSel(ips).tagOut
      ctxtUPipeU_reg.pc         := OrTree(addrVec)
      ctxtUPipeU_reg.ctxtId     := pipeAccSel(ips).selId
      ctxtUPipeU_reg.isRvc      := OrTree(isRvcEarlyVec)
      ctxtUPipeU_reg.pf         := OrTree(isPfVec)
      ctxtUPipeU_reg.ae         := OrTree(isAeVec)
      ctxtUPipeU_reg.pfS        := OrTree(isPfSVec)
      ctxtUPipeU_reg.aeS        := OrTree(isAeSVec)
    }

    // It has two successive registers.
    val isActiveD1_reg = Reg(next=isActive, init=Bool(false))
    when(isActiveD1_reg) {
      toBeSpeculatedD2_reg(ips) := OrTree(toBeSpeculatedD1Vec)
    }
    toBeSpeculatedD3_reg(ips) := toBeSpeculatedD2_reg(ips)

    val isActiveF = pipeAccSelF(ips).validSelP
    ctxtUPipeU_reg.idc_validF := isActiveF
    when(isActiveF) {
      ctxtUPipeU_reg.instF      := pipeAccSelF(ips).tagOut
      ctxtUPipeU_reg.ctxtIdF    := pipeAccSelF(ips).selId
    }

    for(mps <- 0 until NUM_MPS) {
      val isActiveM = pipeAccSelM(ips*NUM_MPS+mps).validSelP
      ctxtUPipeU_reg.idc_validM(mps) := isActiveM
      when(isActiveM) {
        ctxtUPipeU_reg.instM(mps)     := pipeAccSelM(ips*NUM_MPS+mps).tagOut
        if(NUM_CTXT / NUM_MPS > 1) {
          ctxtUPipeU_reg.ctxtIdM(mps)   := Cat(UInt(mps), pipeAccSelM(ips*NUM_MPS+mps).selId(log2Up(NUM_CTXT / NUM_MPS)-1, 0))
        }
        else {
          ctxtUPipeU_reg.ctxtIdM(mps)   := UInt(mps)
        }

        val addrVecM = Vec.fill(NUM_CTXT/NUM_MPS) {Wire(UInt(width = ADDR_LEN))}
        for(row <- 0 until NUM_CTXT/NUM_MPS) {
          addrVecM(row) := ctxtRows((mps*NUM_CTXT/NUM_MPS+row) * NUM_PUS + ips).addr & Fill(ADDR_LEN, pipeAccSelM(ips*NUM_MPS+mps).rowSelP(row))
        }
        ctxtUPipeU_reg.pcM(mps)       := OrTree(addrVecM)
      }
    }
    io.ctxtUPipeU(ips) := ctxtUPipeU_reg

    // Connect the addresses of the registers needed to be read.
    io.ctxtToRfI(ips).ctxtId(0) := pipeAccSel(ips).selId
    io.ctxtToRfI(ips).rdEna(0)  := pipeAccSel(ips).validSelP
    io.ctxtToRfI(ips).addr1(0)  := pipeAccSel(ips).tagOut(19,15)
    io.ctxtToRfI(ips).addr2(0)  := pipeAccSel(ips).tagOut(24,20)

    for(mps <- 0 until NUM_MPS) {
      if(NUM_CTXT / NUM_MPS > 1) {
        io.ctxtToRfI(ips).ctxtId(1+mps) := Cat(UInt(mps), pipeAccSelM(ips*NUM_MPS+mps).selId(log2Up(NUM_CTXT / NUM_MPS)-1, 0))
      }
      else {
        io.ctxtToRfI(ips).ctxtId(1+mps) := UInt(mps)
      }
      io.ctxtToRfI(ips).rdEna(1+mps)  := pipeAccSelM(ips*NUM_MPS+mps).validSelP
      io.ctxtToRfI(ips).addr1(1+mps)  := pipeAccSelM(ips*NUM_MPS+mps).tagOut(19,15)
      io.ctxtToRfI(ips).addr2(1+mps)  := pipeAccSelM(ips*NUM_MPS+mps).tagOut(24,20)
    }

    //**********************************
    // Keep track the DIV/FPU instructions.
    //**********************************
    // TODO: ROCC instructions are needed to be tracked.
    val isDivEarly_reg  = Reg(next=OrTree(isDivEarlyVec), init=Bool(false))
    val divCntr_reg     = Reg(init=UInt(0, log2Up(NUM_DIV+1)))
    when(isDivEarly_reg && !io.pipeUCtxtU(ips).divSafe) {
      divCntr_reg := divCntr_reg + UInt(1)
      when(divCntr_reg === UInt(NUM_DIV - 1)) {
        disableDiv_reg(ips) := Bool(true)
      }
      assert(divCntr_reg < NUM_DIV, "CTXT: disableDiv_reg has a problem 1.")
    }
    .elsewhen(!isDivEarly_reg && io.pipeUCtxtU(ips).divSafe) {
      divCntr_reg := divCntr_reg - UInt(1)
      disableDiv_reg(ips) := Bool(false)
      assert(divCntr_reg > UInt(0), "CTXT: disableDiv_reg has a problem 2.")
    }

    val isMulLEarly_reg  = Reg(next=OrTree(isMulLEarlyVec), init=Bool(false))
    val mulLCntr_reg     = Reg(init=UInt(0, log2Up(NUM_MUL_L+1)))
    when(isMulLEarly_reg && !io.pipeUCtxtU(ips).mulLSafe) {
      mulLCntr_reg := mulLCntr_reg + UInt(1)
      when(mulLCntr_reg === UInt(NUM_MUL_L - 1)) {
        disableMulL_reg(ips) := Bool(true)
      }
      assert(mulLCntr_reg < NUM_MUL_L, "CTXT: disableMulL_reg has a problem 1.")
    }
    .elsewhen(!isMulLEarly_reg && io.pipeUCtxtU(ips).mulLSafe) {
      mulLCntr_reg := mulLCntr_reg - UInt(1)
      disableMulL_reg(ips) := Bool(false)
      assert(mulLCntr_reg > UInt(0), "CTXT: disableMulL_reg has a problem 2.")
    }

    val isMulHEarly_reg  = Reg(next=OrTree(isMulHEarlyVec), init=Bool(false))
    val mulHCntr_reg     = Reg(init=UInt(0, log2Up(NUM_MUL_H+1)))
    when(isMulHEarly_reg && !io.pipeUCtxtU(ips).mulHSafe) {
      mulHCntr_reg := mulHCntr_reg + UInt(1)
      when(mulHCntr_reg === UInt(NUM_MUL_H - 1)) {
        disableMulH_reg(ips) := Bool(true)
      }
      assert(mulHCntr_reg < NUM_MUL_H, "CTXT: disableMulH_reg has a problem 1.")
    }
    .elsewhen(!isMulHEarly_reg && io.pipeUCtxtU(ips).mulHSafe) {
      mulHCntr_reg := mulHCntr_reg - UInt(1)
      disableMulH_reg(ips) := Bool(false)
      assert(mulHCntr_reg > UInt(0), "CTXT: disableMulH_reg has a problem 2.")
    }

    val isFpuEarly_reg = Reg(init=Bool(false))
    if(usingFPU) {
      isFpuEarly_reg := OrTree(isFpuEarlyVecF)
      when(isFpuEarly_reg && !io.fpuToCtxtUnit(ips).fpuSafe1) {
        disableFpuInstr_reg(ips) := Bool(true)
        assert(!disableFpuInstr_reg(ips), "CTXT: disableFpuInstr_reg is true.")
      }
      .elsewhen(!isFpuEarly_reg && io.fpuToCtxtUnit(ips).fpuSafe1) {
        disableFpuInstr_reg(ips) := Bool(false)
        assert(disableFpuInstr_reg(ips), "CTXT: disableFpuInstr_reg is false.")
      }
    }

    //**********************************
    // Connect the control flags to each row.
    //**********************************
    for(row <- 0 until NUM_CTXT) {
      ctxtRows(row * NUM_PUS + ips).disableDiv       := disableDiv_reg(ips) || isDivEarly_reg
      ctxtRows(row * NUM_PUS + ips).disableMulL      := disableMulL_reg(ips) || isMulLEarly_reg
      ctxtRows(row * NUM_PUS + ips).disableMulH      := disableMulH_reg(ips) || isMulHEarly_reg
      ctxtRows(row * NUM_PUS + ips).disableFpuInstr  := disableFpuInstr_reg(ips) || isFpuEarly_reg
    }
  }
  io.hazardStatus0 := hazardSt0.asUInt
  io.hazardStatus1 := hazardSt1.asUInt
  io.hazardStatus2 := hazardSt2.asUInt
  io.hazardStatus3 := hazardSt3.asUInt

  //**********************************
  // Connect flowAltered control signals from the PipeUnit to ROWs.
  //**********************************
  for(row <- 0 until NUM_CTXT) {
    val mpsX = row / (NUM_CTXT / NUM_MPS)
    for(ips <- 0 until NUM_PUS) {
      // Connect PipeUnit flags to the rows.
      ctxtRows(row * NUM_PUS + ips).flowAltered     := io.pipeUCtxtU(ips).flowAltered
      ctxtRows(row * NUM_PUS + ips).alteredPc       := io.pipeUCtxtU(ips).alteredPc
      ctxtRows(row * NUM_PUS + ips).endExecAckEarly := (io.pipeUCtxtU(ips).ctxtIdEndExecEarly === UInt(row)) && io.pipeUCtxtU(ips).endExecValidEarly

      ctxtRows(row * NUM_PUS + ips).flowAlteredM    := io.pipeUCtxtU(ips).flowAlteredM(mpsX)
      ctxtRows(row * NUM_PUS + ips).alteredPcM      := io.pipeUCtxtU(ips).alteredPcM(mpsX)
      ctxtRows(row * NUM_PUS + ips).sfence_rs1      := io.pipeUCtxtU(ips).sfence_rs1(mpsX)
      ctxtRows(row * NUM_PUS + ips).sfence_rs2      := io.pipeUCtxtU(ips).sfence_rs2(mpsX)

      ctxtRows(row * NUM_PUS + ips).sfenceFlush     := io.pipeUCtxtU(ips).sfenceFlush

      // Decoder flags
      ctxtRows(row * NUM_PUS + ips).isLdStM         := io.pipeUCtxtU(ips).isLdStM(mpsX)
      ctxtRows(row * NUM_PUS + ips).isStM           := io.pipeUCtxtU(ips).isStM(mpsX)
      ctxtRows(row * NUM_PUS + ips).isAmoLrScM      := io.pipeUCtxtU(ips).isAmoLrScM(mpsX)
      ctxtRows(row * NUM_PUS + ips).isFenciM        := io.pipeUCtxtU(ips).isFenciM(mpsX)
      ctxtRows(row * NUM_PUS + ips).isFenc          := io.pipeUCtxtU(ips).isFenc
      ctxtRows(row * NUM_PUS + ips).isFencM         := io.pipeUCtxtU(ips).isFencM(mpsX)
      ctxtRows(row * NUM_PUS + ips).isInfLoop       := io.pipeUCtxtU(ips).isInfLoop
      ctxtRows(row * NUM_PUS + ips).checkRegHazard  := io.pipeUCtxtU(ips).checkRegHazard
      ctxtRows(row * NUM_PUS + ips).checkFpuHazard  := io.pipeUCtxtU(ips).checkFpuHazard
      ctxtRows(row * NUM_PUS + ips).isWaitEndExec   := io.pipeUCtxtU(ips).isWaitEndExec
      ctxtRows(row * NUM_PUS + ips).isBackBranch    := io.pipeUCtxtU(ips).isBackBranch
      ctxtRows(row * NUM_PUS + ips).isCondBranch    := io.pipeUCtxtU(ips).isCondBranch
      ctxtRows(row * NUM_PUS + ips).checkRegHazardM := io.pipeUCtxtU(ips).checkRegHazardM(mpsX)
      ctxtRows(row * NUM_PUS + ips).checkFpuHazardM := io.pipeUCtxtU(ips).checkFpuHazardM(mpsX)
      ctxtRows(row * NUM_PUS + ips).isWaitEndExecM  := io.pipeUCtxtU(ips).isWaitEndExecM(mpsX)

      // Connect signals from Regfset to each row.
      ctxtRows(row * NUM_PUS + ips).regfsetSafe0    := (io.regfsetToCtxt(ips).ctxtId0 === UInt(row)) && io.regfsetToCtxt(ips).regSafe0
      ctxtRows(row * NUM_PUS + ips).regfsetIsReal0  := io.regfsetToCtxt(ips).isReal0
      ctxtRows(row * NUM_PUS + ips).regfsetRegId0   := io.regfsetToCtxt(ips).regId0

      ctxtRows(row * NUM_PUS + ips).regfsetSafe3    := (io.dataUnitToCtxt(ips).duCtxtEarlyNotifier3(mpsX).ctxtId === UInt(row)) && io.dataUnitToCtxt(ips).duCtxtEarlyNotifier3(mpsX).valid
      ctxtRows(row * NUM_PUS + ips).regfsetRegId3   := io.dataUnitToCtxt(ips).duCtxtEarlyNotifier3(mpsX).destReg
      ctxtRows(row * NUM_PUS + ips).regfsetisLd3    := io.dataUnitToCtxt(ips).duCtxtEarlyNotifier3(mpsX).isLd
      ctxtRows(row * NUM_PUS + ips).regfsetisSt3    := io.dataUnitToCtxt(ips).duCtxtEarlyNotifier3(mpsX).isSt
      ctxtRows(row * NUM_PUS + ips).regfsetIsFpu3   := io.dataUnitToCtxt(ips).duCtxtEarlyNotifier3(mpsX).isFpuAccess

      ctxtRows(row * NUM_PUS + ips).regfsetSafe2    := (io.dataUnitToCtxt(ips).duCtxtEarlyNotifier.ctxtId === UInt(row)) && io.dataUnitToCtxt(ips).duCtxtEarlyNotifier.valid
      ctxtRows(row * NUM_PUS + ips).regfsetRegId2   := io.dataUnitToCtxt(ips).duCtxtEarlyNotifier.destReg
      ctxtRows(row * NUM_PUS + ips).regfsetIsFpu2   := io.dataUnitToCtxt(ips).duCtxtEarlyNotifier.isFpuAccess

      ctxtRows(row * NUM_PUS + ips).duWriteEarly    := (io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.ctxtId === UInt(row)) && io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.valid
      ctxtRows(row * NUM_PUS + ips).duRegIdEarly    := io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.destReg
      ctxtRows(row * NUM_PUS + ips).duIsFpuEarly    := io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.isFpuAccess
      ctxtRows(row * NUM_PUS + ips).duHitEarlyX     := io.dataUnitToCtxt(ips).duHitEarlyX

      // Connect signals from dmem to each row.
      ctxtRows(row * NUM_PUS + ips).ldStSafeEarly   := io.dataUnitToCtxt(ips).ldStSafeEarly(row)
      ctxtRows(row * NUM_PUS + ips).stSafeLate      := (io.dataUnitToCtxt(ips).ctxtIdLate === UInt(row)) && io.dataUnitToCtxt(ips).stSafeLate
      ctxtRows(row * NUM_PUS + ips).stSafeLateUn    := (io.dataUnitToCtxt(ips).ctxtIdLateUn === UInt(row)) && io.dataUnitToCtxt(ips).stSafeLateUn

      // Connect signals from FPU to each row.
      ctxtRows(row * NUM_PUS + ips).fpuSafe0        := (io.fpuToCtxtUnit(ips).fpuCtxtId0 === UInt(row)) && io.fpuToCtxtUnit(ips).fpuSafe0
      ctxtRows(row * NUM_PUS + ips).fpuRegId0       := io.fpuToCtxtUnit(ips).fpuRegId0
      ctxtRows(row * NUM_PUS + ips).fpuIsReal0      := io.fpuToCtxtUnit(ips).fpuIsReal0
      ctxtRows(row * NUM_PUS + ips).fpuSafe1        := (io.fpuToCtxtUnit(ips).fpuCtxtId1 === UInt(row)) && io.fpuToCtxtUnit(ips).fpuSafe1
      ctxtRows(row * NUM_PUS + ips).fpuRegId1       := io.fpuToCtxtUnit(ips).fpuRegId1
      ctxtRows(row * NUM_PUS + ips).fpuIsReal1      := io.fpuToCtxtUnit(ips).fpuIsReal1
      ctxtRows(row * NUM_PUS + ips).fpuSafe2        := (io.fpuToCtxtUnit(ips).fpuCtxtId2 === UInt(row)) && io.fpuToCtxtUnit(ips).fpuSafe2
      ctxtRows(row * NUM_PUS + ips).fpuRegId2       := io.fpuToCtxtUnit(ips).fpuRegId2
      ctxtRows(row * NUM_PUS + ips).fpuIsReal2      := io.fpuToCtxtUnit(ips).fpuIsReal2
      ctxtRows(row * NUM_PUS + ips).fpuSafe3        := (io.fpuToCtxtUnit(ips).fpuCtxtId3 === UInt(row)) && io.fpuToCtxtUnit(ips).fpuSafe3
      ctxtRows(row * NUM_PUS + ips).fpuRegId3       := io.fpuToCtxtUnit(ips).fpuRegId3
      ctxtRows(row * NUM_PUS + ips).fpuIsReal3      := io.fpuToCtxtUnit(ips).fpuIsReal3

      // Enable/disable slow down the run for debug.
      ctxtRows(row * NUM_PUS + ips).slowDownDbg     := switches_reg(7)
    }
  }
}

// ***********************************
// Context Row implementation
// ***********************************
class CtxtRowIo()(implicit p: Parameters) extends CoreBundle()(p) {
  // Reset vector.
  val ReadyNotReady               = Bool(OUTPUT)
  val ReadyNotReadyCauseDU        = Bool(OUTPUT)
  val IR_instrReadyLWfiStall      = Bool(OUTPUT)
  val IR_instrReadyLOpnotReady    = Bool(OUTPUT)
  val IR_instrReadyLOpnotReadyFPU = Bool(OUTPUT)
  val IR_instrReadyLflowaltered   = Bool(OUTPUT)
  val IR_instrReadyLrowSel        = Bool(OUTPUT)
  val IR_instrReadyNoLInstrFetch  = Bool(OUTPUT)
  val IR_instrReadyNoLnoPrevDep   = Bool(OUTPUT)
  val IR_instrReadyNoLnoHazard7   = Bool(OUTPUT)
  val IR_instrReadyNoLfence       = Bool(OUTPUT)
  val IR_instrReadyNoLtlbSafeR    = Bool(OUTPUT)
  val IR_instrReadyNoLinfSlow     = Bool(OUTPUT)
  val IR_instrReadyNoLctlFlow     = Bool(OUTPUT)

  val reset_vector = UInt(INPUT, ADDR_LEN)
  val instrFetchPerf = Bool(OUTPUT)

  // **** Bus connected between instrUnit and Row ****
  // instrMemReq is asserted when instrUnit is needed to be accessed with address pcMemReq.
  val instrMemReq     = Bool(OUTPUT)
  val instrMemKeep    = Bool(OUTPUT)
  val pcMemReq        = UInt(OUTPUT, ADDR_LEN)

  val validWaySel     = Vec(NUM_PUS, Bool()).asInput
  val wayIdSel        = Vec(NUM_PUS, UInt(width=log2Up(nWaysIC))).asInput

  // TLB sfence interface.
  val sfenceFlush     = Bool(INPUT) // Flush all the iTLB and the PTW.
  val sfence_rs1      = Bool(INPUT) // These are valid only when flowAlteredM is asserted.
  val sfence_rs2      = Bool(INPUT) // These are valid only when flowAlteredM is asserted.

  val sfenceReq_valid = Bool(OUTPUT)
  val sfenceReq_rs1   = Bool(OUTPUT)
  val sfenceReq_rs2   = Bool(OUTPUT)

  // Asserting instrMemStFetch means that the controller start fetching the needed instruction.
  val instrMemStFetch = Bool(INPUT)

  // A flag to indicate the recently received flow altered has been forwarded to the ICache.
  val instrMemFwFetch = Bool(INPUT)

  // The read instruction is provided through instrMemResp and validated with instrMemValidEarly signal.
  val instrMemValidEarly      = Bool(INPUT)
  val instrMemResp            = Vec(CACHE_LINE_LEN / INSTR_LEN, UInt(INPUT, INSTR_LEN))
  val instrMemRespWord        = UInt(INPUT, INSTR_LEN)
  val instrMemFailCauseEarly  = Bool(INPUT)
  val instrMemFailTlbMiss     = Bool(INPUT)
  val icacheReady             = Vec(NUM_PUS, Bool()).asInput
  val tlbReady                = Bool(INPUT)
  val ae                      = Bool(INPUT)
  val pf                      = Bool(INPUT)

  // **** Bus connected between a Row and PipeUnit ****
  // Asserting instrReady means that the row has an instruction to be issued.
  // addr and instr are the address and the instruction, respectively.
  val instrReady       = Bool(OUTPUT)
  val instrReadyF      = Bool(OUTPUT)
  val instrReadyM      = Bool(OUTPUT)
  val isNegOp2Early    = Bool(OUTPUT)
  val isDivEarly       = Bool(OUTPUT)
  val isMulLEarly      = Bool(OUTPUT)
  val isMulHEarly      = Bool(OUTPUT)
  val isChkFpuRdyEarly = Bool(OUTPUT)
  val isRvcEarly       = Bool(OUTPUT)
  val aeOut            = Bool(OUTPUT)
  val pfOut            = Bool(OUTPUT)
  val aeSOut           = Bool(OUTPUT)
  val pfSOut           = Bool(OUTPUT)
  val addr             = UInt(OUTPUT, ADDR_LEN)
  val instr            = UInt(OUTPUT, INSTR_LEN)

  // This can be one cycle after issuing instrReady.
  // Indicate a branch instruction that we do not need to to forward its flow alter signal to the instruction unit.
  val toBeSpeculatedD1 = Bool(OUTPUT)

  // It is asserted when the controller accepts the provided instruction, the row
  // shall start preparing the next instruction to be issued.
  val rowSel     = Bool(INPUT)

  // **** Signals used to return the decoding flags ****
  val isLdStM         = Bool(INPUT)
  val isStM           = Bool(INPUT)
  val isAmoLrScM      = Bool(INPUT)
  val isFenciM        = Bool(INPUT)
  val isFenc          = Bool(INPUT)
  val isFencM         = Bool(INPUT)
  val isInfLoop       = Bool(INPUT)
  val checkRegHazard  = Bool(INPUT)
  val checkFpuHazard  = Bool(INPUT)
  val isWaitEndExec   = Bool(INPUT)
  val checkRegHazardM = Bool(INPUT)
  val checkFpuHazardM = Bool(INPUT)
  val isWaitEndExecM  = Bool(INPUT)
  val isBackBranch    = Bool(INPUT)
  val isCondBranch    = Bool(INPUT)

  // **** Signals used to update PC ****
  // Only in the case of taken branch or jump, the integer pipeline sends the new PC address
  // along with the context ID it's for, and asserts flowAltered.
  val flowAltered    = Bool(INPUT)
  val alteredPc      = UInt(INPUT, ADDR_LEN)
  val flowAlteredM   = Bool(INPUT)
  val alteredPcM     = UInt(INPUT, ADDR_LEN)

  // **** Execution flags generated by the PipeUnit.****
  val endExecAckEarly= Bool(INPUT)

  // **** Bus connected between a Row and Regfset ****
  val regfsetSafe0   = Bool(INPUT)
  val regfsetIsReal0 = Bool(INPUT)
  val regfsetRegId0  = UInt(INPUT, REG_ID_LEN)
  val regfsetSafe2   = Bool(INPUT)
  val regfsetRegId2  = UInt(INPUT, REG_ID_LEN)
  val regfsetIsFpu2  = Bool(INPUT)
  val regfsetSafe3   = Bool(INPUT)
  val regfsetRegId3  = UInt(INPUT, REG_ID_LEN)
  val regfsetisLd3   = Bool(INPUT)
  val regfsetisSt3   = Bool(INPUT)
  val regfsetIsFpu3  = Bool(INPUT)

  // **** Bus connected between a Row and dmem ****
  val ldStSafeEarly  = Bool(INPUT)
  val stSafeLate     = Bool(INPUT)
  val stSafeLateUn   = Bool(INPUT)
  val duWriteEarly   = Bool(INPUT)
  val duRegIdEarly   = UInt(INPUT, REG_ID_LEN)
  val duIsFpuEarly   = Bool(INPUT)
  val duHitEarlyX    = Bool(INPUT)

  // Prevent ST/LD instruction
  val disableDiv        = Bool(INPUT)
  val disableMulL       = Bool(INPUT)
  val disableMulH       = Bool(INPUT)
  val disableFpuInstr   = Bool(INPUT)

  // **** Bus connected between a Row and FPU ****
  val fpuSafe0          = Bool(INPUT)
  val fpuRegId0         = UInt(INPUT, REG_ID_LEN)
  val fpuIsReal0        = Bool(INPUT)
  val fpuSafe1          = Bool(INPUT)
  val fpuRegId1         = UInt(INPUT, REG_ID_LEN)
  val fpuIsReal1        = Bool(INPUT)
  val fpuSafe2          = Bool(INPUT)
  val fpuRegId2         = UInt(INPUT, REG_ID_LEN)
  val fpuIsReal2        = Bool(INPUT)
  val fpuSafe3          = Bool(INPUT)
  val fpuRegId3         = UInt(INPUT, REG_ID_LEN)
  val fpuIsReal3        = Bool(INPUT)

  // Hazard status.
  val rowStatus         = UInt(OUTPUT, 8)

  // Define bus from CSR.
  val csrUnitCtxtUnit   = new CsrUnitCtxtUnitBundle().asInput

  // Slow down the run for debug purposes.
  val slowDownDbg    = Bool(INPUT)

  // Control the RF array for each array.
  val rowToRfI      = new RowToRfIBundle()
  val rowToRfF      = new RowToRfFBundle()

  // Flush the ICache.
  val flush_icache   = Bool(OUTPUT)
}


// The context row implementation consists mainly of a state machine the reads from the instrUnit the needed
// instruction, the read instruction is issued to be provided to the PipeUnit.
//
// The row can receive a flow altered, in this case the PC shall be updated with the provided new address.
class CtxtRow()(implicit p: Parameters) extends CoreModule()(p) with HasCoreParameters
{
  // Define I/O connections.
  val io             = new CtxtRowIo()

  // Align the reset vector.
  def alignPC(pc: UInt) = ~(~pc | (coreInstBytes - 1))

  // Flag to indicate that we are in speculation mode.
  val IF_isSpecu_reg          = Reg(init=Bool(true))

  // Number of fetch cycles.
  val NUM_FETCH_CYCS = 3

  // instrUnit request signals.
  val IF_nxtInstrAddr_reg     = RegInit(t = UInt(width = ADDR_LEN), UInt(p(BootROMParams).hang))
  assert(alignPC(UInt(p(BootROMParams).hang)) === UInt(p(BootROMParams).hang)) // Avoid using alignPC in the init statement above.
  val IF_noSuccSameWay_reg    = Reg(init=Bool(false))
  val IF_instrMemReq_reg      = Reg(init=Bool(true))
  val IF_sfenceValidReq_reg   = Reg(init=Bool(false))
  val IF_isPrevSfence_reg     = Reg(init=Bool(false))
  val IF_sfenceRs1Req_reg     = Reg(init=Bool(false))
  val IF_sfenceRs2Req_reg     = Reg(init=Bool(false))
  val IR_instrIssuedP_reg     = Reg(init=Bool(false))
  val IR_secondHlfSel_reg     = Reg(init=Bool(false))
  val IF_isCached_reg         = Reg(init=Bool(false))
  val IF_instr_reg            = Reg(init=UInt(0, INSTR_LEN))
  val IF_rvcFullInstValid_reg = Reg(init=Bool(false))
  val IR_rvcFrstHlfAe_reg     = Reg(init=Bool(false))
  val IR_rvcFrstHlfPf_reg     = Reg(init=Bool(false))
  val IR_rvcAddrNoUpd_reg     = Reg(init=Bool(false))
  val IR_rvcPc_reg            = Reg(init=UInt(0, ADDR_LEN))
  val IR_rvcPrevHalf_reg      = Reg(init=UInt(0, INSTR_LEN / 2))
  val IF_cacheBaseAddr_reg    = Reg(init=UInt(0, 13 - log2Up(CACHE_LINE_LEN/8)))
  val IF_waitForDecVal_reg    = Reg(init=Bool(false))
  val IF_waitTlb_reg          = Reg(init=Bool(false))
  val IF_waitICache_reg       = Reg(init=Bool(false))
  val IF_aeE_reg           = Reg(init=Bool(false))
  val IF_pfE_reg           = Reg(init=Bool(false))
  val IF_newInstrAvail_reg = Reg(init=Bool(false))
  val IF_newAddrReady_reg  = Reg(init=Bool(false))
  val IF_ae_reg            = Reg(init=Bool(false))
  val IF_pf_reg            = Reg(init=Bool(false))
  val IF_canBeSameCL_reg   = Reg(init=Bool(false))
  val justBranch_reg       = Reg(init=Bool(false))
  val IF_isFpuIndep_reg    = Reg(init=Bool(false))
  val IF_isNextFpu_reg     = Reg(init=Bool(false))
  val IF_op1D1_reg         = Reg(init=UInt(0, REG_ID_LEN))
  val IF_op2D1_reg         = Reg(init=UInt(0, REG_ID_LEN))

  // Flag to indicates that the instruction has been assembled.
  val IF_newInstrAvail   = IF_newInstrAvail_reg && (!IR_secondHlfSel_reg || IF_rvcFullInstValid_reg)
  io.instrFetchPerf := IF_newInstrAvail
  // Keep track the ST/LD instructions
  // ST_CNTR_LEN is needed to be wide enough to cover DU_QUEUE_DEPTH and the DCache SDQ buffer.
  val ST_CNTR_LEN          = 12
  val ldStCntrLate_reg     = Reg(init=UInt(0, ST_CNTR_LEN))
  val ldStCntrEarly_reg    = Reg(init=UInt(0, log2Up(DU_QUEUE_DEPTH + 3)))
  val ldStWait_reg         = Reg(init=Bool(false))
  val waitFence_reg        = Reg(init=Bool(false))
  val waitFenceCntr_reg    = Reg(init=UInt(0, 3))
  val fenceIP_reg          = Reg(init=Bool(false))
  val isFenci_reg          = Reg(init=Bool(false))

  // Latched valid instruction.
  val IR_instr_reg         = Reg(init=UInt(0, INSTR_LEN))
  val IR_addr_reg          = Reg(init=UInt(0, ADDR_LEN))
  val IR_instrReady_reg    = Reg(init=Bool(false))
  val IR_isLdSt_reg        = Reg(init=Bool(false))
  val IR_isLd_reg          = Reg(init=Bool(false))
  val IR_isNegOp2_reg      = Reg(init=Bool(false))
  val IR_noRegCh_reg       = Reg(init=Bool(false))
  val IR_isDivMulPrev_reg  = Reg(init=Bool(false))
  val IR_isDiv_reg         = Reg(init=Bool(false))
  val IR_isMulL_reg        = Reg(init=Bool(false))
  val IR_isMulH_reg        = Reg(init=Bool(false))
  val IR_isFpu_reg         = Reg(init=Bool(false))
  val IR_isFpuChIp_reg     = Reg(init=Bool(false))
  val IR_isRvc_reg         = Reg(init=Bool(false))
  val IR_isChkFpuRdy_reg   = Reg(init=Bool(false))
  val IR_isFpuIndep_reg    = Reg(init=Bool(false))
  val IR_isSfence_reg      = Reg(init=Bool(false))
  val IR_ae_reg            = Reg(init=Bool(false))
  val IR_pf_reg            = Reg(init=Bool(false))
  val IR_isFsflags_reg     = Reg(init=Bool(false))

  // Row cache line and address.
  val row_cLine_reg        = Reg(Vec(CACHE_LINE_LEN / INSTR_LEN,UInt(width=INSTR_LEN)))
  val IF_newInstrAddr_reg  = Reg(init=UInt(0, ADDR_LEN))

  // Finite state machine state.
  val ctlFlowState_reg     = Reg(init=Bool(false))
  val isPrevSt_reg         = Reg(init=Bool(false))
  val isPrevLd_reg         = Reg(init=Bool(false))
  val fetchValidP_reg      = Reg(init=Bool(false))
  val isPipeFlowAlt_reg    = Reg(init=Bool(false))
  val changedReg_reg       = Reg(init=UInt(0, REG_ID_LEN))
  val decValid_reg         = Reg(init=Bool(false))
  val decValidIP_reg       = Reg(init=Bool(false))
  val decValidD1_reg       = Reg(init=Bool(false))
  val decValidD2_reg       = Reg(init=Bool(false))
  val flowAlteredD1_reg    = Reg(init=Bool(false))
  val flowAlteredD2_reg    = Reg(init=Bool(false))
  val flowAlteredD3_reg    = Reg(init=Bool(false))
  val noHazard             = Wire(Bool())
  val fetchCntr_reg        = Reg(init=UInt(NUM_FETCH_CYCS + 1, log2Up(NUM_FETCH_CYCS + 2)))
  val isAlterNxtCycle_reg  = Reg(init=Bool(false))
  val decValidMP_reg       = Reg(init=Bool(false))
  val decValidMD1_reg      = Reg(init=Bool(false))
  val decValidMD2_reg      = Reg(init=Bool(false))
  val decValidFP_reg       = Reg(init=Bool(false))
  val decValidFD1_reg      = Reg(init=Bool(false))
  val decValidFD2_reg      = Reg(init=Bool(false))

  val regChangD1_reg       = Reg(init=Bool(false))
  val regChangD2_reg       = Reg(init=Bool(false))
  val regChangD3_reg       = Reg(init=Bool(false))
  val regChangD4_reg       = Reg(init=Bool(false))
  val regChangD5_reg       = Reg(init=Bool(false))
  val regChangD6_reg       = Reg(init=Bool(false))
  val changedRegD1_reg     = Wire(UInt())
  val changedRegD2_reg     = Wire(UInt())
  val changedRegD3_reg     = Wire(UInt())
  val changedRegD4_reg     = Wire(UInt())
  val changedRegD5_reg     = Wire(UInt())
  val changedRegD6_reg     = Wire(UInt())
  val rdEnaID1_reg         = Reg(init=Bool(false))
  val rdEnaFD1_reg         = Reg(init=Bool(false))

  val IR_rowToRfF_reg         = Reg(new RowToRfFBundle(), init=new RowToRfFBundle().fromBits(0))
  val IR_rdCmdPendingF1_reg   = Reg(init=Bool(false))
  val IR_rdCmdPendingF2_reg   = Reg(init=Bool(false))
  val IR_rdCmdPendingF3_reg   = Reg(init=Bool(false))

  val regfsetSafe0D1_reg   = Reg(init=Bool(false))
  val regfsetRegId0D1_reg  = Wire(UInt())

  val isFpuPath            = Wire(Bool())
  val isMemPath            = Wire(Bool())

  val IF_op2     = Wire(UInt())
  val IF_op1     = Wire(UInt())
  val IF_wrAddr  = Wire(UInt())
  val IF_op1C    = Wire(UInt())
  val IF_wrAddrC = Wire(UInt())

  val IR_op1_reg      = Reg(init=UInt(0, REG_ID_LEN))
  val IR_op1C_reg     = Reg(init=UInt(0, REG_ID_LEN))
  val IR_op2_reg      = Reg(init=UInt(0, REG_ID_LEN))
  val IR_wrAddr_reg   = Reg(init=UInt(0, REG_ID_LEN))

  val duRegIdD0_reg   = Reg(init=UInt(0, REG_ID_LEN))
  val duWriteID0_reg  = Reg(init=Bool(false))
  val duWriteFD0_reg  = Reg(init=Bool(false))

  val duRegIdD1_reg   = Reg(init=UInt(0, REG_ID_LEN))
  val duWriteID1_reg  = Reg(init=Bool(false))
  val duWriteFD1_reg  = Reg(init=Bool(false))

  // Control the branch prediction signals.
  val isBackBranch_reg        = Reg(init=Bool(false))
  val isCondBranchD1_reg      = Reg(init=Bool(false))
  val isCondBranchD2_reg      = Reg(init=Bool(false))
  val condBranchTblValid_reg  = Reg(init=Bool(false))
  val cbPredictionP_reg       = Reg(init=Bool(false))
  val cbPredictionS_reg       = Reg(init=Bool(false))
  val cbUsedBefore_reg        = Reg(init=Bool(false))
  val cbJust_reg              = Reg(init=Bool(false))
  val cbPredictionSPD1_reg    = Reg(init=Bool(false))
  val cbPredictionSPD2_reg    = Reg(init=Bool(false))
  val instrMemSpecAccess_reg  = Reg(init=Bool(false))
  val IR_addrD1_reg           = Reg(init=UInt(0, ADDR_LEN))
  val IR_condBranchPc_reg     = Reg(init=UInt(0, ADDR_LEN))
  val condBranchPC_reg        = Reg(init=UInt(0, ADDR_LEN))
  val condBranchJmp_reg       = Reg(init=UInt(0, ADDR_LEN))
  val condBranchNxt_reg       = Reg(init=UInt(0, ADDR_LEN))

  // Signals used to control the RF.
  val newInstrAvailaStD1_reg = Reg(init=Bool(false))
  val rdEna1I_reg            = Reg(init=Bool(false))
  val rdEna2I_reg            = Reg(init=Bool(false))
  val newInstrAvailaSt       = Wire(Bool())
  val newInstrAvailaStP      = Wire(Bool())

  // Expander module.
  val IF_instr  = Mux(IR_secondHlfSel_reg, Cat(IF_instr_reg(15, 0), IR_rvcPrevHalf_reg), IF_instr_reg)
  val IF_isRVC  = IF_instr(1,0) =/= UInt(3)

  def a_rs2p  = BitPat("b00")
  def a_rs2   = BitPat("b01")
  def a_x0    = BitPat("b1?")
  def x_xx    = BitPat("b??")

  def b_rs1p  = BitPat("b00")
  def b_rd    = BitPat("b01")
  def b_sp    = BitPat("b10")
  def b_x0    = BitPat("b11")

  def c_rs1p  = BitPat("b00")
  def c_rs2p  = BitPat("b01")
  def c_rd    = BitPat("b10")
  def c_x0    = BitPat("b11")
  def c_ra    = BitPat("b0?") // Actually not directly used. The value is chosen not to conflict with c_rd.

  // Bit patterns for the last two bits.
  def BASE_00 = BitPat("b??????????????????????????????00")
  def BASE_01 = BitPat("b??????????????????????????????01")
  def BASE_10 = BitPat("b??????????????????????????????10")
  def BASE_11 = BitPat("b??????????????????????????????11")

  //decode table for register addresses in RVC
  val regaddr_table = Array(
      C_ADDI4SPN_OVLP -> List(a_x0  ,         b_sp  ,        c_rs2p),
      C_FLD           -> List(a_x0  ,         b_rs1p,        c_rs2p),
      C_LW            -> List(a_x0  ,         b_rs1p,        c_rs2p),
    //C_FLW           -> List(a_x0  ,         b_rs1p,        c_rs2p), // Used for (xLen == 32). Replaced by C_LD.
      C_FSD           -> List(a_rs2p,         b_rs1p,        c_x0  ),
      C_SW            -> List(a_rs2p,         b_rs1p,        c_x0  ),
    //C_FSW           -> List(a_rs2p,         b_rs1p,        c_x0  ), // Used for (xLen == 32). Replaced by C_SD.
      C_SUB           -> List(a_rs2p,         b_rs1p,        c_rs1p),
      C_XOR           -> List(a_rs2p,         b_rs1p,        c_rs1p),
      C_OR            -> List(a_rs2p,         b_rs1p,        c_rs1p),
      C_AND           -> List(a_rs2p,         b_rs1p,        c_rs1p),
      C_SUBW          -> List(a_rs2p,         b_rs1p,        c_rs1p),
      C_ADDW          -> List(a_rs2p,         b_rs1p,        c_rs1p),
    //C_SRLI_RV32     -> List(a_x0  ,         b_rs1p,        c_rs1p), // Used for (xLen == 32)
    //C_SRAI_RV32     -> List(a_x0  ,         b_rs1p,        c_rs1p), // Used for (xLen == 32)
      C_SRLI          -> List(a_x0  ,         b_rs1p,        c_rs1p),
      C_SRAI          -> List(a_x0  ,         b_rs1p,        c_rs1p),
      C_ANDI          -> List(a_x0  ,         b_rs1p,        c_rs1p),
    //C_ADDI16SP_OVLP -> List(a_x0  ,         b_rd  ,        c_rd  ), // This is a special case of C_LUI_OVLP.
      C_ADDIW         -> List(a_x0  ,         b_rd  ,        c_rd  ),
      C_ADDI          -> List(a_x0  ,         b_rd  ,        c_rd  ),
    //C_JAL           -> List(a_x0  ,         b_rd  ,        c_rd  ), // Used for (xLen == 32). Replaced by C_ADDIW
      C_LI            -> List(a_x0  ,         b_x0  ,        c_rd  ),
      C_LUI_OVLP      -> List(a_x0  ,         b_rd  ,        c_rd  ), // We need to use b_x0; however, we use b_rd to consider the special case of C_ADDI16SP_OVLP.
      C_BEQZ          -> List(a_x0  ,         b_rs1p,        c_x0  ),
      C_BNEZ          -> List(a_x0  ,         b_rs1p,        c_x0  ),
    //C_JR_OVLP       -> List(a_rs2 ,         b_rd  ,        c_x0  ), // A special case of C_MV_OVLP.
    //C_JALR_OVLP     -> List(a_rs2 ,         b_rd  ,        c_ra  ), // A special case of C_ADD_OVLP.
    //C_SLLI_RV32     -> List(a_x0  ,         b_rd  ,        c_rd  ), // Used for (xLen == 32)
      C_MV_OVLP       -> List(a_rs2 ,         b_rd  ,        c_rd  ), // We need to use b_x0; however, we use b_rd to consider the special case of C_JR_OVLP. Also, we need to use c_x0 for C_JR_OVLP, this is corrected later.
      C_ADD_OVLP      -> List(a_rs2 ,         b_rd  ,        c_rd  ),
      C_LDSP          -> List(a_x0  ,         b_sp  ,        c_rd  ),
      C_SDSP          -> List(a_rs2 ,         b_sp  ,        c_x0  ),
      C_SLLI          -> List(a_x0  ,         b_rd  ,        c_rd  ),
      C_FLDSP         -> List(a_x0  ,         b_sp  ,        c_rd  ),
      C_LWSP          -> List(a_x0  ,         b_sp  ,        c_rd  ),
    //C_FLWSP         -> List(a_x0  ,         b_sp  ,        c_rd  ), // Used for (xLen == 32). Replaced by C_LDSP
      C_FSDSP         -> List(a_rs2 ,         b_sp  ,        c_x0  ),
      C_SWSP          -> List(a_rs2 ,         b_sp  ,        c_x0  ),
    //C_FSWSP         -> List(a_rs2 ,         b_sp  ,        c_x0  ), // Used for (xLen == 32). Replaced by C_SDSP
      C_LD            -> List(a_x0  ,         b_rs1p,        c_rs2p),
      C_SD            -> List(a_rs2p,         b_rs1p,        c_x0  ),

      // Do not care cases.
      BASE_11         -> List(x_xx  ,         x_xx  ,        x_xx  )
    )
  val regaddr_sel = DecodeLogic(IF_instr(15, 0), List(x_xx, x_xx, c_x0), regaddr_table) // The default is c_x0.

  def regaddr_muxA(regaddr : UInt) = {
    // This depends on the constant values of a_*.
    Mux(regaddr(0), IF_instr(6,2), Cat(UInt(1,2), IF_instr(4,2))) & Fill(5, !regaddr(1))
  }

  def regaddr_muxB(regaddr : UInt) = {
    // This depends on the constant values of b_*.
    Mux(regaddr(1), Mux(regaddr(0), UInt(0,5), UInt(2,5)), Mux(regaddr(0), IF_instr(11,7), Cat(UInt(1,2), IF_instr(9,7))))
  }

  def regaddr_muxC(regaddr : UInt) = {
    // This depends on the constant values of c_*.
    Mux(regaddr(1), Mux(regaddr(0), UInt(0,5), IF_instr(11,7)), Mux(regaddr(0), Cat(UInt(1,2), IF_instr(4,2)), Cat(UInt(1,2), IF_instr(9,7))))
  }

  val isNextLd        = Wire(Bool())
  val isNextSt        = Wire(Bool())
  val isNextDiv       = Wire(Bool())
  val isNextChkFpuRdy = Wire(Bool())
  val isNextFpu       = Wire(Bool())
  val isFpuIndep      = Wire(Bool())
  val isNextFpuChIp   = Wire(Bool())
  val isSfence        = Wire(Bool())
  val isNextMulL      = Wire(Bool())
  val isNextMulH      = Wire(Bool())

  // Decoding table for RVC instructions
  val decTable_RVC = Array(
                C_LD       -> List(Y, N, N),
                C_LW       -> List(Y, N, N),
                C_LDSP     -> List(Y, N, N),
                C_LWSP     -> List(Y, N, N),
                C_SW       -> List(N, Y, N),
                C_SWSP     -> List(N, Y, N),
                C_SD       -> List(N, Y, N),
                C_SDSP     -> List(N, Y, N),

                // Do not care cases.
                BASE_11    -> List(X, X, X)
    )
  val fpuDecTable_RVC = Array(
                C_FLD      -> List(Y, N, Y),
                C_FLDSP    -> List(Y, N, Y),
              //C_FLW      -> List(Y, N, N), // Used for (xLen == 32)
              //C_FLWSP    -> List(Y, N, N), // Used for (xLen == 32)
                C_FSD      -> List(N, Y, Y),
                C_FSDSP    -> List(N, Y, Y),
              //C_FSW      -> List(N, Y, N), // Used for (xLen == 32)
              //C_FSWSP    -> List(N, Y, N), // Used for (xLen == 32)
    )
  val csignals_RVC = DecodeLogic(IF_instr(15, 0), List(N, N, N), if(usingFPU) (decTable_RVC ++ fpuDecTable_RVC) else decTable_RVC)
  require(xLen == 64)

  // Decoding table
  val decTable = Array(
                LB         -> List(Y, N, N, N, N, N, N, N, N, N),
                LH         -> List(Y, N, N, N, N, N, N, N, N, N),
                LW         -> List(Y, N, N, N, N, N, N, N, N, N),
                LD         -> List(Y, N, N, N, N, N, N, N, N, N),
                LBU        -> List(Y, N, N, N, N, N, N, N, N, N),
                LHU        -> List(Y, N, N, N, N, N, N, N, N, N),
                LWU        -> List(Y, N, N, N, N, N, N, N, N, N),
                SB         -> List(N, Y, N, N, N, N, N, N, N, N),
                SH         -> List(N, Y, N, N, N, N, N, N, N, N),
                SW         -> List(N, Y, N, N, N, N, N, N, N, N),
                SD         -> List(N, Y, N, N, N, N, N, N, N, N),

                // Those ops are pass through the dataCache even if they are not LD/ST..
                FENCE_I    -> List(Y, N, N, N, N, N, N, N, N, N),
                SFENCE_VMA -> List(Y, N, N, N, N, N, N, Y, N, N),

                LR_W       -> List(Y, N, N, N, N, N, N, N, N, N),
                LR_D       -> List(Y, N, N, N, N, N, N, N, N, N),
                SC_W       -> List(Y, N, N, N, N, N, N, N, N, N),
                SC_D       -> List(Y, N, N, N, N, N, N, N, N, N),

                AMOADD_W   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOXOR_W   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOSWAP_W  -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOAND_W   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOOR_W    -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOMIN_W   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOMINU_W  -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOMAX_W   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOMAXU_W  -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOADD_D   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOSWAP_D  -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOXOR_D   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOAND_D   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOOR_D    -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOMIN_D   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOMINU_D  -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOMAX_D   -> List(Y, N, N, N, N, N, N, N, N, N),
                AMOMAXU_D  -> List(Y, N, N, N, N, N, N, N, N, N),

                MUL        -> List(N, N, N, N, N, N, N, N, Y, N),
                MULH       -> List(N, N, N, N, N, N, N, N, N, Y),
                MULHU      -> List(N, N, N, N, N, N, N, N, N, Y),
                MULHSU     -> List(N, N, N, N, N, N, N, N, N, Y),
                MULW       -> List(N, N, N, N, N, N, N, N, Y, N),
                DIV        -> List(N, N, Y, N, N, N, N, N, N, N),
                DIVU       -> List(N, N, Y, N, N, N, N, N, N, N),
                REM        -> List(N, N, Y, N, N, N, N, N, N, N),
                REMU       -> List(N, N, Y, N, N, N, N, N, N, N),
                DIVW       -> List(N, N, Y, N, N, N, N, N, N, N),
                DIVUW      -> List(N, N, Y, N, N, N, N, N, N, N),
                REMW       -> List(N, N, Y, N, N, N, N, N, N, N),
                REMUW      -> List(N, N, Y, N, N, N, N, N, N, N),

                // Do not care cases.
                BASE_00    -> List(X, X, X, X, X, X, X, X, X, X),
                BASE_01    -> List(X, X, X, X, X, X, X, X, X, X),
                BASE_10    -> List(X, X, X, X, X, X, X, X, X, X)
              )
  val fpuDecTable = Array(
                FSGNJ_S    -> List(N, N, N, N, Y, Y, N, N, N, N),
                FSGNJX_S   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FSGNJN_S   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMIN_S     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMAX_S     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FADD_S     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FSUB_S     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMUL_S     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMADD_S    -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMSUB_S    -> List(N, N, N, N, Y, Y, N, N, N, N),
                FNMADD_S   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FNMSUB_S   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FCLASS_S   -> List(N, N, N, N, Y, N, Y, N, N, N),
                FMV_X_S    -> List(N, N, N, N, Y, N, Y, N, N, N),
                FCVT_W_S   -> List(N, N, N, N, Y, N, Y, N, N, N),
                FCVT_WU_S  -> List(N, N, N, N, Y, N, Y, N, N, N),
                FEQ_S      -> List(N, N, N, N, Y, N, Y, N, N, N),
                FLT_S      -> List(N, N, N, N, Y, N, Y, N, N, N),
                FLE_S      -> List(N, N, N, N, Y, N, Y, N, N, N),
                FMV_S_X    -> List(N, N, N, N, Y, N, N, N, N, N),
                FCVT_S_W   -> List(N, N, N, N, Y, N, N, N, N, N),
                FCVT_S_WU  -> List(N, N, N, N, Y, N, N, N, N, N),
                FLW        -> List(Y, N, N, N, Y, N, N, N, N, N),
                FSW        -> List(N, Y, N, N, Y, N, N, N, N, N),
                FDIV_S     -> List(N, N, N, Y, Y, Y, N, N, N, N),
                FSQRT_S    -> List(N, N, N, Y, Y, Y, N, N, N, N),

                FCVT_S_D   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FCVT_D_S   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FSGNJ_D    -> List(N, N, N, N, Y, Y, N, N, N, N),
                FSGNJX_D   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FSGNJN_D   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMIN_D     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMAX_D     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FADD_D     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FSUB_D     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMUL_D     -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMADD_D    -> List(N, N, N, N, Y, Y, N, N, N, N),
                FMSUB_D    -> List(N, N, N, N, Y, Y, N, N, N, N),
                FNMADD_D   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FNMSUB_D   -> List(N, N, N, N, Y, Y, N, N, N, N),
                FCLASS_D   -> List(N, N, N, N, Y, N, Y, N, N, N),
                FCVT_W_D   -> List(N, N, N, N, Y, N, Y, N, N, N),
                FCVT_WU_D  -> List(N, N, N, N, Y, N, Y, N, N, N),
                FEQ_D      -> List(N, N, N, N, Y, N, Y, N, N, N),
                FLT_D      -> List(N, N, N, N, Y, N, Y, N, N, N),
                FLE_D      -> List(N, N, N, N, Y, N, Y, N, N, N),
                FCVT_D_W   -> List(N, N, N, N, Y, N, N, N, N, N),
                FCVT_D_WU  -> List(N, N, N, N, Y, N, N, N, N, N),
                FLD        -> List(Y, N, N, N, Y, N, N, N, N, N),
                FSD        -> List(N, Y, N, N, Y, N, N, N, N, N),
                FDIV_D     -> List(N, N, N, Y, Y, Y, N, N, N, N),
                FSQRT_D    -> List(N, N, N, Y, Y, Y, N, N, N, N),

                FCVT_L_S   -> List(N, N, N, N, Y, N, Y, N, N, N),
                FCVT_LU_S  -> List(N, N, N, N, Y, N, Y, N, N, N),
                FCVT_S_L   -> List(N, N, N, N, Y, N, N, N, N, N),
                FCVT_S_LU  -> List(N, N, N, N, Y, N, N, N, N, N),

                FMV_X_D    -> List(N, N, N, N, Y, N, Y, N, N, N),
                FCVT_L_D   -> List(N, N, N, N, Y, N, Y, N, N, N),
                FCVT_LU_D  -> List(N, N, N, N, Y, N, Y, N, N, N),
                FMV_D_X    -> List(N, N, N, N, Y, N, N, N, N, N),
                FCVT_D_L   -> List(N, N, N, N, Y, N, N, N, N, N),
                FCVT_D_LU  -> List(N, N, N, N, Y, N, N, N, N, N)
              )

  // Check if the instruction to be issued is LD or ST.
  val csignals   = DecodeLogic(IF_instr, List(N, N, N, N, N, N, N, N, N, N), if(usingFPU) (decTable ++ fpuDecTable) else decTable)

  val regaddr_sel_1_x0 = Wire(Bool())
  val regaddr_sel_2_rd = Wire(Bool())

  regaddr_sel_1_x0 := (regaddr_sel(1) === b_x0)
  regaddr_sel_2_rd := (regaddr_sel(2) === c_rd)
  when(IF_isRVC){
    IF_op2     := regaddr_muxA(regaddr_sel(0))
    IF_op1     := regaddr_muxB(regaddr_sel(1))
    IF_wrAddr  := regaddr_muxC(regaddr_sel(2))
    when(IF_instr(15, 0) === C_JALR_OVLP)  {
      IF_wrAddr        := UInt(1,5) // This needs to be done on IF_wrAddr path, as it is not x0 register.
      regaddr_sel_2_rd := Bool(false)
    }

    // Correct the destination register. if the target is x0.
    IF_op1C    := IF_op1
    when(((IF_instr(15, 0) === C_LUI_OVLP) && (IF_instr(15, 0) =/= C_ADDI16SP_OVLP)) || ((IF_instr(15, 0) === C_MV_OVLP) && (IF_instr(15, 0) =/= C_JR_OVLP)) || (IF_instr(15, 0) === C_RESERVED_OVLP)) {
      IF_op1C          := UInt(0, REG_ID_LEN)
      regaddr_sel_1_x0 := Bool(true)
    }

    IF_wrAddrC := IF_wrAddr
    when(IF_instr(15, 0) === C_JR_OVLP) {
      IF_wrAddrC       := UInt(0, REG_ID_LEN)
      regaddr_sel_2_rd := Bool(false)
    }

    isNextLd        := csignals_RVC(0)
    isNextSt        := csignals_RVC(1)
    isNextFpu       := csignals_RVC(2)
  }
  .otherwise{
    IF_op2     := IF_instr(24, 20)
    IF_op1     := IF_instr(19, 15)
    IF_wrAddr  := IF_instr(11, 7)

    IF_op1C    := IF_instr(19, 15)
    IF_wrAddrC := IF_instr(11, 7)

    isNextLd        := csignals(0)
    isNextSt        := csignals(1)
    isNextFpu       := csignals(4)
  }
  isNextDiv       := csignals(2) & !IF_isRVC
  isNextChkFpuRdy := csignals(3) & !IF_isRVC
  isFpuIndep      := csignals(5) & !IF_isRVC
  isNextFpuChIp   := csignals(6) & !IF_isRVC
  isSfence        := csignals(7) & !IF_isRVC
  isNextMulL      := csignals(8) & !IF_isRVC
  isNextMulH      := csignals(9) & !IF_isRVC

  val dec_overlp = regaddr_sel_1_x0 || (regaddr_sel_2_rd && (regaddr_sel(0) === a_rs2))

  when(IR_instrReady_reg) {
    changedReg_reg := IR_wrAddr_reg
  }
  decValid_reg       := io.rowSel
  decValidIP_reg     := io.rowSel && !io.instrReadyF && !io.instrReadyM
  decValidD1_reg     := decValidIP_reg
  decValidD2_reg     := decValidD1_reg

  decValidMP_reg     := io.rowSel && io.instrReadyM
  decValidMD1_reg    := decValidMP_reg
  decValidMD2_reg    := decValidMD1_reg

  decValidFP_reg     := io.rowSel && io.instrReadyF
  decValidFD1_reg    := decValidFP_reg
  decValidFD2_reg    := decValidFD1_reg

  // Validate the flow alter flag.
  // IMPORTANT: it must be synchronized with the PipeUnit.
  val sfenceAlt     = (io.flowAlteredM && decValidMD2_reg) || (io.sfenceFlush && decValidD2_reg)
  val flowAlteredIF = io.flowAltered && decValidD2_reg
  val flowAlteredI  = flowAlteredIF && !(cbPredictionS_reg && cbPredictionSPD2_reg) // Filter out the alters that have been predicted.
  val flowAltered   = flowAlteredI || sfenceAlt

  // Delay the flowAlter
  flowAlteredD1_reg := flowAltered
  flowAlteredD2_reg := flowAlteredD1_reg
  flowAlteredD3_reg := flowAlteredD2_reg

  // We want to ignore any jump that may occur the next cycle.
  when(flowAltered || io.csrUnitCtxtUnit.csrFlowAlt) {
    decValidD1_reg      := Bool(false)
    decValidD2_reg      := Bool(false)
    decValidMD1_reg     := Bool(false)
    decValidMD2_reg     := Bool(false)
  }

  // Confirm if to use the same available CL.
  val IF_useSameCL = IF_canBeSameCL_reg && (IF_cacheBaseAddr_reg === IF_nxtInstrAddr_reg(12, log2Up(CACHE_LINE_LEN/8)))

  // Increment the fetch counter.
  when(fetchCntr_reg <= UInt(NUM_FETCH_CYCS)) {
    fetchCntr_reg := fetchCntr_reg + UInt(1)
  }

  // Reset the fetch counter.
  val IF_instrMemReqNxt = Wire(Bool())
  when(IF_instrMemReq_reg && !IF_useSameCL) {
    fetchCntr_reg   := UInt(NUM_FETCH_CYCS + 1)
  }

  // Start counting the cycles after receiving start fetch pulse.
  when(io.instrMemStFetch && !IF_useSameCL) {
    fetchCntr_reg := UInt(0)
  }
  when(Bool(ENA_IC_FORWARD) && io.instrMemFwFetch && isAlterNxtCycle_reg && !IF_useSameCL) { // We ignore the forward if we have use the current same CL.
    fetchCntr_reg := UInt(1)
  }

  // Check to activate the instruction memory output.
  fetchValidP_reg  := Bool(false)
  val fetchCntrEnd  = (fetchCntr_reg === UInt(NUM_FETCH_CYCS))
  when(fetchCntrEnd && io.instrMemValidEarly) {
    fetchValidP_reg := Bool(true)

    // Register the LSBs of the address to determine the cache-line will be acquired.
    IF_cacheBaseAddr_reg := io.pcMemReq(12, log2Up(CACHE_LINE_LEN/8))
  }

  // Increment PC.
  def P1_LEN              = ADDR_LEN/2
  val IF_instrAddrIncP1E  = Cat(UInt(0, 1), IF_nxtInstrAddr_reg(P1_LEN-1, 0)) + UInt(4)
  val IF_instrAddrIncP2   = IF_nxtInstrAddr_reg(ADDR_LEN-1, P1_LEN) + UInt(1)
  val IF_instrAddrInc     = Cat(Mux(IF_instrAddrIncP1E(P1_LEN), IF_instrAddrIncP2, IF_nxtInstrAddr_reg(ADDR_LEN-1, P1_LEN)), IF_instrAddrIncP1E(P1_LEN-1, 0))
  require(P1_LEN >= 13) // This is to guarantee the operation of the same cache line detection logic (isCondBranch)

  // Check if the cache line can be used for the new incremented PC.
  val IF_isCached    = (IF_instrAddrInc(CACHE_LINE_LOG2) === IF_nxtInstrAddr_reg(CACHE_LINE_LOG2))

  when(decValidMP_reg && io.isLdStM) {
    isPrevSt_reg := io.isStM || io.isAmoLrScM
    isPrevLd_reg := io.checkRegHazardM || io.isAmoLrScM
  }

  // DU write flags.
  val duWrite   = Reg(next=Reg(next=Reg(next=io.duWriteEarly, init=Bool(false)), init=Bool(false)), init=Bool(false)) && io.duHitEarlyX
  val duRegId   = Reg(next=Reg(next=RegEnable(next=io.duRegIdEarly, enable=io.duWriteEarly, init=UInt(0, REG_ID_LEN)), init=UInt(0, REG_ID_LEN)), init=UInt(0, REG_ID_LEN))
  val duIsFpu   = Reg(next=Reg(next=Reg(next=io.duIsFpuEarly, init=Bool(false)), init=Bool(false)), init=Bool(false))

  // Count the integer ST and FPU St instructions and the integer LD only.
  val inps = PopCount(Cat(io.stSafeLate, io.stSafeLateUn, io.regfsetSafe3 && (io.regfsetisSt3 || (!io.regfsetIsFpu3 && io.regfsetisLd3)), io.regfsetSafe2 && !io.regfsetIsFpu2, duWrite && !duIsFpu))

  // Count the ST instructions.
  when(!(decValidMP_reg && (io.isStM || (io.checkRegHazardM && io.isLdStM))) && (inps > 0)) {
    ldStCntrLate_reg := ldStCntrLate_reg - inps
    assert(ldStCntrLate_reg >= inps, "CTXT ERROR: ldStCntrLate_reg is zero.")
  }
  when(decValidMP_reg && (io.isStM || (io.checkRegHazardM && io.isLdStM))) {
    ldStCntrLate_reg := ldStCntrLate_reg + UInt(1) - inps
    when(inps === UInt(0)) {
      assert(ldStCntrLate_reg < UInt(scala.math.pow(2,ST_CNTR_LEN).intValue - 1), "CTXT ERROR: ldStCntrLate_reg overflow.")
    }
    .otherwise {
      assert(ldStCntrLate_reg >= inps - UInt(1), "CTXT ERROR: ldStCntrLate_reg is zero.")
    }
  }

  // Update ldStWait_reg as soon as possible to be able to support two cycles separation between LD/ST instructions.
  when(io.instrReadyM && (ldStCntrEarly_reg === UInt(DU_QUEUE_DEPTH - 1))) {
    ldStWait_reg := Bool(true)
  }

  when((decValidMP_reg && io.isLdStM) && !io.ldStSafeEarly && !io.regfsetSafe3) {
    ldStCntrEarly_reg := ldStCntrEarly_reg + UInt(1)
    ldStWait_reg := Bool(false)
    when(ldStCntrEarly_reg === UInt(DU_QUEUE_DEPTH - 1)) {
      ldStWait_reg := Bool(true)
    }
    assert(ldStCntrEarly_reg <= UInt(DU_QUEUE_DEPTH - 1), "CTXT ERROR: ldStCntrEarly_reg overflow.")
  }
  .elsewhen(!(decValidMP_reg && io.isLdStM) && io.ldStSafeEarly && io.regfsetSafe3) {
    ldStCntrEarly_reg := ldStCntrEarly_reg - UInt(2)
    ldStWait_reg := Bool(false)
    assert(ldStCntrEarly_reg > UInt(1), "CTXT ERROR: ldStCntrEarly_reg is one.")
  }
  .elsewhen((decValidMP_reg && io.isLdStM) && io.ldStSafeEarly && io.regfsetSafe3) {
    ldStCntrEarly_reg := ldStCntrEarly_reg - UInt(1)
    ldStWait_reg := Bool(false)
    assert(ldStCntrEarly_reg > UInt(0), "CTXT ERROR: ldStCntrEarly_reg is zero.")
  }
  .elsewhen(!(decValidMP_reg && io.isLdStM) && (io.ldStSafeEarly || io.regfsetSafe3)) {
    ldStCntrEarly_reg := ldStCntrEarly_reg - UInt(1)
    ldStWait_reg := Bool(false)
    assert(ldStCntrEarly_reg > UInt(0), "CTXT ERROR: ldStCntrEarly_reg is zero.")
  }

  // Detect if the program hit an infinite loop.
  val infCntr_reg     = Reg(init=UInt(0, 8))
  val infSlowDown_reg = Reg(init=Bool(false))
  when(io.isInfLoop && decValidIP_reg) {
    infCntr_reg     := UInt(255)
    infSlowDown_reg := Bool(true)
  }
  when(infCntr_reg =/= UInt(0)) {
    infCntr_reg := infCntr_reg - UInt(1)
  }
  .otherwise {
    infSlowDown_reg := Bool(false)
  }

  val releaseFenceD1_reg = Reg(init=Bool(false))
  val releaseFence       = (IF_instr(6,0) === UInt(47)) && IF_instr(25) && IF_newInstrAvail_reg
  releaseFenceD1_reg    := releaseFence
  val releaseFenceP      = releaseFence && !releaseFenceD1_reg && !waitFence_reg
  when(releaseFenceP) {
    waitFence_reg     := Bool(true)
    isFenci_reg       := Bool(false)
    waitFenceCntr_reg := UInt(0)
  }

  when(((io.isFenciM || io.isFencM) && decValidMP_reg) || (io.isFenc && decValidIP_reg)) {
    waitFence_reg     := Bool(true)
    isFenci_reg       := io.isFenciM && decValidMP_reg
    waitFenceCntr_reg := UInt(0)
  }

  // Make sure to wait for a minimum number of cycles until any change to the CSR (ptbr and/or status registers) takes effect.
  val NUM_MIN_CYCLES = UInt(5)
  when(waitFence_reg && (waitFenceCntr_reg =/= NUM_MIN_CYCLES)) {
    waitFenceCntr_reg := waitFenceCntr_reg + UInt(1)
  }

  fenceIP_reg  := Bool(false)
  when(waitFence_reg && (waitFenceCntr_reg === NUM_MIN_CYCLES) && (ldStCntrLate_reg === UInt(0))) {
    fenceIP_reg       := isFenci_reg
    waitFence_reg     := Bool(false)
  }

  // Make sure to de-assert the fence if another jump were received. That means the previously received fence was muted.
  val allFlowAltD1_reg = Reg(next=flowAltered || io.csrUnitCtxtUnit.csrFlowAlt, init=Bool(false))
  val allFlowAltD2_reg = Reg(next=allFlowAltD1_reg, init=Bool(false))
  when(flowAltered || io.csrUnitCtxtUnit.csrFlowAlt || allFlowAltD1_reg || allFlowAltD2_reg) {
    fenceIP_reg         := Bool(false)
    waitFence_reg       := Bool(false)
  }
  io.flush_icache := fenceIP_reg

  // Update the available instruction when the instruction is issued to the instruction selection.
  val IF_nxtInstrAddr     = Wire(UInt())
  val IF_nxtInstr         = MuxTree(IF_nxtInstrAddr_reg(CACHE_LINE_LOG2 - 1, log2Up(INSTR_LEN / 8)), row_cLine_reg)
  IF_nxtInstrAddr        := IF_nxtInstrAddr_reg
  when(IR_instrIssuedP_reg) {
    IF_newInstrAvail_reg    := IF_isCached_reg
    IF_newAddrReady_reg     := IF_isCached_reg || IR_secondHlfSel_reg
    IR_rvcPrevHalf_reg      := IF_instr_reg(31, 16)
    IF_rvcFullInstValid_reg := Bool(true)
    when(IF_isCached_reg) {
      // If the instruction is cached.
      IF_instr_reg            := IF_nxtInstr
      IF_newInstrAddr_reg     := IF_nxtInstrAddr_reg
      IF_nxtInstrAddr         := IF_instrAddrInc
      assert(IF_instrAddrInc === IF_nxtInstrAddr_reg + UInt(4))
      IF_isCached_reg         := IF_isCached
      IF_ae_reg               := IF_aeE_reg
      IF_pf_reg               := IF_pfE_reg
      when(IF_aeE_reg || IF_pfE_reg) {
        IF_instr_reg       := UInt(0)
        IR_rvcPrevHalf_reg := UInt(0)
      }
    }
  }

  val icacheReady = Wire(Bool())
  if(NUM_PUS == 1) {
    icacheReady := io.icacheReady(0)
  }
  else {
    icacheReady := Mux(io.pcMemReq(IC_HLF_BIT), io.icacheReady(1), io.icacheReady(0))
  }


  // Set the default value for the instruction memory request flag.
  IF_instrMemReqNxt    := IF_instrMemReq_reg && !IF_useSameCL

  // Check if we are in speculation mode where:
  //   1) Extends memory request IF_instrMemReqNxt.
  //   2) Wait for start fetch pulse (io.instrMemStFetch
  when(IF_isSpecu_reg) {
    // Assert IF_instrMemReqNxt when TLB is ready.
    when(!IF_instrMemReq_reg && !IF_useSameCL) {
      // We do not check for icacheReady if we are waiting only for the TLB.
      IF_instrMemReqNxt  := (!IF_waitICache_reg || icacheReady || IF_waitTlb_reg) && (!IF_waitTlb_reg || io.tlbReady)

      // Make sure that all wait flags are cleared when we are able to issue a new instruction request.
      when(IF_instrMemReqNxt) {
        IF_waitTlb_reg    := Bool(false)
        IF_waitICache_reg := Bool(false)
      }

      // Before sending imem request, we need to make sure that
      // there is no pending store instruction.
      when(IF_isPrevSfence_reg) {
        IF_instrMemReqNxt := Bool(false)
        when(ldStCntrLate_reg === UInt(0)) {
          IF_isPrevSfence_reg := Bool(false)
        }
      }
    }

    // The read request is de-asserted when instrMemStFetch is asserted indicating the start of
    // fetching the needed instruction.
    when((io.instrMemStFetch || (Bool(ENA_IC_FORWARD) && io.instrMemFwFetch && isAlterNxtCycle_reg)) && !IF_useSameCL) {
      IF_instrMemReqNxt       := Bool(false)
      IF_isSpecu_reg          := Bool(false)

      // De-assert the sfence flag as it will be executed from the first time.
      IF_sfenceValidReq_reg   := Bool(false)
      IF_isPrevSfence_reg     := IF_sfenceValidReq_reg
    }
  }

  // If we reached the end of the cache line, we request a new one.
  when(IR_instrIssuedP_reg && (IF_nxtInstrAddr_reg(log2Up(CACHE_LINE_LEN/8) - 1, log2Up(INSTR_LEN/8)) === UInt((CACHE_LINE_LEN / INSTR_LEN) - 1))) {
    IF_instrMemReqNxt       := Bool(true)
    IF_sfenceValidReq_reg   := Bool(false)
    IF_isSpecu_reg          := Bool(true)
  }

  // Register the incoming address and instruction to be issued to the PipeUnit.
  // Also, de-assert waiting acknowledge flag.
  when(IF_useSameCL) {
    // Use the same cache-line we already have.
    when(!IF_newInstrAvail_reg || IR_instrIssuedP_reg) {
      IF_ae_reg    := IF_aeE_reg
      IF_pf_reg    := IF_pfE_reg
      IF_instr_reg := IF_nxtInstr
    }
  }
  .elsewhen(fetchValidP_reg) {
    // Register the cache line.
    row_cLine_reg := io.instrMemResp

    when(!IF_newInstrAvail_reg || IR_instrIssuedP_reg) {
      IF_ae_reg  := io.ae
      IF_pf_reg  := io.pf

      // The word has been selected for the CtxtRow depending on IF_nxtInstrAddr_reg(CACHE_LINE_LOG2 - 1, log2Up(INSTR_LEN / 8).
      IF_instr_reg := io.instrMemRespWord
      when(io.ae || io.pf) {
        IF_instr_reg       := UInt(0)
        IR_rvcPrevHalf_reg := UInt(0)
      }
    }
    IF_aeE_reg := io.ae
    IF_pfE_reg := io.pf
  }

  // Make sure to update IF_newInstrAddr_reg early as possible.
  when(!IF_newInstrAvail_reg || IR_instrIssuedP_reg) {
    IF_newInstrAddr_reg   := IF_nxtInstrAddr_reg
  }

  when(fetchValidP_reg || IF_useSameCL) {
    // De-assert just branch flag.
    justBranch_reg := Bool(false)

    // Move to the next state.
    IF_isSpecu_reg  := Bool(false)

    when(!IF_newInstrAvail_reg || IR_instrIssuedP_reg) {
      IF_newInstrAvail_reg  := Bool(true)
      IF_newAddrReady_reg   := Bool(true)
      IF_nxtInstrAddr       := IF_instrAddrInc
      assert(IF_instrAddrInc === IF_nxtInstrAddr_reg + UInt(4))
      IF_isCached_reg       := IF_isCached

      // If we reached the end of the cache line, we request a new one.
      when(IF_nxtInstrAddr_reg(log2Up(CACHE_LINE_LEN/8) - 1, log2Up(INSTR_LEN/8)) === UInt((CACHE_LINE_LEN / INSTR_LEN) - 1)) {
        IF_instrMemReqNxt       := Bool(true)
        IF_sfenceValidReq_reg   := Bool(false)
        IF_isSpecu_reg          := Bool(true)
      }
    }
    .otherwise {
      IF_isCached_reg       := Bool(true)
    }
  }

  // If we did not receive memory valid after the specified number of cycles, initiate another memory request.
  when(fetchCntrEnd && !io.instrMemValidEarly) {
    IF_isSpecu_reg     := Bool(true)

    // If the fail is due to ICache not ready, wait until it is ready again.
    // We add one random cycle delay to avoid any system dead-lock.
    IF_instrMemReqNxt  := !(io.instrMemFailCauseEarly || io.instrMemFailTlbMiss)
    IF_waitTlb_reg     := io.instrMemFailTlbMiss
    IF_waitICache_reg  := io.instrMemFailCauseEarly
  }

  if (DEBUG == 2) {
    // Check for any halt.
    val dbgCntr_reg       = Reg(init=UInt(0, 20))

    when(fetchValidP_reg || IF_useSameCL) {
      when(!(io.ae || io.pf)) {
        dbgCntr_reg := UInt(0)
      }
      .otherwise {
        dbgCntr_reg := dbgCntr_reg + UInt(1)
        when(dbgCntr_reg >= UInt(5)) {
          assert(Bool(false), "Receive instruction exception in successive times.")
        }
      }
    }
  }

  if (DEBUG == 2) {
    // Check for any halt.
    val dbgCntr_reg       = Reg(init=UInt(0, 20))
    dbgCntr_reg          := dbgCntr_reg + UInt(1)
    when(!IF_isSpecu_reg || IF_newInstrAvail_reg) {
      dbgCntr_reg := UInt(0)
    }
    when(dbgCntr_reg >= UInt(4096 * 16)) {
      //assert(Bool(false), "IF state waits for long time.")
    }
  }

  val nxtPcAddr = Wire(UInt(width = ADDR_LEN))
  when(Reg(next=IF_newAddrReady_reg, init=Bool(false))) {
    nxtPcAddr := IR_addr_reg
  }
  .otherwise {
    val tmp = Mux(IF_newAddrReady_reg, IF_newInstrAddr_reg, IF_nxtInstrAddr_reg)

    // We keep IR_secondHlfSel_reg without change since the fence.vma is usually full 32-bit instruction.
    nxtPcAddr := Cat(tmp(ADDR_LEN-1, log2Up(INSTR_LEN / 8)), IR_secondHlfSel_reg, UInt(0, log2Up(INSTR_LEN / 16)))
  }

  // Track flow change instructions.
  val ctlFlowState  = Wire(Bool())
  val flowCntr_reg  = Reg(init=UInt(0, 3))
  ctlFlowState     := ctlFlowState_reg
  when(ctlFlowState_reg) {
    // Increment the counter.
    flowCntr_reg := flowCntr_reg + UInt(1)

    // In the case of branch speculation, wait longer to make sure that the case of miss-prediction has been handled.
    when(Mux(instrMemSpecAccess_reg, flowCntr_reg === UInt(4), io.endExecAckEarly || (flowCntr_reg === UInt(3)))) {
      ctlFlowState := Bool(false)
    }
  }
  .elsewhen((decValidIP_reg && (io.isWaitEndExec||io.csrUnitCtxtUnit.interruptSlow)) || (decValidMP_reg && (io.isWaitEndExecM||io.csrUnitCtxtUnit.interruptSlow))) {
    // Slow down the issued instructions when an interrupt is being issued.
    ctlFlowState     := Bool(true)
    flowCntr_reg     := UInt(0)
  }
  ctlFlowState_reg    := ctlFlowState

  // Note that this not correct if we support RoCC.
  // We do not need to check the second operator in some instructions (loads and these that use immediate value).
  val isNegOp2 = Mux(IF_isRVC, !IF_instr(15), IF_instr(6,5) === UInt(0))

  // We do not care about the change register ID field if the instruction is store or branch. Those instructions do not change the registerFile.
  val noRegCh  = isNextSt || Mux(IF_isRVC, (IF_instr(15,14) === UInt(3) && IF_instr(1,0) === UInt(1)), (IF_instr(6,0) === UInt(99)))

  // Track the update of the PipeUnit registers.
  val rfIpstatus_reg   = Reg(init=Vec.fill(NUM_ROW_REGS) {Bool(false)})
  when((decValidMP_reg && io.checkRegHazardM) || (decValidIP_reg && io.checkRegHazard && IR_isDivMulPrev_reg)) {
    rfIpstatus_reg(changedReg_reg)    := Bool(true)
    assert(!rfIpstatus_reg(changedReg_reg))
  }
  when(io.regfsetSafe3 && !io.regfsetIsFpu3 && io.regfsetisLd3) {
    rfIpstatus_reg(io.regfsetRegId3) := Bool(false)
    assert(rfIpstatus_reg(io.regfsetRegId3) || (io.regfsetRegId3 === UInt(0)))
  }
  when(io.regfsetSafe2 && !io.regfsetIsFpu2) {
    rfIpstatus_reg(io.regfsetRegId2) := Bool(false)
    assert(rfIpstatus_reg(io.regfsetRegId2) || (io.regfsetRegId2 === UInt(0)))
  }
  when(duWriteID0_reg) {
    rfIpstatus_reg(duRegIdD0_reg)          := Bool(false)
    assert(rfIpstatus_reg(duRegIdD0_reg) || (duRegIdD0_reg === UInt(0)))
  }
  when(io.regfsetSafe0) {
    rfIpstatus_reg(io.regfsetRegId0) := Bool(false)
    assert(rfIpstatus_reg(io.regfsetRegId0) || (io.regfsetRegId0 === UInt(0)))
  }
  rfIpstatus_reg(0) := Bool(false)

  val rfIpstatusU         = rfIpstatus_reg.asUInt
  val IR_wrAddrReady_reg  = Reg(next=rfIpstatusU(Mux(IR_instrReady_reg, IR_wrAddr_reg, IF_wrAddrC)), init=Bool(false))
  val IR_op1AddrReady_reg = Reg(next=rfIpstatusU(Mux(IR_instrReady_reg, IR_op1C_reg, IF_op1C)), init=Bool(false))
  val IR_op2AddrReady_reg = Reg(next=rfIpstatusU(Mux(IR_instrReady_reg, IR_op2_reg, IF_op2)), init=Bool(false))

  val IR_noHazardIp    = !((IR_wrAddrReady_reg && !IR_noRegCh_reg) || IR_op1AddrReady_reg || (IR_op2AddrReady_reg && !IR_isNegOp2_reg))

  // Delayed versions of the incoming releases commands.
  val fpuSafe0FD1_reg  = Reg(next=io.fpuSafe0, init=Bool(false))
  val fpuSafe0D1_reg   = Reg(next=io.fpuSafe0 && io.fpuIsReal0, init=Bool(false))
  val fpuRegId0D1_reg  = RegEnable(next=io.fpuRegId0, enable=io.fpuSafe0, init=UInt(0, REG_ID_LEN))
  val fpuSafe0D2_reg   = Reg(next=fpuSafe0D1_reg, init=Bool(false))
  val fpuRegId0D2_reg  = RegEnable(next=fpuRegId0D1_reg, enable=fpuSafe0D1_reg, init=UInt(0, REG_ID_LEN))
  val fpuSafe0D3_reg   = Reg(next=fpuSafe0D2_reg, init=Bool(false))
  val fpuRegId0D3_reg  = RegEnable(next=fpuRegId0D2_reg, enable=fpuSafe0D2_reg, init=UInt(0, REG_ID_LEN))
  val fpuSafe0D4_reg   = Reg(next=fpuSafe0D3_reg, init=Bool(false))
  val fpuRegId0D4_reg  = RegEnable(next=fpuRegId0D3_reg, enable=fpuSafe0D3_reg, init=UInt(0, REG_ID_LEN))

  val fpuSafe1D1_reg   = Reg(next=io.fpuSafe1 && io.fpuIsReal1, init=Bool(false))
  val fpuRegId1D1_reg  = RegEnable(next=io.fpuRegId1, enable=io.fpuSafe1, init=UInt(0, REG_ID_LEN))
  val fpuSafe1D2_reg   = Reg(next=fpuSafe1D1_reg, init=Bool(false))
  val fpuRegId1D2_reg  = RegEnable(next=fpuRegId1D1_reg, enable=fpuSafe1D1_reg, init=UInt(0, REG_ID_LEN))
  val fpuSafe1D3_reg   = Reg(next=fpuSafe1D2_reg, init=Bool(false))
  val fpuRegId1D3_reg  = RegEnable(next=fpuRegId1D2_reg, enable=fpuSafe1D2_reg, init=UInt(0, REG_ID_LEN))
  val fpuSafe1D4_reg   = Reg(next=fpuSafe1D3_reg, init=Bool(false))
  val fpuRegId1D4_reg  = RegEnable(next=fpuRegId1D3_reg, enable=fpuSafe1D3_reg, init=UInt(0, REG_ID_LEN))

  val fpuSafe2D1_reg   = Reg(next=io.fpuSafe2 && io.fpuIsReal2, init=Bool(false))
  val fpuRegId2D1_reg  = RegEnable(next=io.fpuRegId2, enable=io.fpuSafe2, init=UInt(0, REG_ID_LEN))
  val fpuSafe2D2_reg   = Reg(next=fpuSafe2D1_reg, init=Bool(false))
  val fpuRegId2D2_reg  = RegEnable(next=fpuRegId2D1_reg, enable=fpuSafe2D1_reg, init=UInt(0, REG_ID_LEN))
  val fpuSafe2D3_reg   = Reg(next=fpuSafe2D2_reg, init=Bool(false))
  val fpuRegId2D3_reg  = RegEnable(next=fpuRegId2D2_reg, enable=fpuSafe2D2_reg, init=UInt(0, REG_ID_LEN))
  val fpuSafe2D4_reg   = Reg(next=fpuSafe2D3_reg, init=Bool(false))
  val fpuRegId2D4_reg  = RegEnable(next=fpuRegId2D3_reg, enable=fpuSafe2D3_reg, init=UInt(0, REG_ID_LEN))

  val fpuSafe3FD1_reg  = Reg(next=io.fpuSafe3, init=Bool(false))
  val fpuSafe3D1_reg   = Reg(next=io.fpuSafe3 && io.fpuIsReal3, init=Bool(false))
  val fpuRegId3D1_reg  = RegEnable(next=io.fpuRegId3, enable=io.fpuSafe3, init=UInt(0, REG_ID_LEN))
  val fpuSafe3D2_reg   = Reg(next=fpuSafe3D1_reg, init=Bool(false))
  val fpuRegId3D2_reg  = RegEnable(next=fpuRegId3D1_reg, enable=fpuSafe3D1_reg, init=UInt(0, REG_ID_LEN))
  val fpuSafe3D3_reg   = Reg(next=fpuSafe3D2_reg, init=Bool(false))
  val fpuRegId3D3_reg  = RegEnable(next=fpuRegId3D2_reg, enable=fpuSafe3D2_reg, init=UInt(0, REG_ID_LEN))
  val fpuSafe3D4_reg   = Reg(next=fpuSafe3D3_reg, init=Bool(false))
  val fpuRegId3D4_reg  = RegEnable(next=fpuRegId3D3_reg, enable=fpuSafe3D3_reg, init=UInt(0, REG_ID_LEN))

  // Track the update of the FPU registers.
  val rfFpStatus_reg   = Reg(init=Vec.fill(NUM_ROW_REGS) {Bool(false)})
  val release1_reg     = Reg(next=(duWrite && duIsFpu), init=Bool(false))
  val release2_reg     = Reg(next=io.regfsetSafe2 && io.regfsetIsFpu2, init=Bool(false))
  val rfId2D1_reg      = Reg(next=io.regfsetRegId2, init=UInt(0, REG_ID_LEN))
  val release3_reg     = Reg(next=io.regfsetSafe3 && io.regfsetIsFpu3 && io.regfsetisLd3, init=Bool(false))
  val rfId3D1_reg      = Reg(next=io.regfsetRegId3, init=UInt(0, REG_ID_LEN))
  val release1D1_reg   = Reg(next=release1_reg, init=Bool(false))
  val IR_noHazardFpu   = Wire(init=Bool(true))
  val IR_noHazard      = Wire(Bool())
  val pendingFpu_reg   = Reg(init=Bool(false))
  val fpuCycleWait_reg = Reg(init=Bool(false))
  val IR_forceNoHazardFpu_reg = Reg(init=Bool(false))
  if(usingFPU) {
    // Flag to indicate any outstanding FPU register to be updated.
    pendingFpu_reg   := rfFpStatus_reg.orR || (io.rowSel && IR_isFpu_reg)

    // Make sure that the FPU commands that writes to the RF have one extra delay cycle, this is needed since we do not support the last stage by-pass logic for FPU output.
    fpuCycleWait_reg := IR_instrReady_reg && IR_isFpu_reg && IR_isFpuChIp_reg

    // Assert the change register for the FPU.
    when(!IR_forceNoHazardFpu_reg && IR_instrReady_reg && IR_isFpu_reg && IR_noHazardFpu) {
      rfFpStatus_reg(IR_wrAddr_reg)   := Bool(true)
      assert(!rfFpStatus_reg(IR_wrAddr_reg))
      IR_forceNoHazardFpu_reg         := Bool(true)
    }

    // Check if an FPU registers is being updated. Otherwise, de-assert the corresponding in rfFpStatus_reg.
    when((io.checkFpuHazard && decValidIP_reg) || (io.checkFpuHazardM && decValidMP_reg) || decValidFP_reg) {
      pendingFpu_reg          := Bool(true)
      IR_forceNoHazardFpu_reg := Bool(false)
    }
    .elsewhen(IR_forceNoHazardFpu_reg && !IR_instrReady_reg) {
      // No FPU register to be changed. De-assert rfFpStatus_reg.
      rfFpStatus_reg(changedReg_reg) := Bool(false)
      IR_forceNoHazardFpu_reg        := Bool(false)
    }

    when(fpuSafe0FD1_reg) {
      rfFpStatus_reg(fpuRegId0D1_reg) := Bool(false)
      assert(rfFpStatus_reg(fpuRegId0D1_reg))
    }
    when(io.fpuSafe1) {
      rfFpStatus_reg(io.fpuRegId1) := Bool(false)
      assert(rfFpStatus_reg(io.fpuRegId1))
    }
    when(io.fpuSafe2) {
      rfFpStatus_reg(io.fpuRegId2) := Bool(false)
      assert(rfFpStatus_reg(io.fpuRegId2))
    }
    when(fpuSafe3FD1_reg) {
      rfFpStatus_reg(fpuRegId3D1_reg) := Bool(false)
      assert(rfFpStatus_reg(fpuRegId3D1_reg))
    }

    // Monitor LD instructions.
    when(release1D1_reg) {
      rfFpStatus_reg(duRegIdD1_reg) := Bool(false)
      assert(rfFpStatus_reg(duRegIdD1_reg))
    }
    when(release2_reg) {
      rfFpStatus_reg(rfId2D1_reg) := Bool(false)
      assert(rfFpStatus_reg(rfId2D1_reg))
    }
    when(release3_reg) {
      rfFpStatus_reg(rfId3D1_reg) := Bool(false)
      assert(rfFpStatus_reg(rfId3D1_reg))
    }

    val rfFpStatusU         = rfFpStatus_reg.asUInt
    val IR_wrAddrReadyF_reg = Reg(next=rfFpStatusU(Mux(IR_instrReady_reg, IR_wrAddr_reg,          IF_wrAddrC)), init=Bool(false))
    val IR_addr1ReadyF_reg  = Reg(next=rfFpStatusU(Mux(IR_instrReady_reg, IR_rowToRfF_reg.addr1,  IF_op1C)), init=Bool(false))
    val IR_addr2ReadyF_reg  = Reg(next=rfFpStatusU(Mux(IR_instrReady_reg, IR_rowToRfF_reg.addr2,  IF_op2)), init=Bool(false))
    val IR_addr3ReadyF_reg  = Reg(next=rfFpStatusU(Mux(IR_instrReady_reg, IR_rowToRfF_reg.addr3,  IF_instr(31,27))), init=Bool(false))

    IR_noHazardFpu   := IR_forceNoHazardFpu_reg || !(IR_wrAddrReadyF_reg || ((IR_addr1ReadyF_reg || IR_addr2ReadyF_reg || (IR_addr3ReadyF_reg && IR_isFpuIndep_reg)) && !IR_isLd_reg))
  }

  // Register wfi_stall.
  val wfi_stall_reg = Reg(next=io.csrUnitCtxtUnit.wfi_stall, init=Bool(false))

  // Check for hazard.
  val noHazard7  = (!ldStWait_reg || !(isNextLd || isNextSt))
  noHazard      := noHazard7 && !releaseFenceP && !infSlowDown_reg && !flowAltered  && !io.csrUnitCtxtUnit.csrFlowAlt && !ctlFlowState && !waitFence_reg && !fenceIP_reg && io.csrUnitCtxtUnit.tlbSafeR

  // Check for fsflags hazard.
  // To relax timing, we do not check fflags part.
  val fsflagsHazard = !(IR_isFsflags_reg && Reg(next=pendingFpu_reg, init=Bool(false)))

  // Check for register hazard.
  if(usingFPU) {
    IR_noHazard := (IR_noHazardFpu || !IR_isFpu_reg) && ((IR_noHazardIp && fsflagsHazard) || IR_isFpuIndep_reg)
  }
  else {
    IR_noHazard := IR_noHazardIp
  }

  val rowStatus0 = IF_newInstrAvail_reg
  val rowStatus1 = IR_noHazardIp
  val rowStatus2 = noHazard7
  val rowStatus3 = noHazard
  val rowStatus4 = Bool(false)
  val rowStatus5 = Bool(false)
  val rowStatus6 = !waitFence_reg && !fenceIP_reg && !wfi_stall_reg
  val rowStatus7 = flowAltered
  io.rowStatus  := Cat(rowStatus7, Cat(rowStatus6, Cat(rowStatus5, Cat(rowStatus4, Cat(rowStatus3, Cat(rowStatus2, Cat(rowStatus1, rowStatus0)))))))

  val noPrev     = (changedReg_reg === IF_wrAddr) || (changedReg_reg === IF_op1) || (changedReg_reg === IF_op2)
  val noPrevDep  = ((decValidIP_reg && !IR_isDivMulPrev_reg) || decValidFP_reg || (decValidMP_reg && !noPrev)) && !fpuCycleWait_reg

  when(IR_instrReady_reg) {
    IR_isDivMulPrev_reg := IR_isDiv_reg || IR_isMulL_reg || IR_isMulH_reg
  }

  if(!USE_TWO_CYCLES_RF) {
    // TODO: Control correctly.
    IR_rowToRfF_reg.rdEna1  := Bool(true)
    IR_rowToRfF_reg.rdEna2  := Bool(true)
    IR_rowToRfF_reg.rdEna3  := Bool(true)
  }

  // Delay changed REG.
  regChangD1_reg      := decValidIP_reg && io.checkRegHazard && !flowAlteredD1_reg && !flowAlteredD2_reg
  regChangD2_reg      := regChangD1_reg && !flowAlteredD1_reg && !flowAlteredD2_reg && !flowAlteredD3_reg // The flowAlter will mute these instructions.
  regChangD3_reg      := regChangD2_reg
  regChangD4_reg      := regChangD3_reg
  regChangD5_reg      := regChangD4_reg
  regChangD6_reg      := regChangD5_reg
  changedRegD1_reg    := RegEnable(next=changedReg_reg,   enable=decValidIP_reg, init=UInt(0, REG_ID_LEN))
  changedRegD2_reg    := RegEnable(next=changedRegD1_reg, enable=regChangD1_reg, init=UInt(0, REG_ID_LEN))
  changedRegD3_reg    := RegEnable(next=changedRegD2_reg, enable=regChangD2_reg, init=UInt(0, REG_ID_LEN))
  changedRegD4_reg    := RegEnable(next=changedRegD3_reg, enable=regChangD3_reg, init=UInt(0, REG_ID_LEN))
  changedRegD5_reg    := RegEnable(next=changedRegD4_reg, enable=regChangD4_reg, init=UInt(0, REG_ID_LEN))
  changedRegD6_reg    := RegEnable(next=changedRegD5_reg, enable=regChangD4_reg, init=UInt(0, REG_ID_LEN))

  duWriteID0_reg      := duWrite && !duIsFpu
  duWriteFD0_reg      := duWrite && duIsFpu
  duRegIdD0_reg       := duRegId
  duWriteID1_reg      := duWriteID0_reg
  duWriteFD1_reg      := duWriteFD0_reg
  duRegIdD1_reg       := duRegIdD0_reg
  val duWriteID2_reg   = Reg(next=duWriteID1_reg, init=Bool(false))
  val duWriteFD2_reg   = Reg(next=duWriteFD1_reg, init=Bool(false))
  val duRegIdD2_reg    = Reg(next=duRegIdD1_reg, init=Bool(false))
  val duWriteID3_reg   = Reg(next=(duWriteID2_reg || (io.regfsetSafe2 && !io.regfsetIsFpu2)), init=Bool(false)) // Merge the two notifications come from the un-cached and cached paths.
  val duWriteFD3_reg   = Reg(next=(duWriteFD2_reg || (io.regfsetSafe2 && io.regfsetIsFpu2)), init=Bool(false))  // Merge the two notifications come from the un-cached and cached paths.
  val duRegIdID3_reg   = Reg(next=Mux(duWriteID2_reg, duRegIdD2_reg, io.regfsetRegId2), init=Bool(false))
  val duRegIdFD3_reg   = Reg(next=Mux(duWriteFD2_reg, duRegIdD2_reg, io.regfsetRegId2), init=Bool(false))
  val duWriteID4_reg   = Reg(next=duWriteID3_reg, init=Bool(false))
  val duWriteFD4_reg   = Reg(next=duWriteFD3_reg, init=Bool(false))
  val duRegIdID4_reg   = Reg(next=duRegIdID3_reg, init=Bool(false))
  val duRegIdFD4_reg   = Reg(next=duRegIdFD3_reg, init=Bool(false))
  val duWriteID5_reg   = Reg(next=duWriteID4_reg, init=Bool(false))
  val duWriteFD5_reg   = Reg(next=duWriteFD4_reg, init=Bool(false))
  val duRegIdID5_reg   = Reg(next=duRegIdID4_reg, init=Bool(false))
  val duRegIdFD5_reg   = Reg(next=duRegIdFD4_reg, init=Bool(false))

  regfsetSafe0D1_reg      := io.regfsetSafe0 && io.regfsetIsReal0
  regfsetRegId0D1_reg     := RegEnable(next=io.regfsetRegId0, enable=io.regfsetSafe0, init=UInt(0, REG_ID_LEN))
  val regfsetSafe0D2_reg   = Reg(next=regfsetSafe0D1_reg, init=Bool(false))
  val regfsetRegId0D2_reg  = RegEnable(next=regfsetRegId0D1_reg, enable=regfsetSafe0D1_reg, init=UInt(0, REG_ID_LEN))

  // A pulse is created when the register is available to read in the next cycle.
  def rdEnaChkIP(addr: UInt) = {
    val tstR5 = regChangD6_reg && (addr === changedRegD6_reg)
    val tstE5 = duWriteID5_reg && (addr === duRegIdID5_reg)
    val tstI2 = regfsetSafe0D2_reg && (addr === regfsetRegId0D2_reg)

    (tstR5 || tstE5 || tstI2) && (addr =/= UInt(0))
  }

  def rdEnaChkF(addr: UInt) = {
    val tstA0 = Bool(true)//!(fpuSafe0D1_reg && (addr === fpuRegId0D1_reg))
    val tstA1 = !(fpuSafe0D2_reg && (addr === fpuRegId0D2_reg))
    val tstA2 = !(fpuSafe0D3_reg && (addr === fpuRegId0D3_reg))
    val tstA3 = !(fpuSafe0D4_reg && (addr === fpuRegId0D4_reg))

    val tstD0 = Bool(true)//!(fpuSafe3D1_reg && (addr === fpuRegId3D1_reg))
    val tstD1 = !(fpuSafe3D2_reg && (addr === fpuRegId3D2_reg))
    val tstD2 = !(fpuSafe3D3_reg && (addr === fpuRegId3D3_reg))
    val tstD3 = !(fpuSafe3D4_reg && (addr === fpuRegId3D4_reg))

    // The notification coming from the divSqrt is received very late.
    val tstB0 = Bool(true)//!(fpuSafe1D1_reg && (addr === fpuRegId1D1_reg))
    val tstB1 = Bool(true)//!(fpuSafe1D2_reg && (addr === fpuRegId1D2_reg))
    val tstB2 = Bool(true)//!(fpuSafe1D3_reg && (addr === fpuRegId1D3_reg))
    val tstB3 = Bool(true)//!(fpuSafe1D4_reg && (addr === fpuRegId1D4_reg))

    val tstC0 = !(fpuSafe2D1_reg && (addr === fpuRegId2D1_reg))
    val tstC1 = Bool(true)//!(fpuSafe2D2_reg && (addr === fpuRegId2D2_reg))
    val tstC2 = Bool(true)//!(fpuSafe2D3_reg && (addr === fpuRegId2D3_reg))
    val tstC3 = Bool(true)//!(fpuSafe2D4_reg && (addr === fpuRegId2D4_reg))

    val tstE1 = Bool(true)//!(duWriteFD1_reg && (addr === duRegIdD1_reg))
    val tstE2 = !(duWriteFD2_reg && (addr === duRegIdD2_reg))
    val tstE3 = !(duWriteFD3_reg && (addr === duRegIdFD3_reg))
    val tstE4 = !(duWriteFD4_reg && (addr === duRegIdFD4_reg))

    tstA0 && tstA1 && tstA2 && tstA3 && tstB0 && tstB1 && tstB2 && tstB3 && tstC0 && tstC1 && tstC2 && tstC3 && tstD0 && tstD1 && tstD2 && tstD3 && tstE1 && tstE2 && tstE3 && tstE4
  }

  val rdEna1F  = Wire(init=Bool(false))
  val rdEna2F  = Wire(init=Bool(false))
  val rdEna3F  = Wire(init=Bool(false))
  val rdEna1CF = Wire(init=Bool(false))
  val rdEna2CF = Wire(init=Bool(false))
  val rdEna3CF = Wire(init=Bool(false))
  if(!USE_TWO_CYCLES_RF) {
    // TODO: Read only the needed fields, i.e., not all the three fields.
    rdEna1F  := rdEnaChkF(IF_instr(19, 15)) && !(isNextLd || isNextSt) && isNextFpu // No compressed instruction uses this register
    rdEna2F  := rdEnaChkF(IF_op2) && !isNextLd && isNextFpu
    rdEna3F  := rdEnaChkF(IF_instr(31,27)) && isFpuIndep && isNextFpu // No compressed instruction uses this register
    rdEna1CF := rdEnaChkF(IR_instr_reg(19, 15)) && !IR_isLdSt_reg && IR_isFpu_reg
    rdEna2CF := rdEnaChkF(IR_op2_reg) && !IR_isLd_reg && IR_isFpu_reg
    rdEna3CF := rdEnaChkF(IR_instr_reg(31,27)) && IR_isFpuIndep_reg && IR_isFpu_reg
  }

  // Generate a pulse at the start when the instruction is available.
  // Mark the start when an instruction is available.
  newInstrAvailaSt       := IF_newInstrAvail && !IR_instrReady_reg
  newInstrAvailaStD1_reg := newInstrAvailaSt
  newInstrAvailaStP      := newInstrAvailaSt && !newInstrAvailaStD1_reg

  // Delay the start pulse.
  val newInstrAvailaStPD1_reg = Reg(next=newInstrAvailaStP, init=Bool(false))

  // Register the addresses at the start of instruction availability.
  when(newInstrAvailaStP)  {
    IF_op1D1_reg := IF_op1
    IF_op2D1_reg := IF_op2

    IF_isFpuIndep_reg := isFpuIndep
    IF_isNextFpu_reg  := isNextFpu
  }

  val rowToRfI        = Wire(new RowToRfIBundle())
  rowToRfI.addr1     := Mux(IR_instrReady_reg, IR_op1C_reg, IF_op1C)
  rowToRfI.addr2     := Mux(IR_instrReady_reg, IR_op2_reg, IF_op2)

  if(!USE_TWO_CYCLES_RF) {
    // Mark the start for FPU and IPipe/MPipe instruction cycle.
    val newInstrAvailaStIP = newInstrAvailaStP && !isFpuIndep

    // Initiate a read when the register is just updated.
    // A delayed version of the operands is used.
    val IF_op1D1rdEnaChkIP = rdEnaChkIP(IF_op1D1_reg) && !IF_isFpuIndep_reg
    val IF_op2D1rdEnaChkIP = rdEnaChkIP(IF_op2D1_reg) && !IF_isFpuIndep_reg

    // Consider the case we need to initiate another read in the next cycle from newInstrAvailaStP.
    val rdEna1IG = (newInstrAvailaStIP || rdEna1I_reg || (IF_op1D1rdEnaChkIP&&newInstrAvailaStPD1_reg)) && rdEnaID1_reg && !io.rowSel
    val rdEna2IG = (newInstrAvailaStIP || rdEna2I_reg || (IF_op2D1rdEnaChkIP&&newInstrAvailaStPD1_reg)) && rdEnaID1_reg && !io.rowSel

    // Initiate a new read once the register has been written.
    rdEna1I_reg         := (Mux(IR_instrReady_reg, rdEnaChkIP(IR_op1_reg) && !IR_isFpuIndep_reg, IF_op1D1rdEnaChkIP && !newInstrAvailaStP) || rdEna1IG) && ((IF_newInstrAvail && !IR_instrReady_reg) || IR_instrReady_reg)
    rdEna2I_reg         := (Mux(IR_instrReady_reg, rdEnaChkIP(IR_op2_reg) && !IR_isFpuIndep_reg, IF_op2D1rdEnaChkIP && !newInstrAvailaStP) || rdEna2IG) && ((IF_newInstrAvail && !IR_instrReady_reg) || IR_instrReady_reg)

    rowToRfI.rdEna1    := !((newInstrAvailaStIP || rdEna1I_reg) && (!IR_instrReady_reg || IR_instrReady_reg) && !rdEnaID1_reg)
    rowToRfI.rdEna2    := !((newInstrAvailaStIP || rdEna2I_reg) && (!IR_instrReady_reg || IR_instrReady_reg) && !rdEnaID1_reg)
  }

  // Connect buses to the RF.
  io.rowToRfI    := rowToRfI
  io.rowToRfF    := IR_rowToRfF_reg

  // Keep track when a read has been initiated to make sure the next cycle is free.
  rdEnaID1_reg := !(io.rowToRfI.rdEna1 && io.rowToRfI.rdEna2)
  rdEnaFD1_reg := !(io.rowToRfF.rdEna1 && io.rowToRfF.rdEna2 && io.rowToRfF.rdEna3)

  // Notes about the instruction ready SM.
  // S_IR_DEC_DONE: Make sure that there is no hazard and a new instruction is available before asserting IR_instrReady_reg.
  // S_IR_READY_TO_ISSUE: Wait until the provided instruction is selected when io.rowSel is asserted.
  IR_instrIssuedP_reg := Bool(false)
  when(!IR_instrReady_reg) {
    when(!IR_rvcAddrNoUpd_reg) {
      IR_addr_reg      := Cat(IF_newInstrAddr_reg(ADDR_LEN-1, 2), Mux(IR_secondHlfSel_reg, UInt(2,2), UInt(0,2)))
    }

    when(IF_newInstrAvail_reg) {
      // Register the PC and instruction.
      when(IF_isRVC){
        // Just rearrange the instruction such that the operands will be at the same location as the non-compressed.
        // This is compensated at RVC.scala.
        IR_instr_reg := Cat(dec_overlp, IF_instr(15,10), IF_op2, IF_op1C, IF_instr(9, 7), IF_wrAddrC, IF_instr(6, 0))
      }
      .otherwise{
        IR_instr_reg := IF_instr
      }

      IR_ae_reg    := IF_ae_reg
      IR_pf_reg    := IF_pf_reg

      // For RVC instructions or instruction cross boundaries, make sure that we have
      // a new 32 bit instruction with the previous half.
      when(IR_secondHlfSel_reg && !IF_rvcFullInstValid_reg && !IR_instrIssuedP_reg) {
        IR_rvcPrevHalf_reg    := IF_instr_reg(31, 16)
        IR_rvcAddrNoUpd_reg   := Bool(true)
        IR_rvcPc_reg          := IF_newInstrAddr_reg

        // Ask for the next instruction from IR.
        IR_instrIssuedP_reg   := Bool(true)

        // If we detected a fault in the first half, we need to resolve it first.
        when(IF_ae_reg || IF_pf_reg) {
          IR_instrIssuedP_reg     := Bool(false)
          IF_rvcFullInstValid_reg := Bool(true)
          IR_rvcFrstHlfPf_reg     := IF_pf_reg
          IR_rvcFrstHlfAe_reg     := IF_ae_reg
        }
      }
    }

    when(IR_instrIssuedP_reg) {
      IF_rvcFullInstValid_reg := Bool(true)
    }

    // Reset IF_rvcFullInstValid_reg if we have just branched.
    when(justBranch_reg) {
      IF_rvcFullInstValid_reg := Bool(false)
    }

    // Update the PC to the next to be issue.
    when(IF_newInstrAvail && (IR_secondHlfSel_reg =/= IF_isRVC)) {
      IR_rvcPc_reg := IF_newInstrAddr_reg
    }

    // Set the address to the RF.
    // This condition should be anded by IF_newInstrAvail, but ignored for simplification.
    when(isNextFpu) {
      IR_rowToRfF_reg.addr1  := IF_op1C
      IR_rowToRfF_reg.addr2  := IF_op2
      IR_rowToRfF_reg.addr3  := IF_instr(31,27)
    }

    when(IF_newInstrAvail) {
      // Register the operands.
      IR_op2_reg     := IF_op2
      IR_op1_reg     := IF_op1
      IR_op1C_reg    := IF_op1C
      IR_wrAddr_reg  := IF_wrAddrC

      // Next instruction parameters.
      IR_isLdSt_reg          := isNextLd || isNextSt
      IR_isLd_reg            := isNextLd
      IR_isNegOp2_reg        := isNegOp2
      IR_noRegCh_reg         := noRegCh
      IR_isDiv_reg           := isNextDiv
      IR_isMulL_reg          := isNextMulL
      IR_isMulH_reg          := isNextMulH
      IR_isFpu_reg           := isNextFpu
      IR_isFpuChIp_reg       := isNextFpuChIp
      IR_isRvc_reg           := IF_isRVC
      IR_isChkFpuRdy_reg     := isNextChkFpuRdy
      IR_isSfence_reg        := isSfence
      IR_isFsflags_reg       := (IF_instr(6, 0) === UInt(115)) && (IF_instr(31, 20) === UInt(fflags))

      // For unsupported FPU functionality, the corresponding instruction is issued via PipeUnit as it shall cause an exception.
      IR_isFpuIndep_reg      := isFpuIndep && !io.csrUnitCtxtUnit.fpActive(0) && !(io.csrUnitCtxtUnit.fpActive(1) && ((IF_instr(14,12).isOneOf(5, 6)) || (IF_instr(14,12) === 7)))

      if(!USE_TWO_CYCLES_RF) {
        // Control the read from the RF.
        IR_rowToRfF_reg.rdEna1 := !rdEna1F
        IR_rowToRfF_reg.rdEna2 := !rdEna2F
        IR_rowToRfF_reg.rdEna3 := !rdEna3F
        IR_rdCmdPendingF1_reg  := !rdEna1F
        IR_rdCmdPendingF2_reg  := !rdEna2F
        IR_rdCmdPendingF3_reg  := !rdEna3F
      }
    }

    // To issue a new instruction the following checks are needed to be satisfied:
    //   1- The PipeUnit shall be ready to accept new instruction from this row.
    //   2- A new instruction is available (IF_newInstrAvail_reg).
    when(noHazard && IF_newInstrAvail && (!IF_waitForDecVal_reg || noPrevDep)) {
      // Assert flag to the selector to indicate a new instruction is ready to be issue.
      IR_instrReady_reg    := Bool(true)

      // Start request a new instruction from instrUnit.
      when(!IF_isRVC || !IR_secondHlfSel_reg) {
        IR_instrIssuedP_reg  := Bool(true)
      }
      when(IF_isRVC) {
        IR_secondHlfSel_reg := !IR_secondHlfSel_reg
        when(!IR_secondHlfSel_reg) {
          IR_rvcAddrNoUpd_reg := Bool(true)
        }
      }
    }
  }

  when(decValid_reg) {
    IF_waitForDecVal_reg := Bool(false)
  }

  // De-assert IR_instrReady_reg when an acknowledge is received for
  // issuing the new instruction to the PipeUnit.
  when(io.rowSel) {
    IR_instrReady_reg       := Bool(false)
    IF_waitForDecVal_reg    := Bool(true)
    IR_rvcFrstHlfPf_reg     := Bool(false)
    IR_rvcFrstHlfAe_reg     := Bool(false)
    when(IR_rvcAddrNoUpd_reg) {
      IR_addr_reg  := IR_rvcPc_reg | Mux(IR_secondHlfSel_reg, UInt(2,3), UInt(0,3))
      when(!IR_secondHlfSel_reg) {
        IR_rvcAddrNoUpd_reg := Bool(false)
      }
    }
  }

  // When a TLB exception is received, we drop the current request, and we need to issue a new instruction.
  when(io.csrUnitCtxtUnit.csrFlowAlt || flowAlteredI) {
    IR_instrReady_reg   := Bool(false)
  }

  if(!USE_TWO_CYCLES_RF) {
    // Send read command when the register is fully updated on the array.
    when(io.csrUnitCtxtUnit.csrFlowAlt || io.rowSel) {
      IR_rdCmdPendingF1_reg := Bool(false)
      IR_rdCmdPendingF2_reg := Bool(false)
      IR_rdCmdPendingF3_reg := Bool(false)
    }
    .elsewhen(IR_instrReady_reg) {
      IR_rowToRfF_reg.rdEna1  := !(rdEna1CF && IR_rdCmdPendingF1_reg)
      IR_rowToRfF_reg.rdEna2  := !(rdEna2CF && IR_rdCmdPendingF2_reg)
      IR_rowToRfF_reg.rdEna3  := !(rdEna3CF && IR_rdCmdPendingF3_reg)
      IR_rdCmdPendingF1_reg   := IR_rdCmdPendingF1_reg && !rdEna1CF
      IR_rdCmdPendingF2_reg   := IR_rdCmdPendingF2_reg && !rdEna2CF
      IR_rdCmdPendingF3_reg   := IR_rdCmdPendingF3_reg && !rdEna3CF
    }
  }

  // Check if there is not any detected successive instrUnit access to the same way.
  IF_noSuccSameWay_reg := (0 to NUM_PUS - 1 map { ips => !((io.wayIdSel(ips) === IF_nxtInstrAddr(CACHE_LINE_LOG2+log2Up(nWaysIC)-1,CACHE_LINE_LOG2)) && io.validWaySel(ips)) }).reduce(_&&_)

  // Connect the signals needed to access the instrUnit.
  io.pcMemReq        := Cat(RegEnable(next=IF_nxtInstrAddr(ADDR_LEN-1, log2Up(INSTR_LEN / 8)), enable=IF_instrMemReqNxt && !IF_sfenceValidReq_reg, init=UInt(0, ADDR_LEN-log2Up(INSTR_LEN / 8))), UInt(0, log2Up(INSTR_LEN / 8)))
  io.instrMemReq     := IF_instrMemReq_reg && IF_noSuccSameWay_reg && !(Bool(ENA_IC_FORWARD) && io.instrMemFwFetch && isAlterNxtCycle_reg && Bool(NUM_PUS == 1))
  io.instrMemKeep    := (!isPipeFlowAlt_reg || !io.csrUnitCtxtUnit.muteD2_reg)
  io.sfenceReq_valid := IF_sfenceValidReq_reg
  io.sfenceReq_rs1   := IF_sfenceRs1Req_reg
  io.sfenceReq_rs2   := IF_sfenceRs2Req_reg

  // Connect the signals to the PipeUnit.
  val IR_instrReady        = IR_instrReady_reg && IR_noHazard && !wfi_stall_reg
  val duWriteEarlyD1_reg   = Reg(next=(io.duWriteEarly && !io.duIsFpuEarly), init=Bool(false))
  val duWriteEarlyD2_reg   = Reg(next=duWriteEarlyD1_reg, init=Bool(false))
  val duWriteEarlyD3_reg   = Reg(next=duWriteEarlyD2_reg, init=Bool(false))
  val noDuTrans            = (!((Bool(USE_TWO_CYCLES_RF) && (duWriteEarlyD1_reg || duWriteEarlyD3_reg)) || duWriteEarlyD2_reg)) || IR_isDiv_reg || IR_isMulL_reg || IR_isMulH_reg || IR_noRegCh_reg

  //wire to RocketTile
  io.ReadyNotReady                := IR_instrReady  && !io.instrReady && !io.instrReadyF && !io.instrReadyM //count when true
  io.ReadyNotReadyCauseDU         := IR_instrReady  && !noDuTrans //count when true
  io.IR_instrReadyLWfiStall       := IR_instrReady_reg && wfi_stall_reg //count when true
  io.IR_instrReadyLOpnotReady     := IR_instrReady_reg && !IR_noHazardIp //count when true //ov1
  io.IR_instrReadyLOpnotReadyFPU  := IR_instrReady_reg && !((IR_noHazardFpu || !IR_isFpu_reg) && ((true.B && fsflagsHazard) || IR_isFpuIndep_reg)) //can overlap with IR_instrReadyLOpnotReady //count when true //ov1
  io.IR_instrReadyLflowaltered    := !IR_instrReady_reg && (io.csrUnitCtxtUnit.csrFlowAlt || flowAlteredI) //from this point onwards see what is stopping "ready" from going high
  io.IR_instrReadyLrowSel         := io.rowSel //from this point onwards see what is stopping ready from going high
  io.IR_instrReadyNoLInstrFetch   := !IR_instrReady_reg && !IF_newInstrAvail //count when true
  io.IR_instrReadyNoLnoPrevDep    := !IR_instrReady_reg && !(!IF_waitForDecVal_reg || noPrevDep) //count when true
  io.IR_instrReadyNoLnoHazard7    := !IR_instrReady_reg && !noHazard7 //count when true
  io.IR_instrReadyNoLfence        := !IR_instrReady_reg && !(!waitFence_reg && !fenceIP_reg && !releaseFenceP) //count when true
  io.IR_instrReadyNoLtlbSafeR     := !IR_instrReady_reg && !io.csrUnitCtxtUnit.tlbSafeR //count when true
  io.IR_instrReadyNoLinfSlow      := !IR_instrReady_reg && infSlowDown_reg //count when true
  io.IR_instrReadyNoLctlFlow      := !IR_instrReady_reg && ctlFlowState //count when true 

  isFpuPath           := IR_isFpuIndep_reg
  isMemPath           := IR_isLdSt_reg && (!io.csrUnitCtxtUnit.fpActive(0) || !IR_isFpu_reg) && (!IR_isSfence_reg || io.csrUnitCtxtUnit.fpActive(2))
  io.instrReady       := IR_instrReady && !isFpuPath && !isMemPath && !(io.disableDiv && IR_isDiv_reg) && !(io.disableMulL && IR_isMulL_reg) && !(io.disableMulH && IR_isMulH_reg) && noDuTrans
  io.instrReadyF      := IR_instrReady && isFpuPath && !(io.disableFpuInstr && IR_isChkFpuRdy_reg)
  io.instrReadyM      := IR_instrReady && isMemPath
  io.addr             := IR_addr_reg
  io.instr            := IR_instr_reg
  io.isNegOp2Early    := IR_isNegOp2_reg
  io.isDivEarly       := IR_isDiv_reg
  io.isMulLEarly      := IR_isMulL_reg
  io.isMulHEarly      := IR_isMulH_reg
  io.isChkFpuRdyEarly := IR_isChkFpuRdy_reg
  io.isRvcEarly       := IR_isRvc_reg
  io.aeOut            := IR_ae_reg
  io.pfOut            := IR_pf_reg
  io.aeSOut           := IR_ae_reg && !IR_rvcFrstHlfAe_reg  // Asserted if the second half causes fault.
  io.pfSOut           := IR_pf_reg && !IR_rvcFrstHlfPf_reg  // Asserted if the second half causes fault.

  // This can be one cycle after issuing instrReady.
  io.toBeSpeculatedD1 := cbPredictionS_reg

  // Control the branch prediction.
  when(io.rowSel && !io.instrReadyF && !io.instrReadyM) {
    IR_addrD1_reg   := io.addr
  }

  // Get the PC of the branch instructions.
  when(decValidIP_reg) {
    isBackBranch_reg     := io.isBackBranch
    IR_condBranchPc_reg  := IR_addrD1_reg
    isCondBranchD1_reg   := io.isCondBranch
  }
  isCondBranchD2_reg := isCondBranchD1_reg

  // Update the table entry when we find a backward branch.
  // Note: It is guaranteed that IR_condBranchPc_reg and isBackBranch_reg is stable for at least two cycles.
  cbJust_reg := Bool(false)
  when(flowAlteredI && isBackBranch_reg) {
    // Register the address of the conditional branch instruction.
    condBranchPC_reg       := IR_condBranchPc_reg

    // Register the jump address.
    condBranchJmp_reg      := io.alteredPc

    // The address of the next instruction that we use in the case of a miss prediction.
    condBranchNxt_reg      := nxtPcAddr

    // Validate the table.
    condBranchTblValid_reg := Bool(true)

    // Mark that the table has not been used yet.
    cbUsedBefore_reg       := Bool(false)

    // Mark the cycle when we update the CB.
    cbJust_reg             := Bool(true)
  }

  // If we can use the same cache-line in the branching, do not use prediction.
  when(IF_useSameCL && cbJust_reg) {
    condBranchTblValid_reg := Bool(false)
  }

  // Terminate the speculation.
  when((fetchValidP_reg || IF_useSameCL) && !cbPredictionS_reg && !cbPredictionP_reg && !Reg(next=cbPredictionP_reg, init=Bool(false))) {
    instrMemSpecAccess_reg := Bool(false)
  }

  // Check if the prediction failed.
  cbPredictionP_reg := Bool(false)
  when(cbPredictionSPD2_reg) {
    when(io.flowAltered && decValidD2_reg) {
      // Mark that this table has been usead least once successfully.
      cbUsedBefore_reg := Bool(true)
    }

    when(!io.flowAltered && decValidD2_reg) {
      // Initiate a jump to the normal flow to fix the wrong prediction.
      cbPredictionP_reg := Bool(true)

      // Mark that we access the instruction memory for speculation.
      instrMemSpecAccess_reg := Bool(true)

      // Reject the current table in the case that it has not been used before.
      when(!cbUsedBefore_reg) {
        condBranchTblValid_reg := Bool(false)
      }
    }

    // End speculation state.
    cbPredictionS_reg := Bool(false)
  }

  // Detect just issued instruction that is branch that we know where it is going to branch.
  when(condBranchTblValid_reg && IR_instrReady && (condBranchPC_reg === IR_addr_reg)) {
    // A pulse for the flow alter logic.
    cbPredictionP_reg := Bool(true)

    // A flag to indicate we are doing a speculation for the branching.
    cbPredictionS_reg := Bool(true)

    // Mark that we access the instruction memory for speculation.
    instrMemSpecAccess_reg := Bool(true)
  }

  // Flush the table in the case of CSR flow alter, i-fence and/or SFENCE.
  when(sfenceAlt || (io.csrUnitCtxtUnit.csrFlowAlt && !io.csrUnitCtxtUnit.isTLBMiss) || fenceIP_reg) {
    condBranchTblValid_reg := Bool(false)
  }
  cbPredictionSPD1_reg := cbPredictionS_reg && decValidIP_reg
  cbPredictionSPD2_reg := cbPredictionSPD1_reg

  // Abort speculation in the case of muted instruction.
  when(flowAlteredI || sfenceAlt || io.csrUnitCtxtUnit.csrFlowAlt || fenceIP_reg) {
    cbPredictionSPD1_reg := Bool(false)
    cbPredictionSPD2_reg := Bool(false)
    cbPredictionS_reg    := Bool(false)
    cbPredictionP_reg    := Bool(false)
  }

  // Update the PC.
  val IF_instrAddr     = Wire(UInt())
  IF_instrAddr        := io.csrUnitCtxtUnit.csrFlowPc
  when(io.csrUnitCtxtUnit.csrFlowAlt) {
    IF_instrAddr      := io.csrUnitCtxtUnit.csrFlowPc
  }
  .elsewhen(flowAltered) {
    IF_instrAddr      := Mux(decValidMD2_reg, io.alteredPcM, io.alteredPc)
  }
  .elsewhen(fenceIP_reg) {
    // The fence instruction is 4 bytes, so we keep the lower 2 bits.
    IF_instrAddr := IR_addr_reg
  }
  .elsewhen(cbPredictionP_reg) {
    // We are doing branching speculation when cbPredictionS_reg='1', otherwise we correct it.
    IF_instrAddr := Mux(cbPredictionS_reg, condBranchJmp_reg, condBranchNxt_reg)
  }

  // This logic is used to update PC.
  // When the flow is altered, an instrUnit request is started.
  isAlterNxtCycle_reg := Bool(false)
  IF_canBeSameCL_reg  := Bool(false)
  when(flowAltered || io.csrUnitCtxtUnit.csrFlowAlt || fenceIP_reg || cbPredictionP_reg) {
    // Assert the flag to indicate that we have received a flowAltered in the previous cycle.
    isAlterNxtCycle_reg := flowAlteredIF && !sfenceAlt && !io.csrUnitCtxtUnit.csrFlowAlt

    // On jump, the previous half becomes invalid.
    IF_rvcFullInstValid_reg := Bool(false)
    IR_rvcAddrNoUpd_reg     := Bool(false)

    // De-validate any received instruction.
    IF_newInstrAvail_reg := Bool(false)
    IF_newAddrReady_reg  := Bool(false)
    IF_isCached_reg      := Bool(false)

    // Initiate a read to the instrUnit.
    IR_secondHlfSel_reg     := IF_instrAddr(1)
    IF_nxtInstrAddr         := Cat(IF_instrAddr(ADDR_LEN-1, log2Up(INSTR_LEN / 8)), UInt(0, log2Up(INSTR_LEN / 8)))
    IF_instrMemReqNxt       := Bool(true)
    IF_sfenceValidReq_reg   := sfenceAlt
    IF_sfenceRs1Req_reg     := io.sfence_rs1 && io.flowAlteredM && decValidMD2_reg
    IF_sfenceRs2Req_reg     := io.sfence_rs2 && io.flowAlteredM && decValidMD2_reg
    IF_isSpecu_reg          := Bool(true)
    fetchValidP_reg         := Bool(false)

    // Flag to indicate a pipe flow alter.
    isPipeFlowAlt_reg       := !io.csrUnitCtxtUnit.csrFlowAlt && flowAltered

    // Make sure no change to the base address.
    IF_cacheBaseAddr_reg    := IF_cacheBaseAddr_reg

    // Mark that we have just applied a branch.
    justBranch_reg := Bool(true)

    // Check if we have already this address, this is done only for branching instructions or CSR alter that is caused by a TLB miss.
    IF_canBeSameCL_reg := !IF_aeE_reg && !IF_pfE_reg && (cbPredictionP_reg || isCondBranchD2_reg) && !((io.csrUnitCtxtUnit.csrFlowAlt && !io.csrUnitCtxtUnit.isTLBMiss) || fenceIP_reg || sfenceAlt)

    // Reset the counter to cancel any fetch in progress.
    fetchCntr_reg           := UInt(NUM_FETCH_CYCS + 1)
  }

  when(sfenceAlt) {
    // SFENCE is needed to be applied on io.alteredPc address (involving the corresponding TLB entry),
    // then jump to the next instruction.
    IR_secondHlfSel_reg := nxtPcAddr(1)
    IF_nxtInstrAddr_reg := Cat(nxtPcAddr(ADDR_LEN-1, log2Up(INSTR_LEN / 8)), UInt(0, log2Up(INSTR_LEN / 8)))
  }
  .otherwise{
    IF_nxtInstrAddr_reg := IF_nxtInstrAddr
  }

  // Register the request signal.
  IF_instrMemReq_reg := IF_instrMemReqNxt

  if (DEBUG == 2) {
    // Check for any halt.
    val dbgCntr_reg       = Reg(init=UInt(0, 30))
    dbgCntr_reg          := dbgCntr_reg + UInt(1)
    when((io.instrReady === Bool(true)) || (io.instrReadyF === Bool(true)) || wfi_stall_reg) {
      dbgCntr_reg := UInt(0)
    }
    when(dbgCntr_reg >= UInt(4096*16*DBG_WAIT_SCALE)) {
      printf("ROW: rowStatus = %x, %d\n", io.rowStatus, dbgCntr_reg)
      //assert(Bool(false), "Stall for long time.")
    }
  }
}

// ***********************************
// Priority selector
// ***********************************
// This module sweeps over the ready flag to select one of the rows to be activated.
// The sweep starts from the row that was selected in the previous cycle.
class SelPrioIo(NUM_CTXT : Int, TAG_LEN : Int) extends Bundle() {
  // vector of booleans, each boolean indicates the readiness of the corresponding row.
  val isReady     = Vec(NUM_CTXT, Bool()).asInput

  // Stop changing rowSel.
  val holdOn      = Bool(INPUT)

  // At most, only one bit is active in this vector, which indicates the selected row to be
  // activated.
  val selId       = UInt(OUTPUT, log2Up(NUM_CTXT - 1))
  val rowSel      = Vec(NUM_CTXT, Bool(OUTPUT))
  val rowSelP     = Vec(NUM_CTXT, Bool(OUTPUT))
  val validSelP   = Bool(OUTPUT)
  val validSel    = Bool(OUTPUT)

  val tagIn       = Vec(NUM_CTXT, UInt(width = TAG_LEN)).asInput
  val tagOut      = UInt(OUTPUT, TAG_LEN)
}

//This implements a scan chain, that goes through the rows of the context table to choose
// the next instruction to issue
// Ideally, it starts searching for a ready row starting from the previous selected row.
//
// A tree search is used to do this round robin search functionality.
class SelPrio(NUM_CTXT : Int, TAG_LEN : Int) extends Module {
  // Define I/O connections.
  val io          = new SelPrioIo(NUM_CTXT, TAG_LEN)
  val CTXT_ID_LEN = log2Up(NUM_CTXT)

  // Get the total number of leaves in the tree. It must be a power of 2.
  val CTXT_NUM_UP    = scala.math.pow(2,CTXT_ID_LEN).toInt

  // Register the last selected row to be used as a start point in the next search.
  val prevSel_reg    = Reg(init=Vec.fill(NUM_CTXT) {Bool(false)})
  val prevSelC_reg   = Reg(init=Vec.fill(NUM_CTXT) {Bool(false)})

  // A tree that is used to mark if a ready row was found in a certain node and its below hierarchy.
  val isNewSelTree   = Vec.fill(CTXT_ID_LEN + 1) {Vec.fill(CTXT_NUM_UP) {Wire(Bool())}}

  // A tree that is used to mark if row under this node was selected.
  val isOldSelTree   = Vec.fill(CTXT_ID_LEN + 1) {Vec.fill(CTXT_NUM_UP) {Wire(Bool())}}

  // A tree that is used to mark if a ready row was selected which is to the right of the previous selected row.
  val isFoundTree    = Vec.fill(CTXT_ID_LEN + 1) {Vec.fill(CTXT_NUM_UP) {Wire(Bool())}}

  // A tree that is used to identify a selected row under a node's hierarchy.
  val selIdTree      = Vec.fill(CTXT_ID_LEN + 1) {Vec.fill(CTXT_NUM_UP) {Wire(UInt(width = CTXT_ID_LEN))}}
  val tagTree        = Vec.fill(CTXT_ID_LEN + 1) {Vec.fill(CTXT_NUM_UP) {Wire(UInt(width = TAG_LEN))}}

  // Initialize tree leaves.
  for(leaf <- 0 until CTXT_NUM_UP) {
    if(leaf < NUM_CTXT) {
      // Check the readiness of the row. The previously selected row is disabled in selection.
      isNewSelTree(0)(leaf) := io.isReady(leaf) & !prevSel_reg(leaf)

      // Check if this leaf was selected previously.
      isOldSelTree(0)(leaf) := prevSelC_reg(leaf)

      // Select the current leaf.
      selIdTree(0)(leaf)    := UInt(leaf)
      tagTree(0)(leaf)      := io.tagIn(leaf)
    }
    else {
      isNewSelTree(0)(leaf) := Bool(false)
      isOldSelTree(0)(leaf) := Bool(false)
      selIdTree(0)(leaf)    := UInt(0)
      tagTree(0)(leaf)      := UInt(0)
    }

    // No rows have been found to the right of a previously selected row.
    isFoundTree(0)(leaf)  := Bool(false)
  }

  for(level <- 1 until CTXT_ID_LEN + 1) {
    for(leaf <- 0 until scala.math.pow(2,CTXT_ID_LEN-level).toInt) {
      // Mark if any leaf is ready under the current node.
      isNewSelTree(level)(leaf) := isNewSelTree(level-1)(leaf*2) | isNewSelTree(level-1)(leaf*2+1)

      // Mark if any leaf was previously selected under the current node.
      isOldSelTree(level)(leaf) := isOldSelTree(level-1)(leaf*2) | isOldSelTree(level-1)(leaf*2+1)

      // Determine if a ready row was found to the right of a previously selected row.
      isFoundTree(level)(leaf)  := isFoundTree(level-1)(leaf*2) | isFoundTree(level-1)(leaf*2+1) | (isNewSelTree(level-1)(leaf*2+1) & isOldSelTree(level-1)(leaf*2))

      // Select either the right or left branch. The right is selected only when:
      // - There is no ready rows to in the left rows.
      // - The right hand has a row that is ready and to the right of a previously selected row.
      val selRight               = (isNewSelTree(level-1)(leaf*2+1) & isOldSelTree(level-1)(leaf*2) & !isFoundTree(level-1)(leaf*2)) | !isNewSelTree(level-1)(leaf*2) | isFoundTree(level-1)(leaf*2+1)
      selIdTree(level)(leaf)    := Mux(selRight, selIdTree(level-1)(leaf*2+1), selIdTree(level-1)(leaf*2))
      tagTree(level)(leaf)      := Mux(selRight, tagTree(level-1)(leaf*2+1), tagTree(level-1)(leaf*2))
    }
  }

  for(row <- 0 until NUM_CTXT) {
    // Register the selection of the row to be used in the next cycle search.
    when(!io.holdOn) {
      prevSel_reg(row)  := (selIdTree(CTXT_ID_LEN)(0) === UInt(row)) & isNewSelTree(CTXT_ID_LEN)(0)

      // Update prevSelC_reg only when a valid ROW is selected.
      when(isNewSelTree(CTXT_ID_LEN)(0)) {
        prevSelC_reg(row) := (selIdTree(CTXT_ID_LEN)(0) === UInt(row))
      }
    }

    // Generates the output bit selection map.
    io.rowSel(row)   := (selIdTree(CTXT_ID_LEN)(0) === UInt(row)) & isNewSelTree(CTXT_ID_LEN)(0)
    io.rowSelP(row)  := (selIdTree(CTXT_ID_LEN)(0) === UInt(row)) & isNewSelTree(CTXT_ID_LEN)(0) & !io.holdOn
  }
  io.validSelP := isNewSelTree(CTXT_ID_LEN)(0) & !io.holdOn
  io.validSel  := isNewSelTree(CTXT_ID_LEN)(0)

  if(NUM_CTXT > 1) {
    io.selId     := selIdTree(CTXT_ID_LEN)(0)
    io.tagOut    := tagTree(CTXT_ID_LEN)(0)
  }
  else {
    io.selId     := UInt(0)
    io.tagOut    := tagTree(0)(0)
  }
}

//Q: consider case of JIT updating in one
// context while other contexts access the same instruction addresses, within same application -- how
// does JIT ensure safety among threads sharing the same code?
//A: JIT builds a new image in a new chunk of instr memory, then makes a single write to old instr
// memory, which changes the byte code in one place.  That change causes a native jump to the newly
// constructed JIT image.  So, the only possibilities are that the old byte code is seen, causing
// execution of the old image, or else the new byte code is seen, causing execution of the new JIT
// image..  no chance for partial, no way for cache behaviors to cause problems.

