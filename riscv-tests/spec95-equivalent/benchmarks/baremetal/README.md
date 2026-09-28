# Bare-metal ports (no Linux, runnable under Verilator)

These are the suite's workloads built to run directly on the core: no
operating system, no proxy kernel, M-mode from reset, with the frontend server
(fesvr) in spike or the rocket-chip Verilator emulator providing the console
and host file access over HTIF.

Each port also builds as a **static Linux binary**, for comparison against a
kernel and for running on a RISC-V Linux host or under qemu.  `BM_ENV`
selects which, and the two never produce the same file name or the same
output tag, so a result cannot be attributed to the wrong environment:

| `BM_ENV` | Binary | Runs on | Reports |
|---|---|---|---|
| `baremetal` (default) | `<port>/<port>.riscv` | spike, the Verilator emulator, the chip | `[bm] env=baremetal ... cycles= instret= ipc=` |
| `linux` | `<port>/<port>-linux.riscv` | `qemu-riscv64`, a RISC-V Linux host | `[bm] env=linux counters=... cputime_ms=` |

    make check-toolchain            # the environment's toolchain is usable
    make all                        # build every port, bare metal
    make BM_ENV=linux all           # ... and as static Linux binaries
    make both                       # both environments
    make run                        # build and run every port (spike)
    make BM_ENV=linux run           # build and run every port (qemu)

The Linux build is `-static`: no interpreter, no `DT_NEEDED` entries, nothing
required on the target beyond a kernel.  One caveat, in one port -- see
"What -static does and does not promise" below.  Both builds compile the
*same* benchmark sources; see "Two environments" for the four places they
differ and why.

| Directory | Workload | Verification | tiny / small / ref |
|---|---|---|---|
| `selftest/` | — (harness smoke test) | runs on spike | 0.2 M instructions |
| `xlisp/` | Lisp interpreter | runs on spike | 11 M / 72 M / 470 M |
| `miniweather/` | atmospheric dynamics | runs on spike | 5 M / 51 M / 31,032 M |
| `perl-perl4/` | Perl interpreter | runs on spike, output diffs clean vs host perl | 22 M / 89 M / 709 M |
| `bsmbench/` | lattice gauge theory | runs on spike, its own precision test passes | 31 M / 171 M / 1432 M |
| `libcint/` | quantum chemistry | runs on spike, checksums match host build | 23 M / 154 M / 779 M |
| `gcc-cc1/` | C compiler | runs on spike, SPARC output byte-identical to host build | 13 M / 51 M / 407 M |

Instruction counts are from the bare-metal build on spike.

`PORT-STATUS.md` records what is done, what is left, and what each remaining
port needs.

## How the harness works

The toolchain is `riscv64-unknown-elf-gcc` **from riscv-tools/rocket-tools**,
whose newlib ships the HTIF board-support package (`libgloss_htif.a`,
`htif.specs`, `htif.ld`).  The distribution's `gcc-riscv64-unknown-elf`
package will not do: it has no newlib at all.  `-specs=htif.specs` gives a
static image linked at 0x80000000 with newlib but no host startup files, and
`-specs=htif_argv.specs` makes the frontend server pass a command line, so a
benchmark can be invoked exactly as it would be on a host:

    spike --isa=rv64imafdc xlisp.riscv -b < workload/xlisp-bench-tiny.lsp

That gives a port the C library it was written against -- stdio, malloc,
libm, setjmp -- with no OS on the target.  `common/` then fixes the three
places where that environment is not enough:

**`htif_syscalls.c` -- file I/O and a real heap.**  The HTIF BSP implements
only `_write`, `_exit` and `_sbrk`; `_open`, `_openat`, `_read`, `_close`,
`_lseek` and `_fstat` are stubs that set `errno = ENOENT`, so any benchmark
that reads an input file fails on the target while working on the host.  This
file implements them against fesvr's syscall proxy, which does the host I/O.
Two translations are the whole subtlety: newlib's `open` flags are BSD-ish
(`O_CREAT` 0x200) while fesvr hands them to the host's `openat` which expects
Linux values (0x40), and fesvr writes the Linux `struct stat` layout, not
newlib's.  It also replaces `_sbrk`: `htif.ld` reserves a 128 KiB heap, which
no real workload survives, so the heap here runs from above the stacks to
`BM_HEAP_MB` (default 192 MiB) from the base of DRAM.

