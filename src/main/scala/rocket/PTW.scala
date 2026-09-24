// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

// Page Table Walker
// When the TLB misses, this is triggered to find the page table entry for the virtual addr,
//  which resides in memory, and return the corresponding physical addr (plus flags) to the TLB.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.experimental._
import scala.collection.mutable.ListBuffer

import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants.Util._

class PageCacheMemoryF(NUM_ENTRIES: Int, WIDTH: Int)(implicit p: Parameters) extends CoreModule()(p) {
  val io = new Bundle {
    val n_wr     = Bool(INPUT) // Active low write enable.
    val cebE     = Bool(INPUT) // Active low clock enable.
    val addr     = UInt(INPUT, log2Ceil(NUM_ENTRIES))
    val wrDataE  = UInt(INPUT, WIDTH)
    val rdData   = UInt(OUTPUT, WIDTH)
  }
  val MAX_DEPTH   = 1024
  val n_ceb_reg   = Reg(init=Bool(true))
  n_ceb_reg      := !io.cebE
  if(NUM_ENTRIES<=MAX_DEPTH) {
    val bank  = Module(new PageCacheMemory(NUM_ENTRIES, WIDTH)).io
    bank.n_wr   := io.n_wr
    bank.n_ceb  := n_ceb_reg
    bank.addr   := io.addr
    bank.wrData := RegEnable(io.wrDataE, io.cebE)
    io.rdData   := bank.rdData
  }
  else {
    val addrD1_reg = Reg(UInt(width=log2Ceil(NUM_ENTRIES / MAX_DEPTH)))
    val bank       = 0 until NUM_ENTRIES/MAX_DEPTH map { x => Module(new PageCacheMemory(MAX_DEPTH, WIDTH)).io}
    for(i <- 0 until NUM_ENTRIES/MAX_DEPTH) {
      val sel         = UInt(i) === io.addr(log2Ceil(NUM_ENTRIES)-1, log2Ceil(MAX_DEPTH))
      bank(i).n_wr   := io.n_wr
      bank(i).n_ceb  := n_ceb_reg || !sel
      bank(i).addr   := io.addr(log2Ceil(MAX_DEPTH)-1, 0)
      bank(i).wrData := RegEnable(io.wrDataE, io.cebE)
    }
    when(!n_ceb_reg) {
      addrD1_reg := io.addr(log2Ceil(NUM_ENTRIES)-1, log2Ceil(MAX_DEPTH))
    }
    io.rdData   := MuxTree(addrD1_reg, 0 until NUM_ENTRIES/MAX_DEPTH map { x => bank(x).rdData})
  }
}

// NOTE: n_ceb is asserted for one clock cycle and then it will be de-asserted for another clock cycle.
// during the first cycle, all signals are validated. However, the memory needs to ignore all the signals except n_ceb in the second cycle.
class PageCacheMemory(NUM_ENTRIES: Int, WIDTH: Int)(implicit p: Parameters) extends CoreModule()(p) {
  val io = new Bundle {
    val n_wr     = Bool(INPUT) // Active low write enable.
    val n_ceb    = Bool(INPUT) // Active low clock enable.
    val addr     = UInt(INPUT, log2Ceil(NUM_ENTRIES))
    val wrData   = UInt(INPUT, WIDTH)
    val rdData   = UInt(OUTPUT, WIDTH)
  }
  val rdData_reg = Reg(UInt(width=WIDTH))
  val BANK_BLOCK = Mem(NUM_ENTRIES, Bits(width=WIDTH))

  val rdData     = BANK_BLOCK(io.addr)

  when(!io.n_wr && !io.n_ceb) {
    BANK_BLOCK.write(io.addr, io.wrData)
  }

  when(!io.n_ceb) {
    rdData_reg := rdData
  }

  // The following code asserts that we do not receive any two successive RD/WR operations.
  val n_cebD1_reg   = Reg(next=io.n_ceb, init=Bool(true))

  assert(!(!n_cebD1_reg && !io.n_ceb), "Receive two successive access operations in the PTW.")

  // An extra delay is added to consider the timing of the internal memories.
  // IMPRTANT: We do not need it implement this MUX, this MUX is added for testing.
  io.rdData := Mux(!n_cebD1_reg, UInt(0), rdData_reg)
}

