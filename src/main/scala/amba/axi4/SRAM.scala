// See LICENSE.SiFive for license details.

package freechips.rocketchip.amba.axi4

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{BusMemoryLogicalTreeNode, LogicalModuleTree, LogicalTreeNode}
import freechips.rocketchip.diplomaticobjectmodel.model.AXI4_Lite
import freechips.rocketchip.util._
import freechips.rocketchip.amba._
import freechips.rocketchip.rocket.constants._

import scala.math.{min,max}

// Setting wcorrupt=true is not enough to enable the w.user field
// You must also list AMBACorrupt in your master's requestFields
class AXI4RAM(
    address: AddressSet,
    cacheable: Boolean = true,
    parentLogicalTreeNode: Option[LogicalTreeNode] = None,
    executable: Boolean = true,
    beatBytes: Int = 4,
    devName: Option[String] = None,
    base: BigInt = BigInt(0L),
    errors: Seq[AddressSet] = Nil,
    wcorrupt: Boolean = true)
  (implicit p: Parameters) extends DiplomaticSRAM(address, beatBytes, devName) with HasIntenParameters
{
  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address       = List(address) ++ errors,
      resources     = resources,
      regionType    = if (cacheable) RegionType.UNCACHED else RegionType.IDEMPOTENT,
      executable    = executable,
      supportsRead  = TransferSizes(1, beatBytes),
      supportsWrite = TransferSizes(1, beatBytes),
      interleavedId = Some(0))),
    beatBytes  = beatBytes,
    requestKeys = if (wcorrupt) Seq(AMBACorrupt) else Seq(),
    minLatency = 1)))

  lazy val module = new LazyModuleImp(this) {
    val OrgDepth: BigInt     = 1L << mask.filter(b=>b).size
    val ForceResize: Boolean = (OrgDepth > SIM_MAX_MEM_DEPTH) && (devName.getOrElse("mem") == "SimAXIMem")
    val MemDepth: BigInt     = if(ForceResize) SIM_MEM_PART_DEPTH else OrgDepth
    val NumParts: Int        = if(ForceResize) min(((OrgDepth).toFloat / (MemDepth).toFloat).ceil.toInt, SIM_MAX_NUM_PARTS)  else 1

    // Get address width.
    val addWF = log2Ceil(OrgDepth)
    val addW  = log2Ceil(MemDepth)

    val (in, edgeIn) = node.in(0)
    val corrupt = if (edgeIn.bundle.requestFields.contains(AMBACorrupt)) Some(SeqMem(1 << mask.filter(b=>b).size, UInt(width=2))) else None

    if(ForceResize) {
      println(s"[${Console.YELLOW}warn${Console.RESET}] ${Console.YELLOW}The depth of the memory " ++ "\"" ++ devName.getOrElse("mem") ++ "\"" ++ s" may be reduced from ${OrgDepth} to ${NumParts}x${MemDepth}.${Console.RESET}")
    }

    // Validate the parts.
    val partValid_reg   = Reg(init=Vec.fill(NumParts) {Bool(false)})
    val partId_reg      = Reg(init=Vec.fill(NumParts) {UInt(0, max(addWF-addW, 1))})
    val nxtFreePart_reg = Reg(init=UInt(1, log2Ceil(NumParts+1)))

    if(ForceResize) {
      // Force the first part to be at the base. As this part will be loaded by the prgram, and the loading can be done through Verilator.
      val baseAddrExtend = UInt(base, in.ar.bits.addr.getWidth+1)
      val baseAddr       = baseAddrExtend(in.ar.bits.addr.getWidth-1, 0)
      val baseAddrFilter = Cat((mask zip (baseAddr >> log2Ceil(beatBytes)).asBools).filter(_._1).map(_._2).reverse)
      partValid_reg(0)  := Bool(true)
      partId_reg(0)     := baseAddrFilter(addWF-1, addW)
    }

    val mem = 0 to NumParts - 1 map { x => {
          val suffix = if(x>0) "P"+x.toString else ""
          val (mem, omSRAM, omMem) = makeSinglePortedByteWriteSeqMem(size = MemDepth, suffix=suffix)
          parentLogicalTreeNode.map {
            case parentLTN =>
              def sramLogicalTreeNode = new BusMemoryLogicalTreeNode(
                device = device,
                omSRAMs = Seq(omSRAM),
                busProtocol = new AXI4_Lite(None),
                dataECC = None,
                hasAtomics = None,
                busProtocolSpecification = None)
              LogicalModuleTree.add(parentLTN, sramLogicalTreeNode)
          }
          mem
        }
      }

    val r_addr     = Cat((mask zip (in.ar.bits.addr >> log2Ceil(beatBytes)).asBools).filter(_._1).map(_._2).reverse)
    val w_addr     = Cat((mask zip (in.aw.bits.addr >> log2Ceil(beatBytes)).asBools).filter(_._1).map(_._2).reverse)
    val r_sel0     = address.contains(in.ar.bits.addr)
    val w_sel0     = address.contains(in.aw.bits.addr)
    val r_addr_reg = Reg(next=r_addr)

    val w_full = RegInit(Bool(false))
    val w_id   = Reg(UInt())
    val w_echo = Reg(BundleMap(in.params.echoFields))
    val r_sel1 = Reg(r_sel0)
    val w_sel1 = Reg(w_sel0)

    when (in. b.fire()) { w_full := Bool(false) }
    when (in.aw.fire()) { w_full := Bool(true) }

    when (in.aw.fire()) {
      w_id := in.aw.bits.id
      w_sel1 := w_sel0
      w_echo :<= in.aw.bits.echo
    }

    val wdata = Vec.tabulate(beatBytes) { i => in.w.bits.data(8*(i+1)-1, 8*i) }
    when (in.aw.fire() && w_sel0 && !reset) {
      if(ForceResize) {
        val success  = Wire(Bool())
        val success2 = Wire(Bool())
        val memId    = Wire(UInt())

        success  := Bool(false)
        success2 := Bool(false)

        for (x <- 0 until NumParts) {
          when((partId_reg(x) === w_addr(addWF-1, addW)) && partValid_reg(x)) {
            memId    := UInt(x)
            success  := Bool(true)
          }
        }

        when(!success) {
          // Try to choose the next free part.
          when(nxtFreePart_reg < UInt(NumParts)) {
            for (x <- 0 until NumParts) {
              when(nxtFreePart_reg === UInt(x)) {
                partValid_reg(x) := Bool(true)
                partId_reg(x)    := w_addr(addWF-1, addW)
                nxtFreePart_reg  := nxtFreePart_reg + UInt(1)
                memId            := UInt(x)
                success2         := Bool(true)
              }
            }
          }
        }

        when(success || success2) {
          for (x <- 0 until NumParts) {
            when(memId === UInt(x)) {
              mem(x).write(w_addr(addW-1, 0), wdata, in.w.bits.strb.asBools)
            }
          }
        }

        when(!(success || success2)) {
          // Verify the access address range.
          printf("Unable to access address 0x%x, as the depth of a memory has been reduced from %d to %dx%d.\n", w_addr, UInt(OrgDepth), UInt(NumParts), UInt(MemDepth))
          assert(Bool(false))
        }
      }
      else {
        mem(0).write(w_addr, wdata, in.w.bits.strb.asBools)
      }

      corrupt.foreach { _.write(w_addr, in.w.bits.user(AMBACorrupt).asUInt) }
    }

    in. b.valid := w_full
    in.aw.ready := in. w.valid && (in.b.ready || !w_full)
    in. w.ready := in.aw.valid && (in.b.ready || !w_full)

    in.b.bits.id   := w_id
    in.b.bits.resp := Mux(w_sel1, AXI4Parameters.RESP_OKAY, AXI4Parameters.RESP_DECERR)
    in.b.bits.echo :<= w_echo

    val r_full = RegInit(Bool(false))
    val r_id   = Reg(UInt())
    val r_echo = Reg(BundleMap(in.params.echoFields))

    when (in. r.fire()) { r_full := Bool(false) }
    when (in.ar.fire()) { r_full := Bool(true) }

    when (in.ar.fire()) {
      r_id   := in.ar.bits.id
      r_sel1 := r_sel0
      r_echo :<= in.ar.bits.echo
    }

    val ren = in.ar.fire()
    val rdata = {
      if(ForceResize) {
        val allMemOuts = 0 to NumParts - 1 map { x => mem(x).readAndHold(r_addr(addW-1, 0), ren && (partId_reg(x) === r_addr(addWF-1, addW)) && partValid_reg(x))}
        val r = Vec.fill(beatBytes) {Wire(UInt(0, 8))}
        for (x <- 0 until NumParts) {
          when((partId_reg(x) === r_addr_reg(addWF-1, addW)) && partValid_reg(x)) {
            r := allMemOuts(x)
          }
        }
        r
      }
      else {
        mem(0).readAndHold(r_addr, ren)
      }
    }
    val rcorrupt = corrupt.map(_.readAndHold(r_addr, ren)(0)).getOrElse(Bool(false))

    in. r.valid := r_full
    in.ar.ready := in.r.ready || !r_full

    in.r.bits.id   := r_id
    in.r.bits.resp := Mux(r_sel1, Mux(rcorrupt, AXI4Parameters.RESP_SLVERR, AXI4Parameters.RESP_OKAY), AXI4Parameters.RESP_DECERR)
    in.r.bits.data := Cat(rdata.reverse)
    in.r.bits.echo :<= r_echo
    in.r.bits.last := Bool(true)
  }
}

object AXI4RAM
{
  def apply(
    address: AddressSet,
    cacheable: Boolean = true,
    parentLogicalTreeNode: Option[LogicalTreeNode] = None,
    executable: Boolean = true,
    beatBytes: Int = 4,
    devName: Option[String] = None,
    base: BigInt = BigInt(0L),
    errors: Seq[AddressSet] = Nil,
    wcorrupt: Boolean = true)
  (implicit p: Parameters) =
  {
    val axi4ram = LazyModule(new AXI4RAM(
      address = address,
      cacheable = cacheable,
      executable = executable,
      beatBytes = beatBytes,
      devName = devName,
      base = base,
      errors = errors,
      wcorrupt = wcorrupt))
    axi4ram.node
  }
}
