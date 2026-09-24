// See README.md for license details.

package hardfloat

import chisel3._
import chisel3.util._


class FMA(val w: Int) extends Module /*(MulWidth) */ {
  
  val io = IO(new Bundle {
    val operandA = Input(UInt(w.W))
    val operandB = Input(UInt(w.W))
    val result   = Output(UInt((w+w).W))
  })

  if (w > 52){  
    val FMA = Module(new FPM_54())
    FMA.io.operandA := io.operandA
    FMA.io.operandB := io.operandB
    io.result       := FMA.io.result
  } 
  else { 
    val FMA = Module(new FPM_24())
    FMA.io.operandA := io.operandA
    FMA.io.operandB := io.operandB
    io.result       := FMA.io.result
  }

}


class FPM_54 extends Module {
  val io = IO(new Bundle {
    val operandA    = Input(UInt(54.W))
    val operandB    = Input(UInt(54.W))
    val result      = Output(UInt(108.W))
  })

  /******************************
  Interface Info 
  1) Input/ Outputs DataTypes
  2) Input/ Output Width/ Num of Bits
  *******************************/

  val a0 = Wire(UInt(18.W))
  val a1 = Wire(UInt(18.W))
  val a2 = Wire(UInt(18.W))
  val b0 = Wire(UInt(18.W))
  val b1 = Wire(UInt(18.W))
  val b2 = Wire(UInt(18.W))


  a0 := io.operandA(17, 0)
  a1 := io.operandA(35, 18)
  a2 := io.operandA(53, 36)
  b0 := io.operandB(17, 0)
  b1 := io.operandB(35, 18)
  b2 := io.operandB(53, 36)


  val a0b0_reg = Reg(UInt(36.W)) //shift by 0
  val a0b1_reg = Reg(UInt(36.W)) //shift by 1
  val a1b0_reg = Reg(UInt(36.W)) //shift by 1
  val a1b1_reg = Reg(UInt(36.W)) //shift by 2
  val a0b2_reg = Reg(UInt(36.W)) //shift by 2
  val a2b0_reg = Reg(UInt(36.W)) //shift by 2
  val a1b2_reg = Reg(UInt(36.W)) //shift by 3
  val a2b1_reg = Reg(UInt(36.W)) //shift by 3
  val a2b2_reg = Reg(UInt(36.W)) //shift by 4

  /****************************************
  * ^ ^ ^ ^ ^ ^ ^ PIPE 1 ^ ^ ^ ^ ^ ^ ^ ^ ^ 
  *****************************************/
  a0b0_reg := a0*b0
  a0b1_reg := a0*b1
  a1b0_reg := a1*b0
  a1b1_reg := a1*b1
  a0b2_reg := a0*b2
  a2b0_reg := a2*b0
  a1b2_reg := a1*b2
  a2b1_reg := a2*b1
  a2b2_reg := a2*b2

  val sum_1s       = Wire(UInt(36.W))
  val carry_1s     = Wire(UInt(36.W))

  val CSA_1s       = Module(new CSA_36)
  CSA_1s.io.value1 := a0b0_reg(35,18)
  CSA_1s.io.value2 := a1b0_reg
  CSA_1s.io.value3 := a0b1_reg

  sum_1s           := CSA_1s.io.sum
  carry_1s         := CSA_1s.io.carry

  val sum_2s       = Wire(UInt(36.W))
  val carry_2s     = Wire(UInt(36.W))

  val CSA_2s       = Module(new CSA_36)
  CSA_2s.io.value1 := a1b1_reg
  CSA_2s.io.value2 := a0b2_reg
  CSA_2s.io.value3 := a2b0_reg

  sum_2s           := CSA_2s.io.sum
  carry_2s         := CSA_2s.io.carry

  val sum_3s       = Wire(UInt(36.W))
  val carry_3s     = Wire(UInt(36.W))