class PTWReq(implicit p: Parameters) extends CoreBundle()(p) {
  val addr    = UInt(width = vpnBits)
  val ctxtId  = UInt(width = CTXT_ID_LEN)
}

class PTWResp(implicit p: Parameters) extends CoreBundle()(p) {
  val ae                   = Bool()
  val pte                  = new PTE
  val level                = UInt(width = log2Ceil(pgLevels))
  val fragmented_superpage = Bool()
  val homogeneous          = Bool()
  val r                    = Bool()
  val w                    = Bool()
  val x                    = Bool()
}

class TLBPTWIO(implicit p: Parameters) extends CoreBundle()(p)
    with HasCoreParameters {
  val req        = Decoupled(Valid(new PTWReq))
  val resp       = Valid(new PTWResp).flip
  val ptbr       = new PTBR().asInput
  val status     = new MStatus().asInput
  val pmp        = Vec(nPMPs, new PMP).asInput
  val customCSRs = coreParams.customCSRs.asInput
  val ctxtId     = UInt(width = CTXT_ID_LEN)
}

class PTWPerfEvents extends Bundle {
  val l2miss = Bool()
}

class DatapathPTWIO(implicit p: Parameters) extends CoreBundle()(p)
    with HasCoreParameters {
  val ptbr       = new PTBR()
  val status     = new MStatus()
  val pmp        = Vec(nPMPs, new PMP)
  val customCSRs = coreParams.customCSRs
}

class PTE(isDecode : Boolean = false)(implicit p: Parameters) extends CoreBundle()(p) with HasTileParameters {
  val ppn = UInt(width = (if(isDecode) 54 else ppnBits))
  val reserved_for_software = Bits(width = 2)
  val d = Bool()
  val a = Bool()
  val g = Bool()
  val u = Bool()
  val x = Bool()
  val w = Bool()
  val r = Bool()
  val v = Bool()

  def table(dummy: Int = 0) = v && !r && !w && !x
  def leaf(dummy: Int = 0)  = v && (r || (x && !w)) && a
  def ur(dummy: Int = 0)    = sr() && u
  def uw(dummy: Int = 0)    = sw() && u
  def ux(dummy: Int = 0)    = sx() && u
  def sr(dummy: Int = 0)    = leaf() && r
  def sw(dummy: Int = 0)    = leaf() && w && d
  def sx(dummy: Int = 0)    = leaf() && x

  override def cloneType = { new PTE(isDecode).asInstanceOf[this.type] }
}

@chiselName
class PTW(n: Int)(implicit edge: TLEdgeOut, p: Parameters) extends CoreModule()(p) {
  val io = new Bundle {
    val requestor      = Vec(n, new TLBPTWIO).flip
    val mem            = new HellaCacheIO(paddrBits)
    val dpath          = Vec(NUM_CTXT, new DatapathPTWIO()).asInput
    val sfence         = Valid(new SFenceReq).asInput
    val perf           = new PTWPerfEvents().asOutput
    val ptwEarlyNotif  = Bool(INPUT)
    val sfenceFinish   = Bool(OUTPUT)

    val ptwStatus      = UInt(OUTPUT, 4)
    val ptwCtxtId      = UInt(OUTPUT, 4)
    val ptwVAddr       = UInt(OUTPUT, 28)
    val ptwPAddr       = UInt(OUTPUT, 24)
    val ptwFenceC      = UInt(OUTPUT, 4)
    val ptwFenceA      = UInt(OUTPUT, 28)
  }

  val s_ready :: s_check :: s_req :: s_wait1 :: s_wait2 :: s_wait3 :: Nil = Enum(UInt(), 6)
  val state = Reg(init=s_ready)

