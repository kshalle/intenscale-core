
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with some contributions
from Yunsup Lee and Andrew Waterman, mainly concerning testing).

Copyright 2017 SiFive, Inc.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice,
    this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions, and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of SiFive nor the names of its contributors may
    be used to endorse or promote products derived from this software without
    specific prior written permission.

THIS SOFTWARE IS PROVIDED BY SIFIVE AND CONTRIBUTORS "AS IS", AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ARE
DISCLAIMED.  IN NO EVENT SHALL SIFIVE OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

=============================================================================*/



/*

s = sigWidth
c_i = newBit

Division:
width of a is (s+2)

Normal
------

(qi + ci * 2^(-i))*b <= a
q0 = 0
r0 = a

q(i+1) = qi + ci*2^(-i)
ri = a - qi*b
r(i+1) = a - q(i+1)*b
       = a - qi*b - ci*2^(-i)*b
r(i+1) = ri - ci*2^(-i)*b
ci = ri >= 2^(-i)*b
summary_i = ri != 0

i = 0 to s+1

(s+1)th bit plus summary_(i+1) gives enough information for rounding
If (a < b), then we need to calculate (s+2)th bit and summary_(i+1)
because we need s bits ignoring the leading zero. (This is skipCycle2
part of Hauser's code.)

Hauser
------
sig_i = qi
rem_i = 2^(i-2)*ri
cycle_i = s+3-i

sig_0 = 0
rem_0 = a/4
cycle_0 = s+3
bit_0 = 2^0 (= 2^(s+1), since we represent a, b and q with (s+2) bits)

sig(i+1) = sig(i) + ci*bit_i
rem(i+1) = 2rem_i - ci*b/2
ci = 2rem_i >= b/2
bit_i = 2^-i (=2^(cycle_i-2), since we represent a, b and q with (s+2) bits)
cycle(i+1) = cycle_i-1
summary_1 = a <> b
summary(i+1) = if ci then 2rem_i-b/2 <> 0 else summary_i, i <> 0

Proof:
2^i*r(i+1) = 2^i*ri - ci*b. Qed

ci = 2^i*ri >= b. Qed

summary(i+1) = if ci then rem(i+1) else summary_i, i <> 0
Now, note that all of ck's cannot be 0, since that means
a is 0. So when you traverse through a chain of 0 ck's,
from the end,
eventually, you reach a non-zero cj. That is exactly the
value of ri as the reminder remains the same. When all ck's
are 0 except c0 (which must be 1) then summary_1 is set
correctly according
to r1 = a-b != 0. So summary(i+1) is always set correctly
according to r(i+1)



Square root:
width of a is (s+1)

Normal
------
(xi + ci*2^(-i))^2 <= a
xi^2 + ci*2^(-i)*(2xi+ci*2^(-i)) <= a

x0 = 0
x(i+1) = xi + ci*2^(-i)
ri = a - xi^2
r(i+1) = a - x(i+1)^2
       = a - (xi^2 + ci*2^(-i)*(2xi+ci*2^(-i)))
       = ri - ci*2^(-i)*(2xi+ci*2^(-i))
       = ri - ci*2^(-i)*(2xi+2^(-i))  // ci is always 0 or 1
ci = ri >= 2^(-i)*(2xi + 2^(-i))
summary_i = ri != 0


i = 0 to s+1

For odd expression, do 2 steps initially.

(s+1)th bit plus summary_(i+1) gives enough information for rounding.

Hauser
------

sig_i = xi
rem_i = ri*2^(i-1)
cycle_i = s+2-i
bit_i = 2^(-i) (= 2^(s-i) = 2^(cycle_i-2) in terms of bit representation)

sig_0 = 0
rem_0 = a/2
cycle_0 = s+2
bit_0 = 1 (= 2^s in terms of bit representation)

sig(i+1) = sig_i + ci * bit_i
rem(i+1) = 2rem_i - ci*(2sig_i + bit_i)
ci = 2*sig_i + bit_i <= 2*rem_i
bit_i = 2^(cycle_i-2) (in terms of bit representation)
cycle(i+1) = cycle_i-1
summary_1 = a - (2^s) (in terms of bit representation) 
summary(i+1) = if ci then rem(i+1) <> 0 else summary_i, i <> 0


Proof:
ci = 2*sig_i + bit_i <= 2*rem_i
ci = 2xi + 2^(-i) <= ri*2^i. Qed

sig(i+1) = sig_i + ci * bit_i
x(i+1) = xi + ci*2^(-i). Qed

rem(i+1) = 2rem_i - ci*(2sig_i + bit_i)
r(i+1)*2^i = ri*2^i - ci*(2xi + 2^(-i))
r(i+1) = ri - ci*2^(-i)*(2xi + 2^(-i)). Qed

Same argument as before for summary.


------------------------------
Note that all registers are updated normally until cycle == 2.
At cycle == 2, rem is not updated, but all other registers are updated normally.
But, cycle == 1 does not read rem to calculate anything (note that final summary
is calculated using the values at cycle = 2).

*/



