
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
import Chisel.ImplicitConversions._
//----------------------------------------------------------------------------
//----------------------------------------------------------------------------

class Assemble(n: Int, wid_in: Int, wid_out: Int) {
  val in  = Wire(Bits(width = wid_in))
  val out = Wire(Bits(width = wid_out))

  when (in(n-1+n) === false) {
    out := Cat((in(n-1+n) && in(n-1)) , UInt(0) , in(2*n-2,n))
  }
  .otherwise {
    out := Cat((in(n-1+n) && in(n-1)) , !in(n-1) , in(n-2,0))
  }
}

class Encoder() {
  val in  = Wire(Bits(width = 2))
  val out = Wire(Bits(width = 2))
  when (in === UInt(0)) {out := UInt(2)}
  .elsewhen (in === UInt(1)) {out := UInt(1)}
  .elsewhen (in === UInt(2)) {out := UInt(0)}
  .otherwise {out := UInt(0)}
}

object EncTreeO
{
  // This implements PriorityEncoder, where the logic is arranged in a tree.
  // This is copied from rocket-chip repo.
  def apply(inps: UInt): UInt = {
    val (in1, v1) = EncTree(inps, 0, math.ceil(math.log10(inps.getWidth)/math.log10(2.0)).toInt)
    in1
  }
  def EncTree(inps: UInt): (UInt, Bool) = EncTree(inps, 0, math.ceil(math.log10(inps.getWidth)/math.log10(2.0)).toInt)
  def EncTree(inps: UInt, off: Int, level: Int): (UInt, Bool) = {
    if(level == 0) {
      (UInt(off, log2Up(inps.getWidth)), inps(off))
    }
    else {
      if(off+math.pow(2,level-1).toInt >= inps.getWidth) {
        EncTree(inps, off, level-1)
      }
      else {
        val (in1, v1) = EncTree(inps, off, level-1)
        val (in2, v2) = EncTree(inps, off+math.pow(2,level-1).toInt, level-1)
        (Mux(v1, in1, in2), v1 || v2)
      }
    }
  }
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object lowMask
{
    def apply(in: UInt, topBound: BigInt, bottomBound: BigInt): UInt =
    {
        require(topBound != bottomBound)
        val numInVals = BigInt(1)<<in.getWidth
        if (topBound < bottomBound) {
            lowMask(~in, numInVals - 1 - topBound, numInVals - 1 - bottomBound)
        } else if (numInVals > 64 /* Empirical */) {
            // For simulation performance, we should avoid generating
            // exteremely wide shifters, so we divide and conquer.
            // Empirically, this does not impact synthesis QoR.
            val mid = numInVals / 2
            val msb = in(in.getWidth - 1)
            val lsbs = in(in.getWidth - 2, 0)
            if (mid < topBound) {
                if (mid <= bottomBound) {
                    Mux(msb,
                        lowMask(lsbs, topBound - mid, bottomBound - mid),
                        UInt(0)
                    )
                } else {
                    Mux(msb,
                        Cat(lowMask(lsbs, topBound - mid, 0),
                            UInt((BigInt(1)<<(mid - bottomBound).toInt) - 1)
                        ),
                        lowMask(lsbs, mid, bottomBound)
                    )
                }
            } else {
                ~Mux(msb, UInt(0), ~lowMask(lsbs, topBound, bottomBound))
            }
        } else {
            val shift = SInt(BigInt(-1)<<numInVals.toInt)>>in
            Reverse(
                shift(
                    (numInVals - 1 - bottomBound).toInt,
                    (numInVals - topBound).toInt
                )
            )
        }
    }
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object countLeadingZeros
{
    def apply(in: UInt): UInt = PriorityEncoder(Reverse(in))
}

object opt_countLeadingZeros
{
    def apply(in: UInt): UInt = EncTreeO(Reverse(in))
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object orReduceBy2
{
    def apply(in: UInt): UInt =
    {
        val reducedWidth = (in.getWidth + 1)>>1
        val reducedVec = Wire(Vec(reducedWidth, Bool()))
        for (ix <- 0 until reducedWidth - 1) {
            reducedVec(ix) := in(ix * 2 + 1, ix * 2).orR
        }
        reducedVec(reducedWidth - 1) :=
            in(in.getWidth - 1, (reducedWidth - 1) * 2).orR
        reducedVec.asUInt
    }
}

//----------------------------------------------------------------------------
//----------------------------------------------------------------------------
object orReduceBy4
{
    def apply(in: UInt): UInt =
    {
        val reducedWidth = (in.getWidth + 3)>>2
        val reducedVec = Wire(Vec(reducedWidth, Bool()))
        for (ix <- 0 until reducedWidth - 1) {
            reducedVec(ix) := in(ix * 4 + 3, ix * 4).orR
        }
        reducedVec(reducedWidth - 1) :=
            in(in.getWidth - 1, (reducedWidth - 1) * 4).orR
        reducedVec.asUInt
    }
}

