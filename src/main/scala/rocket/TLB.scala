// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import chisel3.internal.sourceinfo.SourceInfo

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.diplomacy.RegionType
import freechips.rocketchip.tile.{XLen, CoreModule, CoreBundle}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import freechips.rocketchip.devices.debug.DebugModuleKey

import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants.Util._


case object PgLevels extends Field[Int](2)
case object ASIdBits extends Field[Int](0)

class SFenceReq(implicit p: Parameters) extends CoreBundle()(p) {
  val rs1    = Bool()
  val rs2    = Bool()
  val addr   = UInt(width = vaddrBits)
  val asid   = UInt(width = asIdBits max 1) // TODO zero-width
  val ctxtId = UInt(width = CTXT_ID_LEN)
}

class TLBReq(nServedCtxt: Int, lgMaxSize: Int)(implicit p: Parameters) extends CoreBundle()(p) {
  val vaddr       = UInt(width = vaddrBitsExtended)
  val vaddrCtxt   = Vec(nServedCtxt, UInt(width = vaddrBits-pgIdxBits)) // A registered version for vaddr used for each CTXT.
  val passthrough = Bool()
  val isReal      = Bool()
  val size        = UInt(width = log2Ceil(lgMaxSize + 1))
  val cmd         = Bits(width = M_SZ)
  val ctxtId      = UInt(width = CTXT_ID_LEN)

  override def cloneType = new TLBReq(nServedCtxt, lgMaxSize).asInstanceOf[this.type]
}

class TLBExceptions extends Bundle {
  val ld = Bool()
  val st = Bool()
  val inst = Bool()
}

class TLBResp(implicit p: Parameters) extends CoreBundle()(p) {
  // lookup responses
  val miss = Bool()
  val paddr = UInt(width = paddrBits)
  val pf = new TLBExceptions
  val ae = new TLBExceptions
  val ma = new TLBExceptions
  val cacheable = Bool()
  val must_alloc = Bool()
  val prefetchable = Bool()
}

case class TLBConfig(
    nEntries: Int,
    seqSize: Int = 4,
    nSuperpageEntries: Int = 4,
    nServedCtxt: Int = 1)

class TLB(instruction: Boolean, lgMaxSize: Int, cfg: TLBConfig)(implicit edge: TLEdgeOut, p: Parameters) extends CoreModule()(p) {
  val io = new Bundle {
    val validEarly   = Bool(INPUT)
    val ctxtIdEarly  = UInt(INPUT, CTXT_ID_LEN)
    val req          = Decoupled(new TLBReq(cfg.nServedCtxt, lgMaxSize)).flip
    val respD1       = new TLBResp().asOutput
    val sfence       = Valid(new SFenceReq).asInput
    val ptw          = new TLBPTWIO
    val kill         = Bool(INPUT) // suppress a TLB refill, one cycle after a miss
  }

  class EntryData extends Bundle {
    val u                    = Bool()
    val ae                   = Bool()
    val sw                   = Bool()
    val sx                   = Bool()
    val sr                   = Bool()
    val pw                   = Bool()
    val px                   = Bool()
    val pr                   = Bool()
    val pal                  = Bool() // AMO logical
    val paa                  = Bool() // AMO arithmetic
    val eff                  = Bool() // get/put effects
    val c                    = Bool()

    // Do not change the order of the following signals.
    val fragmented_superpage = Bool()
    val g                    = Bool()
  }

  // Require on the virtual address length.
  require(vaddrBitsExtended == ADDR_LEN)

  // Main registers.
  val s_ready :: s_request :: s_wait :: Nil = Enum(UInt(), 3)
  val state                  = Reg(init=s_ready)
  val r_ctxtId               = Reg(UInt(width=CTXT_ID_LEN))
  val justReady_reg          = Reg(init=Bool(false))
  val justReadyD1_reg        = Reg(init=Bool(false))
  val justReadyD2_reg        = Reg(init=Bool(false))
  val justReadyD3_reg        = Reg(init=Bool(false))
  val protectCtxtAccess_reg  = Reg(init=Bool(false))

  // Note: it is assumed to kept the same for one cycle after the ready has been declared.
  val r_refill_tag   = Reg(UInt(width=vpnBits))
  val r_seq_hit      = Reg(init=Bool(false))

  // Virtual address to be translated.
  val vpn  = io.req.bits.vaddr(vaddrBits-1, pgIdxBits)

  val vpnD1_reg     = Reg(UInt(width=vpnBits))
  val ctxtD1_reg    = Reg(UInt(width=CTXT_ID_LEN))
  val protCtxt_reg  = Reg(UInt(width=CTXT_ID_LEN))

  // Ctxt ID.
  val ctxtIdEarlyX   = Wire(UInt(width=log2Up(cfg.nServedCtxt)))
  val ctxtIdX        = Wire(UInt(width=log2Up(cfg.nServedCtxt)))
  val protCtxtX      = Wire(UInt(width=log2Up(cfg.nServedCtxt)))
  val ctxtD1X        = Wire(UInt(width=log2Up(cfg.nServedCtxt)))
  if(cfg.nServedCtxt == 1) {
    ctxtIdEarlyX := UInt(0)
    ctxtIdX      := UInt(0)
    protCtxtX    := UInt(0)
    ctxtD1X      := UInt(0)
  }
  else {
    ctxtIdEarlyX := io.ctxtIdEarly(log2Up(cfg.nServedCtxt)-1, 0)
    ctxtIdX      := io.req.bits.ctxtId(log2Up(cfg.nServedCtxt)-1, 0)
    protCtxtX    := protCtxt_reg(log2Up(cfg.nServedCtxt)-1, 0)
    ctxtD1X      := ctxtD1_reg(log2Up(cfg.nServedCtxt)-1, 0)
  }

