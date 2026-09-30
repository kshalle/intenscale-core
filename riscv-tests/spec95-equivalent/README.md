# spec95-equivalent bare-metal benchmarks

Six spec95-equivalent workloads, ported to run bare-metal on the
IntenCore RTL through the same riscv-tests runtime as the benchmarks in
`riscv-tests/benchmarks` (`common/crt.S`, `syscalls.c`, `test.ld`,
`Makefrag-baremetal`):

| Port | What it is | Upstream |
|---|---|---|
| `libcint` | quantum-chemistry two-electron integrals (derivative ERIs) | libcint |
| `miniweather` | 2-D atmospheric fluid dynamics (C++) | miniWeather |
| `perl-perl4` | Perl 4 interpreter running a prime/factor/hash script | perl 4.036 |
| `bsmbench` | SU(2) lattice gauge theory: Dirac operator, CG inversion | BSMBench |
| `xlisp` | Lisp interpreter: tak, symbolic derivative, sort, list churn | XLISP-PLUS 3.05 |
| `gcc-cc1` | the C compiler proper, compiling generated C to SPARC assembly | GCC 2.5.8 |

### What each port stands in for

SPEC CPU95 (CINT95/CFP95) is proprietary and cannot be redistributed, so this
suite instead uses open-source programs of the same kind as six of its
benchmarks. The SPEC95 program and version each port is modelled on, against ours:

| Port | SPEC95 benchmark modelled on | SPEC95 version / description | Our version |
|---|---|---|---|
| `gcc-cc1` | 126.gcc (CINT95) | GCC 2.5.3, builds SPARC code | GCC 2.5.8 |
| `perl-perl4` | 134.perl (CINT95) | Perl interpreter (Unix-isms stripped); anagram and prime-number scripts | Perl 4.036 |
| `xlisp` | 130.li (CINT95) | XLISP interpreter running the Gabriel benchmarks | XLISP-PLUS 3.05 |
| `libcint` | 145.fpppp (CFP95) | Fortran; quantum chemistry, from the Gaussian series | libcint (C), two-electron integral derivatives |
| `bsmbench` | 103.su2cor (CFP95) | Fortran; quantum physics, Monte Carlo | BSMBench (C++), SU(2) lattice gauge theory |
| `miniweather` | 102.swim (CFP95) | Fortran; shallow-water equations, 1024x1024 grid | miniWeather (C++), 2-D atmospheric dynamics |

SPEC documents an exact version only for gcc; it does not publish the Perl or
XLISP versions. Source: <https://www.spec.org/cpu95/CINT95/> and
<https://www.spec.org/cpu95/CFP95/>.

The workloads are not SPEC's inputs: each port has its own input sizes
(`extra_tiny`, `tiny`, ...), so results are not SPEC95 scores. SPEC and the SPEC CPU95 benchmark names (e.g. 126.gcc, 130.li, 134.perl, 145.fpppp, 103.su2cor, 102.swim) are trademarks of the Standard Performance Evaluation Corporation, used only to identify the kind of program. This suite is not affiliated with or endorsed by SPEC; see `benchmarks/NOTICE.md`.

Upstream sources are under `benchmarks/` unmodified; everything this port adds
is in `benchmarks/baremetal/` (per-port glue, see each `PORT-NOTES.md`) and
`scripts/`. Licences: `benchmarks/LICENSE`, `benchmarks/LICENSES/`,
`benchmarks/NOTICE.md`, per-benchmark `ORIGIN.md`. Note that `gcc-cc1` is
GPL-2.0-only, perl 4 is GPL/Artistic, and BSMBench's BSD licence carries a
citation clause (see `benchmarks/NOTICE.md`).

## Quick start

