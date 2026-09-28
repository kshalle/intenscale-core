# bsmbench -- the lattice gauge theory workload, bare metal

Upstream: `../../lattice-bsmbench/BSMBench` (BSD-3 + citation clause).
**Not edited.**

## What this port adds

| File | Why |
|---|---|
| `shim/suN.h`, `shim/suN_types.h`, `shim/suN_repr_func.h` | Upstream's build *copies* `su2*.h` over these names inside its own `Include/` directory as generated artifacts (see `BSMBench/Include/Makefile`).  These three headers `#include` the `su2` originals instead, so NG = 2 is selected without writing anything into the vendored tree. |
| `Makefile` | Compiles the 20 upstream sources directly with upstream's `MkFlags.proto` macros minus `-DWITH_MPI`, rather than driving `make.sh` and its per-directory makefiles. |
| `workload/bsmbench-{tiny,small,ref}.input` | Our own parameters.  Upstream's own sets are sized for supercomputers -- the smallest is a 64x32x32x32 lattice -- and would take days in RTL. |

No stub file at all: the non-MPI configuration needed nothing beyond what the
harness already provides.  This was the cleanest of the four ports -- it built
on the first attempt.

## Running

    make run-spike SIZE=tiny
    ../scripts/run-verilator.sh bsmbench.riscv \
        -i workload/bsmbench-tiny.input -o /dev/stdout

`-o /dev/stdout` matters.  BSMBench redirects its logger to `mesons.out` by
default, and fesvr will happily create that file on the host -- so a run
appears to produce no output at all while quietly writing a log next to the
binary.  Passing `/dev/stdout` sends it to the console instead, which is what
you want while watching an RTL run.

Measured on spike:

| size | lattice | cycles | precision test |
|---|---|---|---|
| tiny | 4x4x4x4 | 31 M | passed, delta 1.488e-07 |
| small | 8x4x4x4 | 171 M | passed, delta 1.799e-04 |
| ref | 8x8x8x8 | 1432 M | passed, delta 4.356e-04 |

## Sizing: the clock is the knob

BSMBench sizes its own run.  Each test doubles its iteration count until its
measured wall time exceeds `MIN_TIME`, which upstream fixes at **400 seconds**
in `bsmbench.c` -- and because that is a plain `#define`, not an `#ifndef`
guard, `-DMIN_TIME=...` cannot override it.

On a bare-metal target "wall time" is whatever the harness computes from
`mcycle`, so the honest lever is to tell the target what its clock rate is:
`BM_CPU_HZ`.  400 simulated seconds then costs `400 * BM_CPU_HZ` cycles, and
the three sizes pick 10 kHz / 100 kHz / 1 MHz.  The lattice in the input file
sets the *other* half of the cost, the fixed precision test, which does not
scale with the clock.

Two consequences:

- **Every FLOP/s figure BSMBench prints is meaningless here**, as is the
  "times that of a Blue Gene/P node card" ratio.  The FLOP counts in the input
  files are zero and the Blue Gene reference figures are one (rather than
  zero, which produced `nan`).  Read the harness's `[bm] ... cycles=` line.
- Building the harness for a sub-MHz clock exposed a bug in it: the time
  functions divided by `BM_CPU_HZ / 1000000`, which is zero below 1 MHz.  They
  now scale the remainder instead, so any clock rate works.

## Verification

The precision test is a genuine correctness check and not just a smoke test:
it applies the Dirac operator, inverts it with conjugate gradient, and reports
the residual.  It **passes** on the target at all three sizes.

The same sources also build natively with the same shim and macros, which
gives a direct comparison:

| | delta (4x4x4x4) |
|---|---|
| target (spike) | 1.488e-07 |
| host (gcc -O2) | 1.612e-07 |

Both pass upstream's own tolerance.  Unlike the perl port, this is not
byte-identical and should not be expected to be: delta is the residual of an
iterative solve, so it is sensitive to floating-point association order.  The
verdict, not the digits, is the check here.

## Licence obligation, repeated here because it is easy to miss

BSMBench's licence is BSD-3 **plus a citation clause**: any publication using
results derived from it must name the BSMBench package, give its URL, and cite
the two Phys. Rev. D papers listed in
`../../lattice-bsmbench/ORIGIN.md`.  That includes datasheets and blog
posts, not just papers.

## The Linux build, and the 400-second problem

BSMBench decides how much work to do by timing itself: each test doubles its
iteration count until its own measurement passes `MIN_TIME`, which upstream
fixes at 400 seconds in `Bench/bsmbench.c` and does not expose on the command
line.  It reads the clock with `time(NULL)` -- once before the loop, once per
doubling round -- so after *r* rounds it has done 2^r - 1 iterations.

Bare metal there is no wall clock, so `common/htif_syscalls.c` derives
`time()` from `mcycle` and the clock rate the target is told it has,
`BM_CPU_HZ`.  400 simulated seconds then costs `400 * BM_CPU_HZ` cycles, which
is what lets a run finish in a simulator at all, and it is why each size here
pairs a lattice with a clock rate.

Under Linux, `time()` is the real clock, and 400 real seconds per test on an
emulated target is not a benchmark anyone will wait for.  `bm_vtime.c` wraps
`time()` (`-Wl,--wrap=time`, Linux build only) with a **synthetic
deterministic clock**: each reading advances it by exactly `BM_VTIME_STEP`
seconds, with no timing involved.  The number of doubling rounds is then
`floor(400 / BM_VTIME_STEP) + 1` on every machine, and the Makefile sets the
step per size so that it equals the number of rounds the bare-metal run does:
200 for `tiny` (3 rounds, 7 iterations), 100 for `small` and `ref` (5 rounds,
31 iterations).

### Why not derive the clock from the machine's speed

That was the first version of this file, and it was wrong.  It measured an
instructions-per-microsecond rate at startup and scaled CPU time by it, on
the theory that this reproduced the bare-metal cycle budget.  It does not:
the ratio between a tight calibration loop's speed and this workload's speed
is not constant across machines or qemu builds.  The same `tiny` run did

    7 iterations   bare metal (spike)
    1 iteration    Linux, host A
    3 iterations   Linux, host B

-- caught by building and running this port on a second machine.  A benchmark
whose problem size changes with the machine measuring it cannot be compared
with anything, including itself on another day.  The deterministic step has
no such freedom: `tiny` is 7 iterations everywhere.

### What this means for the numbers

The "test completed in N seconds" figures are synthetic, and so are
BSMBench's FLOP/s -- as they always were in this port, in both environments.
Read the harness's `[bm]` line instead.

What *is* comparable, and now matches across environments and hosts, is the
work done: iteration counts identical to bare metal at all three sizes, and
the precision test passing with the same delta on every Linux host
(1.612e-07 / 1.933e-04 / 4.139e-04 for tiny / small / ref).  The bare-metal
deltas differ slightly (1.488e-07 / 1.799e-04 / 4.356e-04) because the
floating-point library and compiler differ, not because the work does.

BSMBench's licence is BSD-3 **plus a citation clause**: any publication using
results derived from it must name the BSMBench package, give its URL, and cite
the two Phys. Rev. D papers listed in
`../../lattice-bsmbench/ORIGIN.md`.  That includes datasheets and blog
posts, not just papers.
