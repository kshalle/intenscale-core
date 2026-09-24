/***************************************************************************************
* Copyright (c) 2020-2023 Institute of Computing Technology, Chinese Academy of Sciences
* Copyright (c) 2020-2021 Peng Cheng Laboratory
*
* DiffTest is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include "ram.h"
#include "common.h"
#include "compress.h"
#include "elfloader.h"
#include <iostream>
#include <sys/mman.h>
#include <fstream>
#include <mutex>
#include <chrono>
#include <iomanip>
#include <thread>
#include <cstdlib>
#ifdef CONFIG_DIFFTEST_PERFCNT
#include "perf.h"
#endif // CONFIG_DIFFTEST_PERFCNT

// #define TLB_UNITTEST
// #define WITH_DRAMSIM3
//#define ENABLE_RAM_LOGGING

#ifdef WITH_DRAMSIM3
#include "cosimulation.h"
CoDRAMsim3 *dram = NULL;
#endif

SimMemory *simMemory = nullptr;

// Optional lightweight logger for RAM accesses. Enable with -DENABLE_RAM_LOGGING
struct RamLogger {
  std::ofstream file;
  std::mutex m;
  RamLogger() {
    const char *path = std::getenv("RAM_LOG_PATH");
    if (!path) path = "ram_rw_rocket-chip.log";
    file.open(path, std::ios::out | std::ios::app);
    if (!file.is_open()) {
      std::cerr << "Cannot open RAM log file: " << path << std::endl;
    }
  }

  void log_access(const char *op, uint64_t index, uint64_t addr, uint64_t oldv, uint64_t newv, uint64_t mask) {
    if (!file.is_open()) return;
    auto now = std::chrono::system_clock::now();
    auto us = std::chrono::duration_cast<std::chrono::microseconds>(now.time_since_epoch()).count();
    std::lock_guard<std::mutex> lk(m);
    file << us << " " << std::this_thread::get_id() << " " << op
         << " idx=0x" << std::hex << index
         << " addr=0x" << std::hex << addr
         << " old=0x" << std::setw(16) << std::setfill('0') << oldv
         << " new=0x" << std::setw(16) << std::setfill('0') << newv
         << " mask=0x" << std::setw(16) << std::setfill('0') << mask
         << std::dec << std::setfill(' ') << "\n";
    file.flush();
  }
};

static RamLogger &get_ram_logger() {
  static RamLogger logger;
  return logger;
}

void init_ram(const char *image, uint64_t ram_size) {
  simMemory = new MmapMemory(image, ram_size);
}

SimMemory::~SimMemory() {}

bool SimMemory::is_stdin(const char *image) {
  return !strcmp(image, "-");
}

// Read memory image from the standard input.
// The stdin is formatted as { total_bytes: uint64_t, bytes: uint8_t[] }
StdinReader::StdinReader() : n_bytes(next()) {}

uint64_t StdinReader::next() {
  uint64_t value;
  std::cin.read(reinterpret_cast<char *>(&value), sizeof(uint64_t));
  if (std::cin.fail()) {
    return 0;
  }
  return value;
}

uint64_t StdinReader::read_all(void *dest, uint64_t max_bytes) {
  uint64_t n_read = n_bytes;
  if (n_read >= max_bytes) {
    n_read = max_bytes;
  }
  std::cin.get((char *)dest, n_read);
  n_bytes -= n_read;
  return n_read;
}

// wim@[base_addr],[wim_size]
uint64_t *SimMemory::is_wim(const char *image, uint64_t &wim_size) {
  const char wim_prefix[] = "wim";
  const char *wim_info = strchr(image, '@');
  if (!wim_info || strncmp(wim_prefix, image, sizeof(wim_prefix) - 1)) {
    return nullptr;
  }
  wim_info++;
  uint64_t base_addr = strtoul(wim_info, (char **)&wim_info, 16);
  if (base_addr % sizeof(uint64_t) || *wim_info != '+') {
    return nullptr;
  }
  wim_info++;
  wim_size = strtoul(wim_info, (char **)&wim_info, 16);
  if (*wim_info) {
    return nullptr;
  }
  return (uint64_t *)base_addr;
}

uint64_t WimReader::next() {
  if (index + sizeof(uint64_t) > size) {
    return 0;
  }
  uint64_t value = base_addr[index / sizeof(uint64_t)];
  index += sizeof(uint64_t);
  return value;
}

uint64_t WimReader::read_all(void *dest, uint64_t max_bytes) {
  uint64_t n_read = size - index;
  if (n_read >= max_bytes) {
    n_read = max_bytes;
  }
  memcpy(dest, base_addr, n_read);
  index += n_read;
  return n_read;
}

FileReader::FileReader(const char *filename) : file(filename, std::ios::binary) {
  if (!file.is_open()) {
    std::cerr << "Cannot open '" << filename << "'\n";
    assert(0);
  }

  // Get the size of the file
  file.seekg(0, std::ios::end);
  file_size = file.tellg();
  file.seekg(0, std::ios::beg);
}

uint64_t FileReader::next() {
  if (!file.eof()) {
    uint64_t value;
    file.read(reinterpret_cast<char *>(&value), sizeof(uint64_t));
    if (!file.fail()) {
      return value;
    }
  }
  return 0;
}

uint64_t FileReader::read_all(void *dest, uint64_t max_bytes) {
  uint64_t read_size = (file_size > max_bytes) ? max_bytes : file_size;
  file.read(static_cast<char *>(dest), read_size);
  return read_size;
}

InputReader *SimMemory::createInputReader(const char *image) {
  if (is_stdin(image)) {
    return new StdinReader();
  }
  uint64_t n_bytes;
  if (uint64_t *ptr = is_wim(image, n_bytes)) {
    return new WimReader(ptr, n_bytes);
  }
  return new FileReader(image);
}

void SimMemory::display_stats() {
#ifdef FUZZING
  uint64_t req_in_range = 0;
  auto const img_indices = get_img_size() / sizeof(uint64_t);
  for (auto index: accessed_indices) {
    if (index < img_indices) {
      req_in_range++;
    }
  }
  auto req_all = accessed_indices.size();
  printf("SimMemory: img_size %lu, req_all %lu, req_in_range %lu\n", img_indices, req_all, req_in_range);
#endif // FUZZING
}

MmapMemory::MmapMemory(const char *image, uint64_t n_bytes) : SimMemory(n_bytes) {
  // initialize memory using Linux mmap
  ram = (uint64_t *)mmap(NULL, memory_size, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE | MAP_NORESERVE, -1, 0);
  if (ram == (uint64_t *)MAP_FAILED) {
    printf("Warning: Insufficient phisical memory\n");
    memory_size = 128 * 1024 * 1024UL;
    ram = (uint64_t *)mmap(NULL, memory_size, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0);
    if (ram == (uint64_t *)MAP_FAILED) {
      printf("Error: Cound not mmap 0x%lx bytes\n", memory_size);
      assert(0);
    }
  }
  printf("Using simulated %luMB RAM\n", memory_size / (1024 * 1024));

#ifdef TLB_UNITTEST
  //new add
  addpageSv39();
  //new end
#endif

  if (image == NULL) {
    img_size = 0;
    return;
  }

  printf("The image is %s\n", image);
  if (isGzFile(image)) {
    printf("Gzip file detected and loading image from extracted gz file\n");
    img_size = readFromGz(ram, image, memory_size, LOAD_RAM);
    assert(img_size >= 0);
  } else if (isZstdFile(image)) {
    printf("Zstd file detected and loading image from extracted zstd file\n");
    img_size = readFromZstd(ram, image, memory_size, LOAD_RAM);
    assert(img_size >= 0);
  } else if (isElfFile(image)) {
    printf("ELF file detected and loading image from extracted elf file\n");
    img_size = readFromElf(ram, image, memory_size);
    assert(img_size >= 0);
  } else {
    InputReader *reader = createInputReader(image);
    img_size = reader->read_all(ram, memory_size);
    delete reader;
  }
}

MmapMemory::~MmapMemory() {
  munmap(ram, memory_size);
#ifdef WITH_DRAMSIM3
  dramsim3_finish();
#endif
}

extern "C" long long difftest_ram_read(long long rIdx) {
#ifdef CONFIG_DIFFTEST_PERFCNT
  difftest_calls[perf_difftest_ram_read]++;
  difftest_bytes[perf_difftest_ram_read] += 8;
#endif // CONFIG_DIFFTEST_PERFCNT
  if (!simMemory)
    return 0;
#ifdef PMEM_CHECK
  if (!simMemory->in_range_u64(rIdx)) {
    printf("ERROR: ram rIdx = 0x%lx out of bound!\n", rIdx);
    return 0;
  }
#endif // PMEM_CHECK
  rIdx %= simMemory->get_size() / sizeof(uint64_t);
  uint64_t rdata = simMemory->at(rIdx);
#ifdef ENABLE_RAM_LOGGING
  {
    uint64_t addr = (uint64_t)rIdx * sizeof(uint64_t) + PMEM_BASE;
    get_ram_logger().log_access("R", (uint64_t)rIdx, addr, rdata, rdata, 0);
  }
#endif
  return rdata;
}

extern "C" void difftest_ram_write(long long wIdx, long long wdata, long long wmask) {
#ifdef CONFIG_DIFFTEST_PERFCNT
  difftest_calls[perf_difftest_ram_write]++;
  difftest_bytes[perf_difftest_ram_write] += 24;
#endif // CONFIG_DIFFTEST_PERFCNT
  if (simMemory) {
    if (!simMemory->in_range_u64(wIdx)) {
      printf("ERROR: ram wIdx = 0x%lx out of bound!\n", wIdx);
      return;
    }
#ifdef ENABLE_RAM_LOGGING
    {
      uint64_t addr = (uint64_t)wIdx * sizeof(uint64_t) + PMEM_BASE;
      uint64_t oldv = simMemory->at(wIdx);
      uint64_t newv = (oldv & ~wmask) | ((uint64_t)wdata & (uint64_t)wmask);
      get_ram_logger().log_access("W", (uint64_t)wIdx, addr, oldv, newv, (uint64_t)wmask);
      simMemory->at(wIdx) = newv;
    }
#else
    simMemory->at(wIdx) = (simMemory->at(wIdx) & ~wmask) | (wdata & wmask);
#endif
  }
}

uint64_t pmem_read(uint64_t raddr) {
  if (raddr % sizeof(uint64_t)) {
    printf("Warning: pmem_read only supports 64-bit aligned memory access\n");
  }
  raddr -= PMEM_BASE;
  return difftest_ram_read(raddr / sizeof(uint64_t));
}

void pmem_write(uint64_t waddr, uint64_t wdata) {
  if (waddr % sizeof(uint64_t)) {
    printf("Warning: pmem_write only supports 64-bit aligned memory access\n");
  }
  waddr -= PMEM_BASE;
  return difftest_ram_write(waddr / sizeof(uint64_t), wdata, -1UL);
}

MmapMemoryWithFootprints::MmapMemoryWithFootprints(const char *image, uint64_t n_bytes, const char *footprints_name)
    : MmapMemory(image, n_bytes) {
  uint64_t n_touched = memory_size / sizeof(uint64_t);
  touched = (uint8_t *)mmap(NULL, n_touched, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0);
  footprints_file.open(footprints_name, std::ios::binary);
  if (!footprints_file.is_open()) {
    printf("Cannot open %s as the footprints file\n", footprints_name);
    assert(0);
  }
}

MmapMemoryWithFootprints::~MmapMemoryWithFootprints() {
  munmap(touched, memory_size / sizeof(uint64_t));
  footprints_file.close();
}

uint64_t &MmapMemoryWithFootprints::at(uint64_t index) {
  uint64_t &data = MmapMemory::at(index);
  if (!touched[index]) {
    footprints_file.write(reinterpret_cast<const char *>(&data), sizeof(data));
    touched[index] = 1;
  }
  return data;
}

FootprintsMemory::FootprintsMemory(const char *footprints_name, uint64_t n_bytes)
    : SimMemory(n_bytes), reader(createInputReader(footprints_name)), n_accessed(0) {
  printf("The image is %s\n", footprints_name);
  add_callback([this](uint64_t, uint64_t) { this->on_access(this->n_accessed / sizeof(uint64_t)); });
}

FootprintsMemory::~FootprintsMemory() {
  delete reader;
}

uint64_t &FootprintsMemory::at(uint64_t index) {
  if (ram.find(index) == ram.end()) {
    uint64_t value = reader->next();
    ram[index] = value;
    for (auto &cb: callbacks) {
      cb(index, value);
    }
    n_accessed += sizeof(uint64_t);
  }
  return ram[index];
}

LinearizedFootprintsMemory::LinearizedFootprintsMemory(const char *footprints_name, uint64_t n_bytes,
                                                       const char *linear_name)
    : FootprintsMemory(footprints_name, n_bytes), linear_name(linear_name), n_touched(0) {
  linear_memory = (uint64_t *)mmap(nullptr, n_bytes, PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
  if (linear_memory == MAP_FAILED) {
    perror("mmap");
    exit(EXIT_FAILURE);
  }
  add_callback([this](uint64_t index, uint64_t value) {
    if (value) {
      linear_memory[index] = value;
      n_touched++;
    }
  });
}

LinearizedFootprintsMemory::~LinearizedFootprintsMemory() {
  save_linear_memory(linear_name);
  munmap(linear_memory, get_size());
}

void LinearizedFootprintsMemory::save_linear_memory(const char *filename) {
  std::ofstream out_file(filename, std::ios::out | std::ios::binary);
  if (!out_file) {
    std::cerr << "Cannot open output file: " << filename << std::endl;
    return;
  }
  // Find the position of the last non-zero element
  uint64_t last_nonzero_index = 0, nonzero_count = 0;
  for (uint64_t i = 0; i < get_size() / sizeof(uint64_t) && nonzero_count < n_touched; ++i) {
    if (linear_memory[i] != 0) {
      last_nonzero_index = i;
      nonzero_count++;
    }
  }
  // Even if all all zeros, we still write one uint64_t.
  size_t n_bytes = (last_nonzero_index + 1) * sizeof(uint64_t);
  out_file.write(reinterpret_cast<char *>(linear_memory), n_bytes);
  out_file.close();
}

void overwrite_ram(const char *gcpt_restore, uint64_t overwrite_nbytes) {
  InputReader *reader = new FileReader(gcpt_restore);
  int overwrite_size = reader->read_all(simMemory->as_ptr(), overwrite_nbytes);
  printf("Overwrite %d bytes from file %s.\n", overwrite_size, gcpt_restore);
  delete reader;
}

#ifdef WITH_DRAMSIM3
void dramsim3_init(const char *config_file) {
#if !defined(DRAMSIM3_CONFIG) || !defined(DRAMSIM3_OUTDIR)
#error DRAMSIM3_CONFIG or DRAMSIM3_OUTDIR is not defined
#endif

  config_file = (config_file == nullptr) ? DRAMSIM3_CONFIG : config_file;

  assert(dram == NULL);
  // check config_file is valid
  std::ifstream ifs(config_file);
  if (!ifs) {
    std::cerr << "Cannot open DRAMSIM3 config file: " << config_file << std::endl;
    exit(1);
  }
  ifs.close();

  std::cout << "DRAMSIM3 config: " << config_file << std::endl;
  dram = new ComplexCoDRAMsim3(config_file, DRAMSIM3_OUTDIR);
  // dram = new SimpleCoDRAMsim3(90);
}

// dramsim3_step() is called unconditionally on every raw emulator tick (see emulator.cc's main
// loop), including during the reset bounce at the very start of simulation (emulator.cc
// deliberately toggles reset 0/1/0/1 for a few raw ticks, so AsyncResetRegs see a real edge,
// before it settles into a sustained assertion for a further ~150 ticks). Before that settles,
// RANDOMIZE_GARBAGE_ASSIGN means the Verilog side of the bridge (in.ar/in.aw in AXI4MemoryImp)
// can carry genuine garbage rather than a clean 0 for a little while. If that garbage happens to
// look like a valid request, DRAMSim3 can accept it, and since nothing will ever generate a real
// completion for that exact garbage address, it sits in DRAMSim3's own request tracking forever
// -- until some later, real transaction's completion happens to alias with it (DRAMSim3's
// internal address decoding almost certainly discards/wraps whatever high bits it doesn't need
// for its configured size), which is when it actually surfaces as corruption.
//
// A Verilog-side guard for this doesn't work: it would need a register that's trustworthy before
// its own reset has ever touched it, and RANDOMIZE_GARBAGE_ASSIGN means no Verilog register can
// promise that (its RegInit-forced value only actually applies starting on the first clock edge
// where reset is asserted -- at simulation time 0, before that has happened even once, a
// RegInit register's displayed value is still raw, uncontrolled garbage same as anything else).
// A plain C++ static, on the other hand, is reliably zero-initialized with no such window, so
// gate acceptance here instead: refuse every request until comfortably past the point
// emulator.cc's own reset schedule (async_reset_cycles*2 + sync_reset_cycles, currently 2*2+150
// = 154 raw ticks) has settled for good.
static uint64_t dram_step_count = 0;
static const uint64_t DRAM_RESET_SETTLE_TICKS = 200;

void dramsim3_step() {
  if (dram == NULL)
    return;
  dram_step_count++;
  dram->tick();
}

void dramsim3_finish() {
  delete dram;
  dram = NULL;
}

// DRAMSim3's own callback API (see DRAMSIM3/src/memory_system.h) only ever reports back
// (address, is_write) for a completed transaction -- no unique per-transaction tag. Its cosim
// wrapper (DRAMSIM3/src/cosimulation.cc, ComplexCoDRAMsim3::callback) matches a completion to the
// oldest still-pending request with that same (address, is_write), which is only correct if two
// requests of the same type are never simultaneously outstanding to the same address: if they
// are, a completion can get paired with the wrong one, silently handing back a different
// request's tag (id) than the one that actually finished. Rather than touch the DRAMSim3 model
// (which has no way to disambiguate this itself), refuse to send a second request to an address
// that already has one of the same type in flight -- the existing retry logic in
// AXI4Memory.scala's AXI4MemoryImp already resends an unaccepted request every cycle with its
// (latched, unchanged) original address until this returns true, so this needs no Chisel-side
// change: it just makes memory_request() report "not yet" for a while longer in this one case.
static std::set<uint64_t> read_addrs_outstanding;
static std::set<uint64_t> write_addrs_outstanding;

extern "C" long long memory_response(unsigned char isWrite) {
  if (dram == NULL)
    return 0;
  auto response = (isWrite) ? dram->check_write_response() : dram->check_read_response();
  //printf("receive %s response from DRAMsim3\n", isWrite ? "write" : "read");
  if (response) {
    //printf("response address: 0x%lx\n", response->req->address);
    auto &outstanding = isWrite ? write_addrs_outstanding : read_addrs_outstanding;
    outstanding.erase((uint64_t)response->req->address);
    auto meta = static_cast<dramsim3_meta *>(response->req->meta);
    uint64_t response_value = meta->id | (1UL << 32);
    delete meta;
    delete response;
    return response_value;
  }
  return 0;
}

extern "C" unsigned char memory_request(long long address, int id, unsigned char isWrite) {
  if (dram == NULL)
    return false;
  if (dram_step_count < DRAM_RESET_SETTLE_TICKS)
    return false;
  auto &outstanding = isWrite ? write_addrs_outstanding : read_addrs_outstanding;
  if (outstanding.count((uint64_t)address))
    return false;
  if (dram->will_accept(address, isWrite)) {
    auto req = new CoDRAMRequest();
    auto meta = new dramsim3_meta;
    req->address = address;
    req->is_write = isWrite;
    meta->id = id;
    req->meta = meta;
    dram->add_request(req);
    outstanding.insert((uint64_t)address);
    //printf("send %s request with addr 0x%lx to DRAMsim3, id=%d\n",
    //       isWrite ? "write" : "read", address, id);
    return true;
  }
  return false;
}

#endif
