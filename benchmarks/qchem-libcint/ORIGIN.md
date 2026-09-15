# qchem-libcint — libcint

The quantum-chemistry workload: two-electron integral derivatives, which is
long straight-line double-precision code with extreme register pressure and
almost no control flow.

## What is vendored here

`libcint/` — libcint, a general Gaussian-integral library (Qiming Sun et al.),
the integral engine behind PySCF.

- URL: https://github.com/sunqm/libcint
- Commit: `3d36c4f4e24ca5aaf91be7299f93dd541db0f50b` (2026-06-20)
- Retrieved: 2026-09-07

## Licence

**Apache-2.0.**  Full text in `libcint/LICENSE`; contributors in
`libcint/AUTHORS`.  Copyright (C) 2013- Qiming Sun; no other holders appear
anywhere in the tree.  Obligations on redistribution: keep the licence and
notices, and state any changes.  Apache-2.0 also carries a patent grant,
which is a plus for a hardware release.

## Why this implementation

The kernel of interest is `int2e_ip1` — the gradient of the two-electron
integral `(ij|kl)` with respect to the first centre.  libcint's auto-generated
integral routines are the defining shape of that computation: very long runs
of straight-line double-precision arithmetic, high register pressure, minimal
branching.  That makes it a register-allocation and scheduling stress test of
a kind nothing else in this suite provides.

The algorithm is the one described in H. B. Schlegel, *J. Chem. Phys.* **77**,
3676 (1982).  Considered and rejected: libint2 (LGPL-3, but requires a
source-generation step), Psi4 (LGPL-3) and NWChem (ECL-2.0), both far too
heavy for a simulator.  libcint is the smallest self-contained option that
still exercises the real kernel, and it needs no external BLAS —
`src/fblas.c` provides what it uses.

## Local modifications

`.git/` removed.

**Nine Rys-root data tables were removed** from `libcint/src/` (122.6 MiB).
This is a stated modification under Apache-2.0 section 4(b); see "Size note"
below for what they were, why they are not needed, and how to restore them.
The source tree is otherwise upstream and unmodified.

## Size note

This directory was 127 MB as upstream ships it, and 123 MB of that was nine
C data tables `#include`d at compile time.  They have been removed, taking the
directory to about 4 MB:

    src/roots_xw.dat            95.3 MiB   included by src/stg_roots.c
    src/sr_roots_part{0..3}_w.dat          included by src/sr_rys_polyfits.c
    src/sr_roots_part{0..3}_x.dat          included by src/sr_rys_polyfits.c

**Why they are not needed.**  Those two sources are reached only through
upstream's `WITH_F12` and `WITH_POLYNOMIAL_FIT` options, both of which default
to off, and the calls into them inside `src/rys_roots.c` sit inside the same
`#ifdef`s.  Plain two-electron integrals and their derivatives -- the kernel
this benchmark measures -- never touch range-separated Coulomb or F12
geminals.  The bare-metal port in `../baremetal/libcint` builds upstream's
base `cintSrc` list, which excludes them, and links clean.

**Verified after removal.**  The port rebuilds and runs, and its checksums are
unchanged to every printed digit against the reference recorded in
`../baremetal/libcint/PORT-NOTES.md` (`SIZE=tiny`: `abssum`
1.52702573244824e+02, `sqsum` 5.81639684601666e+01, `sum`
3.28903571045203e-15).

**`src/roots_for_x0.dat` (76 KB) is still here and must stay.**
`src/rys_roots.c` is built and `#include`s it.  Trim by naming files
explicitly, never by excluding `*.dat`.

**Restoring them.**  Fetch the nine files from upstream at commit `3d36c4f`
and drop them back into `libcint/src/`.  SHA-256, for verification:

    72cdca08395ea38bca0b38ab2eb10b6082c17bbfe804f92854d54b0c1bfd2875  roots_xw.dat
    16be7024697d456601d5b6c5bca6eb54008fe8296e4de06a220b8ebb73c9647c  sr_roots_part0_w.dat
    f2973eca887908370b4466dc1a31d36db528d1411f519de6b0b56e83286749c2  sr_roots_part0_x.dat
    7fc2c7beb9653b80a9cba1a36d8554169d0ef905b760642b6d95da8cdc9d5a3c  sr_roots_part1_w.dat
    cbb4f470517e83158d5f9d0630a4985291ca699285bb31141b8da26fbb6b50b7  sr_roots_part1_x.dat
    5a82861adaf8af7c3667941f7996330fd0a7c481e6a28bbe66eabf991ae74292  sr_roots_part2_w.dat
    34103c32dc5fea6675f879152d02970b7b334f48ce669f1b556abcc879f43715  sr_roots_part2_x.dat
    938468a97e4b9cbb8c45d4da030910e1e7aad0029602c83b6bc9ae26f0b8019b  sr_roots_part3_w.dat
    a10f8cef65ea24bbc13a77b98bcd83faaf08a39f0bfc524cfe296a06a05e4cbf  sr_roots_part3_x.dat

The sources that include them (`src/stg_roots.c`, `src/sr_rys_polyfits.c`,
`src/polyfits.c`, `src/g2e_f12.c`) are retained unmodified, so enabling
`WITH_F12` or `WITH_POLYNOMIAL_FIT` needs only the data files back.
