// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import superThread._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants.Util._

trait HasMissInfo extends HasL1HellaCacheParameters {
  val tag_match = Bool()
  val old_meta  = new L1Metadata
}

class L1MetaRdWrAddrReq(implicit p: Parameters) extends L1MetaRdWrReq()(p) {
  val addr   = Bits(width = untagBits)
}

class L1DataReadReq(implicit p: Parameters) extends L1HellaCacheBundle()(p) {
  val way_en = Bits(width = nWays)
  val addr   = Bits(width = untagBits)
}

class L1DataWriteReq(implicit p: Parameters) extends L1DataReadReq()(p) {
  val wmask  = Bits(width = rowWords)
}

class L1RefillReq(implicit p: Parameters) extends L1DataReadReq()(p) {
  val done   = Bool()
}

class Replay(addrWidth : Int = -1)(implicit p: Parameters) extends HellaCacheReqInternal(addrWidth)(p) with HasCoreData

class ReplayInternal(addrWidth : Int = -1)(implicit p: Parameters) extends HellaCacheReqInternal(addrWidth)(p)
    with HasL1HellaCacheParameters {
  val sdq_id    = UInt(width = log2Up(nSDQ_DC))
}

class MSHRReq(addrWidth : Int = -1)(implicit p: Parameters) extends Replay(addrWidth)(p) with HasMissInfo

class MSHRReqInternal(addrWidth : Int = -1)(implicit p: Parameters) extends ReplayInternal(addrWidth)(p) with HasMissInfo

class WritebackReq(params: TLBundleParameters)(implicit p: Parameters) extends L1HellaCacheBundle()(p) {
  val tag       = Bits(width = tagBits)
  val idx       = Bits(width = idxBits)
  val source    = UInt(width = params.sourceBits)
  val param     = UInt(width = TLPermissions.cWidth)
  val way_en    = Bits(width = nWays)
  val voluntary = Bool()
  val coh_state = new ClientMetadata

  override def cloneType = new WritebackReq(params)(p).asInstanceOf[this.type]
}

class HellaBusArb(implicit p: Parameters) extends CoreBundle()(p) with HasL1HellaCacheParameters {
  val req         = new HellaCacheReq(paddrBits)
  val dataAccEna  = Bool()
  val wbValid     = Bool()

  val way_en    = UInt(width = nWays)
  val coh_state = new ClientMetadata
  val tagWrEna  = Bool()
}

class AMOALU_MOD(operandBits: Int, blockOffBits : Int)(implicit p: Parameters) extends Module {
  require(operandBits == 32 || operandBits == 64)
  val io = new Bundle {
    val m2_addr = Bits(INPUT, blockOffBits)
    val m2_cmd  = Bits(INPUT, M_SZ)
    val m2_typ  = Bits(INPUT, MT_SZ)
    val m2_rhs  = Bits(INPUT, operandBits)
    val m3_lhs  = Bits(INPUT, operandBits)
    val m5_out  = Bits(OUTPUT, operandBits)
  }

  val m2_storegen = new StoreGen(io.m2_typ, io.m2_addr, io.m2_rhs, operandBits/8)
  val m3_rhs      = Reg(next=m2_storegen.wordData)

  val m2_isAdd  = io.m2_cmd === M_XA_ADD
  val m3_isAdd  = Reg(next=m2_isAdd, init=Bool(false))
  val m4_isAdd  = Reg(next=m3_isAdd, init=Bool(false))
  val m5_isAdd  = Reg(next=m4_isAdd, init=Bool(false))

  val m3_sgned  = Reg(next=io.m2_cmd === M_XA_MIN || io.m2_cmd === M_XA_MAX, init=Bool(false))
  val m3_max    = Reg(next=io.m2_cmd === M_XA_MAX || io.m2_cmd === M_XA_MAXU, init=Bool(false))
  val m3_min    = Reg(next=io.m2_cmd === M_XA_MIN || io.m2_cmd === M_XA_MINU, init=Bool(false))
  val m3_word   = Reg(next=io.m2_typ === MT_W || io.m2_typ === MT_WU || io.m2_typ === MT_B || io.m2_typ === MT_BU, init=Bool(false))
  val m3_isAnd  = Reg(next=io.m2_cmd === M_XA_AND, init=Bool(false))
  val m3_isOr   = Reg(next=io.m2_cmd === M_XA_OR, init=Bool(false))
  val m3_isXor  = Reg(next=io.m2_cmd === M_XA_XOR, init=Bool(false))
  val m3_addr2  = Reg(next=io.m2_addr(2), init=Bool(false))

  val m4_adder_out  = Wire(UInt())
  if (operandBits == 32) {
    m4_adder_out := RegEnable(io.m3_lhs + m3_rhs, m3_isAdd)
  }
  else {
    val m3_mask   = RegEnable((~UInt(0,64) ^ (io.m2_addr(2) << 31)), m2_isAdd)
    val m3_op1    = (io.m3_lhs & m3_mask).asUInt
    val m3_op2    = (m3_rhs & m3_mask)
    val m4_addP1  = RegEnable(Cat(Fill(1, UInt(0)), m3_op1(31, 0)) + Cat(Fill(1, UInt(0)), m3_op2(31, 0)), m3_isAdd)
    val m4_addP2  = RegEnable(m3_op1(63, 32) + m3_op2(63, 32), m3_isAdd)
    m4_adder_out := Cat(m4_addP2 + m4_addP1(32), m4_addP1(31, 0))
  }
  val m5_adder_out  = RegEnable(m4_adder_out, m4_isAdd)

  val m4_msb_lhs  = Reg(next=Mux(m3_word && !m3_addr2, io.m3_lhs(31), io.m3_lhs(63)), init=Bool(false))
  val m4_msb_rhs  = Reg(next=Mux(m3_word && !m3_addr2, m3_rhs(31), m3_rhs(63)), init=Bool(false))
  val m4_lt_lo    = Reg(next=io.m3_lhs(31,0) < m3_rhs(31,0), init=Bool(false))
  val m4_lt_hi    = Reg(next=io.m3_lhs(63,32) < m3_rhs(63,32), init=Bool(false))
  val m4_eq_hi    = Reg(next=io.m3_lhs(63,32) === m3_rhs(63,32), init=Bool(false))
  val m4_word     = Reg(next=m3_word, init=Bool(false))
  val m4_addr2    = Reg(next=m3_word, init=Bool(false))

  val m3_out = Mux(m3_isAnd, io.m3_lhs & m3_rhs,
                Mux(m3_isOr,  io.m3_lhs | m3_rhs, io.m3_lhs ^ m3_rhs))

  val m4_isCmp      = Reg(next=(!m3_isAnd && !m3_isOr && !m3_isXor && !m3_isAdd), init=Bool(false))
  val m4_isBitOp    = Reg(next=m3_isAnd || m3_isOr || m3_isXor, init=Bool(false))
  val m4_min        = Reg(next=m3_min, init=Bool(false))
  val m4_max        = Reg(next=m3_max, init=Bool(false))
  val m4_sgned      = Reg(next=m3_sgned, init=Bool(false))
  val m4_out        = Reg(next=m3_out)
  val m4_lhs        = Reg(next=io.m3_lhs)
  val m4_rhs        = Reg(next=m3_rhs)

  val m4_lt         = Mux(m4_word, Mux(m4_addr2, m4_lt_hi, m4_lt_lo), m4_lt_hi || m4_eq_hi && m4_lt_lo)
  val m4_less       = Mux(m4_msb_lhs === m4_msb_rhs, m4_lt, Mux(m4_sgned, m4_msb_lhs, m4_msb_rhs))
  val m4_cmp        = Mux(Mux(m4_less, m4_min, m4_max), m4_lhs, m4_rhs)

  val m5_outSel     = (Fill(DATA_LEN,m5_isAdd) & m5_adder_out) | Reg(next=(Fill(DATA_LEN,m4_isBitOp) & m4_out) | (Fill(DATA_LEN,m4_isCmp) & m4_cmp))
  io.m5_out        := m5_outSel
}

class IOMSHR(id: Int)(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val req               = Decoupled(new HellaCacheReq(paddrBits)).flip
    val resp              = Decoupled(new HellaCacheResp(paddrBits))
    val mem_access        = Decoupled(new TLBundleA(edge.bundle))
    val mem_ack           = Valid(new TLBundleD(edge.bundle)).flip

    val duReadyCheck      = Vec(NUM_PUS, (new ReadyCheckBundle())).asInput
    val m1_unCacheOutReq  = Bool(OUTPUT)
    val m1_unCacheNeedAck = Bool(OUTPUT)
    val m1_unCacheCtxtId  = UInt(OUTPUT, CTXT_ID_LEN)
    val m1_unCacheDestReg = UInt(OUTPUT)
    val m1_unCachePipeId  = UInt(OUTPUT)
    val m1_unCacheIsFpu   = Bool(OUTPUT)
    val m2_unCacheAck     = Bool(INPUT)
    val ptwNotifUn        = Bool(OUTPUT)

    val pipeIdLateUn      = UInt(OUTPUT)
    val stSafeLateUn      = Bool(OUTPUT)
    val ctxtIdLateUn      = UInt(OUTPUT)
    val lateAck           = Bool(INPUT)
  }

  def beatOffset(addr: UInt) = addr.extract(beatOffBits - 1, wordOffBits)

  def wordFromBeat(addr: UInt, dat: UInt) = {
    val shift = Cat(beatOffset(addr), UInt(0, wordOffBits + log2Up(wordBytes)))
    (dat >> shift)(wordBits - 1, 0)
  }

  val req                  = Reg(new HellaCacheReq(paddrBits))
  val grant_word           = Reg(UInt(width = wordBits))

  val m1_unCacheOutReq_reg = Reg(init=Bool(false))
  val m1_rfReady_reg       = Reg(init=Bool(false))
  val ptwNotif_reg         = Reg(init=Bool(false))

  val pipeIdLate_reg       = Reg(UInt())
  val stSafeLate_reg       = Reg(init=Bool(false))
  val ctxtIdLate_reg       = Reg(UInt())

  val s_idle :: s_mem_access :: s_mem_ack :: s_resp :: s_notif_late :: Nil = Enum(Bits(), 5)
  val state     = Reg(init = s_idle)
  io.req.ready := (state === s_idle)

  val loadgen = new LoadGen(req.typ, mtSigned(req.typ), req.addr, grant_word, false.B, wordBytes)

  val a_source = UInt(id)
  val a_address = req.addr
  val a_size = mtSize(req.typ)
  val a_data = Fill(beatWords, req.data)

  val get     = edge.Get(a_source, a_address, a_size)._2
  val put     = edge.Put(a_source, a_address, a_size, a_data)._2
  val atomics = if (edge.manager.anySupportLogical) {
    MuxLookup(req.cmd, Wire(new TLBundleA(edge.bundle)), Array(
      M_XA_SWAP -> edge.Logical(a_source, a_address, a_size, a_data, TLAtomics.SWAP)._2,
      M_XA_XOR  -> edge.Logical(a_source, a_address, a_size, a_data, TLAtomics.XOR) ._2,
      M_XA_OR   -> edge.Logical(a_source, a_address, a_size, a_data, TLAtomics.OR)  ._2,
      M_XA_AND  -> edge.Logical(a_source, a_address, a_size, a_data, TLAtomics.AND) ._2,
      M_XA_ADD  -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.ADD)._2,
      M_XA_MIN  -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.MIN)._2,
      M_XA_MAX  -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.MAX)._2,
      M_XA_MINU -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.MINU)._2,
      M_XA_MAXU -> edge.Arithmetic(a_source, a_address, a_size, a_data, TLAtomics.MAXU)._2))
  }
  else {
    // If no managers support atomics, assert fail if processor asks for them
    assert(state === s_idle || !isAMO(req.cmd))
    Wire(new TLBundleA(edge.bundle))
  }
  assert(state === s_idle || req.cmd =/= M_XSC)

  io.mem_access.valid := (state === s_mem_access)
  io.mem_access.bits  := Mux(isAMO(req.cmd), atomics, Mux(isRead(req.cmd), get, put))
  io.mem_access.bits.user1 := Cat(req.pipeId,req.ctxtId)

  io.resp.valid           := io.m2_unCacheAck
  io.resp.bits            := req
  io.resp.bits.has_data   := isRead(req.cmd)
  io.resp.bits.data       := loadgen.data
  io.resp.bits.replay     := Bool(true)

  when (io.req.fire()) {
    req          := io.req.bits
    state        := s_mem_access
    ptwNotif_reg := isRead(io.req.bits.cmd) && !io.req.bits.needAck
  }

  when (io.mem_access.fire()) {
    state := s_mem_ack
  }

  when (state === s_mem_ack && io.mem_ack.valid) {
    when (isRead(req.cmd)) {
      grant_word           := wordFromBeat(req.addr, io.mem_ack.bits.data)
      state                := s_resp
      m1_unCacheOutReq_reg := Bool(true)
    }
    .otherwise {
      state           := s_idle
      when(isWrite(req.cmd) && !isRdWr(req.cmd)) {
        pipeIdLate_reg  := req.pipeId
        stSafeLate_reg  := Bool(true)
        ctxtIdLate_reg  := req.ctxtId
        state           := s_notif_late
      }
    }
  }
  when (state === s_notif_late && io.lateAck) {
    stSafeLate_reg  := Bool(false)
    state           := s_idle
  }

  when(m1_unCacheOutReq_reg && io.m2_unCacheAck) {
    m1_unCacheOutReq_reg := Bool(false)
  }

  when (io.m2_unCacheAck) {
    state        := s_idle
    ptwNotif_reg := Bool(false)
  }

  // Check if FPU is busy.
  val fpu3_reg = Reg(init=Bool(false))
  fpu3_reg := Bool(false)
  for(ips <- 0 until NUM_PUS) {
    when(((req.ctxtId === io.duReadyCheck(ips).ctxtId2) && io.duReadyCheck(ips).validFpu2 && (UInt(ips) === req.pipeId)) || ((req.ctxtId === io.duReadyCheck(ips).ctxtId3F) && io.duReadyCheck(ips).validFpu3F && (UInt(ips) === req.pipeId))) {
      fpu3_reg := Bool(true)
    }
  }
  val fpu4_reg = Reg(next=fpu3_reg)
  val fpu5_reg = Reg(next=fpu4_reg)

  // Check if the RF is ready to accept the future transaction.
  m1_rfReady_reg     := Bool(true)
  val m1_rfReadyLate  = Wire(Bool())
  m1_rfReadyLate     := Bool(true)
  for(ips <- 0 until NUM_PUS) {
    when(UInt(ips) === req.pipeId) {
      when(req.changeReg && !req.tag(log2Up(NumArbInp))) {
        when(((req.ctxtId === io.duReadyCheck(ips).ctxtId0) && io.duReadyCheck(ips).valid0) || ((req.ctxtId === io.duReadyCheck(ips).ctxtId1) && io.duReadyCheck(ips).valid1)) {
          m1_rfReady_reg := Bool(false)
        }
        when((req.ctxtId === io.duReadyCheck(ips).ctxtId0) && io.duReadyCheck(ips).valid0) {
          m1_rfReadyLate := Bool(false)
        }
      }
    }
  }
  when(req.changeReg && req.tag(log2Up(NumArbInp))) {
    when(fpu3_reg || fpu4_reg || fpu5_reg) {
      m1_rfReady_reg := Bool(false)
    }
  }
  val m1_rfReady = m1_rfReady_reg && m1_rfReadyLate

  io.pipeIdLateUn  := pipeIdLate_reg
  io.stSafeLateUn  := stSafeLate_reg
  io.ctxtIdLateUn  := ctxtIdLate_reg
  io.ptwNotifUn    := ptwNotif_reg

  // Request access.
  io.m1_unCacheOutReq  := m1_unCacheOutReq_reg && m1_rfReady
  io.m1_unCacheNeedAck := req.needAck
  io.m1_unCacheCtxtId  := req.ctxtId
  io.m1_unCacheDestReg := req.destReg
  io.m1_unCachePipeId  := req.pipeId
  io.m1_unCacheIsFpu   := req.tag(log2Up(NumArbInp))

  if (DEBUG == 2) {
    // Check for any halt.
    val dbgCntr_reg       = Reg(init=UInt(0, 30))
    val dbgStateD1_reg    = Reg(init=s_idle)
    dbgStateD1_reg       := state
    dbgCntr_reg          := dbgCntr_reg + UInt(1)
    when((dbgStateD1_reg =/= state) || (state === s_idle)) {
      dbgCntr_reg := UInt(0)
    }
    assert(dbgCntr_reg < UInt(4096*DBG_WAIT_SCALE), "No change in the state in IOMSHR.")
  }
  assert(!(io.mem_ack.valid &&  (state === s_idle)))
}