  val isCtxtIdSM_reg  = Reg(init=Vec.fill(cfg.nServedCtxt) {Bool(false)})
  val isCtxtId_reg    = Reg(init=Vec.fill(cfg.nServedCtxt) {Bool(false)})
  when(io.req.valid) {
    for (i <- 0 until cfg.nServedCtxt) {
      if(cfg.nServedCtxt == 1) {
        isCtxtId_reg(i) := Bool(true)
      }
      else {
        isCtxtId_reg(i) := ctxtIdX === UInt(i)
      }
    }
  }

  // Delayed PTW response.
  val ptwValid_reg    = Reg(next=io.ptw.resp.valid, init=Bool(false))

  // NOTE: it is assumed that no successive valid can come from the PTW, so we do not need to delay the PTW resp.
  val ptwResp_reg     = RegEnable(io.ptw.resp.bits, io.ptw.resp.valid)

  // A PTW valid for each served ROW.
  val ptwValidSM_reg    = Reg(init=UInt(0, cfg.nServedCtxt))
  ptwValidSM_reg       := Mux(io.ptw.resp.valid, isCtxtIdSM_reg.asUInt(), UInt(0))

  // Delay one cycle to compensate the register of the flags.
  val ptwValidSMD1_reg  = Reg(next=ptwValidSM_reg)

  // New entry to be inserted.
  val newEntry        = Wire(new EntryData)
  val newEntryUD1_reg = Reg(next=newEntry.asUInt)

  class Entry(val seqSize: Int, val superpage: Boolean, val superpageOnly: Boolean, val ctxtIdF: Int) extends Bundle {
    require(seqSize == 1 || !superpage)
    require(!superpageOnly || superpage)

    val level       = UInt(width = log2Ceil(pgLevels))
    val tag         = UInt(width = vpnBits)
    val flags       = UInt(width = new EntryData().getWidth)
    val ppnSeq      = Vec(seqSize, UInt(width = ppnBits))
    val valid       = Vec(seqSize, Bool())

    def seqIdx(vpnT: UInt)    = vpnT.extract(seqSize.log2-1, 0)
    def vpnCtxtCurr()         = if(ctxtIdF<0) vpn else io.req.bits.vaddrCtxt(ctxtIdF)
    def getData()             = flags.asTypeOf(new EntryData)
    def seqTagMatch()         = (tag(vpnBits - 1, seqSize.log2) === vpnCtxtCurr()(vpnBits - 1, seqSize.log2))
    def anyValid()            = valid.orR
    def isValid()             = MuxTree(seqIdx(vpnCtxtCurr()), valid)
    def seqHit()              = anyValid() && seqTagMatch()

    def hit() = {
      if (superpage && usingSupervisor) {
        var tagMatch = valid.head
        for (j <- 0 until pgLevels) {
          val base   = vpnBits - (j + 1) * pgLevelBits
          val ignore = level < j || superpageOnly && j == pgLevels - 1
          tagMatch   = tagMatch && (ignore || tag(base + pgLevelBits - 1, base) === vpnCtxtCurr()(base + pgLevelBits - 1, base))
        }
        tagMatch
      }
      else {
        isValid() && seqTagMatch()
      }
    }

    def ppn() = {
      if (superpage && usingSupervisor) {
        val dataPPN = ppnSeq(0)
        var res = dataPPN >> pgLevelBits*(pgLevels - 1)
        for (j <- 1 until pgLevels) {
          val ignore = level < j || superpageOnly && j == pgLevels - 1
          res = Cat(res, (Mux(ignore, vpnCtxtCurr(), 0.U) | dataPPN)(vpnBits - j*pgLevelBits - 1, vpnBits - (j + 1)*pgLevelBits))
        }
        res
      }
      else {
        val dataPPN = MuxTree(seqIdx(vpnCtxtCurr()), ppnSeq)
        dataPPN
      }
    }

    def insert() {
      this.tag         := r_refill_tag
      if((superpage==false) && (superpageOnly==false)) {
        this.level  := UInt(0)
      }
      else {
        this.level  := ptwResp_reg.level.extract(log2Ceil(pgLevels - superpageOnly.toInt)-1, 0)
      }

      if(seqSize > 0) {
        // Clear all valid for sequenced entries if we have to change the tags.
        // We clear all valid in the case that the new entry has different set, which is highly unlikely. This is done as we have only one copy of these flags.
        when (!r_seq_hit || (flags =/= newEntryUD1_reg)) {
          valid.foreach(_ := false)
        }
      }

      val idx       = seqIdx(r_refill_tag)
      valid(idx)   := true
      ppnSeq(idx)  := pte.ppn
      flags        := newEntryUD1_reg
    }

