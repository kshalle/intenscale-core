// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.util._
import ALU._
import constants.StConfiguration._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket.constants._
import superThread._

class MulDivReqE()(implicit val p: Parameters) extends Bundle
{
  val req = new MulDivReq()(p)
  val id  = UInt(OUTPUT, 3)
}


class MulL(cfg: MulDivParams)(implicit val p: Parameters) extends Module {
  val io      = new MulDivIO()
  val w       = io.req.bits.in1.getWidth

  require(w == 32 || w == 64)
  def halfWidth(req: MulDivReq) = Bool(w > 32) && req.dw === DW_32

  val lhs_in = io.req.bits.in1
  val rhs_in = io.req.bits.in2


  val resultTmpD4_reg = Reg(Bits(width = 64))
  val resultD5_reg    = Reg(Bits(width = 64))
  val reqD1_reg       = Reg(new MulDivReqE())
  val reqD2_reg       = Reg(new MulDivReqE())
  val reqD3_reg       = Reg(new MulDivReqE())
  val reqD4_reg       = Reg(new MulDivReqE())
  val reqD5_reg       = Reg(new MulDivReqE())

  val validD1P_reg = Reg(init = Bool(false))
  val valiD1_reg   = Reg(init = Bool(false))
  val valiD2_reg   = Reg(init = Bool(false))
  val valiD3_reg   = Reg(init = Bool(false))
  val valiD4_reg   = Reg(init = Bool(false))
  val valiD5_reg   = Reg(init = Bool(false))

  val mulLLD3_reg = Reg(init = UInt(0, width=64))
  val mulLHD3_reg = Reg(init = UInt(0, width=64))
  val mulHLD3_reg = Reg(init = UInt(0, width=64))
  val mulHLD4_reg = Reg(init = UInt(0, width=64))

  val id_reg      = Reg(init = UInt(0, width=3))
  val idD2_reg    = Reg(init = UInt(0, width=3))
  val idD3_reg    = Reg(init = UInt(0, width=3))

  val opA_1    = Wire(Bits(width = 32))
  val opB_1    = Wire(Bits(width = 32))
  val opA_2    = Wire(Bits(width = 32))
  val opB_2    = Wire(Bits(width = 32))
  val opA_3    = Wire(Bits(width = 32))
  val opB_3    = Wire(Bits(width = 32))

  // Ready flags for each pipe stage.
  val p0Ready = !(valiD5_reg && valiD4_reg && valiD3_reg && valiD2_reg && valiD1_reg)
  val p1Ready = !(valiD5_reg && valiD4_reg && valiD3_reg && valiD2_reg              )
  val p2Ready = !(valiD5_reg && valiD4_reg && valiD3_reg                            )
  val p3Ready = !(valiD5_reg && valiD4_reg                                          )
  val p4Ready = !(valiD5_reg                                                        )

  //Dividing multiplications into smaller parallel ones;
  opA_1 := lhs_in(31,0)
  opB_1 := rhs_in(31,0)
  opA_2 := lhs_in(31,0)
  opB_2 := rhs_in(63,32)
  opA_3 := lhs_in(63,32)
  opB_3 := rhs_in(31,0)

  // Track the ID to match when isRealD3 is received.
  idD2_reg := reqD1_reg.id
  idD3_reg := idD2_reg

  // Capture the correct version of isRealD3.
  val validD2P_reg  = Reg(next=validD1P_reg, init=Bool(false))
  val validD3P_reg  = Reg(next=validD2P_reg, init=Bool(false))
  when(validD3P_reg) {
    when(reqD1_reg.id===idD3_reg) {
      reqD1_reg.req.isRealD3 := io.req.bits.isRealD3
    }
    when(reqD2_reg.id===idD3_reg) {
      reqD2_reg.req.isRealD3 := io.req.bits.isRealD3
    }
    when(reqD3_reg.id===idD3_reg) {
      reqD3_reg.req.isRealD3 := io.req.bits.isRealD3
    }
  }

  def sub_block_mutiplier(n:Int)(in1:UInt, in2:UInt) = {
      val block1_reg = Reg(init = UInt(0, width=n))
      val block2_reg = Reg(init = UInt(0, width=n))
      val block3_reg = Reg(init = UInt(0, width=n))
      val block4_reg = Reg(init = UInt(0, width=n))
      val res_reg    = Reg(init = UInt(0, width=2*n))

      when(p0Ready && io.req.valid) {
        block1_reg := in1((n/2)-1,0)  * in2((n/2)-1,0)
        block2_reg := in1((n/2)-1,0)  * in2(n-1,(n/2))
        block3_reg := in1(n-1,(n/2))  * in2((n/2)-1,0)
        block4_reg := in1(n-1,(n/2))  * in2(n-1,(n/2))
      }

      when(p1Ready && valiD1_reg) {
        res_reg := block1_reg + (block2_reg << (n/2).U) + (block3_reg << (n/2).U) + (block4_reg << n.U)
      }
      res_reg
    }