class NB_MSHR()(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val req_pri_maybe     = Bool(INPUT)
    val req_pri_valA      = Bool(INPUT)
    val req_pri_valB      = Bool(INPUT)
    val req_pri_rdy       = Bool(OUTPUT)
    val req_sec_val       = Bool(INPUT)
    val req_sec_rdyE      = Bool(OUTPUT)
    val req_bits          = new MSHRReqInternal(paddrBits).asInput
    val reqE_bits         = new MSHRReqInternal(paddrBits).asInput
    val idxScrambE        = UInt(INPUT, idxBits)
    val wbDone            = Bool(INPUT)

    val idx_matchE        = Bool(OUTPUT)
    val reservedWaysE     = UInt(OUTPUT, nWays)
    val tag               = Bits(OUTPUT, tagBits)

    val mem_acquire       = Decoupled(new TLBundleA(edge.bundle))
    val mem_grant         = Valid(new TLBundleD(edge.bundle)).flip
    val mem_finish        = Decoupled(new TLBundleE(edge.bundle))

    val refill            = new L1RefillReq().asOutput // Data is bypassed
    val replay            = Decoupled(new ReplayInternal(paddrBits))
    val wb_req            = Decoupled(new WritebackReq(edge.bundle))
    val probe_rdy         = Bool(OUTPUT)
    val s2_dataBankReady  = Bool(INPUT)
    val mshrWriteAckPre   = Bool(INPUT)
    val mshrWriteAck      = Bool(INPUT)
    val replayBusReady    = Bool(INPUT)

    val constants         = new Bundle {
      val id = UInt(INPUT, log2Up(nMSHRs/NUM_L2BANKS))
    }
  }

  val s_invalid :: s_wait_prev_finish :: s_wb_req :: s_wb_resp :: s_refill_req :: s_refill_resp :: s_wait_write_ack :: s_waitReset_rpq :: s_drainWait_rpq :: s_drain_rpq :: Nil = Enum(UInt(), 10)
  val state  = Reg(init=s_invalid)
  val stateStart_reg = Reg(init=s_invalid)

  val grantackq        = Module(new Queue(io.mem_finish.bits, 1))

  val resetCoh_reg            = Reg(init=Bool(false))
  val wbDoneOld_reg           = Reg(init=Bool(false))
  val memGrantOld_reg         = Reg(init=Bool(false))
  val blockProbe_reg          = Reg(init=Bool(false))
  val finishEna_reg           = Reg(init=Bool(false))
  val mshrWriteAckPreRec_reg  = Reg(init=Bool(false))

  val s0_repValid_reg  = Reg(next=io.replay.fire(), init=Bool(false))
  val repFire          = s0_repValid_reg && io.replayBusReady
  val s1_reqValid_reg  = Reg(next=repFire && !resetCoh_reg, init=Bool(false))
  val s2_reqValid_reg  = Reg(next=s1_reqValid_reg, init=Bool(false))
  val s3_reqValid_reg  = Reg(next=s2_reqValid_reg, init=Bool(false))

  val req            = Reg(new MSHRReqInternal(paddrBits))
  val req_idx        = req.addr(untagBits-1,blockOffBits)
  val req_idxScramb  = req_idx ^ tagScramble(req.addr)
  val idx_matchE     = req_idxScramb(idxBits-1, NumDCCmdsL2) === io.idxScrambE(idxBits-1, NumDCCmdsL2)
  val idx_match_reg  = Reg(next=idx_matchE)
  val addr_match_reg = Reg(next=(req.addr(paddrBits-1, blockOffBits) === io.reqE_bits.addr(paddrBits-1, blockOffBits)))

  val new_coh                            = Reg(init=ClientMetadata.onReset)
  val (_, shrink_param, coh_on_clear)    = req.old_meta.coh.onCacheControl(M_FLUSH)
  val grow_param                         = new_coh.onAccess(req.cmd)._2
  val coh_on_grant                       = new_coh.onGrant(req.cmd, io.mem_grant.bits.param)

  // We only accept secondary misses if we haven't yet sent an Acquire to outer memory
  // or if the Acquire that was sent will obtain a Grant with sufficient permissions
  // to let us replay this new request. I.e. we don't handle multiple outstanding
  // Acquires on the same block for now.
  val (cmd_requires_second_acquire, is_hit_again, _, dirtier_coh, dirtier_cmd) =
    new_coh.onSecondaryAccess(req.cmd, io.req_bits.cmd)

  val states_before_refill = Seq(s_wait_prev_finish, s_wb_req, s_wb_resp)
  val (_, _, refill_done, refill_address_inc) = edge.addr_inc(io.mem_grant)
  val sec_rdyE     = idx_matchE && (io.reqE_bits.cmd =/= M_XLR) && (io.reqE_bits.cmd =/= M_XSC) && (req.cmd =/= M_XLR) && (req.cmd =/= M_XSC) &&
                      (state =/= s_invalid) && (state =/= s_drain_rpq) && // Stop accepting more once we approach to update the tags. Note that, there is no problem if we accept one more last operation, as we will update the tags again.
                      (isWrite(req.cmd) || (io.reqE_bits.cmd === M_XRD))
  val sec_rdy_reg  = Reg(next=io.req_sec_rdyE, init=Bool(false))
                  //idx_match_reg && (io.req_bits.cmd =/= M_XLR) && (io.req_bits.cmd =/= M_XSC) && (req.cmd =/= M_XLR) && (req.cmd =/= M_XSC) &&
                  //(state =/= s_drain_rpq) && (state.isOneOf(states_before_refill) || isWrite(req.cmd) || (io.req_bits.cmd === M_XRD))
                  //(state.isOneOf(states_before_refill) ||
                  //  (state.isOneOf(s_refill_req, s_refill_resp) &&
                  //    !cmd_requires_second_acquire && !refill_done))

  val rpq = Module(new QueueReg(new ReplayInternal(blockOffBits), cfg.nRPQ))
  val rpqValidEarly = (io.req_pri_valA && io.req_pri_valB && io.req_pri_rdy) || (io.req_sec_val && sec_rdy_reg)
  rpq.io.enq.valid := Reg(next=rpqValidEarly, init=Bool(false)) // Note: Here we depend that there is no successive inputs can be received. Also, the logic of req_sec_rdyE is modified to consider this register.
  rpq.io.enq.bits  := RegEnable(io.req_bits, rpqValidEarly)
  assert(!rpq.io.enq.valid || rpq.io.enq.ready)

  when(state === s_invalid) {
    wbDoneOld_reg   := Bool(false)
    memGrantOld_reg := Bool(false)
  }

  when (state === s_drain_rpq && (!rpq.io.deq.valid || (rpq.io.deq.fire() && (rpq.io.count === UInt(1))))) {
    state           := s_invalid
    blockProbe_reg  := Bool(false)
    finishEna_reg   := Bool(false)
  }
  when (state === s_refill_resp && refill_done) {
    state                  := s_wait_write_ack
    new_coh                := coh_on_grant
    mshrWriteAckPreRec_reg := io.mshrWriteAckPre

    // Mark the acquisition of a caching line.
    printf("DC_AQ%x_%x: %x %x %x\n", req.pipeId, req.ctxtId, (req.addr >> blockOffBits) << blockOffBits, req_idxScramb, OHToUInt(req.way_en))

    // Skip waiting for ACK if it does not have data.
    when(edge.hasData(io.mem_grant.bits) === Bool(false)) {
      state  := Mux(resetCoh_reg, s_waitReset_rpq, s_drain_rpq)
    }
  }

  // Mark the time when the re-filler starts attempting to write the cache line to the memories.
  when(state === s_wait_write_ack && io.mshrWriteAckPre && !mshrWriteAckPreRec_reg) {
    mshrWriteAckPreRec_reg := Bool(true)
  }

  // Wait until the re-filler writes the cache line to the memory.
  when(state === s_wait_write_ack && io.mshrWriteAck && mshrWriteAckPreRec_reg) {
    state  := Mux(resetCoh_reg, s_waitReset_rpq, s_drain_rpq)
  }

  when(state === s_waitReset_rpq) {
    when(!resetCoh_reg) {
      state := s_drain_rpq
    }
  }
  when (io.mem_acquire.fire()) { // s_refill_req
    state := s_refill_resp
  }
  when (state === s_wb_resp) {
    when(io.wbDone) {
      wbDoneOld_reg := Bool(true)
    }
    when(io.mem_grant.valid) {
      memGrantOld_reg := Bool(true)
    }

    when((io.mem_grant.valid || memGrantOld_reg) && (wbDoneOld_reg || io.wbDone)) {
      state := s_refill_req
    }
  }
  when(state === s_wait_prev_finish) {
    when(!grantackq.io.deq.valid) {
      state := Mux(stateStart_reg === s_drainWait_rpq, s_drain_rpq, stateStart_reg)
    }
  }

  when (io.wb_req.fire()) { // s_wb_req
    state := s_wb_resp
  }
  when (io.req_sec_val && sec_rdy_reg) { // s_wb_req, s_wb_resp, s_refill_req
    //If we get a secondary miss that needs more permissions before we've sent
    //  out the primary miss's Acquire, we can upgrade the permissions we're
    //  going to ask for in s_refill_req
    // This permission update happens only before starting the refill.
    when(state.isOneOf(states_before_refill)) {
      req.cmd := dirtier_cmd
      when (is_hit_again) {
        new_coh := dirtier_coh
      }
    }
  }

  when(state === s_drainWait_rpq) {
    // We may introduce one cycle delay to make sure that the rpq has been updated.
    state := s_drain_rpq
  }

  // Just relax the gated clock logic.
  when (io.req_pri_maybe && io.req_pri_rdy) {
    req  := io.req_bits
  }

  when (io.req_pri_valA && io.req_pri_rdy) {
    val old_coh  = io.req_bits.old_meta.coh
    val needs_wb = old_coh.onCacheControl(M_FLUSH)._1
    val (is_hit, _, coh_on_hit) = old_coh.onAccess(io.req_bits.cmd)
    val stateX   = Wire(init=s_drainWait_rpq)
    when (io.req_bits.tag_match) {
      when (is_hit) { // set dirty bit
        new_coh  := coh_on_hit
        stateX   := s_drainWait_rpq

        // Block the probe until we update the tags.
        blockProbe_reg  := Bool(true)
      }
      .otherwise { // upgrade permissions
        new_coh  := old_coh
        stateX   := s_refill_req
      }
    }
    .otherwise { // writback if necessary and refill
      new_coh          := ClientMetadata.onReset
      stateX           := Mux(needs_wb, s_wb_req, s_refill_req)

      // The WB will reset the tags for us.
      when(!needs_wb) {
        resetCoh_reg := Bool(true)
      }

      // Block the probe until we update the tags.
      blockProbe_reg := Bool(true)
    }

    when(io.req_pri_valB) {
      // Make sure that we finish all previous transactions.
      state          := Mux(grantackq.io.deq.valid, s_wait_prev_finish, stateX)
      stateStart_reg := stateX
    }
    .otherwise {
      blockProbe_reg := Bool(false)
      resetCoh_reg   := Bool(false)
    }
  }

  grantackq.io.enq.valid := io.mem_grant.fire() && (state === s_refill_resp) && !finishEna_reg
  grantackq.io.enq.bits  := edge.GrantAck(io.mem_grant.bits)
  io.mem_finish.valid    := grantackq.io.deq.valid && finishEna_reg
  io.mem_finish.bits     := grantackq.io.deq.bits
  grantackq.io.deq.ready := io.mem_finish.ready && finishEna_reg
  assert(!grantackq.io.enq.valid || (grantackq.io.enq.ready && (state === s_refill_resp)))

  io.idx_matchE    := (state =/= s_invalid) && idx_matchE
  io.reservedWaysE := Mux(io.idx_matchE, req.way_en, UInt(0))
  io.refill.way_en := req.way_en
  io.refill.addr   := (((req.addr >> blockOffBits) << blockOffBits) | refill_address_inc) ^ Cat(tagScramble(req.addr), UInt(0, blockOffBits))
  io.refill.done   := refill_done
  io.tag           := req.addr >> untagBits
  io.req_pri_rdy   := (state === s_invalid)
  io.req_sec_rdyE  := sec_rdyE && rpq.io.enq.ready && !(rpq.io.enq.valid && (rpq.io.count === UInt(cfg.nRPQ-1)))

  // This counter to protect from any probe read that can happen before updating the meta data.
  val meta_hazard = Reg(init=UInt(0,2))
  when (meta_hazard =/= UInt(0)) {
    meta_hazard := meta_hazard - 1
  }

  when ((repFire && resetCoh_reg) || ((state === s_wb_resp) && io.wbDone)) {
    meta_hazard    := 3
    resetCoh_reg   := Bool(false)
    when(!finishEna_reg) {
      blockProbe_reg := Bool(false)
    }
  }

  when(io.mem_grant.fire() && (state === s_refill_resp)) {
    blockProbe_reg := Bool(true)
    finishEna_reg  := Bool(true)
  }

  // The probe request is blocked in the following cases:
  //   - the index part only match while we still need to update the tags for the victim cache line.
  //   - the full address while we need to update the tags.
  io.probe_rdy := !(addr_match_reg && blockProbe_reg) && !(idx_match_reg && (state.isOneOf(states_before_refill) || resetCoh_reg || (meta_hazard =/= 0)))

  io.wb_req.valid          := state === s_wb_req
  io.wb_req.bits.source    := io.constants.id
  io.wb_req.bits.tag       := req.old_meta.tag
  io.wb_req.bits.idx       := req_idxScramb ^ tagScramble(Cat(req.old_meta.tag, UInt(0, untagBits))) // Recover the index bits used for the cache line to be evicted.
  io.wb_req.bits.param     := shrink_param
  io.wb_req.bits.way_en    := req.way_en
  io.wb_req.bits.voluntary := Bool(true)
  io.wb_req.bits.coh_state := coh_on_clear

  io.mem_acquire.valid := state === s_refill_req && grantackq.io.enq.ready
  io.mem_acquire.bits  := edge.AcquireBlock(
                                fromSource = io.constants.id,
                                toAddress = Cat(io.tag, req_idx) << blockOffBits,
                                lgSize = lgCacheBlockBytes,
                                growPermissions = grow_param)._2
  io.mem_acquire.bits.user1 := Cat(req.pipeId,req.ctxtId)

  io.replay.valid           := (resetCoh_reg || ((state === s_drain_rpq) && rpq.io.deq.valid)) && !(s0_repValid_reg || s1_reqValid_reg || s2_reqValid_reg || s3_reqValid_reg)
  io.replay.bits            := rpq.io.deq.bits
  io.replay.bits.pipeId     := rpq.io.deq.bits.pipeId && !resetCoh_reg // Force the pipe to zero in the case of not accessing a certain memory.
  io.replay.bits.phys       := Bool(true)
  io.replay.bits.addr       := Cat(io.tag, req_idx, rpq.io.deq.bits.addr(blockOffBits-1,0))
  io.replay.bits.way_en     := req.way_en
  io.replay.bits.coh_state  := Mux(resetCoh_reg, coh_on_clear, new_coh)
  io.replay.bits.tagWrEna   := ((state === s_drain_rpq) && (rpq.io.count === UInt(1))) || resetCoh_reg
  io.replay.bits.dataAccEna := !resetCoh_reg
  rpq.io.deq.ready          := Reg(next=s2_reqValid_reg && io.s2_dataBankReady && (state === s_drain_rpq))

  if (DEBUG == 2) {
    // Check for any halt.
    val dbgCntr_reg       = Reg(init=UInt(0, 30))
    val dbgStateD1_reg    = Reg(init=s_invalid)
    dbgStateD1_reg       := state
    dbgCntr_reg          := dbgCntr_reg + UInt(1)
    when((dbgStateD1_reg =/= state) || (state === s_invalid)) {
      dbgCntr_reg := UInt(0)
    }
    assert(dbgCntr_reg < UInt(4096*DBG_WAIT_SCALE), "No change in NB_MSHR.")
  }
  assert(!(io.mem_grant.valid &&  (state === s_invalid)))
}

