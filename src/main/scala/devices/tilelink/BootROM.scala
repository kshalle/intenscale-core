// See LICENSE.SiFive for license details.

package freechips.rocketchip.devices.tilelink

import Chisel._
import java.io.{File, FileWriter}
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.subsystem.{BaseSubsystem, HasResetVectorWire}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import chisel3.{withReset,AsyncReset,RequireAsyncReset,dontTouch}
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.file.{Files, Paths}

/** Size, location and contents of the boot rom. */
case class BootROMParams(
  address: BigInt = 0x10000,
  size: Int = 0x10000,
  hang: BigInt = 0x10040,
  contentFileName: String)
case object BootROMParams extends Field[BootROMParams]

class TLROM(val base: BigInt, val size: Int, contentsDelayed: => Seq[Byte], executable: Boolean = true, beatBytes: Int = 4,
  resources: Seq[Resource] = new SimpleDevice("rom", Seq("sifive,rom0")).reg("mem"))(implicit p: Parameters) extends LazyModule
{
  val node = TLManagerNode(Seq(TLSlavePortParameters.v1(
    Seq(TLSlaveParameters.v1(
      address     = List(AddressSet(base, size-1)),
      resources   = resources,
      regionType  = RegionType.UNCACHED,
      executable  = executable,
      supportsGet = TransferSizes(1, beatBytes),
      fifoId      = Some(0))),
    beatBytes = beatBytes)))

  lazy val module = new LazyModuleImp(this) {
    withReset(reset.asAsyncReset){

    val contents = contentsDelayed
    val wrapSize = 1 << log2Ceil(contents.size)
    require (wrapSize <= size)

    val (in, edge) = node.in(0)

    val bootrom = Module(new IntenscaleBootRom(log2Ceil(contents.size)-log2Ceil(beatBytes),size,contentsDelayed,beatBytes))
    bootrom.io.clock := clock
    dontTouch(bootrom.io)

    val valid_reg     = RegInit(Bool(false))
    val validNext_reg = RegInit(Bool(false))
    val addr_reg    = Reg(UInt(width = in.a.bits.address.getWidth))
    val source_reg  = Reg(UInt(width = in.a.bits.source.getWidth))

    bootrom.io.ceb := !valid_reg

    when(in.a.valid){
      assert(in.a.bits.size < 4.U, "bootrom can only do 64 bits at a time, no more no less")
      assert(in.a.bits.opcode === 4.U, "bootrom can only do read requests")
    }

    in.d.valid := validNext_reg
    in.a.ready := !valid_reg

    when(in.a.valid && in.a.ready){
      valid_reg   := true.B
      addr_reg    := in.a.bits.address
      source_reg  := in.a.bits.source
    }
    when(valid_reg){
      validNext_reg  := true.B
    }

    when(validNext_reg && in.d.ready){
      validNext_reg  := false.B
      valid_reg      := false.B
    }
    val index = addr_reg(log2Ceil(wrapSize)-1,log2Ceil(beatBytes))
    bootrom.io.addr := index

    val high = if (wrapSize == size) UInt(0) else addr_reg(log2Ceil(size)-1, log2Ceil(wrapSize))
    in.d.bits.data := Mux(high.orR, UInt(0), bootrom.io.data)
    in.d.bits.source := source_reg
    in.d.bits.size := 3.U

    in.d.bits.opcode := 1.U

    // Tie off unused channels
    in.b.valid := Bool(false)
    in.c.ready := Bool(true)
    in.e.ready := Bool(true)
    }
  }
}
class IntenscaleBootRom(val romsize: Int, val size: Int,contentsDelayed: => Seq[Byte],beatBytes: Int) extends Module  with RequireAsyncReset{
    val io = new Bundle {
      val clock = Input(Clock())
      val data = Output(UInt((8*beatBytes).W))
      val addr = Input(UInt(romsize.W))
      val ceb  = Input(UInt(1.W))
  }    
  dontTouch(io)
  val contents = contentsDelayed
  val wrapSize = 1 << log2Ceil(contents.size)
  require (wrapSize <= size)
  val words = (contents ++ Seq.fill(wrapSize-contents.size)(0.toByte)).grouped(beatBytes).toSeq
  val bigs = words.map(_.foldRight(BigInt(0)){ case (x,y) => (x.toInt & 0xff) | y << 8})
  
  val fileName = "bootrom.hex"
  val srcFile = new File(fileName)
  if (srcFile.exists()){
    srcFile.delete()
  }
  val fw = new FileWriter(srcFile, true)
  val rom = Vec(bigs.map(x => UInt(x, width = 8*beatBytes)))
  io.data := rom(io.addr)

  var addr = 0
  var part = 0
  var str_part = 0
  for(x <- bigs){
    if (part == 0){
      str_part = x.toInt 
      part = 1
    }
    else{
      part = 0
      var str = x.toInt
      fw.write(f"${addr}%04x"+" : ")
      fw.write(f"${str}%08x"+f"${str_part}%08x"+"\n")
      addr = addr + 1
    }
  }
  fw.close()

}
/** Adds a boot ROM that contains the DTB describing the system's subsystem. */
trait HasPeripheryBootROM { this: BaseSubsystem =>
  val dtb: DTB
  private val params = p(BootROMParams)
  private lazy val contents = {
    val romdata = Files.readAllBytes(Paths.get(params.contentFileName))
    val rom = ByteBuffer.wrap(romdata)
    rom.array() ++ dtb.contents
  }
  def resetVector: BigInt = params.hang

  val bootrom = LazyModule(new TLROM(params.address, params.size, contents, true, cbus.beatBytes))

  bootrom.node := cbus.coupleTo("bootrom"){ TLFragmenter(cbus) := _ }
}

/** Subsystem will power-on running at 0x10040 (BootROM) */
trait HasPeripheryBootROMModuleImp extends LazyModuleImp
    with HasResetVectorWire {
  val outer: HasPeripheryBootROM
  global_reset_vector := outer.resetVector.U
}
