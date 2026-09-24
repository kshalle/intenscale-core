// See LICENSE.SiFive for license details.

package freechips.rocketchip.subsystem

import Chisel._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.HasLogicalTreeNode
import freechips.rocketchip.diplomaticobjectmodel.logicaltree._
import freechips.rocketchip.prci._
import freechips.rocketchip.tilelink.TLBusWrapper
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._


//This where SystemBus gets built -- in roundabout way.  BuildSystemBus is a parameter.
// the "new SystemBus" is what creates the system bus.  The constructor takes both p and also systemBus specific params that are extracted via SystemBusKey
// BuildSystemBus is then used below to create a lazy system bus module, with BuildSystemBus as a param, that in turn holds the constructed SystemBus object
// SystemBus object isn't a physical bus, but is rather an object used to negotiate sizes and add TL connections -- the physical bus is the collection of connections.

case object SubsystemDriveAsyncClockGroupsKey extends Field[Option[ClockGroupDriverParameters]](Some(ClockGroupDriverParameters(1)))
case object AsyncClockGroupsKey extends Field[ClockGroupEphemeralNode](ClockGroupEphemeralNode()(ValName("async_clock_groups")))
case class TLNetworkTopologyLocated(where: HierarchicalLocation) extends Field[Seq[CanInstantiateWithinContextThatHasTileLinkLocations with CanConnectWithinContextThatHasTileLinkLocations]]
case class TLManagerViewpointLocated(where: HierarchicalLocation) extends Field[Location[TLBusWrapper]](SBUS)

class HierarchicalLocation(override val name: String) extends Location[LazyScope](name)
case object InTile extends HierarchicalLocation("InTile")
case object InSubsystem extends HierarchicalLocation("InSubsystem")
case object InSystem extends HierarchicalLocation("InSystem")

/** BareSubsystem is the root class for creating a subsystem */
// This creates the device tree, the GraphML and JSON of the MMIO register addresses
// This is the outer twin, but it's abstract, so doesn't have the normal instantiation of the inner twin.  
//   There is long chain of inheritance and then the inner twin is created inside RocketSubsystem.scala
abstract class BareSubsystem(implicit p: Parameters) extends LazyModule with BindingScope with HasIntenParameters {
  lazy val dts = DTS(bindingTree)
  lazy val dtb = DTB(dts)
  lazy val json = JSON(bindingTree)
}

// Cake pattern -- this constructs the actual subsystem
abstract class BareSubsystemModuleImp[+L <: BareSubsystem](_outer: L) extends LazyModuleImp(_outer) {
  val outer = _outer
  ElaborationArtefacts.add("graphml", outer.graphML)
  ElaborationArtefacts.add("dts", outer.dts)
  ElaborationArtefacts.add("json", outer.json)
  ElaborationArtefacts.add("plusArgs", PlusArgArtefacts.serialize_cHeader)
  println(outer.dts)
}

trait SubsystemResetScheme
case object ResetSynchronous extends SubsystemResetScheme
case object ResetAsynchronous extends SubsystemResetScheme
case object ResetAsynchronousFull extends SubsystemResetScheme

case object SubsystemResetSchemeKey extends Field[SubsystemResetScheme](ResetSynchronous)

/** Concrete attachment points for PRCI-related signals.
  * These aren't actually very configurable, yet.
  */
trait HasConfigurablePRCILocations { this: HasPRCILocations =>
  val ibus = new InterruptBusWrapper()
  implicit val asyncClockGroupsNode = p(AsyncClockGroupsKey)
  val async_clock_groups =
    p(SubsystemDriveAsyncClockGroupsKey)
      .map(_.drive(asyncClockGroupsNode))
      .getOrElse(InModuleBody { HeterogeneousBag[ClockGroupBundle](Nil) })
}

/** Look up the topology configuration for the TL buses located within this layer of the hierarchy */
trait HasConfigurableTLNetworkTopology { this: HasTileLinkLocations =>
  val location: HierarchicalLocation

  // Calling these functions populates tlBusWrapperLocationMap and connects the locations to each other.
  val topology = p(TLNetworkTopologyLocated(location))
  topology.map(_.instantiate(this))
  topology.foreach(_.connect(this))

  // This is used lazily at DTS binding time to get a view of the network
  // Since we have separated the cachable from MMIO, have to use both SBUS and MSBU as top managers.
  //lazy val topManagers = tlBusWrapperLocationMap(p(TLManagerViewpointLocated(location))).unifyManagers
  lazy val topManagers = ManagerUnification(tlBusWrapperLocationMap(SBUS).busView.manager.managers ++ tlBusWrapperLocationMap(MBUS).busView.manager.managers)
}