class MSHRFile(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val mshr_busy        = Vec(nMSHRs , Bool(OUTPUT))
    val req              = Vec(NumDCCmds, Decoupled(new MSHRReq(paddrBits)).flip)
    val reqE             = Vec(NumDCCmds, Decoupled(new MSHRReq(paddrBits)).flip)
    val replay           = Vec(NumDCCmds, Decoupled(new Replay(paddrBits)))
    val reservedWays     = Vec(NumDCCmds, UInt(OUTPUT, nWays))
    val halfSel          = Vec(NumDCCmds, Bool(INPUT))

    // It can be used to disable request for previously NACKed transactions.
    val mshrs_ready      = Vec(NumDCCmds, Bool(OUTPUT))
    val mshrs_ready_ch_un= Vec(NumDCCmds, Bool(OUTPUT))

    // Check that the banks are ready after applying a memory op.
    val s2_dataBankReady = Vec(NumDCCmds, Bool(INPUT))

    // Probe ready.
    val probe_rdy        = Vec(NUM_L2BANKS, Bool(OUTPUT))

    // Bus to control the cache line write to the banks.
    val refill           = Vec(NUM_L2BANKS, new L1RefillReq().asOutput)

    // Resp for un-cached transactions.
    val resp             = Decoupled(new HellaCacheResp(paddrBits))

    // Notify and request access for un-cached transactions.
    val m1_unCacheOutReq  = Bool(OUTPUT)
    val m1_unCacheNeedAck = Bool(OUTPUT)
    val m1_unCacheCtxtId  = UInt(OUTPUT)
    val m1_unCacheDestReg = UInt(OUTPUT)
    val m1_unCachePipeId  = UInt(OUTPUT)
    val m1_unCacheIsFpu   = Bool(OUTPUT)
    val m2_unCacheAck     = Bool(INPUT)
    val pipeIdLateUn      = UInt(OUTPUT)
    val stSafeLateUn      = Bool(OUTPUT)
    val ctxtIdLateUn      = UInt(OUTPUT)
    val ptwNotifUn        = Bool(OUTPUT)
    val unCacheFpuNotif   = Bool(OUTPUT)

    // Number of busy MSHR.
    val status           = UInt(OUTPUT, 8)

    // A flag to indicate that the new cache line has been successfully written to the banks.
    val mshrWriteAckPre  = Vec(NUM_L2BANKS, Bool(INPUT))
    val mshrWriteAck     = Vec(NUM_L2BANKS, Bool(INPUT))

    // Flags used to arrange when the MSHRs to write to the register file to avoid any collision.
    val duReadyCheck     = Vec(NUM_PUS, (new ReadyCheckBundle())).asInput

    // Write back interface.
    val wb_req           = Vec(NUM_L2BANKS, Decoupled(new WritebackReq(edge.bundle)))
    val wbDone           = Vec(NUM_L2BANKS, Bool(INPUT))

    // Number of requests.
    val numReqs          = Vec(NumDCCmds*NumDCCmds, UInt(OUTPUT, log2Up(nMSHRs/NumDCCmds+1)))

    // Memory interfaces.
    val mem_acquire      = Vec(NUM_L2BANKS, Decoupled(new TLBundleA(edge.bundle)))
    val mem_grant        = Vec(NUM_L2BANKS, Valid(new TLBundleD(edge.bundle)).flip)
    val mem_finish       = Vec(NUM_L2BANKS, Decoupled(new TLBundleE(edge.bundle)))
  }
  require((nIOMSHRs % NumDCCmds) == 0)
  require((nMSHRs % NumDCCmds) == 0)
  require((nMSHRs % NUM_L2BANKS) == 0)
  val blockOffBitsX = if(NumDCCmds == 1) blockOffBits else (blockOffBits + log2Up(NumDCCmds))

  val mshrs      = 0 to nMSHRs - 1 map { x => Module(new NB_MSHR()).io }
  val mmios      = 0 to nIOMSHRs - 1 map { x => Module(new IOMSHR((nMSHRs/NUM_L2BANKS) + (x/NUM_L2BANKS))).io }
  val wb_req_arb = 0 to NUM_L2BANKS - 1 map { x => Module(new ArbiterTree(new WritebackReq(edge.bundle), nMSHRs / NUM_L2BANKS)) }
  val resp_arb   = Module(new ArbiterTree(new HellaCacheResp(paddrBits), nIOMSHRs))

  val busyFlag            = Wire(Vec(NumDCCmds, UInt(0, 4)))
  val ptwNotifUn          = Wire(Bool())
  val unCacheFpuNotif_reg = Reg(init=Bool(false))
  val pri_rdy_reg         = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val mshrTwoFree_reg     = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val validD1_reg         = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val pri_rdy             = Wire(Vec(NumDCCmds, Bool()))
  val noSuccSameSet_reg   = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})

  for (x <- 0 until nMSHRs) {
    mshrs(x).constants.id := UInt((x/NumDCCmds)%(nMSHRs/NUM_L2BANKS))
    io.mshr_busy(x)          := !mshrs(x).req_pri_rdy
  }

  for (miniId <- 0 until NUM_L2BANKS) {
    io.probe_rdy(miniId)  := noSuccSameSet_reg(miniId%NumDCCmds)
    io.refill(miniId)     := mshrs(miniId * (nMSHRs / NUM_L2BANKS)).refill
  }

  val alloc_arb        = 0 to NUM_L2BANKS - 1 map { x => Module(new ArbiterTree(Bool(), nMSHRs / NUM_L2BANKS)) }
  ptwNotifUn          := Bool(false)
  unCacheFpuNotif_reg := Bool(false)
  for (DC_ID <- 0 until NumDCCmds) {
    // determine if the request is cacheable or not
    val cacheableE   = edge.manager.supportsAcquireBFast(io.reqE(DC_ID).bits.addr, lgCacheBlockBytes)
    val cacheable    = Reg(next=cacheableE)

    // Scramble the incoming address.
    val idxScrambE   = io.reqE(DC_ID).bits.addr(untagBits - 1, blockOffBits) ^ tagScramble(io.reqE(DC_ID).bits.addr)
    val idxScramb    = io.req(DC_ID).bits.addr(untagBits - 1, blockOffBits) ^ tagScramble(io.req(DC_ID).bits.addr)

    // Prevent having two successive misses for the same set.
    noSuccSameSet_reg(DC_ID) := !(io.req(DC_ID).fire() && (idxScrambE(idxBits-1, NumDCCmdsL2) === idxScramb(idxBits-1, NumDCCmdsL2)))

    val sdq_val      = Reg(init=Bits(0, nSDQ_DC))
    val sdq_alloc_id = EncTreeO(~sdq_val(nSDQ_DC-1,0))
    val sdq_rdy      = !sdq_val.andR && noSuccSameSet_reg(DC_ID)
    val sdq_enq      = io.req(DC_ID).valid && io.req(DC_ID).ready && cacheable && isWrite(io.req(DC_ID).bits.cmd)
    val sdq          = Mem(nSDQ_DC, io.req(DC_ID).bits.data)
    when (sdq_enq) {
      sdq(sdq_alloc_id) := io.req(DC_ID).bits.data
    }

    val idxMatchE     = Wire(Vec(nMSHRs / NumDCCmds, Bool()))
    val tagList       = Wire(Vec(nMSHRs / NumDCCmds, Bits(width = tagBits)))
    val tag_match_reg = Reg(next=Mux1H(idxMatchE, tagList) === io.reqE(DC_ID).bits.addr >> untagBits)
    val replay_arb    = Module(new ArbiterTree(new ReplayInternal(paddrBits), nMSHRs / NumDCCmds))

    var idx_matchE     = Bool(false)
    var reservedWaysE  = UInt(0, nWays)
    var sec_rdyE       = Bool(false)
    var pri_rdy_ch_un  = Wire(Bool())

    val mshrBusy      = Wire(Vec(nMSHRs / NumDCCmds, Bool()))

    pri_rdy_ch_un  := Bool(false)
    for (indx <- 0 until nMSHRs / NumDCCmds) {
      val i            = DC_ID + indx * NumDCCmds
      val miniId       = DC_ID + NumDCCmds * (indx / (nMSHRs / NUM_L2BANKS))
      val arbInpId     = indx%(nMSHRs / NUM_L2BANKS)
      idxMatchE(indx) := mshrs(i).idx_matchE
      tagList(indx)   := mshrs(i).tag

      alloc_arb(miniId).io.in(arbInpId).valid  := mshrs(i).req_pri_rdy
      mshrs(i).req_pri_maybe                   := alloc_arb(miniId).io.out.ready
      mshrs(i).req_pri_valA                    := alloc_arb(miniId).io.in(arbInpId).ready
      mshrs(i).req_pri_valB                    := sdq_rdy && pri_rdy(DC_ID)

      mshrs(i).req_sec_val     := io.req(DC_ID).valid && sdq_rdy && tag_match_reg
      mshrs(i).reqE_bits       := io.reqE(DC_ID).bits
      mshrs(i).idxScrambE      := idxScrambE
      mshrs(i).req_bits        := io.req(DC_ID).bits
      mshrs(i).req_bits.sdq_id := sdq_alloc_id
      mshrs(i).wbDone          := io.wbDone(miniId)

      mshrs(i).s2_dataBankReady:= io.s2_dataBankReady(DC_ID)
      mshrs(i).mshrWriteAckPre := io.mshrWriteAckPre(miniId)
      mshrs(i).mshrWriteAck    := io.mshrWriteAck(miniId)

      wb_req_arb(miniId).io.in(arbInpId) <> mshrs(i).wb_req
      replay_arb.io.in(indx)             <> mshrs(i).replay
      mshrs(i).replayBusReady            := io.replay(DC_ID).ready

      if(NumDCCmds == 2) {
        val selIp   = io.halfSel(1-DC_ID)
        val mshrEna = (mshrs(i).replay.bits.pipeId =/= selIp)
        replay_arb.io.in(indx).valid := mshrs(i).replay.valid && mshrEna
        mshrs(i).replay.ready        := replay_arb.io.in(indx).ready && mshrEna
      }

      mshrs(i).mem_grant.valid := io.mem_grant(miniId).valid && io.mem_grant(miniId).bits.source === UInt(indx%(nMSHRs/NUM_L2BANKS))
      mshrs(i).mem_grant.bits  := io.mem_grant(miniId).bits

      sec_rdyE        = sec_rdyE || mshrs(i).req_sec_rdyE
      idx_matchE      = idx_matchE || mshrs(i).idx_matchE
      reservedWaysE   = reservedWaysE | mshrs(i).reservedWaysE
      mshrBusy(indx) := !mshrs(i).req_pri_rdy

      when(io.mem_grant(miniId).bits.source === UInt(indx%(nMSHRs/NUM_L2BANKS))) {
        // Choose the refill bus depending on the selector.
        io.refill(miniId)   := mshrs(i).refill
      }
      when (!mshrs(i).probe_rdy) {
        io.probe_rdy(miniId) := false
      }
    }
    busyFlag(DC_ID)    := mshrBusy.asUInt()

    for(ips <- 0 until NUM_PUS) {
      io.numReqs(ips * NUM_PUS + DC_ID) := PopCount(0 to nMSHRs / NumDCCmds - 1 map { indx =>
          val i = DC_ID + indx * NumDCCmds
          (mshrs(i).replay.bits.pipeId === UInt(ips)) && mshrs(i).replay.valid
        })
    }

    // A flag to indicate that almost all MSHRs are occupied. This is needed as the MSHR ready is flag
    // so we have to prevent the issue of having two successive valid while only one MSHR is ready.
    val mshrReadyGrouped    = (DC_ID until NUM_L2BANKS).by(NumDCCmds).map((i: Int) => alloc_arb(i).io.in.map(_.valid).asUInt)
    val selectedGroupE      = if(NUM_L2BANKS/NumDCCmds == 1) mshrReadyGrouped(0) else MuxTree(io.reqE(DC_ID).bits.addr(blockOffBits+log2Up(NUM_L2BANKS)-1, blockOffBitsX), mshrReadyGrouped)
    val pri_rdyE            = selectedGroupE =/= UInt(0)
    mshrTwoFree_reg(DC_ID) := PopCountAtLeast(selectedGroupE, 2)
    validD1_reg(DC_ID)     := io.req(DC_ID).valid

    pri_rdy_reg(DC_ID) := pri_rdyE
    pri_rdy(DC_ID)     := pri_rdy_reg(DC_ID) && (mshrTwoFree_reg(DC_ID) || !validD1_reg(DC_ID))

    for (miniId <- DC_ID until NUM_L2BANKS by NumDCCmds) {
      val miniCacheSel = if(NUM_L2BANKS/NumDCCmds == 1) Bool(true) else (io.req(DC_ID).bits.addr(blockOffBits+log2Up(NUM_L2BANKS)-1, blockOffBitsX) === UInt(miniId/NumDCCmds))
      alloc_arb(miniId).io.out.ready := io.req(DC_ID).valid && Reg(next=cacheableE && !idx_matchE) && miniCacheSel // This is not a complete condition. "sdq_rdy && pri_rdy(DC_ID)" needs to be considered.
    }

    val replayValid_reg         = Reg(next=replay_arb.io.out.valid, init=Bool(false))
    val replay_reg              = RegEnable(replay_arb.io.out.bits, replay_arb.io.out.valid)
    replay_arb.io.out.ready    := Bool(true)
    io.replay(DC_ID).bits      := replay_reg
    io.replay(DC_ID).bits.data := RegEnable(sdq(replay_reg.sdq_id), replayValid_reg)
    io.replay(DC_ID).valid     := replayValid_reg

    val s1_free_sdq       = Reg(next=io.replay(DC_ID).fire() && isWrite(replay_reg.cmd) && replay_reg.dataAccEna, init=Bool(false))
    val s2_free_sdq       = Reg(next=s1_free_sdq, init=Bool(false))
    val free_sdq          = Reg(next=s2_free_sdq && io.s2_dataBankReady(DC_ID), init=Bool(false))
    val replaySdqD1_reg   = Reg(next=Reg(next=Reg(next=replay_reg.sdq_id)))
    when (free_sdq || sdq_enq) {
      sdq_val := sdq_val & ~(UIntToOH(replaySdqD1_reg) & Fill(nSDQ_DC, free_sdq)) |
                 (UInt(1) << sdq_alloc_id) & Fill(nSDQ_DC, sdq_enq)
    }

    val mmio_alloc_arb = Module(new ArbiterTree(Bool(), nIOMSHRs / NumDCCmds))
    var mmio_rdy       = Bool(false)

    for (indx <- 0 until nIOMSHRs / NumDCCmds) {
      val i      = DC_ID + indx * NumDCCmds

      mmio_alloc_arb.io.in(indx).valid := mmios(i).req.ready
      mmios(i).req.valid               := mmio_alloc_arb.io.in(indx).ready
      mmios(i).req.bits                := io.req(DC_ID).bits

      mmio_rdy = mmio_rdy || mmios(i).req.ready

      val req_rdyD1 = Reg(next=mmios(i).req.ready)
      when(!req_rdyD1 && mmios(i).req.ready) {
        pri_rdy_ch_un := Bool(true)
      }

      // The MMIO are connected to tile-link independent of the address.
      mmios(i).mem_ack.bits  := io.mem_grant(i%NUM_L2BANKS).bits
      mmios(i).mem_ack.valid := io.mem_grant(i%NUM_L2BANKS).valid && io.mem_grant(i%NUM_L2BANKS).bits.source === UInt((nMSHRs/NUM_L2BANKS) + (i/NUM_L2BANKS))

      resp_arb.io.in(i)   <> mmios(i).resp
    }

    // Mux for the signals to report safe store from the un-cache mis-handler.
    io.stSafeLateUn   := mmios(0).stSafeLateUn
    io.pipeIdLateUn   := mmios(0).pipeIdLateUn
    io.ctxtIdLateUn   := mmios(0).ctxtIdLateUn
    mmios(0).lateAck  := mmios(0).stSafeLateUn
    for (i <- 1 until nIOMSHRs) {
      val enaAck  = Wire(Bool())
      enaAck     := mmios(i).stSafeLateUn
      for (k <- 0 until i) {
        when(mmios(k).stSafeLateUn) {
          enaAck := Bool(false)
        }
      }

      mmios(i).lateAck  := enaAck
      when(enaAck) {
        io.stSafeLateUn   := mmios(i).stSafeLateUn
        io.pipeIdLateUn   := mmios(i).pipeIdLateUn
        io.ctxtIdLateUn   := mmios(i).ctxtIdLateUn
      }
    }
    mmio_alloc_arb.io.out.ready  := io.req(DC_ID).valid && !cacheable

    for (i <- 0 until nIOMSHRs) {
      when(mmios(i).m1_unCacheOutReq && mmios(i).m1_unCacheIsFpu) {
        unCacheFpuNotif_reg := Bool(true)
      }
      when(mmios(i).ptwNotifUn) {
        ptwNotifUn := Bool(true)
      }
    }

    io.req(DC_ID).ready          := Mux(!cacheable,
                                     mmio_rdy,
                                     sdq_rdy && Mux(Reg(next=idx_matchE), tag_match_reg && Reg(next=sec_rdyE), pri_rdy(DC_ID)))
    io.reservedWays(DC_ID)       := RegEnable(reservedWaysE, io.reqE(DC_ID).valid)
    io.mshrs_ready(DC_ID)        := pri_rdy(DC_ID)
    io.mshrs_ready_ch_un(DC_ID)  := pri_rdy_ch_un
  }
  io.ptwNotifUn      := ptwNotifUn
  io.unCacheFpuNotif := unCacheFpuNotif_reg

  io.m1_unCacheOutReq         := mmios(0).m1_unCacheOutReq
  io.m1_unCacheNeedAck        := mmios(0).m1_unCacheNeedAck
  io.m1_unCacheCtxtId         := mmios(0).m1_unCacheCtxtId
  io.m1_unCacheDestReg        := mmios(0).m1_unCacheDestReg
  io.m1_unCachePipeId         := mmios(0).m1_unCachePipeId
  io.m1_unCacheIsFpu          := mmios(0).m1_unCacheIsFpu
  mmios(0).m2_unCacheAck      := io.m2_unCacheAck && Reg(next=mmios(0).m1_unCacheOutReq)
  mmios(0).duReadyCheck       := io.duReadyCheck
  for (i <- 1 until nIOMSHRs) {
    val enaUnCacheOutReq  = Wire(Bool())
    enaUnCacheOutReq     := mmios(i).m1_unCacheOutReq
    for (k <- 0 until i) {
      when(mmios(k).m1_unCacheOutReq) {
        enaUnCacheOutReq := Bool(false)
      }
    }
    when(enaUnCacheOutReq) {
      io.m1_unCacheOutReq         := mmios(i).m1_unCacheOutReq
      io.m1_unCacheNeedAck        := mmios(i).m1_unCacheNeedAck
      io.m1_unCacheCtxtId         := mmios(i).m1_unCacheCtxtId
      io.m1_unCacheDestReg        := mmios(i).m1_unCacheDestReg
      io.m1_unCachePipeId         := mmios(i).m1_unCachePipeId
      io.m1_unCacheIsFpu          := mmios(i).m1_unCacheIsFpu
    }
    mmios(i).m2_unCacheAck   := io.m2_unCacheAck && Reg(next=enaUnCacheOutReq)

    // Connect the duReadyCheck flags so the mmios can select the right time to send the report the CTXT without any collision.
    mmios(i).duReadyCheck    := io.duReadyCheck
  }

  io.resp           <> resp_arb.io.out

  for (miniId <- 0 until NUM_L2BANKS) {
    val mshrMemAcq    = 0 to (nMSHRs/NUM_L2BANKS-1) map {x =>
      val mshrIdX = (x*NumDCCmds)+(miniId%NumDCCmds)+(miniId/NumDCCmds)*NumDCCmds*nMSHRs/NUM_L2BANKS
      mshrs(mshrIdX).mem_acquire
    }
    val mmioMemAcc    = miniId to (nIOMSHRs-1) by NUM_L2BANKS map {x => mmios(x).mem_access}
    TLArbiter.robinX(edge, io.mem_acquire(miniId), mshrMemAcq ++ mmioMemAcc)

    val mshrMemFinish = 0 to (nMSHRs/NUM_L2BANKS-1) map {x =>
      val mshrIdX = (x*NumDCCmds)+(miniId%NumDCCmds)+(miniId/NumDCCmds)*NumDCCmds*nMSHRs/NUM_L2BANKS
      mshrs(mshrIdX).mem_finish
    }
    TLArbiter.robinX(edge, io.mem_finish(miniId),  mshrMemFinish)
    io.wb_req(miniId) <> wb_req_arb(miniId).io.out
  }

  if(NumDCCmds == 1) {
    io.status        := busyFlag(0)
  }
  else {
    io.status        := Cat(busyFlag(1)(3,0), busyFlag(0)(3,0))
  }
}

class WritebackUnit(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val req           = Decoupled(new WritebackReq(edge.bundle)).flip
    val meta_read     = Decoupled(new L1MetaRdWrAddrReq)
    val data_req_rdy  = Bool(INPUT)
    val wbDone        = Bool(OUTPUT)
    val m2_wbValid    = Bool(INPUT)
    val m2_wbResp     = Bits(INPUT, CACHE_BLOCK_BITS)
    val release       = Decoupled(new TLBundleC(edge.bundle))
  }

  val req_reg           = Reg(new WritebackReq(edge.bundle))
  val active_reg        = Reg(init=Bool(false))
  val r1_data_req_fired = Reg(init=Bool(false))
  val r2_data_req_fired = Reg(init=Bool(false))
  val wbDone_reg        = Reg(init=Bool(false))
  val (_, last_beat, all_beats_done, beat_count) = edge.count(io.release)

  val wbShift_reg       = Reg(UInt(width=CACHE_BLOCK_BITS))
  val shiftEna_reg      = Reg(init=Bool(false))
  val shiftCntr_reg     = Reg(init=UInt(0, log2Up(CACHE_BLOCK_BITS/encRowBits)))
  val needLine_reg      = Reg(init=Bool(true))

  val s1_metaReq = Reg(next=io.meta_read.fire(), init=Bool(false))
  val s2_metaReq = Reg(next=s1_metaReq, init=Bool(false))

  wbDone_reg        := Bool(false)
  when (active_reg) {
    when (io.meta_read.fire()) {
      needLine_reg  := false
    }

    when(s2_metaReq) {
      // If DataBanks are not available, issue another request.
      needLine_reg  := !io.data_req_rdy
      wbDone_reg    := io.data_req_rdy
    }

    when (io.m2_wbValid) {
      wbShift_reg      := io.m2_wbResp
      shiftEna_reg     := true
    }

    when(io.release.fire()) {
      wbShift_reg     := wbShift_reg(CACHE_BLOCK_BITS - 1, encRowBits)
      shiftCntr_reg   := shiftCntr_reg + UInt(1)
      when(shiftCntr_reg === UInt(CACHE_BLOCK_BITS/encRowBits - 1)) {
        shiftCntr_reg  := UInt(0)
        shiftEna_reg   := false
        when((beat_count === UInt(0)) || (beat_count === UInt(refillCycles - 1))) {
          active_reg       := false
        }
      }
    }
  }

  val r_address = Cat(req_reg.tag, req_reg.idx) << blockOffBits
  when (io.req.fire()) {
    active_reg        := true
    req_reg           := io.req.bits
    needLine_reg      := true

    // Mark the eviction of a caching line.
    printf("DC_EV: %x\n", r_address)
  }
  io.wbDone      := wbDone_reg

  io.req.ready := !active_reg

  val fire      = active_reg && needLine_reg

  // We reissue the meta read as it sets up the mux ctrl for the data
  io.meta_read.valid          := fire
  io.meta_read.bits.idx       := req_reg.idx
  io.meta_read.bits.tag       := req_reg.tag
  io.meta_read.bits.coh_state := req_reg.coh_state
  io.meta_read.bits.way_en    := req_reg.way_en
  io.meta_read.bits.tagWrEna  := Bool(true)
  io.meta_read.bits.addr      := (if(refillCycles > 1)
                                   Cat(req_reg.idx, UInt(0, log2Up(refillCycles)))
                                 else req_reg.idx) << rowOffBits

  val probeResponse = edge.ProbeAck(
                          fromSource = req_reg.source,
                          toAddress = r_address,
                          lgSize = lgCacheBlockBytes,
                          reportPermissions = req_reg.param,
                          data = wbShift_reg(encRowBits-1, 0))

  val voluntaryRelease = edge.Release(
                          fromSource = req_reg.source,
                          toAddress = r_address,
                          lgSize = lgCacheBlockBytes,
                          shrinkPermissions = req_reg.param,
                          data = wbShift_reg(encRowBits-1, 0))._2

  io.release.bits    := Mux(req_reg.voluntary, voluntaryRelease, probeResponse)
  io.release.valid   := shiftEna_reg

  assert((beat_count === UInt(0)) || active_reg, "Something wrong with the beat counter for the release bus.")
}

class ProbeUnit(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val req         = Decoupled(new TLBundleB(edge.bundle)).flip
    val rep         = Decoupled(new TLBundleC(edge.bundle))
    val meta_read   = Decoupled(new L1MetaRdWrReq)
    val wb_req      = Decoupled(new WritebackReq(edge.bundle))
    val way_en      = Bits(INPUT, nWays)
    val mshr_rdy    = Bool(INPUT)
    val lrsc_valid  = Bool(INPUT)
    val wbDone      = Bool(INPUT)
    val block_state = new ClientMetadata().asInput
    val enaProbNack = Bool(OUTPUT)
  }

  val (s_invalid :: s_meta_delay :: s_meta_read :: s_meta_resp :: s_mshr_req ::
       s_mshr_resp :: s_release :: s_writeback_req :: s_writeback_resp ::
       s_meta_write :: s_wait_lrsc :: Nil) = Enum(UInt(), 11)
  val state         = Reg(init=s_invalid)
  val old_coh       = Reg(new ClientMetadata)
  val way_en        = Reg(Bits())
  val req           = Reg(new TLBundleB(edge.bundle))
  val delayCntr_reg = Reg(init=UInt(0, 3))

  val tag_matches = way_en.orR
  val miss_coh    = ClientMetadata.onReset
  val reply_coh   = Mux(tag_matches, old_coh, miss_coh)
  val (is_dirty, report_param, new_coh) = reply_coh.onProbe(req.param)
  io.req.ready    := state === s_invalid
  io.rep.valid    := state === s_release
  io.rep.bits     := edge.ProbeAck(req, report_param)
  io.enaProbNack  := (state =/= s_invalid) && (state =/= s_wait_lrsc)

  assert(!io.rep.valid || !edge.hasData(io.rep.bits),
    "ProbeUnit should not send ProbeAcks with data, WritebackUnit should handle it")

  io.meta_read.valid          := (state === s_meta_read) || (state === s_meta_write)
  io.meta_read.bits.idx       := req.address(untagBits-1, blockOffBits)
  io.meta_read.bits.tag       := req.address >> untagBits
  io.meta_read.bits.way_en    := way_en
  io.meta_read.bits.tagWrEna  := (state === s_meta_write)
  io.meta_read.bits.coh_state := new_coh

  io.wb_req.valid          := state === s_writeback_req
  io.wb_req.bits.source    := req.source
  io.wb_req.bits.idx       := req.address(untagBits-1, blockOffBits)
  io.wb_req.bits.tag       := req.address >> untagBits
  io.wb_req.bits.param     := report_param
  io.wb_req.bits.way_en    := way_en
  io.wb_req.bits.voluntary := Bool(false)
  io.wb_req.bits.coh_state := new_coh

  // state === s_invalid
  when (io.req.fire()) {
    state := s_meta_read
    req   := io.req.bits
  }

  // state === s_meta_read
  when (io.meta_read.fire() && (state === s_meta_read)) {
    state := s_meta_resp
  }

  // we need to wait one cycle for the metadata to be read from the array
  when (state === s_meta_resp) {
    state := s_mshr_req
  }

  when (state === s_mshr_req) {
    old_coh := io.block_state
    way_en  := io.way_en

    // if the read didn't go through, we need to retry
    state   := Mux(io.mshr_rdy, s_mshr_resp, s_meta_delay)
  }

  when (state === s_meta_delay) {
    delayCntr_reg := delayCntr_reg + UInt(1)
    when(delayCntr_reg === UInt(0)) {
      state := s_meta_read
    }
  }

  when (state === s_mshr_resp) {
    state := Mux(tag_matches && is_dirty, s_writeback_req, s_release)
  }

  when (state === s_release && io.rep.ready) {
    state := Mux(tag_matches && (old_coh =/= new_coh), s_meta_write, s_invalid)
  }

  // state === s_writeback_req
  when (io.wb_req.fire()) {
    state := s_writeback_resp
  }

  // wait for the writeback request to finish.
  when ((state === s_writeback_resp) && io.wbDone) {
    state := s_invalid
  }

  when (io.meta_read.fire() && (state === s_meta_write)) {
    state := s_invalid
  }
  when(state === s_wait_lrsc) {
    when(!io.lrsc_valid) {
      state := s_meta_read
    }
  }
  when(io.lrsc_valid && state.isOneOf(s_meta_delay, s_meta_read, s_meta_resp, s_mshr_req)) {
    state := s_wait_lrsc
  }

  if (DEBUG == 2) {
    // Check for any halt.
    val dbgCntr_reg       = Reg(init=UInt(0, 20))
    val dbgStateD1_reg    = Reg(init=s_invalid)
    dbgStateD1_reg       := state
    dbgCntr_reg          := dbgCntr_reg + UInt(1)
    when((dbgStateD1_reg =/= state) || (state === s_invalid)) {
      dbgCntr_reg := UInt(0)
    }
    assert(dbgCntr_reg < UInt(1024*DBG_WAIT_SCALE), "No change in the state in ProbeUnit.")
  }
}

