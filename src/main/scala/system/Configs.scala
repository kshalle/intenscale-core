// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package freechips.rocketchip.system

import Chisel._

import freechips.rocketchip.config.Config
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.constants.StConfiguration._

class WithJtagDTMSystem extends freechips.rocketchip.subsystem.WithJtagDTM
class WithDebugSBASystem extends freechips.rocketchip.subsystem.WithDebugSBA
class WithDebugAPB extends freechips.rocketchip.subsystem.WithDebugAPB

class BaseConfig extends Config(
  new WithPhysAddrBits() ++
  //new WithDefaultMMIOPort() ++
  new WithDefaultSlavePort() ++
  new WithTimebase(BigInt(1000000)) ++ // 1 MHz
  new WithDTS("freechips,rocketchip-unknown", Nil) ++
  new WithNExtTopInterrupts(2) ++
  new BaseSubsystemConfig()
)

class WithIntenCoreParams(NUM_CTXT: Int, NUM_PUS: Int, NUM_PHY_CORES: Int, NUM_MPS: Int, NUM_PTW_ENTRIES: Int, NUM_PHYS_ADDR_BITS: Int, L1_NUM_MSHR: Int, NUM_L2MACRO:Int, NUM_L2BANKS: Int, L2_WAYS: Int, L2_SETS_ALL_BANKS: Int, NUM_L3BANKS: Int, L3_WAYS: Int, L3_SETS_ALL_BANKS: Int, NUM_MEMORYCHANNELS: Int, HasPCIE: Boolean, HasBSYS: Boolean, BACKEND_ENA: Boolean) extends Config((site, here, up) => {
  case IntenCoreKey => IntenCoreParams(NUM_CTXT, NUM_PUS, NUM_PHY_CORES, NUM_MPS, NUM_PTW_ENTRIES, NUM_PHYS_ADDR_BITS, L1_NUM_MSHR, NUM_L2MACRO, NUM_L2BANKS, L2_WAYS, L2_SETS_ALL_BANKS, NUM_L3BANKS, L3_WAYS, L3_SETS_ALL_BANKS, NUM_MEMORYCHANNELS, HasPCIE, HasBSYS, BACKEND_ENA)
})

// Base configurations.
class BaseConfigN extends Config(new WithNBigCores ++ new WithCoherentBusTopology ++ new BaseConfig ++ new WithoutTLMonitors)

// Note that NUM_L2BANKS and NUM_L3BANKS are chosen so that the bandwidth and capacity per core matches the final product.
// The final product has 4 cores per L2 cache, and 8 banks for L2 cache with 2MBytes capacity.
// For L3, it has 64 banks with 64MBytes capacity, each 16 banks are connected to 1 DRAM controller (4 DRAM controllers in total).
// The cache capacity = NUM_SETS * NUM_WAYS * 512 (in bits).
// The capacity of L2 is 1/8 that of the L3 on the final product.

// Single physical core having 16 ROWs (Default).
// This is used for FPGA performance measurments for one full core.
// The caches are sized to make the performance matches the final product.
class Inten1CoreConfig      extends Config(new BaseConfigN ++
                                           new WithIntenCoreParams(NUM_CTXT        = 8,
                                                                   NUM_PUS         = 2,
                                                                   NUM_PHY_CORES   = 1,
                                                                   NUM_MPS         = 2,
                                                                   NUM_PTW_ENTRIES = 1024,
                                                                   NUM_PHYS_ADDR_BITS = 35,
                                                                   L1_NUM_MSHR     = 16,  // Number of MSHR for each TL connection (NUM_L2BANKS).
                                                                   NUM_L2MACRO     = 1,
                                                                   // 1 core config is for perf measurements, so it tries to match BW of final,
                                                                   // so it only has 2 TL (2 L2 banks) because 4 cores share BW of 8TL, but
                                                                   // still have the full 16 MSHR using the BW of those 2 TL
                                                                   // In ASIC silicon, this param should be 2, to end up with the same 16 MSHR.
                                                                   NUM_L2BANKS     = 2,
                                                                   L2_WAYS         = 8,
                                                                   L2_SETS_ALL_BANKS = 2048,
                                                                   NUM_L3BANKS     = 4,
                                                                   L3_WAYS         = 8,
                                                                   L3_SETS_ALL_BANKS = 8192,
                                                                   NUM_MEMORYCHANNELS = 1,
                                                                   HasPCIE         = false,
                                                                   HasBSYS         = false,
                                                                   BACKEND_ENA     = false))