    def invalidateCtrl(isVpnDepD1_reg: Bool, isNGD1_reg: Bool, isInvD1_reg: Bool) {
      val isVpnX   = isVpnDepD1_reg && Reg(next=if(superpage) hit() else seqTagMatch())
      for (i <- 0 until seqSize) {
        val vpnValid = if(superpage) Bool(true) else Reg(next=seqIdx(vpnCtxtCurr()) === UInt(i))
        when(isCtxtId_reg(if(ctxtIdF<0) 0 else ctxtIdF) && (isInvD1_reg || (isNGD1_reg && !flags(0)) || (isVpnX && vpnValid))) { // data(0) is "g" flag.
          valid(i) := false
        }
      }

      if(!superpage) {
        // Assume that fragmented_superpage is always false.
        if(false) {
          // For fragmented superpage mappings, we assume the worst (largest)
          // case, and zap entries whose most-significant VPNs match
          when(isCtxtId_reg(if(ctxtIdF<0) 0 else ctxtIdF) && Reg(next=(((tag ^ vpnCtxtCurr()) >> (pgLevelBits * (pgLevels - 1))) === 0))) {
            for (i <- 0 until seqSize) {
              when(flags(1)) { // Checking for fragmented_superpage.
                valid(i) := false
              }
            }
          }
        }
      }
    }

    def invalidateF() {
      valid.foreach(_ := false)
    }
  }

  val pageGranularityPMPs    = pmpGranularity >= (1 << pgIdxBits)
  val seq_entries            = 0 to cfg.nServedCtxt - 1 map {x => Reg(init=Vec.fill(cfg.nEntries / cfg.seqSize) {new Entry(cfg.seqSize, false, false, x).fromBits(0)})}
  val superpage_entries      = 0 to cfg.nServedCtxt - 1 map {x => Reg(init=Vec.fill(cfg.nSuperpageEntries) {new Entry(1, true, true, x).fromBits(0)})}
  val special_entry          = 0 to cfg.nServedCtxt - 1 map {x => if(!pageGranularityPMPs && (nPMPs > 0)) Reg(init=Vec.fill(1) {new Entry(1, true, false, x).fromBits(0)}) else Reg(Vec(0, new Entry(1, true, false, x)))}

  val seq_entriesS_reg       = Reg(Vec(cfg.nEntries / cfg.seqSize, new Entry(cfg.seqSize, false, false, -1)))
  val superpage_entriesS_reg = Reg(Vec(cfg.nSuperpageEntries, new Entry(1, true, true, -1)))
  val special_entryS_reg     = if(!pageGranularityPMPs && (nPMPs > 0)) Reg(Vec(1, new Entry(1, true, false, -1))) else Reg(Vec(0, new Entry(1, true, false, -1)))

  // Replacement mechanism.
  val seq_plru       = 0 to cfg.nServedCtxt - 1 map {x => new PseudoLRU(seq_entries(0).size)}
  val superpage_plru = 0 to cfg.nServedCtxt - 1 map {x => new PseudoLRU(superpage_entries(0).size)}

  val r_superpage_repl_addr = Reg(UInt(log2Ceil(superpage_entries(0).size).W))
  val r_seq_repl_addr       = Reg(UInt(log2Ceil(seq_entries(0).size).W))

  // Connect the ctxtId to obtain the corresponding status.
  io.ptw.ctxtId      := io.ctxtIdEarly
  val ptwStatus_reg   = RegEnable(io.ptw.status, io.validEarly)
  val ptwPtbr_reg     = RegEnable(io.ptw.ptbr.mode(io.ptw.ptbr.mode.getWidth-1), io.validEarly)

  val priv         = if (instruction) ptwStatus_reg.prv else ptwStatus_reg.dprv
  val priv_s       = priv(0)
  val priv_uses_vm = priv <= PRV.S
  val vm_enabled   = Bool(usingSupervisor) && ptwPtbr_reg && priv_uses_vm && !io.req.bits.passthrough

  // Select the entries.
  when(io.validEarly) {
    seq_entriesS_reg       := MuxTree(ctxtIdEarlyX, seq_entries)
    superpage_entriesS_reg := MuxTree(ctxtIdEarlyX, superpage_entries)
    special_entryS_reg     := MuxTree(ctxtIdEarlyX, special_entry)
  }

  // Make sure that all supported address ranges are one or more pages.
  // This is needed to verify that:
  //   1- we can force the lowest bits to zero for mpu_physaddrM.
  //   2- Make sure that maskHack and alignmentHack have the correct assumptions.
  for(i <- 0 until edge.manager.managers.length) {
    for(k <- 0 until edge.manager.managers(i).address.length) {
      var mask = edge.manager.managers(i).address(k).mask
      mask = mask | ((NUM_L2BANKS-1) << lgCacheBlockBytes) // Avoid considering fragmented ranges over the TLs.
      require((edge.manager.managers(i).address(k).maskHack & ((1<<pgIdxBits)-1))  == ((1<<pgIdxBits)-1))
      //require((mask                                         & ((1<<pgIdxBits)-1))  == ((1<<pgIdxBits)-1))
    }
  }

  // share a single physical memory attribute checker (unshare if critical path)
  val mpu_ppn       = if(special_entry(0).size > 0) Mux(vm_enabled && special_entry(0).nonEmpty, special_entry(ctxtIdX)(0).ppn(), io.req.bits.vaddr >> pgIdxBits) else (io.req.bits.vaddr >> pgIdxBits)
  val mpu_physaddr  = Cat(mpu_ppn, io.req.bits.vaddr(pgIdxBits-1, 0))
  val mpu_physaddrM = Cat(mpu_ppn, UInt(0, pgIdxBits)) // We remove the last pgIdxBits. This is done as the addresses supported by the edge can be fragmented. That also requires each range to cover more than a page.
  val mpu_priv      = Mux[UInt](Bool(usingVM) && (io.req.bits.passthrough /* PTW */), PRV.S, Cat(ptwStatus_reg.debug, priv))

