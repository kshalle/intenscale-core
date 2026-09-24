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
// superThread Register File Set
//--------------------------------------------------------------------------
//

package superThread

import scala.math.pow
import Chisel._
import Chisel.ImplicitConversions._
import chisel3.core.withReset
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import scala.collection.immutable.ListMap
import scala.collection.mutable.ArrayBuffer

import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants.Util._

class RFMemory() extends Module {
  val io = new Bundle {
    val n_wr    = Bool(INPUT) // Active low write enable.
    val n_rd1   = Bool(INPUT) // Active low read enable.
    val n_rd2   = Bool(INPUT) // Active low read enable.
    val rdAddr1 = UInt(INPUT, log2Up(NUM_ROW_REGS))
    val rdAddr2 = UInt(INPUT, log2Up(NUM_ROW_REGS))
    val wrAddr  = UInt(INPUT, log2Up(NUM_ROW_REGS))
    val wrData  = UInt(INPUT, DATA_LEN)
    val rdData1 = UInt(OUTPUT, DATA_LEN)
    val rdData2 = UInt(OUTPUT, DATA_LEN)
  }

  if(USE_TWO_CYCLES_RF) {
    val rdData1_reg   = Reg(init=UInt(0, DATA_LEN))
    val rdData2_reg   = Reg(init=UInt(0, DATA_LEN))

    val BANK_BLOCK = Mem(NUM_ROW_REGS, Bits(width=DATA_LEN))
    when(Reg(next=(!io.n_wr), init=Bool(false))) {
      BANK_BLOCK.write(Reg(next=io.wrAddr), Reg(next=io.wrData))
    }

    when(!io.n_rd1) {
      rdData1_reg := BANK_BLOCK(io.rdAddr1)
    }
    when(!io.n_rd2) {
      rdData2_reg := BANK_BLOCK(io.rdAddr2)
    }

    // The following code asserts that we do not receive any two successive RD/WR operations.
    val n_wrD1_reg    = Reg(next=io.n_wr, init=Bool(true))
    val n_rd1D1_reg   = Reg(next=io.n_rd1, init=Bool(true))
    val rdAddr1D1_reg = Reg(next=io.rdAddr1, init=UInt(0))
    val n_rd2D1_reg   = Reg(next=io.n_rd2, init=Bool(true))
    val rdAddr2D1_reg = Reg(next=io.rdAddr2, init=UInt(0))
    val wrAddrD1_reg  = Reg(next=io.wrAddr, init=UInt(0))

    assert(!(!n_wrD1_reg && !io.n_wr), "Receive two successive write operations.")
    assert(!(!n_rd1D1_reg && !io.n_rd1), "Receive two successive read operations.")
    assert(!(!n_rd2D1_reg && !io.n_rd2), "Receive two successive read operations.")

    assert(!(!io.n_wr && !io.n_rd1 && (io.rdAddr1 === io.wrAddr)), "Access same address by RD and WR(1).")
    assert(!(!io.n_wr && !n_rd1D1_reg && (io.wrAddr === rdAddr1D1_reg)), "Access same address by RD and WR(2).")
    assert(!(!io.n_rd1 && !n_wrD1_reg && (io.rdAddr1 === wrAddrD1_reg)), "Access same address by RD and WR(3).")
    assert(!(!io.n_wr && !io.n_rd2 && (io.rdAddr2 === io.wrAddr)), "Access same address by RD and WR(1A).")
    assert(!(!io.n_wr && !n_rd2D1_reg && (io.wrAddr === rdAddr2D1_reg)), "Access same address by RD and WR(2A).")
    assert(!(!io.n_rd2 && !n_wrD1_reg && (io.rdAddr2 === wrAddrD1_reg)), "Access same address by RD and WR(3A).")

    // An extra delay is added to consider the timing of the internal memories.
    // IMPRTANT: We do not need it implement this MUX, this MUX is added for testing.
    io.rdData1    := Mux(!n_rd1D1_reg, UInt(0), rdData1_reg)
    io.rdData2    := Mux(!n_rd2D1_reg, UInt(0), rdData2_reg)
  }
  else {
    // Implement it as registers.
    val BANK_BLOCK = SeqMem(NUM_ROW_REGS, UInt(width = DATA_LEN))
    when(Reg(next=(!io.n_wr), init=Bool(false))) {
      BANK_BLOCK.write(Reg(next=io.wrAddr), Reg(next=io.wrData))
    }

    io.rdData1 := BANK_BLOCK(io.rdAddr1)
    io.rdData2 := BANK_BLOCK(io.rdAddr2)
  }
}

