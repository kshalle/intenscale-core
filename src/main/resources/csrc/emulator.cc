// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

#include "verilated.h"
#if VM_TRACE
#include <memory>
#include "verilated_vcd_c.h"
#endif
#include <fesvr/dtm.h>
#include "remote_bitbang.h"
#include "ram.h"
#include <iostream>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <getopt.h>
#include "fesvr/elfloader.h"

// For option parsing, which is split across this file, Verilog, and
// FESVR's HTIF, a few external files must be pulled in. The list of
// files and what they provide is enumerated:
//
// $RISCV/include/fesvr/htif.h:
//   defines:
//     - HTIF_USAGE_OPTIONS
//     - HTIF_LONG_OPTIONS_OPTIND
//     - HTIF_LONG_OPTIONS
// $(ROCKETCHIP_DIR)/generated-src(-debug)?/$(CONFIG).plusArgs:
//   defines:
//     - PLUSARG_USAGE_OPTIONS
//   variables:
//     - static const char * verilog_plusargs

extern dtm_t* dtm;

static dtm_t* preload_dtm;

extern remote_bitbang_t * jtag;

static uint64_t trace_count = 0;
bool verbose;
bool done_reset;

class postload_dtm_t: public dtm_t {
public:
  postload_dtm_t(int argc, char** argv):dtm_t(argc,argv) {}
  bool is_address_preloaded(addr_t taddr, size_t len) override {
    return true;
  }
};

class fastload_memif_t:public chunked_memif_t {
  uint64_t* mem;
  uint64_t base,size;

public:
  fastload_memif_t(uint64_t* _mem,uint64_t _base,uint64_t _size):
    mem(_mem), base(_base), size(_size)
  {}

  virtual void read_chunk(addr_t taddr, size_t len, void* dst) override {
    if(len % 8 || taddr % 8) { fprintf(stderr,"invalid chunked_memif_t chunk len?\n"); abort(); }
    uint64_t* p = mem + ((taddr-base) >> 3);
    uint64_t* q = (uint64_t*)dst;
    for(int i=0;i<len; i += 8) *q++ = *p++;
  }

  virtual void write_chunk(addr_t taddr, size_t len, const void* src) override {
    if(len % 8 || taddr % 8) { fprintf(stderr,"invalid chunked_memif_t chunk len?\n"); abort(); }
    uint64_t* p = (uint64_t*)src;
    uint64_t* q = mem + ((taddr-base) >> 3);
    for(int i=0;i<len; i += 8) *q++ = *p++;
  }

  virtual void clear_chunk(addr_t taddr, size_t len) override {
    if(len % 8 || taddr % 8) { fprintf(stderr,"invalid chunked_memif_t chunk len?\n"); abort(); }
    uint64_t* q = mem + ((taddr-base) >> 3);
    for(int i=0;i<len; i += 8) *q++ = 0;
  }

  virtual size_t chunk_align() override { return 8; }
  virtual size_t chunk_max_size() override { return 16384; };
};

static uint64_t parse_and_update_ramsize(const char *arg_ramsize_str) {
  unsigned long ram_size_value = 0;
  char ram_size_unit[64];
  sscanf(arg_ramsize_str, "%ld%s", &ram_size_value, (char *)&ram_size_unit);
  assert(ram_size_value > 0);

  if (!strcmp(ram_size_unit, "GB") || !strcmp(ram_size_unit, "gb")) {
    return ram_size_value * 1024 * 1024 * 1024;
  }
  if (!strcmp(ram_size_unit, "MB") || !strcmp(ram_size_unit, "mb")) {
    return ram_size_value * 1024 * 1024;
  }
  printf("Invalid ram size %s\n", ram_size_unit);
  return 0;
}

void handle_sigterm(int sig)
{
  dtm->stop();
}

double sc_time_stamp()
{
  return trace_count;
}

extern "C" int vpi_get_vlog_info(void* arg)
{
  return 0;
}