class MetadataArrayMemory(TAG_W: Int)(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val n_wr     = Bool(INPUT) // Active low write enable.
    val n_ceb    = Bool(INPUT) // Active low clock enable.
    val addr     = UInt(INPUT, log2Up(nSets / NumDCCmds))
    val wrData   = UInt(INPUT, TAG_W)
    val rdData   = UInt(OUTPUT, TAG_W)
  }

  // This RAM model assumes possibility to receive any two successive R/W access operations
  // We found such TSMC RAM that can read or write data in 1 clock cycle
  // This model is replaced in the backend by verilog instantiation of the TSMC memory
  val rdData_reg = Reg(UInt(width=TAG_W))
  val BANK_BLOCK = Mem(nSets / NumDCCmds, Bits(width=TAG_W))

  val rdData     = BANK_BLOCK(io.addr)

  // Synchronous write
  when(!io.n_wr && !io.n_ceb) {
    BANK_BLOCK.write(io.addr, io.wrData)
  }

  // Synchronous read
  when(!io.n_ceb) {
    rdData_reg := rdData
  }

  io.rdData := rdData_reg
}

class MetadataArrayM[T <: L1Metadata](onReset: () => T)(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val rstVal = onReset()
  val io = new Bundle {
    val rdwr  = Decoupled(new L1MetaRdWrReq).flip
    val tag   = UInt(INPUT, paddrBits - untagBits)
    val resp  = Vec(nWays, rstVal.cloneType).asOutput
  }

  val cohVal = onReset()
  cohVal.coh   := io.rdwr.bits.coh_state
  cohVal.tag   := io.tag

  val idx     = Wire(UInt())
  if(NumDCCmds == 1) {
    idx := io.rdwr.bits.idx
  }
  else {
    idx := io.rdwr.bits.idx(idxBits-1, 1)
  }

  val rst_cnt = Reg(init=UInt(0, log2Up((nSets/NumDCCmds)+1)))
  val rst     = rst_cnt < UInt(nSets / NumDCCmds)
  val wdata   = Mux(rst, rstVal, cohVal).asUInt
  val wmask   = Mux(rst || Bool(nWays == 1), SInt(-1), io.rdwr.bits.way_en.asSInt).asBools
  when (rst) {
    rst_cnt := rst_cnt+UInt(1)
  }

  val metabits  = rstVal.getWidth
  val tag_rdata = Wire(Vec(nWays, UInt()))

  if (DCACHE_TAG_ARRAY_IN_RAM){
    // instantiating RAM instead of registers
    val tag_array = 0 to (nWays-1) map {x => Module(new MetadataArrayMemory(metabits)).io}
    for (i <- 0 until nWays) {
      tag_array(i).addr   := Mux(rst, rst_cnt, idx)
      tag_array(i).n_wr   := !(rst || io.rdwr.bits.tagWrEna)
      tag_array(i).n_ceb  := !(  (rst || (io.rdwr.fire() && io.rdwr.bits.tagWrEna && wmask(i))) || (io.rdwr.fire() && !io.rdwr.bits.tagWrEna)  )
      tag_array(i).wrData := wdata
      tag_rdata(i)        := tag_array(i).rdData
    }
  }
  else {
    val idxD1_reg        = Reg(UInt())
    when(rst) {
      idxD1_reg := rst_cnt
    }
    .elsewhen(io.rdwr.fire()) {
      idxD1_reg := idx
    }

    val writeDataD1_reg  = Reg(next=wdata, init=UInt(0))
    val writeValidD1_reg = Reg(init=Vec.fill(nWays) {Bool(false)})
    for (i <- 0 until nWays) {
      val tag_array = Mem(nSets / NumDCCmds, UInt(width=metabits))
      writeValidD1_reg(i) := ((rst || (io.rdwr.valid && io.rdwr.bits.tagWrEna)) && wmask(i))

      tag_rdata(i)     := tag_array(idxD1_reg)
      when(writeValidD1_reg(i)) {
        tag_array(idxD1_reg) := writeDataD1_reg
      }
    }
  }

  io.resp        := io.resp.fromBits(tag_rdata.asUInt)
  io.rdwr.ready  := !rst // so really this could be a 6T RAM
}


// NOTE: n_ceb is asserted for one clock cycle and then it will be de-asserted for another clock cycle.
// during the first cycle, all signals are validated. However, the memory needs to ignore all the signals except n_ceb in the second cycle.
class DBankMemory(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val n_wrMask = UInt(INPUT, DATA_LEN/8) // Active low write mask.
    val n_wr     = Bool(INPUT) // Active low write enable.
    val n_ceb    = Bool(INPUT) // Active low clock enable.
    val addr     = UInt(INPUT, log2Up(nSets*refillCycles/8))
    val wrData   = UInt(INPUT, DATA_LEN)
    val rdData   = UInt(OUTPUT, DATA_LEN)
  }
  val rdData_reg = Reg(UInt(width=DATA_LEN))
  val BANK_BLOCK = Mem(nSets*refillCycles/8, Bits(width=DATA_LEN))

  val rdData     = BANK_BLOCK(io.addr)

  // Mask write.
  val wrData     = ((0 until DATA_LEN/8).map((w: Int) => Mux(io.n_wrMask(w), rdData(8*(w+1)-1,8*w), io.wrData(8*(w+1)-1,8*w)))).asUInt

  when(!io.n_wr && !io.n_ceb) {
    BANK_BLOCK.write(io.addr, wrData)
  }

  when(!io.n_ceb) {
    rdData_reg := rdData
  }

  // The following code asserts that we do not receive any two successive RD/WR operations.
  val n_cebD1_reg   = Reg(next=io.n_ceb, init=Bool(true))

  assert(!(!n_cebD1_reg && !io.n_ceb), "Receive two successive access operations in the DCache.")

  // An extra delay is added to consider the timing of the internal memories.
  // IMPRTANT: We do not need it implement this MUX, this MUX is added for testing.
  io.rdData := Mux(!n_cebD1_reg, UInt(0), rdData_reg)
}


class DataArrayAccBundle(implicit p: Parameters) extends L1HellaCacheBundle()(p) {
  val waySel       = Bits(width = nWays)
  val req          = new HellaCacheReq(paddrBits)
  val wbValid      = Bool()
  val valid_full   = Bool()
  val sc           = Bool()
  val replay       = Bool()
  val isCpu        = Bool()
  val hit          = Bool()
  val sc_fail      = Bool()
}

class DataBank(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val s2_acc       = Decoupled((new DataArrayAccBundle)).flip
    val write        = Decoupled(new L1DataWriteReq).flip

    val m2_amolhs    = UInt(OUTPUT)

    val m5_amoalu    = UInt(INPUT, DATA_LEN)
    val dataMshr     = UInt(INPUT, CACHE_BLOCK_BITS)

    val m2_wbValid   = Bool(OUTPUT)
    val m2_wbResp    = UInt(OUTPUT, CACHE_BLOCK_BITS)
    val m2_dataResp  = UInt(OUTPUT, DATA_LEN)
  }

  // Instantiate the memory controllers.
  val bank      = 0 to NUM_MEMS_PER_BANK-1 map {x => Module(new DBankMemory).io}

  val stateCnt_reg     = Reg(init=UInt(0, 3))
  val m1_resp          = Wire(Vec(NUM_MEMS_PER_BANK, Bits(width=DATA_LEN)))
  val m2_wbResp_reg    = Reg(init=Vec.fill(CACHE_BLOCK_BITS / DATA_LEN) {UInt(0, DATA_LEN)})
  val m2_amolhs_reg    = Reg(init=UInt(0)) // Initialize to zero is needed due to the use of the oring for the mux.

  val validGenEna_reg       = Reg(init=Bool(false))
  val m1_amoReq_reg         = Reg(init=Bool(false))
  val m1_latchMemOut_reg    = Reg(init=Bool(false))
  val m1_latchWbResp_reg    = Reg(init=Bool(false))
  val m1_arraySel_reg       = Reg(init=Bool(false))
  val m2_latchWbResp_reg    = Reg(init=Bool(false))
  val trans_reg             = Reg(new DataArrayAccBundle)
  val m0_trans_reg          = Reg(new DataArrayAccBundle)
  val transInProgress_reg   = Reg(init=Bool(false))
  val needBreak_reg         = Reg(init=Bool(false))
  val earlyOutValid_reg     = Reg(init=Bool(false))
  val rdEna_reg             = Reg(init=Bool(false))
  val wrEna_reg             = Reg(init=Bool(false))
  val amoWrPhaseP_reg       = Reg(init=Bool(false))
  val bankSel_reg           = Reg(init=Vec.fill(NUM_MEMS_PER_BANK) {Bool(false)})
  val m2_dataResp_reg       = Reg(UInt(width=DATA_LEN))
  val m1_dataSel_reg        = Reg(init=UInt(0))
  val m1_arrayId_reg        = Reg(init=Bool(false))

  def DATA_LEN_LOG2         = log2Up(DATA_LEN/8)

  // Connect the readiness of the memory array.
  io.write.ready  := !needBreak_reg && !transInProgress_reg
  io.s2_acc.ready := !io.write.valid && !needBreak_reg && (!transInProgress_reg || (!io.s2_acc.bits.wbValid && (trans_reg.req.addr(DATA_LEN_LOG2+NUM_MEMS_PER_BANK_LOG2-1, DATA_LEN_LOG2) =/= io.s2_acc.bits.req.addr(DATA_LEN_LOG2+NUM_MEMS_PER_BANK_LOG2-1, DATA_LEN_LOG2))))

  // Increment the state.
  when(transInProgress_reg) {
    stateCnt_reg := stateCnt_reg + UInt(1)
  }

  // Reset the state.
  when((stateCnt_reg === UInt(7)) || (transInProgress_reg && !isRdWr(trans_reg.req.cmd))) {
    transInProgress_reg := Bool(false)
    needBreak_reg       := Bool(false)
    stateCnt_reg        := UInt(0)
  }

  rdEna_reg       := Bool(false)
  wrEna_reg       := Bool(false)
  when(!needBreak_reg && (io.s2_acc.valid || io.write.valid)) {
    transInProgress_reg := io.s2_acc.fire() || io.write.fire()

    when(io.write.valid) {
      // Translate the write bus to DataArrayAccBundle.
      trans_reg.waySel       := io.write.bits.way_en
      trans_reg.req.addr     := io.write.bits.addr
      trans_reg.req.cmd      := M_XWR
      trans_reg.req.typ      := MT_D
      trans_reg.wbValid      := Bool(true)
      trans_reg.valid_full   := Bool(false)
      trans_reg.replay       := Bool(false)
      trans_reg.isCpu        := Bool(false)
      when(io.write.ready) {
        rdEna_reg     := Bool(false)
        wrEna_reg     := Bool(true)
        needBreak_reg := Bool(true)
      }
    }
    .otherwise {
      trans_reg     := io.s2_acc.bits
      when(io.s2_acc.ready) {
        rdEna_reg     := isRead(io.s2_acc.bits.req.cmd)
        wrEna_reg     := isWrite(io.s2_acc.bits.req.cmd) && !isRdWr(io.s2_acc.bits.req.cmd) && io.s2_acc.bits.valid_full
        needBreak_reg := isRdWr(io.s2_acc.bits.req.cmd) || io.s2_acc.bits.wbValid
      }
    }

    // Mark the selected banks.
    for (i <- 0 until NUM_MEMS_PER_BANK) {
      bankSel_reg(i) := io.write.valid || io.s2_acc.bits.wbValid || (io.s2_acc.bits.req.addr(DATA_LEN_LOG2+NUM_MEMS_PER_BANK_LOG2-1, DATA_LEN_LOG2) === UInt(i))
    }
  }

  amoWrPhaseP_reg := Bool(false)
  when(((stateCnt_reg === UInt(5)) && isRdWr(trans_reg.req.cmd))) {
    wrEna_reg           := trans_reg.valid_full
    amoWrPhaseP_reg     := Bool(true)
  }

  // Check if an output valid is needed to be generated.
  when(transInProgress_reg) {
    validGenEna_reg := isRead(trans_reg.req.cmd) && !trans_reg.wbValid
  }

  // Latch the read data for AMO processing.
  when((stateCnt_reg === UInt(0)) && isRdWr(trans_reg.req.cmd) && transInProgress_reg) {
    m1_amoReq_reg  := Bool(true)
  }
  when(m1_amoReq_reg) {
    m1_amoReq_reg  := Bool(false)
  }

  val s2_storegen = new StoreGen(trans_reg.req.typ, trans_reg.req.addr, trans_reg.req.data, DATA_LEN/8)

  val arrayId   = blockOffBits - 1
  val lowerAddr = EncTreeO(trans_reg.waySel)
  for (i <- 0 until NUM_MEMS_PER_BANK) {
    bank(i).n_wrMask   := ~s2_storegen.mask
    bank(i).n_wr       := !(wrEna_reg && bankSel_reg(i))
    bank(i).n_ceb      := !((rdEna_reg || wrEna_reg) && bankSel_reg(i))
    bank(i).addr       := Cat(trans_reg.req.addr(trans_reg.req.addr.getWidth-1, arrayId + 1 + log2Up(NUM_MEMS_PER_BANK)), lowerAddr(log2Up(nWays) - 1, 0))
    bank(i).wrData     := Mux(trans_reg.wbValid, io.dataMshr((i+1)*DATA_LEN - 1, i*DATA_LEN), Mux(amoWrPhaseP_reg, Fill(rowWords, io.m5_amoalu), s2_storegen.data))
    m1_resp(i)         := bank(i).rdData
  }

  when(transInProgress_reg && (stateCnt_reg === UInt(0))) {
    m0_trans_reg := trans_reg
  }

  // Connect to the control of the AMO.
  val m2_amoReq_reg        = Reg(next=m1_amoReq_reg, init=Bool(false))
  val m3_amoReq_reg        = Reg(next=m2_amoReq_reg, init=Bool(false))

  earlyOutValid_reg     := transInProgress_reg && (stateCnt_reg === UInt(0))
  m1_latchWbResp_reg    := m0_trans_reg.wbValid && earlyOutValid_reg && isRead(m0_trans_reg.req.cmd)
  m1_arraySel_reg       := m0_trans_reg.req.addr(arrayId)
  m1_latchMemOut_reg    := Bool(false)
  when(earlyOutValid_reg && validGenEna_reg) {
    m1_latchMemOut_reg   := Bool(true)
    m1_dataSel_reg       := m0_trans_reg.req.addr(DATA_LEN_LOG2+NUM_MEMS_PER_BANK_LOG2-1, DATA_LEN_LOG2)
    m1_arrayId_reg       := m0_trans_reg.req.addr(arrayId)
  }

  // Select the data from the needed way.
  val selData = Wire(init=UInt(0, DATA_LEN))
  selData    := m2_dataResp_reg
  when(m1_latchMemOut_reg) {
    for (i <- 0 until NUM_MEMS_PER_BANK) {
      when(m1_dataSel_reg === UInt(i)) {
        selData        := m1_resp(i)
      }
    }
    m2_dataResp_reg  := selData
    if (CMP_CLK_GATING_ENABLED){
      // Implementing comparator-based clock gating
      CompClockGate(selData, m2_dataResp_reg)
    }
  }
  io.m2_dataResp := m2_dataResp_reg

  when(m2_amoReq_reg || m3_amoReq_reg){
    m2_amolhs_reg := Mux(m2_amoReq_reg, selData, UInt(0))
  }
  io.m2_amolhs := m2_amolhs_reg

  // Connect the bus to the write-back module.
  m2_latchWbResp_reg := m1_latchWbResp_reg
  when(m1_latchWbResp_reg || m2_latchWbResp_reg) {
    for (i <- 0 until NUM_MEMS_PER_BANK) {
      when(m1_latchWbResp_reg) {
        m2_wbResp_reg(i) := m1_resp(i)
      }
      .otherwise {
        m2_wbResp_reg(i) := UInt(0)
      }
    }
  }
  io.m2_wbResp       := m2_wbResp_reg.asUInt
  io.m2_wbValid      := m2_latchWbResp_reg
}

class DataBanks(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val s2_acc        = Decoupled(new DataArrayAccBundle).flip
    val write         = Vec(NUM_L2BANKS/NumDCCmds, Decoupled(new L1DataWriteReq).flip)
    val dataMshr      = Vec(NUM_L2BANKS/NumDCCmds, UInt(INPUT, CACHE_BLOCK_BITS))

    val m1_unCacheOutReq  = Bool(INPUT)
    val m1_unCacheCtxtId  = UInt(INPUT, CTXT_ID_LEN)
    val m1_unCachePipeId = UInt(INPUT, PIPE_ID_LEN)
    val m2_unCacheAck     = Bool(OUTPUT)
    val unCacheProt_out   = Bool(OUTPUT)
    val unCacheProt_in    = Bool(INPUT)

    val m2_wbValid           = Vec(NUM_L2BANKS/NumDCCmds, Bool(OUTPUT))
    val m2_wbResp            = Vec(NUM_L2BANKS/NumDCCmds, UInt(OUTPUT, CACHE_BLOCK_BITS))
    val m2_acc               = (new DataArrayAccBundle).asOutput
    val earlyDuWrFlag        = Vec(NUM_PUS, Bool(OUTPUT))
    val earlyFpuWrFlag       = Vec(NUM_PUS, Bool(OUTPUT))
    val earlyCtxtFpuWr       = Vec(NUM_PUS, UInt(OUTPUT, CTXT_ID_LEN))
  }

  // AMO ALU.
  val amoalu       = Module(new AMOALU_MOD(DATA_LEN, blockOffBits)).io

  // Instantiate the banks.
  val banks = 0 to NUM_BANKS_PER_DC - 1 map {x => Module(new DataBank).io}

  // Connect the ready signal.
  io.s2_acc.ready := OrTree((0 until NUM_BANKS_PER_DC).map((w: Int) => banks(w).s2_acc.ready && (io.s2_acc.bits.req.addr(EN_INDX, ST_INDX) === UInt(w))))

  for (miniId <- 0 until NUM_L2BANKS/NumDCCmds) {
    val m2_wbValidArr      = (miniId until NUM_BANKS_PER_DC).by(NUM_L2BANKS / NumDCCmds).map((w: Int) => banks(w).m2_wbValid).asUInt
    io.m2_wbValid(miniId) := m2_wbValidArr =/= UInt(0)

    val wrReadyArr          = (miniId until NUM_BANKS_PER_DC).by(NUM_L2BANKS / NumDCCmds).map((w: Int) => banks(w).write.ready).asUInt
    if(NUM_BANKS_PER_DC/(NUM_L2BANKS / NumDCCmds) == 1) {
      io.write(miniId).ready := wrReadyArr(0)
    }
    else {
      io.write(miniId).ready := wrReadyArr(io.write(miniId).bits.addr(EN_INDX, ST_INDX_INC))
    }

    // Only one wb access is available in the pipe.
    val m2_wbRespArr      = (miniId until NUM_BANKS_PER_DC).by(NUM_L2BANKS / NumDCCmds).map((w: Int) => banks(w).m2_wbResp)
    io.m2_wbResp(miniId) := OrTree(m2_wbRespArr)
  }

  val addrScrambled = io.s2_acc.bits.req.addr ^ Cat(tagScramble(io.s2_acc.bits.req.addr), UInt(0, blockOffBits))
  for (w <- 0 until NUM_BANKS_PER_DC) {
    val miniId = w % (NUM_L2BANKS / NumDCCmds)
    banks(w).s2_acc.bits          := io.s2_acc.bits
    banks(w).s2_acc.bits.req.addr := addrScrambled
    banks(w).s2_acc.valid         := io.s2_acc.valid && (io.s2_acc.bits.req.addr(EN_INDX, ST_INDX) === UInt(w))

    // The address has been already scrambled inside the MSHR.
    banks(w).write.bits       := io.write(miniId).bits
    banks(w).write.valid      := io.write(miniId).valid && (io.write(miniId).bits.addr(EN_INDX, ST_INDX) === UInt(w))

    banks(w).m5_amoalu    := amoalu.m5_out
    banks(w).dataMshr     := io.dataMshr(miniId)
  }

  val s3_acc_reg    = Reg(new DataArrayAccBundle)
  val m0_acc_reg    = Reg(new DataArrayAccBundle)
  val m1_acc_reg    = Reg(new DataArrayAccBundle)
  val m2_acc_reg    = Reg(new DataArrayAccBundle)

  s3_acc_reg.isCpu := Bool(false)
  val s3_ready_reg  = Reg(next=io.s2_acc.ready)
  when(io.s2_acc.valid && isRead(io.s2_acc.bits.req.cmd) && !io.s2_acc.bits.wbValid) {
    s3_acc_reg       := io.s2_acc.bits
    s3_acc_reg.isCpu := Bool(true)
  }
  m0_acc_reg.isCpu := Bool(false)
  when(s3_acc_reg.isCpu && s3_ready_reg) {
    m0_acc_reg := s3_acc_reg
  }
  m1_acc_reg.isCpu := Bool(false)
  when(m0_acc_reg.isCpu) {
    m1_acc_reg := m0_acc_reg
  }
  m2_acc_reg.isCpu  := Bool(false)
  when(m1_acc_reg.isCpu) {
    m2_acc_reg      := m1_acc_reg
  }

  // Connect the output.
  io.m2_acc          := m2_acc_reg
  io.m2_acc.req.data := OrTree((0 until NUM_BANKS_PER_DC).map((w: Int) => Mux((m2_acc_reg.req.addr(EN_INDX, ST_INDX) === UInt(w)), banks(w).m2_dataResp, UInt(0))))

  // Delay memop parameters.
  val outSel_reg            = Reg(init=UInt(0, 2))
  val earlyWriteValid_reg   = Reg(next=Reg(next=io.s2_acc.valid && io.s2_acc.bits.req.changeReg, init=Bool(false)), init=Bool(false))
  val earlyWriteCtxtId_reg  = Reg(next=Reg(next=io.s2_acc.bits.req.ctxtId, init=UInt(0)), init=UInt(0))
  val earlyWritePipeId_reg  = Reg(next=Reg(next=io.s2_acc.bits.req.pipeId, init=UInt(0)), init=UInt(0))

  // Add protection for the conflict between the cached and non-cached write access.
  // Check if the left or the right of the need cycle target the same ROW.
  // We make sure that no successive selection for the IO-MSHRs.
  val unCacheProt    = Wire(Bool())
  if(NumDCCmds == 1) {
    val unCacheProt1    = !outSel_reg(0) || ((io.m2_acc.req.ctxtId =/= io.m1_unCacheCtxtId) || (io.m2_acc.req.pipeId =/= io.m1_unCachePipeId) || !io.m2_acc.req.changeReg)
    val unCacheProt2    = !earlyWriteValid_reg || (earlyWriteCtxtId_reg =/= io.m1_unCacheCtxtId) || (earlyWritePipeId_reg =/= io.m1_unCachePipeId)
    unCacheProt        := unCacheProt1 && unCacheProt2 && !outSel_reg(1)
  }
  else {
    val unCacheProt1    = !outSel_reg(0) || ((io.m2_acc.req.ctxtId =/= io.m1_unCacheCtxtId) || !io.m2_acc.req.changeReg)
    val unCacheProt2    = !earlyWriteValid_reg || (earlyWriteCtxtId_reg =/= io.m1_unCacheCtxtId)
    io.unCacheProt_out := unCacheProt1 && unCacheProt2 && !outSel_reg(1)
    unCacheProt        := io.unCacheProt_in && unCacheProt1 && unCacheProt2 && !outSel_reg(1)
  }

  // Select either the cached or the un-cached to generate its output.
  val outReq0  = m1_acc_reg.isCpu
  val outReq1  = io.m1_unCacheOutReq && unCacheProt
  outSel_reg  := Cat(outReq1 && !outReq0, outReq0)

  for(ips <- 0 until NUM_PUS) {
    // Connect early the transaction details to the RF to disable only one memory instead of disabling all memories.
    io.earlyDuWrFlag(ips)  := (outSel_reg(0) && (io.m2_acc.req.pipeId === UInt(ips)) && !io.m2_acc.req.tag(log2Up(NumArbInp))) || (outSel_reg(1) && Reg(next=(io.m1_unCachePipeId === UInt(ips))))
    io.earlyFpuWrFlag(ips) := (outSel_reg(0) && (io.m2_acc.req.pipeId === UInt(ips)) && io.m2_acc.req.tag(log2Up(NumArbInp))) || (outSel_reg(1) && Reg(next=(io.m1_unCachePipeId === UInt(ips))))
    io.earlyCtxtFpuWr(ips) := Mux(outSel_reg(0) && (io.m2_acc.req.pipeId === UInt(ips)) && io.m2_acc.req.tag(log2Up(NumArbInp)), io.m2_acc.req.ctxtId, io.m1_unCacheCtxtId)
  }
  io.m2_unCacheAck := outSel_reg(1)

  // Connect to AMO based on the selection.
  val isAmo      = isRdWr(io.s2_acc.bits.req.cmd) && io.s2_acc.valid
  val isAmoD1_reg = Reg(next=isAmo, init=Bool(false))
  val isAmoD2_reg = Reg(next=isAmoD1_reg, init=Bool(false))
  val isAmoD3_reg = Reg(next=isAmoD2_reg, init=Bool(false))
  val isAmoD4_reg = Reg(next=isAmoD3_reg, init=Bool(false))
  val reqD1_reg   = RegEnable(io.s2_acc.bits.req, isAmo)
  val reqD2_reg   = RegEnable(reqD1_reg, isAmoD1_reg)
  val reqD3_reg   = RegEnable(reqD2_reg, isAmoD2_reg)
  val reqD4_reg   = RegEnable(reqD3_reg, isAmoD3_reg)

  // Connect to AMO ALU.
  amoalu.m2_addr      := reqD4_reg.addr
  amoalu.m2_cmd       := reqD4_reg.cmd
  amoalu.m2_typ       := reqD4_reg.typ
  amoalu.m2_rhs       := reqD4_reg.data
  amoalu.m3_lhs       := RegEnable(OrTree((0 until NUM_BANKS_PER_DC).map((w: Int) => banks(w).m2_amolhs)), isAmoD4_reg)
}

