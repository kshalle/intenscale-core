// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import Chisel._
import Chisel.ImplicitConversions._

import chisel3.{withReset,RequireAsyncReset}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import chisel3.internal.sourceinfo.SourceInfo
import superThread._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.Util._

case class FPUParams(
  fLen        : Int     = 64,
  divSqrt     : Boolean = true,
  sfmaLatency : Int     = 7,
  dfmaLatency : Int     = 7
)

object FPConstants
{
  val RM_SZ    = 3
  val FLAGS_SZ = 5
}
import FPConstants._

trait HasFPUCtrlSigs {
  val ldst      = Bool()
  val wen       = Bool()
  val ren1      = Bool()
  val ren2      = Bool()
  val ren3      = Bool()
  val swap12    = Bool()
  val swap23    = Bool()
  val singleIn  = Bool()
  val singleOut = Bool()
  val fromint   = Bool()
  val toint     = Bool()
  val fastpipe  = Bool()
  val fma       = Bool()
  val div       = Bool()
  val sqrt      = Bool()
  val wflags    = Bool()
}

class FPUCtrlSigs extends Bundle with HasFPUCtrlSigs

class FPUDecoder(implicit p: Parameters) extends FPUModule()(p) {
  val io = new Bundle {
    val inst = Bits(INPUT, 32)
    val sigs = new FPUCtrlSigs().asOutput
  }

  val default =       List(X,X, X,X,X, X,X, X,X,X,X,X,X,X,X,X)
  val f =
    Array(FLW      -> List(Y,Y, N,N,N, X,X, X,X,N,N,N,N,N,N,N),
          FSW      -> List(Y,N, N,Y,N, Y,X, N,Y,N,Y,N,N,N,N,N),
          FMV_S_X  -> List(N,Y, N,N,N, X,X, Y,N,Y,N,N,N,N,N,N),
          FCVT_S_W -> List(N,Y, N,N,N, X,X, Y,Y,Y,N,N,N,N,N,Y),
          FCVT_S_WU-> List(N,Y, N,N,N, X,X, Y,Y,Y,N,N,N,N,N,Y),
          FCVT_S_L -> List(N,Y, N,N,N, X,X, Y,Y,Y,N,N,N,N,N,Y),
          FCVT_S_LU-> List(N,Y, N,N,N, X,X, Y,Y,Y,N,N,N,N,N,Y),
          FMV_X_S  -> List(N,N, Y,N,N, N,X, N,Y,N,Y,N,N,N,N,N),
          FCLASS_S -> List(N,N, Y,N,N, N,X, Y,Y,N,Y,N,N,N,N,N),
          FCVT_W_S -> List(N,N, Y,N,N, N,X, Y,Y,N,Y,N,N,N,N,Y),
          FCVT_WU_S-> List(N,N, Y,N,N, N,X, Y,Y,N,Y,N,N,N,N,Y),
          FCVT_L_S -> List(N,N, Y,N,N, N,X, Y,Y,N,Y,N,N,N,N,Y),
          FCVT_LU_S-> List(N,N, Y,N,N, N,X, Y,Y,N,Y,N,N,N,N,Y),
          FEQ_S    -> List(N,N, Y,Y,N, N,N, Y,Y,N,Y,N,N,N,N,Y),
          FLT_S    -> List(N,N, Y,Y,N, N,N, Y,Y,N,Y,N,N,N,N,Y),
          FLE_S    -> List(N,N, Y,Y,N, N,N, Y,Y,N,Y,N,N,N,N,Y),
          FSGNJ_S  -> List(N,Y, Y,Y,N, N,N, Y,Y,N,N,Y,N,N,N,N),
          FSGNJN_S -> List(N,Y, Y,Y,N, N,N, Y,Y,N,N,Y,N,N,N,N),
          FSGNJX_S -> List(N,Y, Y,Y,N, N,N, Y,Y,N,N,Y,N,N,N,N),
          FMIN_S   -> List(N,Y, Y,Y,N, N,N, Y,Y,N,N,Y,N,N,N,Y),
          FMAX_S   -> List(N,Y, Y,Y,N, N,N, Y,Y,N,N,Y,N,N,N,Y),
          FADD_S   -> List(N,Y, Y,Y,N, N,Y, Y,Y,N,N,N,Y,N,N,Y),
          FSUB_S   -> List(N,Y, Y,Y,N, N,Y, Y,Y,N,N,N,Y,N,N,Y),
          FMUL_S   -> List(N,Y, Y,Y,N, N,N, Y,Y,N,N,N,Y,N,N,Y),
          FMADD_S  -> List(N,Y, Y,Y,Y, N,N, Y,Y,N,N,N,Y,N,N,Y),
          FMSUB_S  -> List(N,Y, Y,Y,Y, N,N, Y,Y,N,N,N,Y,N,N,Y),
          FNMADD_S -> List(N,Y, Y,Y,Y, N,N, Y,Y,N,N,N,Y,N,N,Y),
          FNMSUB_S -> List(N,Y, Y,Y,Y, N,N, Y,Y,N,N,N,Y,N,N,Y),
          FDIV_S   -> List(N,Y, Y,Y,N, N,N, Y,Y,N,N,N,N,Y,N,Y),
          FSQRT_S  -> List(N,Y, Y,N,N, N,X, Y,Y,N,N,N,N,N,Y,Y))
  val d =
    Array(FLD      -> List(Y,Y, N,N,N, X,X, X,N,N,N,N,N,N,N,N),
          FSD      -> List(Y,N, N,Y,N, Y,X, N,N,N,Y,N,N,N,N,N),
          FMV_D_X  -> List(N,Y, N,N,N, X,X, X,N,Y,N,N,N,N,N,N),
          FCVT_D_W -> List(N,Y, N,N,N, X,X, N,N,Y,N,N,N,N,N,Y),
          FCVT_D_WU-> List(N,Y, N,N,N, X,X, N,N,Y,N,N,N,N,N,Y),
          FCVT_D_L -> List(N,Y, N,N,N, X,X, N,N,Y,N,N,N,N,N,Y),
          FCVT_D_LU-> List(N,Y, N,N,N, X,X, N,N,Y,N,N,N,N,N,Y),
          FMV_X_D  -> List(N,N, Y,N,N, N,X, N,N,N,Y,N,N,N,N,N),
          FCLASS_D -> List(N,N, Y,N,N, N,X, N,N,N,Y,N,N,N,N,N),
          FCVT_W_D -> List(N,N, Y,N,N, N,X, N,N,N,Y,N,N,N,N,Y),
          FCVT_WU_D-> List(N,N, Y,N,N, N,X, N,N,N,Y,N,N,N,N,Y),
          FCVT_L_D -> List(N,N, Y,N,N, N,X, N,N,N,Y,N,N,N,N,Y),
          FCVT_LU_D-> List(N,N, Y,N,N, N,X, N,N,N,Y,N,N,N,N,Y),
          FCVT_S_D -> List(N,Y, Y,N,N, N,X, N,Y,N,N,Y,N,N,N,Y),
          FCVT_D_S -> List(N,Y, Y,N,N, N,X, Y,N,N,N,Y,N,N,N,Y),
          FEQ_D    -> List(N,N, Y,Y,N, N,N, N,N,N,Y,N,N,N,N,Y),
          FLT_D    -> List(N,N, Y,Y,N, N,N, N,N,N,Y,N,N,N,N,Y),
          FLE_D    -> List(N,N, Y,Y,N, N,N, N,N,N,Y,N,N,N,N,Y),
          FSGNJ_D  -> List(N,Y, Y,Y,N, N,N, N,N,N,N,Y,N,N,N,N),
          FSGNJN_D -> List(N,Y, Y,Y,N, N,N, N,N,N,N,Y,N,N,N,N),
          FSGNJX_D -> List(N,Y, Y,Y,N, N,N, N,N,N,N,Y,N,N,N,N),
          FMIN_D   -> List(N,Y, Y,Y,N, N,N, N,N,N,N,Y,N,N,N,Y),
          FMAX_D   -> List(N,Y, Y,Y,N, N,N, N,N,N,N,Y,N,N,N,Y),
          FADD_D   -> List(N,Y, Y,Y,N, N,Y, N,N,N,N,N,Y,N,N,Y),
          FSUB_D   -> List(N,Y, Y,Y,N, N,Y, N,N,N,N,N,Y,N,N,Y),
          FMUL_D   -> List(N,Y, Y,Y,N, N,N, N,N,N,N,N,Y,N,N,Y),
          FMADD_D  -> List(N,Y, Y,Y,Y, N,N, N,N,N,N,N,Y,N,N,Y),
          FMSUB_D  -> List(N,Y, Y,Y,Y, N,N, N,N,N,N,N,Y,N,N,Y),
          FNMADD_D -> List(N,Y, Y,Y,Y, N,N, N,N,N,N,N,Y,N,N,Y),
          FNMSUB_D -> List(N,Y, Y,Y,Y, N,N, N,N,N,N,N,Y,N,N,Y),
          FDIV_D   -> List(N,Y, Y,Y,N, N,N, N,N,N,N,N,N,Y,N,Y),
          FSQRT_D  -> List(N,Y, Y,N,N, N,X, N,N,N,N,N,N,N,Y,Y))

  val insns = fLen match {
    case 32 => f
    case 64 => f ++ d
  }
  val decoder = DecodeLogic(io.inst, default, insns)
  val s = io.sigs
  val sigs = Seq(s.ldst, s.wen, s.ren1, s.ren2, s.ren3, s.swap12, s.swap23,
                 s.singleIn, s.singleOut, s.fromint, s.toint,
                 s.fastpipe, s.fma, s.div, s.sqrt, s.wflags)
  sigs zip decoder map {case(s,d) => s := d}
}

