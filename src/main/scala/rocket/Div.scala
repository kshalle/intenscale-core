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

class MulDivReq()(implicit val p: Parameters) extends Bundle with HasIntenParameters {
  val fn       = Bits(width = SZ_ALU_FN)
  val dw       = Bits(width = SZ_DW)
  val in1      = Bits(width = DATA_LEN)
  val in2      = Bits(width = DATA_LEN)
  val destReg  = UInt(width = REG_ID_LEN)
  val ctxtId   = UInt(width = CTXT_ID_LEN)
  val isRealD3 = Bool()
  override def cloneType = new MulDivReq().asInstanceOf[this.type]
}

class MulDivIO()(implicit val p: Parameters) extends Bundle {
  val req  = Decoupled(new MulDivReq()).flip
  val resp = Decoupled(new WriteToRegfsetBundle())
}

case class MulDivParams(
  mulUnroll   : Int     = 1, // This is used for MulH that requires the high 64 bits multiplier result.
  divUnroll   : Int     = 1,
  mulEarlyOut : Boolean = false,
  divEarlyOut : Boolean = false
)


class Div(cfg: MulDivParams)(implicit val p: Parameters) extends Module with HasIntenParameters {
  val io      = new MulDivIO()
  val w       = io.req.bits.in1.getWidth
  val divw    = (w+cfg.divUnroll-1)/cfg.divUnroll*cfg.divUnroll

  // TODO: Support higher unroll order for the division.
  require(cfg.divUnroll == 1)

  val part1Size = 32

  val s_ready :: s_neg_inputs :: s_neg_inputs1 :: s_busy :: s_move_rem :: s_neg_output :: s_done :: Nil = Enum(UInt(), 7)

  val state_reg      = Reg(init=s_ready)
  val req_reg        = Reg(io.req.bits)
  val isRecReal_reg  = Reg(init=Bool(false))
  val validD1P_reg   = Reg(init=Bool(false))
  val count_reg      = Reg(UInt(width = log2Ceil(w/cfg.divUnroll + 1)))
  val neg_out_reg    = Reg(init=Bool(false))
  val isHi_reg       = Reg(init=Bool(false))
  val divisor_reg    = Reg(Bits(width = w+1)) // div only needs w bits
  val remainder_reg  = Reg(Bits(width = 2*divw+2)) // div only needs 2*w+1 bits

  val prodP1_reg    = Reg(init=UInt(0))
  val prodP2_reg    = Reg(init=UInt(0))
  val eOutRes_reg   = Reg(init=UInt(0))
  val eOut_reg      = Reg(init=Bool(false))
  val subP1_reg     = Reg(init=UInt(0))
  val subP2_reg     = Reg(init=UInt(0))

  val cmdHi :: lhsSigned :: rhsSigned :: Nil =
    DecodeLogic(io.req.bits.fn, List(X, X, X), List(
                       FN_DIV    -> List(N, Y, Y),
                       FN_REM    -> List(Y, Y, Y),
                       FN_DIVU   -> List(N, N, N),
                       FN_REMU   -> List(Y, N, N))).map(_ toBool)

  require(w == 32 || w == 64)
  def halfWidth(req: MulDivReq) = Bool(w > 32) && req.dw === DW_32

  def sext(x: Bits, halfW: Bool, signed: Bool) = {
    val sign = signed && Mux(halfW, x(w/2-1), x(w-1))
    val hi = Mux(halfW, Fill(w/2, sign), x(w-1,w/2))
    (Cat(hi, x(w/2-1,0)), sign)
  }
  val (lhs_in, lhs_sign) = sext(io.req.bits.in1, halfWidth(io.req.bits), lhsSigned)
  val (rhs_in, rhs_sign) = sext(io.req.bits.in2, halfWidth(io.req.bits), rhsSigned)

  val subtractor        = remainder_reg(2*w,w) - divisor_reg(w,0)
  val negated_remainder = -remainder_reg(w-1,0)

  // Implement a subtractor on two clock cycles.
  val op1S     = remainder_reg(2*w,w)
  val op2S     = divisor_reg(w,0)
  subP1_reg   := Cat(Fill(1, UInt(0)), op1S(part1Size-1, 0)) - Cat(Fill(1, UInt(0)), op2S(part1Size-1, 0))
  subP2_reg   := op1S(op1S.getWidth-1, part1Size) - op2S(op2S.getWidth-1, part1Size)
  val subP2T   = subP2_reg - UInt(1)
  val sub      =  Cat(Mux(subP1_reg(part1Size), subP2T, subP2_reg), subP1_reg(part1Size-1, 0))