From `emulator/`, with the emulator built for the config (`make CONFIG=...`):

    # 1 hart (Inten1RowConfig), all six ports, smallest workload
    make run-spec95-equivalent-tests CONFIG=freechips.rocketchip.system.Inten1RowConfig SPEC95EQ_WORKLOAD=extra_tiny

    # 16 harts (Inten1CoreConfig, the default CONFIG), six runs in parallel
    make -j6 run-spec95-equivalent-tests SPEC95EQ_WORKLOAD=extra_tiny

    # a subset, another size
    make run-spec95-equivalent-tests SPEC95EQ_TESTS="xlisp perl-perl4" SPEC95EQ_WORKLOAD=tiny

Results, logs and IPC/CPI land in `emulator/output/spec95-equivalent/` (see
[Running on the RTL emulator](#running-on-the-rtl-emulator)). To only build
the binaries, from this directory: `make WORKLOAD=extra_tiny NHARTS=16`.

## Rate mode

Every hart runs one complete, independent copy of the benchmark on the same
input, like the N copies of a rate run: nothing is divided between harts,
and the result is throughput -- how many copies the chip completes in a given
time. PASS means every hart finished its copy and parked (the riscv-tests
exit barrier, `tohost_exit`).

The copies are separate *processes* of one binary: one copy of the code,
shared by all harts, and a private copy of the program's writable data per
hart (its globals and C-library state: stdio, malloc arena, errno). Each hart
maps the image's writable range `[.data, _end)` to its own physical copy with
its own Sv39 page table while staying in M-mode with `mstatus.MPRV`
(`scripts/process_dispatch.c`), so data accesses are translated and
instruction fetch is not. Stacks, TLS and heap slices are already physically
per hart. Because code is shared, instruction caches shared between the
contexts of a pipeline behave as in a real rate run. (`RATE_MODE=copies`
instead links N private copies of the whole program; simpler, but N times the
instruction footprint, so not representative on this core.)

Each program's console output goes to its own files (`hartNN.out`,
`hartNN.err`; bsmbench also writes `bsmbench-hartNN.log`, gcc-cc1
`gcc-cc1-out-hartNN.s`), and each hart reports its own counters when it exits:

    R_<hart>: copy_mcycle = <cycles> copy_minstret = <instructions> exit = <status>

counted from the program's entry to its exit. Those are the rate measurement.
Note the emulator's own "Completed after N cycles" counts raw testbench clock
cycles, which is twice the tile's `mcycle`, and includes boot.

## Building

From `riscv-tests/spec95-equivalent` (needs `riscv64-unknown-elf-gcc` 10 with newlib on
`PATH`, or `RISCV_PREFIX`; `python3` for gcc-cc1's input generator):

    make                                      # all six, WORKLOAD=tiny, NHARTS=16
    make WORKLOAD=extra_tiny NHARTS=1         # all six for Inten1RowConfig
    make xlisp WORKLOAD=tiny NHARTS=16        # one port
    make clean

- `NHARTS` must match the harts of the target config: `Inten1RowConfig` 1,
  `Inten1CoreConfig` 16 (2Core 32, 4Core 64). It is compiled in.
- `WORKLOAD` selects the problem size, one flag for all ports
  (`scripts/workloads.sh` has the numbers):

| WORKLOAD | Use |
|---|---|
| `extra_tiny` | smallest size that is still mostly benchmark kernel -- for multi-hart RTL runs within about a day |
| `tiny` | the ports' own smallest size (RTL bring-up) |
| `small` | a routine run on fast targets |
| `ref` | host / spike measurement |

- Output: `build/<port>-<WORKLOAD>-<NHARTS>.riscv`. Each binary opens its
  workload input by absolute path at run time (through the frontend server),
  so run it from the tree it was built in, or rebuild after moving the tree.
- The build refuses a binary that would silently misbehave (runtime `memset`
  compiled into self-recursion, missing host-I/O lock, wrong hart count,
  writable data outside the per-hart range).

## Running on the RTL emulator

From `emulator/`, after building the emulator for the config:

    make run-spec95-equivalent-tests CONFIG=freechips.rocketchip.system.Inten1RowConfig SPEC95EQ_WORKLOAD=extra_tiny
    make -j6 run-spec95-equivalent-tests SPEC95EQ_WORKLOAD=extra_tiny        # Inten1CoreConfig, 6 in parallel

This builds the binaries (NHARTS follows `CONFIG`) and runs each under the
emulator. The terminal shows one line per stage and the results; everything
else goes to files in `output/spec95-equivalent/`, per test (`<name>` =
`<port>-<WORKLOAD>-<NHARTS>`):

| File | Contents |
|---|---|
| `<name>.log` | everything the emulator prints: DRAMSim3's messages, image loading, PASSED/FAILED, the per-hart `R_` counter lines, the trace with `SPEC95EQ_VERBOSE=1`. Written as the run goes (`tail -f`) |
| `<name>.build.log` | the build's output (compiler notes and warnings, see below) |
| `<name>.verdict` | the result, IPC/CPI, per-copy counters and per-copy output check |
| `<name>/` | the run directory: each copy's output (`hartNN.out`/`.err`, ...) and DRAMSim3's statistics (`dramsim3epoch.json`) |

Variables:
`SPEC95EQ_WORKLOAD` (default `tiny`), `SPEC95EQ_TESTS` (subset), `SPEC95EQ_NHARTS`
(override), `SPEC95EQ_EMU` (a different emulator binary), `SPEC95EQ_VERBOSE=1`
(`+verbose` trace, gigabytes for 16 harts), `spec95eq_timeout_cycles`.

One run by hand:

    scripts/run.sh xlisp extra_tiny 1 <emulator> <out-dir> [max-cycles]    # log: <out-dir>/xlisp.log, or LOGFILE=<path>

Expected messages in a `.build.log`, all harmless: bsmbench's
`cc1: note: obsolete option '-I-'` (its upstream include scheme, which the port
relies on for its replacement headers to win) and libcint's
`implicit declaration of function 'expl'` / `erfl` / `erfcl` / `fabsl`
(upstream calls long-double math that newlib's `math.h` does not declare; GCC's
built-ins and `bm_longdouble.c` cover it, and libcint's results match the
reference).

Performance is on the verdict's `perf:` line (also printed in the summary at
the end of `make run-spec95-equivalent-tests`), from the per-hart `R_` counters, in tile
clock cycles:

    perf: IPC per copy (mean) = 0.0207  CPI per copy = 48.424  throughput IPC (16 copies) = 0.3243  [...]

- *IPC per copy*: one copy's `copy_minstret / copy_mcycle`, averaged over the
  copies (the per-copy values are listed further down in the verdict). On a
  multi-context core each hart gets a share of a shared pipeline, so this is
  low by design.
- *CPI per copy*: all copies' cycles over all copies' instructions.
- *throughput IPC*: all copies' instructions over the longest copy's cycles,
  i.e. the instructions per cycle the whole chip retired while the copies ran
  side by side -- the rate figure to compare between configs (on 1Row it
  equals the per-copy IPC). The span is slightly underestimated because harts
  start their copies a little apart.

The emulator's `Completed after N cycles` is raw testbench cycles, 2x the tile
clock, and includes boot; don't divide by it. `REJUDGE=1 scripts/run.sh ...`
regenerates a verdict (with the perf line) from an existing log without
re-running it:

    REJUDGE=1 LOGFILE=<emulator>/output/spec95-equivalent/<name>.log scripts/run.sh <port> <workload> <nharts> <emulator> <emulator>/output/spec95-equivalent/<name>
    cp <emulator>/output/spec95-equivalent/<name>/<port>.verdict <emulator>/output/spec95-equivalent/<name>.verdict

A verdict's first line is `RESULT: CORRECT` only if the emulator reported
PASSED, every hart exited 0 (its `R_` line), and **every copy's output matches
the native reference** in `reference/<WORKLOAD>/` (`scripts/compare.py`; only
differences the ports' PORT-NOTES document as compiler/libm dependent are
tolerated, e.g. bsmbench's residual digits, miniweather's wall-clock line).
A PASSED on its own is not enough: an early version of this port printed
PASSED while computing wrong answers.

How long it takes: the 16-hart `Inten1CoreConfig` emulator simulates about
4,300 raw cycles/s here. Measured/projected for 16 copies:

| Port | 1Row, 1 copy (raw cycles) | extra_tiny instrs/copy (vs tiny) | 1Core extra_tiny, projected |
|---|---|---|---|
| libcint | 226 M (tiny) | 1.6 M (0.07x) | ~6 h |
| gcc-cc1 | 167 M (tiny) | 7.0 M (0.53x) | ~13 h |
| perl-perl4 | 216 M (tiny) | 5.8 M (0.28x) | ~22 h |
| xlisp | 157 M (tiny) | 8.7 M (0.78x) | ~22 h |
| miniweather | 35 M (tiny) | 3.1 M (1.0x) | ~23 h (measured, = tiny) |
| bsmbench | 336 M (tiny) | 12.9 M (0.43x) | ~1.5 days |

## Checking without the RTL

`make spike-check WORKLOAD=extra_tiny NHARTS=16` builds a spike variant
(`-DSPIKE` runtime, `build/*-spike.riscv`) and runs it on spike with the same
DRAM map, checking every copy against the reference: minutes instead of
hours. It checks the programs, the harness and the rate mechanism, not the
RTL. `scripts/profile.py` turns a spike instruction log (`spike -l`) into a
per-function instruction profile.

## How extra_tiny was chosen

Each port's problem was cut with its own knob until either it fitted a day of
16-hart RTL simulation or its kernel share got too low, measured on spike:
instructions of a near-null run (start-up only) against the candidate, and
per-function profiles.

- **libcint**: 1 centre instead of 2 (16 shell quartets instead of 256);
  about 75% of the copy is integral code.
- **gcc-cc1**: a 1-group generated input instead of 2; start-up is 11%.
- **perl-perl4**: `primes.pl 100 20` instead of `400 100`; start-up 11%.
- **xlisp**: `tak (10 6 2)`, 4 derivative repetitions, 60-element lists, 1
  sort pass; interpreter start-up (including `init.lsp`) is about 20%.
- **miniweather**: unchanged from tiny. Start-up is 1.25 M of 3.1 M
  instructions and each time step about 0.9 M, so one step fewer would make
  the run mostly start-up.
- **bsmbench**: half the lattice (2x4x4x4) and one Dphi iteration. Its cost
  is dominated by the precision test's CG inversion, which scales with the
  lattice volume; at this size about 65% of the run is the Dirac operator and
  spinor algebra, the rest is input parsing and random-field set-up. A
  quarter lattice would fit a day but drop the kernel share to about half.

`make reference WORKLOAD=<size>` regenerates the native references
(`scripts/make_reference.sh`: host gcc, same sources, macros and inputs,
without `-ffast-math`); add a size in `scripts/workloads.sh` and generate its
reference before using it.

## Requirements on the rest of the tree

- `riscv-tests/common/syscalls.c`: `memcpy`/`memset` carry
  `optimize("no-tree-loop-distribute-patterns")`. Without it GCC 10 compiles
  `memset`'s byte loop into a call to `memset` itself, and any unaligned
  `memset` recurses until the stack runs into `.text` (the build checks this).
- `riscv-tests/common/crt.S`: per-hart stack size overridable with
  `-DHART_STACK_SIZE` (the ports need MBs, not the default 127 KB).
- `src/main/resources/csrc/emulator.cc`: with DRAMSim3, the program image is
  loaded once by `init_ram()` and not again through the debug module
  (`postload_dtm_t`). The second load writes 8 bytes per debug-module round
  trip and takes hours of simulation for these images before the program
  starts.
