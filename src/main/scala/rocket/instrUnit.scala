// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import chisel3.{withClock,withReset}
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.experimental.chiselName
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.ICacheLogicalTreeNode

import superThread._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._

class InstrUnitReqBundle()(implicit p: Parameters) extends IntenBundle  {
  val ctxtId = UInt(INPUT, CTXT_T_ID_LEN)
  val addr   = UInt(INPUT, ADDR_LEN)
}

class InstrUnit(val icacheParams: ICacheParams, hartid: Int)(implicit p: Parameters) extends LazyModule with HasIntenParameters {
  lazy val module = new InstrUnitModule(this)
  val icache      = 0 to NUM_PUS - 1 map { x => LazyModule(new ICache(icacheParams, hartid))}
  val masterNode  = 0 to NUM_PUS - 1 map { x => icache(x).masterNode}
  val slaveNode   = 0 to NUM_PUS - 1 map { x => icache(x).slaveNode}
}

class InstrUnitBundle(val outer: InstrUnit) extends CoreBundle()(outer.p)
    with HasExternallyDrivenTileConstants {
  val ctxtUnitInstrUnit = Vec(NUM_PUS, new CtxtUnitInstrUnitBundle()).flip()
  val halfSel           = Vec(NUM_PUS, Bool(INPUT))
  val flush_icache      = Bool(INPUT)
  val ptw               = Vec(NUM_PUS, new TLBPTWIO)
  val errors            = new ICacheErrors
  val pInstrAddr        = UInt(OUTPUT, 32)
  val aFirePerf         = Vec(NUM_PUS, Bool(OUTPUT))
  val dFirePerf         = Vec(NUM_PUS, Bool(OUTPUT))
}

@chiselName
class InstrUnitModule(outer: InstrUnit) extends LazyModuleImp(outer) with HasCoreParameters with HasL1ICacheParameters {
  val io = IO(new InstrUnitBundle(outer))
  implicit val edge = outer.masterNode(0).edges.out(0)
  val icache        = 0 to NUM_PUS - 1 map { x => outer.icache(x).module}
  require(fetchWidth*coreInstBytes == outer.icacheParams.fetchBytes)

  val tlb = 0 to NUM_PUS - 1 map { x => Module(new TLB(true, log2Ceil(fetchBytes), TLBConfig(nTLBEntries, 2, 4, NUM_CTXT))).io }