class FPUCoreIO(implicit p: Parameters) extends CoreBundle()(p) {
  // TODO: MIGRATION: Connect the fcsr_flags versions correctly.
  val ex1_fcsr_rm    = Bits(INPUT, FPConstants.RM_SZ)
  val ex1_fcsr_rmF   = Bits(INPUT, FPConstants.RM_SZ)
  val fcsr_flags0    = Valid(Bits(width = FPConstants.FLAGS_SZ))
  val fcsr_ctxtId0   = Bits(OUTPUT, CTXT_ID_LEN)
  val fcsr_flags1    = Valid(Bits(width = FPConstants.FLAGS_SZ))
  val fcsr_ctxtId1   = Bits(OUTPUT, CTXT_ID_LEN)
  val fcsr_flags2    = Valid(Bits(width = FPConstants.FLAGS_SZ))
  val fcsr_ctxtId2   = Bits(OUTPUT, CTXT_ID_LEN)
  val fcsr_flags3    = Valid(Bits(width = FPConstants.FLAGS_SZ))
  val fcsr_ctxtId3   = Bits(OUTPUT, CTXT_ID_LEN)

  val mem_store_data = Vec(NUM_MPS, Bits(OUTPUT, fLen))
  val mem_toint_data = Bits(OUTPUT, xLen)

  val idc_validI       = Bool(INPUT)
  val idc_ctxtId       = UInt(INPUT, CTXT_ID_LEN)
  val idc_inst         = Bits(INPUT, INSTR_LEN)

  val idc_validF       = Bool(INPUT)
  val idc_ctxtIdF      = UInt(INPUT, CTXT_ID_LEN)
  val idc_instF        = Bits(INPUT, INSTR_LEN)

  val idc_validM       = Vec(NUM_MPS, Bool(INPUT))
  val idc_ctxtIdM      = Vec(NUM_MPS, UInt(INPUT, CTXT_ID_LEN))
  val idc_instM        = Vec(NUM_MPS, Bits(INPUT, INSTR_LEN))

  val ex2_fromint_data = Bits(INPUT, xLen)
  val mxx_isReal       = Bits(INPUT, NUM_CTXT)
  val ex2_illegal_rm   = Bool(OUTPUT)
  val dec              = new FPUCtrlSigs().asOutput
}

class FPUIO(implicit p: Parameters) extends CoreBundle ()(p) {
  val pipeFpu            = Vec(NUM_PUS, new FPUCoreIO ())
  val fpuToCtxtUnit      = Vec(NUM_PUS, new FpuToCtxtUnitBundle()).asOutput
  val ctxtToFpu          = Vec(NUM_PUS, new CtxtToFpuBundle()).asInput
  val cp_req             = Decoupled(new FPInput()).flip //cp doesn't pay attn to kill sigs
  val cp_resp            = Decoupled(new FPResult()) //TODO: To be connected when RoCC is enabled.
  val dataUnitToFpu      = Vec(NUM_PUS, Decoupled(new WriteToRegfsetBundle())).flip()
  val fpuPending         = Vec(NUM_PUS, Bool(INPUT))
  val unCacheFpuNotif    = Bool(INPUT)
  val earlyFpuWrFlag     = Vec(NUM_PUS, Bool(INPUT))
  val earlyCtxtFpuWr     = Vec(NUM_PUS, UInt(INPUT, CTXT_ID_LEN))
}

class FPResult(implicit p: Parameters) extends CoreBundle()(p) {
  val data = Bits(width = fLen+1)
  val exc = Bits(width = FPConstants.FLAGS_SZ)
}

class IntToFPInput(implicit p: Parameters) extends CoreBundle()(p) with HasFPUCtrlSigs {
  val rm = Bits(width = FPConstants.RM_SZ)
  val typ = Bits(width = 2)
  val in1 = Bits(width = xLen)
}

class FPInput(implicit p: Parameters) extends CoreBundle()(p) with HasFPUCtrlSigs {
  val rm = Bits(width = FPConstants.RM_SZ)
  val fmaCmd = Bits(width = 2)
  val typ = Bits(width = 2)
  val in1 = Bits(width = fLen+1)
  val in2 = Bits(width = fLen+1)
  val in3 = Bits(width = fLen+1)

  override def cloneType = new FPInput().asInstanceOf[this.type]
}

case class FType(exp: Int, sig: Int) {
  def ieeeWidth = exp + sig
  def recodedWidth = ieeeWidth + 1

  def qNaN = UInt((BigInt(7) << (exp + sig - 3)) + (BigInt(1) << (sig - 2)), exp + sig + 1)
  def isNaN(x: UInt) = x(sig + exp - 1, sig + exp - 3).andR
  def isSNaN(x: UInt) = isNaN(x) && !x(sig - 2)

  def classify(x: UInt) = {
    val sign = x(sig + exp)
    val code = x(exp + sig - 1, exp + sig - 3)
    val codeHi = code(2, 1)
    val isSpecial = codeHi === UInt(3)

    val isHighSubnormalIn = x(exp + sig - 3, sig - 1) < UInt(2)
    val isSubnormal = code === UInt(1) || codeHi === UInt(1) && isHighSubnormalIn
    val isNormal = codeHi === UInt(1) && !isHighSubnormalIn || codeHi === UInt(2)
    val isZero = code === UInt(0)
    val isInf = isSpecial && !code(0)
    val isNaN = code.andR
    val isSNaN = isNaN && !x(sig-2)
    val isQNaN = isNaN && x(sig-2)

    Cat(isQNaN, isSNaN, isInf && !sign, isNormal && !sign,
        isSubnormal && !sign, isZero && !sign, isZero && sign,
        isSubnormal && sign, isNormal && sign, isInf && sign)
  }

  // convert between formats, ignoring rounding, range, NaN
  def unsafeConvert(x: UInt, to: FType) = if (this == to) x else {
    val sign = x(sig + exp)
    val fractIn = x(sig - 2, 0)
    val expIn = x(sig + exp - 1, sig - 1)
    val fractOut = fractIn << to.sig >> sig
    val expOut = {
      val expCode = expIn(exp, exp - 2)
      val commonCase = (expIn + (1 << to.exp)) - (1 << exp)
      Mux(expCode === 0 || expCode >= 6, Cat(expCode, commonCase(to.exp - 3, 0)), commonCase(to.exp, 0))
    }
    Cat(sign, expOut, fractOut)
  }

  def recode(x: UInt) = hardfloat.recFNFromFN(exp, sig, x)
  def ieee(x: UInt) = hardfloat.fNFromRecFN(exp, sig, x)
}

object FType {
  val S = new FType(8, 24)
  val D = new FType(11, 53)

  val all = List(S, D)
}

trait HasFPUParameters {
  require(fLen == 32 || fLen == 64)
  val fLen: Int
  def xLen: Int
  val minXLen = 32
  val nIntTypes = log2Ceil(xLen/minXLen) + 1
  val floatTypes = FType.all.filter(_.ieeeWidth <= fLen)
  val minType = floatTypes.head
  val maxType = floatTypes.last
  def prevType(t: FType) = floatTypes(typeTag(t) - 1)
  val maxExpWidth = maxType.exp
  val maxSigWidth = maxType.sig
  def typeTag(t: FType) = floatTypes.indexOf(t)

  private def isBox(x: UInt, t: FType): Bool = x(t.sig + t.exp, t.sig + t.exp - 4).andR

  private def box(x: UInt, xt: FType, y: UInt, yt: FType): UInt = {
    require(xt.ieeeWidth == 2 * yt.ieeeWidth)
    val swizzledNaN = Cat(
      x(xt.sig + xt.exp, xt.sig + xt.exp - 3),
      x(xt.sig - 2, yt.recodedWidth - 1).andR,
      x(xt.sig + xt.exp - 5, xt.sig),
      y(yt.recodedWidth - 2),
      x(xt.sig - 2, yt.recodedWidth - 1),
      y(yt.recodedWidth - 1),
      y(yt.recodedWidth - 3, 0))
    Mux(xt.isNaN(x), swizzledNaN, x)
  }

  // implement NaN unboxing for FU inputs
  def unbox(x: UInt, tag: UInt, exactType: Option[FType]): UInt = {
    val outType = exactType.getOrElse(maxType)
    def helper(x: UInt, t: FType): Seq[(Bool, UInt)] = {
      val prev =
        if (t == minType) {
          Seq()
        } else {
          val prevT = prevType(t)
          val unswizzled = Cat(
            x(prevT.sig + prevT.exp - 1),
            x(t.sig - 1),
            x(prevT.sig + prevT.exp - 2, 0))
          val prev = helper(unswizzled, prevT)
          val isbox = isBox(x, t)
          prev.map(p => (isbox && p._1, p._2))
        }
      prev :+ (true.B, t.unsafeConvert(x, outType))
    }

    val (oks, floats) = helper(x, maxType).unzip
    if (exactType.isEmpty || floatTypes.size == 1) {
      Mux(oks(tag), floats(tag), maxType.qNaN)
    } else {
      val t = exactType.get
      floats(typeTag(t)) | Mux(oks(typeTag(t)), 0.U, t.qNaN)
    }
  }

  // make sure that the redundant bits in the NaN-boxed encoding are consistent
  def consistent(x: UInt): Bool = {
    def helper(x: UInt, t: FType): Bool = if (typeTag(t) == 0) true.B else {
      val prevT = prevType(t)
      val unswizzled = Cat(
        x(prevT.sig + prevT.exp - 1),
        x(t.sig - 1),
        x(prevT.sig + prevT.exp - 2, 0))
      val prevOK = !isBox(x, t) || helper(unswizzled, prevT)
      val curOK = !t.isNaN(x) || x(t.sig + t.exp - 4) === x(t.sig - 2, prevT.recodedWidth - 1).andR
      prevOK && curOK
    }
    helper(x, maxType)
  }

  // generate a NaN box from an FU result
  def box(x: UInt, t: FType): UInt = {
    if (t == maxType) {
      x
    } else {
      val nt = floatTypes(typeTag(t) + 1)
      val bigger = box(UInt((BigInt(1) << nt.recodedWidth)-1), nt, x, t)
      bigger | UInt((BigInt(1) << maxType.recodedWidth) - (BigInt(1) << nt.recodedWidth))
    }
  }

  // generate a NaN box from an FU result
  def box(x: UInt, tag: UInt): UInt = {
    val opts = floatTypes.map(t => box(x, t))
    opts(tag)
  }