  val pmpR = Wire(Bool())
  val pmpW = Wire(Bool())
  val pmpX = Wire(Bool())
  if(nPMPs > 0) {
    val pmp          = Module(new PMPChecker(lgMaxSize))
    val ptwPmp_reg   = RegEnable(io.ptw.pmp, io.validEarly)
    pmp.io.addr     := mpu_physaddr
    pmp.io.size     := io.req.bits.size
    pmp.io.pmp      := (ptwPmp_reg: Seq[PMP])
    pmp.io.prv      := mpu_priv
    pmpR            := pmp.io.r
    pmpW            := pmp.io.w
    pmpX            := pmp.io.x
  }
  else {
    pmpR := Bool(true)
    pmpW := Bool(true)
    pmpX := Bool(true)
  }

  val legal_address     = OrTree(edge.manager.findSafe(mpu_physaddrM))
  def fastCheck(member: TLManagerParameters => Boolean) =
    legal_address && edge.manager.fastProperty(mpu_physaddrM, member, (b:Boolean) => Bool(b))
  val cacheable   = fastCheck(_.supportsAcquireT) && (instruction || !usingDataScratchpad)
  val homogeneous = TLBPageLookup(edge.manager.managers, xLen, p(CacheBlockBytes), BigInt(1) << pgIdxBits)(mpu_physaddrM).homogeneous
  val deny_access_to_debug = mpu_priv <= PRV.M && p(DebugModuleKey).map(dmp => dmp.address.contains(mpu_physaddr)).getOrElse(false)
  val prot_r      = fastCheck(_.supportsGet) && !deny_access_to_debug && pmpR
  val prot_w      = fastCheck(_.supportsPutFull) && !deny_access_to_debug && pmpW
  val prot_x      = fastCheck(_.executable) && !deny_access_to_debug && pmpX
  val prot_al     = fastCheck(_.supportsLogical)
  val prot_aa     = fastCheck(_.supportsArithmetic)
  val prot_eff    = fastCheck(Seq(RegionType.PUT_EFFECTS, RegionType.GET_EFFECTS) contains _.regionType)

  // Collect seq and super-page hits for each CTXT.
  val seq_all_tagMatch = seq_entriesS_reg.map(_.seqTagMatch()).asUInt
  val seq_all_anyValid = seq_entriesS_reg.map(_.anyValid()).asUInt
  val seq_all_isValid  = seq_entriesS_reg.map(_.isValid()).asUInt

  val seq_all_hits       = seq_all_tagMatch & seq_all_isValid
  val superpage_all_hits = superpage_entriesS_reg.map(_.hit()).asUInt
  val special_all_hits   = if(special_entryS_reg.size > 0) special_entryS_reg.map(_.hit()).asUInt else UInt(width=0)

  // Collect all hits including the special ones
  val all_hits           = if(special_entryS_reg.size > 0) Cat(special_all_hits, superpage_all_hits, seq_all_hits) else Cat(superpage_all_hits, seq_all_hits)

  // A mux to select based on the CTXT ID.
  val real_hits          = Mux(vm_enabled, all_hits, UInt(0, all_hits.getWidth))

  val seq_hitsC          = real_hits(seq_entriesS_reg.size-1, 0)
  val nonseq_hitsC       = real_hits(real_hits.getWidth-1, seq_entriesS_reg.size)
  val superpage_hits     = real_hits(seq_entriesS_reg.size+superpage_entriesS_reg.size-1, seq_entriesS_reg.size)

  // Append the non-vm mode hit flag.
  val hits    = Cat(!vm_enabled, real_hits)

  // Collect the selected PPN for each CTXT.
  // Only one should be active.
  val allPPN       = (seq_entriesS_reg.map(_.ppn()) ++ superpage_entriesS_reg.map(_.ppn()) ++ special_entryS_reg.map(_.ppn()))

  val allPPND1_reg     = Reg(Vec(allPPN.size+1, UInt(width=ppnBits)))
  val vm_enabledD1_reg = Reg(init=Bool(false))
  val pgIdxBitsD1_reg  = Reg(init=UInt(0, pgIdxBits))
  when(io.req.valid) {
    allPPND1_reg     := (0 until allPPN.size+1).map(i => if(i==allPPN.size) Mux(!vm_enabled, vpn(ppnBits-1, 0), UInt(0, ppnBits)) else (allPPN(i) & Fill(allPPN(i).getWidth, all_hits(i) && vm_enabled)))
    vm_enabledD1_reg := vm_enabled
    pgIdxBitsD1_reg  := io.req.bits.vaddr(pgIdxBits-1, 0)
    vpnD1_reg        := vpn
    ctxtD1_reg       := io.req.bits.ctxtId
  }
  io.respD1.paddr := Cat(OrTree(allPPND1_reg), pgIdxBitsD1_reg)

