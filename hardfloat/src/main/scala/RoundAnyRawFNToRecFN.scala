
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with some contributions
from Yunsup Lee and Andrew Waterman, mainly concerning testing).

Copyright 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017 The Regents of the
University of California.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice,
    this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions, and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of the University nor the names of its contributors may
    be used to endorse or promote products derived from this software without
    specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS "AS IS", AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ARE
DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

=============================================================================*/

package hardfloat

import Chisel._
import consts._
import chisel3.{withReset,RequireAsyncReset}

//----------------------------------------------------------------------------
class RoundAnyRawFNToRecFN(
        inExpWidth: Int,
        inSigWidth: Int,
        outExpWidth: Int,
        outSigWidth: Int,
        options: Int
    )
    extends chisel3.Module with RequireAsyncReset
{
    val io = IO(new Bundle {
        val invalidExc  = Bool(INPUT)   // overrides 'infiniteExc' and 'in'
        val infiniteExc = Bool(INPUT)   // overrides 'in' except for 'in.sign'
        val in = new RawFloat(inExpWidth, inSigWidth).asInput
                                        // (allowed exponent range has limits)
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)
        val out = Bits(OUTPUT, outExpWidth + outSigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val sigMSBitAlwaysZero = ((options & flRoundOpt_sigMSBitAlwaysZero) != 0)
    val effectiveInSigWidth =
        if (sigMSBitAlwaysZero) inSigWidth else inSigWidth + 1
    val neverUnderflows =
        ((options &
              (flRoundOpt_neverUnderflows | flRoundOpt_subnormsAlwaysExact)
         ) != 0) ||
            (inExpWidth < outExpWidth)
    val neverOverflows =
        ((options & flRoundOpt_neverOverflows) != 0) ||
            (inExpWidth < outExpWidth)
    val outNaNExp = BigInt(7)<<(outExpWidth - 2)
    val outInfExp = BigInt(6)<<(outExpWidth - 2)
    val outMaxFiniteExp = outInfExp - 1
    val outMinNormExp = (BigInt(1)<<(outExpWidth - 1)) + 2
    val outMinNonzeroExp = outMinNormExp - outSigWidth + 1

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundingMode_near_even   = (io.roundingMode === round_near_even)
    val roundingMode_minMag      = (io.roundingMode === round_minMag)
    val roundingMode_min         = (io.roundingMode === round_min)
    val roundingMode_max         = (io.roundingMode === round_max)
    val roundingMode_near_maxMag = (io.roundingMode === round_near_maxMag)
    val roundingMode_odd         = (io.roundingMode === round_odd)

    val roundMagUp =
        (roundingMode_min && io.in.sign) || (roundingMode_max && ! io.in.sign)

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val sAdjustedExp =
        if (inExpWidth < outExpWidth)
            (io.in.sExp +&
                 SInt((BigInt(1)<<outExpWidth) - (BigInt(1)<<inExpWidth))
            )(outExpWidth, 0).zext
        else if (inExpWidth == outExpWidth)
            io.in.sExp
        else
            io.in.sExp +&
                SInt((BigInt(1)<<outExpWidth) - (BigInt(1)<<inExpWidth))
    val adjustedSig =
        if (inSigWidth <= outSigWidth + 2)
            io.in.sig<<(outSigWidth - inSigWidth + 2)
        else
            Cat(io.in.sig(inSigWidth, inSigWidth - outSigWidth - 1),
                io.in.sig(inSigWidth - outSigWidth - 2, 0).orR
            )
    val doShiftSigDown1 =
        if (sigMSBitAlwaysZero) Bool(false) else adjustedSig(outSigWidth + 2)

    val common_expOut   = Wire(UInt(width = outExpWidth + 1))
    val common_fractOut = Wire(UInt(width = outSigWidth - 1))
    val common_overflow       = Wire(Bool())
    val common_totalUnderflow = Wire(Bool())
    val common_underflow      = Wire(Bool())
    val common_inexact        = Wire(Bool())

    if (
        neverOverflows && neverUnderflows
            && (effectiveInSigWidth <= outSigWidth)
    ) {

        //--------------------------------------------------------------------
        //--------------------------------------------------------------------
        common_expOut := sAdjustedExp(outExpWidth, 0) + doShiftSigDown1
        common_fractOut :=
            Mux(doShiftSigDown1,
                adjustedSig(outSigWidth + 1, 3),
                adjustedSig(outSigWidth, 2)
            )
        common_overflow       := Bool(false)
        common_totalUnderflow := Bool(false)
        common_underflow      := Bool(false)
        common_inexact        := Bool(false)

    } else {

        //--------------------------------------------------------------------
        //--------------------------------------------------------------------
        val roundMask =
            if (neverUnderflows)
                Cat(UInt(0, outSigWidth), doShiftSigDown1, UInt(3, 2))
            else
                Cat(lowMask(
                        sAdjustedExp(outExpWidth, 0),
                        outMinNormExp - outSigWidth - 1,
                        outMinNormExp
                    ) | doShiftSigDown1,
                    UInt(3, 2)
                )
        val shiftedRoundMask = Cat(UInt(0, 1), roundMask>>1)
        val roundPosMask = ~shiftedRoundMask & roundMask
        val roundPosBit = (adjustedSig & roundPosMask).orR
        val anyRoundExtra = (adjustedSig & shiftedRoundMask).orR
        val anyRound = roundPosBit || anyRoundExtra

        val roundIncr =
            ((roundingMode_near_even || roundingMode_near_maxMag) &&
                 roundPosBit) ||
                (roundMagUp && anyRound)
        val roundedSig =
            Mux(roundIncr,
                (((adjustedSig | roundMask)>>2) +& UInt(1)) &
                    ~Mux(roundingMode_near_even && roundPosBit &&
                             ! anyRoundExtra,
                         roundMask>>1,
                         UInt(0, outSigWidth + 2)
                     ),
                (adjustedSig & ~roundMask)>>2 |
                    Mux(roundingMode_odd && anyRound, roundPosMask>>1, UInt(0))
            )
//*** IF SIG WIDTH IS VERY NARROW, NEED TO ACCOUNT FOR ROUND-EVEN ZEROING
//***  M.S. BIT OF SUBNORMAL SIG?
        val sRoundedExp = sAdjustedExp +& (roundedSig>>outSigWidth).zext

        common_expOut := sRoundedExp(outExpWidth, 0)
        common_fractOut :=
            Mux(doShiftSigDown1,
                roundedSig(outSigWidth - 1, 1),
                roundedSig(outSigWidth - 2, 0)
            )
        common_overflow :=
            (if (neverOverflows) Bool(false) else
//*** REWRITE BASED ON BEFORE-ROUNDING EXPONENT?:
                 (sRoundedExp>>(outExpWidth - 1) >= SInt(3)))
        common_totalUnderflow :=
            (if (neverUnderflows) Bool(false) else
//*** WOULD BE GOOD ENOUGH TO USE EXPONENT BEFORE ROUNDING?:
                 (sRoundedExp < SInt(outMinNonzeroExp)))

        val unboundedRange_roundPosBit =
            Mux(doShiftSigDown1, adjustedSig(2), adjustedSig(1))
        val unboundedRange_anyRound =
            (doShiftSigDown1 && adjustedSig(2)) || adjustedSig(1, 0).orR
        val unboundedRange_roundIncr =
            ((roundingMode_near_even || roundingMode_near_maxMag) &&
                 unboundedRange_roundPosBit) ||
                (roundMagUp && unboundedRange_anyRound)
        val roundCarry =
            Mux(doShiftSigDown1,
                roundedSig(outSigWidth + 1),
                roundedSig(outSigWidth)
            )
        common_underflow :=
            (if (neverUnderflows) Bool(false) else
                 common_totalUnderflow ||
//*** IF SIG WIDTH IS VERY NARROW, NEED TO ACCOUNT FOR ROUND-EVEN ZEROING
//***  M.S. BIT OF SUBNORMAL SIG?
                     (anyRound && (sAdjustedExp>>outExpWidth <= SInt(0)) &&
                          Mux(doShiftSigDown1, roundMask(3), roundMask(2)) &&
                          ! ((io.detectTininess === tininess_afterRounding) &&
                                 ! Mux(doShiftSigDown1,
                                       roundMask(4),
                                       roundMask(3)
                                   ) &&
                                 roundCarry && roundPosBit &&
                                 unboundedRange_roundIncr)))

        common_inexact := common_totalUnderflow || anyRound
    }

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val isNaNOut = io.invalidExc || io.in.isNaN
    val notNaN_isSpecialInfOut = io.infiniteExc || io.in.isInf
    val commonCase = ! isNaNOut && ! notNaN_isSpecialInfOut && ! io.in.isZero
    val overflow  = commonCase && common_overflow
    val underflow = commonCase && common_underflow
    val inexact = overflow || (commonCase && common_inexact)

    val overflow_roundMagUp =
        roundingMode_near_even || roundingMode_near_maxMag || roundMagUp
    val pegMinNonzeroMagOut =
        commonCase && common_totalUnderflow && (roundMagUp || roundingMode_odd)
    val pegMaxFiniteMagOut = overflow && ! overflow_roundMagUp
    val notNaN_isInfOut =
        notNaN_isSpecialInfOut || (overflow && overflow_roundMagUp)

    val signOut = Mux(isNaNOut, Bool(false), io.in.sign)
    val expOut =
        (common_expOut &
             ~Mux(io.in.isZero || common_totalUnderflow,
                  UInt(BigInt(7)<<(outExpWidth - 2), outExpWidth + 1),
                  UInt(0)
              ) &
             ~Mux(pegMinNonzeroMagOut,
                  ~UInt(outMinNonzeroExp, outExpWidth + 1),
                  UInt(0)
              ) &
             ~Mux(pegMaxFiniteMagOut,
                  UInt(BigInt(1)<<(outExpWidth - 1), outExpWidth + 1),
                  UInt(0)
              ) &
             ~Mux(notNaN_isInfOut,
                  UInt(BigInt(1)<<(outExpWidth - 2), outExpWidth + 1),
                  UInt(0)
              )) |
            Mux(pegMinNonzeroMagOut,
                UInt(outMinNonzeroExp, outExpWidth + 1),
                UInt(0)
            ) |
            Mux(pegMaxFiniteMagOut,
                UInt(outMaxFiniteExp, outExpWidth + 1),
                UInt(0)
            ) |
            Mux(notNaN_isInfOut, UInt(outInfExp, outExpWidth + 1), UInt(0)) |
            Mux(isNaNOut,        UInt(outNaNExp, outExpWidth + 1), UInt(0))
    val fractOut =
        Mux(isNaNOut || io.in.isZero || common_totalUnderflow,
            Mux(isNaNOut, UInt(BigInt(1)<<(outSigWidth - 2)), UInt(0)),
            common_fractOut
        ) |
        Fill(outSigWidth - 1, pegMaxFiniteMagOut)

    io.out := Cat(signOut, expOut, fractOut)
    io.exceptionFlags :=
        Cat(io.invalidExc, io.infiniteExc, overflow, underflow, inexact)
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class RoundAnyRawFNToRecFN_reg(                         //pipelined version
        inExpWidth: Int,
        inSigWidth: Int,
        outExpWidth: Int,
        outSigWidth: Int,
        options: Int
    )
    extends Module with RequireAsyncReset
{
    val io = new Bundle {
        val invalidExc  = Bool(INPUT)   // overrides 'infiniteExc' and 'in'
        val infiniteExc = Bool(INPUT)   // overrides 'in' except for 'in.sign'
        val in = new RawFloat(inExpWidth, inSigWidth).asInput
                                        // (allowed exponent range has limits)
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)
        val out = Bits(OUTPUT, outExpWidth + outSigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
    }

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val sigMSBitAlwaysZero = ((options & flRoundOpt_sigMSBitAlwaysZero) != 0)
    val effectiveInSigWidth =
        if (sigMSBitAlwaysZero) inSigWidth else inSigWidth + 1
    val neverUnderflows =
        ((options &
              (flRoundOpt_neverUnderflows | flRoundOpt_subnormsAlwaysExact)
         ) != 0) ||
            (inExpWidth < outExpWidth)
    val neverOverflows =
        ((options & flRoundOpt_neverOverflows) != 0) ||
            (inExpWidth < outExpWidth)
    val outNaNExp = BigInt(7)<<(outExpWidth - 2)
    val outInfExp = BigInt(6)<<(outExpWidth - 2)
    val outMaxFiniteExp = outInfExp - 1
    val outMinNormExp = (BigInt(1)<<(outExpWidth - 1)) + 2
    val outMinNonzeroExp = outMinNormExp - outSigWidth + 1

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundingMode_near_even   = (io.roundingMode === round_near_even)
    val roundingMode_minMag      = (io.roundingMode === round_minMag)
    val roundingMode_min         = (io.roundingMode === round_min)
    val roundingMode_max         = (io.roundingMode === round_max)
    val roundingMode_near_maxMag = (io.roundingMode === round_near_maxMag)
    val roundingMode_odd         = (io.roundingMode === round_odd)

    val roundMagUp =
        (roundingMode_min && io.in.sign) || (roundingMode_max && ! io.in.sign)

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val sAdjustedExp =
        if (inExpWidth < outExpWidth)
            (io.in.sExp +&
                 SInt((BigInt(1)<<outExpWidth) - (BigInt(1)<<inExpWidth))
            )(outExpWidth, 0).zext
        else if (inExpWidth == outExpWidth)
            io.in.sExp
        else
            io.in.sExp +&
                SInt((BigInt(1)<<outExpWidth) - (BigInt(1)<<inExpWidth))
    val adjustedSig =
        if (inSigWidth <= outSigWidth + 2)
            io.in.sig<<(outSigWidth - inSigWidth + 2)
        else
            Cat(io.in.sig(inSigWidth, inSigWidth - outSigWidth - 1),
                io.in.sig(inSigWidth - outSigWidth - 2, 0).orR
            )
    val doShiftSigDown1 =
        if (sigMSBitAlwaysZero) Bool(false) else adjustedSig(outSigWidth + 2)

    val common_expOut   = Wire(UInt(width = outExpWidth + 1))
    val common_fractOut = Wire(UInt(width = outSigWidth - 1))
    val common_overflow       = Wire(Bool())
    val common_totalUnderflow = Wire(Bool())
    val common_underflow      = Wire(Bool())
    val common_inexact        = Wire(Bool())

    if (
        neverOverflows && neverUnderflows
            && (effectiveInSigWidth <= outSigWidth)
    ) {

        //--------------------------------------------------------------------
        //--------------------------------------------------------------------
        common_expOut := sAdjustedExp(outExpWidth, 0) + doShiftSigDown1
        common_fractOut :=
            Mux(doShiftSigDown1,
                adjustedSig(outSigWidth + 1, 3),
                adjustedSig(outSigWidth, 2)
            )
        common_overflow       := Bool(false)
        common_totalUnderflow := Bool(false)
        common_underflow      := Bool(false)
        common_inexact        := Bool(false)

    } else {

        //--------------------------------------------------------------------
        //--------------------------------------------------------------------
        val roundMask =
            if (neverUnderflows)
                Cat(UInt(0, outSigWidth), doShiftSigDown1, UInt(3, 2))
            else
                Cat(lowMask(
                        sAdjustedExp(outExpWidth, 0),
                        outMinNormExp - outSigWidth - 1,
                        outMinNormExp
                    ) | doShiftSigDown1,
                    UInt(3, 2)
                )
        val shiftedRoundMask = Cat(UInt(0, 1), roundMask>>1)
        val roundPosMask = ~shiftedRoundMask & roundMask
        val roundPosBit = (adjustedSig & roundPosMask).orR
        val anyRoundExtra = (adjustedSig & shiftedRoundMask).orR
        val anyRound = roundPosBit || anyRoundExtra

        val roundIncr =
            ((roundingMode_near_even || roundingMode_near_maxMag) &&
                 roundPosBit) ||
                (roundMagUp && anyRound)
        val roundedSig =
            Mux(roundIncr,
                (((adjustedSig | roundMask)>>2) +& UInt(1)) &
                    ~Mux(roundingMode_near_even && roundPosBit &&
                             ! anyRoundExtra,
                         roundMask>>1,
                         UInt(0, outSigWidth + 2)
                     ),
                (adjustedSig & ~roundMask)>>2 |
                    Mux(roundingMode_odd && anyRound, roundPosMask>>1, UInt(0))
            )
//*** IF SIG WIDTH IS VERY NARROW, NEED TO ACCOUNT FOR ROUND-EVEN ZEROING
//***  M.S. BIT OF SUBNORMAL SIG?
        val sRoundedExp = sAdjustedExp +& (roundedSig>>outSigWidth).zext

        common_expOut := sRoundedExp(outExpWidth, 0)
        common_fractOut :=
            Mux(doShiftSigDown1,
                roundedSig(outSigWidth - 1, 1),
                roundedSig(outSigWidth - 2, 0)
            )
        common_overflow :=
            (if (neverOverflows) Bool(false) else
//*** REWRITE BASED ON BEFORE-ROUNDING EXPONENT?:
                 (sRoundedExp>>(outExpWidth - 1) >= SInt(3)))
        common_totalUnderflow :=
            (if (neverUnderflows) Bool(false) else
//*** WOULD BE GOOD ENOUGH TO USE EXPONENT BEFORE ROUNDING?:
                 (sRoundedExp < SInt(outMinNonzeroExp)))

        val unboundedRange_roundPosBit =
            Mux(doShiftSigDown1, adjustedSig(2), adjustedSig(1))
        val unboundedRange_anyRound =
            (doShiftSigDown1 && adjustedSig(2)) || adjustedSig(1, 0).orR
        val unboundedRange_roundIncr =
            ((roundingMode_near_even || roundingMode_near_maxMag) &&
                 unboundedRange_roundPosBit) ||
                (roundMagUp && unboundedRange_anyRound)
        val roundCarry =
            Mux(doShiftSigDown1,
                roundedSig(outSigWidth + 1),
                roundedSig(outSigWidth)
            )
        common_underflow :=
            (if (neverUnderflows) Bool(false) else
                 common_totalUnderflow ||
//*** IF SIG WIDTH IS VERY NARROW, NEED TO ACCOUNT FOR ROUND-EVEN ZEROING
//***  M.S. BIT OF SUBNORMAL SIG?
                     (anyRound && (sAdjustedExp>>outExpWidth <= SInt(0)) &&
                          Mux(doShiftSigDown1, roundMask(3), roundMask(2)) &&
                          ! ((io.detectTininess === tininess_afterRounding) &&
                                 ! Mux(doShiftSigDown1,
                                       roundMask(4),
                                       roundMask(3)
                                   ) &&
                                 roundCarry && roundPosBit &&
                                 unboundedRange_roundIncr)))

        common_inexact := common_totalUnderflow || anyRound
    }

    val ex1_sign             = RegNext(io.in.sign)
    val ex1_invalidExc       = RegNext(io.invalidExc)
    val ex1_isNaN            = RegNext(io.in.isNaN)
    val ex1_infiniteExc      = RegNext(io.infiniteExc)
    val ex1_isInf            = RegNext(io.in.isInf)
    val ex1_isZero           = RegNext(io.in.isZero)
    val ex1_common_overflow  = RegNext(common_overflow)
    val ex1_common_underflow = RegNext(common_underflow)
    val ex1_common_inexact   = RegNext(common_inexact)
    val ex1_roundMagUp       = RegNext(roundMagUp)
    val ex1_roundingMode_odd = RegNext(roundingMode_odd)
    val ex1_common_fractOut  = RegNext(common_fractOut)
    val ex1_common_expOut    = RegNext(common_expOut)
    val ex1_roundingMode_near_even   = RegNext(roundingMode_near_even)
    val ex1_roundingMode_near_maxMag = RegNext(roundingMode_near_maxMag)
    val ex1_common_totalUnderflow    = RegNext(common_totalUnderflow)

    //------------------------------------------------------------------------
    val isNaNOut = ex1_invalidExc || ex1_isNaN
    val notNaN_isSpecialInfOut = ex1_infiniteExc || ex1_isInf
    val commonCase = ! isNaNOut && ! notNaN_isSpecialInfOut && ! ex1_isZero
    val overflow  = commonCase && ex1_common_overflow
    val underflow = commonCase && ex1_common_underflow
    val inexact = overflow || (commonCase && ex1_common_inexact)

    val overflow_roundMagUp =
        ex1_roundingMode_near_even || ex1_roundingMode_near_maxMag || ex1_roundMagUp
    val pegMinNonzeroMagOut =
        commonCase && ex1_common_totalUnderflow && (ex1_roundMagUp || ex1_roundingMode_odd)
    val pegMaxFiniteMagOut = overflow && ! overflow_roundMagUp
    val notNaN_isInfOut =
        notNaN_isSpecialInfOut || (overflow && overflow_roundMagUp)

    val signOut = Mux(isNaNOut, Bool(false), ex1_sign)
    val expOut =
        (ex1_common_expOut &
             ~Mux(ex1_isZero || ex1_common_totalUnderflow,
                  UInt(BigInt(7)<<(outExpWidth - 2), outExpWidth + 1),
                  UInt(0)
              ) &
             ~Mux(pegMinNonzeroMagOut,
                  ~UInt(outMinNonzeroExp, outExpWidth + 1),
                  UInt(0)
              ) &
             ~Mux(pegMaxFiniteMagOut,
                  UInt(BigInt(1)<<(outExpWidth - 1), outExpWidth + 1),
                  UInt(0)
              ) &
             ~Mux(notNaN_isInfOut,
                  UInt(BigInt(1)<<(outExpWidth - 2), outExpWidth + 1),
                  UInt(0)
              )) |
            Mux(pegMinNonzeroMagOut,
                UInt(outMinNonzeroExp, outExpWidth + 1),
                UInt(0)
            ) |
            Mux(pegMaxFiniteMagOut,
                UInt(outMaxFiniteExp, outExpWidth + 1),
                UInt(0)
            ) |
            Mux(notNaN_isInfOut, UInt(outInfExp, outExpWidth + 1), UInt(0)) |
            Mux(isNaNOut,        UInt(outNaNExp, outExpWidth + 1), UInt(0))
    val fractOut =
        Mux(isNaNOut || ex1_isZero || ex1_common_totalUnderflow,
            Mux(isNaNOut, UInt(BigInt(1)<<(outSigWidth - 2)), UInt(0)),
            ex1_common_fractOut
        ) |
        Fill(outSigWidth - 1, pegMaxFiniteMagOut)

    io.out := Cat(signOut, expOut, fractOut)
    io.exceptionFlags :=
        Cat(ex1_invalidExc, ex1_infiniteExc, overflow, underflow, inexact)
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class RoundRawFNToRecFN(expWidth: Int, sigWidth: Int, options: Int)
    extends Module with RequireAsyncReset
{
    val io = new Bundle {
        val invalidExc  = Bool(INPUT)   // overrides 'infiniteExc' and 'in'
        val infiniteExc = Bool(INPUT)   // overrides 'in' except for 'in.sign'
        val in = new RawFloat(expWidth, sigWidth + 2).asInput
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)
        val out = Bits(OUTPUT, expWidth + sigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
    }

    val roundAnyRawFNToRecFN =
        Module(
            new RoundAnyRawFNToRecFN(
                    expWidth, sigWidth + 2, expWidth, sigWidth, options))
    roundAnyRawFNToRecFN.io.invalidExc     := io.invalidExc
    roundAnyRawFNToRecFN.io.infiniteExc    := io.infiniteExc
    roundAnyRawFNToRecFN.io.in             := io.in
    roundAnyRawFNToRecFN.io.roundingMode   := io.roundingMode
    roundAnyRawFNToRecFN.io.detectTininess := io.detectTininess
    io.out            := roundAnyRawFNToRecFN.io.out
    io.exceptionFlags := roundAnyRawFNToRecFN.io.exceptionFlags
}

class RoundRawFNToRecFN_reg(expWidth: Int, sigWidth: Int, options: Int)
    extends Module with RequireAsyncReset
{
    val io = new Bundle {
        val invalidExc  = Bool(INPUT)   // overrides 'infiniteExc' and 'in'
        val infiniteExc = Bool(INPUT)   // overrides 'in' except for 'in.sign'
        val in = new RawFloat(expWidth, sigWidth + 2).asInput
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)
        val out = Bits(OUTPUT, expWidth + sigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
    }

    val roundAnyRawFNToRecFN_reg =
        Module(
            new RoundAnyRawFNToRecFN_reg(
                    expWidth, sigWidth + 2, expWidth, sigWidth, options))
    roundAnyRawFNToRecFN_reg.io.invalidExc     := io.invalidExc
    roundAnyRawFNToRecFN_reg.io.infiniteExc    := io.infiniteExc
    roundAnyRawFNToRecFN_reg.io.in             := io.in
    roundAnyRawFNToRecFN_reg.io.roundingMode   := io.roundingMode
    roundAnyRawFNToRecFN_reg.io.detectTininess := io.detectTininess
    io.out            := roundAnyRawFNToRecFN_reg.io.out
    io.exceptionFlags := roundAnyRawFNToRecFN_reg.io.exceptionFlags
}