**`bm.mk` -- stack size.**  `htif.ld` sizes each hart's stack from
`__stack_size_min`, which it *provides* as 24 KiB and which overflows silently
under an interpreter or a compiler.  A `--defsym` overrides the provide;
default here is 1 MiB, and the heap floor moves with it.

**`bm_main.c` -- measurement.**  Ports are linked with `-Wl,--wrap=main`, so
cycles and instructions are counted around the benchmark's own `main` without
editing vendored source, and every port reports the same
`[bm] env=... name=... cycles=... instret=... ipc=...` line.  Wrapping at link time
rather than with `-Dmain=...` is deliberate: C++ grants `main` an implicit
`return 0`, that rule is keyed to the function actually being called `main`,
and renaming it turns a benchmark whose `main` lacks a return statement
(miniWeather is one) into undefined behaviour.

Two harness decisions are worth knowing because they change what a port sees:

- **`isatty` always reports false.**  There is no terminal on the target, and
  programs branch on `isatty` to choose between an interactive session and
  batch processing.  A bare-metal benchmark always wants the batch path.
  (XLISP otherwise sets up raw-mode tty handling and blocks forever.)
- **stdout is line buffered.**  `_fstat` reports the standard descriptors as
  character devices whatever the host says, because when the host has stdout
  redirected to a file newlib picks full buffering -- and an RTL run then
  shows nothing for minutes, or nothing at all if it is killed early.  Each
  line costs one HTIF round trip, so workloads should print sparingly.
- **stderr arrives on the host's stdout.**  fesvr routes the target's fd 2 to
  its own fd 1, so a bare-metal run has one console carrying both streams,
  while the Linux build keeps them apart.  This is the reason for the one
  cosmetic difference between the two builds' output: XLISP announces
  `; loading "init.lsp"` on `*debug-io*`, which is fd 2.  Compare stdout, or
  redirect `2>&1` on the Linux side.

## Two environments

`BM_ENV=linux` compiles the same benchmark sources with a
`riscv64-unknown-linux-gnu` toolchain and `-static`.  Nothing in `common/` is
needed except the statistics reporter: the kernel provides the file I/O, the
heap, the stack and the POSIX calls that `htif_syscalls.c`, `bm_posix_stubs.c`
and the linker script stand in for bare metal.  Four ports need something for
one environment and not the other, and each declares it rather than branching
on `BM_ENV` (`BM_SRCS_BM`, `BM_CFLAGS_LINUX` and friends -- see `common/bm.mk`):

| Port | Difference |
|---|---|
| `xlisp` | `shim/sys/termios.h` and the `tcgetattr`/`tcsetattr` stubs are bare metal only; glibc has both, and the shim must stay off the include path so it cannot shadow the real header.  `STSZ` follows the stack each environment actually gives it. |
| `libcint` | `bm_longdouble.c` supplies `expl`/`erfl`/`erfcl`, which newlib lacks and glibc has. |
| `perl-perl4` | `-DI_TIME`.  perl 4's `config.h` leaves it off and `I_SYS_TIME` on, which was right for the systems it was configured against and is right for newlib: `<sys/time.h>` declared `struct tm` too.  glibc separates them. |
| `bsmbench` | `bm_vtime.c` wraps `time()`.  BSMBench decides how much work to do by timing itself against a hard-coded 400-second minimum; bare metal has no wall clock, so the harness derives `time()` from `mcycle` and `BM_CPU_HZ`.  Under Linux the wrapper supplies a *synthetic deterministic* clock -- a fixed step per reading -- so the run does the same work on every host, and the same as bare metal.  Deriving it from the machine's measured speed was tried first and gave host-dependent problem sizes; see its PORT-NOTES. |

### What `-static` does and does not promise