class NonBlockingDCache(hartid: Int)(implicit p: Parameters) extends HellaCache(hartid)(p) {
  override lazy val module = new NonBlockingDCacheModule(this)
}

class NonBlockingDCacheModule(outer: NonBlockingDCache) extends HellaCacheModule(outer) {
  require(isPow2(nWays))              // TODO: relax this
  require(nMSHRs % NUM_L2BANKS == 0)

  // ECC is only supported on the data array
  require(cacheParams.tagCode.isInstanceOf[IdentityCode])
  val dECC = cacheParams.dataCode

  def onReset                        = L1Metadata(UInt(0), ClientMetadata.onReset)
  def wayMap[T <: Data](f: Int => T) = Vec((0 until nWays).map(f))

  //**********************************
  // Instantiated modules.
  //**********************************
  val duc          = 0 to NUM_PUS - 1 map { x => Module(new DataUnitCntrl()(outer.p, edge)).io }
  val dcArb        = 0 to NumDCCmds - 1 map { x => Module(new HellaCacheArbiter(NumArbInp, paddrBits)).io }
  val medaArb      = 0 to NumDCCmds - 1 map { x => Module(new ArbiterTree(new HellaBusArb, 4)).io }
  val dataBanks    = 0 to NumDCCmds - 1 map { x => Module(new DataBanks).io }
  val metaArray    = 0 to NumDCCmds - 1 map { x => Module(new MetadataArrayM(onReset _)).io }
  val mshrs        = Module(new MSHRFile)
  val wb           = 0 to NUM_L2BANKS - 1 map { x => Module(new WritebackUnit) }
  val prober       = 0 to NUM_L2BANKS - 1 map { x => Module(new ProbeUnit) }
  val wbArb        = 0 to NUM_L2BANKS - 1 map { x => Module(new ArbiterTree(new WritebackReq(edge.bundle), 2)) }
  val wbOutArb     = 0 to NumDCCmds - 1 map { x => Module(new ArbiterReg(new L1MetaRdWrAddrReq, NUM_L2BANKS/NumDCCmds)) }
  val probeOutArb  = 0 to NumDCCmds - 1 map { x => Module(new ArbiterReg(new L1MetaRdWrReq, NUM_L2BANKS/NumDCCmds)) }

  //**********************************
  // Data unit controller.
  //**********************************
  // Define needed registers.
  val halfSel_reg        = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val halfSelD1_reg      = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val halfSelD2_reg      = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val halfSelD3_reg      = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val halfSelD4_reg      = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val halfSelD5_reg      = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val halfSelD6_reg      = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val halfSelD7_reg      = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val respValid_reg      = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val dataWordBypass_reg = Reg(Vec(NUM_PUS, UInt(width=coreDataBits)))
  val readData_reg       = Reg(Vec(NUM_PUS, UInt(width=DATA_LEN)))
  val readAddr_reg       = Reg(Vec(NUM_PUS, UInt(width=paddrBits)))
  val respTag_reg        = Reg(Vec(NUM_PUS, UInt(width=coreParams.dcacheReqTagBits)))
  val respCtxtId_reg     = Reg(Vec(NUM_PUS, UInt(width=CTXT_ID_LEN)))
  val respChangeReg_reg  = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val respDestReg_reg    = Reg(Vec(NUM_PUS, UInt(width=REG_ID_LEN)))
  val respFpuPending_reg = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val ldCntr_reg         = Reg(init=Vec.fill(NUM_T_CTXT){UInt(0, 32)})
  val s1_req_reg         = Wire(Vec(NumDCCmds, dcArb(0).mem.req.bits))
  val s1_tag_reg         = Wire(Vec(NumDCCmds, UInt(width=paddrBits-untagBits)))
  val s1_enaTrans_reg    = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s2_enaTrans_reg    = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s2_req_reg         = Reg(Vec(NumDCCmds, dcArb(0).mem.req.bits))
  val s1_clk_en          = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s2_clk_en          = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val lrsc_ready_reg     = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val ptwEarlyNotif_reg  = Reg(init=Bool(false))

  // Interface to the DUC.
  val duHitEarlyX        = Wire(Vec(NUM_PUS,   Wire(Bool())))
  val s0_enaTrans        = Wire(Vec(NumDCCmds, Wire(Bool())))
  val s0_notif           = Wire(Vec(NumDCCmds, Wire(Bool())))
  val s2_enaTrans        = Wire(Vec(NumDCCmds, Wire(Bool())))
  val cache_resp         = Wire(Vec(NumDCCmds, Valid(new HellaCacheResp(paddrBits))))
  val uncache_resp       = Wire(Vec(NumDCCmds, Valid(new HellaCacheResp(paddrBits))))
  val sel_resp           = Wire(Vec(NumDCCmds, Valid(new HellaCacheResp(paddrBits))))

  val s2_earlyNack_reg     = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s2_fpuPending_reg    = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val fpuPendingBusy_reg   = Reg(init=Vec.fill(NUM_PUS)   {Bool(false)})
  val s2_checkProc_reg     = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s2_lrsc_nack_reg     = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s2_valid_reg         = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s2_replay_reg        = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val fpuPendingCtxtId_reg = Reg(init=Vec.fill(NumDCCmds) {UInt(0, CTXT_ID_LEN)})
  val mshrs_ready_ch_reg   = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s2_hit               = Vec.fill(NumDCCmds) {Wire(Bool())}
  val s2_nack              = Vec.fill(NumDCCmds) {Wire(Bool())}
  val s2_nackP             = Vec.fill(NumDCCmds) {Wire(Bool())}

  val s3_nack_reg          = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s3_ctxtId_reg        = Reg(init=Vec.fill(NumDCCmds) {UInt(0, CTXT_ID_LEN)})
  val s3_pipeId_reg        = Reg(init=Vec.fill(NumDCCmds) {UInt(0, PIPE_ID_LEN)})
  val lrsc_valid           = Wire(Vec(NumDCCmds, Wire(Bool())))

  val s1_valid_reg         = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s1_cpu_valid_reg     = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val s1_cpu_disable       = Vec.fill(NumDCCmds) {Wire(Bool())}

  // Tag comparison results.
  val s2_tag_eq_way_reg = Reg(init=Vec.fill(NumDCCmds*nWays) {Bool(false)})
  val s2_coh_reg        = Reg(Vec(NumDCCmds*nWays, new ClientMetadata))

  // RF readiness check.
  mshrs.io.duReadyCheck  := io.duReadyCheck
  mshrs.io.resp.ready    := Bool(true)

  // Control the selection of the two halves.
  if(NumDCCmds == 2) {
    assert(NumDCCmds == NUM_PUS)

    // Total number of available requests.
    val numReqs   = Vec.fill(NumDCCmds*NumDCCmds) {Wire(UInt(OUTPUT, log2Up(nMSHRs/NumDCCmds+NUM_CTXT+1)))}
    val numReqsM  = Vec.fill(NumDCCmds*NumDCCmds) {Wire(UInt(OUTPUT, log2Up(nMSHRs/NumDCCmds+NUM_CTXT+1)))}
    val isNotZero = Vec.fill(NumDCCmds*NumDCCmds) {Wire(Bool())}

    // Combine the requests coming from MSHRs and DUC.
    numReqs(0) := Cat(UInt(0,log2Up(nMSHRs/NumDCCmds+NUM_CTXT+1)), duc(0).numReqs(0)) + mshrs.io.numReqs(0)
    numReqs(1) := Cat(UInt(0,log2Up(nMSHRs/NumDCCmds+NUM_CTXT+1)), duc(0).numReqs(1)) + mshrs.io.numReqs(1)
    numReqs(2) := Cat(UInt(0,log2Up(nMSHRs/NumDCCmds+NUM_CTXT+1)), duc(1).numReqs(0)) + mshrs.io.numReqs(2)
    numReqs(3) := Cat(UInt(0,log2Up(nMSHRs/NumDCCmds+NUM_CTXT+1)), duc(1).numReqs(1)) + mshrs.io.numReqs(3)

    // Consider the case of memon that is currently being selected.
    numReqsM(0) := Mux((numReqs(0) > UInt(1))&&halfSel_reg(1), numReqs(0)-UInt(1), numReqs(0))
    numReqsM(1) := Mux((numReqs(1) > UInt(1))&&halfSel_reg(0), numReqs(1)-UInt(1), numReqs(1))
    numReqsM(2) := Mux((numReqs(2) > UInt(1))&&halfSel_reg(0), numReqs(2)-UInt(1), numReqs(2))
    numReqsM(3) := Mux((numReqs(3) > UInt(1))&&halfSel_reg(1), numReqs(3)-UInt(1), numReqs(3))

    // Check that there is an available memop for each path.
    isNotZero(0) := numReqsM(0) =/= UInt(0)
    isNotZero(1) := numReqsM(1) =/= UInt(0)
    isNotZero(2) := numReqsM(2) =/= UInt(0)
    isNotZero(3) := numReqsM(3) =/= UInt(0)

    val halfSelS = Wire(init=Bool(false))
    halfSelS := halfSel_reg(0)
    when(((isNotZero(0)&&isNotZero(3)) && !(isNotZero(1)&&isNotZero(2))) || ((isNotZero(0)||isNotZero(3)) && !(isNotZero(1)||isNotZero(2)))) {
      halfSelS := Bool(false)
    }
    .otherwise {
      when((!(isNotZero(0)&&isNotZero(3)) && (isNotZero(1)&&isNotZero(2))) || (!(isNotZero(0)||isNotZero(3)) && (isNotZero(1)||isNotZero(2)))) {
        halfSelS := Bool(true)
      }
      .otherwise {
        halfSelS := !halfSel_reg(0)
      }
    }
    halfSel_reg(0) :=  halfSelS
    halfSel_reg(1) := !halfSelS
  }

  // Delayed versions of the selected half.
  halfSelD1_reg := halfSel_reg
  halfSelD2_reg := halfSelD1_reg
  halfSelD3_reg := halfSelD2_reg
  halfSelD4_reg := halfSelD3_reg
  halfSelD5_reg := halfSelD4_reg
  halfSelD6_reg := halfSelD5_reg
  halfSelD7_reg := halfSelD6_reg

  // Connect duc.
  for(ips <- 0 until NUM_PUS) {
    duc(ips).constants.hartid := (io.hartid*UInt(NUM_PUS)) + UInt(ips)

    for(mps <- 0 until NUM_MPS) {
      duc(ips).mpipeDataUnit(mps)  := io.mpipeDataUnit(ips*NUM_MPS + mps)
    }

    // Connect the selection control.
    if(ips == 0) {
      duc(ips).halfSel  := halfSel_reg
    }
    else {
      // Invert halfSel_reg for the other pipe, not to make a conflict with the other half.
      for(dc <- 0 until NumDCCmds) {
        duc(ips).halfSel(dc)  := !halfSel_reg(dc)
      }
    }

    for(mps <- 0 until NUM_MPS) {
      io.ptw(ips*NUM_MPS+mps)   <> duc(ips).ptw(mps)
    }
  }

  for(ips <- 0 until NUM_PUS) {
    for(mps <- 0 until NUM_MPS) {
      duc(ips).csrToDataUnit(mps)       := io.csrToDataUnit(NUM_MPS*ips+mps)
      io.dataUnitToCsr(NUM_MPS*ips+mps) := duc(ips).dataUnitToCsr(mps)
    }
    io.dataUnitToCtxt(ips)              <> duc(ips).dataUnitToCtxt
    io.dataUnitToCtxt(ips).duHitEarlyX  := duHitEarlyX(ips)

    io.dataUnitToCtxt(ips).duCtxtEarlyNotifier.valid       := mshrs.io.m2_unCacheAck && Reg(next=(mshrs.io.m1_unCacheNeedAck && (mshrs.io.m1_unCachePipeId === UInt(ips))))
    io.dataUnitToCtxt(ips).duCtxtEarlyNotifier.ctxtId      := Reg(next=mshrs.io.m1_unCacheCtxtId)
    io.dataUnitToCtxt(ips).duCtxtEarlyNotifier.destReg     := Reg(next=mshrs.io.m1_unCacheDestReg)
    io.dataUnitToCtxt(ips).duCtxtEarlyNotifier.isFpuAccess := Reg(next=mshrs.io.m1_unCacheIsFpu)
  }

  // Connect the trace bus.
  io.duTrace           := duc(0).duTrace
  io.mshr_busy         := mshrs.io.mshr_busy
  if(NUM_PUS == 2) {
    //io.duTrace.addrVDU1  := duc(1).duTrace.addrVDU0
    //io.duTrace.addrPDU1  := duc(1).duTrace.addrPDU0
  }

  // Arbiter for the PTW input requests.
  require(NumDCCmds == NUM_PUS)
  val reqValidD_reg  = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val reqReady_reg   = Reg(init=Vec.fill(NumDCCmds) {Bool(true)})
  val validInP_reg   = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val inpIdSel_reg   = Reg(init=Vec.fill(NumDCCmds) {Bool(false)})
  val dcSel_reg      = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val reqD_reg       = Reg(Vec(NumDCCmds, new HellaCacheReq))
  for(DC_ID <- 0 until NumDCCmds) {
    // Initialize the valid pulse.
    validInP_reg(DC_ID)  := Bool(false)

    if(NumDCCmds == 1) {
      when(io.dcPorts(0).req.valid && !reqValidD_reg(DC_ID)) {
        reqValidD_reg(DC_ID) := Bool(true)
        reqReady_reg(DC_ID)  := Bool(false)
        validInP_reg(DC_ID)  := Bool(true)
        inpIdSel_reg(DC_ID)  := Bool(false)
        reqD_reg(DC_ID)      := io.dcPorts(0).req.bits
      }

      when(dcArb(DC_ID).requestor(0).req.fire() || (validInP_reg(DC_ID) && io.dcPorts(0).s1_kill)) {
        reqValidD_reg(DC_ID) := Bool(false)
        reqReady_reg(DC_ID)  := Bool(true)
      }
    }
    else {
      require(NumDCCmds == 2)

      when(!reqValidD_reg(DC_ID) && ((io.dcPorts(0).req.valid && (io.dcPorts(0).req.bits.addr(blockOffBits) === UInt(DC_ID))) || (io.dcPorts(1).req.valid && (io.dcPorts(1).req.bits.addr(blockOffBits) === UInt(DC_ID))))) {
        reqValidD_reg(DC_ID) := Bool(true)
        reqReady_reg(DC_ID)  := Bool(false)
        validInP_reg(DC_ID)  := Bool(true)

        when(io.dcPorts(0).req.valid && (io.dcPorts(0).req.bits.addr(blockOffBits) === UInt(DC_ID))) {
          reqD_reg(DC_ID)      := io.dcPorts(0).req.bits
          inpIdSel_reg(DC_ID)  := Bool(false)
        }
        .otherwise {
          reqD_reg(DC_ID)      := io.dcPorts(1).req.bits
          inpIdSel_reg(DC_ID)  := Bool(true)
        }
      }

      when(dcArb(DC_ID).requestor(0).req.fire() || (validInP_reg(DC_ID) && Mux(inpIdSel_reg(DC_ID), io.dcPorts(1).s1_kill, io.dcPorts(0).s1_kill))) {
        reqValidD_reg(DC_ID) := Bool(false)
        reqReady_reg(DC_ID)  := Bool(true)
      }
    }

    // Connect dcArb.
    dcArb(DC_ID).requestor(0).req.bits  := reqD_reg(DC_ID)
    dcArb(DC_ID).requestor(0).req.valid := reqValidD_reg(DC_ID)
    dcArb(DC_ID).requestor(0).s1_kill   := Bool(false)
    dcArb(DC_ID).requestor(0).s2_kill   := Bool(false)

    dcArb(DC_ID).requestor(0).req.bits.isReal    := Bool(true)
    dcArb(DC_ID).requestor(0).req.bits.changeReg := Bool(false)
    dcArb(DC_ID).requestor(0).req.bits.pipeId    := inpIdSel_reg(DC_ID)
    dcArb(DC_ID).requestor(0).req.bits.needAck   := Bool(false)
  }