  // zap bits that hardfloat thinks are don't-cares, but we do care about
  def sanitizeNaN(x: UInt, t: FType): UInt = {
    if (typeTag(t) == 0) {
      x
    } else {
      val maskedNaN = x & ~UInt((BigInt(1) << (t.sig-1)) | (BigInt(1) << (t.sig+t.exp-4)), t.recodedWidth)
      Mux(t.isNaN(x), maskedNaN, x)
    }
  }

  // implement NaN boxing and recoding for FL*/fmv.*.x
  def recode(x: UInt, tag: UInt): UInt = {
    def helper(x: UInt, t: FType): UInt = {
      if (typeTag(t) == 0) {
        t.recode(x)
      } else {
        val prevT = prevType(t)
        box(t.recode(x), t, helper(x, prevT), prevT)
      }
    }

    // fill MSBs of subword loads to emulate a wider load of a NaN-boxed value
    val boxes = floatTypes.map(t => UInt((BigInt(1) << maxType.ieeeWidth) - (BigInt(1) << t.ieeeWidth)))
    helper(boxes(tag) | x, maxType)
  }

  // implement NaN unboxing and un-recoding for FS*/fmv.x.*
  def ieee(x: UInt, t: FType = maxType): UInt = {
    if (typeTag(t) == 0) {
      t.ieee(x)
    } else {
      val unrecoded = t.ieee(x)
      val prevT = prevType(t)
      val prevRecoded = Cat(
        x(prevT.recodedWidth-2),
        x(t.sig-1),
        x(prevT.recodedWidth-3, 0))
      val prevUnrecoded = ieee(prevRecoded, prevT)
      Cat(unrecoded >> prevT.ieeeWidth, Mux(t.isNaN(x), prevUnrecoded, unrecoded(prevT.ieeeWidth-1, 0)))
    }
  }
}

abstract class FPUModule(implicit p: Parameters) extends CoreModule()(p) with HasFPUParameters

class FPToInt(implicit p: Parameters) extends FPUModule()(p) with ShouldBeRetimed {
  class Output extends Bundle {
    val in    = new FPInput
    val lt    = Bool()
    val store = Bits(width = fLen)
    val toint = Bits(width = xLen)
    val exc   = Bits(width = FPConstants.FLAGS_SZ)
    override def cloneType = new Output().asInstanceOf[this.type]
  }
  val io = new Bundle {
    val in  = Valid(new FPInput).flip
    val out = Valid(new Output)
  }
  withReset(reset.asAsyncReset){
  val validD1_reg = Reg(next=io.in.valid, init=Bool(false))
  val inD1_reg    = RegEnable(io.in.bits, io.in.valid)
  val cvtTypeD1   = inD1_reg.typ.extract(log2Ceil(nIntTypes), 1)
  val tagD1       = !inD1_reg.singleOut // TODO typeTag

  val validD2_reg = Reg(next=validD1_reg, init=Bool(false))
  val inD2_reg    = RegEnable(inD1_reg, validD1_reg)
  val cvtTypeD2   = inD2_reg.typ.extract(log2Ceil(nIntTypes), 1)
  val tagD2       = !inD2_reg.singleOut // TODO typeTag

  val dcmp           = Module(new hardfloat.CompareRecFN(maxExpWidth, maxSigWidth))
  dcmp.io.a         := inD1_reg.in1
  dcmp.io.b         := inD1_reg.in2
  dcmp.io.signaling := !inD1_reg.rm(1)
  val dcmpLtD2_reg   = RegEnable(dcmp.io.lt, validD1_reg)
  val dcmpEqD2_reg   = RegEnable(dcmp.io.eq, validD1_reg)
  val dcmpExpD2_reg  = RegEnable(dcmp.io.exceptionFlags, validD1_reg)

  val conv = Module(new hardfloat.RecFNToIN_reg(maxExpWidth, maxSigWidth, xLen))
  conv.io.in                       := inD1_reg.in1
  conv.io.roundingMode             := inD1_reg.rm
  conv.io.signedOut                := ~inD1_reg.typ(0)
  val convIntExceptionFlagsD2       = conv.io.intExceptionFlags
  val convOutD2                     = conv.io.out

  val storeD2_reg       = RegEnable(ieee(inD1_reg.in1), validD1_reg)

  val narrowIntExceptionFlagsD2_reg = Reg(init=Bool(false))
  for (i <- 0 until nIntTypes-1) {
    val w = minXLen << i
    val narrow = Module(new hardfloat.RecFNToIN(maxExpWidth, maxSigWidth, w))
    narrow.io.in           := inD1_reg.in1
    narrow.io.roundingMode := inD1_reg.rm
    narrow.io.signedOut    := ~inD1_reg.typ(0)
    when (cvtTypeD1 === i) {
      narrowIntExceptionFlagsD2_reg := narrow.io.intExceptionFlags(1)
    }
  }

  val excSignD2_reg   = RegEnable(inD1_reg.in1(maxExpWidth + maxSigWidth) && !maxType.isNaN(inD1_reg.in1), validD1_reg)
  val classifyD2_reg  = RegEnable((floatTypes.map(t => t.classify(maxType.unsafeConvert(inD1_reg.in1, t))): Seq[UInt])(tagD1), validD1_reg)
  val inLtD2_reg      = RegEnable((inD1_reg.in1.asSInt < 0.S && inD1_reg.in2.asSInt >= 0.S), validD1_reg)

  val toint          = Wire(init = storeD2_reg)
  val intType        = Wire(init = tagD2)
  val exc            = Wire(init = UInt(0, io.out.bits.exc.getWidth))

  when (inD2_reg.rm(0)) {
    toint            := classifyD2_reg | (storeD2_reg >> minXLen << minXLen)
    intType          := 0
  }

  when (inD2_reg.wflags) { // feq/flt/fle, fcvt
    toint   := (~inD2_reg.rm & Cat(dcmpLtD2_reg, dcmpEqD2_reg)).orR | (storeD2_reg >> minXLen << minXLen)
    exc     := dcmpExpD2_reg
    intType := 0

    when (!inD2_reg.ren2) { // fcvt
      intType       := cvtTypeD2

      toint                := convOutD2
      exc                  := Cat(convIntExceptionFlagsD2(2, 1).orR, UInt(0, 3), convIntExceptionFlagsD2(0))

      for (i <- 0 until nIntTypes-1) {
        val w = minXLen << i
        when (cvtTypeD2 === i) {
          val excOut    = Cat((~inD2_reg.typ(0)) === excSignD2_reg, Fill(w-1, !excSignD2_reg))
          val invalid   = convIntExceptionFlagsD2(2) || narrowIntExceptionFlagsD2_reg
          when (invalid) {
            toint := Cat(convOutD2 >> w, excOut)
          }
          exc   := Cat(invalid, UInt(0, 3), !invalid && convIntExceptionFlagsD2(0))
        }
      }
    }
  }

  io.out.bits.store := (floatTypes.map(t => Fill(maxType.ieeeWidth / t.ieeeWidth, storeD2_reg(t.ieeeWidth - 1, 0))): Seq[UInt])(tagD2)
  io.out.bits.toint := ((0 until nIntTypes).map(i => toint((minXLen << i) - 1, 0).sextTo(xLen)): Seq[UInt])(intType)
  io.out.bits.exc   := exc

  io.out.valid   := validD2_reg
  io.out.bits.lt := dcmpLtD2_reg || inLtD2_reg
  io.out.bits.in := inD2_reg
  }
}

class IntToFP(val latency: Int)(implicit p: Parameters) extends FPUModule()(p) with ShouldBeRetimed {
  val io = new Bundle {
    val in = Valid(new IntToFPInput).flip
    val out = Valid(new FPResult)
  }
  withReset(reset.asAsyncReset){
  val in = Pipe(io.in)
  val tag = !in.bits.singleIn // TODO typeTag

  val ex1_valid_reg     = Reg(next = in.valid, init=Bool(false))
  val ex1_intValue_reg  = Reg(init = 0.U)            //intermediate register for INToRecFn input
  val ex1_in_typ_reg    = Reg(Bits(width = 1))
  val ex1_in_rm_reg     = Reg(Bits(width = FPConstants.RM_SZ))
  val ex1_in_wflags_reg = Reg(init=Bool(false))
  val ex1_tag_reg       = Reg(init=Bool(false))
  val ex1_mux_reg       = Reg(new FPResult)

  val ex2_valid_reg     = Reg(next = ex1_valid_reg, init=Bool(false))
  val ex2_tag_reg       = Reg(init=Bool(false))
  val ex2_in_wflags_reg = Reg(init=Bool(false))
  val ex2_mux_reg       = Reg(new FPResult)

  val ex3_tag_reg       = Reg(init=Bool(false))
  val ex3_in_wflags_reg = Reg(init=Bool(false))
  val ex3_mux_reg       = Reg(new FPResult)
  val ex3_valid_reg     = Reg(next = ex2_valid_reg, init=Bool(false))

  val mux = Wire(new FPResult)
  mux.exc := Bits(0)
  mux.data := recode(in.bits.in1, !in.bits.singleIn)

  val intValue = {
    val res = Wire(init = in.bits.in1.asSInt)
    for (i <- 0 until nIntTypes-1) {
      val smallInt = in.bits.in1((minXLen << i) - 1, 0)
      when (in.bits.typ.extract(log2Ceil(nIntTypes), 1) === i) {
        res := Mux(in.bits.typ(0), smallInt.zext, smallInt.asSInt)
      }
    }
    res.asUInt
  }

  when (in.valid) {                               //1st cycle
    ex1_intValue_reg  := intValue
    ex1_in_typ_reg    := in.bits.typ(0)
    ex1_in_rm_reg     := in.bits.rm
    ex1_in_wflags_reg := in.bits.wflags
    ex1_tag_reg       := tag
    ex1_mux_reg       := mux
  }

  // could be improved for RVD/RVQ with a single variable-position rounding
  // unit, rather than N fixed-position ones
  val ex3_i2fResults = for (t <- floatTypes) yield {
    val i2f = Module(new hardfloat.INToRecFNReg(xLen, t.exp, t.sig))
    i2f.io.signedIn       := ~ex1_in_typ_reg
    i2f.io.in             := ex1_intValue_reg
    i2f.io.roundingMode   := ex1_in_rm_reg
    i2f.io.detectTininess := hardfloat.consts.tininess_afterRounding
    (sanitizeNaN(i2f.io.out, t), i2f.io.exceptionFlags)
  }

  when (ex1_valid_reg) {                        //2nd cycle inside INToRecFN
    ex2_tag_reg       := ex1_tag_reg
    ex2_in_wflags_reg := ex1_in_wflags_reg
    ex2_mux_reg       := ex1_mux_reg
  }

  when (ex2_valid_reg) {                       //3rd cycle inside INToRecFN
    ex3_tag_reg       := ex2_tag_reg
    ex3_in_wflags_reg := ex2_in_wflags_reg
    ex3_mux_reg       := ex2_mux_reg
  }

  val ex3_mux  = Wire(new FPResult)
  ex3_mux     := ex3_mux_reg

  when (ex3_in_wflags_reg) { // All the logic below is combinational.
    val (data, exc)   = ex3_i2fResults.unzip
    val dataPadded    = data.init.map(d => Cat(data.last >> d.getWidth, d)) :+ data.last
    ex3_mux.data     := dataPadded(ex3_tag_reg)
    ex3_mux.exc      := exc(ex3_tag_reg)
  }
  io.out <> Pipe(ex3_valid_reg, ex3_mux, latency-4)
  }
}

