// See LICENSE.Berkeley for license details.

package freechips.rocketchip.rocket.constants

import Chisel._
import chisel3.{withReset,RequireAsyncReset}
import scala.math._
import freechips.rocketchip.config._
import freechips.rocketchip.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tilelink.cache._
import chisel3.{withReset,RequireAsyncReset,AsyncReset}

// Define IntenCore parameters.
case object IntenCoreKey extends Field[IntenCoreParams]
case class IntenCoreParams(
  NUM_CTXT:        Int     = 8,     // Number of ROWs per PipeUnit.
  NUM_PUS:         Int     = 2,     // Number of PipeUnits per physical core.
  NUM_PHY_CORES:   Int     = 1,     // Number of physical cores.
  NUM_MPS:         Int     = 2,     // Number of MPIPEs per PipeUnit.
  NUM_PTW_ENTRIES: Int     = 1024,  // Number of entries stored in the PTW third level.
  NUM_PHYS_ADDR_BITS: Int  = 34,    // Number of physical address bits that connects to memory
  L1_NUM_MSHR:     Int     = 2,     // Number of MSHR for each TL connection.
  NUM_L2MACRO:     Int     = 1,     // Number of L2 macros.
  NUM_L2BANKS:     Int     = 8,     // Number of TL connections.
  L2_WAYS:         Int     = 8,     // Number of L2 ways.
  L2_SETS_ALL_BANKS: Int   = 4096,  // Number of L2 sets for all banks; it is divided among the L2 banks.
  NUM_L3BANKS:     Int     = 16,    // NUmber of L3 banks.
  L3_WAYS:         Int     = 8,     // Number of L3 ways.
  L3_SETS_ALL_BANKS : Int   = 16384,// Number of L3 sets for all banks.
  NUM_MEMORYCHANNELS: Int   = 1,
  HasPCIE           : Boolean = false,
  HasBSYS           : Boolean = false,
  BACKEND_ENA       : Boolean = false,
  SIM_MAX_MEM_DEPTH : BigInt= BigInt(1L << 22), // Maximum depth of the memory used to simulate the RAM. The width is 8 Bytes.
  SIM_MEM_PART_DEPTH: BigInt= BigInt(1L << 22), // Memory part depth, used for SimAXIMem.
  SIM_MAX_NUM_PARTS : Int   = 4,                // Maximum number of parts to be used.
)
{
  // Make sure that the number of the ROWs is power of 2.
  require(isPow2(NUM_CTXT))

  require(isPow2(NUM_L2BANKS))
  require(isPow2(NUM_PTW_ENTRIES))
  require((NUM_CTXT % NUM_MPS) == 0)
  require((NUM_L3BANKS % NUM_L2BANKS) == 0)
  require((NUM_PUS == 1) || (NUM_PUS == 2))
  require(L1_NUM_MSHR > 0)
}

trait HasIntenParameters {
  implicit val p: Parameters

  // Number of context rows served by each PipeUnit.
  val NUM_CTXT      = p(IntenCoreKey).NUM_CTXT

  // Number of instantiated PipeUnits, each PIPE serves NUM_CTXT ROWs.
  val NUM_PUS       = p(IntenCoreKey).NUM_PUS

  // Number of physical cores. The total number of ROWs is NUM_PHY_CORES*NUM_PUS*NUM_CTXT
  val NUM_PHY_CORES = p(IntenCoreKey).NUM_PHY_CORES

  // Number of physical address bits that connects to memory
  val NUM_PHYS_ADDR_BITS =p(IntenCoreKey).NUM_PHYS_ADDR_BITS

  // Number of L2 mini-caches.
  val NUM_L2BANKS   = p(IntenCoreKey).NUM_L2BANKS

  // Number of L2MACRO.
  val NUM_L2MACRO   = p(IntenCoreKey).NUM_L2MACRO

  // Number of L2 ways.
  val L2_WAYS       = p(IntenCoreKey).L2_WAYS

  // Number of L2 sets for all banks.
  val L2_SETS_ALL_BANKS = p(IntenCoreKey).L2_SETS_ALL_BANKS

  // Number of L3 banks.
  val NUM_L3BANKS   = p(IntenCoreKey).NUM_L3BANKS

  // Number of L3 ways.
  val L3_WAYS       = p(IntenCoreKey).L3_WAYS

  // Number of L3 sets for all banks.
  val L3_SETS_ALL_BANKS = p(IntenCoreKey).L3_SETS_ALL_BANKS