package hardfloat

import chisel3._
import chisel3.util._
import hardfloat.consts._
import chisel3.{withReset,RequireAsyncReset}

/*----------------------------------------------------------------------------
| Computes a division or square root for floating-point in recoded form.
| Multiple clock cycles are needed for each division or square-root operation,
| except possibly in special cases.
*----------------------------------------------------------------------------*/

class
    DivSqrtRawFN_small(expWidth: Int, sigWidth: Int, options: Int)
    extends Module with RequireAsyncReset
{
    val io = IO(new Bundle {
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val inReady        = Output(Bool())
        val inValid        = Input(Bool())
        val sqrtOp         = Input(Bool())
        val a              = Input(new RawFloat(expWidth, sigWidth))
        val b              = Input(new RawFloat(expWidth, sigWidth))
        val roundingMode   = Input(UInt(3.W))
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val rawOutValid_div  = Output(Bool())
        val rawOutValid_sqrt = Output(Bool())
        val roundingModeOut  = Output(UInt(3.W))
        val invalidExc       = Output(Bool())
        val infiniteExc      = Output(Bool())
        val rawOut = Output(new RawFloat(expWidth, sigWidth + 2))
    })

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val cycleNum       = RegInit(1.U((sigWidth + 3).W))

    val sqrtOp_Z       = Reg(Bool())
    val majorExc_Z     = Reg(Bool())
//*** REDUCE 3 BITS TO 2-BIT CODE:
    val isNaN_Z        = Reg(Bool())
    val isInf_Z        = Reg(Bool())
    val isZero_Z       = Reg(Bool())
    val sign_Z         = Reg(Bool())
    val sExp_Z         = Reg(SInt((expWidth + 2).W))
    val fractB_Z       = Reg(UInt((sigWidth - 1).W))
    val roundingMode_Z = Reg(UInt(3.W))

    /*------------------------------------------------------------------------
    | (The most-significant and least-significant bits of 'rem_Z' are needed
    | only for square roots.)
    *------------------------------------------------------------------------*/
    val rem_Z          = Reg(UInt((sigWidth + 2).W))
    val notZeroRem_Z   = Reg(Bool())
    val sigX_Z         = Reg(UInt((sigWidth + 2).W))

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val rawA_S = io.a
    val rawB_S = io.b

//*** IMPROVE THESE:
    val notSigNaNIn_invalidExc_S_div =
        (rawA_S.isZero && rawB_S.isZero) || (rawA_S.isInf && rawB_S.isInf)
    val notSigNaNIn_invalidExc_S_sqrt =
        ! rawA_S.isNaN && ! rawA_S.isZero && rawA_S.sign
    val majorExc_S =
        Mux(io.sqrtOp,
            isSigNaNRawFloat(rawA_S) || notSigNaNIn_invalidExc_S_sqrt,
            isSigNaNRawFloat(rawA_S) || isSigNaNRawFloat(rawB_S) ||
                notSigNaNIn_invalidExc_S_div ||
                (! rawA_S.isNaN && ! rawA_S.isInf && rawB_S.isZero)
        )
    val isNaN_S =
        Mux(io.sqrtOp,
            rawA_S.isNaN || notSigNaNIn_invalidExc_S_sqrt,
            rawA_S.isNaN || rawB_S.isNaN || notSigNaNIn_invalidExc_S_div
        )
    val isInf_S  = Mux(io.sqrtOp, rawA_S.isInf,  rawA_S.isInf || rawB_S.isZero)
    val isZero_S = Mux(io.sqrtOp, rawA_S.isZero, rawA_S.isZero || rawB_S.isInf)
    val sign_S = rawA_S.sign ^ (! io.sqrtOp && rawB_S.sign)

    val specialCaseA_S = rawA_S.isNaN || rawA_S.isInf || rawA_S.isZero
    val specialCaseB_S = rawB_S.isNaN || rawB_S.isInf || rawB_S.isZero
    val normalCase_S_div = ! specialCaseA_S && ! specialCaseB_S
    val normalCase_S_sqrt = ! specialCaseA_S && ! rawA_S.sign
    val normalCase_S = Mux(io.sqrtOp, normalCase_S_sqrt, normalCase_S_div)

    val sExpQuot_S_div =
        rawA_S.sExp +&
            Cat(rawB_S.sExp(expWidth), ~rawB_S.sExp(expWidth - 1, 0)).asSInt
//*** IS THIS OPTIMAL?:
    val sSatExpQuot_S_div =
        Cat(Mux(((BigInt(7)<<(expWidth - 2)).S <= sExpQuot_S_div),
                6.U,
                sExpQuot_S_div(expWidth + 1, expWidth - 2)
            ),
            sExpQuot_S_div(expWidth - 3, 0)
        ).asSInt

    val evenSqrt_S = io.sqrtOp && ! rawA_S.sExp(0)
    val oddSqrt_S  = io.sqrtOp &&   rawA_S.sExp(0)

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val idle = cycleNum(0)
    val inReady = idle || cycleNum(1)
    val entering = inReady && io.inValid
    val entering_normalCase = entering && normalCase_S

    val skipCycle2 = cycleNum(3) && sigX_Z(sigWidth + 1)

    when (! idle || entering) {
        cycleNum :=
            Mux(entering & ! normalCase_S, (1<<1).U, 0.U) |
            Mux(entering_normalCase,
                Mux(io.sqrtOp,
                    Mux(rawA_S.sExp(0), (BigInt(1)<<sigWidth).U, (BigInt(1)<<(sigWidth + 1)).U),
                    (BigInt(1)<<(sigWidth + 2)).U
                ),
                0.U
            ) |
            Mux(! entering && ! skipCycle2, cycleNum>>1, 0.U) |
            Mux(skipCycle2, (1<<1).U,    0.U)
    }

    io.inReady := inReady

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    when (entering) {
        sqrtOp_Z   := io.sqrtOp
        majorExc_Z := majorExc_S
        isNaN_Z    := isNaN_S
        isInf_Z    := isInf_S
        isZero_Z   := isZero_S
        sign_Z     := sign_S
    }
    when (entering_normalCase) {
        sExp_Z :=
            Mux(io.sqrtOp,
                (rawA_S.sExp>>1) +& (BigInt(1)<<(expWidth - 1)).S,
                sSatExpQuot_S_div
            )
        roundingMode_Z := io.roundingMode
    }
    when (entering_normalCase && ! io.sqrtOp) {
        fractB_Z := rawB_S.sig(sigWidth - 2, 0)
    }

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val rem =
        Mux(inReady && ! oddSqrt_S, rawA_S.sig<<1, 0.U) |
        Mux(inReady && oddSqrt_S,
            Cat(rawA_S.sig(sigWidth - 1, sigWidth - 2) - 1.U,
                rawA_S.sig(sigWidth - 3, 0)<<3
            ),
            0.U
        ) |
        Mux(! inReady, rem_Z<<1, 0.U)
    val bitMask = cycleNum>>2
    val trialTerm =
        Mux(inReady && ! io.sqrtOp, rawB_S.sig<<1,                 0.U) |
        Mux(inReady && evenSqrt_S,  (BigInt(1)<<sigWidth).U,       0.U) |
        Mux(inReady && oddSqrt_S,   (BigInt(5)<<(sigWidth - 1)).U, 0.U) |
        Mux(! inReady && ! sqrtOp_Z, Cat(1.U, fractB_Z)<<1,        0.U) |
        Mux(! inReady &&   sqrtOp_Z, sigX_Z<<1 | bitMask,          0.U)
    val trialRem = rem.zext - trialTerm.zext
    val newBit = (0.S <= trialRem)

    when (entering_normalCase || !(idle || cycleNum(2))) {
        rem_Z := Mux(newBit, trialRem.asUInt, rem)
    }
    when (entering_normalCase || (! inReady && newBit)) {
        notZeroRem_Z := (trialRem =/= 0.S)
        sigX_Z :=
            Mux(inReady && ! io.sqrtOp, newBit<<(sigWidth + 1),  0.U) |
            Mux(inReady &&   io.sqrtOp, (BigInt(1)<<sigWidth).U, 0.U) |
            Mux(inReady && oddSqrt_S,   newBit<<(sigWidth - 1),  0.U) |
            Mux(! inReady,              sigX_Z | bitMask,        0.U)
    }

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val rawOutValid = cycleNum(1)

    io.rawOutValid_div  := rawOutValid && ! sqrtOp_Z
    io.rawOutValid_sqrt := rawOutValid &&   sqrtOp_Z
    io.roundingModeOut  := roundingMode_Z
    io.invalidExc    := majorExc_Z &&   isNaN_Z
    io.infiniteExc   := majorExc_Z && ! isNaN_Z
    io.rawOut.isNaN  := isNaN_Z
    io.rawOut.isInf  := isInf_Z
    io.rawOut.isZero := isZero_Z
    io.rawOut.sign   := sign_Z
    io.rawOut.sExp   := sExp_Z
    io.rawOut.sig    := sigX_Z<<1 | notZeroRem_Z

}


