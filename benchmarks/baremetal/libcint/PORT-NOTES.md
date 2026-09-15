# libcint -- the quantum chemistry workload, bare metal

Upstream: `../../qchem-libcint/libcint` (Apache-2.0).  **Not edited.**

## What this port adds

| File | Why |
|---|---|
| `mkheaders.sh` + `gen/` | libcint's cmake build produces two headers with `configure_file`: `cint.h` (version substitution) and `cint_config.h` (`#cmakedefine` resolution).  cmake is not used here -- cross-compiling a bare-metal target through it costs more than compiling the source list directly -- so this script does the same substitutions into `gen/`. |
| `libcint-driver.c` | The workload.  Builds a small molecule and basis in code, then evaluates `int2e_ip1_sph` -- the gradient of `(ij\|kl)` with respect to the first centre -- over every shell quartet, accumulating checksums. |
| `bm_longdouble.c` | `expl`, `erfl`, `erfcl`.  See below. |

Only the one small `.dat` table `rys_roots.c` needs; no polynomial-fit or F12
sources: see "What is deliberately
not built".

## Why this kernel

`int2e_ip1` is the gradient of the two-electron integral `(ij|kl)` with
respect to the first centre -- the algorithm described in H. B. Schlegel,
*J. Chem. Phys.* **77**, 3676 (1982).  The machine characteristics are why it
is here: libcint's autocode routines are long runs of straight-line
double-precision arithmetic with very high register pressure and almost no
control flow, which is a register-allocation and scheduling stress test of a
kind nothing else in this suite provides.

## Running

    make run-spike SIZE=tiny
    ../scripts/run-verilator.sh libcint.riscv

Angular momentum drives cost much harder than centre count, so the ladder
turns d shells on before adding a third centre:

| size | basis | quartets | derivative values | instructions |
|---|---|---|---|---|
| tiny | 2 centres, s+p | 256 | 12,288 | 23 M |
| small | 2 centres, s+p+d | 1,296 | 373,248 | 154 M |
| ref | 3 centres, s+p+d | 6,561 | 1,594,323 | 779 M |

No input files: the molecule and basis are built in the driver, which keeps
the benchmark free of both an input dependency and any licensing question
about basis-set data.

## Verification

The same driver and sources build natively against the same generated
headers, so the checksums compare directly.  At `SIZE=tiny`:

| | target (spike) | host (gcc -O2) |
|---|---|---|
| `abssum` | 1.52702573244824e+02 | 1.52702573244824e+02 |
| `sqsum` | 5.81639684601666e+01 | 5.81639684601666e+01 |
| `sum` | 3.28903571045203e-15 | 3.39311911901063e-15 |

`abssum` and `sqsum` agree to every printed digit.  `sum` does not, and should
not: the derivative of the energy with respect to a translation of the whole
system is zero, so that accumulator is pure cancellation -- both values are
round-off noise against terms of order 1e+2, and their agreeing to 1e-15
relative is the actual result.  It doubles as a physics check: if `sum` came
out large, the port would be wrong.

## Long double: a real limitation, measured rather than assumed

riscv64's `long double` is binary128.  libgcc provides the arithmetic, but
newlib provides no `expl`, `erfl` or `erfcl`, and libcint uses them in its
second-tier precision path: when the double-precision Rys root finder loses
accuracy it retries in long double, and with quadmath available it would retry
again in `__float128`.

`bm_longdouble.c` falls back to the double versions, so that retry no longer
buys extra precision.  Rather than assume this is harmless, every call is
counted and the driver prints the count.  **It is zero at all three sizes** --
the fallback is never reached with these basis sets, so the numbers above are
unaffected.  A workload with harder integrals (very diffuse or very tight
exponents, higher angular momentum) could reach it, and that counter is how
you would find out.

## What is deliberately not built, and a correction

The source list is upstream's base `cintSrc` set from `CMakeLists.txt`.  These
are left out, all of them upstream options that default to off:

- `src/polyfits.c`, `src/sr_rys_polyfits.c` (`WITH_POLYNOMIAL_FIT`)
- `src/g2e_f12.c`, `src/stg_roots.c` (`WITH_F12`)
- `src/cint4c1e.c`, `src/g4c1e.c` (`WITH_4C1E`, which upstream notes is buggy)

That matters for repository size, and corrects what
`../../PROVENANCE.md` said earlier.  Those four files are the only users of
`src/roots_xw.dat` and `src/sr_roots_part*.dat` -- 123 MB of `#include`d
data tables, which is nearly the whole vendored tree.  The earlier note claimed
they could not be dropped without a code change; that was wrong.  The calls
into them are guarded by `#ifdef WITH_POLYNOMIAL_FIT` and `#ifdef WITH_F12`
inside `src/rys_roots.c`, so with those options off nothing references them,
which this port demonstrates: it links clean without any of them.

This was acted on: the nine `.dat` files have been removed from the vendored
tree, taking `qchem-libcint/` from 127 MB to about 4 MB.  The six sources were
kept, so restoring the data files is all that enabling those options requires.
The removal is declared under Apache-2.0 section 4(b) in
`../../qchem-libcint/ORIGIN.md`, with checksums.  This port was rebuilt and
rerun afterwards: it links clean and its checksums are unchanged to every
printed digit.

**One `.dat` file must stay.**  `src/rys_roots.c` is built, and it
`#include`s `src/roots_for_x0.dat` (76 KB).  A blanket `*.dat` exclusion
therefore breaks the build -- found the hard way when this port was built on
a second host from a tree packed that way:

    rys_roots.c:19:10: fatal error: roots_for_x0.dat: No such file or directory

`WITH_CINT2_INTERFACE` is also off, so the port calls the modern
`int2e_ip1_sph` rather than the v2-compatibility `cint2e_ip1_sph` wrapper.

## The Linux build

`make BM_ENV=linux` drops `bm_longdouble.c`: `expl`, `erfl` and `erfcl` are
there only because newlib does not provide them, and glibc does.  The driver's
`longdouble_fallback_calls` line goes with it, since there is no fallback to
count -- it was always 0 bare metal anyway, which is the point of printing it.

Cross-checking the two builds is a good test of this port, because the whole
workload is a number: `abssum` and `sqsum` agree to every printed digit.
`sum` does not, and should not -- it is a sum of ~12 k signed values that
cancels to about 1e-16, so its printed digits are round-off, and the fact that
it lands near zero in both builds is the actual check on it.