// Two physical cores, each has 16 ROWs.
// The caches are sized to make the performance matches the final product.
// Due to FPGA limitations, L3 parameters are further down by half.
class Inten2CoreConfig      extends Config(new BaseConfigN ++
                                           new WithIntenCoreParams(NUM_CTXT        = 8,
                                                                   NUM_PUS         = 2,
                                                                   NUM_PHY_CORES   = 2,
                                                                   NUM_MPS         = 2,
                                                                   NUM_PTW_ENTRIES = 1024,
                                                                   NUM_PHYS_ADDR_BITS = 35,
                                                                   L1_NUM_MSHR     = 16, // Number of MSHR for each TL connection (NUM_L2BANKS).
                                                                   NUM_L2MACRO     = 1,
                                                                   NUM_L2BANKS     = 4,
                                                                   L2_WAYS         = 8,
                                                                   L2_SETS_ALL_BANKS = 2048,
                                                                   NUM_L3BANKS     = 4,
                                                                   L3_WAYS         = 4,
                                                                   L3_SETS_ALL_BANKS = 8192,
                                                                   NUM_MEMORYCHANNELS = 1,
                                                                   HasPCIE         = false,
                                                                   HasBSYS         = false,
                                                                   BACKEND_ENA     = false))

class Inten4CoreConfig      extends Config(new BaseConfigN ++
                                           new WithIntenCoreParams(NUM_CTXT        = 8,
                                                                   NUM_PUS         = 2,
                                                                   NUM_PHY_CORES   = 4,
                                                                   NUM_MPS         = 2,
                                                                   NUM_PTW_ENTRIES = 1024,
                                                                   NUM_PHYS_ADDR_BITS = 35,
                                                                   // Must be large enough per bank to sustain 4 cores * 2 PUs * 8 ctxt
                                                                   // = 64 harts' worth of concurrent PTW/cache-fill traffic; too small
                                                                   // a value exhausts MSHR capacity and stalls the design.
                                                                   L1_NUM_MSHR     = 8, // Number of MSHR for each TL connection (NUM_L2BANKS).
                                                                   NUM_L2MACRO     = 1,
                                                                   NUM_L2BANKS     = 8,
                                                                   L2_WAYS         = 8,
                                                                   L2_SETS_ALL_BANKS = 4096,
                                                                   NUM_L3BANKS     = 16,
                                                                   L3_WAYS         = 8,
                                                                   L3_SETS_ALL_BANKS = 32768,
                                                                   NUM_MEMORYCHANNELS = 1,
                                                                   HasPCIE         = false,
                                                                   HasBSYS         = false,
                                                                   BACKEND_ENA     = false))


// Single ROW configuration.
// This is for RTL develpment for fast testing of functionality and not performance.
class Inten1RowConfig       extends Config(new BaseConfigN ++
                                           new WithIntenCoreParams(NUM_CTXT        = 1,
                                                                   NUM_PUS         = 1,
                                                                   NUM_PHY_CORES   = 1,
                                                                   NUM_MPS         = 1,
                                                                   NUM_PTW_ENTRIES = 1024,
                                                                   NUM_PHYS_ADDR_BITS = 34,
                                                                   L1_NUM_MSHR     = 2,
                                                                   NUM_L2MACRO     = 1,
                                                                   NUM_L2BANKS     = 1,
                                                                   L2_WAYS         = 8,
                                                                   L2_SETS_ALL_BANKS = 1024,
                                                                   NUM_L3BANKS     = 1,
                                                                   L3_WAYS         = 8,
                                                                   L3_SETS_ALL_BANKS = 4096,
                                                                   NUM_MEMORYCHANNELS = 1,
                                                                   HasPCIE         = false,
                                                                   HasBSYS         = false,
                                                                   BACKEND_ENA     = false))
