// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.amba._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.subsystem.RocketTilesKey
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import chisel3.internal.sourceinfo.SourceInfo
import Chisel.ImplicitConversions._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants.Util._
import chisel3.dontTouch
import chisel3.util.random.LFSR
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.model._

case class ICacheParams(
    nSets: Int = 128,
    nWays: Int = 8,
    rowBits: Int = 128,
    nTLBEntries: Int = 16, // Per ROW.
    cacheIdBits: Int = 0,
    tagECC: Option[String] = None,
    dataECC: Option[String] = None,
    itimAddr: Option[BigInt] = None,
    prefetch: Boolean = false,
    blockBytes: Int = 64,
    latency: Int = 2,
    fetchBytes: Int = 4) extends L1CacheParams {
  def tagCode: Code = Code.fromString(tagECC)
  def dataCode: Code = Code.fromString(dataECC)
}

trait HasL1ICacheParameters extends HasL1CacheParameters with HasCoreParameters {
  val cacheParams = tileParams.icache.get
}

class ICacheReq(implicit p: Parameters) extends CoreBundle()(p) {
  val idx    = UInt(width = pgIdxBits+ppnBits)
  val pipeId = UInt(width = PIPE_ID_LEN)
}

class ICacheErrors(implicit p: Parameters) extends CoreBundle()(p)
    with HasL1ICacheParameters
    with CanHaveErrors {
  val correctable = (cacheParams.tagCode.canDetect || cacheParams.dataCode.canDetect).option(Valid(UInt(width = paddrBits)))
  val uncorrectable = (cacheParams.itimAddr.nonEmpty && cacheParams.dataCode.canDetect).option(Valid(UInt(width = paddrBits)))
}

class ICacheResp(implicit p: Parameters) extends CoreBundle()(p) {
  val instrMemResp = Vec(NUM_PUS, Vec(CACHE_LINE_LEN / INSTR_LEN, UInt(width=INSTR_LEN)))
}

class ICache(val icacheParams: ICacheParams, val hartId: Int)(implicit p: Parameters) extends LazyModule {
  lazy val module = new ICacheModule(this)
  val useVM = p(TileKey).core.useVM
  val masterNode = TLClientNode(Seq(TLMasterPortParameters.v1(
    clients = Seq(TLMasterParameters.v1(
      sourceId = IdRange(0, 1 + icacheParams.prefetch.toInt), // 0=refill, 1=hint
      name = s"Core ${hartId} ICache")),
    requestFields = useVM.option(Seq()).getOrElse(Seq(AMBAProtField())))))

  val size = icacheParams.nSets * icacheParams.nWays * icacheParams.blockBytes
  val itim_control_offset = size - icacheParams.nSets * icacheParams.blockBytes

  val device = new SimpleDevice("itim", Seq("sifive,itim0")) {
    override def describe(resources: ResourceBindings): Description = {
     val Description(name, mapping) = super.describe(resources)
     val Seq(Binding(_, ResourceAddress(address, perms))) = resources("reg/mem")
     val base_address = address.head.base
     val mem_part = AddressSet.misaligned(base_address, itim_control_offset)
     val control_part = AddressSet.misaligned(base_address + itim_control_offset, size - itim_control_offset)
     val extra = Map(
       "reg-names" -> Seq(ResourceString("mem"), ResourceString("control")),
       "reg" -> Seq(ResourceAddress(mem_part, perms), ResourceAddress(control_part, perms)))
     Description(name, mapping ++ extra)
    }
  }

  def itimProperty: Option[Seq[ResourceValue]] = icacheParams.itimAddr.map(_ => device.asProperty)

  private val wordBytes = icacheParams.fetchBytes
  val slaveNode =
    TLManagerNode(icacheParams.itimAddr.toSeq.map { itimAddr => TLSlavePortParameters.v1(
      Seq(TLSlaveParameters.v1(
        address         = Seq(AddressSet(itimAddr, size-1)),
        resources       = device.reg("mem"),
        regionType      = RegionType.IDEMPOTENT,
        executable      = true,
        supportsPutFull = TransferSizes(1, wordBytes),
        supportsPutPartial = TransferSizes(1, wordBytes),
        supportsGet     = TransferSizes(1, wordBytes),
        fifoId          = Some(0))), // requests handled in FIFO order
      beatBytes = wordBytes,
      minLatency = 1)})
}