  // The following multiplications happen over two cycles.
  val mulLL1D2_reg = sub_block_mutiplier(16)(opA_1(15,0) , opB_1(15,0))
  val mulLH1D2_reg = sub_block_mutiplier(16)(opA_1(15,0) , opB_1(31,16))
  val mulHL1D2_reg = sub_block_mutiplier(16)(opA_1(31,16), opB_1(15,0))
  val mulHH1D2_reg = sub_block_mutiplier(16)(opA_1(31,16), opB_1(31,16))

  val mulLL2D2_reg = sub_block_mutiplier(16)(opA_2(15,0) , opB_2(15,0))
  val mulLH2D2_reg = sub_block_mutiplier(16)(opA_2(15,0) , opB_2(31,16))
  val mulHL2D2_reg = sub_block_mutiplier(16)(opA_2(31,16), opB_2(15,0))
  val mulHH2D2_reg = sub_block_mutiplier(16)(opA_2(31,16), opB_2(31,16))

  val mulLL3D2_reg = sub_block_mutiplier(16)(opA_3(15,0) , opB_3(15,0))
  val mulLH3D2_reg = sub_block_mutiplier(16)(opA_3(15,0) , opB_3(31,16))
  val mulHL3D2_reg = sub_block_mutiplier(16)(opA_3(31,16), opB_3(15,0))
  val mulHH3D2_reg = sub_block_mutiplier(16)(opA_3(31,16), opB_3(31,16))

  //pipe_0
  validD1P_reg := Bool(false)
  when(p0Ready) {
    when(io.req.valid) {
      reqD1_reg.req := io.req.bits
      reqD1_reg.id  := id_reg
      id_reg        := id_reg + UInt(1)
      validD1P_reg  := Bool(true)
    }
    valiD1_reg := io.req.valid
  }

  // pipe_1
  when(p1Ready) {
    when(valiD1_reg) {
      reqD2_reg    := reqD1_reg
      when(validD3P_reg && (reqD1_reg.id===idD3_reg)) {
        reqD2_reg.req.isRealD3 := io.req.bits.isRealD3
      }
    }
    valiD2_reg := valiD1_reg
  }

  // pipe_2
  when(p2Ready) {
    when(valiD2_reg) {
      mulLLD3_reg  := mulLL1D2_reg + (mulLH1D2_reg << 16.U) + (mulHL1D2_reg << 16.U) + (mulHH1D2_reg << 32.U)
      mulLHD3_reg  := mulLL2D2_reg + (mulLH2D2_reg << 16.U) + (mulHL2D2_reg << 16.U) + (mulHH2D2_reg << 32.U)
      mulHLD3_reg  := mulLL3D2_reg + (mulLH3D2_reg << 16.U) + (mulHL3D2_reg << 16.U) + (mulHH3D2_reg << 32.U)

      reqD3_reg    := reqD2_reg
      when(validD3P_reg && (reqD2_reg.id===idD3_reg)) {
        reqD3_reg.req.isRealD3 := io.req.bits.isRealD3
      }
    }
    valiD3_reg := valiD2_reg
  }

  // pipe_3
  when(p3Ready) {
    when(valiD3_reg) {
      resultTmpD4_reg := mulLLD3_reg + (mulLHD3_reg << 32.U)
      mulHLD4_reg     := mulHLD3_reg
      reqD4_reg       := reqD3_reg
      when(validD3P_reg && (reqD3_reg.id===idD3_reg)) {
        reqD4_reg.req.isRealD3 := io.req.bits.isRealD3
      }
    }
    valiD4_reg := valiD3_reg
  }

  // Continue Working
  when (io.resp.fire()) {
    valiD5_reg := 0.U
  }

  // pipe_4
  when(p4Ready) {
    when(valiD4_reg) {
      when(halfWidth(reqD4_reg.req)) {
        resultD5_reg  := Cat(Fill(w/2, resultTmpD4_reg(w/2-1)), resultTmpD4_reg(w/2-1,0))
      }
      .otherwise {
        resultD5_reg  := resultTmpD4_reg + (mulHLD4_reg << 32.U)
      }
      reqD5_reg     := reqD4_reg
    }
    valiD5_reg := valiD4_reg
  }

  // output
  io.resp.bits             := reqD5_reg
  io.resp.bits.isReal      := reqD5_reg.req.isRealD3 // The delay on isRealD3 has been compensated in the pipe.
  io.resp.bits.dataToWrite := resultD5_reg
  io.resp.bits.destReg     := reqD5_reg.req.destReg
  io.resp.bits.ctxtId      := reqD5_reg.req.ctxtId
  io.resp.valid            := valiD5_reg === 1.U
  io.req.ready             := p0Ready //false when pipeline is full.
}