  for(por <- 0 until NUM_PUS) {
    assert(!io.dcPorts(por).s1_kill)
    assert(!io.dcPorts(por).s2_kill)

    if(NumDCCmds == 1) {
      io.dcPorts(por).req.ready      := dcArb(0).requestor(0).req.fire()
      io.dcPorts(por).resp           := dcArb(0).requestor(0).resp
      io.dcPorts(por).s2_nack        := dcArb(0).requestor(0).s2_nack
      io.dcPorts(por).s2_nack_cause1 := dcArb(0).requestor(0).s2_nack_cause1
      io.dcPorts(por).s2_nack_cause2 := dcArb(0).requestor(0).s2_nack_cause2
      io.dcPorts(por).s2_nack_cause3 := dcArb(0).requestor(0).s2_nack_cause3
    }
    else {
      // Find the half to be used.
      when(io.dcPorts(por).req.valid) {
        dcSel_reg(por) := io.dcPorts(por).req.bits.addr(blockOffBits)
      }

      // Forward the ready flag.
      io.dcPorts(por).req.ready      := Mux(dcSel_reg(por), dcArb(1).requestor(0).req.fire() && (inpIdSel_reg(1)===UInt(por)), dcArb(0).requestor(0).req.fire() && (inpIdSel_reg(0)===UInt(por)))

      // Connect the resp of the other requesters.
      io.dcPorts(por).resp.bits      := Mux(dcSel_reg(por), dcArb(1).requestor(0).resp.bits, dcArb(0).requestor(0).resp.bits)
      io.dcPorts(por).resp.valid     := Mux(dcSel_reg(por), dcArb(1).requestor(0).resp.valid && (dcArb(1).requestor(0).resp.bits.pipeId===UInt(por)), dcArb(0).requestor(0).resp.valid && (dcArb(0).requestor(0).resp.bits.pipeId===UInt(por)))
      io.dcPorts(por).s2_nack        := Mux(dcSel_reg(por), dcArb(1).requestor(0).s2_nack, dcArb(0).requestor(0).s2_nack)
      io.dcPorts(por).s2_nack_cause1 := Mux(dcSel_reg(por), dcArb(1).requestor(0).s2_nack_cause1, dcArb(0).requestor(0).s2_nack_cause1)
      io.dcPorts(por).s2_nack_cause2 := Mux(dcSel_reg(por), dcArb(1).requestor(0).s2_nack_cause2, dcArb(0).requestor(0).s2_nack_cause2)
      io.dcPorts(por).s2_nack_cause3 := Mux(dcSel_reg(por), dcArb(1).requestor(0).s2_nack_cause3, dcArb(0).requestor(0).s2_nack_cause3)
    }
  }

  for(ips <- 0 until NUM_PUS) {
    when(io.dataUnitToFpu(ips).ready) {
      fpuPendingBusy_reg(ips)  := Bool(false)
    }
  }