  // Subtract in the same clock cycle.
  val subEarly = op1S - op2S

  when (state_reg === s_neg_inputs) {
    state_reg := s_busy
    when (remainder_reg(w-1)) {
      remainder_reg := negated_remainder
    }
    when (divisor_reg(w-1)) {
      state_reg := s_neg_inputs1
    }
  }

  when (state_reg === s_neg_inputs1) {
    divisor_reg := sub
    state_reg   := s_busy
  }

  when (state_reg === s_neg_output) {
    remainder_reg := negated_remainder
    state_reg     := s_done
  }

  when (state_reg === s_move_rem) {
    remainder_reg := remainder_reg(2*w, w+1)
    state_reg     := Mux(neg_out_reg, s_neg_output, s_done)
  }

  when (state_reg === s_busy) {
    val divisorMSB  = Log2(divisor_reg(w-1,0), w)
    val dividendMSB = Log2(remainder_reg(w-1,0), w)
    val eOutPos_w   = UInt(w-1) + divisorMSB - dividendMSB
    val eOutZero_w  = divisorMSB > dividendMSB

    // Note: req_reg.isRealD3 is active in the couple of cycles after the received request.
    when ((count_reg === UInt(w)) || !req_reg.isRealD3) {
      state_reg := Mux(isHi_reg && req_reg.isRealD3, s_move_rem, Mux(neg_out_reg && req_reg.isRealD3, s_neg_output, s_done))
    }
    count_reg      := count_reg + UInt(1)

    val less        = subEarly(w)
    remainder_reg  := Cat(Mux(less, remainder_reg(2*w-1,w), subEarly(w-1,0)), remainder_reg(w-1,0), !less)

    val eOut        = (count_reg === UInt(0)) && less /* not divby0 */ && ((eOutPos_w >= UInt(cfg.divUnroll)) || eOutZero_w)
    when (Bool(cfg.divEarlyOut) && eOut) {
      val shift      = Mux(eOutZero_w, UInt(w-1, log2Up(w)), eOutPos_w(log2Up(w)-1,0))
      remainder_reg := remainder_reg(w-1,0) << shift
      count_reg     := shift
    }

    when ((count_reg === UInt(0)) && !less /* divby0 */ && !isHi_reg) {
      neg_out_reg := Bool(false)
    }
  }

  when (io.resp.fire()) {
    state_reg := s_ready
  }

  // Capture the correct version of isRealD3.
  val validD2P_reg = Reg(next=validD1P_reg, init=Bool(false))
  val validD3P_reg = Reg(next=validD2P_reg, init=Bool(false))
  when(validD3P_reg) {
    req_reg.isRealD3 := io.req.bits.isRealD3
    isRecReal_reg    := Bool(true)
  }

  validD1P_reg := Bool(false)
  when (io.req.fire()) {
    state_reg        := Mux(lhs_sign || rhs_sign, s_neg_inputs, s_busy)
    isHi_reg         := cmdHi
    count_reg        := UInt(0)
    neg_out_reg      := Mux(cmdHi, lhs_sign, lhs_sign =/= rhs_sign)
    divisor_reg      := Cat(rhs_sign, rhs_in)
    remainder_reg    := lhs_in
    req_reg          := io.req.bits
    req_reg.isRealD3 := Bool(true) // In the first cycle, we force isRealD3=1 until we receive the correct value next cycle.
    validD1P_reg     := Bool(true)
    isRecReal_reg    := Bool(false)
  }

  io.resp.bits.isReal      := req_reg.isRealD3 // The delay on isRealD3 has been compensated in the pipe.
  io.resp.bits.dataToWrite := Mux(halfWidth(req_reg), Cat(Fill(w/2, remainder_reg(w/2-1)), remainder_reg(w/2-1,0)), remainder_reg(w-1,0))
  io.resp.bits.destReg     := req_reg.destReg
  io.resp.bits.ctxtId      := req_reg.ctxtId
  io.resp.valid            := (state_reg === s_done) && isRecReal_reg
  io.req.ready             := state_reg === s_ready
}