/** Base Subsystem class with no peripheral devices, ports or cores added yet */
abstract class BaseSubsystem(val location: HierarchicalLocation = InSubsystem)
                            (implicit p: Parameters)
  extends BareSubsystem
  with Attachable
  with HasConfigurablePRCILocations
  with HasConfigurableTLNetworkTopology
{
//================
//= Busses
//================

//Originally, rocket-chip had only one system-wide bus that everyting connetected to. It has sub-busses, including control bus and peripheral bus.
// However, Intensivate needs multiple L2 caches and a common L3 cache.  This is incompatible with a single system wide bus.
// Also, for layout reasons, system bus will be slow.

  override val module: BaseSubsystemModuleImp[BaseSubsystem]

  // TODO must there really always be an "sbus"?
  val sbus = tlBusWrapperLocationMap(SBUS)
  tlBusWrapperLocationMap.lift(SBUS).map { _.clockGroupNode := asyncClockGroupsNode }

  val IntenParam = new IntenParamC()(p)

  // TODO deprecate these public members to see where users are manually hardcoding a particular bus that might actually not exist in a certain dynamic topology
  val pbus = tlBusWrapperLocationMap.lift(PBUS).getOrElse(sbus)
  val fbus = tlBusWrapperLocationMap.lift(FBUS).getOrElse(sbus)
  val mbus = tlBusWrapperLocationMap.lift(MBUS).getOrElse(sbus)
  val cbus = tlBusWrapperLocationMap.lift(CBUS).getOrElse(sbus)
  val Coherencebus = tlBusWrapperLocationMap.lift(L2).getOrElse(sbus)
  
  val l2l3xbar    = 0 to (IntenParam.NUM_L2BANKS) - 1 map {x => LazyModule(new TLXbar())}
  val BSysPCIeXBar  = LazyModule(new TLXbar)
  val BeToCtxt    = LazyModule(new TLXbar())
  
  // Connect the clock to L2 bus.
  tlBusWrapperLocationMap.lift(L2).map { _.clockGroupNode := asyncMux(NoCrossing, asyncClockGroupsNode, sbus.clockGroupNode) }

  // Collect information for use in DTS
  ResourceBinding {
    val managers = topManagers
    val max = managers.flatMap(_.address).map(_.max).max
    val width = ResourceInt((log2Ceil(max)+31) / 32)
    val model = p(DTSModel)
    val compat = p(DTSCompat)
    val devCompat = (model +: compat).map(s => ResourceString(s + "-dev"))
    val socCompat = (model +: compat).map(s => ResourceString(s + "-soc"))
    devCompat.foreach { Resource(ResourceAnchors.root, "compat").bind(_) }
    socCompat.foreach { Resource(ResourceAnchors.soc,  "compat").bind(_) }
    Resource(ResourceAnchors.root, "model").bind(ResourceString(model))
    Resource(ResourceAnchors.root, "width").bind(width)
    Resource(ResourceAnchors.soc,  "width").bind(width)
    Resource(ResourceAnchors.cpus, "width").bind(ResourceInt(1))

    managers.foreach { case manager =>
      val value = manager.toResource
      manager.resources.foreach { case resource =>
        resource.bind(value)
      }
    }
  }

  lazy val logicalTreeNode = new SubsystemLogicalTreeNode()

  tlBusWrapperLocationMap.values.foreach { bus =>
    val builtIn = bus.builtInDevices
    builtIn.errorOpt.foreach { error =>
      LogicalModuleTree.add(logicalTreeNode, error.logicalTreeNode)
    }
    builtIn.zeroOpt.foreach { zero =>
      LogicalModuleTree.add(logicalTreeNode, zero.logicalTreeNode)
    }
  }
}

abstract class BaseSubsystemModuleImp[+L <: BaseSubsystem](_outer: L) extends BareSubsystemModuleImp(_outer) {
  private val mapping: Seq[AddressMapEntry] = Annotated.addressMapping(this, {
    outer.collectResourceAddresses.groupBy(_._2).toList.flatMap { case (key, seq) =>
      AddressRange.fromSets(key.address).map { r => AddressMapEntry(r, key.permissions, seq.map(_._1)) }
    }.sortBy(_.range)
  })

  Annotated.addressMapping(this, mapping)

  println("Generated Address Map")
  mapping.map(entry => {
    println(entry.toString((outer.sbus.busView.bundle.addressBits-1)/4 + 1))

    // Make sure that the size for each range is bigger than the page size. This is required to accomedate the TLB implmentation that do not consider
    // the lower bits on check the address validaty. See mpu_physaddrM in TLB.scala.
    assert(entry.range.size >= BigInt(1<<12))
  })
  println("")

  ElaborationArtefacts.add("memmap.json", s"""{"mapping":[${mapping.map(_.toJSON).mkString(",")}]}""")

  // Confirm that all of memory was described by DTS
  private val dtsRanges = AddressRange.unify(mapping.map(_.range))
  private val allRanges = AddressRange.unify(outer.topManagers.flatMap { m => AddressRange.fromSets(m.address) })

  if (dtsRanges != allRanges) {
    println("Address map described by DTS differs from physical implementation:")
    AddressRange.subtract(allRanges, dtsRanges).foreach { case r =>
      println(s"\texists, but undescribed by DTS: ${r}")
    }
    AddressRange.subtract(dtsRanges, allRanges).foreach { case r =>
      println(s"\tdoes not exist, but described by DTS: ${r}")
    }
    println("")
  }
}