  // Find the flags of the incoming entry.
  val mpu_physaddrX    = Cat(ptwResp_reg.pte.ppn(ppnBits-1, 0), UInt(0, pgIdxBits))
  val legal_addressX   = OrTree(edge.manager.findSafe(mpu_physaddrX))
  def fastCheckX(member: TLManagerParameters => Boolean) =
    legal_addressX && edge.manager.fastProperty(mpu_physaddrX, member, (b:Boolean) => Bool(b))
  val cacheableX  = fastCheckX(_.supportsAcquireT) && (instruction || !usingDataScratchpad)
  val prot_rX     = fastCheckX(_.supportsGet) && ptwResp_reg.r
  val prot_wX     = fastCheckX(_.supportsPutFull) && ptwResp_reg.w
  val prot_xX     = fastCheckX(_.executable) && ptwResp_reg.x
  val prot_alX    = fastCheckX(_.supportsLogical)
  val prot_aaX    = fastCheckX(_.supportsArithmetic)
  val prot_effX   = fastCheckX(Seq(RegionType.PUT_EFFECTS, RegionType.GET_EFFECTS) contains _.regionType)

  // Prepare the information of the entry to be inserted.
  val pte           = ptwResp_reg.pte
  newEntry.c       := cacheableX
  newEntry.u       := pte.u
  newEntry.g       := pte.g && pte.v
  newEntry.ae      := ptwResp_reg.ae
  newEntry.sr      := pte.sr()
  newEntry.sw      := pte.sw()
  newEntry.sx      := pte.sx()
  newEntry.pr      := prot_rX
  newEntry.pw      := prot_wX
  newEntry.px      := prot_xX
  newEntry.pal     := prot_alX
  newEntry.paa     := prot_aaX
  newEntry.eff     := prot_effX
  newEntry.fragmented_superpage := ptwResp_reg.fragmented_superpage
  assert(!ptwResp_reg.fragmented_superpage || !(Bool(usingSupervisor) && ptwValid_reg))

  val specialValid = ((special_entry(0).size > 0) && !ptwResp_reg.homogeneous)
  val superValid   = !specialValid && (ptwResp_reg.level < pgLevels-1)
  val seqValid     = !superValid

  for (i <- 0 until cfg.nServedCtxt) {
    when(Bool(usingSupervisor) && ptwValidSMD1_reg(i)){
      if(special_entry(0).size > 0) {
        when(specialValid){
          special_entry(i)(0).insert()
        }
      }

      when(superValid) {
        for (k <- 0 until superpage_entries(0).size) {
          when(r_superpage_repl_addr === UInt(k)) {
            superpage_entries(i)(k).insert()
          }
        }

        superpage_plru(i).access(r_superpage_repl_addr) // Mark the new written entry to not be replaced immediately.
      }

      when(seqValid) {
        for (k <- 0 until seq_entries(0).size) {
          when(r_seq_repl_addr === UInt(k)) {
            seq_entries(i)(k).insert()
          }
        }

        seq_plru(i).access(r_seq_repl_addr) // Mark the new written entry to not be replaced immediately.
      }
    }
  }

  // Collect the flags for each ctxt.
  val NormalEntriesSize = seq_entries(0).size + superpage_entries(0).size
  val AllEntriesSize    = NormalEntriesSize + special_entry(0).size
  class EntryDataAll extends Bundle {
    val u                    = UInt(width=AllEntriesSize)
    val ae                   = UInt(width=AllEntriesSize)
    val sw                   = UInt(width=AllEntriesSize)
    val sx                   = UInt(width=AllEntriesSize)
    val sr                   = UInt(width=AllEntriesSize)
    val pw                   = UInt(width=NormalEntriesSize)
    val px                   = UInt(width=NormalEntriesSize)
    val pr                   = UInt(width=NormalEntriesSize)
    val pal                  = UInt(width=NormalEntriesSize)
    val paa                  = UInt(width=NormalEntriesSize)
    val eff                  = UInt(width=NormalEntriesSize)
    val c                    = UInt(width=NormalEntriesSize)
  }

  val selData = {
    val allData = Wire(Vec(cfg.nServedCtxt, new EntryDataAll))
    for(i <- 0 until cfg.nServedCtxt) {
       val normal_entries  = seq_entries(i).map(_.getData()) ++ superpage_entries(i).map(_.getData())
       val entries         = normal_entries ++ special_entry(i).map(_.getData())
       allData(i).u       := entries.map(_.u   ).asUInt
       allData(i).ae      := entries.map(_.ae  ).asUInt
       allData(i).sw      := entries.map(_.sw  ).asUInt
       allData(i).sx      := entries.map(_.sx  ).asUInt
       allData(i).sr      := entries.map(_.sr  ).asUInt
       allData(i).pw      := normal_entries.map(_.pw  ).asUInt
       allData(i).px      := normal_entries.map(_.px  ).asUInt
       allData(i).pr      := normal_entries.map(_.pr  ).asUInt
       allData(i).pal     := normal_entries.map(_.pal ).asUInt
       allData(i).paa     := normal_entries.map(_.paa ).asUInt
       allData(i).eff     := normal_entries.map(_.eff ).asUInt
       allData(i).c       := normal_entries.map(_.c   ).asUInt
    }
    RegEnable(MuxTree(ctxtIdEarlyX, allData), io.validEarly)
  }