class
    DivSqrtRecFNToRaw_small4(expWidth: Int, sigWidth: Int, options: Int)
    extends Module with RequireAsyncReset
{
    val io = IO(new Bundle {
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val inReady        = Output(Bool())
        val inValid        = Input(Bool())
        val sqrtOp         = Input(Bool())
        val a              = Input(UInt((expWidth + sigWidth + 1).W))
        val b              = Input(UInt((expWidth + sigWidth + 1).W))
        val roundingMode   = Input(UInt(3.W))
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val rawOutValid_div  = Output(Bool())
        val rawOutValid_sqrt = Output(Bool())
        val roundingModeOut  = Output(UInt(3.W))
        val invalidExc       = Output(Bool())
        val infiniteExc      = Output(Bool())
        val rawOut = Output(new RawFloat(expWidth, sigWidth + 2))
    })

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val cycleNum_reg       = RegInit(0.U((log2Up(sigWidth + 3)).W))
    val newBit             = Wire(UInt(2.W))
    val sqrtOp_Z_reg       = Reg(Bool())
    val majorExc_Z_reg     = RegInit( false.B)
//*** REDUCE 3 BITS TO 2-BIT CODE:
    val isNaN_Z_reg        = Reg(Bool())
    val isInf_Z_reg        = Reg(Bool())
    val isZero_Z_reg       = Reg(Bool())
    val ex1_valid          = RegInit( true.B)
    val sign_Z_reg         = RegInit( false.B)
    val sExp_Z_reg         = RegInit( 0.S((expWidth + 2).W))
    val fractB_Z_reg       = Reg(UInt((sigWidth - 1).W))
    val roundingMode_Z_reg = RegInit(0.U(3.W))
    val sigx_wire          = WireInit(0.U((sigWidth + 2).W))
    /*------------------------------------------------------------------------
    | (The most-significant and least-significant bits of 'rem_Z' are needed
    | only for square roots.)
    *------------------------------------------------------------------------*/
    val rem_Z_reg          = Reg(UInt((2*sigWidth+5).W))
    val notZeroRem_Z_reg   = RegInit(false.B)
    val sigX_Z_reg         = RegInit(0.U((sigWidth + 2).W))

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val rawA_S = rawFloatFromRecFN(expWidth, sigWidth, io.a)
    val rawB_S = rawFloatFromRecFN(expWidth, sigWidth, io.b)

//*** IMPROVE THESE:
    val notSigNaNIn_invalidExc_S_div =
        (rawA_S.isZero && rawB_S.isZero) || (rawA_S.isInf && rawB_S.isInf)
    val notSigNaNIn_invalidExc_S_sqrt =
        ! rawA_S.isNaN && ! rawA_S.isZero && rawA_S.sign
    val majorExc_S =
        Mux(io.sqrtOp,
            isSigNaNRawFloat(rawA_S) || notSigNaNIn_invalidExc_S_sqrt,
            isSigNaNRawFloat(rawA_S) || isSigNaNRawFloat(rawB_S) ||
                notSigNaNIn_invalidExc_S_div ||
                (! rawA_S.isNaN && ! rawA_S.isInf && rawB_S.isZero)
        )
    val isNaN_S =
        Mux(io.sqrtOp,
            rawA_S.isNaN || notSigNaNIn_invalidExc_S_sqrt,
            rawA_S.isNaN || rawB_S.isNaN || notSigNaNIn_invalidExc_S_div
        )
    val isInf_S  = Mux(io.sqrtOp, rawA_S.isInf,  rawA_S.isInf || rawB_S.isZero)
    val isZero_S = Mux(io.sqrtOp, rawA_S.isZero, rawA_S.isZero || rawB_S.isInf)
    val sign_S = rawA_S.sign ^ (! io.sqrtOp && rawB_S.sign)

    val specialCaseA_S = rawA_S.isNaN || rawA_S.isInf || rawA_S.isZero
    val specialCaseB_S = rawB_S.isNaN || rawB_S.isInf || rawB_S.isZero
    val normalCase_S_div = ! specialCaseA_S && ! specialCaseB_S
    val normalCase_S_sqrt = ! specialCaseA_S && ! rawA_S.sign
    val normalCase_S = Mux(io.sqrtOp, normalCase_S_sqrt, normalCase_S_div)

    val sExpQuot_S_div =
        rawA_S.sExp +&
            Cat(rawB_S.sExp(expWidth), ~rawB_S.sExp(expWidth - 1, 0)).asSInt
//*** IS THIS OPTIMAL?:
    val sSatExpQuot_S_div =
        Cat(Mux(((BigInt(7)<<(expWidth - 2)).S <= sExpQuot_S_div),
                6.U,
                sExpQuot_S_div(expWidth + 1, expWidth - 2)
            ),
            sExpQuot_S_div(expWidth - 3, 0)
        ).asSInt

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    val idle = (cycleNum_reg === 0.U)
    val inReady = (cycleNum_reg <= 1.U)
    val entering = inReady && io.inValid
    val entering_normalCase = entering && normalCase_S

    val skipCycle2 = (cycleNum_reg === 3.U || cycleNum_reg === 4.U) && sigx_wire(sigWidth + 1) 

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    when (entering) {
        sqrtOp_Z_reg   := io.sqrtOp
        majorExc_Z_reg := majorExc_S
        isNaN_Z_reg    := isNaN_S
        isInf_Z_reg    := isInf_S
        isZero_Z_reg   := isZero_S
        sign_Z_reg     := sign_S
    }
    val exp_sqrt = (rawA_S.sExp>>1) +& (BigInt(1)<<(expWidth - 1)).S
    when (entering_normalCase) {
        sExp_Z_reg := Mux(io.sqrtOp, exp_sqrt, sSatExpQuot_S_div )
        roundingMode_Z_reg := io.roundingMode
    }
    when (entering_normalCase && ! io.sqrtOp) {
        fractB_Z_reg := rawB_S.sig(sigWidth - 2, 0)
    }
    val rem_sqrt_odd    = rawA_S.sig<<(rawA_S.sExp(1,0)+1.U)
    val testterm_sqrt   = (sigX_Z_reg<<((sigWidth + 4).U-cycleNum_reg) | (1.U(2.W)<<sigWidth))
    val testterm_div    = Cat(1.U(1.W), fractB_Z_reg)<<2
    val testterm1_sqrt  = (sigX_Z_reg<<((sigWidth + 5).U-cycleNum_reg) | (2.U(2.W)<<sigWidth+1))
    val testterm1_div   = (Cat(1.U(1.W), fractB_Z_reg)<<2)<<1
    val testterm2_div   = (Cat(1.U(1.W), fractB_Z_reg)<<2)*3.U(2.W)
    val testterm2_sqrt  = ((sigX_Z_reg<<((sigWidth + 4).U-cycleNum_reg) | (3.U(2.W)<<sigWidth))*3.U(2.W))
    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    ex1_valid := Mux(io.inValid, false.B, !ex1_valid)
    when ((! idle && ex1_valid) || io.inValid) {
        cycleNum_reg :=
            Mux(entering & ! normalCase_S, 1.U, 0.U) |
            Mux(entering_normalCase, Mux(io.sqrtOp, (sigWidth + 1).U, (sigWidth + 2).U), 0.U) |
            Mux(! idle && ! skipCycle2 && (cycleNum_reg > 2.U), cycleNum_reg - 2.U, 0.U) |
            Mux(! idle && ! skipCycle2 && (cycleNum_reg <= 2.U), cycleNum_reg - 1.U, 0.U) |
            Mux(! idle &&   skipCycle2, 1.U, 0.U)
    }
    val rem =
        Mux(inReady && !io.sqrtOp, rawA_S.sig<<2, 0.U) |
        Mux(inReady && io.sqrtOp && rawA_S.sExp(1,0) =/= 3.U, rem_sqrt_odd, 0.U) |
        Mux(inReady && io.sqrtOp && rawA_S.sExp(1,0) === 3.U, rawA_S.sig<<4, 0.U) |
        Mux(!inReady && sqrtOp_Z_reg, rem_Z_reg<<4, 0.U) |
        Mux(!inReady && !sqrtOp_Z_reg, rem_Z_reg<<2, 0.U)
    val trialTerm =
        Mux(inReady  && !io.sqrtOp, rawB_S.sig<<2,             0.U) |
        Mux(inReady  && io.sqrtOp, (1.U(4.W)<<sigWidth),       0.U) |
        Mux(!inReady && sqrtOp_Z_reg, testterm_sqrt, 0.U) |
        Mux(!inReady && !sqrtOp_Z_reg, testterm_div, 0.U)
    val trialTerm1 =
        Mux(inReady  && !io.sqrtOp, rawB_S.sig<<3,             0.U) |
        Mux(inReady  && io.sqrtOp, (4.U(4.W)<<sigWidth),       0.U) |
        Mux(!inReady && sqrtOp_Z_reg, testterm1_sqrt, 0.U) |
        Mux(!inReady && !sqrtOp_Z_reg, testterm1_div, 0.U)
    val trialTerm2 =
        Mux(inReady  && !io.sqrtOp,(rawB_S.sig<<2)*3.U(2.W),   0.U) |
        Mux(inReady  && io.sqrtOp, (9.U(4.W)<<sigWidth),       0.U) |
        Mux(!inReady && sqrtOp_Z_reg, testterm2_sqrt, 0.U) |
        Mux(!inReady && !sqrtOp_Z_reg, testterm2_div, 0.U)
//---------------------------------------------------------------------------------------
    val ex1_inReady             = RegNext( inReady)
    val ex1_sqrtOp              = RegNext( io.sqrtOp)
    val ex1_trialTerm           = RegNext( trialTerm)
    val ex1_trialTerm2          = RegNext( trialTerm2)
    val ex1_trialTerm1          = RegNext( trialTerm1)
    val ex1_cycleNum_reg        = RegNext( cycleNum_reg)
    val ex1_rem                 = RegNext( rem)
    val ex1_normalCase_S        = RegNext( normalCase_S)
    val ex1_entering            = RegNext( entering)
    val ex1_inValid             = RegNext( io.inValid)
    val ex1_rawA_S_sExp         = RegEnable(rawA_S.sExp(1,0), entering)
    val ex1_entering_normalCase = RegNext( entering_normalCase)
//-----------------------------------------------------------------------------------------
    val evenSqrt_S = sqrtOp_Z_reg && ex1_rawA_S_sExp(1,0) === 2.U
    val oddSqrt_S  = sqrtOp_Z_reg && ex1_rawA_S_sExp(1,0) === 3.U

    val trialRem  = ex1_rem.zext - ex1_trialTerm.zext
    val trialRem1 = ex1_rem.zext - ex1_trialTerm1.zext
    val trialRem2 = ex1_rem.zext - ex1_trialTerm2.zext

    newBit := Mux(0.S <= trialRem2, 3.U(2.W), 0.U(2.W)) |
              Mux(0.S <= trialRem1 && 0.S > trialRem2, 2.U(2.W), 0.U(2.W)) |
              Mux(0.S <= trialRem && 0.S > trialRem1,  1.U(2.W), 0.U(2.W))
    val bitMask = Mux(ex1_valid, 0.U, ((newBit<<ex1_cycleNum_reg)>>3))
    val new_rem = Mux(newBit === 3.U, trialRem2.asUInt, 0.U) |
                  Mux(newBit === 2.U, trialRem1.asUInt, 0.U) |
                  Mux(newBit === 1.U, trialRem.asUInt, 0.U)  |
                  Mux(newBit === 0.U, ex1_rem, 0.U)

    when (ex1_entering_normalCase || (ex1_cycleNum_reg > 2.U)) {
        rem_Z_reg := new_rem
    }
    when (ex1_entering_normalCase || (! ex1_inReady && ex1_cycleNum_reg >= 2.U)) {
        notZeroRem_Z_reg := (new_rem =/= 0.U)    //corner case: this flags tells when remainder is not zero, watch out for signs
        sigX_Z_reg :=
            Mux(ex1_inReady && !ex1_sqrtOp, newBit<<(sigWidth + 1),    0.U) |
            Mux(ex1_inReady && ex1_sqrtOp,  newBit<<sigWidth, 0.U) |
            Mux(!ex1_inReady , sigX_Z_reg | bitMask, 0.U)
    }
    val rawOutValid = (ex1_cycleNum_reg === 1.U)
    sigx_wire := Mux(oddSqrt_S || evenSqrt_S, sigX_Z_reg>>1, sigX_Z_reg)

    /*------------------------------------------------------------------------
    *------------------------------------------------------------------------*/
    io.inReady := ex1_inReady && !ex1_inValid
    io.rawOutValid_div  := rawOutValid && ! sqrtOp_Z_reg && ex1_valid 
    io.rawOutValid_sqrt := rawOutValid &&   sqrtOp_Z_reg && ex1_valid
    io.roundingModeOut  := roundingMode_Z_reg
    io.invalidExc    := majorExc_Z_reg &&   isNaN_Z_reg
    io.infiniteExc   := majorExc_Z_reg && ! isNaN_Z_reg
    io.rawOut.isNaN  := isNaN_Z_reg
    io.rawOut.isInf  := isInf_Z_reg
    io.rawOut.isZero := isZero_Z_reg
    io.rawOut.sign   := sign_Z_reg
    io.rawOut.sExp   := sExp_Z_reg
    io.rawOut.sig    := sigx_wire<<1 | notZeroRem_Z_reg

}
/*----------------------------------------------------------------------------
*----------------------------------------------------------------------------*/

