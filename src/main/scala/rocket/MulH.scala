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

class MulH(cfg: MulDivParams)(implicit val p: Parameters) extends Module with HasIntenParameters {
  val io      = new MulDivIO()
  val w       = io.req.bits.in1.getWidth
  require(w == 32 || w == 64)

  // Define the states.
  val s_ready :: s_busy :: s_corrOp1 :: s_corrOp2 :: s_done :: Nil = Enum(UInt(), 5)

  val in2Sh_reg = Reg(UInt(width = w))
  val acc_reg   = Reg(UInt(width = w+1))
  val corr_reg  = Reg(UInt(width = w))
  val out_reg   = Reg(UInt(width = w))

  val state_reg    = Reg(init=s_ready)
  val req_reg      = Reg(io.req.bits)
  val validD1P_reg = Reg(init=Bool(false))
  val count_reg    = Reg(UInt(width = log2Ceil(w/cfg.mulUnroll)))

  val lhsSigned :: rhsSigned :: Nil =
    DecodeLogic(req_reg.fn, List(X, X), List(
                       FN_MULH   -> List(Y, Y),
                       FN_MULHU  -> List(N, N),
                       FN_MULHSU -> List(Y, N))).map(_ toBool)

  // Check if it is signed operands.
  val sign1 = req_reg.in1(w-1) & lhsSigned
  val sign2 = req_reg.in2(w-1) & rhsSigned

  validD1P_reg := Bool(false)
  when (io.req.fire()) {
    state_reg        := s_busy
    count_reg        := UInt(0)
    in2Sh_reg        := io.req.bits.in2
    acc_reg          := UInt(0)
    req_reg          := io.req.bits
    req_reg.isRealD3 := Bool(true) // In the first cycle, we force isRealD3=1 until we receive the correct value.
    validD1P_reg     := Bool(true)
  }

  // Capture the correct version of isRealD3.
  val validD2P_reg = Reg(next=validD1P_reg, init=Bool(false))
  val validD3P_reg = Reg(next=validD2P_reg, init=Bool(false))
  when(validD3P_reg) {
    req_reg.isRealD3 := io.req.bits.isRealD3
  }

  val acc = Mux(in2Sh_reg(0), Cat(UInt(0,1), acc_reg(w, 1)) + req_reg.in1, Cat(UInt(0,1), acc_reg(w, 1)))
  when(state_reg === s_busy) {
    // Register the accumulator output.
    acc_reg := acc

    // Apply bit-shift.
    in2Sh_reg := in2Sh_reg(w-1, 1)

    // Note: req_reg.isRealD3 is active in the couple of cycles after the received request.
    state_reg := s_busy
    count_reg := count_reg + UInt(1)
    when((count_reg === UInt(w - 1)) || !req_reg.isRealD3) {
      when(!req_reg.isRealD3 || !(sign1 || sign2)) {
        out_reg   := acc(w, 1)
        state_reg := s_done
      }
      .otherwise {
        when(sign2) {
          state_reg := s_corrOp1
        }
        .otherwise {
          req_reg.in1 := req_reg.in2
          state_reg   := s_corrOp2
        }
        corr_reg := acc(w, 1)
      }
    }
  }

  val corr = corr_reg - req_reg.in1
  when(state_reg === s_corrOp1) {
    req_reg.in1   := req_reg.in2 // Change op1 as it is used in corr logic.
    when(sign1) {
      corr_reg  := corr
      state_reg := s_corrOp2
    }
    .otherwise {
      out_reg   := corr
      state_reg := s_done
    }
  }
  when(state_reg === s_corrOp2) {
    out_reg   := corr
    state_reg := s_done
  }

  // Return to ready state when the new transaction has been accepted.
  when (io.resp.fire()) {
    state_reg := s_ready
  }

  io.resp.bits.isReal      := req_reg.isRealD3 // The delay on isRealD3 has been compensated in the pipe.
  io.resp.bits.dataToWrite := out_reg
  io.resp.bits.destReg     := req_reg.destReg
  io.resp.bits.ctxtId      := req_reg.ctxtId
  io.resp.valid            := state_reg === s_done
  io.req.ready             := state_reg === s_ready
}