  // Number of memory channels.
  val NUM_MEMORYCHANNELS = p(IntenCoreKey).NUM_MEMORYCHANNELS

  //Bsys is included
  val HasPCIE        = p(IntenCoreKey).HasPCIE

  //Bsys is included
  val HasBSYS        = p(IntenCoreKey).HasBSYS

  //Backend elements like hard IPs are included
  val BACKEND_ENA    = p(IntenCoreKey).BACKEND_ENA

  // Maximum depth of the memory used to simulate the RAM. The width is 4 Bytes.
  val SIM_MAX_MEM_DEPTH  = p(IntenCoreKey).SIM_MAX_MEM_DEPTH

  // Memory part depth, used for SimAXIMem.
  val SIM_MEM_PART_DEPTH = p(IntenCoreKey).SIM_MEM_PART_DEPTH

  // Maximum number of parts to be used.
  val SIM_MAX_NUM_PARTS  = p(IntenCoreKey).SIM_MAX_NUM_PARTS

  // Parameters needed for L2/L3.
  val WRITE_BYTES    = 8
  val PORT_FACTOR_ECC = 8
  val PORT_FACTOR_L2 = 8
  val PORT_FACTOR_L3 = 8
  val DRAM_CYCLES    = 105//97
  val LE_CYCLES      = 144
  val bufInnerInterior: InclusiveCachePortParameters = InclusiveCachePortParameters.full
  val bufInnerExterior: InclusiveCachePortParameters = InclusiveCachePortParameters.full
  val bufOuterInterior: InclusiveCachePortParameters = InclusiveCachePortParameters.full
  val bufOuterExterior: InclusiveCachePortParameters = InclusiveCachePortParameters.full

  // Number of Memory pipes inside the pipeUnit.
  val NUM_MPS         = p(IntenCoreKey).NUM_MPS

  // Number of entries stored in the PTW third level.
  val NUM_PTW_ENTRIES = p(IntenCoreKey).NUM_PTW_ENTRIES

  // Number of MSHR for each TL connection.
  val L1_NUM_MSHR     = p(IntenCoreKey).L1_NUM_MSHR

  // Configure the number of physical cores assigned per each core.
  val NUM_OF_CORES_PER_L2 = 4

  // Scale factor for the "no state change" watchdog counters (IOMSHR, NB_MSHR,
  // ProbeUnit, and similar). Must be large enough that legitimate queueing
  // latency under heavy multi-core cache/memory contention, or a slower
  // downstream clock domain, doesn't get misflagged as a stall.
  val DBG_WAIT_SCALE = 16

  // Get the bit widths.
  val CTXT_ID_LEN   = log2Up(NUM_CTXT)
  val NUM_T_CTXT    = NUM_PUS * NUM_CTXT
  val PIPE_ID_LEN   = max(1, log2Up(NUM_PUS))
  val CTXT_T_ID_LEN = log2Up(NUM_T_CTXT)

  // Number of ports used in the DU arbiter.
  val NumArbInp      = 2

  // Number of commands that D$ can serve per cycle.
  val NumDCCmds      = NUM_PUS

  // Number of bits to allocated for the DC ID.
  val NumDCCmdsL2    = if (NumDCCmds == 2) 1 else 0

  // More checks.
  require(NUM_L3BANKS >= NUM_MEMORYCHANNELS)
  require(NumDCCmds <= NUM_PUS)
  require(NumDCCmds <= 2)
  require(NumDCCmds >= 1)
  require((NUM_L2BANKS%NumDCCmds) == 0)
  require(NUM_PHYS_ADDR_BITS > 31)  //We use number of addr bits as the size of addressable memory but chisel expects address range of memory to calculate its size, starting 0x2000_0000 gets lost, this field cannot be less than 512MB(0x2000_0000)
}

abstract class IntenBundle(implicit val p: Parameters) extends ParameterizedBundle()(p) with HasIntenParameters
abstract class IntenModule(implicit val p: Parameters) extends Module with HasIntenParameters
class IntenParamC()(implicit val p: Parameters) extends HasIntenParameters

object StConfiguration
{
  // Define buses lengths.
  val DATA_LEN     = 64
  val ADDR_LEN     = 40
  val INSTR_LEN    = 32

  // Define the length of the cache line in bits.
  val CACHE_LINE_LEN  = INSTR_LEN * 8
  val CACHE_LINE_LOG2 = log2Up(CACHE_LINE_LEN/8)

