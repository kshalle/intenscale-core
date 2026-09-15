# Port status

All six workloads run bare metal, verified on spike against native builds of
the same source.  Three are also confirmed running on RTL; RTL is a
functional check rather than a measurement path today, for the reason in
"RTL status" below.

All six also build and run as **static Linux binaries** (`BM_ENV=linux`),
verified under `qemu-riscv64`, and the two builds cross-check each other --
see "Linux builds" below.

Everything here was established by building and running it, not by inspection.
`RTL-NOTES.md` covers the emulator; each port's `PORT-NOTES.md` covers what it
needed and why.

## Done

| Port | Workload | Verified | Notes |
|---|---|---|---|
| `selftest` | harness smoke test | spike + **RTL** | argv, heap, libm, setjmp, host file read/write/seek/stat, counters |
| `xlisp` | Lisp interpreter | spike (3 sizes) + **RTL** | 29 of 30 upstream files compiled unmodified |
| `miniweather` | atmospheric dynamics | spike (numerics vs host) + **RTL** | upstream source completely unmodified |
| `perl-perl4` | Perl interpreter | spike, output byte-identical to host perl 5.40 | parser generated at build time; see its PORT-NOTES for the `-I-` trap |
| `bsmbench` | lattice gauge theory | spike, upstream's own precision test passes at all three sizes | built on the first attempt; needed no stubs of its own |
| `libcint` | quantum chemistry | spike, checksums identical to a host build | workload is a driver we wrote; needs three long-double math fallbacks, never reached |
| `gcc-cc1` | C compiler | spike, SPARC assembly byte-identical to a host build | the only port that cannot compile the vendored tree unmodified -- see its PORT-NOTES |

All seven (six workloads plus the selftest) build for both environments and
run in both:

| Port | bare metal (spike) | Linux (qemu-riscv64) | agreement between them |
|---|---|---|---|
| `selftest` | pass | pass | `argv[0]` only |
| `xlisp` | pass | pass | one line on a different stream (fd 2) |
| `miniweather` | pass | pass | `d_mass` differs at 1e-15 |
| `perl-perl4` | pass | pass | **byte-identical** |
| `bsmbench` | pass | pass | clock-sized, so iteration counts differ by design |
| `libcint` | pass | pass | cancelling `sum` ~1e-16; `abssum`/`sqsum` identical |
| `gcc-cc1` | pass | pass | **byte-identical**, including 419 lines of SPARC asm |

Measured instruction counts (spike, bare-metal build).  These are the numbers
to quote: exact, reproducible, and independent of host load -- unlike the
emulator's whole-run cycle counts, and unlike anything a Linux run under an
emulator can report (see "Linux builds").