static void usage(const char * program_name)
{
  printf("Usage: %s [EMULATOR OPTION]... [VERILOG PLUSARG]... [HOST OPTION]... BINARY [TARGET OPTION]...\n",
         program_name);
  fputs("\
Run a BINARY on the Rocket Chip emulator.\n\
\n\
Mandatory arguments to long options are mandatory for short options too.\n\
\n\
EMULATOR OPTIONS\n\
  -c, --cycle-count        Print the cycle count before exiting\n\
       +cycle-count\n\
  -h, --help               Display this help and exit\n\
  -m, --max-cycles=CYCLES  Kill the emulation after CYCLES\n\
       +max-cycles=CYCLES\n\
  -s, --seed=SEED          Use random number seed SEED\n\
  -b, --ram-size=SIZE      simulation memory size, for example 8GB / 128MB\n\
  -y, --dramsim3-ini       specify the ini file for DRAMSim3\n\
  -r, --rbb-port=PORT      Use PORT for remote bit bang (with OpenOCD and GDB) \n\
                           If not specified, a random port will be chosen\n\
                           automatically.\n\
  -V, --verbose            Enable all Chisel printfs (cycle-by-cycle info)\n\
       +verbose\n\
", stdout);
#if VM_TRACE == 0
  fputs("\
\n\
EMULATOR DEBUG OPTIONS (only supported in debug build -- try `make debug`)\n",
        stdout);
#endif
  fputs("\
  -v, --vcd=FILE,          Write vcd trace to FILE (or '-' for stdout)\n\
  -x, --dump-start=CYCLE   Start VCD tracing at CYCLE\n\
       +dump-start\n\
", stdout);
  fputs("\n" PLUSARG_USAGE_OPTIONS, stdout);
  fputs("\n" HTIF_USAGE_OPTIONS, stdout);
  printf("\n"
"EXAMPLES\n"
"  - run a bare metal test:\n"
"    %s $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add\n"
"  - run a bare metal test showing cycle-by-cycle information:\n"
"    %s +verbose $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add 2>&1 | spike-dasm\n"
#if VM_TRACE
"  - run a bare metal test to generate a VCD waveform:\n"
"    %s -v rv64ui-p-add.vcd $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-add\n"
#endif
"  - run an ELF (you wrote, called 'hello') using the proxy kernel:\n"
"    %s pk hello\n",
         program_name, program_name, program_name
#if VM_TRACE
         , program_name
#endif
         );
}