class
    DivSqrtRecFNToRaw_small(expWidth: Int, sigWidth: Int, options: Int)
    extends Module with RequireAsyncReset
{
    val io = IO(new Bundle {
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val inReady        = Output(Bool())
        val inValid        = Input(Bool())
        val sqrtOp         = Input(Bool())
        val a              = Input(UInt((expWidth + sigWidth + 1).W))
        val b              = Input(UInt((expWidth + sigWidth + 1).W))
        val roundingMode   = Input(UInt(3.W))
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val rawOutValid_div  = Output(Bool())
        val rawOutValid_sqrt = Output(Bool())
        val roundingModeOut  = Output(UInt(3.W))
        val invalidExc       = Output(Bool())
        val infiniteExc      = Output(Bool())
        val rawOut = Output(new RawFloat(expWidth, sigWidth + 2))
    })

    val divSqrtRawFN =
        Module(new DivSqrtRawFN_small(expWidth, sigWidth, options))

    io.inReady := divSqrtRawFN.io.inReady
    divSqrtRawFN.io.inValid      := io.inValid
    divSqrtRawFN.io.sqrtOp       := io.sqrtOp
    divSqrtRawFN.io.a            := rawFloatFromRecFN(expWidth, sigWidth, io.a)
    divSqrtRawFN.io.b            := rawFloatFromRecFN(expWidth, sigWidth, io.b)
    divSqrtRawFN.io.roundingMode := io.roundingMode

    io.rawOutValid_div  := divSqrtRawFN.io.rawOutValid_div
    io.rawOutValid_sqrt := divSqrtRawFN.io.rawOutValid_sqrt
    io.roundingModeOut  := divSqrtRawFN.io.roundingModeOut
    io.invalidExc       := divSqrtRawFN.io.invalidExc
    io.infiniteExc      := divSqrtRawFN.io.infiniteExc
    io.rawOut           := divSqrtRawFN.io.rawOut

}

