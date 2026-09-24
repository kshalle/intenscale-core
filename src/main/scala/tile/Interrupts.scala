// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import Chisel._
import scala.collection.mutable.ListBuffer

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket.constants.StConfiguration._

class TileInterrupts(implicit p: Parameters) extends CoreBundle()(p) {
  val debug = Bool()
  val mtip  = Bool()
  val msip  = Bool()
  val meip  = Bool()
  val seip  = usingSupervisor.option(Bool())
  val lip   = Vec(coreParams.nLocalInterrupts, Bool())
}

// Use diplomatic interrupts to external interrupts from the subsystem into the tile
trait SinksExternalInterrupts { this: BaseTile =>

  protected val intXbar     = 0 to (NUM_CTXT*NUM_PUS) - 1 map {x => LazyModule(new IntXbar)}
  protected val intSinkNode = 0 to (NUM_CTXT*NUM_PUS) - 1 map {x => IntSinkNode(IntSinkPortSimple())}

  val intInwardNode = ListBuffer[IntInwardNode]()
  for(row <- 0 until NUM_PUS*NUM_CTXT) {
    intInwardNode += intXbar(row).intnode :=* IntIdentityNode()(ValName("int_local"))
  }

  def cpuDevice: ListBuffer[Device]
  for(row <- 0 until NUM_PUS*NUM_CTXT) {
    intSinkNode(row)       := intXbar(row).intnode

    val intcDevice = new DeviceSnippet {
    override def parent = Some(cpuDevice(row))
    def describe(): Description = {
      Description("interrupt-controller", Map(
          "compatible"           -> "riscv,cpu-intc".asProperty,
          "interrupt-controller" -> Nil,
          "#interrupt-cells"     -> 1.asProperty))
      }
    }

    ResourceBinding {
      intSinkNode(row).edges.in.flatMap(_.source.sources).map { case s =>
        for (i <- s.range.start until s.range.end) {
          csrIntMap.lift(i).foreach { j =>
            s.resources.foreach { r =>
              r.bind(intcDevice, ResourceInt(j))
            }
          }
        }
      }
    }
  }

  // TODO: the order of the following two functions must match, and
  //         also match the order which things are connected to the
  //         per-tile crossbar in subsystem.HasTiles.connectInterrupts

  // debug, msip, mtip, meip, seip, lip offsets in CSRs
  def csrIntMap: List[Int] = {
    val nlips = tileParams.core.nLocalInterrupts
    val seip = if (usingSupervisor) Seq(9) else Nil
    List(65535, 3, 7, 11) ++ seip ++ List.tabulate(nlips)(_ + 16)
  }

  // go from flat diplomatic Interrupts to bundled TileInterrupts
  def decodeCoreInterrupts(core: TileInterrupts, row : Int) {
    val async_ips = Seq(core.debug)
    val periph_ips = Seq(
      core.msip,
      core.mtip,
      core.meip)

    val seip = if (core.seip.isDefined) Seq(core.seip.get) else Nil

    val core_ips = core.lip

    val (interrupts, _) = intSinkNode(row).in(0)
    (async_ips ++ periph_ips ++ seip ++ core_ips).zip(interrupts).foreach { case(c, i) => c := i }
  }
}

trait SourcesExternalNotifications { this: BaseTile =>
  // Report unrecoverable error conditions
  val haltNode = 0 to (NUM_CTXT*NUM_PUS) - 1 map {x => IntSourceNode(IntSourcePortSimple())}

  def reportHalt(could_halt: Option[Bool]) {
    val (halt_and_catch_fire, _) = haltNode(0).out(0) // TODO: Generalized for the all ROWs.
    halt_and_catch_fire(0) := could_halt.map(RegEnable(true.B, false.B, _)).getOrElse(false.B)
  }

  def reportHalt(errors: Seq[CanHaveErrors]) {
    reportHalt(errors.flatMap(_.uncorrectable).map(_.valid).reduceOption(_||_))
  }

  // Report when the tile has ceased to retire instructions
  val ceaseNode = 0 to (NUM_CTXT*NUM_PUS) - 1 map {x => IntSourceNode(IntSourcePortSimple())}

  def reportCease(could_cease: Option[Bool], quiescenceCycles: Int = 8) {
    def waitForQuiescence(cease: Bool): Bool = {
      // don't report cease until signal is stable for longer than any pipeline depth
      val count = RegInit(0.U(log2Ceil(quiescenceCycles + 1).W))
      val saturated = count >= quiescenceCycles.U
      when (!cease) { count := 0.U }
      when (cease && !saturated) { count := count + 1.U }
      saturated
    }
    val (cease, _) = ceaseNode(0).out(0) // TODO: Generalized for the all ROWs.
    cease(0) := could_cease.map{ c => 
      val cease = (waitForQuiescence(c))
      // Test-Only Code --
      val prev_cease = RegNext(c, false.B)
      assert(!(prev_cease & !c), "CEASE line can not glitch once raised") 
      cease
    }.getOrElse(false.B)
  }

  // Report when the tile is waiting for an interrupt
  val wfiNode = 0 to (NUM_CTXT*NUM_PUS) - 1 map {x => IntSourceNode(IntSourcePortSimple())}

  def reportWFI(could_wfi: Option[Bool]) {
    val (wfi, _) = wfiNode(0).out(0) // TODO: Generalized for the all ROWs.
    wfi(0) := could_wfi.map(RegNext(_, init=false.B)).getOrElse(false.B)
  }
}

