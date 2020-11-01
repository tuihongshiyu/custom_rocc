// See LICENSE.SiFive for license details.

package freechips.rocketchip.subsystem

import Chisel._
import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

case class SystemBusParams(
    beatBytes: Int,
    blockBytes: Int,
    policy: TLArbiter.Policy = TLArbiter.roundRobin,
    dtsFrequency: Option[BigInt] = None,
    zeroDevice: Option[AddressSet] = None,
    errorDevice: Option[DevNullParams] = None,
    replication: Option[ReplicatedRegion] = None)
  extends HasTLBusParams
  with HasBuiltInDeviceParams
  with TLBusWrapperInstantiationLike
{
  def instantiate(context: HasTileLinkLocations, loc: Location[TLBusWrapper])(implicit p: Parameters): SystemBus = {
    val sbus = LazyModule(new SystemBus(this, loc.name))
    sbus.suggestName(loc.name)
    context.tlBusWrapperLocationMap += (loc -> sbus)
    sbus
  }
}

class SystemBus(params: SystemBusParams, name: String = "system_bus")(implicit p: Parameters)
    extends TLBusWrapper(params, name)
{
  private val replicator = params.replication.map(r => LazyModule(new RegionReplicator(r)))
  val prefixNode = replicator.map(_.prefix)

  private val system_bus_xbar = LazyModule(new TLXbar(policy = params.policy))
  val inwardNode: TLInwardNode = system_bus_xbar.node :=* TLFIFOFixer(TLFIFOFixer.allVolatile) :=* replicator.map(_.node).getOrElse(TLTempNode())
  val outwardNode: TLOutwardNode = system_bus_xbar.node
  def busView: TLEdge = system_bus_xbar.node.edges.in.head

  val builtInDevices: BuiltInDevices = BuiltInDevices.attach(params, outwardNode)
}