  // Number of registers in a row.
  val NUM_ROW_REGS = 32
  val REG_ID_LEN   = log2Up(NUM_ROW_REGS)

  // Queue depth used in dataUnit.
  val DU_QUEUE_DEPTH  = 4

  // Enable disable debug.
  val DEBUG    = 2

  // Disable/enable PERF (performance counters)
  val ENA_PERF = 0

  // Extract tag ID for the current transaction.
  val NUM_BANKS_PER_WAY  = 1

  // Power opt.: Implement RAMs instead of the original constructs (MEMs or DescribedSRAM)
  val USING_RAMS = true
  val DCACHE_TAG_ARRAY_IN_RAM = USING_RAMS
  val ICACHE_TAG_ARRAY_IN_RAM = USING_RAMS

  // Power opt.: Implement comparator-based clock gating on registers
  val CMP_CLK_GATING_ENABLED = false

  // Number of Div instances.
  val NUM_DIV        = 4

  // Maximum Number of MulL instructions to be in in the pipe.
  val NUM_MUL_L      = 5

  // Maximum Number of MulH instructions to be in in the pipe.
  val NUM_MUL_H      = 1
  require(NUM_MUL_H == 1)

  // Instructions
  val INF_LOOP_INSTRS = UInt(0x0000006f)
  val INF_LOOP_RVC    = UInt(0xA001)

  // Enable/disable I$ forwarding.
  val ENA_IC_FORWARD  = false

  // Enable/disable the use of two cycles to access RF.
  val USE_TWO_CYCLES_RF = false
}

trait ScalarOpConstants {
  val MT_SZ = 3
  def MT_X  = BitPat("b???")
  def MT_B  = UInt("b000")
  def MT_H  = UInt("b001")
  def MT_W  = UInt("b010")
  def MT_D  = UInt("b011")
  def MT_BU = UInt("b100")
  def MT_HU = UInt("b101")
  def MT_WU = UInt("b110")
  def mtSize(mt: UInt) = mt(MT_SZ-2, 0)
  def mtSigned(mt: UInt) = !mt(MT_SZ-1)

  val SZ_BR = 3
  def BR_X    = BitPat("b???")
  def BR_EQ   = UInt(0, 3)
  def BR_NE   = UInt(1, 3)
  def BR_J    = UInt(2, 3)
  def BR_N    = UInt(3, 3)
  def BR_LT   = UInt(4, 3)
  def BR_GE   = UInt(5, 3)
  def BR_LTU  = UInt(6, 3)
  def BR_GEU  = UInt(7, 3)

  def A1_ZERO = UInt(0, 2)
  def A1_RS1  = UInt(1, 2)
  def A1_PC   = UInt(2, 2)
  def A1_X    = A1_ZERO

  def IMM_X  = BitPat("b???")
  def IMM_S  = UInt(0, 3)
  def IMM_SB = UInt(1, 3)
  def IMM_U  = UInt(2, 3)
  def IMM_UJ = UInt(3, 3)
  def IMM_I  = UInt(4, 3)
  def IMM_Z  = UInt(5, 3)
  def IMM_I4SP = UInt(6,3)
  def IMM_I16SP = UInt(7,3)

  def A2_ZERO = UInt(0, 2)
  def A2_SIZE = UInt(1, 2)
  def A2_RS2  = UInt(2, 2)
  def A2_IMM  = UInt(3, 2)
  def A2_X    = A2_ZERO

  def X = BitPat("b?")
  def N = BitPat("b0")
  def Y = BitPat("b1")

  val SZ_DW = 1
  def DW_X  = X
  def DW_32 = Bool(false)
  def DW_64 = Bool(true)
  def DW_XPR = DW_64
}