  val nPhysicalEntries = 1 + special_entry(0).size
  val ptw_ae_array = Cat(false.B, selData.ae)
  val priv_rw_ok   = Mux(!priv_s || ptwStatus_reg.sum, selData.u, 0.U) | Mux(priv_s, ~selData.u, 0.U)
  val priv_x_ok    = Mux(priv_s, ~selData.u, selData.u)
  val r_array      = priv_rw_ok & (selData.sr | Mux(ptwStatus_reg.mxr, selData.sx, UInt(0)))
  val w_array      = priv_rw_ok & selData.sw
  val x_array      = priv_x_ok & selData.sx
  val pr_array     = Cat(Fill(nPhysicalEntries, prot_r), selData.pr) & ~ptw_ae_array
  val pw_array     = Cat(Fill(nPhysicalEntries, prot_w), selData.pw) & ~ptw_ae_array
  val px_array     = Cat(Fill(nPhysicalEntries, prot_x), selData.px) & ~ptw_ae_array
  val eff_array    = Cat(Fill(nPhysicalEntries, prot_eff), selData.eff)
  val c_array      = Cat(Fill(nPhysicalEntries, cacheable), selData.c)
  val paa_array    = Cat(Fill(nPhysicalEntries, prot_aa), selData.paa)
  val pal_array    = Cat(Fill(nPhysicalEntries, prot_al), selData.pal)
  val paa_array_if_cached = paa_array | Mux(usingAtomicsInCache, c_array, 0.U)
  val pal_array_if_cached = pal_array | Mux(usingAtomicsInCache, c_array, 0.U)
  val prefetchable_array = Cat((cacheable && homogeneous) << (nPhysicalEntries-1), selData.c)

  // Command type.
  val cmd_lrsc           = Bool(usingAtomics) && io.req.bits.cmd.isOneOf(M_XLR, M_XSC)
  val cmd_amo_logical    = Bool(usingAtomics) && isAMOLogical(io.req.bits.cmd)
  val cmd_amo_arithmetic = Bool(usingAtomics) && isAMOArithmetic(io.req.bits.cmd)
  val cmd_read           = isRead(io.req.bits.cmd)
  val cmd_write          = isWrite(io.req.bits.cmd)
  val cmd_write_perms = cmd_write ||
    io.req.bits.cmd.isOneOf(M_FLUSH_ALL, M_WOK) // not a write, but needs write permissions

  val misaligned = (io.req.bits.vaddr & (UIntToOH(io.req.bits.size) - 1)).orR
  val bad_va = if (!usingVM || (minPgLevels == pgLevels && vaddrBits == vaddrBitsExtended)) false.B else vm_enabled && {
    val nPgLevelChoices = pgLevels - minPgLevels + 1
    val minVAddrBits = pgIdxBits + minPgLevels * pgLevelBits
    (for (i <- 0 until nPgLevelChoices) yield {
      val mask = ((BigInt(1) << vaddrBitsExtended) - (BigInt(1) << (minVAddrBits + i * pgLevelBits - 1))).U
      val maskedVAddr = io.req.bits.vaddr & mask
      io.ptw.ptbr.additionalPgLevels === i && !(maskedVAddr === 0 || maskedVAddr === mask)
    }).orR
  }

  val lrscAllowed = Mux(Bool(usingDataScratchpad || usingAtomicsOnlyForIO), 0.U, c_array)
  val ae_array =
    Mux(misaligned, eff_array, 0.U) |
    Mux(cmd_lrsc, ~lrscAllowed, 0.U)
  val ae_st_array =
    Mux(cmd_write_perms, ae_array | ~pw_array, 0.U) |
    Mux(cmd_amo_logical, ~pal_array_if_cached, 0.U) |
    Mux(cmd_amo_arithmetic, ~paa_array_if_cached, 0.U)
  val must_alloc_array =
    Mux(cmd_amo_logical, ~paa_array, 0.U) |
    Mux(cmd_amo_arithmetic, ~pal_array, 0.U) |
    Mux(cmd_lrsc, ~0.U(pal_array.getWidth.W), 0.U)

 val missEna      = !io.sfence.valid && (io.req.bits.cmd =/= M_FLUSH_ALL)

  // Delay.
  val hitsD1_reg            = RegEnable(hits, io.req.valid)
  val all_hitsD1_reg        = RegEnable(all_hits, io.req.valid)
  val bad_vaD1_reg          = RegEnable(bad_va, io.req.valid)
  val selDataD1_reg         = RegEnable(selData, io.req.valid)
  val cmd_readD1_reg        = RegEnable(cmd_read, io.req.valid)
  val cmd_writeD1_reg       = RegEnable(cmd_write, io.req.valid)
  val cmd_write_permsD1_reg = RegEnable(cmd_write_perms, io.req.valid)
  val missEnaD1_reg         = RegEnable(missEna, io.req.valid)
  val r_arrayD1_reg         = RegEnable(r_array, io.req.valid)
  val w_arrayD1_reg         = RegEnable(w_array, io.req.valid)
  val x_arrayD1_reg         = RegEnable(x_array, io.req.valid)
  val ae_arrayD1_reg        = RegEnable(ae_array, io.req.valid)
  val pr_arrayD1_reg        = RegEnable(pr_array, io.req.valid)
  val c_arrayD1_reg         = RegEnable(c_array, io.req.valid)
  val ae_st_arrayD1_reg     = RegEnable(ae_st_array, io.req.valid)
  val px_arrayD1_reg        = RegEnable(px_array, io.req.valid)
  val misalignedD1_reg      = RegEnable(misaligned, io.req.valid)
  val eff_arrayD1_reg       = RegEnable(eff_array, io.req.valid)
  val seq_hitsCD1_reg       = RegEnable(seq_hitsC, io.req.valid)
  val nonseq_hitsCD1_reg    = RegEnable(nonseq_hitsC, io.req.valid)
  val isRealD1_reg          = Reg(next=io.req.bits.isReal, init=Bool(false))