  val justReset_reg       = Reg(init=Bool(true))
  val resetInProg_reg     = Reg(init=Bool(false))
  val useRstCntr_reg      = Reg(init=Bool(false))
  val entryValidE         = Wire(Bool())
  val memTrkCntr_reg      = Reg(init=UInt(0, 4))
  val available_reg       = Reg(init=Bool(false))
  val rstAddr_reg         = Reg(init=UInt(0, log2Ceil(NUM_PTW_ENTRIES)))
  val finishRst_reg       = Reg(init=Bool(false))
  val sfenceActive_reg    = Reg(init=Bool(false))
  val sfenceInProg_reg    = Reg(init=Bool(false))
  val sfenceResume_reg    = Reg(init=Bool(false))
  val sfenceCtxtId_reg    = Reg(init=UInt(0, CTXT_ID_LEN))
  val sfenceAddr_reg      = Reg(init=UInt(0, vaddrBits - pgIdxBits))
  val sfenceClearAll_reg  = Reg(init=Bool(false))
  val sfenceJstStrt_reg   = Reg(init=Bool(false))
  val sfenceFinish_reg    = Reg(init=Bool(false))

  val memRespD1_reg       = Reg(new HellaCacheResp(paddrBits))
  val memRespValidD1_reg  = Reg(next=io.mem.resp.valid, init=Bool(false))
  when(io.mem.resp.valid) {
    memRespD1_reg := io.mem.resp.bits
  }

  val arb           = Module(new RRArbiter(Valid(new PTWReq), n))
  arb.io.in        <> io.requestor.map(_.req)
  arb.io.out.ready := (state === s_ready) && available_reg && !sfenceInProg_reg && !resetInProg_reg

  val ptwEarlyNotifD4_reg = Reg(next=io.ptwEarlyNotif, init=Bool(false))
  val ptwEarlyNotifD5_reg = Reg(next=ptwEarlyNotifD4_reg, init=Bool(false))
  val ptwEarlyNotifD6_reg = Reg(next=ptwEarlyNotifD5_reg, init=Bool(false))
  val ptwEarlyNotifD7_reg = Reg(next=ptwEarlyNotifD6_reg, init=Bool(false))
  available_reg := !(ptwEarlyNotifD5_reg || ptwEarlyNotifD6_reg || ptwEarlyNotifD7_reg)
  assert(!memRespValidD1_reg || ptwEarlyNotifD7_reg)

  val resp_valid = Reg(init=Vec.fill(io.requestor.size) {Bool(false)})
  val disableDCacheClockGate = OrTree(io.dpath.map(_.customCSRs.disableDCacheClockGate))

  // Detect when rstAddr_reg is going to roll-over.
  val rstAddrInc    = Wire(UInt(width=log2Ceil(NUM_PTW_ENTRIES)))
  rstAddrInc       := rstAddr_reg + UInt(1)
  val finishRstNxt  = rstAddrInc.andR

  // Set the default value for the valid.
  for(reqId <- 0 until io.requestor.size) {
    resp_valid(reqId) := Bool(false)
  }

  when(io.sfence.valid) {
    sfenceActive_reg   := Bool(true)
    sfenceInProg_reg   := Bool(true)
    sfenceCtxtId_reg   := io.sfence.bits.ctxtId
    sfenceClearAll_reg := !io.sfence.bits.rs1 // NOTE: We do not consider the g flag in clearing.
    sfenceAddr_reg     := io.sfence.bits.addr(vaddrBits - 1, pgIdxBits)
    sfenceJstStrt_reg  := Bool(true)
    rstAddr_reg        := UInt(0)
    finishRst_reg      := Bool(false)
    assert(!sfenceActive_reg)
  }
  sfenceFinish_reg := Bool(false)
  io.sfenceFinish  := sfenceFinish_reg