trait MemoryOpConstants {
  val NUM_XA_OPS = 9
  val M_SZ      = 5
  def M_X       = BitPat("b?????");
  def M_XRD     = UInt("b00000"); // int load
  def M_XWR     = UInt("b00001"); // int store
  def M_PFR     = UInt("b00010"); // prefetch with intent to read
  def M_PFW     = UInt("b00011"); // prefetch with intent to write
  def M_XA_SWAP = UInt("b00100");
  def M_FLUSH_ALL = UInt("b00101")  // flush all lines
  def M_XLR     = UInt("b00110");
  def M_XSC     = UInt("b00111");
  def M_XA_ADD  = UInt("b01000");
  def M_XA_XOR  = UInt("b01001");
  def M_XA_OR   = UInt("b01010");
  def M_XA_AND  = UInt("b01011");
  def M_XA_MIN  = UInt("b01100");
  def M_XA_MAX  = UInt("b01101");
  def M_XA_MINU = UInt("b01110");
  def M_XA_MAXU = UInt("b01111");
  def M_FLUSH   = UInt("b10000") // write back dirty data and cede R/W permissions
  def M_PWR     = UInt("b10001") // partial (masked) store
  def M_PRODUCE = UInt("b10010") // write back dirty data and cede W permissions
  def M_CLEAN   = UInt("b10011") // write back dirty data and retain R/W permissions
  def M_SFENCE  = UInt("b10100") // flush TLB
  def M_WOK     = UInt("b10111") // check write permissions but don't perform a write
  val M_FAKE    = UInt("b10111") // Mark fake transaction.

  def isAMOLogical(cmd: UInt) = cmd.isOneOf(M_XA_SWAP, M_XA_XOR, M_XA_OR, M_XA_AND)
  def isAMOArithmetic(cmd: UInt) = cmd.isOneOf(M_XA_ADD, M_XA_MIN, M_XA_MAX, M_XA_MINU, M_XA_MAXU)
  def isAMO(cmd: UInt) = isAMOLogical(cmd) || isAMOArithmetic(cmd)
  def isPrefetch(cmd: UInt) = cmd === M_PFR || cmd === M_PFW
  def isRead(cmd: UInt) = cmd === M_XRD || cmd === M_XLR || cmd === M_XSC || isAMO(cmd)
  def isWrite(cmd: UInt) = cmd === M_XWR || cmd === M_PWR || cmd === M_XSC || isAMO(cmd)
  def isWriteIntent(cmd: UInt) = isWrite(cmd) || cmd === M_PFW || cmd === M_XLR
  def isRdWr(cmd: UInt) = isWrite(cmd) && isRead(cmd)
}

object Util {
  def MuxTree[T <: Data](selId: UInt, inps: Seq[T]): T = MuxTree(selId, inps, 0, math.ceil(math.log10(inps.size)/math.log10(2.0)).toInt)
  def MuxTree[T <: Data](selId: UInt, inps: Seq[T], off: Int, level: Int): T = {
    if(level == 0) {
      inps(off)
    }
    else {
      if(off+math.pow(2,level-1).toInt >= inps.size) {
        MuxTree(selId, inps, off, level-1)
      }
      else {
        Mux(selId(level-1), MuxTree(selId, inps, off+math.pow(2,level-1).toInt, level-1), MuxTree(selId, inps, off, level-1))
      }
    }
  }

  // A tree mux with a hard-coded mask to mark the considered bits.
  def MuxTreeMask(selId: UInt, inps: UInt, mask: Int): Bool = {
    val (a, ac) = MuxTreeMask(selId, inps, mask, 0, math.ceil(math.log10(inps.getWidth)/math.log10(2.0)).toInt)
    a
  }
  def MuxTreeMask(selId: UInt, inps: UInt, mask: Int, off: Int, level: Int): (Bool, Boolean) = {
    if(level == 0) {
      if((off < inps.getWidth) && (((mask >> off) & 0x1) == 1)) {
        (inps(off), true)
      }
      else {
        (Bool(false), false)
      }
    }
    else {
      val (a, ac) = MuxTreeMask(selId, inps, mask, off+math.pow(2,level-1).toInt, level-1)
      val (b, bc) = MuxTreeMask(selId, inps, mask, off, level-1)
      if(ac && bc) {
        (Mux(selId(level-1), a, b), true)
      }
      else if(ac) {
        (a && selId(level-1), true)
      }
      else if(bc) {
        (b && !selId(level-1), true)
      }
      else {
        (Bool(false), false)
      }
    }
  }

  // A tree of 1-hot mux for the most significant .
  def Mux1HTree[T <: Data](flags: UInt, inps: Seq[T]): T = {
    val (a, ac) = Mux1HTree(flags, inps, 0, math.ceil(math.log10(inps.size)/math.log10(2.0)).toInt)
    a
  }
  def Mux1HTree[T <: Data](flags: UInt, inps: Seq[T], off: Int, level: Int): (T, Bool) = {
    if((level == 0) || (off >= inps.size)) {
      if(off < inps.size) {
        (inps(off), flags(off))
      }
      else {
        (inps(0), Bool(false))
      }
    }
    else {
      val (a, ac) = Mux1HTree(flags, inps, off+math.pow(2,level-1).toInt, level-1)
      val (b, bc) = Mux1HTree(flags, inps, off, level-1)
      (Mux(ac, a, b), ac || bc)
    }
  }

