// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.HasCoreParameters
import freechips.rocketchip.util._
import Instructions._
import ALU._

abstract trait DecodeConstants extends HasCoreParameters
{
  val table: Array[(BitPat, List[BitPat])]
}

class IntCtrlSigsBUN extends Bundle {
  val instrId = Bits(width = 8)
  val legal = Bool()
  val fp = Bool()
  val rocc = Bool()
  val branch = Bool()
  val jal = Bool()
  val jalr = Bool()
  val rxs2 = Bool()
  val rxs1 = Bool()
  val sel_alu2 = Bits(width = A2_X.getWidth)
  val sel_alu1 = Bits(width = A1_X.getWidth)
  val sel_imm = Bits(width = IMM_X.getWidth)
  val alu_dw = Bool()
  val alu_fn = Bits(width = FN_X.getWidth)
  val mem = Bool()
  val mem_cmd = Bits(width = M_SZ)
  val mem_type = Bits(width = MT_SZ)
  val rfs1 = Bool()
  val rfs2 = Bool()
  val rfs3 = Bool()
  val wfd = Bool()
  val mul = Bool()
  val div = Bool()
  val wxd = Bool()
  val csr          = Bits(width = CSR.SZ)
  val fence_i      = Bool()
  val fence        = Bool()
  val amo          = Bool()
  val dp           = Bool()
  val end_flag     = Bool()
  val isCondBranch = Bool()
}

class IntCtrlSigs extends IntCtrlSigsBUN {
  def default: List[BitPat] =
                //                     jal                                                                 renf1               fence.i
                //             val     | jalr                                                              | renf2             |
                //             | fp_val| | renx2                                                           | | renf3           | fence
                //             | | rocc| | | renx1     s_alu1                          mem_val             | | | wfd           | |
                //             | | | br| | | | s_alu2  |       imm    dw     alu       | mem_cmd   mem_type| | | | mul         | | amo
                //             | | | | | | | | |       |       |      |      |         | |           |     | | | | | div       | | | dp
                //   instrId   | | | | | | | | |       |       |      |      |         | |           |     | | | | | | wxd     | | | | end_flag
                //   |         | | | | | | | | |       |       |      |      |         | |           |     | | | | | | |       | | | | | isCondBranch
                List(UInt(  0),N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,N)

  def decode(inst: UInt, table: Iterable[(BitPat, List[BitPat])]) = {
    val decoder = DecodeLogic(inst, default, table)
    val sigs = Seq(instrId, legal, fp, rocc, branch, jal, jalr, rxs2, rxs1, sel_alu2,
                   sel_alu1, sel_imm, alu_dw, alu_fn, mem, mem_cmd, mem_type,
                   rfs1, rfs2, rfs3, wfd, mul, div, wxd, csr, fence_i, fence, amo, dp, end_flag, isCondBranch)
    sigs zip decoder map {case(s,d) => s := d}
    this
  }
}

