---
name: intenscore-rocketchip
description: Technical primer on this repo's IntenCore multi-context RISC-V design (built on RocketChip) — architecture, chip configurations, build/test/debug workflow, and the bare-metal runtime. Load this before exploring, modifying, or experimenting with the RTL, configs, or riscv-tests in this repository.
---

# IntenCore / RocketChip: Repository Skill

This is a reference for working in this repository — a RocketChip-derived RISC-V SoC generator
(Chisel → FIRRTL → Verilog → Verilator simulation) extended with a custom multi-context front end
called **IntenCore**. It describes the current state of the design: what's here, how the pieces fit
together, how to build/run/debug it, and what to watch out for when changing it. It describes
what exists in the tree today; where a pitfall was found the hard way, it is recorded next to the
thing it affects so it isn't rediscovered.

## 1. What this is

Standard RocketChip provides the base Chisel infrastructure (diplomacy, TileLink, the Rocket
in-order core, standard cache/bus generators). IntenCore is a layer on top of that infrastructure
which lets multiple hardware thread contexts ("ROWs") time-share a smaller number of physical
execute pipelines, rather than giving every hart its own dedicated core. The result is configurable
along several axes: number of physical cores, number of shared pipelines per core, and number of
contexts multiplexed onto each pipeline — see Section 4.

The simulation flow is Verilator-based: Chisel elaborates to FIRRTL, FIRRTL compiles to Verilog,
Verilator compiles that Verilog plus a C++ testbench into a simulator binary, and that binary runs
compiled RISC-V ELF binaries (bare-metal ISA tests and benchmarks) against the simulated chip.

## 2. Repository layout

```
src/main/scala/
  rocket/        Core pipeline, caches, and the IntenCore front end (see Section 3)
  subsystem/     Chip-level wiring: bus topology, clocking, chip Configs
  tile/          Per-tile (per-physical-core) wrapping around the Rocket pipeline
  system/        Top-level Configs and test-suite registration
  device/        AXI4 memory models (DRAMSim3-backed and fixed-latency)
  tilelink/      TileLink protocol infrastructure (crossbars, crossings, cork, width adapters)
  amba/          AXI4 protocol infrastructure
  cache/         Optional standard SiFive InclusiveCache config (not wired into any active Config)
  diplomacy/, diplomaticobjectmodel/, interrupts/, jtag/, prci/, regmapper/, util/, ...
    Standard RocketChip infrastructure, mostly unmodified

riscv-tests/     Bare-metal ISA tests and benchmarks that run against the simulator (Section 6)
  spec95-equivalent/  Six larger rate-mode workloads with their own build/run/verify flow (Section 10)
emulator/        Verilator build/run flow — build here, outputs land in emulator/output/
DRAMSIM3/        Vendored (not a submodule) DRAMSim3 fork, used for cycle-accurate DRAM timing (Section 4.4);
                 libdramsim3.a is built by cmake on first use, not checked in
chisel3/, hardfloat/, macros/
                 Vendored dependencies of the Chisel toolchain
firrtl/, api-config-chipsalliance/, torture/
                 Git submodules (run `git submodule update --init --recursive`);
                 torture is the cache-coherence / AMO stress-test suite (riscv-torture)
```

**Licensing matters before you reuse or redistribute anything.** Intensivate's own core files carry
`SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0` (the Intensivate Non-Commercial Hardware
Source License: source-available, not open source, non-commercial use and evaluation only; commercial use
needs a separate licence from info@intensivate.com; the core is also covered by patents, see
`PATENTS.md`). The rest of the tree keeps its upstream licences (Apache-2.0, BSD, MIT, MulanPSL-2.0,
and GPL in the benchmark suite). Intensivate's benchmark glue is BSD-2-Clause. Read `LICENSE.md` and
`NOTICE.md`, and check the licence of the specific file, before reusing it.

## 3. Architecture: how a request flows through the chip

Bottom-up, in terms of the module hierarchy under `RocketSubsystem` (`subsystem/RocketSubsystem.scala`):

- **`L2MacroTile`** (`subsystem/L2Macro.scala`, extends `L2BaseTile`) — one instance per L2-macro
  group. Wraps a set of standard RocketChip `RocketTile`s (`tile/RocketTile.scala`) plus the
  IntenCore front end and a banked L2.
- **IntenCore front end** (`rocket/ctxtUnit.scala`, `rocket/instrUnit.scala`, `rocket/pipeUnit.scala`,
  `rocket/csrUnit.scala`) — this is what makes multiple hardware contexts ("ROWs") share physical
  pipeline resources:
  - `ctxtUnit.scala` holds the per-context architectural state (register files, CSRs via
    `csrUnit.scala`) and arbitrates context access to the shared instruction-fetch path
    (`instrUnit.scala`) and to each shared execute pipe (`pipeUnit.scala`) using a round-robin
    priority selector (`SelPrio`, defined in `ctxtUnit.scala`) that scans forward from whichever
    context won last.
  - A PU ("PipeUnit") is one shared execute pipeline; `NUM_PUS` of them exist per physical core,
    each serving `NUM_CTXT` contexts. `NUM_MPS` further splits memory-pipe (load/store) issue within
    a PU. See Section 4 for how these compose into the total hart count.
  - Each PU has its own shared L1 data cache (`rocket/HellaCache.scala` / `DCache.scala` /
    `NBDcache.scala`); contexts within a PU arbitrate for cache-command issue the same way, via a
    `SelPrio` instance sized `NumDCCmds` wide.