  // A tree of 1-hot mux for the least significant .
  def Mux1HTreeL[T <: Data](flags: UInt, inps: Seq[T]): T = {
    val (a, ac) = Mux1HTreeL(flags, inps, 0, math.ceil(math.log10(inps.size)/math.log10(2.0)).toInt)
    a
  }
  def Mux1HTreeL[T <: Data](flags: UInt, inps: Seq[T], off: Int, level: Int): (T, Bool) = {
    if((level == 0) || (off >= inps.size)) {
      if(off < inps.size) {
        (inps(off), flags(off))
      }
      else {
        (inps(0), Bool(false))
      }
    }
    else {
      val (a, ac) = Mux1HTreeL(flags, inps, off+math.pow(2,level-1).toInt, level-1)
      val (b, bc) = Mux1HTreeL(flags, inps, off, level-1)
      (Mux(bc, b, a), ac || bc)
    }
  }

  // This is for the least significant bit location.
  def EncTreeO(inps: UInt): UInt = {
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

  // This is for the most significant bit location.
  def EncTreeMO(inps: UInt): UInt = {
    val (in1, v1) = EncTreeM(inps, 0, math.ceil(math.log10(inps.getWidth)/math.log10(2.0)).toInt)
    in1
  }
  def EncTreeM(inps: UInt): (UInt, Bool) = EncTreeM(inps, 0, math.ceil(math.log10(inps.getWidth)/math.log10(2.0)).toInt)
  def EncTreeM(inps: UInt, off: Int, level: Int): (UInt, Bool) = {
    if(level == 0) {
      (UInt(off, log2Up(inps.getWidth)), inps(off))
    }
    else {
      if(off+math.pow(2,level-1).toInt >= inps.getWidth) {
        EncTreeM(inps, off, level-1)
      }
      else {
        val (in1, v1) = EncTreeM(inps, off+math.pow(2,level-1).toInt, level-1)
        val (in2, v2) = EncTreeM(inps, off, level-1)
        (Mux(v1, in1, in2), v1 || v2)
      }
    }
  }

  def OrTree(inps: Seq[Bool]): Bool = (inps.asUInt() =/= UInt(0))
  def OrTree(inps: Seq[UInt]): UInt = OrTree(inps, 0, math.ceil(math.log10(inps.size)/math.log10(2.0)).toInt)
  def OrTree(inps: Seq[UInt], off: Int, level: Int): UInt = {
    if(level == 0) {
      inps(off)
    }
    else {
      if(off+math.pow(2,level-1).toInt >= inps.size) {
        OrTree(inps, off, level-1)
      }
      else {
        OrTree(inps, off, level-1) | OrTree(inps, off+math.pow(2,level-1).toInt, level-1)
      }
    }
  }

  // Comparator-based clock gating element. reg_val MUST be a multiply of the width input value
  def CompClockGate(next_val: UInt, reg_val: UInt, width: Int = 8) = {
    require(width > 0)
    require(reg_val.getWidth > 0)
    require(reg_val.getWidth == next_val.getWidth)
    require(reg_val.getWidth % width == 0) // size of reg_val MUST be a multiply of width!

    val numEnBytes = (reg_val.getWidth + width - 1) / width
    val ret_val    = Wire(Vec(numEnBytes, UInt(0, width)))

    // Lower part is processed as a vector of UInts
    for (i<- 0 until numEnBytes){
      val idxHigh  = (i+1)*width-1 min (reg_val.getWidth-1)
      val idxLow   = (i+0)*width
      when(next_val(idxHigh,idxLow) =/= reg_val(idxHigh,idxLow)){
        ret_val(i) := next_val(idxHigh,idxLow)
      } .otherwise {
        ret_val(i) := reg_val(idxHigh,idxLow)
      }
    }
    // Overwrite the original reg_val data
    reg_val := ret_val.asUInt
    null // no value is returned
  }
}

class QueueReg[T <: Data](gen: T, val entries: Int,
                       pipe: Boolean = false) extends Module()
{
  /** The I/O for this queue */
  val io            = new QueueIO(gen, entries)
  require(entries > 1)

  val ram             = Mem(entries, gen)
  val deq_ptrE_reg    = Reg(init=UInt(1, log2Up(entries)))
  val enq_ptr_reg     = Reg(init=UInt(0, log2Up(entries)))
  val full_reg        = Reg(init=Bool(false))
  val empty_reg       = Reg(init=Bool(true))
  val readData_reg    = Reg(gen)
  val count_reg       = Reg(init=UInt(0, log2Up(entries+1)))

  val do_enq = io.enq.ready && io.enq.valid
  val do_deq = io.deq.ready && io.deq.valid
  when (do_enq && !do_deq) {
    count_reg := count_reg + UInt(1)
    assert(count_reg <= UInt(entries))
    when(count_reg === UInt(entries - 1)) {
      full_reg  := Bool(true)
    }
    empty_reg := Bool(false)
  }
  when (!do_enq && do_deq) {
    count_reg := count_reg - UInt(1)
    assert(count_reg >= UInt(0))
    full_reg  := Bool(false)
    when(count_reg === UInt(1)) {
      empty_reg  := Bool(true)
    }
  }

  when (do_enq) {
    ram(enq_ptr_reg) := io.enq.bits
    enq_ptr_reg := enq_ptr_reg + UInt(1)
    if(!isPow2(entries)) {
      when(enq_ptr_reg === UInt(entries - 1)) {
        enq_ptr_reg := UInt(0)
      }
    }
  }
  when (do_deq) {
    deq_ptrE_reg := deq_ptrE_reg + UInt(1)
    if(!isPow2(entries)) {
      when(deq_ptrE_reg === UInt(entries - 1)) {
        deq_ptrE_reg := UInt(0)
      }
    }

    // Read the next data.
    readData_reg    := ram(deq_ptrE_reg)
  }

  // Check for bypass logic.
  when((do_deq && do_enq && (io.count === UInt(1))) || (do_enq && (io.count === UInt(0)))) {
    readData_reg    := io.enq.bits
  }

  io.deq.valid  := !empty_reg
  io.enq.ready  := !full_reg || (Bool(pipe) && io.deq.ready)
  io.deq.bits   := readData_reg
  io.count      := count_reg
}

// An arbiter with registered output.
class ArbiterReg[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new ArbiterIO(gen, n))