All seven Linux binaries have no interpreter and no `DT_NEEDED` entries, which
is what `-static` is for.  There is one honest qualification, and it is
confined to `perl-perl4`: glibc's name-service functions load their backends
with `dlopen` at run time, so linking them statically draws a warning from
`ld`.

    ld: eval.c: warning: Using 'setpwent' in statically linked applications
        requires at runtime the shared libraries from the glibc version used
        for linking

That is glibc telling the truth: `setpwent`, `endpwent`, `setgrent` and
`endgrent` are reachable in the perl binary, and *if a script called them* on
a machine without matching `libnss_*` modules, the lookup would fail (return
nothing) rather than crash.  No workload in this suite performs user or group
lookups, so those paths are never entered -- the two perl workloads produce
byte-identical output on two hosts with different glibc versions (2.41 and
2.35), which is the practical demonstration.  The other six ports link no
such function at all.

`ld` also repeats perl 4's own `mktemp` warning.  That is upstream 1991 code
in perl's `main`, unreached by these workloads, and not something this port
edits.

### What the Linux numbers mean

`rdcycle` and `rdinstret` are readable from user mode only if the kernel
enables them, and most do not: they raise `SIGILL`.  The harness probes for
that -- but probing for a fault is not enough, because they can also *answer,
about something else*.  Under `qemu-riscv64` they return a host-clock-derived
value.

Telling that apart from a real counter took three attempts, and the first two
were wrong in ways worth recording, because both looked convincing:

1. Accept a reading within a factor of two of a known instruction count.
   qemu passed while being wrong by 27-55%.
2. Demand agreement to a few percent over a tight decrement-and-branch loop,
   taking the minimum of five trials.  qemu passed *that* too -- a host clock
   ticking about once per guest instruction is precisely what a tight ALU loop
   produces.  Over five trials that loop read 0.67x to 2.65x of expected, so
   whether the minimum landed inside a 5% window was luck: in one batch of
   seven benchmarks on one machine, three were labelled `counters=hardware`
   and reported instruction counts **9x to 17x** the true ones.

The mistake in both was looking for a *value*.  What separates an instruction
counter from a clock is not what it reads on some loop but that it does not
care how long the instructions take.  So the probe now runs two loops with
the same known instruction count and very different speeds:

| | loop | time |
|---|---|---|
| A | decrement and branch, ALU only | tens of microseconds |
| B | dependent pointer chase through 16 MiB, one cache miss per load | tens of milliseconds |

A real `instret` reads 300,000 for both, because both retire 300,000
instructions.  Measured under qemu, loop B reads 306x to 315x that, five
trials running -- a signal no host clock can avoid producing, on any machine,
at any load.  Loop A is held to +-5%; loop B is allowed up to 2x, which
tolerates timer ticks adding instructions the hart really did retire while
still leaving a 150x margin against a clock.  If the 16 MiB buffer will not
allocate, the verdict is `unverifiable` and the counters are not used.

The verdict appears on every line:

    [bm] env=linux name=xlisp counters=hardware cycles=... instret=... ipc=...
    [bm] env=linux name=xlisp counters=unrelated cputime_ms=34.056
    [bm] env=linux name=xlisp counters=trapped   cputime_ms=34.056

`hardware` means both loops checked out.  `trapped` means SIGILL.
`unrelated` means they answer and were caught not counting this program --
what `qemu-riscv64` does, so **a Linux run under qemu reports CPU time only,
by design.**  Twenty consecutive runs on each of two hosts now return
`unrelated` every time; the old probe was not even self-consistent within one
batch.

The accept path is the one thing here not confirmed against hardware, for
lack of a RISC-V machine with the counters exposed: its arithmetic accepts an
exact counter, and accepts one inflated by timer ticks, but that is a
desk check.  A wrong answer in that direction costs a `cputime_ms` line
instead of a `cycles=` line, which is the safe way round.

Bare metal needs none of this: in M-mode `mcycle` and `minstret` are the
program's own, and they are exact.  **That is the measurement path.**

### Do not carry an instruction count between the two environments

The counts in `PORT-STATUS.md` are bare-metal figures.  They are not the
Linux build's counts, because the two builds are compiled by different
compilers against different C libraries: same sources, same output, different
instructions to get there.