  val s2_tag_match_wayProb  = Wire(Vec(NumDCCmds, UInt()))
  val s2_hit_stateProb      = Wire(Vec(NumDCCmds, new ClientMetadata))
  ptwEarlyNotif_reg        := Bool(false)
  for(DC_ID <- 0 until NumDCCmds) {
    // Define the signals used to trakc the LR/SC.
    val lrsc_count_reg         = Reg(init=UInt(0, log2Up(lrscCycles)))
    val lrsc_addr_reg          = Reg(init=UInt(0))
    val lrsc_ctxtId_reg        = Reg(init=UInt(0))
    val lrsc_pipeId_reg        = Reg(init=UInt(0))
    val lrsc_validL_reg        = lrsc_count_reg.orR
    lrsc_ready_reg(DC_ID)     := !lrsc_validL_reg

    // Any further LR operation is NACK'd until the current LR/SC is served, that to increase the chance of processing LR/SC.
    s2_lrsc_nack_reg(DC_ID)   := (s1_req_reg(DC_ID).cmd === M_XLR) && (lrsc_validL_reg || ((s2_req_reg(DC_ID).cmd === M_XLR) && (s2_valid_reg(DC_ID) || s2_replay_reg(DC_ID)) && !s2_nack(DC_ID)))

    // The other half.
    val DC_IDX  = 1 - DC_ID

    // Aliasing for the arbiter output.
    val cpuInt             = dcArb(DC_ID).mem
    cpuInt.s2_xcpt.ma.ld  := Bool(false)
    cpuInt.s2_xcpt.ma.st  := Bool(false)
    cpuInt.s2_xcpt.pf.ld  := Bool(false)
    cpuInt.s2_xcpt.pf.st  := Bool(false)
    cpuInt.s2_xcpt.ae.ld  := Bool(false)
    cpuInt.s2_xcpt.ae.st  := Bool(false)
    cpuInt.ordered        := Bool(false)

    // Aliasing for the arbiter input port.
    val dmemPort           = dcArb(DC_ID).requestor(1)

    // Connect dmem port.
    if(NUM_PUS == 1) {
      dmemPort.s1_data.data        := duc(0).dataToStoreD1(DC_ID)
      dmemPort.req.valid           := duc(0).mpipeDataUnitD0(DC_ID).valid
      dmemPort.req.bits.tag        := Cat(duc(0).mpipeDataUnitD0(DC_ID).size, duc(0).mpipeDataUnitD0(DC_ID).isFpuAccess)
      dmemPort.req.bits.destReg    := duc(0).mpipeDataUnitD0(DC_ID).destReg
      dmemPort.req.bits.cmd        := duc(0).mpipeDataUnitD0(DC_ID).memOp
      dmemPort.req.bits.isReal     := duc(0).mpipeDataUnitD0(DC_ID).isReal
      dmemPort.req.bits.typ        := duc(0).mpipeDataUnitD0(DC_ID).size
      dmemPort.req.bits.addr       := duc(0).mpipeDataUnitD0(DC_ID).addr
      dmemPort.req.bits.ctxtId     := duc(0).mpipeDataUnitD0(DC_ID).ctxtId
      dmemPort.req.bits.changeReg  := duc(0).mpipeDataUnitD0(DC_ID).changeReg
      dmemPort.req.bits.pipeId     := Bool(false)

      duc(0).mpipeDataUnitReadyD0(DC_ID) := dmemPort.req.ready
    }
    else {
      val ducSel                    = Wire(Bool())
      if(NumDCCmds == 1) {
        ducSel := !duc(0).mpipeDataUnitD0(DC_ID).valid
      }
      else {
        ducSel := halfSelD1_reg(DC_ID)
      }
      dmemPort.req.valid           := Mux(ducSel, duc(1).mpipeDataUnitD0(DC_ID).valid, duc(0).mpipeDataUnitD0(DC_ID).valid)
      dmemPort.req.bits.tag        := Mux(ducSel, Cat(duc(1).mpipeDataUnitD0(DC_ID).size, duc(1).mpipeDataUnitD0(DC_ID).isFpuAccess), Cat(duc(0).mpipeDataUnitD0(DC_ID).size, duc(0).mpipeDataUnitD0(DC_ID).isFpuAccess))
      dmemPort.req.bits.destReg    := Mux(ducSel, duc(1).mpipeDataUnitD0(DC_ID).destReg, duc(0).mpipeDataUnitD0(DC_ID).destReg)
      dmemPort.req.bits.cmd        := Mux(ducSel, duc(1).mpipeDataUnitD0(DC_ID).memOp, duc(0).mpipeDataUnitD0(DC_ID).memOp)
      dmemPort.req.bits.typ        := Mux(ducSel, duc(1).mpipeDataUnitD0(DC_ID).size, duc(0).mpipeDataUnitD0(DC_ID).size)
      dmemPort.req.bits.addr       := Mux(ducSel, duc(1).mpipeDataUnitD0(DC_ID).addr, duc(0).mpipeDataUnitD0(DC_ID).addr)
      dmemPort.req.bits.ctxtId     := Mux(ducSel, duc(1).mpipeDataUnitD0(DC_ID).ctxtId, duc(0).mpipeDataUnitD0(DC_ID).ctxtId)
      dmemPort.req.bits.changeReg  := Mux(ducSel, duc(1).mpipeDataUnitD0(DC_ID).changeReg, duc(0).mpipeDataUnitD0(DC_ID).changeReg)
      dmemPort.req.bits.pipeId     := ducSel

      // One cycle delayed signals.
      val ducSelD1_reg              = Reg(next=ducSel)
      dmemPort.s1_data.data        := Mux(ducSelD1_reg, duc(1).dataToStoreD1(DC_ID), duc(0).dataToStoreD1(DC_ID))
      dmemPort.req.bits.isReal     := Mux(ducSelD1_reg, duc(1).mpipeDataUnitD0(DC_ID).isReal, duc(0).mpipeDataUnitD0(DC_ID).isReal)

      // Connect the ready to each of the DUC.
      duc(0).mpipeDataUnitReadyD0(DC_ID) := dmemPort.req.ready && !ducSel
      duc(1).mpipeDataUnitReadyD0(DC_ID) := dmemPort.req.ready &&  ducSel
    }
    dmemPort.req.bits.needAck    := Bool(true)
    dmemPort.req.bits.phys       := Bool(false)
    dmemPort.s1_kill             := Bool(false)

    // Track previous validated memop.
    for(ips <- 0 until NUM_PUS) {
      val mshrNumReqs = mshrs.io.numReqs(ips * NUM_PUS + DC_ID)

      // Consider the case of memon that is currently being selected.
      val useHalfSel   = if(NUM_PUS==1) {Bool(false)} else {if(ips==DC_ID) {halfSel_reg(1)} else {halfSel_reg(0)}}
      val mshrNumReqsM = Mux((mshrNumReqs > UInt(1))&useHalfSel, mshrNumReqs-UInt(1), mshrNumReqs)

      // If a memop coming from a MSHR, de-activate valid_prev.
      duc(ips).valid_prev(DC_ID) := dmemPort.req.valid && Reg(next=mshrNumReqsM == UInt(0))
      duc(ips).addr_prev(DC_ID)  := dmemPort.req.bits.addr(EN_INDX, ST_INDX)
    }

    //**********************************
    // Signals needed for DCache.
    //**********************************
    val s1_way_en_reg        = Wire(UInt())
    val s1_forceHit_reg      = Wire(Bool())
    val s1_replay_reg        = Reg(init=Bool(false))
    s1_valid_reg(DC_ID)     := cpuInt.req.fire() && (isRead(cpuInt.req.bits.cmd) || isWrite(cpuInt.req.bits.cmd) || isPrefetch(cpuInt.req.bits.cmd) || (cpuInt.req.bits.cmd === M_SFENCE))

    // Disable any non-real/killed instructions, we also consider if the ROW has been just NACK'd.
    // Note: s3_cpuCore_reg includes s3_req_reg(DC_ID).needAck
    val s3_cpuCore_reg      = Reg(next=Reg(next=s1_cpu_valid_reg(DC_ID) && s1_req_reg(DC_ID).needAck))
    s1_cpu_disable(DC_ID)  := s1_cpu_valid_reg(DC_ID) && (cpuInt.s1_kill || !cpuInt.req.bits.isReal || (s1_req_reg(DC_ID).needAck && s3_cpuCore_reg && s3_nack_reg(DC_ID) && (s1_req_reg(DC_ID).ctxtId === s3_ctxtId_reg(DC_ID)) && (s1_req_reg(DC_ID).pipeId === s3_pipeId_reg(DC_ID))))

    val s2_cpu_valid_reg     = Reg(init=Bool(false))
    val s2_readWrite_reg     = Reg(init=Bool(false))
    val s2_forceHit_reg      = Reg(init=Bool(false))
    val s2_way_en_reg        = Reg(init=UInt(0))
    s2_replay_reg(DC_ID)    := s1_replay_reg && (s1_req_reg(DC_ID).cmd =/= M_FAKE)
    s2_valid_reg(DC_ID)     := s1_valid_reg(DC_ID) && !s1_cpu_disable(DC_ID) && !(s1_req_reg(DC_ID).cmd === M_SFENCE)

    //**********************************
    // Entry arbiter.
    //**********************************
    for(miniId <- 0 until NUM_L2BANKS/NumDCCmds) {
      wbOutArb(DC_ID).io.in(miniId)    <> wb(miniId*NumDCCmds+DC_ID).io.meta_read
      probeOutArb(DC_ID).io.in(miniId) <> prober(miniId*NumDCCmds+DC_ID).io.meta_read
    }

    medaArb(DC_ID).in(0).bits.req.addr       := Cat(wbOutArb(DC_ID).io.out.bits.tag, wbOutArb(DC_ID).io.out.bits.idx, wbOutArb(DC_ID).io.out.bits.addr(blockOffBits-1, 0))
    medaArb(DC_ID).in(0).bits.req.phys       := Bool(true)
    medaArb(DC_ID).in(0).bits.req.cmd        := M_XRD
    medaArb(DC_ID).in(0).bits.req.changeReg  := Bool(false)
    medaArb(DC_ID).in(0).bits.req.needAck    := Bool(false)
    medaArb(DC_ID).in(0).bits.dataAccEna     := Bool(true)
    medaArb(DC_ID).in(0).bits.wbValid        := Bool(true)
    medaArb(DC_ID).in(0).bits.way_en         := wbOutArb(DC_ID).io.out.bits.way_en
    medaArb(DC_ID).in(0).bits.coh_state      := wbOutArb(DC_ID).io.out.bits.coh_state
    medaArb(DC_ID).in(0).bits.tagWrEna       := wbOutArb(DC_ID).io.out.bits.tagWrEna
    medaArb(DC_ID).in(0).valid               := wbOutArb(DC_ID).io.out.valid
    wbOutArb(DC_ID).io.out.ready             := medaArb(DC_ID).in(0).ready

    // Note: We need the priority of both prober and mshrs reflects the priority in wbArb connections.
    medaArb(DC_ID).in(1).bits.req.addr       := Cat(probeOutArb(DC_ID).io.out.bits.tag, probeOutArb(DC_ID).io.out.bits.idx) << blockOffBits
    medaArb(DC_ID).in(1).bits.req.phys       := Bool(true)
    medaArb(DC_ID).in(1).bits.req.cmd        := M_FAKE
    medaArb(DC_ID).in(1).bits.req.changeReg  := Bool(false)
    medaArb(DC_ID).in(1).bits.req.needAck    := Bool(false)
    medaArb(DC_ID).in(1).bits.dataAccEna     := Bool(false)
    medaArb(DC_ID).in(1).bits.wbValid        := Bool(false)
    medaArb(DC_ID).in(1).bits.way_en         := probeOutArb(DC_ID).io.out.bits.way_en
    medaArb(DC_ID).in(1).bits.coh_state      := probeOutArb(DC_ID).io.out.bits.coh_state
    medaArb(DC_ID).in(1).bits.tagWrEna       := probeOutArb(DC_ID).io.out.bits.tagWrEna
    medaArb(DC_ID).in(1).valid               := probeOutArb(DC_ID).io.out.valid
    probeOutArb(DC_ID).io.out.ready          := medaArb(DC_ID).in(1).ready

    medaArb(DC_ID).in(2).bits.req        := mshrs.io.replay(DC_ID).bits
    medaArb(DC_ID).in(2).bits.dataAccEna := mshrs.io.replay(DC_ID).bits.dataAccEna
    medaArb(DC_ID).in(2).bits.way_en     := mshrs.io.replay(DC_ID).bits.way_en
    medaArb(DC_ID).in(2).bits.coh_state  := mshrs.io.replay(DC_ID).bits.coh_state
    medaArb(DC_ID).in(2).bits.tagWrEna   := mshrs.io.replay(DC_ID).bits.tagWrEna
    medaArb(DC_ID).in(2).bits.wbValid    := Bool(false)
    medaArb(DC_ID).in(2).valid           := mshrs.io.replay(DC_ID).valid
    mshrs.io.replay(DC_ID).ready         := medaArb(DC_ID).in(2).ready
    mshrs.io.s2_dataBankReady(DC_ID)     := dataBanks(DC_ID).s2_acc.ready && !s2_earlyNack_reg(DC_ID) && !s2_lrsc_nack_reg(DC_ID)

    mshrs_ready_ch_reg(DC_ID) := Bool(false)
    when(medaArb(DC_ID).in(2).fire() && mshrs.io.replay(DC_ID).bits.tagWrEna && mshrs.io.replay(DC_ID).bits.dataAccEna) {
      mshrs_ready_ch_reg(DC_ID) := Bool(true)
    }

    medaArb(DC_ID).in(3).bits.req        := cpuInt.req.bits
    medaArb(DC_ID).in(3).bits.dataAccEna := Bool(true)
    medaArb(DC_ID).in(3).bits.wbValid    := Bool(false)
    medaArb(DC_ID).in(3).bits.tagWrEna   := Bool(false)
    medaArb(DC_ID).in(3).valid           := cpuInt.req.valid
    cpuInt.req.ready                     := medaArb(DC_ID).in(3).ready

    // Connect to the meta memory.
    metaArray(DC_ID).rdwr.bits.idx       := (medaArb(DC_ID).out.bits.req.addr >> blockOffBits) ^ tagScramble(medaArb(DC_ID).out.bits.req.addr)
    metaArray(DC_ID).rdwr.bits.coh_state := medaArb(DC_ID).out.bits.coh_state
    metaArray(DC_ID).rdwr.bits.tagWrEna  := medaArb(DC_ID).out.bits.tagWrEna
    metaArray(DC_ID).rdwr.bits.way_en    := medaArb(DC_ID).out.bits.way_en
    metaArray(DC_ID).rdwr.valid          := medaArb(DC_ID).out.valid
    metaArray(DC_ID).tag                 := medaArb(DC_ID).out.bits.req.addr >> untagBits
    medaArb(DC_ID).out.ready             := metaArray(DC_ID).rdwr.ready

    // Checks....
    assert(!medaArb(DC_ID).out.fire() || (medaArb(DC_ID).out.bits.req.addr(blockOffBits) === UInt(DC_ID)) || Bool(NumDCCmds == 1))
    val s2_isWrBack_reg = Reg(next=Reg(next=medaArb(DC_ID).in(0).fire()))
    val s2_isProber_reg = Reg(next=Reg(next=medaArb(DC_ID).in(1).fire()))

    //**********************************
    // One cycle delay.
    //**********************************
    s1_clk_en(DC_ID)        := medaArb(DC_ID).out.fire()
    s1_cpu_valid_reg(DC_ID) := medaArb(DC_ID).in(3).fire()
    s1_req_reg(DC_ID)       := RegEnable(medaArb(DC_ID).out.bits.req, medaArb(DC_ID).out.fire())
    s1_tag_reg(DC_ID)       := RegEnable(medaArb(DC_ID).out.bits.req.addr(paddrBits-1, untagBits), medaArb(DC_ID).out.fire())
    s1_forceHit_reg         := RegEnable(medaArb(DC_ID).in(0).fire() || (medaArb(DC_ID).in(1).fire() && medaArb(DC_ID).in(1).bits.tagWrEna) || medaArb(DC_ID).in(2).fire(), medaArb(DC_ID).out.fire())
    s1_way_en_reg           := RegEnable(medaArb(DC_ID).out.bits.way_en, medaArb(DC_ID).out.fire())

    // check for unsupported operations
    assert(!s1_valid_reg(DC_ID) || !s1_req_reg(DC_ID).cmd.isOneOf(M_PWR))

    val s1_read               = isRead(s1_req_reg(DC_ID).cmd)
    val s1_write              = isWrite(s1_req_reg(DC_ID).cmd)
    s2_checkProc_reg(DC_ID)  := Bool(false)
    when (s1_clk_en(DC_ID)) {
      s2_readWrite_reg    := isRead(s1_req_reg(DC_ID).cmd) || s1_write || isPrefetch(s1_req_reg(DC_ID).cmd)
      s2_req_reg(DC_ID)   := s1_req_reg(DC_ID)
      s2_forceHit_reg     := s1_forceHit_reg
      s2_way_en_reg       := s1_way_en_reg
      when (s1_write) {
        s2_req_reg(DC_ID).data := Mux(s1_replay_reg, mshrs.io.replay(DC_ID).bits.data, cpuInt.s1_data.data)
      }
      .otherwise {
        s2_req_reg(DC_ID).data := s2_req_reg(DC_ID).data
      }

      // Produce nack if it has a conflict with FPU write.
      s2_earlyNack_reg(DC_ID)  := Bool(false)
      s2_fpuPending_reg(DC_ID) := Bool(false)
      when(s1_req_reg(DC_ID).tag(log2Up(NumArbInp)) && s1_read && (s1_valid_reg(DC_ID) || s1_replay_reg)) {
        for(ips <- 0 until NUM_PUS) {
          val t1 = (s1_req_reg(DC_ID).pipeId === UInt(ips)) && (s1_req_reg(DC_ID).ctxtId === io.duReadyCheck(ips).ctxtIdM) && io.duReadyCheck(ips).validFpuM && Bool(USE_TWO_CYCLES_RF)
          val t2 = (s1_req_reg(DC_ID).pipeId === UInt(ips)) && (s1_req_reg(DC_ID).ctxtId === io.duReadyCheck(ips).ctxtId0) && io.duReadyCheck(ips).validFpu0
          val t3 = (s1_req_reg(DC_ID).pipeId === UInt(ips)) && (s1_req_reg(DC_ID).ctxtId === io.duReadyCheck(ips).ctxtId1) && io.duReadyCheck(ips).validFpu1 && Bool(USE_TWO_CYCLES_RF)
          val t4 = (s1_req_reg(DC_ID).pipeId === UInt(ips)) && (s1_req_reg(DC_ID).ctxtId === io.duReadyCheck(ips).ctxtId1F) && io.duReadyCheck(ips).validFpu1F && Bool(USE_TWO_CYCLES_RF)
          val t5 = (s1_req_reg(DC_ID).pipeId === UInt(ips)) && (s1_req_reg(DC_ID).ctxtId === io.duReadyCheck(ips).ctxtId2F) && io.duReadyCheck(ips).validFpu2F
          val t6 = (s1_req_reg(DC_ID).pipeId === UInt(ips)) && (s1_req_reg(DC_ID).ctxtId === io.duReadyCheck(ips).ctxtId3F) && io.duReadyCheck(ips).validFpu3F && Bool(USE_TWO_CYCLES_RF)
          when(t1 || t2 || t3 || t4 || t5 || t6) {
            when(!fpuPendingBusy_reg(ips)) {
              s2_earlyNack_reg(DC_ID)   := Bool(false)
              s2_fpuPending_reg(DC_ID)  := Bool(true)
              fpuPendingBusy_reg(ips)   := Bool(true)
              s2_checkProc_reg(DC_ID)   := Bool(true)
              fpuPendingCtxtId_reg(ips) := s1_req_reg(DC_ID).ctxtId
            }
            .otherwise {
              s2_earlyNack_reg(DC_ID)  := Bool(true)
              s2_fpuPending_reg(DC_ID) := Bool(false)
            }
          }
          .otherwise {
            when(fpuPendingBusy_reg(ips) && (s1_req_reg(DC_ID).pipeId === UInt(ips)) && (s1_req_reg(DC_ID).ctxtId === fpuPendingCtxtId_reg(ips))) {
              s2_earlyNack_reg(DC_ID) := Bool(true)
            }
          }
        }
      }

      // Introduce a random NACK for AMOs.
      val randNack    = LFSR16(s1_clk_en(DC_ID))(4,0)
      when((randNack === UInt(1)) && isRdWr(s1_req_reg(DC_ID).cmd)) {
        s2_earlyNack_reg(DC_ID) := Bool(true)
      }

      // Prevent issuing two successive memops for the same ROW that require register write.
      val memopEna  = Wire(Bool())
      val succSame  = s2_clk_en(DC_ID) && s2_req_reg(DC_ID).changeReg && s2_enaTrans(DC_ID) && s1_req_reg(DC_ID).changeReg && (s2_req_reg(DC_ID).ctxtId === s1_req_reg(DC_ID).ctxtId) && (s2_req_reg(DC_ID).pipeId === s1_req_reg(DC_ID).pipeId) && dataBanks(DC_ID).s2_acc.fire()
      if(NumDCCmds == 1) {
        memopEna := !succSame
      }
      else {
        val succSameX  = s2_clk_en(DC_IDX) && s2_req_reg(DC_IDX).changeReg && s2_enaTrans(DC_IDX) && s1_req_reg(DC_ID).changeReg && (s2_req_reg(DC_IDX).ctxtId === s1_req_reg(DC_ID).ctxtId) && (s2_req_reg(DC_IDX).pipeId === s1_req_reg(DC_ID).pipeId) && dataBanks(DC_IDX).s2_acc.fire()
        memopEna := !(succSame || succSameX)
      }

      when(!memopEna) {
        s2_earlyNack_reg(DC_ID)  := Bool(true)
      }

      if(false) {
        // Force random NACKs.
        val randXX    = LFSR16(Bool(true))(2,0)
        when(randXX === UInt(0)) {
          s2_earlyNack_reg(DC_ID)  := Bool(true)
        }
      }
    }
    s2_clk_en(DC_ID) := s1_clk_en(DC_ID) && !s1_cpu_disable(DC_ID)
    when((s2_clk_en(DC_ID) && !s2_req_reg(DC_ID).needAck && !s2_isProber_reg && !s2_isWrBack_reg) || mshrs.io.ptwNotifUn) {
      ptwEarlyNotif_reg := Bool(true)
    }

    //**********************************
    // tag check and way muxing
    //**********************************
    // Check for meta hit.
    for (i <- 0 until nWays) {
      // We do not use s1_clk_en here just to relax the timing.
      s2_tag_eq_way_reg(DC_ID*nWays+i) := metaArray(DC_ID).resp(i).tag === s1_tag_reg(DC_ID)
    }
    when(s1_clk_en(DC_ID)) {
      for (i <- 0 until nWays) {
        s2_coh_reg(DC_ID*nWays+i)        := metaArray(DC_ID).resp(i).coh
      }
    }

    // Tag match for each way. We expect by most we have only one active.
    val s2_tag_match_way  = wayMap((w: Int) => s2_tag_eq_way_reg(DC_ID*nWays+w) && s2_coh_reg(DC_ID*nWays+w).isValid()).asUInt
    val s2_tag_match      = s2_tag_match_way.orR

    // Get the coherent state.
    val s2_hit_state  = Wire(new ClientMetadata)
    s2_hit_state     := s2_hit_state.fromBits(OrTree(wayMap((w: Int) => Mux(s2_tag_match_way(w), s2_coh_reg(DC_ID*nWays+w).asUInt, UInt(0)))))

    val s2_cmdCategorized_reg    = Reg(next=MemoryOpCategories.categorize(s1_req_reg(DC_ID).cmd))
    s2_hit(DC_ID)               := (s2_tag_match && s2_hit_state.onAccessFast(s2_cmdCategorized_reg)) || s2_forceHit_reg
    s2_tag_match_wayProb(DC_ID) := s2_tag_match_way
    s2_hit_stateProb(DC_ID)     := s2_hit_state

    // NACK generation in case of miss.
    val s2_nack_probe_reg  = Reg(init=Vec.fill(NUM_L2BANKS/NumDCCmds) {Bool(false)})
    val s2_nack_wb_reg     = Reg(init=Vec.fill(NUM_L2BANKS/NumDCCmds) {Bool(false)})
    val s1_idxSramble      = s1_req_reg(DC_ID).addr(untagBits-1,blockOffBits) ^ tagScramble(s1_req_reg(DC_ID).addr)
    when(s1_cpu_valid_reg(DC_ID)) {
      for(miniId <- 0 until NUM_L2BANKS by NumDCCmds) {
        val proberIdxScramble = prober(miniId+DC_ID).io.meta_read.bits.idx ^ tagScramble(Cat(prober(miniId+DC_ID).io.meta_read.bits.tag, prober(miniId+DC_ID).io.meta_read.bits.idx, UInt(0, blockOffBits)))
        s2_nack_probe_reg(miniId / NumDCCmds) := (s1_idxSramble === proberIdxScramble) && prober(miniId+DC_ID).io.enaProbNack
        s2_nack_wb_reg(miniId / NumDCCmds)    := (s1_req_reg(DC_ID).addr(paddrBits-1, blockOffBits) === wb(miniId+DC_ID).io.release.bits.address(paddrBits-1, blockOffBits)) && !wb(miniId+DC_ID).io.req.ready
      }
    }
    val s2_nack_probe = OrTree(s2_nack_probe_reg) || OrTree(s2_nack_wb_reg)

    // In the case of a miss, we need to check that:
    //   - The MSHR can accept this miss.
    //   - The prober does not process a cache line with a same index.
    // Note that: here we guarantee that the WB will clear the tag on the first access, so we prevent any further change in the cache line after the WB has started.
    val s2_nack_mshr     = !s2_hit(DC_ID) && (!mshrs.io.req(DC_ID).ready || s2_nack_probe)

    // - Make sure not to use the same cache line that is going to be evicted by the MSHR.
    // - That protect the order on the operations in the case of the need to update the permission.
    // - Avoid accepting more operations once the MSHR set the tags for the new cache-line.
    val s2_nack_victim   = s2_hit(DC_ID) && (mshrs.io.reservedWays(DC_ID) & s2_tag_match_way).orR

    s2_nackP(DC_ID)     := s2_earlyNack_reg(DC_ID) || ((s2_nack_victim || s2_nack_mshr) && s2_cpu_valid_reg)
    s2_nack(DC_ID)      := (s2_hit(DC_ID) && (!dataBanks(DC_ID).s2_acc.ready || s2_lrsc_nack_reg(DC_ID))) || s2_nackP(DC_ID)
    cpuInt.s2_nack      := s2_valid_reg(DC_ID) && s2_nack(DC_ID)

    // NACK relates status.
    cpuInt.s2_nack_cause1 := s2_lrsc_nack_reg(DC_ID)
    cpuInt.s2_nack_cause2 := (!s2_hit(DC_ID) && !mshrs.io.req(DC_ID).ready) || s2_nack_victim || (s2_nack_mshr && !s2_nack_probe)
    cpuInt.s2_nack_cause3 := s2_nack_probe

    // Register NACK and ctxtId.
    s3_nack_reg(DC_ID)      := cpuInt.s2_nack
    s3_ctxtId_reg(DC_ID)    := s2_req_reg(DC_ID).ctxtId
    s3_pipeId_reg(DC_ID)    := s2_req_reg(DC_ID).pipeId

    //**********************************
    // replacement policy. Using LFSR to randomly select one.
    //**********************************
    val replacer           = cacheParams.replacement
    val s1_replaced_way_en = UIntToOH(replacer.way)
    val s2_replaced_way_en = RegEnable(s1_replaced_way_en, s1_clk_en(DC_ID))
    val s2_repl_meta       = Reg(next=Mux1H(s1_replaced_way_en, wayMap((w: Int) => metaArray(DC_ID).resp(w)).toSeq))

    //**********************************
    // miss handling
    //**********************************
    mshrs.io.reqE(DC_ID).valid         := s1_valid_reg(DC_ID)
    mshrs.io.reqE(DC_ID).bits          := s1_req_reg(DC_ID)
    mshrs.io.req(DC_ID).valid          := !s2_nack_probe && s2_valid_reg(DC_ID) && !s2_hit(DC_ID) && s2_readWrite_reg && !s2_earlyNack_reg(DC_ID)
    io.req_trace_valid(DC_ID)          := (!s2_nack_probe && s2_valid_reg(DC_ID) && !s2_hit(DC_ID) && s2_readWrite_reg && !s2_earlyNack_reg(DC_ID)) & mshrs.io.req(DC_ID).ready
    io.req_trace_addr(DC_ID)           := s2_req_reg(DC_ID).addr
    io.req_trace_ctxt(DC_ID)           := Cat(s2_req_reg(DC_ID).pipeId,s2_req_reg(DC_ID).ctxtId)
    mshrs.io.req(DC_ID).bits           := s2_req_reg(DC_ID)
    mshrs.io.req(DC_ID).bits.tag_match := s2_tag_match
    mshrs.io.req(DC_ID).bits.old_meta  := Mux(s2_tag_match, L1Metadata(s2_repl_meta.tag, s2_hit_state), s2_repl_meta)
    mshrs.io.req(DC_ID).bits.way_en    := Mux(s2_tag_match, s2_tag_match_way, s2_replaced_way_en)
    mshrs.io.req(DC_ID).bits.data      := s2_req_reg(DC_ID).data

    // Update the LFSR of the victim selector.
    when (s2_valid_reg(DC_ID) && !s2_tag_match) {
      replacer.miss
    }

    // Then repeat the transaction again after updating the tags/data memories.
    s1_replay_reg                := mshrs.io.replay(DC_ID).fire() && mshrs.io.replay(DC_ID).bits.dataAccEna

    //**********************************
    // load-reserved/store-conditional.
    //**********************************
    val (s2_lr, s2_sc)     = (s2_req_reg(DC_ID).cmd === M_XLR, s2_req_reg(DC_ID).cmd === M_XSC)

    // Decrement the LR/SC counter.
    lrsc_valid(DC_ID) := Bool(false)
    when (lrsc_validL_reg) {
      lrsc_count_reg := lrsc_count_reg - 1
      lrsc_valid(DC_ID) := Bool(true)
    }

    // Terminate the LR/SC if we find that the MSHR tries to acquire the cache line again.
    when (lrsc_validL_reg) {
      for(miniId <- DC_ID until NUM_L2BANKS by NumDCCmds) {
        val memAcqFire_reg = Reg(next=mshrs.io.mem_acquire(miniId).fire(), init=Bool(false))
        val memAcqAddr_reg = RegEnable(next=mshrs.io.mem_acquire(miniId).bits.address >> (blockOffBits + NumDCCmdsL2), mshrs.io.mem_acquire(miniId).fire())
        when (memAcqFire_reg && (lrsc_addr_reg === memAcqAddr_reg)) {
          lrsc_count_reg := 0
        }
      }
    }

    val lrscSame = (lrsc_addr_reg === (s2_req_reg(DC_ID).addr >> (blockOffBits + NumDCCmdsL2)))
    when((!s2_nack(DC_ID) && s2_valid_reg(DC_ID) && s2_hit(DC_ID)) || (mshrs.io.s2_dataBankReady(DC_ID) && s2_replay_reg(DC_ID))){
      when (lrsc_validL_reg) {
        // On receiving any other transaction from the same ROW, terminate the count.
        when((s2_req_reg(DC_ID).ctxtId === lrsc_ctxtId_reg) && (s2_req_reg(DC_ID).pipeId === lrsc_pipeId_reg)) {
          lrsc_count_reg := 0
        }

        // If any ROW is trying to access the same address for write, terminate the count.
        when(lrscSame && (isWrite(s2_req_reg(DC_ID).cmd) && !s2_sc)) {
          lrsc_count_reg := 0
        }

        // Check if SC shall fail/pass.
        when(s2_sc) {
          when((s2_req_reg(DC_ID).ctxtId === lrsc_ctxtId_reg) && (s2_req_reg(DC_ID).pipeId === lrsc_pipeId_reg) && lrscSame) {
            lrsc_count_reg := 0
          }
        }
      }
      .otherwise {
        when (s2_lr) {
          lrsc_count_reg  := lrscCycles - 1
          lrsc_addr_reg   := s2_req_reg(DC_ID).addr >> (blockOffBits + NumDCCmdsL2)
          lrsc_ctxtId_reg := s2_req_reg(DC_ID).ctxtId
          lrsc_pipeId_reg := s2_req_reg(DC_ID).pipeId
        }
      }
    }

    // We do not consider dataBanks(DC_ID).s2_acc.ready in generating s2_sc_fail.
    val s2_sc_fail  = Wire(Bool())
    s2_sc_fail     := Bool(false)
    when (lrsc_validL_reg) {
      // Check if SC shall fail/pass.
      when(s2_sc) {
        s2_sc_fail := Bool(true)
        when((s2_req_reg(DC_ID).ctxtId === lrsc_ctxtId_reg) && (s2_req_reg(DC_ID).pipeId === lrsc_pipeId_reg) && lrscSame) {
          s2_sc_fail := Bool(false)
        }
      }
    }
    .otherwise {
      when(s2_sc) {
        s2_sc_fail := Bool(true)
      }
    }

    //**********************************
    // Get the hit data.
    //**********************************
    s0_enaTrans(DC_ID)                      := medaArb(DC_ID).out.fire() && medaArb(DC_ID).out.bits.dataAccEna
    s0_notif(DC_ID)                         := s0_enaTrans(DC_ID) && !medaArb(DC_ID).out.bits.wbValid && isRead(medaArb(DC_ID).out.bits.req.cmd) && medaArb(DC_ID).out.bits.req.needAck
    s1_enaTrans_reg(DC_ID)                  := s0_enaTrans(DC_ID)
    s2_enaTrans_reg(DC_ID)                  := s1_enaTrans_reg(DC_ID)
    s2_cpu_valid_reg                        := s1_cpu_valid_reg(DC_ID)
    s2_enaTrans(DC_ID)                      := s2_enaTrans_reg(DC_ID) && !s2_earlyNack_reg(DC_ID) && !s2_lrsc_nack_reg(DC_ID) && ((!s2_nack(DC_ID) && s2_valid_reg(DC_ID) && s2_hit(DC_ID)) || !s2_cpu_valid_reg)
    dataBanks(DC_ID).s2_acc.valid           := s2_enaTrans_reg(DC_ID) && !s2_earlyNack_reg(DC_ID) && !s2_lrsc_nack_reg(DC_ID) && ((!s2_nackP(DC_ID) && s2_valid_reg(DC_ID) && s2_hit(DC_ID)) || !s2_cpu_valid_reg) // Ignore the check for s2_acc.ready.
    dataBanks(DC_ID).s2_acc.bits.req        := s2_req_reg(DC_ID)
    dataBanks(DC_ID).s2_acc.bits.wbValid    := Reg(next=Reg(next=medaArb(DC_ID).out.bits.wbValid))
    dataBanks(DC_ID).s2_acc.bits.valid_full := !s2_sc_fail
    dataBanks(DC_ID).s2_acc.bits.sc         := s2_sc
    dataBanks(DC_ID).s2_acc.bits.replay     := s2_replay_reg(DC_ID)
    dataBanks(DC_ID).s2_acc.bits.isCpu      := s2_cpu_valid_reg
    dataBanks(DC_ID).s2_acc.bits.hit        := s2_hit(DC_ID)
    dataBanks(DC_ID).s2_acc.bits.sc_fail    := s2_sc_fail

    // When not real, enable one of the ways to pass the transaction.
    // In the case of forced hit, s2_way_en_reg needs to be used instead.
    dataBanks(DC_ID).s2_acc.bits.waySel     := Mux(s2_forceHit_reg, s2_way_en_reg, s2_tag_match_way)

    //**********************************
    // Un-cache selection.
    //**********************************
    val dbSel = Wire(Bool())
    if(NumDCCmds == 1) {
      dbSel := Bool(true)
    }
    else {
      dbSel := (halfSelD6_reg(DC_ID) === mshrs.io.m1_unCachePipeId)
    }
    dataBanks(DC_ID).m1_unCacheOutReq  := mshrs.io.m1_unCacheOutReq && !mshrs.io.m2_unCacheAck && dbSel
    dataBanks(DC_ID).m1_unCacheCtxtId  := mshrs.io.m1_unCacheCtxtId
    dataBanks(DC_ID).m1_unCachePipeId  := mshrs.io.m1_unCachePipeId
    if(NumDCCmds == 1) {
      dataBanks(DC_ID).unCacheProt_in   := Bool(false)
    }
    else {
      dataBanks(DC_ID).unCacheProt_in   := dataBanks(DC_IDX).unCacheProt_out
    }

    //**********************************
    // Cache buses.
    //**********************************
    val loadgen                          = new LoadGen(dataBanks(DC_ID).m2_acc.req.typ, mtSigned(dataBanks(DC_ID).m2_acc.req.typ), dataBanks(DC_ID).m2_acc.req.addr, dataBanks(DC_ID).m2_acc.req.data, dataBanks(DC_ID).m2_acc.sc, wordBytes)
    cache_resp(DC_ID).valid             := dataBanks(DC_ID).m2_acc.isCpu
    cache_resp(DC_ID).bits              := dataBanks(DC_ID).m2_acc.req
    cache_resp(DC_ID).bits.has_data     := isRead(dataBanks(DC_ID).m2_acc.req.cmd)
    cache_resp(DC_ID).bits.data         := loadgen.data | dataBanks(DC_ID).m2_acc.sc_fail
    cache_resp(DC_ID).bits.replay       := dataBanks(DC_ID).m2_acc.replay
    cache_resp(DC_ID).bits.fpuPending   := Reg(next=Reg(next=Reg(next=Reg(next=s2_fpuPending_reg(DC_ID)))))
    uncache_resp(DC_ID).bits            := mshrs.io.resp.bits
    uncache_resp(DC_ID).valid           := dataBanks(DC_ID).m2_unCacheAck && isRead(mshrs.io.resp.bits.cmd)
    uncache_resp(DC_ID).bits.fpuPending := Bool(false)

    // Choose the output.
    sel_resp(DC_ID)                       := Mux(cache_resp(DC_ID).valid, cache_resp(DC_ID), uncache_resp(DC_ID))
    sel_resp(DC_ID).bits.data_word_bypass := Mux(cache_resp(DC_ID).valid, loadgen.wordData, mshrs.io.resp.bits.data)
    dcArb(DC_ID).mem.resp                 := sel_resp(DC_ID)

    //**********************************
    // Connect debug signals.
    //**********************************
    val s1_currCtxtId  = Wire(Bits(width=4))
    if(NUM_PUS == 2) {
      s1_currCtxtId     := Cat(s1_req_reg(DC_ID).ctxtId, s1_req_reg(DC_ID).pipeId)
    }
    else {
      s1_currCtxtId     := s1_req_reg(DC_ID).ctxtId
    }

    val s2_resp  = Wire(Bits(width=4))
    s2_resp     := Cat(dataBanks(DC_ID).s2_acc.fire(), s2_enaTrans(DC_ID), s2_nack(DC_ID), s2_hit(DC_ID))

    val s2_status_reg     = Reg(next=Cat(s1_req_reg(DC_ID).cmd(3, 0), s1_currCtxtId(3,0), s1_req_reg(DC_ID).addr(31, 0)))
    val s2_nack_cause     = Mux(Reg(next=s1_clk_en(DC_ID)), Cat(s2_hit(DC_ID) && !dataBanks(DC_ID).s2_acc.ready, cpuInt.s2_nack_cause3, cpuInt.s2_nack_cause2 && !s2_forceHit_reg, s2_earlyNack_reg(DC_ID)), UInt(0))
    val s2_fire_reg       = Reg(next=Reg(next=Cat(medaArb(DC_ID).in(3).fire(),medaArb(DC_ID).in(2).fire(), medaArb(DC_ID).in(1).fire(), medaArb(DC_ID).in(0).fire())))
    // if(DC_ID == 0) {
    //   io.duTrace.statusDU0 := Cat(s2_nack_cause(3, 0), s2_resp, s2_status_reg(39, 0), s2_fire_reg(3,0), mshrs.io.status(3,0))
    // }
    // else {
    //   io.duTrace.statusDU1 := Cat(s2_nack_cause(3, 0), s2_resp, s2_status_reg(39, 0), s2_fire_reg(3,0), mshrs.io.status(7,4))
    // }

    // If we generate NACK, we may free fpuPendingBusy_reg.
    for(ips <- 0 until NUM_PUS) {
      when(!dataBanks(DC_ID).s2_acc.fire() && s2_checkProc_reg(DC_ID) && (s2_req_reg(DC_ID).pipeId === UInt(ips))) {
        fpuPendingBusy_reg(ips)  := Bool(false)
      }
    }
  }