class IBankMemory(val memWidth: Int)(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val n_wr   = Bool(INPUT) // Active low write enable.
    val n_ceb  = Bool(INPUT) // Active low clock enable.
    val addr   = UInt(INPUT, log2Up(nSets * refillCycles * rowBits / CACHE_LINE_LEN))
    val wrData = UInt(INPUT, memWidth)
    val rdData = UInt(OUTPUT, memWidth)
  }
  val rdData_reg = Reg(UInt(width=memWidth))

  val BANK_BLOCK = Mem(nSets * refillCycles * rowBits / CACHE_LINE_LEN, Bits(width=memWidth))
  when(!io.n_wr && !io.n_ceb) {
    BANK_BLOCK.write(io.addr, io.wrData)
  }

  when(!io.n_ceb) {
    rdData_reg := BANK_BLOCK(io.addr)
  }

  // The following code asserts that we do not receive any two successive RD/WR operations.
  val n_cebD1_reg   = Reg(next=io.n_ceb, init=Bool(true))

  assert(!(!n_cebD1_reg && !io.n_ceb), "Receive two successive access operations in the ICache.")

  // An extra delay is added to consider the timing of the internal memories.
  // IMPRTANT: We do not need it implement this MUX, this MUX is added for testing.
  io.rdData := Mux(!n_cebD1_reg, UInt(0), rdData_reg)
}

// get a tile-specific property without breaking deduplication
object GetPropertyByHartId {
  def apply[T <: Data](tiles: Seq[RocketTileParams], f: RocketTileParams => Option[T], hartId: UInt): T = {
    PriorityMux(tiles.collect { case t if f(t).isDefined => (t.hartId === hartId) -> f(t).get })
  }
}

class ICacheBundle(val outer: ICache) extends CoreBundle()(outer.p) {
  val hartid             = UInt(INPUT, hartIdLen)
  val req                = Valid(new ICacheReq).flip
  val s1_kill            = Bool(INPUT) // delayed one cycle w.r.t. req
  val validEarly         = Bool(INPUT)
  val addrP1Early        = UInt(INPUT, pgIdxBits)

  val resp               = Decoupled(new ICacheResp)
  val invalidate         = Bool(INPUT)
  val earlyReadValid     = Bool(OUTPUT)
  val earlyReadFailCause = Bool(OUTPUT)
  val icacheReady        = Bool(OUTPUT)
  val aFirePerf          = Bool(OUTPUT)
  val dFirePerf          = Bool(OUTPUT)
}

class ICacheTagArrayMemory(MEM_DEPTH: Int, MEM_WIDTH: Int)(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val n_wr     = Bool(INPUT) // Active low write enable.
    val n_ceb    = Bool(INPUT) // Active low clock enable.
    val addr     = UInt(INPUT, log2Up(MEM_DEPTH))
    val wrData   = UInt(INPUT, MEM_WIDTH)
    val rdData   = UInt(OUTPUT, MEM_WIDTH)
  }

  // This RAM model assumes possibility to receive any two successive R/W access operations
  // We found such TSMC RAM that can read or write data in 1 clock cycle
  // This model is replaced in the backend by verilog instantiation of the TSMC memory
  val rdData_reg = Reg(UInt(width=MEM_WIDTH))
  val BANK_BLOCK = Mem(MEM_DEPTH, Bits(width=MEM_WIDTH))

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

class ICacheModule(outer: ICache) extends LazyModuleImp(outer) with HasL1ICacheParameters {
  override val cacheParams = outer.icacheParams // Use the local parameters

  val io = IO(new ICacheBundle(outer))

  // The number of ways needs to be equal in Consts.scala.
  require(nWaysIC == nWays)
  require(isPow2(nSets) && isPow2(nWays))
  require(isPow2(coreInstBytes))
  require(!outer.icacheParams.prefetch)

  // ECC encoder and decoder.
  val dECC = cacheParams.dataCode