class FPToFP(val latency: Int)(implicit p: Parameters) extends FPUModule()(p) with ShouldBeRetimed {
  val io = new Bundle {
    val in  = Valid(new FPInput).flip
    val out = Valid(new FPResult)
    val lt  = Bool(INPUT) // from FPToInt
  }
  withReset(reset.asAsyncReset){
  val in          = Pipe(io.in)
  val validD1_reg = Reg(next = in.valid)

  val signNum = Mux(in.bits.rm(1), in.bits.in1 ^ in.bits.in2, Mux(in.bits.rm(0), ~in.bits.in2, in.bits.in2))
  val fsgnj   = Cat(signNum(fLen), in.bits.in1(fLen-1, 0))

  val fsgnjMux = Wire(new FPResult)
  fsgnjMux.exc  := UInt(0)
  fsgnjMux.data := fsgnj

  when (in.bits.wflags) { // fmin/fmax
    val isnan1 = maxType.isNaN(in.bits.in1)
    val isnan2 = maxType.isNaN(in.bits.in2)
    val isInvalid = maxType.isSNaN(in.bits.in1) || maxType.isSNaN(in.bits.in2)
    val isNaNOut = isnan1 && isnan2
    val isLHS = isnan2 || in.bits.rm(0) =/= io.lt && !isnan1
    fsgnjMux.exc := isInvalid << 4
    fsgnjMux.data := Mux(isNaNOut, maxType.qNaN, Mux(isLHS, in.bits.in1, in.bits.in2))
  }

  if (floatTypes.size > 1) {
    when (in.bits.wflags && !in.bits.ren2) { // fcvt
      // widening conversions simply canonicalize NaN operands
      val widened   = Mux(maxType.isNaN(in.bits.in1), maxType.qNaN, in.bits.in1)
      fsgnjMux.data := widened
      fsgnjMux.exc  := maxType.isSNaN(in.bits.in1) << 4
    }
  }

  val inTagD1_reg        = Reg(next=(!in.bits.singleIn))  // TODO typeTag
  val outTagD1_reg       = Reg(next=(!in.bits.singleOut)) // TODO typeTag
  val fsgnjMuxD1_reg     = RegEnable(fsgnjMux, in.valid)
  val narrowEnaD1_reg    = RegEnable(in.bits.wflags && !in.bits.ren2, in.valid)

  val muxD1    = Wire(init = fsgnjMuxD1_reg)
  for (t <- floatTypes.init) {
    when (outTagD1_reg === typeTag(t)) {
      muxD1.data := Cat(fsgnjMuxD1_reg.data >> t.recodedWidth, maxType.unsafeConvert(fsgnjMuxD1_reg.data, t))
    }
  }

  if (floatTypes.size > 1) {
    for (outType <- floatTypes.init) {
      // narrowing conversions require rounding (for RVQ, this could be
      // optimized to use a single variable-position rounding unit, rather
      // than two fixed-position ones)
      val narrower = Module(new hardfloat.RecFNToRecFN_reg(maxType.exp, maxType.sig, outType.exp, outType.sig))
      narrower.io.in             := in.bits.in1
      narrower.io.roundingMode   := in.bits.rm
      narrower.io.detectTininess := hardfloat.consts.tininess_afterRounding

      when (narrowEnaD1_reg && (outTagD1_reg === typeTag(outType)) && (typeTag(outType) == 0 || outTagD1_reg < inTagD1_reg)) {
        val narrowed   = sanitizeNaN(narrower.io.out, outType)
        muxD1.data    := Cat(fsgnjMuxD1_reg.data >> narrowed.getWidth, narrowed)
        muxD1.exc     := narrower.io.exceptionFlags
      }
    }
  }

  io.out <> Pipe(validD1_reg, muxD1, latency-2)
  }
}

class MulAddRecFNPipe(latency: Int, expWidth: Int, sigWidth: Int) extends Module
{
    val io = new Bundle {
        val validin = Bool(INPUT)
        val op = Bits(INPUT, 2)
        val a = Bits(INPUT, expWidth + sigWidth + 1)
        val b = Bits(INPUT, expWidth + sigWidth + 1)
        val c = Bits(INPUT, expWidth + sigWidth + 1)
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)
        val out = Bits(OUTPUT, expWidth + sigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
        val validout = Bool(OUTPUT)
    }

    withReset(reset.asAsyncReset){
    val mulAddRecFNToRaw_preMul = Module(new hardfloat.MulAddRecFNToRaw_preMul(expWidth, sigWidth))
    mulAddRecFNToRaw_preMul.io.op := io.op
    mulAddRecFNToRaw_preMul.io.a  := io.a
    mulAddRecFNToRaw_preMul.io.b  := io.b
    mulAddRecFNToRaw_preMul.io.c  := io.c

    val ex1_latency : Int = 3  //Stage 1 takes 3 cycles
    val ex2_latency : Int = 2  //Stage 2 takes 2 cycles

    //stage1 is after going through pipeline stages of premul
    val ex1_valid          = Wire(Bool())
    val ex1_roundingMode   = Wire(UInt(width=3))
    val ex1_detectTininess = Wire(UInt(width=1))

    //stage2 is after going through pipeline stages of postmul
    val ex2_valid          = Wire(Bool())
    val ex2_roundingMode   = Wire(UInt(width=3))
    val ex2_detectTininess = Wire(UInt(width=1))

    ex1_roundingMode    := Pipe(io.validin, io.roundingMode, ex1_latency).bits
    ex1_valid           := Pipe(io.validin, false.B, ex1_latency).valid
    ex1_detectTininess  := Pipe(io.validin, io.detectTininess, ex1_latency).bits

    val ex1_mulAddC_reg     = Reg(next = mulAddRecFNToRaw_preMul.io.mulAddC)
    val ex1_1_toPostMul_reg = Reg(next = mulAddRecFNToRaw_preMul.io.toPostMul)
    val ex1_2_toPostMul_reg = Reg(next = ex1_1_toPostMul_reg)

    val fpm              = Module(new hardfloat.FMA(sigWidth))       
    fpm.io.operandA      := mulAddRecFNToRaw_preMul.io.mulAddA       
    fpm.io.operandB      := mulAddRecFNToRaw_preMul.io.mulAddB       

    val mulAddResult_reg = Reg(next=fpm.io.result +& ex1_mulAddC_reg)

    val mulAddRecFNToRaw_postMul = Module(new hardfloat.MulAddRecFNToRaw_postMul(expWidth, sigWidth))
    mulAddRecFNToRaw_postMul.io.fromPreMul   := ex1_2_toPostMul_reg
    mulAddRecFNToRaw_postMul.io.mulAddResult := mulAddResult_reg
    mulAddRecFNToRaw_postMul.io.roundingMode := ex1_roundingMode

    ex2_valid           := Pipe(ex1_valid, false.B, ex2_latency).valid
    ex2_roundingMode    := Pipe(ex1_valid, ex1_roundingMode, ex2_latency).bits
    ex2_detectTininess  := Pipe(ex1_valid, ex1_detectTininess, ex2_latency).bits

    val postmul_invalidExc_reg = Reg(next = mulAddRecFNToRaw_postMul.io.invalidExc)
    val postmul_rawOut_reg     = Reg(next = mulAddRecFNToRaw_postMul.io.rawOut)

    val roundRawFNToRecFN = Module(new hardfloat.RoundRawFNToRecFN_reg(expWidth, sigWidth, 0))
    roundRawFNToRecFN.io.invalidExc         := postmul_invalidExc_reg
    roundRawFNToRecFN.io.in                 := postmul_rawOut_reg
    roundRawFNToRecFN.io.roundingMode       := ex2_roundingMode
    roundRawFNToRecFN.io.detectTininess     := ex2_detectTininess
    roundRawFNToRecFN.io.infiniteExc        := Bool(false)

    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags
    io.validout       := Pipe(ex2_valid, false.B, 1).valid   //1 cycle for the compensation of register stage
    }
}                                                            //inside roundRawFNToRecFN

class FPUFMAPipe(val latency: Int, val t: FType)
                (implicit p: Parameters) extends FPUModule()(p) with ShouldBeRetimed {
  require(latency==7)

  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val out = Valid(new FPResult)
  }

  val valid = Reg(next=io.in.valid)
  val in = Reg(new FPInput)
  when (io.in.valid) {
    val one = UInt(1) << (t.sig + t.exp - 1)
    val zero = (io.in.bits.in1 ^ io.in.bits.in2) & (UInt(1) << (t.sig + t.exp))
    val cmd_fma = io.in.bits.ren3
    val cmd_addsub = io.in.bits.swap23
    in := io.in.bits
    when (cmd_addsub) {
      in.in2 := one
      in.in3 := io.in.bits.in2
    }
    when (!(cmd_fma || cmd_addsub)) {
      in.in3 := zero
    }
  }

  val fma = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  fma.io.validin := valid
  fma.io.op := in.fmaCmd
  fma.io.roundingMode := in.rm
  fma.io.detectTininess := hardfloat.consts.tininess_afterRounding
  fma.io.a := in.in1
  fma.io.b := in.in2
  fma.io.c := in.in3

  val res = Wire(new FPResult)
  res.data := sanitizeNaN(fma.io.out, t)
  res.exc := fma.io.exceptionFlags

  io.out := Pipe(fma.io.validout, res, (latency-8) max 0) //Change (latency - x), x-1 is # of pipeline stages
}

