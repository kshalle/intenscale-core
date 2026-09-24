// See LICENSE.SiFive for license details.

package freechips.rocketchip.interrupts

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.util.{SynchronizerShiftReg, AsyncResetReg}
import freechips.rocketchip.diplomacy._

@deprecated("IntXing does not ensure interrupt source is glitch free. Use IntSyncSource and IntSyncSink", "rocket-chip 1.2")
class IntXing(sync: Int = 3)(implicit p: Parameters) extends LazyModule
{
  val intnode = IntAdapterNode()

  lazy val module = new LazyModuleImp(this) {
    (intnode.in zip intnode.out) foreach { case ((in, _), (out, _)) =>
      out := SynchronizerShiftReg(in, sync) //a specially designed register that has improved metastability characteristics
      //use a synchronizer register in any place that crosses clock domain where the two clocks are not integer multiples
      // or there is some reason to suspect that there will be drift in positions of their edges relative to each other.
      //If the clock tree phase locks the two clocks relative to each other, a synchronizer should not be necessary.
      //However, for reset, or external interrupt, and other signals not synchronized to system clock, these are needed.
    }
  }
}

object IntSyncCrossingSource
{
  def apply(alreadyRegistered: Boolean = false)(implicit p: Parameters) =
  {
    val intsource = LazyModule(new IntSyncCrossingSource(alreadyRegistered))
    intsource.node
  }
}


class IntSyncCrossingSource(alreadyRegistered: Boolean = false)(implicit p: Parameters) extends LazyModule
{
  val node = IntSyncSourceNode(alreadyRegistered)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      if (alreadyRegistered) {
        out.sync := in
      } else {
        out.sync := AsyncResetReg(Cat(in.reverse)).asBools
      }
    }
  }
}


object IntSyncCrossingSink
{
  @deprecated("IntSyncCrossingSink which used the `sync` parameter to determine crossing type is deprecated. Use IntSyncAsyncCrossingSink, IntSyncRationalCrossingSink, or IntSyncSyncCrossingSink instead for > 1, 1, and 0 sync values respectively", "rocket-chip 1.2")
  def apply(sync: Int = 3)(implicit p: Parameters) =
  {
    val intsink = LazyModule(new IntSyncAsyncCrossingSink(sync))
    intsink.node
  }
}


class IntSyncAsyncCrossingSink(sync: Int = 3)(implicit p: Parameters) extends LazyModule
{
  val node = IntSyncSinkNode(sync)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      out := SynchronizerShiftReg(in.sync, sync)
    }
  }
}

object IntSyncAsyncCrossingSink
{
  def apply(sync: Int = 3)(implicit p: Parameters) =
  {
    val intsink = LazyModule(new IntSyncAsyncCrossingSink(sync))
    intsink.node
  }
}

class IntSyncSyncCrossingSink()(implicit p: Parameters) extends LazyModule
{
  val node = IntSyncSinkNode(0)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      out := in.sync
    }
  }
}

object IntSyncSyncCrossingSink
{
  def apply()(implicit p: Parameters) =
  {
    val intsink = LazyModule(new IntSyncSyncCrossingSink())
    intsink.node
  }
}

class IntSyncRationalCrossingSink()(implicit p: Parameters) extends LazyModule
{
  val node = IntSyncSinkNode(1)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      out := RegNext(in.sync)
    }
  }
}
class PulseExtenderBuffer(enExtend: Boolean, pulse: Int)(implicit p: Parameters) extends LazyModule
{
  val node = IntAdapterNode()

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      if(enExtend){
        for (i <- 0 until (in.asUInt).getWidth) {
          val IntHold = RegInit(false.B)
          val count   = RegInit(0.U(pulse.W))
          when(in(i)){
            IntHold := true.B
            count := 15.U
          }
          .elsewhen(count === 0.U){
            IntHold := false.B
          }
          .otherwise{
            count := count - 1.U
          }
          out(i) := IntHold
        }
      }
      else{
        out := RegNext(in)
      }
    }
  }
}
object PulseExtenderBuffer
{
  def apply(enExtend: Boolean, pulse: Int)(implicit p: Parameters) =
  {
    val intsink = LazyModule(new PulseExtenderBuffer(enExtend, pulse))
    intsink.node
  }
}
class PulseExtenderBuffer_simple(enExtend: Boolean, pulse: Int)(implicit p: Parameters) extends Module
{
  val io = IO(new Bundle {
    val in  = Input(UInt(1.W))
    val out = Output(UInt(1.W))
  })

  if(enExtend){
      val IntHold = RegInit(false.B)
      val count   = RegInit(0.U(pulse.W))
      when(io.in.asBool){
        IntHold := true.B
        count := 15.U
      }
      .elsewhen(count === 0.U){
        IntHold := false.B
      }
      .otherwise{
        count := count - 1.U
      }
      io.out := IntHold
  }
  else{
    io.out := io.in
  }
}

object IntSyncRationalCrossingSink
{
  def apply()(implicit p: Parameters) =
  {
    val intsink = LazyModule(new IntSyncRationalCrossingSink())
    intsink.node
  }
}

class IntAsyncCrossing()(implicit p: Parameters) extends LazyModule
{
  val source = LazyModule(new IntSyncCrossingSource())
  val sink = LazyModule(new IntSyncAsyncCrossingSink())
  val intnode = NodeHandle(source.node, sink.node)

  sink.node := source.node

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val in_clock  = Clock(INPUT)
      val in_reset  = Input(Bool())
      val out_clock = Clock(INPUT)
      val out_reset = Input(Bool())
    })

    source.module.clock := io.in_clock
    source.module.reset := io.in_reset
    sink.module.clock := io.out_clock
    sink.module.reset := io.out_reset
  }
}