For `perl-perl4` at `ref` this is not a caution but a measurement.  The Linux
run finishes in 79 ms of CPU on a host whose qemu ceiling -- for a
two-instruction loop with nothing else in it -- is 6.07 G instructions per
second.  So it executed **at most 480 M instructions**, where the bare-metal
build executes 709,389,752.  The bound is loose and the real figure is far
lower; the point is that no arithmetic can reconcile the two, and dividing a
Linux CPU time by a bare-metal instruction count produces a number that means
nothing.  (glibc's allocator against newlib's is the likely bulk of it: perl
4 allocates heavily, and this port uses each libc's own `malloc`.)

How large the gap is depends entirely on the workload, so do not take perl's
as a rule either.  `miniweather` at `ref` is 31.0 G instructions bare metal
and 18.5 s of Linux CPU, an implied 1.68 G instructions per second -- well
inside the ceiling, and consistent with the two builds executing a similar
number of instructions for that workload.  Same suite, same two builds,
completely different answer.

Compare instruction counts within an environment, never across.

## Porting recipe

The pattern every port here follows, in order of preference:

1. **Change nothing.**  Compile the upstream sources as they are.  29 of
   XLISP-PLUS's 30 translation units, and all of miniWeather, needed no edit.
2. **Add shim headers, not patches.**  Where upstream includes something the
   target lacks, put a header on the include path: `miniweather/shim/`
   has a single-rank `mpi.h` and a `pnetcdf.h` whose functions trap if they
   are ever reached; `xlisp/shim/sys/termios.h` supplies the one header
   newlib's riscv64-unknown-elf target omits.  This keeps the vendored trees
   byte-identical to upstream, which keeps the provenance story simple.
3. **Stub the calls that cannot exist**, in a port-local file, each failing
   the way its caller already handles: `xlisp/bm_os_stubs.c` makes
   `tcgetattr` succeed on a terminal that ignores it and `fork`/`popen` fail.
4. **Watch out for generated headers.**  If upstream generates a header at
   build time and also ships a stale copy of it (perl 4 ships `perly.h` and
   regenerates it from `perly.y`), a quoted `#include` in an upstream source
   file finds the *shipped* one, because a quoted include searches the
   including file's own directory before any `-I`.  Compile with `-I-` to turn
   that rule off and let the `-I` order decide.  Symptom when this bites: the
   build is clean and the program is completely broken.
5. **Write the workload yourself.**  Every workload here was written for this
   suite or is generated by a script in it, which keeps the release free of
   any third-party benchmark suite's material.
6. **Size it for RTL.**  Every port ships tiny/small/ref.  The Verilator
   emulator runs at about **6 kHz** (measured on two hosts; see
   `RTL-NOTES.md`), so 1 M cycles is roughly three minutes: `tiny` is for
   bring-up, `ref` is for spike, qemu and hosts -- and cheapest under qemu,
   where every `ref` size in this suite runs in well under a minute.

7. **Do not branch on the environment inside a port.**  Declare what differs
   (`BM_SRCS_BM`, `BM_CFLAGS_LINUX`, ...) and let `common/bm.mk` apply it.  A
   port's variables are set before `bm.mk` is included, so an
   `ifeq ($(BM_ENV),...)` in a port would test a variable that does not exist
   yet -- and would silently take the wrong branch rather than fail.

Host tools a port may need: `bison` (perl's parser), `awk` and `python3`
(workload and config generation).  All are build-time only.

## Verifying a port

spike is the functional oracle -- it runs the same binary the RTL will, in
seconds instead of hours:

    make -C xlisp run                    # spike
    make -C xlisp BM_ENV=linux run       # qemu

The two environments are also each other's check.  Comparing the tiny runs of
all seven ports, `perl-perl4` and `gcc-cc1` are byte-identical between them --
including 419 lines of generated SPARC assembly -- and every remaining
difference is accounted for:

- `libcint`'s `sum` (a cancelling sum, ~1e-16) differs while `abssum` and
  `sqsum` agree to every digit, which is what round-off looks like.
- `miniweather`'s `d_mass` differs at 1e-15, likewise.
- `bsmbench` reports a different precision-test delta (floating-point library
  and compiler), and synthetic "seconds" figures in both environments.  Its
  *iteration counts* match bare metal exactly at all three sizes, and match
  across Linux hosts -- see its PORT-NOTES for why that took a second machine
  to get right.
- `selftest` prints its own `argv[0]`.
- `xlisp` puts one line on a different stream (see fd 2, above).

Then diff the target's output against a host build of the same thing.  Every
port does this: miniWeather's mass and total-energy diagnostics agree
with a native build to 1e-15 (round-off, from floating-point contraction
order) and exactly at 1e-6, and the perl workloads are byte-identical to host
perl 5.40 output.  Where a workload could differ for a legitimate reason --
hash iteration order, in perl's case -- pin it down in the workload rather
than accepting a diff, or the comparison stops being useful.

Then, on RTL:

    make -C xlisp BM_RTL=1 SIZE=tiny
    scripts/run-verilator.sh xlisp/xlisp.riscv -b < xlisp/workload/xlisp-bench-tiny.lsp

**Read [`RTL-NOTES.md`](RTL-NOTES.md) before doing this.**  It covers building
the emulator (which the tree cannot currently do as shipped), the measured
costs, and one open platform question.  The short version:

- The ports **run** on RTL -- three are confirmed passing.
- The ports cannot yet be **measured** on RTL: the in-program `[bm]` counters
  do not reach the console, for a platform reason that is diagnosed but not
  resolved.
- So **spike is the measurement path** for now.  Its instruction counts are
  exact and reproducible, and each port is verified there against a native
  build.  The emulator's own whole-run cycle count is not a usable metric
  regardless -- it varies by up to 1.9x with host load, because the target
  spins on `fromhost` while the frontend server works.
- `BM_RTL=1` matters when you do run on RTL: an HTIF round trip costs about
  **151 k simulated cycles** (measured), on top of ~1.5 M cycles of fixed boot
  and load, so a line-buffered console can cost more than the benchmark.  The
  emulator runs at roughly **6 kHz**, which is the number to plan sizes
  against: `tiny` is minutes to hours, `small` is overnight, and `ref` does
  not belong on RTL at all -- run it on spike, or under qemu where every
  `ref` size here takes well under a minute.

## Host toolchain note

Two toolchains are needed, one per environment:

| `BM_ENV` | Toolchain | Requirement |
|---|---|---|
| `baremetal` | `riscv64-unknown-elf-gcc` from **riscv-tools/rocket-tools** | its newlib must ship the HTIF BSP (`libgloss_htif.a`, `htif.specs`).  A distribution `gcc-riscv64-unknown-elf` package has no newlib at all. |
| `linux` | `riscv64-unknown-linux-gnu-gcc` | must be able to link `-static`, so its sysroot needs `libc.a`. |

`make check-toolchain` and `make BM_ENV=linux check-toolchain` check exactly
those two things.  With the tools on `PATH` nothing else is required; where
they are not, copy `common/bm-site.mk.example` to `common/bm-site.mk` and set
`RISCV_ELF_BIN` / `RISCV_LINUX_BIN`.  That file is not tracked, so one
developer's paths never end up in someone else's checkout.

### `libmpfr.so.4`, a hard prerequisite of the bare-metal build

The riscv-tools gcc 9.2.0 binaries link against MPFR 3.x (`libmpfr.so.4`),
which current distributions no longer ship.  Without it `cc1` does not start
at all -- `error while loading shared libraries` -- so this is a prerequisite
of the bare-metal build, not a detail.

`bm.mk` looks for a copy in the Ubuntu core snap and in `/usr/lib`, and
symlinks what it finds into `build/compat`, rather than vendoring a binary
library into this repository.  **A host may have neither.**  One of the two
machines this suite was verified on had no `libmpfr.so.4` anywhere -- no snap,
no system copy -- and the search quietly found nothing, leaving a build that
fails on the first compile.  On such a host, put a copy somewhere and name it:

    BM_MPFR4 = /path/to/libmpfr.so.4      # in common/bm-site.mk

Any Ubuntu core snap or Debian oldstable `libmpfr4` package supplies one; it
is LGPL-3, so it is fine to copy between your own machines, and nothing in
this repository ships it.

The Linux toolchain is newer and needs none of this.