  // Number of drop bits.
  val untagBitsX    = if (NUM_PUS == 2) (untagBits-1) else untagBits
  val pgIdxBitsX    = if (NUM_PUS == 2) (pgIdxBits-1) else pgIdxBits
  val idxSh         = Wire(UInt())
  val addrP1EarlySh = Wire(UInt())
  if(NUM_PUS == 2) {
    idxSh         := Cat(io.req.bits.idx(paddrBits-1, IC_HLF_BIT+1), io.req.bits.idx(IC_HLF_BIT-1, 0))
    addrP1EarlySh := Cat(io.addrP1Early(pgIdxBits-1, IC_HLF_BIT+1), io.addrP1Early(IC_HLF_BIT-1, 0))
  }
  else {
    idxSh         := io.req.bits.idx
    addrP1EarlySh := io.addrP1Early
  }

  // The banks are split across NUM_PUS ICache(s).
  val NumBanks  = nWays / NUM_PUS

  // Memory banks.
  val banks         = 0 to NumBanks - 1 map {x => Module(new IBankMemory(CACHE_LINE_LEN)).io}

  val NUM_SUB_TAG_MEM = Math.pow(2, untagBitsX - pgIdxBitsX).intValue

  val (tl_out, edge_out) = outer.masterNode.out(0)
  // Option.unzip does not exist :-(
  val (tl_in, edge_in) = outer.slaveNode.in.headOption.unzip

  val refill_one_beat  = tl_out.d.fire() && edge_out.hasData(tl_out.d.bits)
  val tl_d_data = Mux(tl_out.d.bits.corrupt || tl_out.d.bits.denied, UInt(48057234611961770L) , tl_out.d.bits.data)
  val s_ready :: s_request :: s_refill_wait :: s_refill :: Nil = Enum(UInt(), 4)
  val state_reg       = Reg(init=s_ready)
  val invalidated_reg = Reg(init=Bool(false))
  val tag_rdata_reg   = Reg(Vec(nWays * NUM_SUB_TAG_MEM, UInt(width=tagBits)))
  val tag_rdata_out   = Wire(init=tag_rdata_reg)