- **L1↔L2 crossbar** (`l1l2xbar` in `subsystem/L2BaseTile.scala`) — connects the L1s to `NUM_L2BANKS`
  banks of shared L2.
- **L2** (`subsystem/BankedL2Params.scala`) — a banked coherence-manager-backed cache, sized by
  `L2_WAYS` / `L2_SETS_ALL_BANKS` / `NUM_L2BANKS`.
- **`TLCacheCork`** (`subsystem/TLCacheCork.scala`) — terminates L2's coherent (`AcquireB`-capable)
  TileLink traffic into plain `Get`/`Put` before it reaches memory. No L3 cache is instantiated in
  this design yet, so this termination is required (nothing downstream accepts TL-C traffic) rather
  than optional; `NUM_L3BANKS` sizes how many parallel cork/crossbar slots exist on this path, mostly
  for bandwidth partitioning rather than caching. All of this — `l2l3xbar`, the corks, and the
  downstream crossbar — sits on the same clock domain, so no clock-domain crossing is needed here.
- **AXI4 memory** (`device/AXI4Memory.scala`, `device/AXI4RAM.scala`) — the final TileLink-to-AXI4
  conversion feeds one of two interchangeable memory models, selected at build time; see Section 4.4.

### Clock domains (`subsystem/ClockRouting.scala`)

A single undivided reference clock is divided down into three domains used by the simulation
testbench (`ClockRouting` is an explicit stand-in for a real clock-control unit):