class RegfsetIo()(implicit p: Parameters) extends CoreBundle
    with HasCoreParameters
{
  val ctxtToRfI         = new CtxtToRfIBundle().flip()
  val earlyRfWrValid    = Bool(INPUT)
  val earlyRfWrCtxtId   = UInt(INPUT, CTXT_ID_LEN)
  val writeToRegfset    = Vec(4, Decoupled(new WriteToRegfsetBundle())).flip()
  val regfsetToIntpipe  = Vec(NUM_MPS+1, new RegfsetToIntpipeBundle())
  val regfsetToCtxt     = new RegfsetToCtxtBundle()
  val earlyDuWrFlag     = Bool(INPUT)
  val hartid            = UInt(INPUT, hartIdLen)
}

class Regfset()(implicit p: Parameters) extends CoreModule()(p) with HasCoreParameters
{
  val io           = new RegfsetIo()

  // Debug counters.
  val wrCntr0      = Reg(init=Vec.fill(NUM_CTXT){UInt(0, 32)})
  val wrCntr1      = Reg(init=Vec.fill(NUM_CTXT){UInt(0, 32)})

  // Instantiate the register file.
  val regfile1     = 0 to NUM_CTXT - 1 map {x => Module(new RFMemory).io}

  // Delay address one cycle.
  val earlyDuWrFlagD1_reg    = Reg(init=Bool(false))
  val earlyDuWrFlagD2_reg    = Reg(init=Bool(false))

  val bypassData1D1_reg      = Reg(init=Vec.fill(NUM_CTXT){UInt(0, DATA_LEN)})
  val bypassData2D1_reg      = Reg(init=Vec.fill(NUM_CTXT){UInt(0, DATA_LEN)})
  val addr1BypassD1_reg      = Reg(init=Vec.fill(NUM_CTXT){Bool(false)})
  val addr2BypassD1_reg      = Reg(init=Vec.fill(NUM_CTXT){Bool(false)})

  val bypassValid0_reg       = Reg(init=Bool(false))
  val destRegLast0_reg       = Reg(UInt())
  val dataToWriteLast0_reg   = Reg(UInt())
  val ctxtIdLast0_reg        = Reg(UInt())
  val bypassValid1_reg       = Reg(init=Bool(false))
  val destRegLast1_reg       = Reg(UInt())
  val dataToWriteLast1_reg   = Reg(UInt())
  val ctxtIdLast1_reg        = Reg(UInt())

  val bypassValid0D1_reg      = Reg(init=Bool(false))
  val destRegLast0D1_reg      = Reg(UInt())
  val dataToWriteLast0D1_reg  = Reg(UInt())
  val ctxtIdLast0D1_reg       = Reg(UInt())

  val bypassValid1D1_reg      = Reg(init=Bool(false))
  val destRegLast1D1_reg      = Reg(UInt())
  val dataToWriteLast1D1_reg  = Reg(UInt())
  val ctxtIdLast1D1_reg       = Reg(UInt())

  earlyDuWrFlagD1_reg      := io.earlyDuWrFlag
  earlyDuWrFlagD2_reg      := earlyDuWrFlagD1_reg
  val duWrFlagGuard         = (Bool(USE_TWO_CYCLES_RF) && (io.earlyDuWrFlag || earlyDuWrFlagD2_reg)) || earlyDuWrFlagD1_reg

  // Write to the register file.
  val wrArr       = Vec.fill(4) {Wire(Bool())}
  val wrArrReal   = Vec.fill(4) {Wire(Bool())}
  for(i <- 0 until 4) {
    wrArr(i)     := io.writeToRegfset(i).valid && Mux(Bool(i > 1), !duWrFlagGuard, Bool(true))
    wrArrReal(i) := wrArr(i) && io.writeToRegfset(i).bits.isReal && (io.writeToRegfset(i).bits.destReg =/= UInt(0))
  }

