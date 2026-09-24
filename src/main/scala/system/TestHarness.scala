// See LICENSE.SiFive for license details.

package freechips.rocketchip.system

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.debug.Debug
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.util.AsyncResetReg
import freechips.rocketchip.device.AXI4MemorySlave
import freechips.rocketchip.subsystem.ClockRouting
import chisel3.withClockAndReset

class TestHarness()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val ldut = LazyModule(new ExampleRocketSystem)
  val dut = Module(ldut.module)

  // Dummy CCU stand-in (subsystem/ClockRouting.scala): one input clock,
  // producing the three clock/reset domains the design needs. dut's own
  // ambient clock is the 500MHz/rest-of-chip domain; the other two feed
  // dut's clockRoutingIO ports, which RocketSubsystemModuleImp routes down
  // to L2Macro/tiles.
  val clockRoute = Module(new ClockRouting)
  dut.clock := clockRoute.io.clk_500MHz
  dut.clockRoutingIO.clk_3_5GHz  := clockRoute.io.clk_3_5GHz
  dut.clockRoutingIO.clk_1_75GHZ := clockRoute.io.clk_1_75GHZ
  dut.clockRoutingIO.rst_3_5GHz  := clockRoute.io.rst_3_5GHz
  dut.clockRoutingIO.rst_1_75GHZ := clockRoute.io.rst_1_75GHZ

  // Allow the debug ndreset to reset the dut, but not until the initial reset has completed.
  // Use rst_500MHz (synchronized to clk_500MHz, dut's own ambient clock) rather than the
  // plain top-level reset, which is only synchronous to the undivided clock.
  dut.reset := (clockRoute.io.rst_500MHz | dut.debug.map { debug => AsyncResetReg(debug.ndreset) }.getOrElse(false.B)).asBool

  dut.dontTouchPorts()
  dut.tieOffInterrupts()

  // dynamicLatency = true wires in the DRAMSim3-backed model for realistic DRAM
  // timing; false falls back to a fixed-latency memory model with no DRAMSim3
  // dependency. Controlled by the DRAMSIM3 make variable (see top-level
  // Makefrag), passed through as this JVM system property.
  // TODO Multiple channels
  val l_simAXIMem = AXI4MemorySlave(
    ldut.memAXI4Node,
    p(freechips.rocketchip.subsystem.ExtMem).get.master.size.toLong,
    useBlackBox = true,
    dynamicLatency = sys.props.getOrElse("dramsimEnabled", "true").toBoolean
  )
  val simAXIMem = Module(l_simAXIMem.module)
  // simAXIMem is wired to ldut.mem_axi4 below via a raw (non-synchronizing) bundle
  // connection, so it must run on the exact same clock/reset as dut, not
  // TestHarness's own (undivided) ambient clock, which is a separate domain.
  simAXIMem.clock := clockRoute.io.clk_500MHz
  simAXIMem.reset := dut.reset
  l_simAXIMem.io_axi4.elements.head._2 <> ldut.mem_axi4.elements.head._2

  // mmio_mem (built inside connectMMIO) is wired to ldut.mmio_axi4 the same
  // raw way as simAXIMem above, and needs the same clock/reset as dut for
  // the same reason.
  withClockAndReset(clockRoute.io.clk_500MHz, dut.reset) {
    SimAXIMem.connectMMIO(ldut)
  }
  ldut.l2_frontend_bus_axi4.foreach(_.tieoff)
  // devices/debug/Debug.scala's TLDebugModule documents this contract explicitly:
  // "debug_clock / debug_reset = Inner debug (synchronous to tl_clock)". tl_clock
  // (devices/debug/Periphery.scala) is bound to RocketSubsystemModuleImp's own
  // ambient clock/reset, i.e. dut.clock/dut.reset = clk_500MHz/rst_500MHz -- so
  // debug_clock must be that same clock, not TestHarness's raw clock (which is
  // the undivided 7GHz reference, with no relationship to clk_500MHz). dmInner has
  // no synchronizer between debug_clock and tl_clock because it isn't meant to
  // need one -- they're supposed to be the same clock.
  Debug.connectDebug(dut.debug, dut.resetctrl, dut.psd, clockRoute.io.clk_500MHz, dut.reset, io.success)
}