  val wen_block_reg     = Reg(init=Bool(false))
  val tagClearP_reg     = Reg(init=Bool(false))
  val dataConc_reg      = Reg(Vec(NumBanks, UInt(width=CACHE_LINE_LEN)))
  val dataConcC_reg     = Reg(UInt(width = CACHE_LINE_LEN - rowBits))
  val refill_addr_reg   = Reg(UInt(width = paddrBits))
  val refill_addrSh     = Wire(UInt())
  val wrAddr_reg        = Reg(UInt())
  val s1_tag_hit        = Wire(UInt(width = nWays))
  val s1_any_tag_hit    = Wire(Bool())
  val memBusy_reg       = Reg(init=Vec.fill(NumBanks) {Bool(false)})
  val s1_valid_reg      = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})

  // A signal where we set a multi-cycle constraint on it.
  // The name should be kept intact.
  val bankSelMultiC_reg = Reg(Vec(NUM_PUS, UInt(width=NumBanks)))
  bankSelMultiC_reg.suggestName("bankSelMultiC_reg")

  val rdEna            = Wire(Vec(NumBanks, Bool()))
  val s1_tag_match     = Wire(Vec(nWays, Bool()))
  val s1_tag_hit_reg   = Reg(init=Vec.fill(nWays) {Bool(false)})
  val s3_dout          = Wire(Vec(NumBanks, Bits(width = CACHE_LINE_LEN)))

  val refill_doneD1_reg = Reg(init=Bool(false))
  val refill_doneP_reg  = Reg(init=Bool(false))
  val s1_valid          = Reg(init=Bool(false))
  val s1_invalidTag_reg = Reg(init=Bool(false))
  val s1_addrSh         = Wire(UInt())
  val s1_addr_reg       = Wire(UInt())
  val wrMemSel_reg      = Reg(init=Vec.fill(NumBanks) {Bool(false)})

  // Make sure if the used names in RTL.
  s1_tag_match.suggestName("s1_tag_match")
  s1_tag_hit_reg.suggestName("s1_tag_hit_reg")
  s1_addrSh.suggestName("s1_addrSh")
  s1_addr_reg.suggestName("s1_addr_reg")
  refill_addr_reg.suggestName("refill_addr_reg")
  state_reg.suggestName("state_reg")
  s1_any_tag_hit.suggestName("s1_any_tag_hit")
  s1_invalidTag_reg.suggestName("s1_invalidTag_reg")
  s1_tag_hit.suggestName("s1_tag_hit")

  //TileLink connection to the D channel -- this channel brings back data in response to Get that was sent on A channel
  val (_, _, d_done, refill_cnt) = edge_out.count(tl_out.d)
  val refill_done                = refill_one_beat && d_done
  tl_out.d.ready                := Bool(true)
  require (edge_out.manager.minLatency > 0)

  s1_valid     := io.req.valid
  s1_addr_reg  := RegEnable(io.req.bits.idx, io.req.valid)

  if(NUM_PUS == 2) {
    s1_addrSh     := Cat(s1_addr_reg(paddrBits-1, IC_HLF_BIT+1), s1_addr_reg(IC_HLF_BIT-1, 0))
    refill_addrSh := Cat(refill_addr_reg(paddrBits-1, IC_HLF_BIT+1), refill_addr_reg(IC_HLF_BIT-1, 0))
  }
  else {
    s1_addrSh     := s1_addr_reg
    refill_addrSh := refill_addr_reg
  }

  // Get the full read address in a way.
  val s1_raddrF  = s1_addrSh(untagBitsX-1,CACHE_LINE_LOG2)

  // Check if the memory is not busy
  val memAvail  = Wire(Bool())
  memAvail     := Bool(true)
  for (i <- 0 until NumBanks) {
    when(s1_any_tag_hit && (s1_raddrF(log2Up(NumBanks) - 1, 0) === UInt(i)) && memBusy_reg(i)) {
      memAvail := Bool(false)
    }
  }

  val out_valid = s1_valid && !io.s1_kill
  val s1_hit    = out_valid && s1_any_tag_hit && memAvail && !s1_invalidTag_reg
  dontTouch(s1_hit)
  val s1_miss   = out_valid && (state_reg === s_ready) && !s1_any_tag_hit && !s1_invalidTag_reg

  io.earlyReadValid     := Reg(next=s1_hit && (rdEna.asUInt() =/= UInt(0)), init=Bool(false))
  io.earlyReadFailCause := Reg(next=s1_valid && !s1_any_tag_hit && !s1_invalidTag_reg, init=Bool(false))
  io.icacheReady        := (state_reg === s_ready)

  when (out_valid && (state_reg === s_ready) && s1_miss) {
    refill_addr_reg := s1_addr_reg
  }
  val tag        = refill_addrSh(tagBits+untagBitsX-1,untagBitsX)
  val repl_way   = if (isDM) UInt(0) else LFSR16(s1_miss)(log2Up(nWays)-1,0)

  val tagRdAddr = addrP1EarlySh(pgIdxBitsX-1,blockOffBits)
  val tagWrAddr = refill_addrSh(pgIdxBitsX-1,blockOffBits)

  for (i <- 0 until nWays) {
    for (k <- 0 until NUM_SUB_TAG_MEM) {
      val tag_wr = (refill_doneP_reg && ((refill_addrSh(untagBitsX-1, pgIdxBitsX) === UInt(k)) || Bool(untagBitsX == pgIdxBitsX))) && (repl_way === UInt(i))
      val tag_rd = !refill_doneP_reg && io.validEarly

      if (ICACHE_TAG_ARRAY_IN_RAM){
        // instantiating RAM instead of registers
        val tag_array = Module(new ICacheTagArrayMemory(nSets / NUM_SUB_TAG_MEM / NUM_PUS, tagBits)).io

        tag_array.addr     := Mux(tag_wr, tagWrAddr, tagRdAddr)
        tag_array.n_wr     := ! tag_wr
        tag_array.n_ceb    := !( tag_wr || tag_rd )
        tag_array.wrData   := tag
        tag_rdata_out(i*NUM_SUB_TAG_MEM + k) := tag_array.rdData
      }
      else {
        val tag_array = Mem(nSets / NUM_SUB_TAG_MEM / NUM_PUS, UInt(width=tagBits))

        when(tag_rd) {
          tag_rdata_reg(i*NUM_SUB_TAG_MEM + k) := tag_array(tagRdAddr)
        }
        tag_rdata_out := tag_rdata_reg

        when (tag_wr) {
          tag_array(tagWrAddr) := tag
        }
      }
    }
  }
  s1_invalidTag_reg := Reg(next=refill_doneP_reg && io.validEarly)

  for (i <- 0 until nWays) {
    // validate the entries.
    val vb_array = Reg(init=Bits(0, nSets/NUM_PUS))
    vb_array.suggestName("vb_array")

    when (((refill_doneP_reg && !invalidated_reg) || tagClearP_reg) && (repl_way === UInt(i))) {
      vb_array := vb_array.bitSet(refill_addrSh(untagBitsX-1,blockOffBits), (refill_doneP_reg && !invalidated_reg))
    }
    when (io.invalidate) {
      vb_array := Bits(0)
    }
    s1_tag_hit_reg(i) := vb_array(idxSh(untagBitsX-1,blockOffBits)).asBool

    // Select tag out using the bits output from the TLB.
    val allTags  = (0 until NUM_SUB_TAG_MEM).map(k => tag_rdata_out(i*NUM_SUB_TAG_MEM + k))
    val tag_out  = allTags(idxSh(untagBitsX-1, pgIdxBitsX))

    val tagBitsEq_reg    = Reg(init=Vec.fill((tagBits+1)/2) {Bool(false)})
    for (i <- 0 until ((tagBits+1)/2)) {
      tagBitsEq_reg(i) := (tag_out(scala.math.min(tagBits-1,((i+1)*2)-1),i*2) === idxSh(untagBitsX+scala.math.min(tagBits-1,((i+1)*2)-1),untagBitsX+i*2))
    }

    s1_tag_match(i) := tagBitsEq_reg.reduce(_&&_) & s1_tag_hit_reg(i)
  }
  s1_tag_hit     := s1_tag_match.asUInt()
  s1_any_tag_hit := (s1_tag_hit =/= UInt(0))

  when (io.invalidate) {
    invalidated_reg := Bool(true)
  }

  // De-assert wen_block_reg when the memory is available to accept the write transaction.
  wen_block_reg     := Bool(false)
  refill_doneP_reg  := Bool(false)
  for (i <- 0 until NumBanks) {
    when(wrMemSel_reg(i) && wen_block_reg && memBusy_reg(i)) {
      // If the bank is busy this cycle, the write will happen next cycle.
      when(refill_doneD1_reg) {
        refill_doneP_reg := Bool(true)
        state_reg            := s_ready
      }

      // Extends the write request.
      wen_block_reg := Bool(true)
    }
  }

  val wenF = (refill_cnt(log2Up(CACHE_LINE_LEN/rowBits) - 1, 0) === (CACHE_LINE_LEN/rowBits - 1))

  // Note: here we assume that WR has a higher priority than the RD.
  tagClearP_reg  := Bool(false)
  when(refill_one_beat) {
    when(wenF) {
      wen_block_reg     := Bool(true)

      val fullAddr       = Cat(refill_addrSh(untagBitsX-1,blockOffBits), refill_cnt(log2Up(outer.icacheParams.blockBytes * 8 / rowBits) - 1, log2Up(CACHE_LINE_LEN/rowBits)))
      wrAddr_reg        := Cat(fullAddr(fullAddr.getWidth - 1, log2Up(NumBanks)), repl_way)
      for (i <- 0 until NumBanks) {
        val bankSel = (fullAddr(log2Up(NumBanks)-1,0) === UInt(i))
        when(bankSel) {
          dataConc_reg(i) := Cat(tl_d_data, dataConcC_reg)

          when(refill_done) {
            // Check if we can write smoothly in the next clock cycle.
            when(banks(i).n_ceb) {
              refill_doneP_reg := Bool(true)
              state_reg            := s_ready
            }
          }
        }

        wrMemSel_reg(i) := bankSel
      }
    }
    .otherwise {
      if(rowBits < 128) {
        dataConcC_reg     := Cat(tl_d_data, dataConcC_reg(CACHE_LINE_LEN-rowBits-1, rowBits))
      }
      else {
        dataConcC_reg     :=  tl_d_data
      }
    }
    tagClearP_reg     := wenF && !refill_done
    refill_doneD1_reg := refill_done
  }

  // Find the full read address after concatenating the way ID.
  val s1_raddr   = Cat(s1_raddrF(s1_raddrF.getWidth - 1, log2Up(NumBanks)), EncTreeO(s1_tag_hit))

  for (i <- 0 until NumBanks) {
    // Note the read happens even when miss is detected, that is done to relax the timing.
    val ren        = Reg(next=(io.req.valid && (idxSh(log2Up(NumBanks) + CACHE_LINE_LOG2 - 1, CACHE_LINE_LOG2) === UInt(i))), init=Bool(false))

    // Generate the write enable signal.
    val wen        = wrMemSel_reg(i) && wen_block_reg

    banks(i).addr   := Mux(wen, wrAddr_reg, s1_raddr)
    banks(i).n_wr   := !wen
    banks(i).n_ceb  := !(wen || ren) || memBusy_reg(i) || s1_invalidTag_reg
    banks(i).wrData := dataConc_reg(i)
    s3_dout(i)      := banks(i).rdData

    // Track if the banks are busy or free to accept new trans.
    // Only one transaction can be accepted every two cycles.
    memBusy_reg(i)  := (wen || ren) && !memBusy_reg(i)

    // Check if read.
    rdEna(i)        := ren && !wen
  }

  for(ips <- 0 until NUM_PUS) {
    // Delayed version for the valid for each pipe.
    s1_valid_reg(ips) := io.req.valid && (io.req.bits.pipeId === UInt(ips))

    if(NUM_PUS > 1) {
      // No two successive valid.
      val s2_valid_reg = Reg(next=s1_valid_reg(ips), init=Bool(false))
      assert(!s1_valid_reg(ips) || !s2_valid_reg)
    }

    // Select bank coded one-hot.
    if(NUM_PUS == 1) {
      // This can be changed every cycle.
      bankSelMultiC_reg(ips) := UIntToOH(Reg(next=s1_raddrF(log2Up(NumBanks) - 1, 0)))
    }
    else {
      when(s1_valid_reg(ips)) {
        // This should not change for two cycles.
        // Multi-cycle constraint is applied on this signal.
        bankSelMultiC_reg(ips) := UIntToOH(s1_raddrF(log2Up(NumBanks) - 1, 0))
      }
    }

    // output signals
    val data = OrTree((0 until s3_dout.size).map(i => s3_dout(i) & Fill(s3_dout(i).getWidth, bankSelMultiC_reg(ips)(i))))
    for (j <- 0 until CACHE_LINE_LEN / INSTR_LEN) {
      io.resp.bits.instrMemResp(ips)(j) := data((j + 1) * INSTR_LEN - 1, j * INSTR_LEN)
    }
  }

  // Validate the output.
  io.resp.valid       := Reg(next=io.earlyReadValid, init=Bool(false))

  // Simple miss handler
  // Connect memory bus.
  tl_out.a.valid := (state_reg === s_request) //s_request triggers this outgoing tilelink message (s_request only remains until msg accepted)
  tl_out.a.bits  := edge_out.Get( //edge_out is the tilelink connection, "Get" is a particular message that goes out on tilelink
                    fromSource = UInt(0),
                    toAddress = (refill_addr_reg >> blockOffBits) << blockOffBits,
                    lgSize = lgCacheBlockBytes)._2
  tl_out.b.ready := Bool(true)
  tl_out.c.valid := Bool(false)
  tl_out.e.valid := Bool(false)

  // control state_reg machine
  switch (state_reg) {
    is (s_ready) {
      when (s1_miss) { state_reg := s_request } //here is where the one-cycle request state_reg gets set, at end of cycle that s1_miss is asserted
      //this state_reg triggers Get message to go out on tilelink connection.  It remains in this state_reg only until Get msg accepted on tilelink
      invalidated_reg := Bool(false)
    }
    is (s_request) {
      when (tl_out.a.fire()) { state_reg := s_refill_wait } //remain in s_request until Get msg accepted on tilelink
    }
    //is (s_refill_wait) {
    //  when (io.mem.grant.valid) { state_reg := s_refill }
    //}
  }
  io.aFirePerf := tl_out.a.fire()
  io.dFirePerf := tl_out.d.fire()
}

