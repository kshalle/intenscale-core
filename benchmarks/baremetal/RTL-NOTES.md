# Running the suite on RTL

Everything here was established by running it, on two hosts, with the ports in
`../baremetal`.  It covers how to build the emulator, how to run a benchmark on
it, what the numbers mean, and one unresolved platform question that currently
limits RTL to a functional check rather than a measurement.

## Summary

| | state |
|---|---|
| Ports functional on RTL | yes -- selftest, xlisp, miniweather all `*** PASSED ***` |
| Ports functional on spike | yes -- all six, verified against native builds |
| Measurement on RTL | **not yet** -- the `[bm]` counters do not reach the console; see "The HTIF write problem" |
| Measurement on spike | yes -- exact and reproducible; use these numbers |

## Building the emulator

The prebuilt binary that came with the tree works, but its DRAMSim3 config
path is compiled in and points at the tree it was built in, so it needs
`-y <this tree>/DRAMSIM3/configs/DDR4_8Gb_x8_3200.ini`.  A binary rebuilt in
place finds its own.

Rebuilding is verilate + compile only -- the elaborated Verilog in
`emulator/generated-src/` is already there, and re-running Chisel is neither
needed nor currently possible (see below).  On a 16-core desktop it takes
about 11 minutes.

    cd open_source/i-rocket-chip/emulator
    export RISCV=<tree>/open_source/i-rocket-chip/riscv-tools
    make INSTALLED_VERILATOR=$PWD/verilator/install/bin/verilator

### Three things that make this harder than it looks

**The tree cannot rebuild its own emulator as shipped.**  Confirmed on two
independent hosts, so it is not a local quirk:

1. It pins **Verilator 4.028** and builds its own copy.  The system Verilator
   on a current distribution is 5.x, which this vintage of rocket-chip and its
   `-std=c++11` C++ harness will not build against.
2. **Verilator 4.028 does not build with bison 3.8.**  Its `bisonpre` wrapper
   no longer rewrites an `#include "verilog.h"` in the generated parser.  A
   one-line wrapper header fixes it:

       for d in src/obj_opt src/obj_dbg; do
         echo '#include "V3ParseBison.h"' > $d/verilog.h
       done

3. Its download URL points at veripool, which no longer serves it.  The
   tarball is on GitHub: `verilator/verilator/archive/refs/tags/v4.028.tar.gz`.

Before release, pin bison, vendor the tarball, or move to a Verilator that
current bison can build.

**Copying the tree updates mtimes.**  Every `.scala` then looks newer than the
elaborated `generated-src/*.fir`, so make attempts a full Chisel
re-elaboration -- which fails, because it wants sbt and a firrtl.jar that is
not in a staging copy.  Touch the generated artifacts in dependency order
(`.fir`, `.d`, then `.conf`, then `.v`, then `.behav_srams.v`) and it builds.

**`emulator/output/*` are dangling symlinks**, created relative to the
emulator directory rather than to `output/`.  Run tests from
`../riscv-tests/isa/<test>` directly.

## Running a benchmark

    make -C xlisp BM_RTL=1 SIZE=tiny
    scripts/run-verilator.sh xlisp/xlisp.riscv -b < xlisp/workload/xlisp-bench-tiny.lsp

Note the file name: RTL runs the **bare-metal** build, `<port>/<port>.riscv`.
`BM_ENV=linux` produces `<port>/<port>-linux.riscv`, which is a static Linux
binary and has nothing to do with this document -- it expects a kernel, and
would not get past the first system call here.

`scripts/run-verilator.sh` picks the emulator, passes the DRAMSim3 config, and
runs it under `stdbuf -oL`.  Override `BM_EMULATOR`, `BM_FESVR_LIB`,
`BM_DRAMSIM3` or `BM_RAM` for another host.

Two things to know when reading a run:

- **`*** PASSED ***` is the success signal, not the exit status.**  The
  emulator does not exit when the target does -- its JTAG remote-bitbang
  listener keeps the process alive -- so a successful run ends with the outer
  `timeout` killing it and reporting 124.