/*----------------------------------------------------------------------------
*----------------------------------------------------------------------------*/

class
    DivSqrtRecFN_small_rad2(expWidth: Int, sigWidth: Int, options: Int)
    extends Module with RequireAsyncReset
{
    val io = IO(new Bundle {
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val inReady        = Output(Bool())
        val inValid        = Input(Bool())
        val sqrtOp         = Input(Bool())
        val a              = Input(UInt((expWidth + sigWidth + 1).W))
        val b              = Input(UInt((expWidth + sigWidth + 1).W))
        val roundingMode   = Input(UInt(3.W))
        val detectTininess = Input(UInt(1.W))
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val outValid_div   = Output(Bool())
        val outValid_sqrt  = Output(Bool())
        val out            = Output(UInt((expWidth + sigWidth + 1).W))
        val exceptionFlags = Output(UInt(5.W))
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val divSqrtRecFNToRaw =
        Module(new DivSqrtRecFNToRaw_small(expWidth, sigWidth, options))

    io.inReady := divSqrtRecFNToRaw.io.inReady
    divSqrtRecFNToRaw.io.inValid      := io.inValid
    divSqrtRecFNToRaw.io.sqrtOp       := io.sqrtOp
    divSqrtRecFNToRaw.io.a            := io.a
    divSqrtRecFNToRaw.io.b            := io.b
    divSqrtRecFNToRaw.io.roundingMode := io.roundingMode

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    io.outValid_div  := divSqrtRecFNToRaw.io.rawOutValid_div
    io.outValid_sqrt := divSqrtRecFNToRaw.io.rawOutValid_sqrt

    val roundRawFNToRecFN =
        Module(new RoundRawFNToRecFN(expWidth, sigWidth, 0))
    roundRawFNToRecFN.io.invalidExc   := divSqrtRecFNToRaw.io.invalidExc
    roundRawFNToRecFN.io.infiniteExc  := divSqrtRecFNToRaw.io.infiniteExc
    roundRawFNToRecFN.io.in           := divSqrtRecFNToRaw.io.rawOut
    roundRawFNToRecFN.io.roundingMode := divSqrtRecFNToRaw.io.roundingModeOut
    roundRawFNToRecFN.io.detectTininess := io.detectTininess
    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags

}

class
    DivSqrtRecFN_small(expWidth: Int, sigWidth: Int, options: Int)
    extends Module with RequireAsyncReset
{
    val io = IO(new Bundle {
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val inReady        = Output(Bool())
        val inValid        = Input(Bool())
        val sqrtOp         = Input(Bool())
        val a              = Input(UInt((expWidth + sigWidth + 1).W))
        val b              = Input(UInt((expWidth + sigWidth + 1).W))
        val roundingMode   = Input(Bool())
        val detectTininess = Input(Bool())
        /*--------------------------------------------------------------------
        *--------------------------------------------------------------------*/
        val outValid_div   = Output(Bool())
        val outValid_sqrt  = Output(Bool())
        val out            = Output(UInt((expWidth + sigWidth + 1).W))
        val exceptionFlags = Output(UInt(5.W))
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val divSqrtRecFNToRaw4 =
        Module(new DivSqrtRecFNToRaw_small(expWidth, sigWidth, options))
    //io.inReady       := divSqrtRecFNToRaw4.io.inReady
    divSqrtRecFNToRaw4.io.inValid      := io.inValid
    divSqrtRecFNToRaw4.io.sqrtOp       := io.sqrtOp
    divSqrtRecFNToRaw4.io.a            := io.a
    divSqrtRecFNToRaw4.io.b            := io.b
    divSqrtRecFNToRaw4.io.roundingMode := io.roundingMode
    //------------------------------------------------------------------------
    io.inReady       := divSqrtRecFNToRaw4.io.inReady

    val roundRawFNToRecFN =
        Module(new RoundRawFNToRecFN_reg(expWidth, sigWidth, 0))

    roundRawFNToRecFN.io.invalidExc   :=  divSqrtRecFNToRaw4.io.invalidExc
    roundRawFNToRecFN.io.infiniteExc  :=  divSqrtRecFNToRaw4.io.infiniteExc
    roundRawFNToRecFN.io.in           :=  divSqrtRecFNToRaw4.io.rawOut
    roundRawFNToRecFN.io.roundingMode :=  divSqrtRecFNToRaw4.io.roundingModeOut
    roundRawFNToRecFN.io.detectTininess := io.detectTininess

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------

    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags
    io.outValid_div  := RegNext(divSqrtRecFNToRaw4.io.rawOutValid_div)
    io.outValid_sqrt := RegNext(divSqrtRecFNToRaw4.io.rawOutValid_sqrt)

}