| Domain | Divider | Feeds |
|---|---|---|
| `clk_3_5GHz` | ÷2 | Tile / CPU pipeline logic |
| `clk_1_75GHZ` | ÷4 (cascaded from `clk_3_5GHz`'s divider) | L2 cache |
| `clk_500MHz` | ÷14 (cascaded further) | Everything else (uncore, memory path) |

Each domain has its own reset, synchronized to that domain's own clock via `ResetCatchAndSync`, and
resets release slowest-domain-first (`clk_500MHz` → `clk_1_75GHZ` → `clk_3_5GHz`) because the
crossings between domains assume the slower side is already out of reset before the faster side
starts issuing traffic across them.

## 4. Chip configurations (`system/Configs.scala`)

All chip parameters are collected in one case class, `IntenCoreParams` (`rocket/Consts.scala`,
exposed to the rest of the design via `IntenCoreKey`/`HasIntenParameters`). A config picks a set of
values for these knobs:

| Field | Meaning |
|---|---|
| `NUM_CTXT` | Contexts ("ROWs") multiplexed onto each PU. Must be a power of 2. |
| `NUM_PUS` | PUs (shared execute pipelines) per physical core. Currently must be 1 or 2. |
| `NUM_PHY_CORES` | Physical cores instantiated. |
| `NUM_MPS` | Memory-pipe splits per PU (`NUM_CTXT` must be divisible by this). |
| `NUM_PTW_ENTRIES` | Page-table-walker cache entries (power of 2). |
| `NUM_PHYS_ADDR_BITS` | Physical address width to memory (must be > 31). |
| `L1_NUM_MSHR` | Outstanding miss buffers per L1↔L2 TileLink connection — must be large enough to sustain the concurrent-context traffic for the chosen hart count, or the design stalls under load. |
| `NUM_L2MACRO` | Number of `L2MacroTile` instances. |
| `NUM_L2BANKS` | L2 bank count (power of 2). |
| `L2_WAYS` / `L2_SETS_ALL_BANKS` | L2 associativity / total set count across all banks. |
| `NUM_L3BANKS` | `TLCacheCork` slot count (must be a multiple of `NUM_L2BANKS`) — see Section 3. |
| `L3_WAYS` / `L3_SETS_ALL_BANKS` | Sizing for the (currently absent) L3 cache path. |
| `NUM_MEMORYCHANNELS` | Memory channel count (must not exceed `NUM_L3BANKS`). |
| `HasPCIE` / `HasBSYS` | Optional peripheral attachment points. |
| `BACKEND_ENA` | Whether backend/hard-IP elements are included. |

Total hart count for a config is `NUM_PHY_CORES * NUM_PUS * NUM_CTXT`.

The configs currently defined:

| Config | NUM_CTXT | NUM_PUS | NUM_PHY_CORES | Total harts | L1_NUM_MSHR | NUM_L2BANKS | NUM_L3BANKS |
|---|---|---|---|---|---|---|---|
| `Inten1RowConfig` | 1 | 1 | 1 | 1 | 2 | 1 | 1 |
| `Inten1CoreConfig` | 8 | 2 | 1 | 16 | 16 | 2 | 4 |
| `Inten2CoreConfig` | 8 | 2 | 2 | 32 | 16 | 4 | 4 |
| `Inten4CoreConfig` | 8 | 2 | 4 | 64 | 8 | 8 | 16 |

`Inten1RowConfig` is the fastest-building, minimal-hart-count config — use it for quick sanity
checks of a change before running a full multi-hart config.

### 4.4 Memory timing: DRAMSim3 vs. fixed-latency

The AXI4 memory backend is selected at build time by the `DRAMSIM3` make variable
(`Makefrag`/`emulator/Makefile`), and the two options exercise genuinely different hardware, not
just a flag:

- **`DRAMSIM3=1` (default).** Every AXI request is handed off via a DPI-C call to DRAMSim3 (the
  vendored `DRAMSIM3/` fork), which models real DDR4 timing per request — row activation, CAS latency,
  precharge, bank/row conflicts — based on the config file at
  `DRAMSIM3/configs/intenscore_DDR4_8Gb_x8_3200_1ch_2ra_16GB.ini`, converted into RTL-visible cycles
  via that file's `cpu_freq`/`dram_freq` ratio. Multiple requests can be genuinely outstanding at
  once, tracked per-AXI-ID (`device/AXI4Memory.scala`), similar to a real memory controller.
- **`DRAMSIM3=0`.** Uses `device/AXI4RAM.scala` instead — a plain synthesizable memory array wired
  directly to a minimal AXI handshake state machine, with no DRAM timing model and no DRAMSim3
  dependency at all (this flow doesn't link `libdramsim3`).

Both flows run the same binaries with identical architectural (correctness) behavior; the
difference is purely in memory-latency realism and the resulting cycle counts. Use `DRAMSIM3=1` for
anything performance- or contention-sensitive, `DRAMSIM3=0` for fast functional iteration. The two
flows keep separate build outputs (`generated-src[_nodramsim3]`, distinct emulator binary names) so
switching doesn't force a clean rebuild.

DRAMSim3 must be built once per checkout before the default (`DRAMSIM3=1`) flow will link:

```
cd DRAMSIM3
mkdir -p build && cd build
cmake -DCOSIM=1 ..
make
```

This is skippable if you only ever intend to build with `DRAMSIM3=0`.

## 5. Build, run, and debug workflow

```
cd emulator
make                                            # build for the default config (Inten1CoreConfig)
make CONFIG=freechips.rocketchip.system.Inten2CoreConfig
make run-asm-tests                              # RISC-V ISA compliance suite
make run-bmark-tests                            # bare-metal benchmarks
make run                                        # both of the above
make run-spec95-equivalent-tests SPEC95EQ_WORKLOAD=extra_tiny   # larger workloads, Section 10
```

Add `CONFIG=freechips.rocketchip.system.<name>` to any target to pick a config (Section 4). Add
`DRAMSIM3=0` (or use a `-nodramsim3` target suffix, e.g. `make run-bmark-tests-nodramsim3`) to use
the fixed-latency memory model instead (Section 4.4).

Each benchmark/test's log lands in `emulator/output/<test>.riscv.out`, ending with
`*** PASSED *** Completed after N cycles` or a failure/timeout message.

**Waveform debugging:**

```
cd emulator
make debug                                      # build the -debug (VCD-capable) binary
make output/<test>.riscv.out
make output/<test>.riscv.vcd
gtkwave output/<test>.riscv.vcd
```

VCD files can be very large for long-running benchmarks; pass `--dump-start=CYCLE` /
`--max-cycles=N` directly to the emulator binary to bound a capture. When correlating a VCD against
a benchmark's behavior, cross-reference PC values against the matching `<test>.riscv.dump`
disassembly (built alongside the binary) rather than reading raw opcodes.

**Stress tests:** `make run-torture-tests[-debug]` runs the cache-coherence / atomic-memory-operation
stress suite in `torture/`.

**Adding a new benchmark:** copy an existing directory under `riscv-tests/benchmarks/`, add it to
`tests +=` in `riscv-tests/benchmarks/Makefile`, and add the compiled binary to
`rvi-bmark-tests` in `emulator/Makefile`. **Adding a new test suite category** additionally requires
registering it in `src/main/scala/system/RocketTestSuite.scala`,
`src/main/scala/stage/phases/AddDefaultTests.scala`, and `riscv-tests/Makefile.in`.

**Note:** Make decides whether to rebuild based on file timestamps, not build-flag values — switching
`CONFIG`, `DRAMSIM3`, or `NHARTS` (below) on an existing build tree can silently reuse stale
artifacts. Run `make clean` first when in doubt.

## 6. The bare-metal runtime (`riscv-tests/`)

Tests and benchmarks run with no OS and no standard C library — only the minimal helpers in
`riscv-tests/common/` (a small `printf`, `memcpy`/`memset`, etc.). Boot sequence, per-hart stack
setup, and trap vector installation happen in `riscv-tests/common/crt.S` before control reaches one
of three possible entry points a test can define:

- `int thread_entry(int cid, int nc)` — run by every hart simultaneously (`cid` = this hart's index,
  `nc` = total hart count).
- `int main(int argc, char** argv)` — run only by hart 0.
- `int extra_thread_entry(int cid, int nc)` — run by every hart except hart 0, if `thread_entry` isn't
  defined (pairs with `main()` for hart 0's own work).

`nc` is a compile-time constant, threaded in via the `NHARTS` make variable
(`riscv-tests/Makefrag-baremetal`, default 16) into `crt.S`. **It must match the hart count of
whichever RTL config the resulting binaries will run against** — rebuild with
`make -C riscv-tests/benchmarks NHARTS=<count>` (matching Section 4's "Total harts" column) before
testing a different config, and `make clean` first since Make won't detect the flag change on its
own.

**Synchronization:** `riscv-tests/common/util.h` provides `barrier(ncores)`, an atomic-counter
barrier used between phases of multi-hart benchmarks. Its wait loop backs off between polls
(`delay_cycles`) rather than busy-spinning on the shared counter, so harts that finish early don't
saturate that cache line while others are still working — relevant if you're adding a new
multi-hart benchmark or scaling an existing one to a higher hart count.

**Host communication (HTIF):** a test communicates with the simulation host through a shared
`tohost`/`fromhost`/`hostAccessSema` mailbox in memory (`riscv-tests/common/syscalls.c`). Only hart 0
signals process exit to the host; other harts that finish just update shared status and wait.
Requests to this shared mailbox are serialized via an LR/SC spinlock with a per-hart randomized
backoff (`arch_rand_delay`) to reduce collision probability as hart count grows.

**Pass/fail:** a run passes only if every hart exits with status 0. Any hart calling `abort()` fails
the run immediately.

**Runtime details that matter for larger programs:**
- **`memcpy`/`memset` in `syscalls.c` carry `optimize("no-tree-loop-distribute-patterns")`.**
  GCC 10 at -O2 recognises their byte loops as memcpy/memset idioms and compiles them into a call
  to themselves; any unaligned `memset` then recurses until the stack runs into `.text`. Keep the
  attribute on any similar helper you add.
- **Per-hart stack+TLS size** is `HART_STACK_SIZE` in `crt.S` (default `0x1fe40`, about 127 KB),
  overridable with `-DHART_STACK_SIZE=<bytes>`. Interpreters and compilers need MBs; an overflow
  silently corrupts the next hart's stack/TLS.
- **The riscv-tests mini-libc shadows newlib.** Its `printf`/`sprintf` have no `%e/%f/%g`. A
  program linked against newlib must resolve its stdio to newlib's, not to `syscalls.c`'s, or it
  prints wrong numbers while still "passing".
- **Emulator exit reporting:** the emulator prints `*** PASSED ***` only with `-c` or `+verbose`;
  without them a successful run prints nothing and only the exit status tells.

## 7. Debugging methodology

**Instruction-commit logs** (`+verbose` runs) print one line per retired instruction, per hart:

```
C<pu>_<ctxt>: <seq> <PC> <raw-instr> [<op1> <op2>] <priv> <cycle-hex>
```

Grepping this format lets you check which harts are progressing, reconstruct control flow (cross-
reference `<PC>` against the `.riscv.dump` disassembly), and spot a hart that's stopped retiring
(a repeated or absent PC in the tail of the log for that hart's label).

**VCD analysis at scale:** these files can be tens of GB. Useful techniques:
- `grep -m1 '\$enddefinitions'` to find the header boundary quickly (it's near the top even in a huge
  file).
- Walk `$scope`/`$upscope` lines with `awk`, tracking depth, to map out module hierarchy and find the
  exact signal codes you need before extracting anything.
- Extract only the specific signal codes you need, within a narrow timestamp window, via targeted
  `grep -E` — never load a multi-GB VCD wholesale.
- Always capture `#<timestamp>` marker lines alongside signal changes; without them it's easy to
  misattribute which signal changed at which point in time.
- Cross-reference decoded PCs/addresses against the actual `.riscv.dump` disassembly rather than
  interpreting raw opcodes by hand.

## 8. Hart bring-up: the real mechanism, and the non-VCD bring-up race

**Every hart's true RTL reset vector is `_hang` (`bootrom/bootrom.S`), not `_start`.**
`BootROMParams.hang` (`devices/tilelink/BootROM.scala`, default `0x10040`) feeds
`resetVector`, and `ctxtUnit.scala`'s `IF_nxtInstrAddr_reg` is `RegInit`'d to that same
`hang` address for every context row. `_hang` clears `mie` and parks in `wfi` after ~8
instructions (`csrwi 0x7c1,0` / `csrr mhartid` / `la _dtb` / `csrwi mie,0` / `li a2,0xfff` /
`csrw 0xf,a2` / `wfi` / loop) — with `mie=0`, no ordinary interrupt can ever wake it.

**The only way out is the debug module**, driven from a *separate host thread* in fesvr
(`dtm_t::producer_thread`, riscv-isa-sim's `fesvr/dtm.cc`). `dtm_t::reset()`
loops `hartsel = 0 .. num_harts-1`, and for each one does `fence_i()` (halt+resume) then
`write_csr(0x7b1 /* dpc */, get_entry_point())` — redirecting that hart's PC to the
loaded ELF's real entry point. This requires genuine JTAG/DMI round trips per hartsel, so
it takes real, non-instant time to work through all of them.

**The race: at full (non-VCD) simulation speed, this reset() sequence could fail to finish before
the simulation itself reported success.** Root cause, confirmed via direct VCD signal extraction
on dhrystone:
- `emulator.cc`'s main loop breaks on `dtm->done() || jtag->done() || tile->io_success`.
  `tile->io_success` is wired (`devices/debug/Periphery.scala`,
  `tbsuccess := io.exit === 1.U`) to the debug module's own SBA-based read of `tohost` —
  a *second*, RTL-side path to detect completion, independent of fesvr's own C++-side
  polling of the same memory location.
- If that RTL-side check reports "exit" before any hart has actually reached the target
  program (confirmed empirically: in a fast run, grepping the `+verbose` log for any PC in
  the `0020xxxxxx` range across *every* hart label returned zero matches), the emulator falls
  through to `*** PASSED ***` anyway — `dtm->exit_code()`/`jtag->exit_code()` are both 0
  (never actually got a *real* failing tohost write either), and `trace_count != max_cycles`
  rules out the timeout branch.
- **Practical consequence:** at normal speed, every hart may still be parked in `_hang`/
  `entry_loop` (`debug_rom.S`, PCs ~`0x800`-`0x84c`) for the *entire* run, and it will still
  print `*** PASSED ***`. This was confirmed on `dhrystone` and `intMul` — both "passed" at
  ~1.6-1.7M cycles with every hart's PC trace confined to the `0x10040`-`0x10064` /
  `0x800`-`0x84c` ranges only, never touching `0x20000000+` at all.
- **VCD tracing incidentally works around this** (not a real fix): the extra per-cycle trace
  overhead gives the host thread's `reset()` loop enough real wall-clock time to finish
  redirecting all `num_harts` hartsels before the RTL-side `io.exit` check can fire
  prematurely, so VCD-enabled runs reliably show every hart reaching real code.
- **A flat, hart-independent boot delay in `crt.S` (tried: 2,000,000 iterations before the
  existing `mhartid`-scaled delay) does NOT fix this** — a hart stuck in `_hang`/
  `entry_loop` has not yet executed a single instruction of `crt.S`, so no amount of delay
  *inside* the target binary can help. Any real fix has to be on the host-thread timing /
  `io.exit` RTL-check side, not in the target program.

**The fix in place: `emulator.cc` throttles the early boot window.** For the first
`BOOT_THROTTLE_CYCLES` raw cycles (default 2,000,000) the main loop sleeps
`BOOT_THROTTLE_USLEEP` µs (default 20) per cycle, which gives the host thread time to redirect
every hartsel before any hart can finish. Both are environment variables; raise the cycle count
if a config with more harts shows the symptom again.

**Still: treat `*** PASSED ***` as a claim to verify, not a result.** Confirm with `+verbose`:
grep for `^C<pu>_<ctxt>: [0-9a-f]+ 0020[0-9a-f]+` across all expected hart labels. If every label
shows commits at real `0x20000000+` PCs (not just the `0x10040`/`0x800` ranges), every hart ran
the program. The emulator runs at the same speed with `+verbose` (the trace costs disk, not
time), so this check is cheap except for log size.

## 9. Three parallelism patterns, by benchmark source (verified via VCD + address-level trace)

Once genuinely booted, every hart reaches `crt.S`'s `_init(cid, nc)`, which dispatches per
Section 6's three entry points. In practice, existing `riscv-tests/benchmarks/` sources fall
into three distinct patterns — **don't assume one applies without checking the source**:

| Pattern | Example | Mechanism | Per-hart commit signature (VCD-confirmed) |
|---|---|---|---|
| **True work division** | `mt-vvadd`, `mt-matmul` | Custom `thread_entry(cid,nc)`; loop strides `for(i=coreid;i<n;i+=ncores)` over one *shared* `static` array | hart 0 much larger (own slice + all boot/exit bookkeeping), others roughly equal and smaller (their own slice only) — e.g. mt-vvadd: hart0 ~190K, others ~24-26K each |
| **Rate/throughput** | `dhrystone` | Custom `thread_entry(cid,nc)`; every hart runs the *entire* benchmark independently, using `coreId`-indexed private data (e.g. `Ptr_Glob[coreId]`) inside otherwise-shared statics to avoid collisions | all harts roughly equal and large — e.g. dhrystone: 296K-392K each across all 16 |
| **Single-threaded (default)** | `median`, `qsort`, `pmp`, `towers`, `rsort`, `spmv`, `vvadd`, `mm`, `multiply`, `intMul` | No `thread_entry` override — vanilla default: only `cid==0` calls `main()`; every other hart calls the weak no-op `extra_thread_entry()` | hart 0 large (real benchmark work), others small-but-nonzero (~4,500 each — boot + `_init`'s counter-print/sprintf overhead + exit housekeeping, no real benchmark work) |

Confirm which pattern a given source uses by (a) `grep -n thread_entry` in the benchmark's own
`.c` files — its presence means pattern 1 or 2, its absence means pattern 3 — and (b) for
patterns 1 vs 2, checking whether the loop body divides one shared array by `cid`/`nc`
(pattern 1) or runs a self-contained, `coreId`-indexed-only workload (pattern 2). Verify with
the address-level trace (are different hart labels writing to different-but-related addresses
in one shared array, or is each hart's write pattern self-contained?) rather than assuming
from the benchmark's name alone.

## 10. spec95-equivalent benchmarks (`riscv-tests/spec95-equivalent`) and how to trust a run

Six larger, real-program workloads, far bigger than `riscv-tests/benchmarks`, built for the same
riscv-tests runtime (`common/crt.S`, `syscalls.c`, `test.ld`, `Makefrag-baremetal`) and linked
against newlib:

| Port | What it is | Language |
|---|---|---|
| `libcint` | quantum-chemistry two-electron integrals (derivative ERIs) | C |
| `miniweather` | 2-D atmospheric fluid dynamics | C++ |
| `perl-perl4` | Perl 4 interpreter running a prime/factor/hash script | C |
| `bsmbench` | SU(2) lattice gauge theory: Dirac operator, CG inversion | C |
| `xlisp` | Lisp interpreter: tak, symbolic derivative, sort, list churn | C |
| `gcc-cc1` | GCC 2.5.8's compiler proper, compiling generated C to SPARC assembly | C |

The folder's `README.md` is the user-facing manual. This section is the working knowledge
behind it.

### 10.1 Layout

```
riscv-tests/spec95-equivalent/
  Makefile, link.mk        build entry points (link.mk: final link through Makefrag-baremetal)
  benchmarks/              upstream sources, unmodified; licences, NOTICE, PROVENANCE, REUSE.toml
  benchmarks/baremetal/    everything the port adds: per-port glue (bm_rate_thread.c, OS stubs,
                           shims, workload inputs, PORT-NOTES.md) and common/ (htif_syscalls.c,
                           bm_rate_heap.c, bm_cxx.c, ...)
  scripts/                 build.sh, run.sh, workloads.sh, process_dispatch.c, process.ld,
                           rate_ldr.ld, copies_dispatch.sh, compare.py, make_reference.sh,
                           spike_check.sh, profile.py, port-layout.txt, stubs/
  reference/<WORKLOAD>/    native (x86 host) reference output per port, used by compare.py
  build/                   binaries: <port>-<WORKLOAD>-<NHARTS>.riscv (git-ignored)
```

### 10.2 Commands

```
# build only (from riscv-tests/spec95-equivalent)
make WORKLOAD=extra_tiny NHARTS=1             # all six for Inten1RowConfig
make xlisp WORKLOAD=tiny NHARTS=16            # one port for Inten1CoreConfig
make spike-check WORKLOAD=extra_tiny NHARTS=16    # functional check on spike, minutes
make reference WORKLOAD=<size>                # regenerate native references

# build + run + verify (from emulator/); NHARTS follows CONFIG
make run-spec95-equivalent-tests CONFIG=freechips.rocketchip.system.Inten1RowConfig SPEC95EQ_WORKLOAD=extra_tiny
make -j6 run-spec95-equivalent-tests SPEC95EQ_WORKLOAD=extra_tiny          # Inten1CoreConfig
make run-spec95-equivalent-tests SPEC95EQ_TESTS="xlisp perl-perl4"         # subset

# one run by hand, and re-judging an existing log without re-running
scripts/run.sh <port> <workload> <nharts> <emulator> <out-dir> [max-cycles]
REJUDGE=1 LOGFILE=<log> scripts/run.sh <port> <workload> <nharts> <emulator> <out-dir>
```

Make variables: `SPEC95EQ_WORKLOAD` (default `tiny`), `SPEC95EQ_TESTS`, `SPEC95EQ_NHARTS`
(derived from `CONFIG`: 1Row 1, 1Core 16, 2Core 32, 4Core 64), `SPEC95EQ_EMU`,
`SPEC95EQ_VERBOSE=1` (`+verbose`; gigabytes to tens of GB for 16 harts),
`spec95eq_timeout_cycles` (default 5e9). `run.sh` options: `VERBOSE=1`, `REJUDGE=1`,
`LOGFILE=<path>`. `build.sh` options: `RATE_MODE=process|copies`, `BUILD_TAG` (variant name
suffix, e.g. the `-spike` builds).

Per test, in `emulator/output/spec95-equivalent/` (`<name>` = `<port>-<workload>-<nharts>`):
`<name>.log` (everything the emulator prints, including DRAMSim3's start-up lines, PASSED/FAILED
and the `R_` lines, written live), `<name>.build.log`, `<name>.verdict`, and the run directory
`<name>/` (each copy's `hartNN.out`/`.err`, bsmbench's `bsmbench-hartNN.log`, gcc-cc1's
`gcc-cc1-out-hartNN.s`, and `dramsim3epoch.json`). The terminal shows one line per stage and a
summary with each test's RESULT and `perf:` line.

### 10.3 Workload sizes: one flag, `WORKLOAD`

`scripts/workloads.sh` is the single table of sizes, sourced by both `build.sh` (the RISC-V
binary) and `make_reference.sh` (its native reference), so they cannot disagree:

| Port | extra_tiny | tiny |
|---|---|---|
| libcint | 1 centre, no d shells | 2 centres |
| gcc-cc1 | 1-group generated input | 2 groups |
| perl-perl4 | `primes.pl 100 20` | `400 100` |
| xlisp | tak (10 6 2), 4 deriv reps, 60-element lists, 1 sort pass | `xlisp-bench-tiny.lsp` |
| miniweather | same as tiny (20x10 grid, 5 s) | 20x10, 5 s |
| bsmbench | 2x4x4x4 lattice, 1 Dphi iteration | 4x4x4x4, 7 iterations |

`small` and `ref` exist for fast targets and spike. extra_tiny is the smallest cut that is still
mostly benchmark kernel (measured on spike: a near-null run's instructions against the
candidate's, plus per-function profiles from `scripts/profile.py`). miniweather cannot shrink
further without becoming mostly start-up. A new size needs a row in `workloads.sh` and
`make reference WORKLOAD=<size>` before use.

bsmbench sizes its own run by timing itself. On a target with no real clock that is
nondeterministic, so `bm_vtime.c` wraps `time()` (`--wrap=time`) with a deterministic clock that
advances a fixed step per reading. That fixes the iteration count.

### 10.4 Rate mode: every hart runs a complete copy

Nothing is divided between harts. Every hart runs one complete, independent copy on the same
input and the result is throughput. PASS means every hart finished its copy and parked
(`tohost_exit`). This is pattern 2 of Section 9, but for programs whose globals and C-library
state (stdio, malloc arena, errno) cannot be indexed by hart, so the build provides the
isolation.

**Process mode (`RATE_MODE=process`, the default), `scripts/process_dispatch.c`:** one copy of
the code shared by all harts, a private copy of the writable data per hart:
- The program is compiled once, linked relocatable with newlib (`ld -r`), and every symbol
  gets the prefix `h0_` (`objcopy --prefix-symbols`). A second pass renames back the symbols the
  runtime shares: `tohost`, `fromhost`, `hostAccessSema`, `_exit`, `__heap_end`.
  (`objcopy` applies `--redefine-sym` before `--prefix-symbols`, hence two passes.)
- `process.ld` defines `bm_data_start = ADDR(.data)`; the writable range is
  `[bm_data_start, _end)`.
- `enter_process(h)` copies that range to a private physical copy at
  `0x40000000 + h*stride` (stride rounded to 2 MB). It builds the hart's Sv39 page table at
  `0x3C000000 + h*1MB`: identity everywhere except the private range. It then sets `satp` and
  `mstatus.MPRV=1, MPP=S, SUM=1` and stays in M-mode. Only data accesses are translated, and
  instruction fetch stays physical (code is identity mapped).
- Per-hart stacks/TLS (`crt.S`) and heap slices (`bm_rate_heap.c`, from `0x80000000`) are
  already physically separate.
- fesvr works on physical memory, so `htif_syscalls.c` translates pointer arguments that fall
  in the private range (`bm_pa`, using `bm_proc_lo/hi/off`).
- C++ constructors: `rate_ldr.ld` gathers the copy's static constructors into one
  `.init_array` (in init-priority order) during the `ld -r` step. `objcopy` renames it to
  `h0_init_array` so crt.S's runtime doesn't run it once globally. The dispatcher runs
  `__start_h0_init_array..__stop_h0_init_array` per hart after entering the process, so each
  hart's private data gets constructed.
- `_exit` prints the hart's `R_` line, then calls `tohost_exit`.

**Copies mode (`RATE_MODE=copies`, `copies_dispatch.sh`):** N fully private prefixed copies of
the whole program linked into one image. It is simpler, but has N times the instruction
footprint, which is not representative on a core whose contexts share an I-cache. The copies
must come last on the link line, because `crt.S`'s `j _init` only reaches ±1 MB.

**Checks `build.sh` enforces** (it refuses the binary otherwise):
- `memset` has no self-call;
- the `sc.w.aq` host lock is present in `h0_bm_htif_syscall`;
- `h0__vfprintf_r` is present, i.e. stdio is newlib's;
- the copy count equals `NHARTS`;
- every writable non-TLS section other than `.tohost` lies inside `[bm_data_start, _end)`.

Builds go through a symlink tree generated from `port-layout.txt`, so each port sees its headers
exactly as upstream laid them out. Compiling at the real paths picked up the wrong `obstack.h`.

### 10.5 Host I/O with many harts

- **One mailbox, one lock.** `htif_syscalls.c` (newlib's syscalls) takes the same
  `hostAccessSema` lr/sc lock as `syscalls.c`'s `wait_host_free()`, through a weak reference.
  Without it, two harts' requests interleave in `tohost`/`fromhost`.
- **fesvr has one fd table for all harts.** Per-hart console output is done in the target:
  `bm_redirect_std` maps each program's fds 0-2 to its own `hartNN.out`/`.err` opened by the
  dispatcher. Closing and reopening fds does not work: `freopen` reorders fds and fesvr's
  `_close` ignores 0-2.
- **Inputs are opened by absolute path** through fesvr at run time. A binary only runs from the
  tree it was built in, so rebuild after moving or renaming the tree.
- **Lock spinning shows up in the counters.** On 16 harts each copy retires 4-29% more
  instructions than the same copy on 1 hart (measured: libcint +29%, perl +13%, gcc-cc1 +12%,
  miniweather +10%). Compare work with the 1-hart instruction count.

### 10.6 Verdict: what CORRECT means

`run.sh` runs the emulator with `-c` (otherwise success prints nothing) and records its exit
status. The first line of `<name>.verdict` is `RESULT: CORRECT` only if all of these hold:
1. The emulator reported PASSED, or exited 0 with no FAILED line.
2. All `NHARTS` harts printed `R_<hart>: copy_mcycle = … copy_minstret = … exit = 0`.
3. `compare.py` matches every copy's output against `reference/<WORKLOAD>/`.
4. With `VERBOSE=1` only: every hart committed at program PCs and none keeps re-entering
   `trap_entry`.

compare.py tolerates only what the PORT-NOTES document as compiler/libm dependent:
- libcint: the sum near 0 and the long-double line;
- miniweather: the wall-clock line, and `d_mass`/`d_te` to 1e-3 relative;
- bsmbench: timings, residual digits, and file paths compared by basename;
- gcc-cc1: its `.s` output compared per hart.

**Why every one of these checks exists:**
- **A PASSED line on its own proved nothing, twice.** Once it came with zero program commits
  (the bring-up race, Section 8). Once it came while perl computed wrong answers, because the
  mini-libc `sprintf` without `%g` shadowed newlib's (Section 6).
- **A hang prints nothing.** A recursive trap storm (`trap_entry` faulting on its own stack save
  because `sp` is bad) never reaches FAILED or `bad syscall`; it just spins. With `+verbose`,
  every hart label must commit at PCs >= `0x20000000`. The cycle field of a trace line is hex.

### 10.7 Performance numbers

The verdict's `perf:` line comes from the `R_` counters, in tile clock (`mcycle`). The emulator's
`Completed after N cycles` is raw testbench cycles (2x the tile clock) and includes boot, so do
not divide by it.
- **IPC per copy:** `copy_minstret / copy_mcycle`, averaged over copies.
- **CPI per copy:** all copies' cycles over all copies' instructions.
- **Throughput IPC:** all copies' instructions over the longest copy's cycles, i.e. the chip's
  aggregate rate. For work-normalised chip CPI use
  `longest copy cycles / (NHARTS × the 1-hart instruction count)`, so lock spinning does not
  count as work.

Measured with DRAMSim3, process mode:

| Port | Workload | 1Row CPI | 1Core CPI per hart | 1Core chip CPI (work-normalised) | 16-hart throughput gain |
|---|---|---|---|---|---|
| libcint | extra_tiny | 5.21 | 31.25 | 2.63 | 1.98x |
| miniweather | extra_tiny / tiny | 5.01 / 5.02 | 50.0 / 48.4 | 3.47 / 3.38 | 1.44x / 1.49x |
| perl-perl4 | extra_tiny / tiny | 5.35 / 5.04 | 19.1 / 28.9 | 1.53 / 2.00 | 3.51x / 2.52x |
| gcc-cc1 | extra_tiny / tiny | 6.43 / 5.99 | 24.9 / 23.9 | 1.83 / 1.70 | 3.51x / 3.52x |
| bsmbench | extra_tiny / tiny | 5.27 / 5.44 | — | — | — |
| xlisp | extra_tiny / tiny | 6.33 / 6.32 | — | — | — |

1Row CPI is stable at 5-6.4 and agrees between extra_tiny and tiny within 0.4. miniweather
scales worst on 16 harts, consistently at both sizes, which points at a shared resource (FP or
memory) rather than noise.

### 10.8 How long runs take

- **1Row:** every port finishes in under an hour at extra_tiny (miniweather 17 min, bsmbench
  54 min).
- **1Core:** the 16-hart emulator simulates about 4,300 raw cycles/s. extra_tiny takes about
  6-36 h per port, and tiny several days for the larger ports. Run the six in parallel (`-j6`,
  or one machine per port).
- **spike:** runs the same binaries in seconds to minutes (`make spike-check`). They are built
  with `-DSPIKE` because spike lacks the delay CSR `0xf`. Check new sizes and harness changes
  there before spending RTL time.

### 10.9 Pitfalls that bit and would bite again

- **The debug-module program load.** Unless the DTM knows the image is preloaded, fesvr writes
  the ELF through the debug module 8 bytes per round trip (~1,100 cycles each). With DRAMSim3,
  `emulator.cc` constructs `postload_dtm_t`: `init_ram()` loads the image once and fesvr only
  reads symbols and the entry point. Without it, hart 0 sits in the debug ROM for hours of
  simulation before a multi-hundred-KB image starts. The `DRAMSIM3=0` flow still loads through
  fesvr, because `AXI4RAM` is an RTL array that `init_ram()` does not reach.
- **Never edit a script while a run is using it.** bash reads scripts incrementally, and an
  in-place edit broke a running build. Replace files atomically (write a temp file, then `mv`).
- **bash's `GROUPS` is a builtin.** A variable of that name silently produced a garbage input
  file name, hence `CC1_GROUPS`.
- **Native references:** `make_reference.sh` builds with host gcc, the same sources, macros and
  inputs, and without `-ffast-math`. perl keeps its `bm_os_stubs.c` (for `sys_errlist`); xlisp's
  OS stubs and libcint's `bm_longdouble.c` are excluded on the host.
- **Harmless build messages:** bsmbench's `cc1: note: obsolete option '-I-'` (its upstream
  include scheme) and libcint's implicit `expl`/`erfl`/`erfcl`/`fabsl` declarations (GCC
  built-ins and `bm_longdouble.c` cover them).
- **A rare testbench assertion (`resp_wait`)** has been seen with specific random seeds on long
  multi-hart runs. It is not reproducible on other seeds and not in the program; rerun with
  another seed.

## 11. Where to look next

- `README.md` / `CONTRIBUTING.md` at the repo root for upstream RocketChip context.
- `src/main/scala/system/RocketTestSuite.scala` for the full list of registered test suites.
- `emulator/Makefrag-verilator` to tune Verilator's `--threads <n>` for your machine.