  val prefetchable_arrayD1_reg = RegEnable(prefetchable_array, io.req.valid)
  val must_alloc_arrayD1_reg   = RegEnable(must_alloc_array, io.req.valid)

  val tlb_seq_hitD1  = seq_hitsCD1_reg.orR
  val tlb_hitD1      = nonseq_hitsCD1_reg.orR || tlb_seq_hitD1
  val forceMissD1_reg= RegEnable(protectCtxtAccess_reg && (protCtxtX === ctxtIdX), io.req.valid)
  val tlb_missD1     = (vm_enabledD1_reg && !bad_vaD1_reg && !tlb_hitD1) || forceMissD1_reg

  // Collect the tag match flags for sequenced entries.
  val seq_anyTagMatchD1_reg  = Reg(UInt(width=seq_entries(0).size))
  if(cfg.seqSize == 1) {
    seq_anyTagMatchD1_reg := seq_hitsC
  }
  else {
    seq_anyTagMatchD1_reg := seq_all_tagMatch & seq_all_anyValid
  }
  val seq_hit_addrD1         = OHToUInt(seq_anyTagMatchD1_reg)
  val seq_hitD1              = seq_anyTagMatchD1_reg.orR

  // Collect information for superpage entries hit.
  val superpage_hitsD1_reg   = Reg(UInt(width=superpage_entries(0).size))
  superpage_hitsD1_reg      := superpage_hits
  val superPageHitD1         = superpage_hitsD1_reg.orR
  val superPageAddrD1        = OHToUInt(superpage_hitsD1_reg)

  // Make sure that we do not update the TLB entries as that may require invoking seq_plru and superpage_plru.
  val enaUpdPlru_reg = Reg(next=io.req.valid && vm_enabled && !io.ptw.resp.valid)
  when(enaUpdPlru_reg) {
    for (i <- 0 until cfg.nServedCtxt) {
      when(isCtxtId_reg(i)) {
        when(seq_hitD1) {
          seq_plru(i).access(seq_hit_addrD1)
        }
        when(superPageHitD1) {
          superpage_plru(i).access(superPageAddrD1)
        }
      }
    }
  }

  // Ready flag.
  io.req.ready := (state === s_ready)

  // Superpages create the possibility that two entries in the TLB may match.
  // This corresponds to a software bug, but we can't return complete garbage;
  // we must return either the old translation or the new translation.  This
  // isn't compatible with the Mux1H approach.  So, flush the TLB and report
  // a miss on duplicate entries.
  // Note: there cannot be a multi-hit within sequenced entries.
  val multipleHitsD1 = PopCountAtLeast(Cat(nonseq_hitsCD1_reg, tlb_seq_hitD1), 2)

  // The response.
  io.respD1.pf.ld        := isRealD1_reg && (bad_vaD1_reg || (vm_enabledD1_reg && (~(r_arrayD1_reg | selDataD1_reg.ae) & all_hitsD1_reg).orR)) && cmd_readD1_reg
  io.respD1.pf.st        := isRealD1_reg && (bad_vaD1_reg || (vm_enabledD1_reg && (~(w_arrayD1_reg | selDataD1_reg.ae) & all_hitsD1_reg).orR)) && cmd_write_permsD1_reg
  io.respD1.pf.inst      := isRealD1_reg && (bad_vaD1_reg || (vm_enabledD1_reg && (~(x_arrayD1_reg | selDataD1_reg.ae) & all_hitsD1_reg).orR)) && missEnaD1_reg
  io.respD1.ae.ld        := isRealD1_reg && ((ae_arrayD1_reg | ~pr_arrayD1_reg) & hitsD1_reg).orR && cmd_readD1_reg
  io.respD1.ae.st        := isRealD1_reg && (ae_st_arrayD1_reg & hitsD1_reg).orR && missEnaD1_reg
  io.respD1.ae.inst      := isRealD1_reg && (~px_arrayD1_reg & hitsD1_reg).orR && missEnaD1_reg

  // Check for misalignment exceptions.
  val maD1                = isRealD1_reg && misalignedD1_reg && (~eff_arrayD1_reg & hitsD1_reg).orR
  io.respD1.ma.ld        := maD1 && cmd_readD1_reg
  io.respD1.ma.st        := maD1 && cmd_writeD1_reg
  io.respD1.ma.inst      := false // this is up to the pipeline to figure out

  io.respD1.cacheable    := isRealD1_reg && (c_arrayD1_reg & hitsD1_reg).orR
  io.respD1.must_alloc   := isRealD1_reg && (must_alloc_arrayD1_reg & hitsD1_reg).orR
  io.respD1.prefetchable := isRealD1_reg && (prefetchable_arrayD1_reg & hitsD1_reg).orR && edge.manager.managers.forall(m => !m.supportsAcquireB || m.supportsHint)
  io.respD1.miss         := isRealD1_reg && (tlb_missD1 || multipleHitsD1) && missEnaD1_reg