  //**********************************
  // Notify the DUC about the ACK/NACK.
  //**********************************
  for(ips <- 0 until NUM_PUS) {
    if (DEBUG == 2) {
      // Check for any halt.
      val dbgCntr_reg       = Reg(init=UInt(0, 30))
      dbgCntr_reg          := dbgCntr_reg + UInt(1)
      when(fpuPendingBusy_reg(ips) === Bool(false)) {
        dbgCntr_reg := UInt(0)
      }
      when(dbgCntr_reg >= UInt(4096*16*DBG_WAIT_SCALE)) {
        assert(Bool(false), "fpuPendingBusy_reg stuck.")
      }
    }

    // NACK bus.
    if(NumDCCmds == 1) {
      io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.valid       := s0_notif(0) && (medaArb(0).out.bits.req.pipeId === UInt(ips))
      io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.ctxtId      := (medaArb(0).out.bits.req.ctxtId)
      io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.destReg     := (medaArb(0).out.bits.req.destReg)
      io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.isFpuAccess := (medaArb(0).out.bits.req.tag(log2Up(NumArbInp)))

      val stSafeLateE         = dataBanks(0).s2_acc.fire() && isWrite(dataBanks(0).s2_acc.bits.req.cmd) && !isRdWr(dataBanks(0).s2_acc.bits.req.cmd) && (dataBanks(0).s2_acc.bits.req.pipeId === UInt(ips))
      duc(ips).stSafeLate    := Reg(next=stSafeLateE, init=Bool(false))
      duc(ips).ctxtIdLate    := RegEnable(dataBanks(0).s2_acc.bits.req.ctxtId, stSafeLateE)

      duHitEarlyX(ips)     := Reg(next=(s2_enaTrans(0) && dataBanks(0).s2_acc.ready && !s2_fpuPending_reg(0)))
    }
    else {
      val halfSelD1          = halfSelD1_reg(1-ips)
      io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.valid       := Mux(halfSelD1, s0_notif(0), s0_notif(1))
      io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.ctxtId      := Mux(halfSelD1, medaArb(0).out.bits.req.ctxtId, medaArb(1).out.bits.req.ctxtId)
      io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.destReg     := Mux(halfSelD1, medaArb(0).out.bits.req.destReg, medaArb(1).out.bits.req.destReg)
      io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.isFpuAccess := Mux(halfSelD1, medaArb(0).out.bits.req.tag(log2Up(NumArbInp)), medaArb(1).out.bits.req.tag(log2Up(NumArbInp)))
      assert(!io.dataUnitToCtxt(ips).duCtxtEarlyNotifier2.valid || (Mux(halfSelD1, medaArb(0).out.bits.req.pipeId, medaArb(1).out.bits.req.pipeId) === UInt(ips)))

      val halfSelD3           = halfSelD3_reg(1-ips)
      val stSafeLateE         = Mux(halfSelD3, dataBanks(0).s2_acc.fire() && isWrite(dataBanks(0).s2_acc.bits.req.cmd) && !isRdWr(dataBanks(0).s2_acc.bits.req.cmd), dataBanks(1).s2_acc.fire() && isWrite(dataBanks(1).s2_acc.bits.req.cmd) && !isRdWr(dataBanks(1).s2_acc.bits.req.cmd))
      duc(ips).stSafeLate    := Reg(next=stSafeLateE, init=Bool(false))
      duc(ips).ctxtIdLate    := RegEnable(Mux(halfSelD3, dataBanks(0).s2_acc.bits.req.ctxtId, dataBanks(1).s2_acc.bits.req.ctxtId), stSafeLateE)

      duHitEarlyX(ips)     := Reg(next=(Mux(halfSelD3, s2_enaTrans(0) && dataBanks(0).s2_acc.ready && !s2_fpuPending_reg(0), s2_enaTrans(1) && dataBanks(1).s2_acc.ready && !s2_fpuPending_reg(1))))
    }

    for(DC_ID <- 0 until NumDCCmds) {
      duc(ips).s2_nack(DC_ID)        := dcArb(DC_ID).requestor(1).s2_nack
      duc(ips).s2_nack_cause1(DC_ID) := dcArb(DC_ID).requestor(1).s2_nack_cause1
      duc(ips).s2_nack_cause2(DC_ID) := dcArb(DC_ID).requestor(1).s2_nack_cause2
      duc(ips).s2_nack_cause3(DC_ID) := dcArb(DC_ID).requestor(1).s2_nack_cause3
    }

    duc(ips).stSafeLateUn  := mshrs.io.stSafeLateUn && (mshrs.io.pipeIdLateUn === UInt(ips))
    duc(ips).ctxtIdLateUn  := mshrs.io.ctxtIdLateUn
  }

  // Connect duc.
  for(ips <- 0 until NUM_PUS) {
    val res = Wire(Valid(new HellaCacheResp(paddrBits)))
    if(NumDCCmds == 1) {
      res := dcArb(0).requestor(1).resp
    }
    else {
      res := Mux(halfSelD7_reg(ips), dcArb(1).requestor(1).resp, dcArb(0).requestor(1).resp)
    }

    respValid_reg(ips)   := res.valid
    when(res.valid) {
      // Latch the read word.
      when(res.bits.has_data) {
        dataWordBypass_reg(ips) := res.bits.data_word_bypass
        readData_reg(ips)       := res.bits.data
        readAddr_reg(ips)       := res.bits.addr
      }

      respTag_reg(ips)        := res.bits.tag
      respCtxtId_reg(ips)     := res.bits.ctxtId
      respChangeReg_reg(ips)  := res.bits.changeReg
      respDestReg_reg(ips)    := res.bits.destReg
      respFpuPending_reg(ips) := res.bits.fpuPending

      if(NumDCCmds == 2) {
        assert(res.bits.pipeId === UInt(ips))
      }
    }

    val isFpuAccess = respTag_reg(ips)(0)
    val size        = respTag_reg(ips)(MT_SZ + 0, 1)

    // Check for end of transaction. That includes the fake ones.
    val endLdTrans  = respValid_reg(ips) && respChangeReg_reg(ips)

    io.dataUnitToFpu(ips).valid             := endLdTrans && isFpuAccess
    io.dataUnitToFpu(ips).bits.isReal       := Bool(true)
    io.dataUnitToFpu(ips).bits.dataToWrite  := dataWordBypass_reg(ips)
    io.dataUnitToFpu(ips).bits.destReg      := respDestReg_reg(ips)
    io.dataUnitToFpu(ips).bits.ctxtId       := respCtxtId_reg(ips)
    io.dataUnitToFpu(ips).bits.size         := size
    io.fpuPending(ips)                      := respFpuPending_reg(ips)

    // Connect DUC to regFSet.
    io.dataUnitToRegfset(ips).valid                := endLdTrans && !isFpuAccess
    io.dataUnitToRegfset(ips).bits.isReal          := Bool(true)
    io.dataUnitToRegfset(ips).bits.dataToWrite     := readData_reg(ips)
    io.dataUnitToRegfset(ips).bits.destReg         := respDestReg_reg(ips)
    io.dataUnitToRegfset(ips).bits.ctxtId          := respCtxtId_reg(ips)
    io.dataUnitToRegfset(ips).bits.size            := size
    assert(Reg(next=Reg(next=(!io.dataUnitToRegfset(ips).valid || io.dataUnitToRegfset(ips).ready), init=Bool(true)), init=Bool(true)), "DataUnit sends a transaction while RegFSet is not ready.")

    if (DEBUG == 2) {
      when(endLdTrans) {
        printf("LD%x_%x: %x %x %x\n", (io.hartid*UInt(NUM_PUS)) + UInt(ips), respCtxtId_reg(ips), ldCntr_reg(UInt(ips) + respCtxtId_reg(ips) * UInt(NUM_PUS)), readAddr_reg(ips)(paddrBits-1, 0), readData_reg(ips))
        ldCntr_reg(UInt(ips) + respCtxtId_reg(ips) * UInt(NUM_PUS))  := ldCntr_reg(UInt(ips) + respCtxtId_reg(ips) * UInt(NUM_PUS)) + UInt(1)
      }
    }
  }
  io.ptwEarlyNotif      := ptwEarlyNotif_reg
  io.unCacheFpuNotif    := mshrs.io.unCacheFpuNotif

  //**********************************
  // Write to the data memory.
  //**********************************
  val dataMshr_reg    = Reg(Vec(NUM_L2BANKS, UInt(width=CACHE_BLOCK_BITS)))
  val writeValid_reg  = Reg(init=Vec.fill(NUM_L2BANKS) {Bool(false)})
  val writeAddr_reg   = Reg(Vec(NUM_L2BANKS, UInt(width=untagBits)))
  val writeWayEn_reg  = Reg(Vec(NUM_L2BANKS, UInt(width=nWays)))

  for(miniId <- 0 until NUM_L2BANKS) {
    val dataMshrB_reg   = Reg(UInt(width=CACHE_BLOCK_BITS))
    val writeValidB_reg = Reg(init=Bool(false))
    val refillDoneB_reg = Reg(init=Bool(false))
    val writeAddrB_reg  = Reg(UInt())
    val writeWayEnB_reg = Reg(UInt())
    val writeReadyB_reg = Reg(init=Bool(true))
    val writeCntrB_reg  = Reg(init=UInt(0, log2Up(CACHE_BLOCK_BITS/encRowBits)))

    val refillDone_reg      = Reg(init=Bool(false))
    val writeReady_reg      = Reg(init=Bool(true))
    val mshrWriteAckPre_reg = Reg(init=Bool(false))
    val mshrWriteAck_reg    = Reg(init=Bool(false))
    val mshrWriteD1_reg     = Reg(init=Bool(false))
    val mshrWriteD2_reg     = Reg(init=Bool(false))

    // Concatenate the input data.
    val wdata_encoded   = (0 until rowWords).map(i => tl_out(miniId).d.bits.data(coreDataBits*(i+1)-1,coreDataBits*i))
    val grant_has_data  = edge.hasData(tl_out(miniId).d.bits)
    when(tl_out(miniId).d.fire() && grant_has_data && (tl_out(miniId).d.bits.source < UInt(nMSHRs/NUM_L2BANKS))) {
      writeCntrB_reg := writeCntrB_reg + UInt(1)
      dataMshrB_reg  := Cat(Cat(wdata_encoded.reverse), dataMshrB_reg(dataMshrB_reg.getWidth-1, encRowBits))

      when(writeCntrB_reg === UInt(CACHE_BLOCK_BITS/encRowBits - 1)) {
        writeValidB_reg  := Bool(true)
        writeAddrB_reg   := mshrs.io.refill(miniId).addr
        writeWayEnB_reg  := mshrs.io.refill(miniId).way_en
        refillDoneB_reg  := mshrs.io.refill(miniId).done

        when(!(writeReady_reg || mshrWriteD2_reg)) {
          writeReadyB_reg  := Bool(false)
        }
      }
    }

    // Connects TL.d ready signal.
    tl_out(miniId).d.ready   := writeReadyB_reg || !grant_has_data

    mshrWriteAckPre_reg := Bool(false)
    when(writeValidB_reg && writeReady_reg) {
      dataMshr_reg(miniId)   := dataMshrB_reg
      writeValid_reg(miniId) := Bool(true)
      writeReady_reg         := Bool(false)
      writeAddr_reg(miniId)  := writeAddrB_reg
      writeWayEn_reg(miniId) := writeWayEnB_reg
      refillDone_reg         := refillDoneB_reg
      writeValidB_reg        := Bool(false)
      writeReadyB_reg        := Bool(true)
      mshrWriteAckPre_reg    := Bool(true)
    }

    // De-assert the valid when the write transaction is committed.
    val dbWriteFire  = dataBanks(miniId%NumDCCmds).write(miniId/NumDCCmds).fire()
    when(dbWriteFire) {
      writeValid_reg(miniId) := Bool(false)
    }

    // A pre-ack flag, so we can safely have an outstanding cache line.
    mshrs.io.mshrWriteAckPre(miniId) := mshrWriteAckPre_reg

    // Send an acknowledge to the MSHR.
    mshrWriteAck_reg              := dbWriteFire && refillDone_reg
    mshrs.io.mshrWriteAck(miniId) := mshrWriteAck_reg

    mshrWriteD1_reg  := dbWriteFire
    mshrWriteD2_reg  := mshrWriteD1_reg
    assert((!mshrWriteD2_reg && !mshrWriteD1_reg && dbWriteFire) || !dbWriteFire, "MSHR cannot write on two successive cycles.")

    when(mshrWriteD2_reg) {
      writeReady_reg  := Bool(true)
    }
  }

  // Connect the write bus.
  for(miniId <- 0 until NUM_L2BANKS) {
    dataBanks(miniId%NumDCCmds).write(miniId/NumDCCmds).valid       := writeValid_reg(miniId)
    dataBanks(miniId%NumDCCmds).write(miniId/NumDCCmds).bits.addr   := writeAddr_reg(miniId)
    dataBanks(miniId%NumDCCmds).write(miniId/NumDCCmds).bits.way_en := writeWayEn_reg(miniId)
    dataBanks(miniId%NumDCCmds).write(miniId/NumDCCmds).bits.wmask  := ~UInt(0, rowWords)
    dataBanks(miniId%NumDCCmds).dataMshr(miniId/NumDCCmds)          := dataMshr_reg(miniId)
  }
  if(NumDCCmds == 1) {
    io.earlyDuWrFlag  := dataBanks(0).earlyDuWrFlag
    io.earlyFpuWrFlag := dataBanks(0).earlyFpuWrFlag
    io.earlyCtxtFpuWr := dataBanks(0).earlyCtxtFpuWr
  }
  else {
    io.earlyDuWrFlag     := dataBanks(0).earlyDuWrFlag | dataBanks(1).earlyDuWrFlag
    io.earlyFpuWrFlag    := dataBanks(0).earlyFpuWrFlag | dataBanks(1).earlyFpuWrFlag
    io.earlyCtxtFpuWr(0) := Mux(dataBanks(0).earlyFpuWrFlag(0), dataBanks(0).earlyCtxtFpuWr(0), dataBanks(1).earlyCtxtFpuWr(0))
    io.earlyCtxtFpuWr(1) := Mux(dataBanks(0).earlyFpuWrFlag(1), dataBanks(0).earlyCtxtFpuWr(1), dataBanks(1).earlyCtxtFpuWr(1))
  }

  if(NumDCCmds == 1) {
    mshrs.io.m2_unCacheAck    := dataBanks(0).m2_unCacheAck
  }
  else {
    mshrs.io.m2_unCacheAck    := dataBanks(0).m2_unCacheAck || dataBanks(1).m2_unCacheAck
    assert(!(dataBanks(0).m2_unCacheAck && dataBanks(1).m2_unCacheAck))
  }

  //**********************************
  // Connect memory buses to MSHR.
  //**********************************
  for(miniId <- 0 until NUM_L2BANKS) {
    val tl_d_data = Mux(tl_out(miniId).d.bits.corrupt || tl_out(miniId).d.bits.denied, UInt(48057234611961770L) , tl_out(miniId).d.bits.data)
    tl_out(miniId).a <> mshrs.io.mem_acquire(miniId)
    tl_out(miniId).e <> mshrs.io.mem_finish(miniId)

    // refills
    mshrs.io.mem_grant(miniId).valid := tl_out(miniId).d.fire()
    mshrs.io.mem_grant(miniId).bits  := tl_out(miniId).d.bits
    mshrs.io.mem_grant(miniId).bits.data := tl_d_data
  }

  //**********************************
  // Write back.
  //**********************************
  for(miniId <- 0 until NUM_L2BANKS) {
    wbArb(miniId).io.in(0)  <> prober(miniId).io.wb_req
    wbArb(miniId).io.in(1)  <> mshrs.io.wb_req(miniId)
    wb(miniId).io.req       <> wbArb(miniId).io.out

    wb(miniId).io.m2_wbResp    := dataBanks(miniId%NumDCCmds).m2_wbResp(miniId/NumDCCmds)
    wb(miniId).io.m2_wbValid   := dataBanks(miniId%NumDCCmds).m2_wbValid(miniId/NumDCCmds)
    wb(miniId).io.data_req_rdy := mshrs.io.s2_dataBankReady(miniId%NumDCCmds)

    mshrs.io.wbDone(miniId)  := wb(miniId).io.wbDone
    prober(miniId).io.wbDone := wb(miniId).io.wbDone
  }
  mshrs.io.halfSel := halfSel_reg

  //**********************************
  // Status to DUC.
  //**********************************
  for(ips <- 0 until NUM_PUS) {
    for (DC_ID <- 0 until NumDCCmds) {
      duc(ips).mshrs_ready_ch(DC_ID) := mshrs.io.mshrs_ready_ch_un(DC_ID) || (if(nMSHRs / NumDCCmds > 1) mshrs_ready_ch_reg(DC_ID) else mshrs.io.mshrs_ready(DC_ID))
    }
    for(miniId <- 0 until NUM_L2BANKS) {
      duc(ips).prober_ready(miniId)   := prober(miniId).io.req.ready
    }
    duc(ips).lrsc_ready     := lrsc_ready_reg
  }

  //**********************************
  // probes and releases connections.
  //**********************************
  for(miniId <- 0 until NUM_L2BANKS) {
    // Round-robin, not lowest (priority-first): lowest let a busy
    // WritebackUnit (wb.io.release, index 0) starve the ProbeUnit
    // (prober.io.rep, index 1) indefinitely, hanging on probe-heavy tests.
    TLArbiter.robin(edge, tl_out(miniId).c, wb(miniId).io.release, prober(miniId).io.rep)
    prober(miniId).io.req.valid   := tl_out(miniId).b.valid
    tl_out(miniId).b.ready        := prober(miniId).io.req.ready
    prober(miniId).io.req.bits    := tl_out(miniId).b.bits

    prober(miniId).io.way_en      := s2_tag_match_wayProb(miniId%NumDCCmds)
    prober(miniId).io.block_state := s2_hit_stateProb(miniId%NumDCCmds)
    prober(miniId).io.lrsc_valid  := lrsc_valid(miniId%NumDCCmds)
    prober(miniId).io.mshr_rdy    := mshrs.io.probe_rdy(miniId)

    if(NUM_L2BANKS > 1) {
      // Make sure that we receive probe only on the wanted mini-cache.
      when(prober(miniId).io.req.valid) {
        assert(prober(miniId).io.req.bits.address(blockOffBits+log2Up(NUM_L2BANKS)-1, blockOffBits) === UInt(miniId))
      }
    }
  }
  if(NUM_L2BANKS > 1) {
    println("\nMini-cache identifier: address[" ++ (blockOffBits+log2Up(NUM_L2BANKS)-1).toString ++ "," ++ blockOffBits.toString ++ "]\n")
  }

  val supports_flush = !edge.manager.managers.forall(m => !m.supportsAcquireT || !m.executable || m.regionType >= RegionType.TRACKED || m.regionType <= RegionType.UNCACHED)
  require(!supports_flush)
}