int main(int argc, char** argv)
{
  unsigned random_seed = (unsigned)time(NULL) ^ (unsigned)getpid();
  uint64_t max_cycles = -1;
  int ret = 0;
  bool print_cycles = false;
  uint64_t ram_size = DEFAULT_EMU_RAM_SIZE;
  const char* dramsim3_ini = nullptr;
  // Port numbers are 16 bit unsigned integers.
  uint16_t rbb_port = 0;
#if VM_TRACE
  FILE * vcdfile = NULL;
  uint64_t start = 0;
#endif
  char ** htif_argv = NULL;
  int verilog_plusargs_legal = 1;

  while (1) {
    static struct option long_options[] = {
      {"cycle-count", no_argument,       0, 'c' },
      {"help",        no_argument,       0, 'h' },
      {"max-cycles",  required_argument, 0, 'm' },
      {"seed",        required_argument, 0, 's' },
      {"rbb-port",    required_argument, 0, 'r' },
      {"verbose",     no_argument,       0, 'V' },
      {"ram-size",   required_argument, NULL,  0  },
      {"dramsim3-ini", required_argument, NULL,  0  },
#if VM_TRACE
      {"vcd",         required_argument, 0, 'v' },
      {"dump-start",  required_argument, 0, 'x' },
#endif
      HTIF_LONG_OPTIONS
    };
    int option_index = 0;
#if VM_TRACE
    int c = getopt_long(argc, argv, "-chm:s:r:v:Vx:b:y:", long_options, &option_index);
#else
    int c = getopt_long(argc, argv, "-chm:s:r:Vb:y:", long_options, &option_index);
#endif
    if (c == -1) break;
 retry:
    switch (c) {
      // Process long and short EMULATOR options
      case '?': usage(argv[0]);             return 1;
      case 'c': print_cycles = true;        break;
      case 'h': usage(argv[0]);             return 0;
      case 'm': max_cycles = atoll(optarg); break;
      case 's': random_seed = atoi(optarg); break;
      case 'r': rbb_port = atoi(optarg);    break;
      case 'V': verbose = true;             break;
      case 'b': ram_size = parse_and_update_ramsize(optarg); break;
      case 'y': dramsim3_ini = optarg; break;
#if VM_TRACE
      case 'v': {
        vcdfile = strcmp(optarg, "-") == 0 ? stdout : fopen(optarg, "w");
        if (!vcdfile) {
          std::cerr << "Unable to open " << optarg << " for VCD write\n";
          return 1;
        }
        break;
      }
      case 'x': start = atoll(optarg);      break;
#endif
      // Process legacy '+' EMULATOR arguments by replacing them with
      // their getopt equivalents
      case 1: {
        std::string arg = optarg;
        if (arg.substr(0, 1) != "+") {
          optind--;
          goto done_processing;
        }
        if (arg == "+verbose")
          c = 'V';
        else if (arg.substr(0, 12) == "+max-cycles=") {
          c = 'm';
          optarg = optarg+12;
        }
#if VM_TRACE
        else if (arg.substr(0, 12) == "+dump-start=") {
          c = 'x';
          optarg = optarg+12;
        }
#endif
        else if (arg.substr(0, 12) == "+cycle-count")
          c = 'c';
        // If we don't find a legacy '+' EMULATOR argument, it still could be
        // a VERILOG_PLUSARG and not an error.
        else if (verilog_plusargs_legal) {
          const char ** plusarg = &verilog_plusargs[0];
          int legal_verilog_plusarg = 0;
          while (*plusarg && (legal_verilog_plusarg == 0)){
            if (arg.substr(1, strlen(*plusarg)) == *plusarg) {
              legal_verilog_plusarg = 1;
            }
            plusarg ++;
          }
          if (!legal_verilog_plusarg) {
            verilog_plusargs_legal = 0;
          } else {
            c = 'P';
          }
          goto retry;
        }
        // If we STILL don't find a legacy '+' argument, it still could be
        // an HTIF (HOST) argument and not an error. If this is the case, then
        // we're done processing EMULATOR and VERILOG arguments.
        else {
          static struct option htif_long_options [] = { HTIF_LONG_OPTIONS };
          struct option * htif_option = &htif_long_options[0];
          while (htif_option->name) {
            if (arg.substr(1, strlen(htif_option->name)) == htif_option->name) {
              optind--;
              goto done_processing;
            }
            htif_option++;
          }
          std::cerr << argv[0] << ": invalid plus-arg (Verilog or HTIF) \""
                    << arg << "\"\n";
          c = '?';
        }
        goto retry;
      }
      case 'P': break; // Nothing to do here, Verilog PlusArg
      // Realize that we've hit HTIF (HOST) arguments or error out
      default:
        if (c >= HTIF_LONG_OPTIONS_OPTIND) {
          optind--;
          goto done_processing;
        }
        c = '?';
        goto retry;
    }
  }

done_processing:
  if (optind == argc) {
    std::cerr << "No binary specified for emulator\n";
    usage(argv[0]);
    return 1;
  }
  int htif_argc = 1 + argc - optind;
  htif_argv = (char **) malloc((htif_argc) * sizeof (char *));
  htif_argv[0] = argv[0];
  for (int i = 1; optind < argc;) htif_argv[i++] = argv[optind++];

  if (verbose)
    fprintf(stderr, "using random seed %u\n", random_seed);

  srand(random_seed);
  srand48(random_seed);

  Verilated::randReset(2);
  Verilated::commandArgs(argc, argv);
  TEST_HARNESS *tile = new TEST_HARNESS;

#if VM_TRACE
  Verilated::traceEverOn(true); // Verilator must compute traced signals
  std::unique_ptr<VerilatedVcdFILE> vcdfd(new VerilatedVcdFILE(vcdfile));
  std::unique_ptr<VerilatedVcdC> tfp(new VerilatedVcdC(vcdfd.get()));
  if (vcdfile) {
    tile->trace(tfp.get(), 99);  // Trace 99 levels of hierarchy
    tfp->open("");
  }
#endif

  jtag = new remote_bitbang_t(rbb_port);

  // The DRAMSim3-backed memory model stores data behind a DPI-C blackbox
  // (DifftestMem), not as a plain Verilated SRAM array, so it can no longer be
  // backdoor-loaded via a TestHarness__DOT__... hierarchical signal path.
  // Instead, the image is loaded into a host-side memory (ram.cc/SimMemory)
  // that the DPI-C difftest_ram_read/difftest_ram_write calls read and write.
  init_ram(htif_argv[1], ram_size);
#ifdef WITH_DRAMSIM3
  dramsim3_init(dramsim3_ini);
#endif

#ifdef WITH_DRAMSIM3
  // init_ram() above has already placed every ELF section into the memory the
  // DRAMSim3 model reads, so fesvr must not load the program a second time
  // through the debug module: that path writes 8 bytes per abstract-command
  // round trip (~1,100 target cycles each) while hart 0 sits in the debug
  // ROM, which for a multi-hundred-KB image delays the start of the program
  // by tens to hundreds of millions of cycles. fesvr still reads the ELF for
  // its symbols (tohost/fromhost) and entry point.
  dtm = new postload_dtm_t(htif_argc, htif_argv);
#else
  // The fixed-latency AXI4RAM model is a plain RTL array that init_ram()'s
  // host-side copy never reaches, so the image has to go in through fesvr.
  dtm = new dtm_t(htif_argc, htif_argv);
#endif

  signal(SIGTERM, handle_sigterm);

  // The initial block in AsyncResetReg is either racy or is not handled
  // correctly by Verilator when the reset signal isn't a top-level pin.
  // So guarantee that all the AsyncResetRegs will see a rising edge of
  // the reset signal instead of relying on the initial block.
  int async_reset_cycles = 2;

  // Rocket-chip requires synchronous reset to be asserted for several cycles.
  // Bumped from 10 to 150: must outlast subsystem/ClockRouting.scala's
  // cascaded per-domain reset chain (uncore -> L3 -> L2 -> tile), which now
  // takes ~100 of these raw clock cycles to fully release since clk_500MHz/
  // clk_1GHz divide down much slower than this raw clock.
  int sync_reset_cycles = 150;

  // Hart bring-up is driven by SimDTM's own dtm_t (see SimDTM.cc's debug_tick,
  // the only DTM instance actually wired to the RTL -- this file's own `dtm`
  // has no RTL connection at all) on a separate host pthread, looping
  // hartsel 0..num_harts-1 over real JTAG/DMI round trips (dtm.cc's reset()).
  // At full simulation speed that host thread can lose the race against the
  // target's own execution: a hart that gets its PC redirected early can run
  // to completion -- and tile->io_success can go true -- before reset() has
  // gotten through the rest of the hartsels, leaving them stuck in bootrom's
  // _hang/entry_loop for the whole run despite a "*** PASSED ***" report.
  // VCD tracing incidentally avoids this by slowing the sim down enough for
  // the host thread to keep up; this throttles only the early boot window
  // (not the whole run) to get the same effect without VCD's dump overhead.
  // Override via BOOT_THROTTLE_CYCLES/BOOT_THROTTLE_USLEEP env vars.
  static uint64_t boot_throttle_cycles = [](){
    const char *e = getenv("BOOT_THROTTLE_CYCLES");
    return e ? strtoull(e, nullptr, 10) : 2000000ULL;
  }();
  static useconds_t boot_throttle_usleep = [](){
    const char *e = getenv("BOOT_THROTTLE_USLEEP");
    return (useconds_t)(e ? strtoul(e, nullptr, 10) : 20);
  }();

  while (trace_count < max_cycles) {
    if (trace_count < boot_throttle_cycles && boot_throttle_usleep)
      usleep(boot_throttle_usleep);
    if (done_reset && (dtm->done() || jtag->done() || tile->io_success))
      break;

    tile->clock = 0;
    tile->reset = trace_count < async_reset_cycles*2 ? trace_count % 2 :
      trace_count < async_reset_cycles*2 + sync_reset_cycles;
    // Track the reset schedule directly instead of "!tile->reset": the
    // bounce formula above deasserts reset at trace_count=0, which would
    // make done_reset (gates STOP_COND/PRINTF_COND) go true before reset
    // has ever actually been asserted.
    done_reset = trace_count >= async_reset_cycles*2 + sync_reset_cycles;
    tile->eval();
#if VM_TRACE
    bool dump = tfp && trace_count >= start;
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2));
#endif

    tile->clock = trace_count >= async_reset_cycles*2;
    tile->eval();