- **Build with `BM_RTL=1`.**  An HTIF round trip costs ~151 k cycles here, so
  a line-buffered console can cost more than the benchmark.  `BM_RTL=1`
  buffers it in 8 KiB chunks (`setvbuf` in `common/bm_main.c`).

### Running it outside the tree this suite was developed in

**No emulator ships with this suite**, and none can: it is a ~16 MB binary
built from a different repository, from elaborated Verilog that is not here.
Everything above assumes a rocket-chip checkout with a built emulator sitting
somewhere above `benchmarks/`, which is true in the tree this was developed in
and is not true in a standalone clone of this repository.

`scripts/run-verilator.sh` locates that checkout by walking up from its own
directory looking for `i-rocket-chip` or `open_source/i-rocket-chip` -- by
walking rather than by counting `../`, because the suite has been moved once
already and a hard-coded depth breaks silently when it is.  With nothing to
find, the walk reaches `/`, and the script stops with:

    no emulator: set BM_EMULATOR

That is the expected message in a fresh clone, not a broken script.  Spike
needs none of this: `make run` works anywhere the bare-metal toolchain is
installed, and spike is where the measurements come from anyway (see
"Summary").

To run on RTL from a clone, supply the emulator yourself.  Either point
`ROCKET` at a rocket-chip checkout laid out the way the script expects:

    ROCKET=/path/to/rocket-chip \
      scripts/run-verilator.sh xlisp/xlisp.riscv -b < xlisp/workload/xlisp-bench-tiny.lsp

or name the pieces individually, which is the better route when your emulator
was built somewhere else:

    BM_EMULATOR=/path/to/emulator-freechips.rocketchip.system-<Config> \
    BM_FESVR_LIB=/path/to/riscv-tools/lib \
    BM_DRAMSIM3=/path/to/DRAMSIM3/configs/DDR4_8Gb_x8_3200.ini \
      scripts/run-verilator.sh xlisp/xlisp.riscv -b < xlisp/workload/xlisp-bench-tiny.lsp

`BM_FESVR_LIB` is not optional: the emulator links `libfesvr.so` dynamically
and records no rpath, so without it the run dies at load time rather than
with a useful message.  `BM_DRAMSIM3` matters only when the emulator's
compiled-in config path does not resolve on your machine; passing it is
harmless either way.

**Expect a stock emulator to fail on anything that does console I/O.**  The
two MSHR watchdogs described under "The MSHR watchdog, and the patched
emulator" fire after 16,384 cycles, and one HTIF round trip is about nine
times that, so a stock build asserts partway through -- on the emulator, not
in the benchmark.  `emulator-relaxed-watchdog` is a local patch to generated
Verilog in the Intensivate tree and is not something a clone inherits.  If you
hit it, that section gives the one-line Chisel fix and the two alternatives,
all of which need re-elaboration through sbt.

### Measured rates and costs

| | |
|---|---|
| Simulation rate | ~6 kHz (both hosts; Verilator is single-threaded, so this tracks single-core clock) |
| Fixed cost per run | ~1.5 M cycles (boot, image load, exit) |
| Cost per HTIF round trip | ~151 k cycles (1 write = 1.68 M cycles total, 5 writes = 2.29 M) |

At 6 kHz, one million cycles is about three minutes: `tiny` sizes are tens of
minutes to a few hours, `small` is overnight, `ref` belongs on spike.

### Do not use the emulator's whole-run cycle count as a metric

The same miniWeather binary on the same input gave, on the same host:

    41,340,527    79,755,655    45,194,483 cycles

all three passing.  The target spins on `fromhost` while the frontend server
does its work on the host, so the number of simulated cycles that elapse
during a syscall depends on how loaded the host is.  Whole-run counts are not
comparable between runs, let alone between machines.

The number to quote is the in-program `[bm] ... cycles=/instret=` line, which
brackets the workload with `mcycle`/`minstret` and excludes startup and I/O.
Which brings us to the reason there is no RTL measurement in this report.

## The HTIF write problem