  val CSA_3s       = Module(new CSA_36)
  CSA_3s.io.value1 := a1b2_reg
  CSA_3s.io.value2 := a2b1_reg
  CSA_3s.io.value3 := a2b2_reg(17,0) << 18.U

  sum_3s                := CSA_3s.io.sum
  carry_3s              := CSA_3s.io.carry

  /*******************************************************
  * ^ ^ ^ ^ ^ ^ ^ ^ ^ PIPE 2 ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^
  ********************************************************/

  val result_reg = Reg(UInt(108.W))
  result_reg     :=  a0b0_reg(17,0) + (sum_1s << 18.U) + (carry_1s << 19.U) + (sum_2s << 36.U) + (carry_2s << 37.U) + (sum_3s << 54.U) + (carry_3s << 55.U) + (a2b2_reg(35, 18) << 90.U) 
  io.result      := result_reg.asUInt
}


class FPM_24 extends Module {
  val io = IO(new Bundle {
    val operandA    = Input(UInt(24.W))
    val operandB    = Input(UInt(24.W))
    val result      = Output(UInt(48.W))
  })

  /******************************
  Interface Info 
  1) Input/ Outputs DataTypes
  2) Input/ Output Width/ Num of Bits
  *******************************/
 
  val a0 = Wire(UInt(12.W))
  val a1 = Wire(UInt(12.W))
  val b0 = Wire(UInt(12.W))
  val b1 = Wire(UInt(12.W))

  a0 := io.operandA(11, 0)
  a1 := io.operandA(23, 12)
  b0 := io.operandB(11, 0)
  b1 := io.operandB(23, 12)

  val a0b0 = Wire(UInt(24.W)) //shift 0
  val a0b1 = Wire(UInt(24.W)) //shift 1
  val a1b0 = Wire(UInt(24.W)) //shift 2
  val a1b1 = Wire(UInt(24.W)) //shift 3

  /****************************************
  * ^ ^ ^ ^ ^ ^ ^ PIPE 1 ^ ^ ^ ^ ^ ^ ^ ^ ^ 
  *****************************************/

  a0b0 := a0 * b0
  a0b1 := a0 * b1
  a1b0 := a1 * b0
  a1b1 := a1 * b1

  val a0b0_reg = Reg(UInt(36.W))
  val a1b0_reg = Reg(UInt(36.W))
  val a0b1_reg = Reg(UInt(36.W))
  val a1b1_reg = Reg(UInt(36.W))
  a0b0_reg := a0b0
  a1b0_reg := a1b0
  a0b1_reg := a0b1
  a1b1_reg := a1b1

  //Only shifted by 1
  val sum_2s            = Wire(UInt(36.W))
  val carry_2s          = Wire(UInt(36.W))
  val CSAlevel_2s       = Module(new CSA_36)
  CSAlevel_2s.io.value1 := a1b0_reg
  CSAlevel_2s.io.value2 := a0b1_reg
  CSAlevel_2s.io.value3 := (a1b1_reg << 12.U)
  sum_2s                := CSAlevel_2s.io.sum
  carry_2s              := CSAlevel_2s.io.carry

  /*******************************************************
  * ^ ^ ^ ^ ^ ^ ^ ^ ^ PIPE 2 ^ ^ ^ ^ ^ ^ ^ ^ ^ ^ ^
  ********************************************************/

  val result_reg = Reg(UInt(48.W))
  result_reg    := (sum_2s << 12.U) + (carry_2s << 13.U) + (a0b0_reg)
  io.result     := result_reg.asUInt
}

class CSA_12 extends Module {
  val io = IO(new Bundle {
    val value1  = Input(UInt(12.W))
    val value2  = Input(UInt(12.W))
    val value3  = Input(UInt(12.W))
    val sum     = Output(UInt(12.W))
    val carry   = Output(UInt(12.W))
  })
  