  // Signals needed to check previous clock cycle write.
  val wrCtxtIdD1_reg  = Reg(next=io.writeToRegfset(0).bits.ctxtId, init=UInt(0))
  val wrD1_reg        = Reg(next=wrArr(0), init=Bool(false))

  // Check for any conflict between the ctxt's memory needed to be accessed by any peripheral and the PipeUnit.
  // When the PipeUnit write something, we need the cycle before and after be dedicated for this PipeUnit  write.
  def checkUseSameCtxt(ctxtId: UInt) = (Bool(USE_TWO_CYCLES_RF) && (((io.earlyRfWrCtxtId === ctxtId) && io.earlyRfWrValid) || ((wrCtxtIdD1_reg === ctxtId) && wrD1_reg))) || ((io.writeToRegfset(0).bits.ctxtId === ctxtId) && wrArr(0))

  // Check the source of the data to write.
  val dataPipeU   = io.writeToRegfset(0).bits.dataToWrite

  // Bypass.
  bypassValid0_reg := Bool(false)
  when(io.writeToRegfset(0).valid) {
    destRegLast0_reg     := io.writeToRegfset(0).bits.destReg
    dataToWriteLast0_reg := dataPipeU
    ctxtIdLast0_reg      := io.writeToRegfset(0).bits.ctxtId
    bypassValid0_reg     := Bool(true)
  }

  bypassValid0D1_reg := bypassValid0_reg
  when(bypassValid0_reg) {
    destRegLast0D1_reg     := destRegLast0_reg
    dataToWriteLast0D1_reg := dataToWriteLast0_reg
    ctxtIdLast0D1_reg      := ctxtIdLast0_reg
  }

  // Connect ready signals.
  io.writeToRegfset(0).ready     := Bool(true)
  io.writeToRegfset(1).ready     := !((Bool(USE_TWO_CYCLES_RF) && bypassValid1_reg) && (io.writeToRegfset(1).bits.ctxtId === ctxtIdLast1_reg)) && !checkUseSameCtxt(io.writeToRegfset(1).bits.ctxtId)
  io.writeToRegfset(2).ready     := !(wrArr(1)) && !duWrFlagGuard && !((Bool(USE_TWO_CYCLES_RF) && bypassValid1_reg) && (io.writeToRegfset(2).bits.ctxtId === ctxtIdLast1_reg)) && !checkUseSameCtxt(io.writeToRegfset(2).bits.ctxtId)
  io.writeToRegfset(3).ready     := !(wrArr(1) || wrArr(2)) && !duWrFlagGuard && !((Bool(USE_TWO_CYCLES_RF) && bypassValid1_reg) && (io.writeToRegfset(3).bits.ctxtId === ctxtIdLast1_reg)) && !checkUseSameCtxt(io.writeToRegfset(3).bits.ctxtId)

  val dataToWrite = Mux(wrArr(1), io.writeToRegfset(1).bits.dataToWrite,
                      Mux(wrArr(2), io.writeToRegfset(2).bits.dataToWrite,
                        io.writeToRegfset(3).bits.dataToWrite))

  val wrCtxtId    = Mux(wrArr(1), io.writeToRegfset(1).bits.ctxtId,
                      Mux(wrArr(2), io.writeToRegfset(2).bits.ctxtId,
                        io.writeToRegfset(3).bits.ctxtId))
  val destReg     = Mux(wrArr(1), io.writeToRegfset(1).bits.destReg,
                      Mux(wrArr(2), io.writeToRegfset(2).bits.destReg,
                        io.writeToRegfset(3).bits.destReg))

  val wrPulse     = Mux(wrArr(1), wrArrReal(1) && io.writeToRegfset(1).ready,
                      Mux(wrArr(2), wrArrReal(2) && io.writeToRegfset(2).ready,
                        wrArrReal(3) && io.writeToRegfset(3).ready))