  io.ptw.req.valid            := (state === s_request)
  io.ptw.req.bits.valid       := !io.kill
  io.ptw.req.bits.bits.addr   := r_refill_tag
  io.ptw.req.bits.bits.ctxtId := r_ctxtId

  if (usingSupervisor) {
    // We prevent serving a miss from the same CTXT in the next cycle after becoming available, so we are sure that the entries have been updated.
    val missEffD1 = Reg(next=io.req.valid, init=Bool(false)) && tlb_missD1 && missEnaD1_reg && !forceMissD1_reg && isRealD1_reg
    when(missEffD1 && (state === s_ready)) {
      state          := s_request
      r_refill_tag   := vpnD1_reg
      r_ctxtId       := ctxtD1_reg

      // Determine the entry to be replaced.
      val superpage_repl_addr = MuxTree(ctxtD1X, 0 to cfg.nServedCtxt - 1 map {i => replacementEntry(superpage_entries(i), superpage_plru(i).replace)})
      val seq_repl_addr       = MuxTree(ctxtD1X, 0 to cfg.nServedCtxt - 1 map {i => replacementEntry(seq_entries(i), seq_plru(i).replace)})
      r_seq_hit             := seq_hitD1
      r_seq_repl_addr       := Mux(seq_hitD1, seq_hit_addrD1, seq_repl_addr)
      r_superpage_repl_addr := superpage_repl_addr

      for (i <- 0 until cfg.nServedCtxt) {
        // Mark the CTXT we work on.
        if(cfg.nServedCtxt == 1) {
          isCtxtIdSM_reg(i) := Bool(true)
        }
        else {
          isCtxtIdSM_reg(i) := (ctxtD1X === UInt(i))
        }
      }
    }

    when (state === s_request) {
      when (io.ptw.req.ready) { state := s_wait }
      when (io.kill) { state := s_ready }
    }

    justReady_reg := Bool(false)
    when (ptwValid_reg) {
      state         := s_ready
      justReady_reg := Bool(true)
    }
    justReadyD1_reg := justReady_reg
    justReadyD2_reg := justReadyD1_reg
    justReadyD3_reg := justReadyD2_reg

    // We produces a miss for any received request from the same CTXT during inserting the new entry.
    // This is needed since the flags are read one cycle earlier.
    when(io.ptw.resp.valid) {
      protectCtxtAccess_reg := Bool(true)
      protCtxt_reg          := r_ctxtId
    }
    when(justReadyD3_reg) {
      protectCtxtAccess_reg := Bool(false)
    }

    val sfence         = io.req.valid && io.sfence.valid
    val isVpnDepD1_reg = Reg(next=sfence && io.sfence.bits.rs1, init=Bool(false)) // invalidate based on VPN
    val isNGD1_reg     = Reg(next=sfence && !io.sfence.bits.rs1 && io.sfence.bits.rs2, init=Bool(false)) // Invalidate only non-global
    val isInvD1_reg    = multipleHitsD1 || Reg(next=(sfence && !io.sfence.bits.rs1 && !io.sfence.bits.rs2), init=Bool(false)) // Invalidate all seqs for this ctxt.

    for (i <- 0 until cfg.nServedCtxt) {
      seq_entries(i).foreach(_.invalidateCtrl(isVpnDepD1_reg, isNGD1_reg, isInvD1_reg))
      superpage_entries(i).foreach(_.invalidateCtrl(isVpnDepD1_reg, isNGD1_reg, isInvD1_reg))
      special_entry(i).foreach(_.invalidateCtrl(isVpnDepD1_reg, isNGD1_reg, isInvD1_reg))
    }

    if(false) {
      // Clear the entries periodically, used ONLY for testing.
      val cntr_reg         = Reg(init=UInt(0, 8))
      cntr_reg := cntr_reg + UInt(1)
      when (cntr_reg === UInt(0)) {
        seq_entries.foreach(_.foreach(_.invalidateF()))
        superpage_entries.foreach(_.foreach(_.invalidateF()))
        special_entry.foreach(_.foreach(_.invalidateF()))
      }
    }

    ccover(io.ptw.req.fire(), "MISS", "TLB miss")
    ccover(io.ptw.req.valid && !io.ptw.req.ready, "PTW_STALL", "TLB miss, but PTW busy")
    ccover(sfence && !io.sfence.bits.rs1 && !io.sfence.bits.rs2, "SFENCE_ALL", "flush TLB")
    ccover(sfence && !io.sfence.bits.rs1 && io.sfence.bits.rs2, "SFENCE_ASID", "flush TLB ASID")
    ccover(sfence && io.sfence.bits.rs1 && !io.sfence.bits.rs2, "SFENCE_LINE", "flush TLB line")
    ccover(sfence && io.sfence.bits.rs1 && io.sfence.bits.rs2, "SFENCE_LINE_ASID", "flush TLB line/ASID")
    ccover(multipleHitsD1, "MULTIPLE_HITS", "Two matching translations in TLB")
  }

  def ccover(cond: Bool, label: String, desc: String)(implicit sourceInfo: SourceInfo) =
    cover(cond, s"${if (instruction) "I" else "D"}TLB_$label", "MemorySystem;;" + desc)

  def replacementEntry(set: Seq[Entry], alt: UInt) = {
    val valids = set.map(_.valid.orR).asUInt
    val (sel, v) = EncTree(~valids)
    Mux(v, sel, alt)
  }
}

