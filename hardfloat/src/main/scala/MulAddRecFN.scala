
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
//----------------------------------------------------------------------------

class MulAddRecFN_interIo(expWidth: Int, sigWidth: Int) extends Bundle
{
//*** ENCODE SOME OF THESE CASES IN FEWER BITS?:
    val isSigNaNAny     = Bool()
    val isNaNAOrB       = Bool()
    val isInfA          = Bool()
    val isZeroA         = Bool()
    val isInfB          = Bool()
    val isZeroB         = Bool()
    val signProd        = Bool()
    val isNaNC          = Bool()
    val isInfC          = Bool()
    val isZeroC         = Bool()
    val sExpSum         = SInt(width = expWidth + 2)
    val doSubMags       = Bool()
    val CIsDominant     = Bool()
    val CDom_CAlignDist = UInt(width = log2Up(sigWidth + 1))
    val highAlignedSigC = UInt(width = sigWidth + 2)
    val bit0AlignedSigC = UInt(width = 1)

    override def cloneType =
        new MulAddRecFN_interIo(
                expWidth, sigWidth).asInstanceOf[this.type]
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
class MulAddRecFNToRaw_preMul(expWidth: Int, sigWidth: Int) extends Module with RequireAsyncReset
{
    val io = IO(new Bundle {
        val op = Bits(INPUT, 2)
        val a = Bits(INPUT, expWidth + sigWidth + 1)
        val b = Bits(INPUT, expWidth + sigWidth + 1)
        val c = Bits(INPUT, expWidth + sigWidth + 1)
        val mulAddA = UInt(OUTPUT, sigWidth)
        val mulAddB = UInt(OUTPUT, sigWidth)
        val mulAddC = UInt(OUTPUT, sigWidth * 2)
        val toPostMul = new MulAddRecFN_interIo(expWidth, sigWidth).asOutput
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
//*** POSSIBLE TO REDUCE THIS BY 1 OR 2 BITS?  (CURRENTLY 2 BITS BETWEEN
//***  UNSHIFTED C AND PRODUCT):
    val sigSumWidth = sigWidth * 3 + 3

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val rawA = rawFloatFromRecFN(expWidth, sigWidth, io.a)
    val rawB = rawFloatFromRecFN(expWidth, sigWidth, io.b)
    val rawC = rawFloatFromRecFN(expWidth, sigWidth, io.c)

    val signProd = rawA.sign ^ rawB.sign ^ io.op(1)
//*** REVIEW THE BIAS FOR 'sExpAlignedProd':
    val sExpAlignedProd =
        rawA.sExp +& rawB.sExp + SInt(-(BigInt(1)<<expWidth) + sigWidth + 3)

    val doSubMags = signProd ^ rawC.sign ^ io.op(0)
    val sNatCAlignDist = sExpAlignedProd - rawC.sExp
    val posNatCAlignDist = sNatCAlignDist(expWidth + 1, 0)
    val isMinCAlign = rawA.isZero || rawB.isZero || (sNatCAlignDist < SInt(0))
    val CIsDominant =
        ! rawC.isZero && (isMinCAlign || (posNatCAlignDist <= UInt(sigWidth)))
    //------------------------------------------------------------------------
    // Pipeline Stage 1
    //------------------------------------------------------------------------
    val rawAS2            = Reg(next = rawA)
    val rawBS2            = Reg(next = rawB)
    val rawCS2            = Reg(next = rawC)
    val signProdS2        = Reg(next = signProd)
    val doSubMagsS2       = Reg(next = doSubMags)
    val CIsDominantS2     = Reg(next = CIsDominant)
    val sExpAlignedProdS2 = Reg(next = sExpAlignedProd)
    val posNatCAlignDistS2= Reg(next = posNatCAlignDist)
    val isMinCAlignS2     = Reg(next = isMinCAlign)

    val CAlignDist =
        Mux(isMinCAlignS2,
            UInt(0),
            Mux(posNatCAlignDistS2 < UInt(sigSumWidth - 1),
                posNatCAlignDistS2(log2Up(sigSumWidth) - 1, 0),
                UInt(sigSumWidth - 1)
            )
        )
    val mainAlignedSigC =
        Cat(Mux(doSubMagsS2, ~rawCS2.sig, rawCS2.sig),
            Fill(sigSumWidth - sigWidth + 2, doSubMagsS2)
        ).asSInt>>CAlignDist
    val reduced4CExtra =
        (orReduceBy4(rawCS2.sig<<((sigSumWidth - sigWidth - 1) & 3)) &
             lowMask(
                 CAlignDist>>2,
//*** NOT NEEDED?:
//                 (sigSumWidth + 2)>>2,
                 (sigSumWidth - 1)>>2,
                 (sigSumWidth - sigWidth - 1)>>2
             )
        ).orR
    val alignedSigC =
        Cat(mainAlignedSigC>>3,
            Mux(doSubMagsS2,
                mainAlignedSigC(2, 0).andR && ! reduced4CExtra,
                mainAlignedSigC(2, 0).orR  ||   reduced4CExtra
            )
        )

    io.mulAddA := rawA.sig  
    io.mulAddB := rawB.sig  
    io.mulAddC := alignedSigC(sigWidth * 2, 1)  
    io.toPostMul.isSigNaNAny := isSigNaNRawFloat(rawAS2) || isSigNaNRawFloat(rawBS2) || isSigNaNRawFloat(rawCS2)
    io.toPostMul.isNaNAOrB := rawAS2.isNaN || rawBS2.isNaN
    io.toPostMul.isInfA    := rawAS2.isInf
    io.toPostMul.isZeroA   := rawAS2.isZero
    io.toPostMul.isInfB    := rawBS2.isInf
    io.toPostMul.isZeroB   := rawBS2.isZero
    io.toPostMul.signProd  := signProdS2 
    io.toPostMul.isNaNC    := rawCS2.isNaN
    io.toPostMul.isInfC    := rawCS2.isInf
    io.toPostMul.isZeroC   := rawCS2.isZero
    io.toPostMul.sExpSum   :=
        Mux(CIsDominantS2, rawCS2.sExp, sExpAlignedProdS2 - SInt(sigWidth))
    io.toPostMul.doSubMags := doSubMagsS2
    io.toPostMul.CIsDominant := CIsDominantS2
    io.toPostMul.CDom_CAlignDist := CAlignDist(log2Up(sigWidth + 1) - 1, 0)
    io.toPostMul.highAlignedSigC :=
        alignedSigC(sigSumWidth - 1, sigWidth * 2 + 1)
    io.toPostMul.bit0AlignedSigC := alignedSigC(0)
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
class MulAddRecFNToRaw_postMul(expWidth: Int, sigWidth: Int) extends Module with RequireAsyncReset
{
    val io = IO(new Bundle {
        val fromPreMul = new MulAddRecFN_interIo(expWidth, sigWidth).asInput
        val mulAddResult = UInt(INPUT, sigWidth * 2 + 1)
        val roundingMode = UInt(INPUT, 3)
        val invalidExc  = Bool(OUTPUT)
        val rawOut = new RawFloat(expWidth, sigWidth + 2).asOutput
    })

    val sigSumWidth = sigWidth * 3 + 3
    val roundingMode_min = (io.roundingMode === round_min)
    val opSignC = io.fromPreMul.signProd ^ io.fromPreMul.doSubMags
    val sigSum =
        Cat(Mux(io.mulAddResult(sigWidth * 2),
                io.fromPreMul.highAlignedSigC + UInt(1),
                io.fromPreMul.highAlignedSigC
               ),
            io.mulAddResult(sigWidth * 2 - 1, 0),
            io.fromPreMul.bit0AlignedSigC
        )
    val notCDom_signSigSum = sigSum(sigWidth * 2 + 3)
    val notCDom_absSigSum  =
        Mux(notCDom_signSigSum,
            ~sigSum(sigWidth * 2 + 2, 0),
            sigSum(sigWidth * 2 + 2, 0) + io.fromPreMul.doSubMags
        )
    val notCDom_reduced2AbsSigSum = orReduceBy2(notCDom_absSigSum)
    val notCDom_normDistReduced2  = opt_countLeadingZeros(notCDom_reduced2AbsSigSum)
    val notCDom_nearNormDist = notCDom_normDistReduced2<<1

    val CDom_sExp = io.fromPreMul.sExpSum - io.fromPreMul.doSubMags.zext
    val CDom_absSigSum =
        Mux(io.fromPreMul.doSubMags,
            ~sigSum(sigSumWidth - 1, sigWidth + 1),
            Cat(UInt(0, 1),
//*** IF GAP IS REDUCED TO 1 BIT, MUST REDUCE THIS COMPONENT TO 1 BIT TOO:
                io.fromPreMul.highAlignedSigC(sigWidth + 1, sigWidth),
                sigSum(sigSumWidth - 3, sigWidth + 2)
            )
        )
    val CDom_absSigSumExtra =
        Mux(io.fromPreMul.doSubMags,
            (~sigSum(sigWidth, 1)).orR,
            sigSum(sigWidth + 1, 1).orR
        )
    val CDom_mainSig =
        (CDom_absSigSum<<io.fromPreMul.CDom_CAlignDist)(
            sigWidth * 2 + 1, sigWidth - 3)
    val CDom_reduced4SigExtra =
        (orReduceBy4(CDom_absSigSum(sigWidth - 1, 0)<<(~sigWidth & 3)) &
             lowMask(io.fromPreMul.CDom_CAlignDist>>2, 0, sigWidth>>2)).orR
    val CDom_sig =
        Cat(CDom_mainSig>>3,
            CDom_mainSig(2, 0).orR || CDom_reduced4SigExtra ||
                CDom_absSigSumExtra
        )
    //------------------------------------------------------------------------
    // Pipeline stage - 1
    //------------------------------------------------------------------------
    val notCDom_normDistReduced2S3  = Reg(UInt(0, width = log2Ceil(notCDom_reduced2AbsSigSum.getWidth))) //width knowledge before actual usage required
    val CDom_sigS3                  = Reg(next = CDom_sig)
    val fromPreMulS3                = Reg(next = io.fromPreMul)
    val CDom_signS3                 = Reg(next = opSignC)
    val CDom_sExpS3                 = Reg(next = CDom_sExp)
    val roundingMode_minS3          = Reg(next = roundingMode_min)
    val notCDom_reduced2AbsSigSumS3 = Reg(next = notCDom_reduced2AbsSigSum)
    val notCDom_absSigSumS3         = Reg(next = notCDom_absSigSum)
    val notCDom_signSigSumS3        = Reg(next = notCDom_signSigSum)
    val notCDom_nearNormDistS3      = Reg(next = notCDom_nearNormDist)
    notCDom_normDistReduced2S3     := notCDom_normDistReduced2 

    val notCDom_sExp = fromPreMulS3.sExpSum - notCDom_nearNormDistS3.zext
    val notCDom_mainSig =
        (notCDom_absSigSumS3<<notCDom_nearNormDistS3)(
            sigWidth * 2 + 3, sigWidth - 1)
    val notCDom_reduced4SigExtra =
        (orReduceBy2(
             notCDom_reduced2AbsSigSumS3(sigWidth>>1, 0)<<((sigWidth>>1) & 1)) &
             lowMask(notCDom_normDistReduced2S3>>1, 0, (sigWidth + 2)>>2)
        ).orR
    val notCDom_sig =
        Cat(notCDom_mainSig>>3,
            notCDom_mainSig(2, 0).orR || notCDom_reduced4SigExtra
        )
    val notCDom_completeCancellation =
        (notCDom_sig(sigWidth + 2, sigWidth + 1) === UInt(0))
    val notCDom_signS2 =
        Mux(notCDom_completeCancellation,
            roundingMode_minS3,
            fromPreMulS3.signProd ^ notCDom_signSigSumS3
        )  
   
    val notNaN_isInfProd = fromPreMulS3.isInfA || fromPreMulS3.isInfB
    val notNaN_isInfOut  = notNaN_isInfProd || fromPreMulS3.isInfC
    val notNaN_addZeros  =
        (fromPreMulS3.isZeroA || fromPreMulS3.isZeroB) &&
           fromPreMulS3.isZeroC   

    io.invalidExc :=
       fromPreMulS3.isSigNaNAny ||
       (fromPreMulS3.isInfA && fromPreMulS3.isZeroB) ||
       (fromPreMulS3.isZeroA && fromPreMulS3.isInfB) ||
       (! fromPreMulS3.isNaNAOrB &&
            (fromPreMulS3.isInfA || fromPreMulS3.isInfB) &&
            fromPreMulS3.isInfC &&
            fromPreMulS3.doSubMags)
    io.rawOut.isNaN := fromPreMulS3.isNaNAOrB || fromPreMulS3.isNaNC
    io.rawOut.isInf := notNaN_isInfOut
//*** IMPROVE?:
    io.rawOut.isZero :=
       notNaN_addZeros ||
           (! fromPreMulS3.CIsDominant && notCDom_completeCancellation)
    io.rawOut.sign :=
       (notNaN_isInfProd && fromPreMulS3.signProd) ||
       (fromPreMulS3.isInfC && CDom_signS3) ||
       (notNaN_addZeros && ! roundingMode_minS3 &&
           fromPreMulS3.signProd && CDom_signS3) ||
       (notNaN_addZeros && roundingMode_minS3 &&
           (fromPreMulS3.signProd || CDom_signS3)) ||
       (! notNaN_isInfOut && ! notNaN_addZeros &&
            Mux(fromPreMulS3.CIsDominant, CDom_signS3, notCDom_signS2))
    io.rawOut.sExp := Mux(fromPreMulS3.CIsDominant, CDom_sExpS3, notCDom_sExp)
    io.rawOut.sig := Mux(fromPreMulS3.CIsDominant, CDom_sigS3, notCDom_sig)
}
//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class MulAddRecFN(expWidth: Int, sigWidth: Int) extends chisel3.Module with RequireAsyncReset
{
    val io = IO(new Bundle {
        val op = Bits(INPUT, 2)
        val a = Bits(INPUT, expWidth + sigWidth + 1)
        val b = Bits(INPUT, expWidth + sigWidth + 1)
        val c = Bits(INPUT, expWidth + sigWidth + 1)
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)
        val out = Bits(OUTPUT, expWidth + sigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
    })

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val mulAddRecFNToRaw_preMul =
        Module(new MulAddRecFNToRaw_preMul(expWidth, sigWidth))
    val mulAddRecFNToRaw_postMul =
        Module(new MulAddRecFNToRaw_postMul(expWidth, sigWidth))

    mulAddRecFNToRaw_preMul.io.op := io.op
    mulAddRecFNToRaw_preMul.io.a  := io.a
    mulAddRecFNToRaw_preMul.io.b  := io.b
    mulAddRecFNToRaw_preMul.io.c  := io.c

    val mulAddResult =
        (mulAddRecFNToRaw_preMul.io.mulAddA *
             mulAddRecFNToRaw_preMul.io.mulAddB) +&
            mulAddRecFNToRaw_preMul.io.mulAddC

    mulAddRecFNToRaw_postMul.io.fromPreMul :=
        mulAddRecFNToRaw_preMul.io.toPostMul
    mulAddRecFNToRaw_postMul.io.mulAddResult := mulAddResult
    mulAddRecFNToRaw_postMul.io.roundingMode := io.roundingMode

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundRawFNToRecFN =
        Module(new RoundRawFNToRecFN(expWidth, sigWidth, 0))
    roundRawFNToRecFN.io.invalidExc   := mulAddRecFNToRaw_postMul.io.invalidExc
    roundRawFNToRecFN.io.infiniteExc  := Bool(false)
    roundRawFNToRecFN.io.in           := mulAddRecFNToRaw_postMul.io.rawOut
    roundRawFNToRecFN.io.roundingMode := io.roundingMode
    roundRawFNToRecFN.io.detectTininess := io.detectTininess
    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags
}