**A bare-metal program's console output does not reliably reach the host, while
its exit does.**  This is a platform-level issue, not a harness one, and it is
what stops the `[bm]` counters from being collected on RTL.

What was measured:

| binary | what it does | result |
|---|---|---|
| `htifmin` | one raw HTIF write, no harness, tiny | **writes appear**, PASSED at 1.65 M cycles |
| `buftest2` | harness, buffered stdio, raw markers around it | **nothing appears**, PASSED at 5.44 M cycles |
| `buftest3` | as buftest2 but magic_mem in `.bss`, not on the stack | **nothing appears**, PASSED at 7.00 M cycles |
| `selftest` on the other host | same harness | **first six lines appear, then nothing** |

Ruled out by experiment: the Verilator version (4.028 and 4.038 behave
identically), the console buffering mode, `stdbuf` on the emulator's own
stdout, and the placement of the magic_mem buffer (stack vs `.bss`).

What the evidence says.  A console write requires the frontend server to read
the `magic_mem` buffer back out of target memory; an exit does not, because
`_exit` encodes its payload directly into `tohost`.  Writes fail while exits
work.  The programs do not hang -- `fromhost` is set and they run to
completion -- so the frontend server *is* servicing the syscall; it just acts
on a buffer whose contents it cannot see, returns an error the target ignores,
and the output vanishes.  A tiny program works and a program with more cache
pressure does not, and one host showed the first few writes working before it
stopped.

That is the signature of the frontend server reading stale memory: the
target's writes are still in the L1 D-cache when the frontend server reads
DRAM.  `__sync_synchronize()` is a `fence`, which orders accesses but does not
write back a dirty line, and this core has no `cbo.flush`.

**The open question, which needs the hardware team:** is the HTIF/DMA path in
`L2MacroTile` coherent with the L1 D-cache?  If it is not, every bare-metal
program that does frontend-server I/O on this design is affected.

Consistent with that: **the tree's own `riscv-tests` benchmarks fail while its
ISA tests pass**, and the ISA tests are precisely the ones that never use the
syscall proxy.  `median.riscv` dies on the MSHR watchdog described below;
these ports lose their output.  Both are programs that use HTIF for I/O.

To reproduce, the three probes are in
`/tmp/.../scratchpad/htiftest/` (`htifmin.c`, `buftest2.c`, `buftest3.c`) --
each prints correctly under spike, so any difference is the platform.

## The MSHR watchdog, and the patched emulator

Any longer-running program that uses HTIF trips this assertion:

    // src/main/scala/rocket/NBDcache.scala:623
    assert(dbgCntr_reg < UInt(4096*DBG_WAIT_SCALE), "No change in NB_MSHR.")

It is a watchdog: it fires when an MSHR sits in a non-invalid state without
changing for `4096 * DBG_WAIT_SCALE` cycles.  `Consts.scala:136` sets
`DBG_WAIT_SCALE = 4`, so the window is **16,384 cycles**, and `DEBUG = 2`
(`Consts.scala:185`) compiles it in.  One HTIF round trip is ~151,000 cycles,
nine times the window.

The two watchdogs with this window -- `NBDcache.scala:322` (IOMSHR) and
`:623` (NB_MSHR) -- use `4096*DBG_WAIT_SCALE`, while their two siblings,
`NBDcache.scala:2519` and `ctxtUnit.scala:2476`, use
`4096*16*DBG_WAIT_SCALE` = 262,144 cycles, comfortably above a round trip.
The missing `*16` looks like an oversight rather than a tighter bound on
purpose.

The fix is one line of Chisel and is yours to choose: give those two the same
`*16`, raise `DBG_WAIT_SCALE`, or elaborate benchmark builds with
`DEBUG != 2`.  All three need re-elaboration through sbt.

Until then, `emulator-relaxed-watchdog` raises just those two thresholds in a
patched copy of the generated Verilog (`30'h4000` appears exactly twice in the
45 MB file, both of them these watchdogs), leaving every other assertion in
the design live.  It is built alongside the normal emulator and
`scripts/run-verilator.sh` prefers it.  Use the normal emulator for
verification work.