#ifdef WITH_DRAMSIM3
    dramsim3_step();
#endif
#if VM_TRACE
    if (dump)
      tfp->dump(static_cast<vluint64_t>(trace_count * 2 + 1));
#endif
    trace_count++;
  }

#if VM_TRACE
  if (tfp)
    tfp->close();
  if (vcdfile)
    fclose(vcdfile);
#endif

  if (dtm->exit_code())
  {
    fprintf(stderr, "*** FAILED *** via dtm (code = %d, seed %d) after %ld cycles\n", dtm->exit_code(), random_seed, trace_count);
    ret = dtm->exit_code();
  }
  else if (jtag->exit_code())
  {
    fprintf(stderr, "*** FAILED *** via jtag (code = %d, seed %d) after %ld cycles\n", jtag->exit_code(), random_seed, trace_count);
    ret = jtag->exit_code();
  }
  else if (trace_count == max_cycles)
  {
    fprintf(stderr, "*** FAILED *** via trace_count (timeout, seed %d) after %ld cycles\n", random_seed, trace_count);
    ret = 2;
  }
  else if (verbose || print_cycles)
  {
    fprintf(stderr, "*** PASSED *** Completed after %ld cycles\n", trace_count);
  }

  if (dtm) delete dtm;
  if (jtag) delete jtag;
  if (tile) delete tile;
  if (htif_argv) free(htif_argv);
  return ret;
}