  val sumTemp   = VecInit(io.value1.toBools)
  val carryTemp = VecInit(io.value1.toBools)

  for(i <- 0 to 11){
    sumTemp(i) := io.value1(i) ^ io.value2(i) ^ io.value3(i)
  }

  for(i <- 0 to 11){
    carryTemp(i) := (io.value1(i) & io.value2(i)) | ( io.value3(i) & (io.value1(i) ^ io.value2(i)) )
  }

  io.sum   := sumTemp.asUInt
  io.carry := carryTemp.asUInt
}



class CSA_32 extends Module {
  val io = IO(new Bundle {
    val value1  = Input(UInt(32.W))
    val value2  = Input(UInt(32.W))
    val value3  = Input(UInt(32.W))
    val sum     = Output(UInt(32.W))
    val carry   = Output(UInt(32.W))
  })
  
  val sumTemp   = VecInit(io.value1.toBools)
  val carryTemp = VecInit(io.value1.toBools)

  for(i <- 0 to 31){
    sumTemp(i) := io.value1(i) ^ io.value2(i) ^ io.value3(i)
  }

  for(i <- 0 to 31){
    carryTemp(i) := (io.value1(i) & io.value2(i)) | ( io.value3(i) & (io.value1(i) ^ io.value2(i)) )
  }

  io.sum   := sumTemp.asUInt
  io.carry := carryTemp.asUInt
}


class CSA_16 extends Module {
  val io = IO(new Bundle {
    val value1  = Input(UInt(16.W))
    val value2  = Input(UInt(16.W))
    val value3  = Input(UInt(16.W))
    val sum     = Output(UInt(16.W))
    val carry   = Output(UInt(16.W))
  })
  
  val sumTemp = VecInit(io.value1.toBools)
  val carryTemp = VecInit(io.value1.toBools)


  for(i <- 0 to 15){
    sumTemp(i) := io.value1(i) ^ io.value2(i) ^ io.value3(i)
  }

  for(i <- 0 to 15){
    carryTemp(i) := (io.value1(i) & io.value2(i)) | ( io.value3(i) & (io.value1(i) ^ io.value2(i)) )
  }

  io.sum := sumTemp.asUInt
  io.carry := carryTemp.asUInt
}

class CSA_6 extends Module {
  val io = IO(new Bundle {
    val value1  = Input(UInt(6.W))
    val value2  = Input(UInt(6.W))
    val value3  = Input(UInt(6.W))
    val sum     = Output(UInt(6.W))
    val carry   = Output(UInt(6.W))
  })
  
  val sumTemp   = VecInit(io.value1.toBools)
  val carryTemp = VecInit(io.value1.toBools)

  for(i <- 0 to 5){
    sumTemp(i) := io.value1(i) ^ io.value2(i) ^ io.value3(i)
  }

  for(i <- 0 to 5){
    carryTemp(i) := (io.value1(i) & io.value2(i)) | ( io.value3(i) & (io.value1(i) ^ io.value2(i)) )
  }

  io.sum   := sumTemp.asUInt
  io.carry := carryTemp.asUInt
}

class CSA_36 extends Module {
  val io = IO(new Bundle {
    val value1  = Input(UInt(36.W))
    val value2  = Input(UInt(36.W))
    val value3  = Input(UInt(36.W))
    val sum     = Output(UInt(36.W))
    val carry   = Output(UInt(36.W))
  })
  
  val sumTemp   = VecInit(io.value1.toBools)
  val carryTemp = VecInit(io.value1.toBools)

  for(i <- 0 to 35){
    sumTemp(i) := io.value1(i) ^ io.value2(i) ^ io.value3(i)
  }

  for(i <- 0 to 35){
    carryTemp(i) := (io.value1(i) & io.value2(i)) | ( io.value3(i) & (io.value1(i) ^ io.value2(i)) )
  }

  io.sum   := sumTemp.asUInt
  io.carry := carryTemp.asUInt
}