  bypassValid1_reg := Bool(false)
  when(wrPulse) {
    destRegLast1_reg     := destReg
    dataToWriteLast1_reg := dataToWrite
    ctxtIdLast1_reg      := wrCtxtId
    bypassValid1_reg     := Bool(true)
  }

  bypassValid1D1_reg := bypassValid1_reg
  when(bypassValid1_reg) {
    destRegLast1D1_reg     := destRegLast1_reg
    dataToWriteLast1D1_reg := dataToWriteLast1_reg
    ctxtIdLast1D1_reg      := ctxtIdLast1_reg
  }

  for (i <- 0 until NUM_CTXT) {
    val wrSpecificD1_reg  = Reg(init=Bool(false))
    val wrFromPipeU       = checkUseSameCtxt(UInt(i))
    val wrCurr            = Mux(wrFromPipeU, wrArrReal(0) && (io.writeToRegfset(0).bits.ctxtId === UInt(i)), (wrPulse && (wrCtxtId === UInt(i)))) && !(Bool(USE_TWO_CYCLES_RF) && wrSpecificD1_reg)

    // Disable any two successive writes.
    wrSpecificD1_reg     := wrCurr

    regfile1(i).n_rd1    := io.ctxtToRfI.rowToRfI(i).rdEna1
    regfile1(i).n_rd2    := io.ctxtToRfI.rowToRfI(i).rdEna2
    regfile1(i).rdAddr1  := io.ctxtToRfI.rowToRfI(i).addr1
    regfile1(i).rdAddr2  := io.ctxtToRfI.rowToRfI(i).addr2
    regfile1(i).n_wr     := Reg(next=(!wrCurr), init=Bool(true))
    regfile1(i).wrAddr   := RegEnable(Mux(wrFromPipeU, io.writeToRegfset(0).bits.destReg, destReg), wrCurr)
    regfile1(i).wrData   := RegEnable(Mux(wrFromPipeU, dataPipeU, dataToWrite), wrCurr)

    if(DEBUG == 2) {
      when(!regfile1(i).n_wr) {
        printf("RI%x_%x: %x %x %x\n", io.hartid, UInt(i), wrCntr0(UInt(i)), regfile1(i).wrAddr, regfile1(i).wrData)
        wrCntr0(UInt(i))  := wrCntr0(UInt(i)) + UInt(1)
      }
    }

    // Check if we can use bypass.
    val isBypassXB  = (UInt(i) === io.writeToRegfset(0).bits.ctxtId) && io.writeToRegfset(0).valid
    val isBypass1XB = isBypassXB && (io.ctxtToRfI.rowToRfI(i).addr1 === io.writeToRegfset(0).bits.destReg)
    val isBypass2XB = isBypassXB && (io.ctxtToRfI.rowToRfI(i).addr2 === io.writeToRegfset(0).bits.destReg)

    val isBypassXC  = (UInt(i) === ctxtIdLast0_reg) && bypassValid0_reg
    val isBypass1XC = isBypassXC && (io.ctxtToRfI.rowToRfI(i).addr1 === destRegLast0_reg)
    val isBypass2XC = isBypassXC && (io.ctxtToRfI.rowToRfI(i).addr2 === destRegLast0_reg)

    val isBypassXD  = (UInt(i) === ctxtIdLast0D1_reg) && bypassValid0D1_reg
    val isBypass1XD = isBypassXD && (io.ctxtToRfI.rowToRfI(i).addr1 === destRegLast0D1_reg)
    val isBypass2XD = isBypassXD && (io.ctxtToRfI.rowToRfI(i).addr2 === destRegLast0D1_reg)


    val isBypassYB  = (UInt(i) === io.writeToRegfset(1).bits.ctxtId) && io.writeToRegfset(1).valid
    val isBypass1YB = isBypassYB && (io.ctxtToRfI.rowToRfI(i).addr1 === io.writeToRegfset(1).bits.destReg)
    val isBypass2YB = isBypassYB && (io.ctxtToRfI.rowToRfI(i).addr2 === io.writeToRegfset(1).bits.destReg)

    val isBypassYC  = (UInt(i) === ctxtIdLast1_reg) && bypassValid1_reg
    val isBypass1YC = isBypassYC && (io.ctxtToRfI.rowToRfI(i).addr1 === destRegLast1_reg)
    val isBypass2YC = isBypassYC && (io.ctxtToRfI.rowToRfI(i).addr2 === destRegLast1_reg)

    val isBypassYD  = (UInt(i) === ctxtIdLast1D1_reg) && bypassValid1D1_reg
    val isBypass1YD = isBypassYD && (io.ctxtToRfI.rowToRfI(i).addr1 === destRegLast1D1_reg)
    val isBypass2YD = isBypassYD && (io.ctxtToRfI.rowToRfI(i).addr2 === destRegLast1D1_reg)

    // D1_reg >> is a one cycle delayed version.. D2_reg >> is a two cycle delayed version
    // The write takes three cycles, we need to check if the read require a register that is being updated in the current cycle of the previous cycles.
    // Q: Why "0D1, 1D1, 2D1"?  what's the 0,1,2? A: 0,1,2 are the ID of the sources, 0: PipeUnit, 1: DataUnit, 2: Div/Mul

    // X,Y,Z is an indication for the source.: X: PipeUnit, Y: DataUnit, Z: Div/Mul.
    // A..E is for the cycle in which the write happens..
    // For example, isBypass1XB and isBypass1XE can both be '1', indicating they write to the same register, separated by 3 cycles..
    // The bypass should only be the last of those two (last in instruction issue order order)
    //
    // So.. "1XD" is 4 cycles old, indicated by "D" .. while "0D1" is one cycle delayed where "D" indicates "Delayed"
    // However we need to bypass only isBypass1XB which is the closest.
    when(isBypass1XB) {
      bypassData1D1_reg(i) := dataPipeU
    }
    .elsewhen(isBypass1YB) {
      bypassData1D1_reg(i) := io.writeToRegfset(1).bits.dataToWrite
    }
    .elsewhen(isBypass1XC) {
      bypassData1D1_reg(i) := dataToWriteLast0_reg
    }
    .elsewhen(isBypass1YC) {
      bypassData1D1_reg(i) := dataToWriteLast1_reg
    }
    .otherwise {
      // Relax the logic by avoiding using isBypass1YD.
      bypassData1D1_reg(i) := Mux(isBypass1XD, dataToWriteLast0D1_reg, dataToWriteLast1D1_reg)
    }

    when(isBypass2XB) {
      bypassData2D1_reg(i) := dataPipeU
    }
    .elsewhen(isBypass2YB) {
      bypassData2D1_reg(i) := io.writeToRegfset(1).bits.dataToWrite
    }
    .elsewhen(isBypass2XC) {
      bypassData2D1_reg(i) := dataToWriteLast0_reg
    }
    .elsewhen(isBypass2YC) {
      bypassData2D1_reg(i) := dataToWriteLast1_reg
    }
    .otherwise {
      // Relax the logic by avoiding using isBypass2YD.
      bypassData2D1_reg(i) := Mux(isBypass2XD, dataToWriteLast0D1_reg, dataToWriteLast1D1_reg)
    }

    // Delay address.
    addr1BypassD1_reg(i)  := isBypass1XB || isBypass1XC || isBypass1XD || isBypass1YB || isBypass1YC || isBypass1YD
    addr2BypassD1_reg(i)  := isBypass2XB || isBypass2XC || isBypass2XD || isBypass2YB || isBypass2YC || isBypass2YD
  }