All six `ref` figures below were re-measured during the two-host work and are
unchanged.  They are **bare-metal** counts and do not transfer to the Linux
build -- see README.md, "Do not carry an instruction count between the two
environments", where `perl-perl4` at `ref` shows why.

    selftest                    0.2 M
    miniweather  tiny      5.1 M
    miniweather  small    50.8 M
    miniweather  ref   31,031.6 M   (upstream's own problem size, 100x50, 1000 s)
    xlisp          tiny     11.2 M
    xlisp          small    72.4 M
    xlisp          ref     468.5 M
    perl-perl4        tiny     21.5 M (primes) /   9.7 M (anagram)
    perl-perl4        small    89.0 M (primes) /  63.8 M (anagram)
    perl-perl4        ref     709.4 M (primes) / 406.3 M (anagram)
    bsmbench   tiny     30.9 M   (4x4x4x4,  precision delta 1.488e-07)
    bsmbench   small   170.8 M   (8x4x4x4,  precision delta 1.799e-04)
    bsmbench   ref    1432.0 M   (8x8x8x8,  precision delta 4.356e-04)
    libcint     tiny     23.1 M   (2 centres s+p,     256 quartets)
    libcint     small   154.3 M   (2 centres s+p+d,  1296 quartets)
    libcint     ref     779.0 M   (3 centres s+p+d,  6561 quartets)
    gcc-cc1           tiny     13.2 M   (121-line input  ->   419 lines of sparc asm)
    gcc-cc1           small    50.7 M   (463-line input  ->  1652 lines)
    gcc-cc1           ref     406.6 M   (3655-line input -> 13068 lines)

## Linux builds

`BM_ENV=linux` produces `<port>/<port>-linux.riscv`: `-static`, no interpreter
and no `DT_NEEDED` entries at all, so it runs on any RISC-V Linux with nothing
installed.  The same benchmark sources compile in both environments; only four
ports need anything environment-specific, and each declares it rather than
branching (see README.md, "Two environments").

The two builds are each other's strongest available check, and the two most
demanding ones pass it outright: `perl-perl4` and `gcc-cc1` produce
byte-identical output across a different libc, a different compiler (gcc 9.2
vs 13.2) and a different execution environment.

**The bare-metal instruction counts reproduce exactly on a second host.**
This matters more than the Linux cross-checks, because these are the figures
the suite actually quotes.  Built and run on host B with its own independently
installed riscv-tools toolchain, under its own spike:

    port          host A               host B
    selftest              204,806              204,806
    gcc-cc1           406,534,672          406,534,672
    xlisp             468,494,873          468,494,873
    perl-perl4        709,389,752          709,389,752
    libcint           779,029,734          779,029,734
    bsmbench        1,432,064,854        1,432,064,854
    miniweather   31,031,580,358       31,031,580,358

Not approximately -- the same integer, to the last digit of 31 billion, on a
different CPU with a different glibc.  The binaries explain why: the
**loadable image is byte-identical** across the two hosts (`objcopy -O binary`
md5 `4d204cc493870b2d14771965772f7e98` for selftest on both), every loadable
section matches in size, and the two toolchains' `libc.a` have the same md5.
The ELF *files* differ by 40 bytes, all of it non-loadable metadata holding
the build path (`/home` against `/home6`).

So the measurement is a property of the suite, not of the machine that ran
it -- which is what a benchmark's headline number has to be.

Getting there needed one thing the docs had understated: host B had no
`libmpfr.so.4` **anywhere**, and the riscv-tools gcc 9.2.0 `cc1` will not
start without it.  `bm.mk`'s search covers Ubuntu snap paths and `/usr/lib`
and quietly finds nothing on such a host.  README.md now treats it as a
prerequisite and documents `BM_MPFR4`; that override is verified to work
unaided, with nothing set in the environment.

**Verified on a second host.**  All seven were also built from source and run
on host B (96-core x86_64, glibc 2.35, against host A's 2.41) under the same
qemu 9.0.2.  All seven binaries came out **byte-size identical** to
host A's, and six of seven produced **identical output**.  The seventh was
`bsmbench`, and the difference was a real defect rather than noise: its Linux
build sized its own run from the machine's measured instruction rate, so the
`tiny` run did 7 iterations bare metal, 1 on host A and 3 on host B.  Its clock
is now synthetic and deterministic (a fixed step per reading, see its
PORT-NOTES), the iteration counts match bare metal at all three sizes, and
the two hosts now agree exactly.

**All three sizes on both hosts.**  `tiny`, `small` and `ref` were each built
and run on host A and host B -- all 42 runs with the current counter check --
and every port's output is identical between the two hosts at every size:
**21 comparisons, no differences.**  Every one of the 42 returns
`counters=unrelated`, so not one reports a `cycles=` figure under qemu.

Worth noting for anyone reproducing the counter bug: it never appeared on
host B.  Its cores are slower, so the old probe's tight loop always read far
enough from the expected count to be rejected.  The false accepts needed a
fast, *idle* host -- which is the worst possible property for a bug of this
kind, since that is the machine someone would choose to measure on.  Against bare
metal, `small` behaves exactly as the other sizes do: five of seven identical,
`bsmbench` differing only in its precision-test delta (1.799e-04 vs
1.933e-04) and `libcint` only in its cancelling `sum` (-7.418e-13 vs
-7.276e-13, with `abssum` and `sqsum` matching to every digit) plus the
bare-metal-only `longdouble_fallback_calls` line.

Linux CPU time at `small`, host A / host B:

    selftest         0.6 ms /    0.8 ms
    perl-perl4        19 ms  /    24 ms
    xlisp             34 ms  /    47 ms
    miniweather       32 ms  /    49 ms
    gcc-cc1          134 ms  /   126 ms
    libcint          152 ms  /   213 ms
    bsmbench         202 ms  /   288 ms

Do not read too much into small differences between the two columns: an
earlier pass measured host A while spike was running on it and got figures
up to 2.2x out (libcint 147 ms against 321 ms on the same host, minutes
apart).  qemu CPU time is a rough guide, not a metric.

**All three sizes, including `ref`.**  The `ref` sizes were built and run on
both hosts under qemu, and all seven produce output identical between them --
including the 13,068 lines of SPARC assembly `gcc-cc1` emits at `ref`.  CPU
time for a `ref` run, host A / host B:

    selftest         0.7 ms /     0.8 ms
    perl-perl4        79 ms  /    114 ms
    xlisp            182 ms  /    232 ms
    gcc-cc1          460 ms  /    454 ms
    libcint          714 ms  /   1017 ms
    bsmbench        1816 ms  /   2356 ms
    miniweather    18483 ms  /  28533 ms

That is the practical argument for the Linux build: `ref` is seconds under
qemu where it is minutes on spike, so the largest size is cheap to run for
functional checking.  `miniweather` at `ref` makes the case on its own -- it
is 31.0 G instructions, twenty times the next largest workload here, and 158
seconds on spike against 18 under qemu.  Host B is the slower machine per
core (its tight-loop ceiling is 2.69 G instructions/s against host A's 6.07),
which is most of the spread; `gcc-cc1` not following that pattern at all is a
reminder that what qemu CPU time measures varies by workload.

**At `ref`, the two environments agree more closely than at `tiny`** -- which
is what should happen, since a longer run averages out the round-off that
dominates a short one.  Bare metal (spike) against Linux (qemu), `ref`:

| Port | agreement at `ref` |
|---|---|
| `selftest` | identical |
| `xlisp` | identical |
| `perl-perl4` | identical |
| `gcc-cc1` | identical -- 13,068 lines of SPARC assembly |
| `miniweather` | identical, including `d_mass = -2.031407e-14` and `d_te = 1.005165e-04` after 31.0 G instructions |
| `libcint` | checksums identical to every printed digit (`sum`, `abssum`, `sqsum`); only the bare-metal-only `longdouble_fallback_calls` line differs |
| `bsmbench` | only the precision-test delta (4.356e-04 vs 4.139e-04), floating-point library and compiler |

`libcint` is worth singling out: at `tiny` its `sum` differed between
environments because it is a cancelling sum near 1e-16, and at `ref` all
three checksums match exactly.  `miniweather` agreeing to the last digit of
its conservation diagnostics across 31 G instructions, two compilers and two
C libraries is the strongest single correctness result in the suite.

Two things the second host caught that one host could not:

- The `bsmbench` host-dependent problem size above.  One host cannot show it.
- A blanket `*.dat` exclusion when packing the tree breaks `libcint`:
  `src/rys_roots.c` is built and `#include`s `src/roots_for_x0.dat` (76 KB).
  The nine *large* tables (123 MB) are genuinely unused, and the trim advice
  in `qchem-libcint/ORIGIN.md` now names them individually and says so.

**What a Linux run can and cannot measure.**  `rdcycle`/`rdinstret` usually
trap from user mode, and -- worse -- can answer about something else.  Under
`qemu-riscv64` they return a host-clock-derived value.

Distinguishing that from a real counter took three attempts; the first two
passed qemu and are described in README.md, "What the Linux numbers mean",
along with the two-loop check that now does the job.  The short version: a
counter is verified by showing it does not care how long the instructions
take, using two loops with identical instruction counts and a 300x
difference in execution time.  Verdicts are `hardware`, `trapped`,
`unrelated` or `unverifiable`, and appear on every reported line.  Under
qemu the verdict is `unrelated` and only `cputime_ms` is reported -- twenty
consecutive runs on each of two hosts, unanimous.

**Bare metal on spike remains the measurement path.**  The Linux build is for
functional cross-checking and for running on real RISC-V Linux hardware,
where the counters may well pass the check.

## RTL status

All the RTL detail -- how to build the emulator, how to run a port on it, the
measured costs, and the one open platform question -- is in
[`RTL-NOTES.md`](RTL-NOTES.md).  In brief:

| | state |
|---|---|
| Ports functional on RTL | **yes** -- selftest, xlisp and miniweather all `*** PASSED ***`, with the selftest's host-file artifact proving the I/O path end to end |
| Emulator | **rebuilt** from this checkout on two hosts; the original is kept as `...Inten1CoreConfig.prebuilt` |
| Measurement on RTL | **not achieved** -- the in-program `[bm]` counters never reach the console, for a platform reason described below |
| Measurement to use meanwhile | **spike** -- exact, reproducible, and verified against native builds |

Cycle counts observed (emulator `-c`, so including boot and I/O):

    selftest            26,229,728
    miniweather    41,340,527 / 79,755,655 / 45,194,483   (same binary, three runs)
    xlisp            58,759,519

Those miniweather figures are the reason not to quote whole-run counts as a metric:
the target spins on `fromhost` while the frontend server works, so elapsed
simulated cycles track host load.  A 1.9x spread on identical work.

### Why there is no RTL measurement

A bare-metal program's console output does not reliably reach the host on this
design, while its exit does.  Ruled out by experiment: the Verilator version
(4.028 and 4.038 behave identically), the console buffering mode, the
emulator's own stdout buffering, and whether the HTIF buffer sits on the stack
or in `.bss`.  What fits every observation is the frontend server reading
target memory that the core has written but not written back -- a tiny program
works, one with cache pressure does not, and one host showed the first few
writes succeeding before it stopped.

**The open question for the hardware team: is the HTIF/DMA path in
`L2MacroTile` coherent with the L1 D-cache?**  If not, every bare-metal
program doing frontend-server I/O on this design is affected -- which is
consistent with the tree's own riscv-tests *benchmarks* failing while its ISA
tests, the ones that never use the syscall proxy, all pass.

Three probes that reproduce it in minutes each (all correct under spike) are
described in `RTL-NOTES.md`.

### The MSHR watchdog

Any longer-running HTIF-using program also trips a debug watchdog whose
window (16,384 cycles) is far shorter than an HTIF round trip (~151,000).  It
is `NBDcache.scala:623`, it is compiled in by `DEBUG = 2`, and the two
watchdogs with that window are missing the `*16` their two siblings have.
Details, and the patched `emulator-relaxed-watchdog` that works around it,
are in `RTL-NOTES.md`.

### Corrections to earlier notes in this file

The counter check was wrong twice before it was right, and the second time it
had been written up here as sound.  What this file previously claimed -- that
no number could reach a `cycles=` field without being checked -- was false:
the check existed but tested the wrong property, and in one batch of seven
benchmarks on one machine it labelled three runs `counters=hardware` while
reporting instruction counts 9x to 17x the true ones.  The current check is
described in README.md; it was validated by twenty consecutive runs on each
of two hosts, and the reasoning for its tolerances is recorded next to them
in `common/bm_stats.c` with the measurements they came from.

The general lesson, since it cost three tries: to test whether a counter
counts what it claims, do not check its value against an expectation.  Check
that it is invariant under the thing it is supposed to be independent of.

A third correction, from the Linux work: the first version of the counter
probe accepted `rdinstret` if it landed within a factor of two of a known
instruction count.  `qemu-riscv64` passed that test while being wrong by
27-55%, and reported `counters=hardware` on runs whose numbers meant nothing.
Being within a factor is not evidence; the counter is exact or it is not the
counter.  The check now requires exactness.

Two explanations for the missing output were recorded here before and were
wrong; both are gone from the text above, and are noted here so nobody redoes
the work.  The first blamed the emulator's block-buffered stdout being lost
when the outer `timeout` killed it -- `stdbuf -oL` was worth adding, but it
was not the cause.  The second blamed the harness's own buffering: an earlier
`BM_RTL=1` made `_fstat` describe the console as a regular file so newlib
would fully buffer it, which is genuinely wrong (newlib then tracks a file
offset on a descriptor that cannot support it) and is now done with `setvbuf`
instead -- but that was not the cause either.  The cause is upstream of both,
in whether the frontend server can see the buffer at all.

## Remaining ports

All six are done, in both environments.  What is left is validation on real
RTL, which needs a working emulator build (see the blocker above), plus the
harness items below.

## Harness work done since the first two ports

- `bm_posix_stubs.c` collects the process/user/terminal/filesystem stubs that
  every legacy port needs, so a new port starts from a much shorter list of
  its own.  perl needed exactly one port-local stub (`sys_errlist`).
- The counters are reported through `atexit` as well as on the return from
  `main`, because perl leaves through `exit()` and printed nothing otherwise.
  gcc will do the same.
- The harness compiles cleanly as either C or C++.
- The time functions work at any `BM_CPU_HZ`, including below 1 MHz.  They
  used to divide by `BM_CPU_HZ / 1000000`, which is zero there -- found by
  BSMBench, which sizes its own run from the clock it is told it has.
- `BM_ENV` added, with the per-environment declarations (`BM_SRCS_BM`,
  `BM_CFLAGS_LINUX`, ...) that keep ports from branching on it, plus
  `common/bm-site.mk` for machine-local toolchain paths so no developer's
  home directory is baked into the build.
- Every `[bm]` line is now tagged `env=`, and the counter probe validates
  itself rather than trusting `rdinstret` to mean what it says.
- One `make run` per port drives the right simulator for the environment the
  port was built for, so the two are exercised the same way.

## Harness work that would pay off next

- `bm_stats` currently measures whole-program.  A `BM_WRAP_MAIN = 0` path
  exists for ports that want to bracket a region of interest; nothing uses it
  yet.
- No `mcause`/`mtval` trap handler: a bad access traps into the BSP's default
  handler and the run just stops.  A handler that prints the trap frame would
  save time on the perl and gcc ports.
- `selftest` does not currently defeat dead-store elimination on its heap
  test (gcc drops the 4 MB `memset` because nothing reads it back), so that
  check is weaker than it looks.
- Nothing measures a region of interest yet: every port reports whole-program
  cycles.  `BM_WRAP_MAIN = 0` exists for ports that want to bracket the
  workload itself, and `gcc-cc1` would benefit most (its startup builds the
  entire target description before it reads a line of input).