class FPUMemory() extends Module {
  val io = new Bundle {
    val n_wr    = Bool(INPUT) // Active low write enable.
    val n_rd1   = Bool(INPUT) // Active low read enable.
    val n_rd2   = Bool(INPUT) // Active low read enable.
    val n_rd3   = Bool(INPUT) // Active low read enable.
    val rdAddr1 = UInt(INPUT, log2Up(NUM_ROW_REGS))
    val rdAddr2 = UInt(INPUT, log2Up(NUM_ROW_REGS))
    val rdAddr3 = UInt(INPUT, log2Up(NUM_ROW_REGS))
    val wrAddr  = UInt(INPUT, log2Up(NUM_ROW_REGS))
    val wrData  = UInt(INPUT, 65)
    val rdData1 = UInt(OUTPUT, 65)
    val rdData2 = UInt(OUTPUT, 65)
    val rdData3 = UInt(OUTPUT, 65)
  }

  val BANK_BLOCK    = Mem(NUM_ROW_REGS, Bits(width=65))
  val rdData1_reg   = Reg(init=UInt(0, 65))
  val rdData2_reg   = Reg(init=UInt(0, 65))
  val rdData3_reg   = Reg(init=UInt(0, 65))

  when(Reg(next=(!io.n_wr), init=Bool(false))) {
    BANK_BLOCK.write(Reg(next=io.wrAddr), Reg(next=io.wrData))
  }

  // The following code asserts that we do not receive any two successive RD/WR operations.
  val n_wrD1_reg    = Reg(next=io.n_wr, init=Bool(true))
  val n_rd1D1_reg   = Reg(next=io.n_rd1, init=Bool(true))
  val rdAddr1D1_reg = Reg(next=io.rdAddr1, init=UInt(0))
  val n_rd2D1_reg   = Reg(next=io.n_rd2, init=Bool(true))
  val rdAddr2D1_reg = Reg(next=io.rdAddr2, init=UInt(0))
  val n_rd3D1_reg   = Reg(next=io.n_rd3, init=Bool(true))
  val rdAddr3D1_reg = Reg(next=io.rdAddr3, init=UInt(0))
  val wrAddrD1_reg  = Reg(next=io.wrAddr, init=UInt(0))

  if(USE_TWO_CYCLES_RF) {
    assert(!(!io.n_wr && !io.n_rd1 && (io.rdAddr1 === io.wrAddr)), "Access same address by RD and WR(1A).")
    assert(!(!io.n_wr && !n_rd1D1_reg && (io.wrAddr === rdAddr1D1_reg)), "Access same address by RD and WR(2A).")
    assert(!(!io.n_rd1 && !n_wrD1_reg && (io.rdAddr1 === wrAddrD1_reg)), "Access same address by RD and WR(3A).")
    assert(!(!io.n_wr && !io.n_rd2 && (io.rdAddr2 === io.wrAddr)), "Access same address by RD and WR(1B).")
    assert(!(!io.n_wr && !n_rd2D1_reg && (io.wrAddr === rdAddr2D1_reg)), "Access same address by RD and WR(2B).")
    assert(!(!io.n_rd2 && !n_wrD1_reg && (io.rdAddr2 === wrAddrD1_reg)), "Access same address by RD and WR(3B).")
    assert(!(!io.n_wr && !io.n_rd3 && (io.rdAddr3 === io.wrAddr)), "Access same address by RD and WR(1C).")
    assert(!(!io.n_wr && !n_rd3D1_reg && (io.wrAddr === rdAddr3D1_reg)), "Access same address by RD and WR(2C).")
    assert(!(!io.n_rd3 && !n_wrD1_reg && (io.rdAddr3 === wrAddrD1_reg)), "Access same address by RD and WR(3C).")

    assert(!(!n_wrD1_reg && !io.n_wr), "Receive two successive write operations.")
    assert(!(!n_rd1D1_reg && !io.n_rd1), "Receive two successive read operations.")
    assert(!(!n_rd2D1_reg && !io.n_rd2), "Receive two successive read operations.")
    assert(!(!n_rd3D1_reg && !io.n_rd3), "Receive two successive read operations.")

    when(!io.n_rd1) {
      rdData1_reg := BANK_BLOCK(io.rdAddr1)
    }
    when(!io.n_rd2) {
      rdData2_reg := BANK_BLOCK(io.rdAddr2)
    }
    when(!io.n_rd3) {
      rdData3_reg := BANK_BLOCK(io.rdAddr3)
    }

    // An extra delay is added to consider the timing of the internal memories.
    // IMPRTANT: We do not need it implement this MUX, this MUX is added for testing.
    io.rdData1    := Mux(!n_rd1D1_reg, UInt(0), rdData1_reg)
    io.rdData2    := Mux(!n_rd2D1_reg, UInt(0), rdData2_reg)
    io.rdData3    := Mux(!n_rd3D1_reg, UInt(0), rdData3_reg)
  }
  else {
    // Ignore the rdEna
    rdData1_reg := BANK_BLOCK(io.rdAddr1)
    rdData2_reg := BANK_BLOCK(io.rdAddr2)
    rdData3_reg := BANK_BLOCK(io.rdAddr3)

    io.rdData1 := rdData1_reg
    io.rdData2 := rdData2_reg
    io.rdData3 := rdData3_reg
  }
}

class FpuRegWriteInt()(implicit p: Parameters) extends IntenBundle  {
  val isReal                 = Bool()
  val ctxtId                 = UInt(OUTPUT, CTXT_ID_LEN)
  val tag                    = UInt(OUTPUT, REG_ID_LEN)
  val data                   = UInt(OUTPUT, 65)
}

class FPU(cfg: FPUParams)(implicit p: Parameters) extends FPUModule()(p) {
  val io = new FPUIO

  // Debug counters.
  val wrCntr          = Reg(init=Vec.fill(NUM_T_CTXT){UInt(0, 32)})