  for(mps <- 0 until NUM_MPS+1) {
    val addr1D1_reg  = RegEnable(io.ctxtToRfI.addr1(mps), io.ctxtToRfI.rdEna(mps))
    val addr2D1_reg  = RegEnable(io.ctxtToRfI.addr2(mps), io.ctxtToRfI.rdEna(mps))
    val ctxtIdD1_reg = RegEnable(io.ctxtToRfI.ctxtId(mps), io.ctxtToRfI.rdEna(mps))

    // Check if zero.
    val addr1NZero   = addr1D1_reg =/= UInt(0)
    val addr2NZero   = addr2D1_reg =/= UInt(0)

    // Check if we can use bypass.
    val isBypassXA  = (ctxtIdD1_reg === io.writeToRegfset(0).bits.ctxtId) && io.writeToRegfset(0).valid
    val isBypass1XA = isBypassXA && (addr1D1_reg === io.writeToRegfset(0).bits.destReg)
    val isBypass2XA = isBypassXA && (addr2D1_reg === io.writeToRegfset(0).bits.destReg)

    val isBypassYA  = (ctxtIdD1_reg === io.writeToRegfset(1).bits.ctxtId)  && io.writeToRegfset(1).valid
    val isBypass1YA = isBypassYA && (addr1D1_reg === io.writeToRegfset(1).bits.destReg)
    val isBypass2YA = isBypassYA && (addr2D1_reg === io.writeToRegfset(1).bits.destReg)

    val operand1   = Wire(UInt())
    val operand2   = Wire(UInt())
    val operand1B  = Wire(UInt())
    val operand2B  = Wire(UInt())
    val byPass1    = Wire(Bool())
    val byPass2    = Wire(Bool())
    if(mps == 0) {
      operand1  := OrTree((0 until NUM_CTXT).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), regfile1(w).rdData1, UInt(0))))
      operand2  := OrTree((0 until NUM_CTXT).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), regfile1(w).rdData2, UInt(0))))
      operand1B := OrTree((0 until NUM_CTXT).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), bypassData1D1_reg(w), UInt(0))))
      operand2B := OrTree((0 until NUM_CTXT).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), bypassData2D1_reg(w), UInt(0))))
      byPass1   := OrTree((0 until NUM_CTXT).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), addr1BypassD1_reg(w), UInt(0))))
      byPass2   := OrTree((0 until NUM_CTXT).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), addr2BypassD1_reg(w), UInt(0))))
    }
    else {
      operand1  := OrTree(((mps-1)*NUM_CTXT/NUM_MPS until mps*NUM_CTXT/NUM_MPS).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), regfile1(w).rdData1, UInt(0))))
      operand2  := OrTree(((mps-1)*NUM_CTXT/NUM_MPS until mps*NUM_CTXT/NUM_MPS).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), regfile1(w).rdData2, UInt(0))))
      operand1B := OrTree(((mps-1)*NUM_CTXT/NUM_MPS until mps*NUM_CTXT/NUM_MPS).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), bypassData1D1_reg(w), UInt(0))))
      operand2B := OrTree(((mps-1)*NUM_CTXT/NUM_MPS until mps*NUM_CTXT/NUM_MPS).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), bypassData2D1_reg(w), UInt(0))))
      byPass1   := OrTree(((mps-1)*NUM_CTXT/NUM_MPS until mps*NUM_CTXT/NUM_MPS).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), addr1BypassD1_reg(w), UInt(0))))
      byPass2   := OrTree(((mps-1)*NUM_CTXT/NUM_MPS until mps*NUM_CTXT/NUM_MPS).map((w: Int) => Mux(ctxtIdD1_reg === UInt(w), addr2BypassD1_reg(w), UInt(0))))
    }

    // Inform CTXT about the change of the registers.
    io.regfsetToCtxt.regSafe0  := io.writeToRegfset(2).fire() || io.writeToRegfset(3).fire()
    io.regfsetToCtxt.isReal0   := wrPulse
    io.regfsetToCtxt.regId0    := destReg
    io.regfsetToCtxt.ctxtId0   := wrCtxtId

    // Writing from ALU has the highest priority.
    io.regfsetToIntpipe(mps).rdData1 := Mux(isBypass1XA, dataPipeU, Mux(isBypass1YA, io.writeToRegfset(1).bits.dataToWrite, Mux(byPass1, operand1B, operand1))) & (Vec.fill(DATA_LEN) {addr1NZero}).asUInt()
    io.regfsetToIntpipe(mps).rdData2 := Mux(isBypass2XA, dataPipeU, Mux(isBypass2YA, io.writeToRegfset(1).bits.dataToWrite, Mux(byPass2, operand2B, operand2))) & (Vec.fill(DATA_LEN) {addr2NZero}).asUInt()
  }
}

