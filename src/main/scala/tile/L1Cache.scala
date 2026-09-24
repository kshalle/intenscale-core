// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import Chisel._

import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.tilelink.ClientMetadata
import freechips.rocketchip.util._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants.Util._

trait L1CacheParams {
  def nSets:         Int
  def nWays:         Int
  def rowBits:       Int
  def nTLBEntries:   Int
  def blockBytes:    Int // TODO this is ignored in favor of p(CacheBlockBytes) in BaseTile
}

trait HasL1CacheParameters extends HasTileParameters {
  val cacheParams: L1CacheParams

  def nSets = cacheParams.nSets
  def blockOffBits = lgCacheBlockBytes
  def idxBits = log2Up(cacheParams.nSets)
  def untagBits = blockOffBits + idxBits
  def pgUntagBits = if (usingVM) untagBits min pgIdxBits else untagBits
  //def tagBits = tlBundleParams.addressBits - pgUntagBits
  def tagBits = tlBundleParams.addressBits - untagBits // We use the physical addresses for both I$ and D$.
  def nWays = cacheParams.nWays
  def wayBits = log2Up(nWays)
  def isDM = nWays == 1
  def rowBits = cacheParams.rowBits
  def rowBytes = rowBits/8
  def rowOffBits = log2Up(rowBytes)
  def nTLBEntries = cacheParams.nTLBEntries

  def cacheDataBits = tlBundleParams.dataBits
  def cacheDataBytes = cacheDataBits / 8
  def cacheDataBeats = (cacheBlockBytes * 8) / cacheDataBits
  def refillCycles = cacheDataBeats

  // Get number of banks in the system.
  def NUM_BANKS_PER_DC     = nWays * NUM_BANKS_PER_WAY / NumDCCmds

  // Define bit range to select the bank.
  def ST_INDX     = blockOffBits + (if(NumDCCmds == 1) 0 else log2Up(NumDCCmds))
  def ST_INDX_INC = ST_INDX + (if(NUM_L2BANKS / NumDCCmds == 1) 0 else log2Up(NUM_L2BANKS / NumDCCmds))
  def EN_INDX     = ST_INDX + log2Up(NUM_BANKS_PER_DC) - 1
}

abstract class L1CacheModule(implicit val p: Parameters) extends Module
  with HasL1CacheParameters

abstract class L1CacheBundle(implicit val p: Parameters) extends Bundle
  with HasL1CacheParameters