  val clock_en = (state =/= s_ready) || arb.io.out.valid || sfenceActive_reg || disableDCacheClockGate || resetInProg_reg || justReset_reg
  val gated_clock =
    if (!usingSupervisor || !tileParams.dcache.get.clockGate) clock
    else ClockGate(clock, clock_en, "ptw_clock_gate")
  chisel3.withClock (gated_clock) { // entering gated-clock domain

  // control state machine
  val next_state = Wire(init = state)
  state := OptimizationBarrier(next_state)

  val count_reg    = Reg(UInt(width = log2Ceil(pgLevels)))
  val countOut_reg = Reg(UInt(width = log2Ceil(pgLevels)))
  val resp_ae      = RegNext(false.B)

  val r_req      = Reg(new PTWReq)
  val r_req_dest = Reg(Bits())
  val pte_reg    = Reg(new PTE)
  val pteOut_reg = Reg(new PTE)

  val levelMemSel_reg   = Reg(init=Vec.fill(pgLevels){Bool(false)})
  val levelLessEq_reg   = Reg(init=Vec.fill(pgLevels){Bool(false)})
  val memCtxtId_reg     = Reg(UInt(width=CTXT_ID_LEN))
  val memVpnAddrE       = Wire(UInt(width=vpnBits))
  val memVpnAddr_reg    = Reg(UInt(width=vpnBits))
  val memCtxtIdChk_reg  = Reg(UInt(width=CTXT_ID_LEN))
  val memVpnAddrChk_reg = Reg(UInt(width=vpnBits))
  val wr_reg            = Reg(init=Bool(false))
  val rdWrBusy_reg      = Reg(init=Bool(false))
  val cebE              = Wire(Vec(pgLevels, Bool()))
  val isTagEqD4_reg     = Reg(init=Vec.fill(pgLevels) {Bool(false)})
  val pteMemD4_reg      = Reg(Vec(pgLevels, new PTE()))
  val reqD1_reg         = Reg(init=Bool(false))
  val reqD2_reg         = Reg(init=Bool(false))
  val reqD3_reg         = Reg(init=Bool(false))
  val reqD4_reg         = Reg(init=Bool(false))
  require(isPow2(NUM_PTW_ENTRIES))

  // The address must stay the same for two successive cycles.
  when(reqD1_reg) {
    memCtxtIdChk_reg  := memCtxtId_reg
    memVpnAddrChk_reg := memVpnAddr_reg
  }

  for (i <- 0 until pgLevels) {
    levelMemSel_reg(i) := (count_reg === UInt(i))
    levelLessEq_reg(i) := (count_reg <=  UInt(i))
  }

  // Construct a mask used to mark the lower bits of PPN.
  def getMask(i: Int):UInt = if(i==pgLevels-2) Fill(pgLevelBits, levelLessEq_reg(i)) else Cat(Fill(pgLevelBits, levelLessEq_reg(i)), getMask(i+1))

  val (pteD1, invalid_paddrD1) = {
    val tmp  = new PTE(true).fromBits(memRespD1_reg.data_word_bypass)
    val res  = Wire(new PTE())
    res     := tmp // Re-adjust the ppn width to ppnBits.

    // for superpage mappings, make sure PPN LSBs are zero
    when((tmp.r || tmp.w || tmp.x) && ((getMask(0) & tmp.ppn((pgLevels-1)*pgLevelBits-1, 0)) =/= 0)) {
      res.v := false
    }
    (res, (tmp.ppn >> ppnBits) =/= 0)
  }

  val isTagEqD4U_reg = isTagEqD4_reg.asUInt()
  val isTable        = ((0 until pgLevels).map(i => pteMemD4_reg(i).table() && Bool(i < (pgLevels-1)))).asUInt()
  val isValidT       = isTagEqD4U_reg &  isTable
  val isValidNT      = isTagEqD4U_reg & ~isTable
  val isValidNTNZ    = isValidNT =/= UInt(0)
  val isValidTNZ     = isValidT =/= UInt(0)
  when(reqD4_reg) {
    // These are used if we are going to explore more.
    when(isValidTNZ) {
      pte_reg   := Mux1HTree(isValidT, pteMemD4_reg)
      count_reg := EncTreeMO(isValidT) + UInt(1)
    }

    // These are used to generate the output.
    pteOut_reg             := Mux1HTree(isValidNT, pteMemD4_reg)
    resp_ae                := Bool(false)
    resp_valid(r_req_dest) := isValidNTNZ
    countOut_reg           := EncTreeMO(isValidNT)

    // State transition.
    next_state := Mux(isValidNTNZ, s_ready, s_req)
  }

  // Initialize the PageCaches.
  entryValidE := Bool(false)
  for (i <- 0 until pgLevels) {
    cebE(i) := Bool(false)
  }
  when(justReset_reg) {
    for (i <- 0 until pgLevels) {
      cebE(i) := Bool(true)
    }
    wr_reg          := Bool(true)
    memCtxtId_reg   := UInt(0)
    rstAddr_reg     := UInt(0)
    finishRst_reg   := Bool(false)
    resetInProg_reg := Bool(true)
    useRstCntr_reg  := Bool(true)
    rdWrBusy_reg    := Bool(true)
    justReset_reg   := Bool(false)
    entryValidE     := Bool(false)
  }
  when(resetInProg_reg && !rdWrBusy_reg) {
    when(finishRst_reg) {
      memCtxtId_reg := memCtxtId_reg + UInt(1)
    }

    when(finishRst_reg && (memCtxtId_reg === UInt(NUM_CTXT - 1))) {
      resetInProg_reg := Bool(false)
      useRstCntr_reg  := Bool(false)
    }
    .otherwise {
      rdWrBusy_reg   := Bool(true)
      useRstCntr_reg := Bool(true)
      for (i <- 0 until pgLevels) {
        cebE(i)    := Bool(true)
      }
      wr_reg         := Bool(true)
      entryValidE    := Bool(false)
    }
    rstAddr_reg   := rstAddrInc
    finishRst_reg := finishRstNxt
  }

  when(rdWrBusy_reg) {
    rdWrBusy_reg := Bool(false)
    wr_reg       := Bool(false)
    for (i <- 0 until pgLevels) {
      cebE(i)    := Bool(false)
    }

    // Stop sfence for a while to take the outstanding request.
    when(sfenceInProg_reg && useRstCntr_reg && arb.io.out.valid && arb.io.out.bits.valid && (state === s_ready)) {
      sfenceInProg_reg := Bool(false)
      sfenceResume_reg := Bool(true)
    }

    when(sfenceInProg_reg && (finishRst_reg || (!sfenceClearAll_reg && !sfenceJstStrt_reg))) {
      sfenceActive_reg := Bool(false)
      sfenceInProg_reg := Bool(false)
      sfenceFinish_reg := Bool(true)
      sfenceResume_reg := Bool(false)
    }
  }

  // Resume the sfence invalidating.
  when(sfenceResume_reg) {
    sfenceInProg_reg := Bool(true)
    sfenceResume_reg := Bool(false)
  }

  memVpnAddrE := memVpnAddr_reg
  when(memRespValidD1_reg && (!sfenceActive_reg || (r_req.ctxtId =/= sfenceCtxtId_reg))) {
    memCtxtId_reg  := r_req.ctxtId
    memVpnAddrE    := r_req.addr
    useRstCntr_reg := Bool(false)
    rdWrBusy_reg   := Bool(true)
    wr_reg         := Bool(true)

    // Mark to validate the entry.
    entryValidE    := pteD1.v && !invalid_paddrD1

    for (i <- 0 until pgLevels) {
      cebE(i) := levelMemSel_reg(i)
    }
  }

  reqD1_reg := Bool(false)
  when (arb.io.out.fire() && arb.io.out.bits.valid) {
    memCtxtId_reg  := arb.io.out.bits.bits.ctxtId
    memVpnAddrE    := arb.io.out.bits.bits.addr
    useRstCntr_reg := Bool(false)
    r_req          := arb.io.out.bits.bits
    r_req_dest     := arb.io.chosen
    rdWrBusy_reg   := Bool(true)
    for (i <- 0 until pgLevels) {
      cebE(i)    := Bool(true)
    }
    reqD1_reg := Bool(true)
  }
  reqD2_reg := reqD1_reg
  reqD3_reg := reqD2_reg
  reqD4_reg := reqD3_reg

  // Sfence clearing.
  when(sfenceInProg_reg && !rdWrBusy_reg && available_reg) {
    memCtxtId_reg     := sfenceCtxtId_reg
    memVpnAddrE       := sfenceAddr_reg
    rdWrBusy_reg      := Bool(true)
    wr_reg            := Bool(true)
    entryValidE       := Bool(false)
    sfenceJstStrt_reg := Bool(false)
    when(!sfenceJstStrt_reg) {
      rstAddr_reg   := rstAddrInc
      finishRst_reg := finishRstNxt
    }
    for (i <- 0 until pgLevels) {
      cebE(i)    := Bool(true)
    }
    useRstCntr_reg := sfenceClearAll_reg
  }
  memVpnAddr_reg := memVpnAddrE

  def REDUCE_FACTOR = 2
  for (i <- 0 until pgLevels) {
    def numEntries = (NUM_PTW_ENTRIES / scala.math.pow(2,REDUCE_FACTOR*(pgLevels-1 - i))).toInt
    def idxBits    = log2Ceil(numEntries)
    def tagBits    = vpnBits - idxBits - (pgLevels-1 - i) * pgLevelBits
    class Entry extends Bundle {
      val ppn    = UInt(width = ppnBits)
      val d      = Bool()
      val a      = Bool()
      val g      = Bool()
      val u      = Bool()
      val x      = Bool()
      val w      = Bool()
      val r      = Bool()
      val v      = Bool()
      val tag    = UInt(width = tagBits)
      val valid  = Bool()
      override def cloneType = new Entry().asInstanceOf[this.type]
    }

    // Split the address into tag and accessing address.
    val (r_tagE, r_idxE)   = Split(memVpnAddrE(vpnBits - 1, (pgLevels-1 - i) * pgLevelBits), idxBits)
    val (r_tag, r_idx)     = Split(memVpnAddr_reg(vpnBits - 1, (pgLevels-1 - i) * pgLevelBits), idxBits)
    val (r_tagD3, r_idxD3) = Split(memVpnAddrChk_reg(vpnBits - 1, (pgLevels-1 - i) * pgLevelBits), idxBits)

    // Update the tag according to the level.
    val wDataLocalE    = Wire(new Entry())
    wDataLocalE       := pteD1
    wDataLocalE.tag   := r_tagE
    wDataLocalE.valid := entryValidE

    val bank      = Module(new PageCacheMemoryF(NUM_CTXT * numEntries, (new Entry()).getWidth)).io
    bank.addr    := Cat(memCtxtId_reg, Mux(useRstCntr_reg, rstAddr_reg(idxBits-1, 0), r_idx))
    bank.n_wr    := !wr_reg

    // Early signals.
    bank.wrDataE := wDataLocalE.asUInt
    bank.cebE    := cebE(i)

    val pteX        = new Entry().fromBits(bank.rdData)
    when(reqD3_reg) {
      // Make sure that we do not use any entry being reset by the sfence.
      isTagEqD4_reg(i) := pteX.valid && (pteX.tag === r_tagD3) && ((memCtxtIdChk_reg =/= sfenceCtxtId_reg) || !sfenceActive_reg)
      pteMemD4_reg(i)  := pteX
    }
  }

  val traverse = pteD1.table() && !invalid_paddrD1 && count_reg < pgLevels-1
  val pte_addr = if (!usingSupervisor) 0.U else {
    val vpn_idxs = (0 until pgLevels).map(i => (r_req.addr >> (pgLevels-i-1)*pgLevelBits)(pgLevelBits-1,0))
    val vpn_idx  = vpn_idxs(count_reg)
    Cat(pte_reg.ppn, vpn_idx) << log2Ceil(xLen/8)
  }
  val pte_addrOut = if (!usingSupervisor) 0.U else {
    val vpn_idxs = (0 until pgLevels).map(i => (r_req.addr >> (pgLevels-i-1)*pgLevelBits)(pgLevelBits-1,0))
    val vpn_idx  = vpn_idxs(countOut_reg)
    Cat(pteOut_reg.ppn, vpn_idx) << log2Ceil(xLen/8)
  }
  io.perf.l2miss := false

  // Make sure that the requested address pte_addr is accessible.
  val mpu_physaddrM = Cat(pte_reg.ppn, UInt(0, pgIdxBits)) // We remove the last pgIdxBits. This is done as the addresses supported by the edge can be fragmented. That also requires each range to cover more than a page.
  val legal_address = OrTree(edge.manager.findSafe(mpu_physaddrM))
  def fastCheck(member: TLManagerParameters => Boolean) =
    legal_address && edge.manager.fastProperty(mpu_physaddrM, member, (b:Boolean) => Bool(b))
  val prot_r      = fastCheck(_.supportsGet)

  io.mem.req.valid     := (state === s_req) && prot_r
  io.mem.req.bits.phys := Bool(true)
  io.mem.req.bits.cmd  := M_XRD
  io.mem.req.bits.typ  := log2Ceil(xLen/8)
  io.mem.req.bits.addr := pte_addr
  io.mem.s1_kill       := Bool(false)
  io.mem.s2_kill       := Bool(false)

  // Make sure that state is s_wait1 after a fire.
  val fireD1_reg   = Reg(init=Bool(false))
  fireD1_reg      := io.mem.req.fire
  assert((state === s_wait1) || !fireD1_reg)

  // TODO: Do we need to support pageGranularityPMPs?
  require(pmpGranularity < (1 << pgIdxBits))

  val pmaPgLevelHomogeneous = (0 until pgLevels) map { i =>
    val pgSize = BigInt(1) << (pgIdxBits + ((pgLevels - 1 - i) * pgLevelBits))
    TLBPageLookup(edge.manager.managers, xLen, p(CacheBlockBytes), pgSize)(pte_addrOut).homogeneous
  }
  val pmaHomogeneous = pmaPgLevelHomogeneous(countOut_reg)

  val pmpR     = Wire(Bool())
  val pmpW     = Wire(Bool())
  val pmpX     = Wire(Bool())
  val pmpHomog = Wire(Bool())
  if(nPMPs > 0) {
    // Find the PMP.
    val pmpF           = MuxTree(r_req.ctxtId, io.dpath.map(_.pmp))
    val pmpHomogeneous = new PMPHomogeneityChecker(pmpF).apply(pte_addrOut >> pgIdxBits << pgIdxBits, countOut_reg)

    val ppnResp        = pte_addrOut >> pgIdxBits
    val mpu_physaddr   = Cat(ppnResp(ppnBits-1, 0), UInt(0, pgIdxBits))
    val pmp            = Module(new PMPChecker(32))
    pmp.io.addr       := mpu_physaddr
    pmp.io.size       := MT_W
    pmp.io.pmp        := (pmpF: Seq[PMP])
    pmp.io.prv        := PRV.S
    pmpR              := pmp.io.r
    pmpW              := pmp.io.w
    pmpX              := pmp.io.x
    pmpHomog          := pmpHomogeneous
  }
  else {
    pmpR     := Bool(true)
    pmpW     := Bool(true)
    pmpX     := Bool(true)
    pmpHomog := Bool(true)
  }
  val homogeneous    = pmaHomogeneous && pmpHomog

  for (i <- 0 until io.requestor.size) {
    io.requestor(i).resp.valid            := resp_valid(i)
    io.requestor(i).resp.bits.ae          := resp_ae
    io.requestor(i).resp.bits.pte         := pteOut_reg
    io.requestor(i).resp.bits.level       := countOut_reg
    io.requestor(i).resp.bits.r           := pmpR
    io.requestor(i).resp.bits.w           := pmpW
    io.requestor(i).resp.bits.x           := pmpX

    // Homogeneous evaluation.
    io.requestor(i).resp.bits.homogeneous          := homogeneous
    io.requestor(i).resp.bits.fragmented_superpage := Bool(false)

    // Connect the corresponding status.
    val dpathREQ                = MuxTree(io.requestor(i).ctxtId, io.dpath)
    io.requestor(i).ptbr       := dpathREQ.ptbr
    io.requestor(i).status     := dpathREQ.status
    io.requestor(i).pmp        := dpathREQ.pmp
    io.requestor(i).customCSRs := dpathREQ.customCSRs
  }

  switch (state) {
    is (s_ready) {
      when (arb.io.out.fire() && arb.io.out.bits.valid) {
        next_state := s_check
      }
      count_reg := pgLevels - minPgLevels - io.dpath(arb.io.out.bits.bits.ctxtId).ptbr.additionalPgLevels
    }
    is (s_req) {
      // Note: Here we check on the ready only, however, the valid may be zero as it is protected by prot_r.
      // That means in the case that prot_r=0; we do not issue a request to the memory.
      when(io.mem.req.ready) {
        next_state := s_wait1
      }
    }
    is (s_wait1) {
      next_state := s_wait2
    }
    is (s_wait2) {
      next_state := s_wait3
      // As the PTW is not connected to the TLB, we need to check for the access enable internally.
      // https://github.com/riscv/riscv-isa-manual/issues/109
      when (!prot_r) {
        resp_ae                := true
        next_state             := s_ready
        resp_valid(r_req_dest) := true
      }
    }
  }

  def makePTE(ppn: UInt, default: PTE) = {
    val pte = Wire(init = default)
    pte.ppn := ppn
    pte
  }
  when(arb.io.out.fire()) {
    pte_reg := makePTE(io.dpath(arb.io.out.bits.bits.ctxtId).ptbr.ppn, pte_reg)
  }
  .elsewhen(memRespValidD1_reg) {
    pte_reg := pteD1
  }

  when (io.mem.s2_nack && (state === s_wait2)) {
    next_state := s_req
  }

  when (memRespValidD1_reg) {
    assert(state === s_wait2 || state === s_wait3)
    when (traverse) {
      next_state := s_req
      count_reg := count_reg + 1
    }
    .otherwise {
      val ae                  = pteD1.v && invalid_paddrD1
      resp_ae                := ae
      next_state             := s_ready
      resp_valid(r_req_dest) := true
      pteOut_reg             := pteD1
      countOut_reg           := count_reg
    }
  }

  val stateX     = Wire(UInt(width=3))
  stateX        := state
  when((state === s_check) && reqD4_reg) {
    stateX      := isTagEqD4U_reg
  }
  io.ptwStatus   := Cat(sfenceActive_reg, stateX)
  io.ptwCtxtId   := r_req.ctxtId
  io.ptwVAddr    := r_req.addr
  io.ptwPAddr    := Mux(resp_ae, UInt(0xddeadd), pteOut_reg.ppn)
  io.ptwFenceC   := sfenceCtxtId_reg
  io.ptwFenceA   := sfenceAddr_reg

  for (i <- 0 until pgLevels) {
    val leaf = memRespValidD1_reg && !traverse && count_reg === i
    ccover(leaf && pteD1.v && !invalid_paddrD1, s"L$i", s"successful page-table access, level $i")
    ccover(leaf && pteD1.v && invalid_paddrD1, s"L${i}_BAD_PPN_MSB", s"PPN too large, level $i")
    ccover(leaf && !memRespD1_reg.data_word_bypass(0), s"L${i}_INVALID_PTE", s"page not present, level $i")
    if (i != pgLevels-1)
      ccover(leaf && !pteD1.v && memRespD1_reg.data_word_bypass(0), s"L${i}_BAD_PPN_LSB", s"PPN LSBs not zero, level $i")
  }
  ccover(memRespValidD1_reg && count_reg === pgLevels-1 && pteD1.table(), s"TOO_DEEP", s"page table too deep")
  ccover(io.mem.s2_nack, "NACK", "D$ nacked page-table access")
  ccover(state === s_wait2 && io.mem.s2_xcpt.ae.ld, "AE", "access exception while walking page table")

  } // leaving gated-clock domain

  private def ccover(cond: Bool, label: String, desc: String)(implicit sourceInfo: SourceInfo) =
    if (usingSupervisor) cover(cond, s"PTW_$label", "MemorySystem;;" + desc)
}

/** Mix-ins for constructing tiles that might have a PTW */
trait CanHavePTW extends HasTileParameters with HasHellaCache { this: BaseTile =>
  val module: CanHavePTWModule
  var nPTWPorts = NUM_MPS // Initial PTW ports connected to the D$.
  nDCachePorts += usingPTW.toInt
}

trait CanHavePTWModule extends HasHellaCacheModule with HasIntenParameters {
  val outer: CanHavePTW
  val ptwPortsH0 = ListBuffer[TLBPTWIO]()
  val ptwPortsH1 = ListBuffer[TLBPTWIO]()
  val ptw      = 0 to NUM_PUS - 1 map { x => Module(new PTW(outer.nPTWPorts)(outer.dcache.node(0).edges.out(0), outer.p)) }
  // Note: The dcache PTW is connected externally.
  if (outer.usingPTW) {
    for(ips <- 0 until NUM_PUS) {
      dcachePorts += ptw(ips).io.mem
    }
  }
}