  val s0_valid        = Wire(Vec(NUM_PUS, Bool()))
  val s0_addr         = Wire(Vec(NUM_PUS, UInt()))
  val s1_valid_reg    = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val respCtxtId_reg  = Reg(init=Vec.fill(NUM_PUS) {UInt(0, CTXT_ID_LEN)})
  val s2_valid_reg    = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})

  val s3_valid_reg    = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val s4_valid_reg    = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val s4_xcpt_ae_reg  = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val s4_xcpt_pf_reg  = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})
  val s4_tlb_miss_reg = Reg(init=Vec.fill(NUM_PUS) {Bool(false)})

  for(ips <- 0 until NUM_PUS) {
    s0_valid(ips) := io.ctxtUnitInstrUnit(ips).reqValid && io.ctxtUnitInstrUnit(ips).reqReady && !io.ctxtUnitInstrUnit(ips).sfenceReq_valid
    s0_addr(ips)  := alignPC(io.ctxtUnitInstrUnit(ips).reqAddr)
    io.aFirePerf(ips) := icache(ips).io.aFirePerf
    io.dFirePerf(ips) := icache(ips).io.dFirePerf
  }

  for(ips <- 0 until NUM_PUS) {
    io.ptw(ips)                        <> tlb(ips).ptw
    io.ctxtUnitInstrUnit(ips).reqReady := Bool(true)

    // TODO: The TLB need to be re-timed.
    tlb(ips).ctxtIdEarly          := io.ctxtUnitInstrUnit(ips).reqCtxtId
    tlb(ips).validEarly           := io.ctxtUnitInstrUnit(ips).reqValid
    tlb(ips).req.valid            := Reg(next=io.ctxtUnitInstrUnit(ips).reqValid, init=Bool(false))
    tlb(ips).req.bits.isReal      := Bool(true)
    tlb(ips).req.bits.vaddr       := RegEnable(next=s0_addr(ips), enable=io.ctxtUnitInstrUnit(ips).reqValid, init=UInt(0, vaddrBitsExtended))
    for(c <- 0 until NUM_CTXT) {
      tlb(ips).req.bits.vaddrCtxt(c) := RegEnable(next=s0_addr(ips)(vaddrBits-1, pgIdxBits), enable=io.ctxtUnitInstrUnit(ips).reqValid && (io.ctxtUnitInstrUnit(ips).reqCtxtId(log2Up(NUM_CTXT)-1, 0) === UInt(c)), init=UInt(0, vaddrBits-pgIdxBits))
    }

    tlb(ips).sfence.valid         := RegEnable(next=io.ctxtUnitInstrUnit(ips).sfenceReq_valid && io.ctxtUnitInstrUnit(ips).reqValid, enable=io.ctxtUnitInstrUnit(ips).reqValid, init=Bool(false))
    tlb(ips).sfence.bits.rs1      := RegEnable(next=io.ctxtUnitInstrUnit(ips).sfenceReq_rs1, enable=io.ctxtUnitInstrUnit(ips).reqValid, init=Bool(false))
    tlb(ips).sfence.bits.rs2      := RegEnable(next=io.ctxtUnitInstrUnit(ips).sfenceReq_rs2, enable=io.ctxtUnitInstrUnit(ips).reqValid, init=Bool(false))
    tlb(ips).req.bits.ctxtId      := RegEnable(next=io.ctxtUnitInstrUnit(ips).reqCtxtId, enable=io.ctxtUnitInstrUnit(ips).reqValid, init=UInt(0, CTXT_ID_LEN))
    tlb(ips).req.bits.passthrough := Bool(false)
    tlb(ips).sfence.bits.addr     := UInt(0)
    tlb(ips).sfence.bits.asid     := UInt(0)
    tlb(ips).req.bits.size        := log2Ceil(coreInstBytes*fetchWidth)

    s1_valid_reg(ips)             := s0_valid(ips)
    s2_valid_reg(ips)             := s1_valid_reg(ips)
    s3_valid_reg(ips)             := s2_valid_reg(ips)
    s4_valid_reg(ips)             := s3_valid_reg(ips)
    s4_xcpt_ae_reg(ips)           := Reg(next=tlb(ips).respD1.ae.inst && !tlb(ips).respD1.miss, init=Bool(false))
    s4_xcpt_pf_reg(ips)           := Reg(next=tlb(ips).respD1.pf.inst && !tlb(ips).respD1.miss, init=Bool(false))
    s4_tlb_miss_reg(ips)          := Reg(next=tlb(ips).respD1.miss, init=Bool(false))

    if(NUM_PUS == 1) {
      icache(ips).io.validEarly      := s1_valid_reg(ips)
      icache(ips).io.addrP1Early     := RegEnable(next=s0_addr(ips)(pgIdxBits-1, 0), enable=io.ctxtUnitInstrUnit(ips).reqValid, init=UInt(0, pgIdxBits))
      icache(ips).io.req.valid       := Reg(next=s1_valid_reg(ips), init=Bool(false))
      icache(ips).io.req.bits.idx    := tlb(ips).respD1.paddr
      icache(ips).io.req.bits.pipeId := UInt(0)
      icache(ips).io.s1_kill         := Reg(next=tlb(ips).respD1.miss, init=Bool(false)) || (Reg(next=(tlb(ips).respD1.ae.inst || tlb(ips).respD1.pf.inst), init=Bool(false)) && s3_valid_reg(ips))
    }
    else {
      val icValidEarly                = Mux((!io.halfSel(ips)), s0_valid(0), s0_valid(1))
      icache(ips).io.validEarly      := Reg(next=icValidEarly, init=Bool(false))
      icache(ips).io.addrP1Early     := RegEnable(next=Mux((!io.halfSel(ips)), s0_addr(0)(pgIdxBits-1, 0), s0_addr(1))(pgIdxBits-1, 0), enable=icValidEarly, init=UInt(0, pgIdxBits))

      val icValid                     = Mux((io.halfSel(ips)), s1_valid_reg(0), s1_valid_reg(1))
      icache(ips).io.req.valid       := Reg(next=icValid, init=Bool(false))
      icache(ips).io.req.bits.idx    := Mux((!io.halfSel(ips)), tlb(0).respD1.paddr, tlb(1).respD1.paddr)
      icache(ips).io.req.bits.pipeId := io.halfSel(ips)
      val s0_kill0                    = tlb(0).respD1.miss || ((tlb(0).respD1.ae.inst || tlb(0).respD1.pf.inst) && s2_valid_reg(0))
      val s0_kill1                    = tlb(1).respD1.miss || ((tlb(1).respD1.ae.inst || tlb(1).respD1.pf.inst) && s2_valid_reg(1))
      icache(ips).io.s1_kill         := Reg(next=Mux(!(io.halfSel(ips)), s0_kill0, s0_kill1), init=Bool(false))
    }
    icache(ips).io.invalidate   := Reg(next=io.flush_icache || io.ctxtUnitInstrUnit(ips).flush_icache, init=Bool(false))

    // Connect IMEM response to the CTXT.
    icache(ips).io.resp.ready            := Bool(true)
    if(NUM_PUS == 1) {
      io.ctxtUnitInstrUnit(ips).instrMemResp   := icache(ips).io.resp.bits.instrMemResp(ips)
    }
    else {
      io.ctxtUnitInstrUnit(ips).instrMemResp   := Mux((io.halfSel(ips)), icache(0).io.resp.bits.instrMemResp(ips), icache(1).io.resp.bits.instrMemResp(ips))
    }
    io.ctxtUnitInstrUnit(ips).ae         := Reg(next=s4_xcpt_ae_reg(ips) && s4_valid_reg(ips), init=Bool(false))
    io.ctxtUnitInstrUnit(ips).pf         := Reg(next=s4_xcpt_pf_reg(ips) && s4_valid_reg(ips), init=Bool(false))

    // Provides an early valid.
    if(NUM_PUS == 1) {
      io.ctxtUnitInstrUnit(ips).respValidEarly  := (icache(ips).io.earlyReadValid || s4_xcpt_ae_reg(ips) || s4_xcpt_pf_reg(ips)) && s4_valid_reg(ips)
      io.ctxtUnitInstrUnit(ips).failCauseEarly  := icache(ips).io.earlyReadFailCause
    }
    else {
      io.ctxtUnitInstrUnit(ips).respValidEarly  := (Mux(!(io.halfSel(ips)), icache(0).io.earlyReadValid, icache(1).io.earlyReadValid) || s4_xcpt_ae_reg(ips) || s4_xcpt_pf_reg(ips)) && s4_valid_reg(ips)
      io.ctxtUnitInstrUnit(ips).failCauseEarly  := Mux(!(io.halfSel(ips)), icache(0).io.earlyReadFailCause, icache(1).io.earlyReadFailCause)
    }
    io.ctxtUnitInstrUnit(ips).failTlbMiss     := s4_tlb_miss_reg(ips)
    io.ctxtUnitInstrUnit(ips).icacheReady     := icache(ips).io.icacheReady
    io.ctxtUnitInstrUnit(ips).itlbReady       := tlb(ips).req.ready
  }
  io.pInstrAddr := icache(0).io.req.bits.idx

  if(NUM_PUS == 2) {
    for(ips <- 0 until NUM_PUS) {
      assert(!icache(ips).io.req.valid || (icache(ips).io.req.bits.idx(IC_HLF_BIT) === Bool(ips==1)))
    }
  }

  def alignPC(pc: UInt) = ~(~pc | (coreInstBytes - 1))

}

/** Mix-ins for constructing tiles that have an ICache-based pipeline frontend */
trait HasICacheFrontend extends CanHavePTW { this: BaseTile =>
  val module: HasICacheFrontendModule
  val frontend = LazyModule(new InstrUnit(tileParams.icache.get, hartId))
  for(ips <- 0 until NUM_PUS) {
    icacheNode(ips) := frontend.masterNode(ips)
    connectTLSlave(frontend.slaveNode(ips), tileParams.core.fetchBytes)
  }
  nPTWPorts += 1

  // This should be a None in the case of not having an ITIM address, when we
  // don't actually use the device that is instantiated in the frontend.
  private val deviceOpt = if (tileParams.icache.get.itimAddr.isDefined) Some(frontend.icache(0).device) else None

  val iCacheLogicalTreeNode = new ICacheLogicalTreeNode(frontend.icache(0), deviceOpt, tileParams.icache.get)
}

trait HasICacheFrontendModule extends CanHavePTWModule with HasIntenParameters {
  val outer: HasICacheFrontend
  ptwPortsH0 += outer.frontend.module.io.ptw(0)
  if(NUM_PUS == 2) {
    ptwPortsH1 += outer.frontend.module.io.ptw(1)
  }
}