  val arb          = Module(new ArbiterTree(gen, n))
  val newAvail_reg = Reg(init=Bool(false))
  val bitMap_reg   = Reg(init=UInt(0, n))

  val bitMapE      = Wire(Vec(n, Bool()))
  for(i <- n-1 to 0 by -1) {
    bitMapE(i) := arb.io.in(i).fire()
  }

  when(io.out.ready) {
    newAvail_reg := Bool(false)
    bitMap_reg   := UInt(0)
  }
  when(arb.io.out.fire()) {
    newAvail_reg := Bool(true)
    bitMap_reg   := bitMapE.asUInt
  }

  arb.io.out.ready := !newAvail_reg || io.out.ready

  val out_reg      = RegEnable(arb.io.out.bits, arb.io.out.fire())
  val chosen_reg   = RegEnable(arb.io.chosen,   arb.io.out.fire())

  for(i <- n-1 to 0 by -1) {
    arb.io.in(i).bits   := io.in(i).bits
    arb.io.in(i).valid  := io.in(i).valid && !bitMap_reg(i)
    io.in(i).ready      := io.out.ready && bitMap_reg(i)
  }

  io.chosen    := chosen_reg
  io.out.bits  := out_reg
  io.out.valid := newAvail_reg
}

// An arbiter with tree structure.
import freechips.rocketchip.rocket.constants.Util._
class ArbiterTree[T <: Data](gen: T, n: Int) extends Module {
  val io = IO(new ArbiterIO(gen, n))

  io.in(0).ready := io.out.ready
  for (i <- 1 until n) {
    io.in(i).ready := io.out.ready && ~(OrTree((0 until i).map(k => io.in(k).valid)))
  }

  io.chosen    := EncTreeO(((0 until n).map(k => io.in(k).valid)).asUInt)
  io.out.bits  := Mux1HTreeL(((0 until n).map(k => io.in(k).valid)).asUInt, (0 until n).map(k => io.in(k).bits))
  io.out.valid := OrTree((0 until n).map(k => io.in(k).valid))
}