class IDecodeM(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    LB->        List(UInt( 10),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_B, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    LH->        List(UInt( 11),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_H, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    LW->        List(UInt( 12),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    LBU->       List(UInt( 13),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_BU,N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    LHU->       List(UInt( 14),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_HU,N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SB->        List(UInt( 15),Y,N,N,N,N,N,Y,Y,A2_IMM, A1_RS1, IMM_S, DW_XPR,FN_ADD,   Y,M_XWR,      MT_B, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    SH->        List(UInt( 16),Y,N,N,N,N,N,Y,Y,A2_IMM, A1_RS1, IMM_S, DW_XPR,FN_ADD,   Y,M_XWR,      MT_H, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    SW->        List(UInt( 17),Y,N,N,N,N,N,Y,Y,A2_IMM, A1_RS1, IMM_S, DW_XPR,FN_ADD,   Y,M_XWR,      MT_W, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),

    FENCE_I->   List(UInt( 39),Y,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     Y,M_FLUSH_ALL,MT_X, N,N,N,N,N,N,N,CSR.N,Y,N,N,N,Y,N))
}

class IDecodeMR(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array( //3532
    C_LW->      List(UInt( 12),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I4SP, DW_XPR,FN_ADD, Y,M_XRD,     MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_LWSP->    List(UInt( 12),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I16SP, DW_XPR,FN_ADD,Y,M_XRD,     MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_SW->      List(UInt( 17),Y,N,N,N,N,N,Y,Y,A2_IMM, A1_RS1, IMM_I4SP, DW_XPR,FN_ADD, Y,M_XWR,     MT_W, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    C_SWSP->    List(UInt( 17),Y,N,N,N,N,N,Y,Y,A2_IMM, A1_RS1, IMM_SB, DW_XPR,FN_ADD,   Y,M_XWR,     MT_W, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N))
}

class IDecode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    BNE->       List(UInt(  1),Y,N,N,Y,N,N,Y,Y,A2_RS2, A1_RS1, IMM_SB,DW_X,  FN_SNE,   N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,Y),
    BEQ->       List(UInt(  2),Y,N,N,Y,N,N,Y,Y,A2_RS2, A1_RS1, IMM_SB,DW_X,  FN_SEQ,   N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,Y),
    BLT->       List(UInt(  3),Y,N,N,Y,N,N,Y,Y,A2_RS2, A1_RS1, IMM_SB,DW_X,  FN_SLT,   N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,Y),
    BLTU->      List(UInt(  4),Y,N,N,Y,N,N,Y,Y,A2_RS2, A1_RS1, IMM_SB,DW_X,  FN_SLTU,  N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,Y),
    BGE->       List(UInt(  5),Y,N,N,Y,N,N,Y,Y,A2_RS2, A1_RS1, IMM_SB,DW_X,  FN_SGE,   N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,Y),
    BGEU->      List(UInt(  6),Y,N,N,Y,N,N,Y,Y,A2_RS2, A1_RS1, IMM_SB,DW_X,  FN_SGEU,  N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,Y),

    JAL->       List(UInt(  7),Y,N,N,N,Y,N,N,N,A2_SIZE,A1_PC,  IMM_UJ,DW_XPR,FN_ADD,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,Y,N),
    JALR->      List(UInt(  8),Y,N,N,N,N,Y,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,Y,N),

    AUIPC->     List(UInt(  9),Y,N,N,N,N,N,N,N,A2_IMM, A1_PC,  IMM_U, DW_XPR,FN_ADD,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),

    LUI->       List(UInt( 18),Y,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_U, DW_XPR,FN_ADD,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    ADDI->      List(UInt( 19),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SLTI ->     List(UInt( 20),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SLT,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SLTIU->     List(UInt( 21),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SLTU,  N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    ANDI->      List(UInt( 22),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_AND,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    ORI->       List(UInt( 23),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_OR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    XORI->      List(UInt( 24),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_XOR,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    ADD->       List(UInt( 28),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_ADD,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SUB->       List(UInt( 29),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_SUB,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SLT->       List(UInt( 30),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_SLT,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SLTU->      List(UInt( 31),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_SLTU,  N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    AND->       List(UInt( 32),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_AND,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    OR->        List(UInt( 33),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_OR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    XOR->       List(UInt( 34),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_XOR,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SLL->       List(UInt( 35),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_SL,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRL->       List(UInt( 36),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_SR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRA->       List(UInt( 37),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_SRA,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),

    FENCE->     List(UInt( 38),Y,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,Y,N,N,Y,N),

    SCALL->     List(UInt( 40),Y,N,N,N,N,N,N,X,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.I,N,N,N,N,Y,N),
    SBREAK->    List(UInt( 41),Y,N,N,N,N,N,N,X,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.I,N,N,N,N,Y,N),
    MRET->      List(UInt( 42),Y,N,N,N,N,N,N,X,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.I,N,N,N,N,Y,N),
    WFI->       List(UInt( 43),Y,N,N,N,N,N,N,X,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.I,N,N,N,N,Y,N),

    CSRRW->     List(UInt( 44),Y,N,N,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_OR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.W,N,N,N,N,Y,N),
    CSRRS->     List(UInt( 45),Y,N,N,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_OR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.S,N,N,N,N,Y,N),
    CSRRC->     List(UInt( 46),Y,N,N,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_OR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.C,N,N,N,N,Y,N),
    CSRRWI->    List(UInt( 47),Y,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_Z, DW_XPR,FN_OR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.W,N,N,N,N,Y,N),
    CSRRSI->    List(UInt( 48),Y,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_Z, DW_XPR,FN_OR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.S,N,N,N,N,Y,N),
    CSRRCI->    List(UInt( 49),Y,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_Z, DW_XPR,FN_OR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.C,N,N,N,N,Y,N))
}

class IDecodeR(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    C_RESERVED->List(UInt(  0),N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X,    DW_X,  FN_X,  N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,N),
    C_J->       List(UInt(  7),Y,N,N,N,Y,N,N,N,A2_SIZE,A1_PC,  IMM_UJ,   DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,Y,N),
    C_BEQZ->    List(UInt(  2),Y,N,N,Y,N,N,Y,Y,A2_RS2, A1_RS1, IMM_SB,   DW_X,  FN_SEQ,N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,Y),
    C_BNEZ->    List(UInt(  1),Y,N,N,Y,N,N,Y,Y,A2_RS2, A1_RS1, IMM_SB,   DW_X,  FN_SNE,N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,Y),
    C_JR->      List(UInt(  8),Y,N,N,N,N,Y,N,Y,A2_IMM, A1_RS1, IMM_I,    DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,Y,N),
    C_JALR->    List(UInt(  8),Y,N,N,N,N,Y,N,Y,A2_IMM, A1_RS1, IMM_I,    DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,Y,N),

    C_ADDI4SPN->List(UInt( 19),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I4SP, DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_SUB->     List(UInt( 29),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X,    DW_XPR,FN_SUB,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_XOR->     List(UInt( 34),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X,    DW_XPR,FN_XOR,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_OR->      List(UInt( 33),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X,    DW_XPR,FN_OR, N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_AND->     List(UInt( 32),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X,    DW_XPR,FN_AND,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_ANDI->    List(UInt( 22),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I,    DW_XPR,FN_AND,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_ADDI16SP->List(UInt( 19),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I16SP,DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_ADDI->    List(UInt( 19),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I,    DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_LI->      List(UInt( 19),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I,    DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_LUI->     List(UInt( 18),Y,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_U,    DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_MV->      List(UInt( 28),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X,    DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_ADD->     List(UInt( 28),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X,    DW_XPR,FN_ADD,N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N))
}

class SDecodeM(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SFENCE_VMA->List(UInt( 50),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_SFENCE,   MT_W, N,N,N,N,N,N,N,CSR.N,N,N,N,N,Y,N))
}

class SDecode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SRET->      List(UInt( 51),Y,N,N,N,N,N,N,X,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.I,N,N,N,N,Y,N))
}

class DebugDecode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    DRET->      List(UInt( 52),Y,N,N,N,N,N,N,X,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,N,N,N,N,CSR.I,N,N,N,N,Y,N))
}

class I32Decode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SLLI_RV32-> List(UInt( 234),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SL,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRLI_RV32-> List(UInt( 235),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRAI_RV32-> List(UInt( 236),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SRA,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N))
}

class I64DecodeM(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    LD->        List(UInt( 53),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    LWU->       List(UInt( 54),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_WU,N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SD->        List(UInt( 55),Y,N,N,N,N,N,Y,Y,A2_IMM, A1_RS1, IMM_S, DW_XPR,FN_ADD,   Y,M_XWR,      MT_D, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N))
}
class I64DecodeMR(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array( //0104
    C_LD->      List(UInt( 53),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_U, DW_XPR,FN_ADD,   Y,M_XRD,      MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_LDSP->    List(UInt( 53),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_SD->      List(UInt( 55),Y,N,N,N,N,N,Y,Y,A2_IMM, A1_RS1, IMM_U, DW_XPR,FN_ADD,   Y,M_XWR,      MT_D, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    C_SDSP->    List(UInt( 55),Y,N,N,N,N,N,Y,Y,A2_IMM, A1_RS1, IMM_UJ,DW_XPR,FN_ADD,   Y,M_XWR,      MT_D, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N))
}

class I64Decode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SLLI->      List(UInt( 25),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SL,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRLI->      List(UInt( 26),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRAI->      List(UInt( 27),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SRA,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),

    ADDIW->     List(UInt( 56),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_32,FN_ADD,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SLLIW->     List(UInt( 57),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_32,FN_SL,     N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRLIW->     List(UInt( 58),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_32,FN_SR,     N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRAIW->     List(UInt( 59),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_32,FN_SRA,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    ADDW->      List(UInt( 60),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32,FN_ADD,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SUBW->      List(UInt( 61),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32,FN_SUB,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SLLW->      List(UInt( 62),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32,FN_SL,     N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRLW->      List(UInt( 63),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32,FN_SR,     N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    SRAW->      List(UInt( 64),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32,FN_SRA,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N))
}

class I64DecodeR(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    C_SUBW->    List(UInt( 61),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32,FN_SUB,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_ADDW->    List(UInt( 60),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32,FN_ADD,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_ADDIW->   List(UInt( 56),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_32,FN_ADD,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),

    C_SRLI->    List(UInt( 26),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SR,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_SRAI->    List(UInt( 27),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SRA,   N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    C_SLLI->    List(UInt( 25),Y,N,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_SL,    N,M_X,        MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N))
}

class MDecode(pipelinedMul: Boolean)(implicit val p: Parameters) extends DecodeConstants
{
  val M = if (pipelinedMul) Y else N
  val D = if (pipelinedMul) N else Y
  val table: Array[(BitPat, List[BitPat])] = Array(
    MUL->       List(UInt( 65),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_MUL,   N,M_X,        MT_X, N,N,N,N,M,D,Y,CSR.N,N,N,N,N,N,N),
    MULH->      List(UInt( 66),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_MULH,  N,M_X,        MT_X, N,N,N,N,M,D,Y,CSR.N,N,N,N,N,N,N),
    MULHU->     List(UInt( 67),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_MULHU, N,M_X,        MT_X, N,N,N,N,M,D,Y,CSR.N,N,N,N,N,N,N),
    MULHSU->    List(UInt( 68),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_MULHSU,N,M_X,        MT_X, N,N,N,N,M,D,Y,CSR.N,N,N,N,N,N,N),

    DIV->       List(UInt( 69),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_DIV,   N,M_X,        MT_X, N,N,N,N,N,Y,Y,CSR.N,N,N,N,N,N,N),
    DIVU->      List(UInt( 70),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_DIVU,  N,M_X,        MT_X, N,N,N,N,N,Y,Y,CSR.N,N,N,N,N,N,N),
    REM->       List(UInt( 71),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_REM,   N,M_X,        MT_X, N,N,N,N,N,Y,Y,CSR.N,N,N,N,N,N,N),
    REMU->      List(UInt( 72),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_XPR,FN_REMU,  N,M_X,        MT_X, N,N,N,N,N,Y,Y,CSR.N,N,N,N,N,N,N))
}

class M64Decode(pipelinedMul: Boolean)(implicit val p: Parameters) extends DecodeConstants
{
  val M = if (pipelinedMul) Y else N
  val D = if (pipelinedMul) N else Y
  val table: Array[(BitPat, List[BitPat])] = Array(
    MULW->      List(UInt( 80),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32, FN_MUL,   N,M_X,        MT_X, N,N,N,N,M,D,Y,CSR.N,N,N,N,N,N,N),

    DIVW->      List(UInt( 81),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32, FN_DIV,   N,M_X,        MT_X, N,N,N,N,N,Y,Y,CSR.N,N,N,N,N,N,N),
    DIVUW->     List(UInt( 82),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32, FN_DIVU,  N,M_X,        MT_X, N,N,N,N,N,Y,Y,CSR.N,N,N,N,N,N,N),
    REMW->      List(UInt( 83),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32, FN_REM,   N,M_X,        MT_X, N,N,N,N,N,Y,Y,CSR.N,N,N,N,N,N,N),
    REMUW->     List(UInt( 84),Y,N,N,N,N,N,Y,Y,A2_RS2, A1_RS1, IMM_X, DW_32, FN_REMU,  N,M_X,        MT_X, N,N,N,N,N,Y,Y,CSR.N,N,N,N,N,N,N))
}

class ADecodeM(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    AMOADD_W->  List(UInt( 90),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_ADD,   MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOXOR_W->  List(UInt( 91),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_XOR,   MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOSWAP_W-> List(UInt( 92),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_SWAP,  MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOAND_W->  List(UInt( 93),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_AND,   MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOOR_W->   List(UInt( 94),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_OR,    MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOMIN_W->  List(UInt( 95),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_MIN,   MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOMINU_W-> List(UInt( 96),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_MINU,  MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOMAX_W->  List(UInt( 97),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_MAX,   MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOMAXU_W-> List(UInt( 98),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_MAXU,  MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),

    LR_W->      List(UInt(100),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XLR,      MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    SC_W->      List(UInt(101),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XSC,      MT_W, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N))
}

class A64DecodeM(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    AMOADD_D->  List(UInt(110),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_ADD,   MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOSWAP_D-> List(UInt(111),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_SWAP,  MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOXOR_D->  List(UInt(112),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_XOR,   MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOAND_D->  List(UInt(113),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_AND,   MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOOR_D->   List(UInt(114),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_OR,    MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOMIN_D->  List(UInt(115),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_MIN,   MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOMINU_D-> List(UInt(116),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_MINU,  MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOMAX_D->  List(UInt(117),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_MAX,   MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    AMOMAXU_D-> List(UInt(118),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XA_MAXU,  MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),

    LR_D->      List(UInt(120),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XLR,      MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N),
    SC_D->      List(UInt(121),Y,N,N,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD,   Y,M_XSC,      MT_D, N,N,N,N,N,N,Y,CSR.N,N,N,Y,N,N,N))
}

class FDecodeM(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FLW->       List(UInt(152),Y,Y,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_W, N,N,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FSW->       List(UInt(153),Y,Y,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_S, DW_XPR,FN_ADD,   Y,M_XWR,      MT_W, N,Y,N,N,N,N,N,CSR.N,N,N,N,N,N,N))
}

class FDecodeF(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FSGNJ_S->   List(UInt(130),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FSGNJX_S->  List(UInt(131),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FSGNJN_S->  List(UInt(132),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FMIN_S->    List(UInt(133),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FMAX_S->    List(UInt(134),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FADD_S->    List(UInt(135),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FSUB_S->    List(UInt(136),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FMUL_S->    List(UInt(137),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FMADD_S->   List(UInt(138),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FMSUB_S->   List(UInt(139),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FNMADD_S->  List(UInt(140),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FNMSUB_S->  List(UInt(141),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FDIV_S->    List(UInt(154),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FSQRT_S->   List(UInt(155),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N,N,N))
}

class FDecode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FCLASS_S->  List(UInt(142),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    FMV_X_S->   List(UInt(143),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    FCVT_W_S->  List(UInt(144),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    FCVT_WU_S-> List(UInt(145),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    FEQ_S->     List(UInt(146),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    FLT_S->     List(UInt(147),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    FLE_S->     List(UInt(148),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    FMV_S_X->   List(UInt(149),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FCVT_S_W->  List(UInt(150),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FCVT_S_WU-> List(UInt(151),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,N,N,N))
}

class DDecodeM(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FLD->       List(UInt(182),Y,Y,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_D, N,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FSD->       List(UInt(183),Y,Y,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_S, DW_XPR,FN_ADD,   Y,M_XWR,      MT_D, N,Y,N,N,N,N,N,CSR.N,N,N,N,Y,N,N))
}

class DDecodeMR(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array( //0104
    C_FLD->     List(UInt(182),Y,Y,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_U, DW_XPR,FN_ADD,   Y,M_XRD,      MT_D, N,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    C_FLDSP->   List(UInt(182),Y,Y,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_I, DW_XPR,FN_ADD,   Y,M_XRD,      MT_D, N,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    C_FSD->     List(UInt(183),Y,Y,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_U, DW_XPR,FN_ADD,   Y,M_XWR,      MT_D, N,Y,N,N,N,N,N,CSR.N,N,N,N,Y,N,N),
    C_FSDSP->   List(UInt(183),Y,Y,N,N,N,N,N,Y,A2_IMM, A1_RS1, IMM_UJ,DW_XPR,FN_ADD,   Y,M_XWR,      MT_D, N,Y,N,N,N,N,N,CSR.N,N,N,N,Y,N,N))
}

class DDecodeF(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FCVT_S_D->  List(UInt(160),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FCVT_D_S->  List(UInt(161),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FSGNJ_D->   List(UInt(162),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FSGNJX_D->  List(UInt(163),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FSGNJN_D->  List(UInt(164),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FMIN_D->    List(UInt(165),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FMAX_D->    List(UInt(166),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FADD_D->    List(UInt(167),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FSUB_D->    List(UInt(168),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FMUL_D->    List(UInt(169),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FMADD_D->   List(UInt(170),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FMSUB_D->   List(UInt(171),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FNMADD_D->  List(UInt(172),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FNMSUB_D->  List(UInt(173),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FDIV_D->    List(UInt(184),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FSQRT_D->   List(UInt(185),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N))
}

class DDecode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FCLASS_D->  List(UInt(174),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y,N,N),
    FCVT_W_D->  List(UInt(175),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y,N,N),
    FCVT_WU_D-> List(UInt(176),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y,N,N),
    FEQ_D->     List(UInt(177),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,N,N,N,Y,CSR.N,N,N,N,Y,N,N),
    FLT_D->     List(UInt(178),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,N,N,N,Y,CSR.N,N,N,N,Y,N,N),
    FLE_D->     List(UInt(179),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,Y,N,N,N,N,Y,CSR.N,N,N,N,Y,N,N),
    FCVT_D_W->  List(UInt(180),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FCVT_D_WU-> List(UInt(181),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N))
}

class F64Decode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FCVT_L_S->  List(UInt(190),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    FCVT_LU_S-> List(UInt(191),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    FCVT_S_L->  List(UInt(192),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,N,N,N),
    FCVT_S_LU-> List(UInt(193),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,N,N,N))
}

class D64Decode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FMV_X_D->   List(UInt(200),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y,N,N),
    FCVT_L_D->  List(UInt(201),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y,N,N),
    FCVT_LU_D-> List(UInt(202),Y,Y,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y,N,N),
    FMV_D_X->   List(UInt(203),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FCVT_D_L->  List(UInt(204),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N),
    FCVT_D_LU-> List(UInt(205),Y,Y,N,N,N,N,N,Y,A2_X,   A1_RS1, IMM_X, DW_X,  FN_X,     N,M_X,        MT_X, N,N,N,Y,N,N,N,CSR.N,N,N,N,Y,N,N))
}

class RoCCDecode(implicit val p: Parameters) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    CUSTOM0->           List(UInt(210),Y,N,Y,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM0_RS1->       List(UInt(211),Y,N,Y,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM0_RS1_RS2->   List(UInt(212),Y,N,Y,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM0_RD->        List(UInt(213),Y,N,Y,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM0_RD_RS1->    List(UInt(214),Y,N,Y,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM0_RD_RS1_RS2->List(UInt(215),Y,N,Y,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM1->           List(UInt(216),Y,N,Y,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM1_RS1->       List(UInt(217),Y,N,Y,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM1_RS1_RS2->   List(UInt(218),Y,N,Y,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM1_RD->        List(UInt(219),Y,N,Y,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM1_RD_RS1->    List(UInt(220),Y,N,Y,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM1_RD_RS1_RS2->List(UInt(221),Y,N,Y,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM2->           List(UInt(222),Y,N,Y,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM2_RS1->       List(UInt(223),Y,N,Y,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM2_RS1_RS2->   List(UInt(224),Y,N,Y,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM2_RD->        List(UInt(225),Y,N,Y,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM2_RD_RS1->    List(UInt(226),Y,N,Y,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM2_RD_RS1_RS2->List(UInt(227),Y,N,Y,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM3->           List(UInt(228),Y,N,Y,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM3_RS1->       List(UInt(229),Y,N,Y,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM3_RS1_RS2->   List(UInt(230),Y,N,Y,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,N,CSR.N,N,N,N,N,N,N),
    CUSTOM3_RD->        List(UInt(231),Y,N,Y,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM3_RD_RS1->    List(UInt(232),Y,N,Y,N,N,N,N,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N),
    CUSTOM3_RD_RS1_RS2->List(UInt(233),Y,N,Y,N,N,N,Y,Y,A2_ZERO,A1_RS1, IMM_X, DW_XPR,FN_ADD, N,M_X,  MT_X, N,N,N,N,N,N,Y,CSR.N,N,N,N,N,N,N))
}