  for(ips <- 0 until NUM_PUS) {
    // load response
    val duLd_valid_reg   = Reg(next=io.dataUnitToFpu(ips).valid && !io.fpuPending(ips))
    val duLd_reg         = Reg(new WriteToRegfsetBundle())
    val duLd_validP_reg  = Reg(init=Bool(false))
    val duLdP_reg        = Reg(new WriteToRegfsetBundle())
    when(io.dataUnitToFpu(ips).valid) {
      duLd_reg := io.dataUnitToFpu(ips).bits
      when(io.fpuPending(ips)) {
        duLd_validP_reg := Bool(true)
        duLdP_reg       := io.dataUnitToFpu(ips).bits
      }
    }

    val ex1_valid_reg    = Reg(next=io.pipeFpu(ips).idc_validI, init=Bool(false))
    val ex1_ctxtId_reg   = RegEnable(io.pipeFpu(ips).idc_ctxtId, io.pipeFpu(ips).idc_validI)
    val ex1_inst_reg     = RegEnable(io.pipeFpu(ips).idc_inst, io.pipeFpu(ips).idc_validI)

    val ex1_validF_reg   = Reg(next=io.pipeFpu(ips).idc_validF, init=Bool(false))
    val ex1_ctxtIdF_reg  = RegEnable(io.pipeFpu(ips).idc_ctxtIdF, io.pipeFpu(ips).idc_validF)
    val ex1_instF_reg    = RegEnable(io.pipeFpu(ips).idc_instF, io.pipeFpu(ips).idc_validF)

    val ex2_valid_reg   = Reg(next=ex1_valid_reg, init=Bool(false))
    val ex2_inst_reg    = RegEnable(ex1_inst_reg, ex1_valid_reg)
    val ex2_ctxtId_reg  = RegEnable(ex1_ctxtId_reg, ex1_valid_reg)

    val ex2_validF_reg   = Reg(next=ex1_validF_reg, init=Bool(false))
    val ex2_instF_reg    = RegEnable(ex1_instF_reg, ex1_validF_reg)
    val ex2_ctxtIdF_reg  = RegEnable(ex1_ctxtIdF_reg, ex1_validF_reg)

    val mem_valid_reg   = Reg(next=ex2_valid_reg, init=Bool(false))
    val mem_ctxtId_reg  = RegEnable(ex2_ctxtId_reg, ex2_valid_reg)
    val mem_inst_reg    = RegEnable(ex2_inst_reg, ex2_valid_reg)

    val mem_validF_reg   = Reg(next=ex2_validF_reg, init=Bool(false))
    val mem_ctxtIdF_reg  = RegEnable(ex2_ctxtIdF_reg, ex2_validF_reg)
    val mem_instF_reg    = RegEnable(ex2_instF_reg, ex2_validF_reg)

    val fp_decoder       = Module(new FPUDecoder)
    fp_decoder.io.inst  := io.pipeFpu(ips).idc_inst
    val idc_ctrl         = fp_decoder.io.sigs
    val fp_decoderF      = Module(new FPUDecoder)
    fp_decoderF.io.inst := io.pipeFpu(ips).idc_instF
    val idc_ctrlF        = fp_decoderF.io.sigs

    val ex1_ctrl_reg     = RegEnable(idc_ctrl, io.pipeFpu(ips).idc_validI)
    val ex2_ctrl_reg     = RegEnable(ex1_ctrl_reg, ex1_valid_reg)
    val mem_ctrl_reg     = RegEnable(ex2_ctrl_reg, ex2_valid_reg)

    val ex1_ctrlF_reg    = RegEnable(idc_ctrlF, io.pipeFpu(ips).idc_validF)
    val ex2_ctrlF_reg    = RegEnable(ex1_ctrlF_reg, ex1_validF_reg)
    val mem_ctrlF_reg    = RegEnable(ex2_ctrlF_reg, ex2_validF_reg)

    val ex1_ctrlM_reg    = Reg(Vec(NUM_MPS, idc_ctrl))
    val ex1_instM_reg    = Reg(Vec(NUM_MPS, UInt(width=INSTR_LEN)))
    val ex1_ctxtIdM_reg  = Reg(Vec(NUM_MPS, UInt(width=CTXT_ID_LEN)))
    val ex1_validM_reg   = Reg(init=Vec.fill(NUM_MPS) {Bool(false)})
    for(mps <- 0 until NUM_MPS) {
      val fp_decoderM       = Module(new FPUDecoder)
      fp_decoderM.io.inst  := io.pipeFpu(ips).idc_instM(mps)
      val idc_ctrlM         = fp_decoderM.io.sigs
      ex1_validM_reg(mps)  := io.pipeFpu(ips).idc_validM(mps)
      when(io.pipeFpu(ips).idc_validM(mps)) {
        ex1_ctrlM_reg(mps)   := idc_ctrlM
        ex1_instM_reg(mps)   := io.pipeFpu(ips).idc_instM(mps)
        ex1_ctxtIdM_reg(mps) := io.pipeFpu(ips).idc_ctxtIdM(mps)
      }
    }

    // added regfile
    val regfile     = 0 to NUM_CTXT - 1 map {x => Module(new FPUMemory).io}
    val rdDataW1    = Wire(Vec(NUM_CTXT, UInt()))
    val rdDataW2    = Wire(Vec(NUM_CTXT, UInt()))
    val rdDataW3    = Wire(Vec(NUM_CTXT, UInt()))

    // Read from the register file.
    val ex1_rs1  = MuxTree(ex1_ctxtId_reg , (0 until NUM_CTXT).map((w: Int) => rdDataW1(w)))
    val ex1_rs2  = MuxTree(ex1_ctxtId_reg , (0 until NUM_CTXT).map((w: Int) => rdDataW2(w)))
    val ex1_rs3  = MuxTree(ex1_ctxtId_reg , (0 until NUM_CTXT).map((w: Int) => rdDataW3(w)))
    val ex1_rs1F = MuxTree(ex1_ctxtIdF_reg, (0 until NUM_CTXT).map((w: Int) => rdDataW1(w)))
    val ex1_rs2F = MuxTree(ex1_ctxtIdF_reg, (0 until NUM_CTXT).map((w: Int) => rdDataW2(w)))
    val ex1_rs3F = MuxTree(ex1_ctxtIdF_reg, (0 until NUM_CTXT).map((w: Int) => rdDataW3(w)))

    val ex1_rs2M = Wire(Vec(NUM_MPS, UInt()))
    for(mps <- 0 until NUM_MPS) {
      ex1_rs2M(mps) := OrTree((mps*(NUM_CTXT/NUM_MPS) until (mps+1)*(NUM_CTXT/NUM_MPS)).map((w: Int) => Mux(ex1_ctxtIdM_reg(mps) === UInt(w), rdDataW2(w), UInt(0))))
    }

    def fuInput(minT: Option[FType]): FPInput = {
      val req     = Wire(new FPInput)
      val tag     = !ex1_ctrl_reg.singleIn // TODO typeTag
      val ex1_rm  = Mux(ex1_inst_reg(14,12) === Bits(7), io.pipeFpu(ips).ex1_fcsr_rm, ex1_inst_reg(14,12))
      req        := ex1_ctrl_reg
      req.rm     := ex1_rm
      req.in1    := unbox(ex1_rs1, tag, minT)
      req.in2    := unbox(ex1_rs2, tag, minT)
      req.in3    := unbox(ex1_rs3, tag, minT)
      req.typ    := ex1_inst_reg(21,20)
      req.fmaCmd := ex1_inst_reg(3,2) | (!ex1_ctrl_reg.ren3 && ex1_inst_reg(27))
      req
    }

    def fuInputF(minT: Option[FType]): FPInput = {
      val req     = Wire(new FPInput)
      val tag     = !ex1_ctrlF_reg.singleIn // TODO typeTag
      val ex1_rmF = Mux(ex1_instF_reg(14,12) === Bits(7), io.pipeFpu(ips).ex1_fcsr_rmF, ex1_instF_reg(14,12))
      req        := ex1_ctrlF_reg
      req.rm     := ex1_rmF
      req.in1    := unbox(ex1_rs1F, tag, minT)
      req.in2    := unbox(ex1_rs2F, tag, minT)
      req.in3    := unbox(ex1_rs3F, tag, minT)
      req.typ    := ex1_instF_reg(21,20)
      req.fmaCmd := ex1_instF_reg(3,2) | (!ex1_ctrlF_reg.ren3 && ex1_instF_reg(27))
      req
    }

    def fuInputM(minT: Option[FType], mps: Int): FPInput = {
      val req     = Wire(new FPInput)
      val tag     = !ex1_ctrlM_reg(mps).singleIn // TODO typeTag
      assert((ex1_instM_reg(mps)(14,12) =/= Bits(7)) || !ex1_validM_reg(mps))
      req        := ex1_ctrlM_reg(mps)
      req.rm     := ex1_instM_reg(mps)(14,12)
      req.in1    := unbox(ex1_rs2M(mps), tag, minT)
      req.in2    := UInt(0)
      req.in3    := UInt(0)
      req.typ    := ex1_instM_reg(mps)(21,20)
      req.fmaCmd := ex1_instM_reg(mps)(3,2) | (!ex1_ctrlM_reg(mps).ren3 && ex1_instM_reg(mps)(27))
      req
    }

    val sfmaF = Module(new FPUFMAPipe(cfg.sfmaLatency, FType.S))
    sfmaF.io.in.valid := ex1_validF_reg && ex1_ctrlF_reg.fma && ex1_ctrlF_reg.singleOut
    sfmaF.io.in.bits  := fuInputF(Some(sfmaF.t))

    val fpiu                         = Module(new FPToInt)
    fpiu.io.in.valid                := ex1_valid_reg && ex1_ctrl_reg.toint
    fpiu.io.in.bits                 := fuInput(None)
    io.pipeFpu(ips).mem_toint_data  := fpiu.io.out.bits.toint

    for(mps <- 0 until NUM_MPS) {
      val fpiuM                             = Module(new FPToInt)
      fpiuM.io.in.valid                    := ex1_validM_reg(mps)
      fpiuM.io.in.bits                     := fuInputM(None, mps)
      io.pipeFpu(ips).mem_store_data(mps)  := fpiuM.io.out.bits.store
    }

    val fpiuF           = Module(new FPToInt)
    fpiuF.io.in.valid  := ex1_validF_reg && (ex1_ctrlF_reg.div || ex1_ctrlF_reg.sqrt || (ex1_ctrlF_reg.fastpipe && ex1_ctrlF_reg.wflags))
    fpiuF.io.in.bits   := fuInputF(None)

    val ifpu = Module(new IntToFP(6))
    ifpu.io.in.valid    := ex2_valid_reg && ex2_ctrl_reg.fromint
    ifpu.io.in.bits     := Reg(next=fuInput(None))
    ifpu.io.in.bits.in1 := io.pipeFpu(ips).ex2_fromint_data

    val fpmuF = Module(new FPToFP(6))
    fpmuF.io.in.valid := ex2_validF_reg && ex2_ctrlF_reg.fastpipe
    fpmuF.io.in.bits  := Reg(next=fuInputF(None))
    fpmuF.io.lt       := fpiuF.io.out.bits.lt

    val divSqrt_inFlight  = Wire(init = false.B)
    val divSqrt_waddr     = Reg(UInt(width = 5))
    val divSqrt_ctxtId    = Reg(Bits())
    val divSqrt_isReal    = Reg(init=Bool(false))

    val divSqrt_wen_reg     = Reg(init=Bool(false))
    val divSqrt_wrG_reg     = Reg(init=Bool(false))
    val divSqrt_wen         = Wire(init=Bool(false))
    val divSqrt_wdata_reg   = Reg(init=UInt(0, fLen+1))
    val divSqrt_flags_reg   = Reg(init=UInt(0, FPConstants.FLAGS_SZ))
    val divSqrt_typeTag_reg = Reg(init=UInt(0, log2Up(floatTypes.size)))

    val wrMemPGE1_reg       = Reg(init=Bool(false))
    val wrMemPG_reg         = Reg(init=Bool(false))
    val wrMemPGE1           = Wire(init=Bool(false))

    val dfmaF = Module(new FPUFMAPipe(cfg.dfmaLatency, FType.D))

    // writeback arbitration
    case class Pipe(p: Module, lat: Int, cond: (FPUCtrlSigs) => Bool, res: FPResult)
    val pipes = List(
      Pipe(fpmuF, fpmuF.latency, (c: FPUCtrlSigs) => c.fastpipe, fpmuF.io.out.bits),
      Pipe(ifpu, ifpu.latency, (c: FPUCtrlSigs) => c.fromint, ifpu.io.out.bits),
      Pipe(sfmaF, sfmaF.latency-1, (c: FPUCtrlSigs) => c.fma && c.singleOut, sfmaF.io.out.bits)) ++
      (fLen > 32).option({
            dfmaF.io.in.valid := ex1_validF_reg && ex1_ctrlF_reg.fma && !ex1_ctrlF_reg.singleOut
            dfmaF.io.in.bits  := fuInputF(Some(dfmaF.t))
            require(dfmaF.latency == sfmaF.latency)
            Pipe(dfmaF, dfmaF.latency-1, (c: FPUCtrlSigs) => c.fma && !c.singleOut, dfmaF.io.out.bits)
          })
    def latencyMask(c: FPUCtrlSigs, offset: Int) = {
      require(pipes.forall(_.lat >= offset))
      OrTree(pipes.map(p => Mux(p.cond(c), UInt(1 << p.lat-offset), UInt(0))))
    }
    def pipeid(c: FPUCtrlSigs) = OrTree(pipes.zipWithIndex.map(p => Mux(p._1.cond(c), UInt(p._2), UInt(0))))
    val maxLatency = pipes.map(_.lat).max

    // Make sure that the latency are the same.
    require(fpmuF.latency == ifpu.latency)

    class WBInfo extends Bundle {
      val rd     = UInt(width = 5)
      val single = Bool()
      val cp     = Bool()
      val isReal = Bool()
      val ctxtId = UInt(width = CTXT_ID_LEN)
      val pipeid = UInt(width = log2Ceil(pipes.size))
      override def cloneType: this.type = new WBInfo().asInstanceOf[this.type]
    }

    val wenArr          = Reg(init=Bits(0, maxLatency-1))
    val wbInfoArr       = Reg(Vec(maxLatency-1, new WBInfo))
    val memLatencyMask  = latencyMask(mem_ctrl_reg, 2)
    for (i <- 0 until maxLatency-2) {
      when (wenArr(i+1)) { wbInfoArr(i) := wbInfoArr(i+1) }
    }
    wenArr := wenArr >> 1
    when (mem_valid_reg && (mem_ctrl_reg.fma || mem_ctrl_reg.fromint)) {
      wenArr := wenArr >> 1 | memLatencyMask
      for (i <- 0 until maxLatency-1) {
        when (memLatencyMask(i)) {
          wbInfoArr(i).cp         := Bool(false)
          wbInfoArr(i).single     := mem_ctrl_reg.singleOut
          wbInfoArr(i).pipeid     := pipeid(mem_ctrl_reg)
          wbInfoArr(i).rd         := mem_inst_reg(11,7)
          wbInfoArr(i).ctxtId     := mem_ctxtId_reg
          wbInfoArr(i).isReal     := io.pipeFpu(ips).mxx_isReal(mem_ctxtId_reg)
        }
      }
    }

    val wenArrF         = Reg(init=Bits(0, maxLatency-1))
    val wbInfoArrF      = Reg(Vec(maxLatency-1, new WBInfo))
    val memLatencyMaskF = latencyMask(mem_ctrlF_reg, 2)
    for (i <- 0 until maxLatency-2) {
      when (wenArrF(i+1)) { wbInfoArrF(i) := wbInfoArrF(i+1) }
    }
    wenArrF := wenArrF >> 1
    when (mem_validF_reg && (mem_ctrlF_reg.fma || mem_ctrlF_reg.fastpipe)) {
      wenArrF := wenArrF >> 1 | memLatencyMaskF
      for (i <- 0 until maxLatency-1) {
        when (memLatencyMaskF(i)) {
          wbInfoArrF(i).cp         := Bool(false)
          wbInfoArrF(i).single     := mem_ctrlF_reg.singleOut
          wbInfoArrF(i).pipeid     := pipeid(mem_ctrlF_reg)
          wbInfoArrF(i).rd         := mem_instF_reg(11,7)
          wbInfoArrF(i).ctxtId     := mem_ctxtIdF_reg
          wbInfoArrF(i).isReal     := io.pipeFpu(ips).mxx_isReal(mem_ctxtIdF_reg)
        }
      }
    }

    // Output latency.
    val DELAY_VER                      = 4

    // Connect the feed-back bus to the ctxtUnit.
    io.fpuToCtxtUnit(ips).fpuSafe0    := (!wbInfoArr(DELAY_VER).cp && wenArr(DELAY_VER))
    io.fpuToCtxtUnit(ips).fpuCtxtId0  := wbInfoArr(DELAY_VER).ctxtId
    io.fpuToCtxtUnit(ips).fpuRegId0   := wbInfoArr(DELAY_VER).rd
    io.fpuToCtxtUnit(ips).fpuIsReal0  := wbInfoArr(DELAY_VER).isReal

    io.fpuToCtxtUnit(ips).fpuSafe1    := divSqrt_wen
    io.fpuToCtxtUnit(ips).fpuCtxtId1  := divSqrt_ctxtId
    io.fpuToCtxtUnit(ips).fpuRegId1   := divSqrt_waddr
    io.fpuToCtxtUnit(ips).fpuIsReal1  := divSqrt_isReal

    io.fpuToCtxtUnit(ips).fpuSafe2    := wrMemPGE1
    io.fpuToCtxtUnit(ips).fpuCtxtId2  := duLdP_reg.ctxtId
    io.fpuToCtxtUnit(ips).fpuRegId2   := duLdP_reg.destReg
    io.fpuToCtxtUnit(ips).fpuIsReal2  := duLdP_reg.isReal

    io.fpuToCtxtUnit(ips).fpuSafe3    := (!wbInfoArrF(DELAY_VER).cp && wenArrF(DELAY_VER))
    io.fpuToCtxtUnit(ips).fpuCtxtId3  := wbInfoArrF(DELAY_VER).ctxtId
    io.fpuToCtxtUnit(ips).fpuRegId3   := wbInfoArrF(DELAY_VER).rd
    io.fpuToCtxtUnit(ips).fpuIsReal3  := wbInfoArrF(DELAY_VER).isReal

    for (i <- 0 until NUM_CTXT) {
      val mpsX   = i / (NUM_CTXT / NUM_MPS)

      val valid1 = (!wbInfoArr(0).cp && wenArr(0)) && (wbInfoArr(0).ctxtId === UInt(i)) && wbInfoArr(0).isReal
      val valid2 = divSqrt_wen && (divSqrt_ctxtId === UInt(i)) && divSqrt_isReal
      val valid3 = duLd_valid_reg && (duLd_reg.ctxtId === UInt(i)) && duLd_reg.isReal
      val valid4 = wrMemPG_reg && (duLdP_reg.ctxtId === UInt(i)) && duLdP_reg.isReal
      val valid5 = (!wbInfoArrF(0).cp && wenArrF(0)) && (wbInfoArrF(0).ctxtId === UInt(i)) && wbInfoArrF(0).isReal
      assert(PopCount(Cat(valid1, valid2, valid3, valid4, valid5)) <= UInt(1))

      val wrAddr = Mux(valid1, wbInfoArr(0).rd, Mux(valid2, divSqrt_waddr, Mux(valid4, duLdP_reg.destReg, Mux(valid5, wbInfoArrF(0).rd, duLd_reg.destReg))))

      val wrData = Mux(valid1, box((pipes.map(_.res.data): Seq[UInt])(wbInfoArr(0).pipeid), !wbInfoArr(0).single),
                       Mux(valid2, box(divSqrt_wdata_reg, divSqrt_typeTag_reg), Mux(valid4, recode(duLdP_reg.dataToWrite, duLdP_reg.size(0)), Mux(valid5, box((pipes.map(_.res.data): Seq[UInt])(wbInfoArrF(0).pipeid), !wbInfoArrF(0).single), recode(duLd_reg.dataToWrite, duLd_reg.size(0))))))

      regfile(i).rdAddr1  := io.ctxtToFpu(ips).rowToRfF(i).addr1
      regfile(i).rdAddr2  := io.ctxtToFpu(ips).rowToRfF(i).addr2
      regfile(i).rdAddr3  := io.ctxtToFpu(ips).rowToRfF(i).addr3
      regfile(i).n_rd1    := io.ctxtToFpu(ips).rowToRfF(i).rdEna1
      regfile(i).n_rd2    := io.ctxtToFpu(ips).rowToRfF(i).rdEna2
      regfile(i).n_rd3    := io.ctxtToFpu(ips).rowToRfF(i).rdEna3
      regfile(i).n_wr     := !(valid1 || valid2 || valid3 || valid4 || valid5)
      regfile(i).wrAddr   := wrAddr
      regfile(i).wrData   := wrData

      when(valid1 || valid2 || valid3 || valid4 || valid5) {
        assert(consistent(wrData))

        if(DEBUG == 2) {
          printf ("FP%x_%x: %x %x %x\n", UInt(ips), UInt(i), wrCntr(ips*NUM_CTXT + i), wrAddr, wrData)
          wrCntr(ips*NUM_CTXT + i)  := wrCntr(ips*NUM_CTXT + i) + UInt(1)
        }
      }

      val wrDataD1_reg    = RegEnable(regfile(i).wrData, !regfile(i).n_wr)
      val isFeed1AD1_reg  = Reg(next=(!regfile(i).n_wr && (regfile(i).rdAddr1 === regfile(i).wrAddr)))
      val isFeed2AD1_reg  = Reg(next=(!regfile(i).n_wr && (regfile(i).rdAddr2 === regfile(i).wrAddr)))
      val isFeed3AD1_reg  = Reg(next=(!regfile(i).n_wr && (regfile(i).rdAddr3 === regfile(i).wrAddr)))

      val wdD1_reg        = Reg(next=(!regfile(i).n_wr))
      val wdAddrD1_reg    = RegEnable(regfile(i).wrAddr, !regfile(i).n_wr)
      val isFeed1CD1_reg  = Reg(next=wdD1_reg && (regfile(i).rdAddr1 === wdAddrD1_reg))
      val isFeed2CD1_reg  = Reg(next=wdD1_reg && (regfile(i).rdAddr2 === wdAddrD1_reg))
      val isFeed3CD1_reg  = Reg(next=wdD1_reg && (regfile(i).rdAddr3 === wdAddrD1_reg))
      val wrDataD2_reg    = RegEnable(wrDataD1_reg, wdD1_reg)

      if(USE_TWO_CYCLES_RF) {
        val wdD2_reg        = Reg(next=wdD1_reg)
        val wdAddrD2_reg    = RegEnable(wdAddrD1_reg, wdD1_reg)
        val isFeed1D        = wdD2_reg && (regfile(i).rdAddr1 === wdAddrD2_reg)
        val isFeed2D        = wdD2_reg && (regfile(i).rdAddr2 === wdAddrD2_reg)
        val isFeed3D        = wdD2_reg && (regfile(i).rdAddr3 === wdAddrD2_reg)
        val isFeed1DD1_reg  = Reg(next=isFeed1D)
        val isFeed2DD1_reg  = Reg(next=isFeed2D)
        val isFeed3DD1_reg  = Reg(next=isFeed3D)
        val wrDataD3_reg    = RegEnable(wrDataD2_reg, isFeed1D || isFeed2D || isFeed3D)

        rdDataW1(i)       := Mux(isFeed1AD1_reg, wrDataD1_reg, Mux(isFeed1CD1_reg, wrDataD2_reg, Mux(isFeed1DD1_reg, wrDataD3_reg, regfile(i).rdData1)))
        rdDataW2(i)       := Mux(isFeed2AD1_reg, wrDataD1_reg, Mux(isFeed2CD1_reg, wrDataD2_reg, Mux(isFeed2DD1_reg, wrDataD3_reg, regfile(i).rdData2)))
        rdDataW3(i)       := Mux(isFeed3AD1_reg, wrDataD1_reg, Mux(isFeed3CD1_reg, wrDataD2_reg, Mux(isFeed3DD1_reg, wrDataD3_reg, regfile(i).rdData3)))
      }
      else {
        rdDataW1(i)       := Mux(isFeed1AD1_reg, wrDataD1_reg, Mux(isFeed1CD1_reg, wrDataD2_reg, regfile(i).rdData1))
        rdDataW2(i)       := Mux(isFeed2AD1_reg, wrDataD1_reg, Mux(isFeed2CD1_reg, wrDataD2_reg, regfile(i).rdData2))
        rdDataW3(i)       := Mux(isFeed3AD1_reg, wrDataD1_reg, Mux(isFeed3CD1_reg, wrDataD2_reg, regfile(i).rdData3))
      }
    }

    val mem_toint_valid = mem_valid_reg && mem_ctrl_reg.toint
    val mem_toint_exc   = fpiu.io.out.bits.exc // TODO: MIGRATION: We may need to register this signal

    io.pipeFpu(ips).fcsr_flags0.valid := mem_toint_valid && io.pipeFpu(ips).mxx_isReal(mem_ctxtId_reg)
    io.pipeFpu(ips).fcsr_flags0.bits  := mem_toint_exc
    io.pipeFpu(ips).fcsr_ctxtId0      := mem_ctxtId_reg
    io.pipeFpu(ips).fcsr_flags1.valid := divSqrt_wen && divSqrt_isReal
    io.pipeFpu(ips).fcsr_flags1.bits  := divSqrt_flags_reg
    io.pipeFpu(ips).fcsr_ctxtId1      := divSqrt_ctxtId
    io.pipeFpu(ips).fcsr_flags2.valid := wenArr(0) && wbInfoArr(0).isReal
    io.pipeFpu(ips).fcsr_flags2.bits  := (pipes.map(_.res.exc): Seq[UInt])(wbInfoArr(0).pipeid)
    io.pipeFpu(ips).fcsr_ctxtId2      := wbInfoArr(0).ctxtId
    io.pipeFpu(ips).fcsr_flags3.valid := wenArrF(0) && wbInfoArrF(0).isReal
    io.pipeFpu(ips).fcsr_flags3.bits  := (pipes.map(_.res.exc): Seq[UInt])(wbInfoArrF(0).pipeid)
    io.pipeFpu(ips).fcsr_ctxtId3      := wbInfoArrF(0).ctxtId

    io.pipeFpu(ips).dec         := ex1_ctrl_reg

    // we don't currently support round-max-magnitude (rm=4)
    // NOTE: Any change here shall be incorporated in isFpuPath signal in the CTXT.
    io.pipeFpu(ips).ex2_illegal_rm := Reg(next=((ex1_inst_reg(14,12).isOneOf(5, 6)) || (ex1_inst_reg(14,12) === 7)) && (io.pipeFpu(ips).ex1_fcsr_rm >= 5))

    val checkPA0      = wenArr(0) && (wbInfoArr(0).ctxtId === duLdP_reg.ctxtId)
    val checkPA1      = wenArr(1) && (wbInfoArr(1).ctxtId === duLdP_reg.ctxtId)
    val checkPA2      = wenArr(2) && (wbInfoArr(2).ctxtId === duLdP_reg.ctxtId)
    val checkPA3      = wenArr(3) && (wbInfoArr(3).ctxtId === duLdP_reg.ctxtId)
    val checkPC0      = wenArrF(0) && (wbInfoArrF(0).ctxtId === duLdP_reg.ctxtId)
    val checkPC1      = wenArrF(1) && (wbInfoArrF(1).ctxtId === duLdP_reg.ctxtId)
    val checkPC2      = wenArrF(2) && (wbInfoArrF(2).ctxtId === duLdP_reg.ctxtId)
    val checkPC3      = wenArrF(3) && (wbInfoArrF(3).ctxtId === duLdP_reg.ctxtId)
    wrMemPGE1_reg    := !((checkPA1&&Bool(USE_TWO_CYCLES_RF))||checkPA2||(checkPA3&&Bool(USE_TWO_CYCLES_RF)) || (checkPC1&&Bool(USE_TWO_CYCLES_RF))||checkPC2||(checkPC3&&Bool(USE_TWO_CYCLES_RF))) && duLd_validP_reg
    val wrMemPGE10    = !((checkPA0&&Bool(USE_TWO_CYCLES_RF))||checkPA1||(checkPA2&&Bool(USE_TWO_CYCLES_RF)) || (checkPC0&&Bool(USE_TWO_CYCLES_RF))||checkPC1||(checkPC2&&Bool(USE_TWO_CYCLES_RF))) && duLd_validP_reg
    wrMemPGE1        := ((wrMemPGE1_reg&&duLd_validP_reg&&Bool(USE_TWO_CYCLES_RF)) || wrMemPGE10) && !(wrMemPG_reg&&Bool(USE_TWO_CYCLES_RF))
    wrMemPG_reg      := wrMemPGE1
    when(Mux(Bool(USE_TWO_CYCLES_RF), wrMemPG_reg, wrMemPGE1)) {
      duLd_validP_reg  := Bool(false)
    }
    io.dataUnitToFpu(ips).ready  := wrMemPGE1

    divSqrt_wen     := Bool(false)
    if (cfg.divSqrt) {
      // Check for the time to write to the register.
      val checkA0 = wenArr(0) && (wbInfoArr(0).ctxtId === divSqrt_ctxtId) && Bool(USE_TWO_CYCLES_RF)
      val checkA1 = wenArr(1) && (wbInfoArr(1).ctxtId === divSqrt_ctxtId)
      val checkA2 = wenArr(2) && (wbInfoArr(2).ctxtId === divSqrt_ctxtId) && Bool(USE_TWO_CYCLES_RF)

      val checkD0 = wenArrF(0) && (wbInfoArrF(0).ctxtId === divSqrt_ctxtId) && Bool(USE_TWO_CYCLES_RF)
      val checkD1 = wenArrF(1) && (wbInfoArrF(1).ctxtId === divSqrt_ctxtId)
      val checkD2 = wenArrF(2) && (wbInfoArrF(2).ctxtId === divSqrt_ctxtId) && Bool(USE_TWO_CYCLES_RF)

      val checkB0 = duLd_valid_reg && (duLd_reg.ctxtId === divSqrt_ctxtId) && Bool(USE_TWO_CYCLES_RF)
      val checkB1 = io.dataUnitToFpu(ips).valid && (io.dataUnitToFpu(ips).bits.ctxtId === divSqrt_ctxtId)
      val checkB2 = io.earlyFpuWrFlag(ips) && (io.earlyCtxtFpuWr(ips) === divSqrt_ctxtId) && Bool(USE_TWO_CYCLES_RF)

      val checkC0 = duLd_validP_reg && (duLdP_reg.ctxtId === divSqrt_ctxtId)
      val checkE2 = io.unCacheFpuNotif

      // Check for the right time to write to the RF.
      divSqrt_wrG_reg   := !(checkA0 || checkA1 || checkA2 || checkB0 || checkB1 || checkB2 || checkC0 || checkD0 || checkD1 || checkD2 || checkE2)
      divSqrt_wen    := divSqrt_wen_reg && divSqrt_wrG_reg
      when(divSqrt_wen) {
        divSqrt_wen_reg := Bool(false)
      }

      for (t <- floatTypes) {
        val tag     = !mem_ctrlF_reg.singleOut // TODO typeTag
        withReset(reset.asAsyncReset){
        val divSqrt = Module(new hardfloat.DivSqrtRecFN_small(t.exp, t.sig, 0))
        divSqrt.io.inValid        := mem_validF_reg && tag === typeTag(t) && (mem_ctrlF_reg.div || mem_ctrlF_reg.sqrt) && !divSqrt_inFlight
        divSqrt.io.sqrtOp         := mem_ctrlF_reg.sqrt
        divSqrt.io.a              := maxType.unsafeConvert(fpiuF.io.out.bits.in.in1, t)
        divSqrt.io.b              := maxType.unsafeConvert(fpiuF.io.out.bits.in.in2, t)
        divSqrt.io.roundingMode   := fpiuF.io.out.bits.in.rm
        divSqrt.io.detectTininess := hardfloat.consts.tininess_afterRounding

        when (!divSqrt.io.inReady) { divSqrt_inFlight := true } // only 1 in flight

        when (divSqrt.io.inValid && divSqrt.io.inReady) {
          divSqrt_waddr     := mem_instF_reg(11,7)
          divSqrt_ctxtId    := mem_ctxtIdF_reg
          divSqrt_isReal    := io.pipeFpu(ips).mxx_isReal(mem_ctxtIdF_reg)
        }

        when (divSqrt.io.outValid_div || divSqrt.io.outValid_sqrt) {
          divSqrt_wen_reg     := Bool(true)
          divSqrt_wdata_reg   := sanitizeNaN(divSqrt.io.out, t)
          divSqrt_flags_reg   := divSqrt.io.exceptionFlags
          divSqrt_typeTag_reg := typeTag(t)
        }
        assert(!(mem_validF_reg && tag === typeTag(t) && (mem_ctrlF_reg.div || mem_ctrlF_reg.sqrt)) || !divSqrt_inFlight, "DivSqrt is not ready.")
        }
      }
    }
    else {
      when (ex1_ctrl_reg.div || ex1_ctrl_reg.sqrt) { io.pipeFpu(ips).ex2_illegal_rm := true }
    }
    require(cfg.divSqrt)
    assert(!(mem_valid_reg && (mem_ctrl_reg.div || mem_ctrl_reg.sqrt || mem_ctrl_reg.fma || mem_ctrl_reg.fastpipe)))
    assert(!mem_validF_reg || (mem_ctrlF_reg.div || mem_ctrlF_reg.sqrt || mem_ctrlF_reg.fma || mem_ctrlF_reg.fastpipe))
  }

  def ccover(cond: Bool, label: String, desc: String)(implicit sourceInfo: SourceInfo) =
    cover(cond, s"FPU_$label", "Core;;" + desc)
}

