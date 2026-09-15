# Provenance and licence matrix

Where each application in this suite came from, what licence it carries, and
what obligations travel with it.  Compiled 2026-09-08; every row was checked
against the files in this tree, not against upstream project pages.

Per-application detail is in each directory's `ORIGIN.md`.  The file-by-file
licence position, including third-party notices found *inside* the upstream
trees, is in [`LICENSE-AUDIT.md`](LICENSE-AUDIT.md).

## Matrix

| Workload class | Application | Upstream version | Licence | Obligations |
|---|---|---|---|---|
| C compiler | GCC 2.5.8 (`cc1` only) | 2.5.8, 1994-01-24 | GPL-2.0-only | Keep `COPYING` and notices; provide corresponding source |
| Lisp interpreter | XLISP-PLUS | commit `81bc021`, 2026-08-21 | MIT | Keep `LICENSE.txt` (Betz / Tierney / HP / contributors chain) |
| Perl interpreter | Perl 4.036 | 4.036, 1994-02-01 | GPL-1.0-or-later **OR** Artistic-1.0 | Keep `Copying` and `Artistic`; the Artistic option avoids GPL discussion |
| Lattice gauge theory | BSMBench 1.0 | master archive | BSD-3-Clause **+ citation clause** | Retain notice **and** cite two papers + the package URL in any publication |
| Quantum chemistry | libcint | commit `3d36c4f`, 2026-06-20 | Apache-2.0 | Keep LICENSE/NOTICE; state modifications; carries a patent grant |
| Atmospheric dynamics | miniWeather | commit `b001069`, 2025-08-11 | BSD-2-Clause | Retain the notice |

## Why these applications

Each was chosen to represent a workload class on a core-design benchmark
suite, under two constraints: a licence that permits public redistribution,
and an implementation whose machine behaviour is representative rather than
synthetic.

**GCC 2.5.8** is a real optimising compiler small enough to run in RTL
simulation and old enough to be self-contained.  Only `cc1` is built — the
compiler proper, no driver, preprocessor or assembler — reading preprocessed
C and emitting SPARC assembly.  Compilers are the canonical irregular
integer workload: pointer-chasing over graph structures, unpredictable
branches, a large instruction footprint and heavy allocation.

**XLISP-PLUS** is the maintained descendant of David Betz's XLISP, a compact
tree-walking Lisp interpreter with a mark-and-sweep collector.  It exercises
deep non-tail recursion, cons-cell allocation against a garbage collector and
symbol lookup — a pointer-heavy profile with deliberately poor locality.
Chosen over Betz's own later byte-code XLISP because the tree-walking
interpreter is the more interesting workload, and over larger Lisps because it
fits a simulator.

**Perl 4.036** is the last Perl 4 release: a tree-walking interpreter whose
hot loops are string and hash manipulation, regular-expression compilation and
matching, and allocator traffic.  Perl 5 was considered and rejected — it is
substantially larger for no additional workload character at this scale.

**BSMBench** is a lattice gauge theory benchmark extracted from the HiRep
lattice code by the Swansea/Edinburgh/Southern Denmark lattice group, and
purpose-built as a portable supercomputer benchmark.  SU(2) Monte Carlo with
Dirac operator applications over a 4-D lattice: regular, vectorisable
double-precision work over a large working set.  Legacy codes in this class
are research software without public licences; BSMBench exists precisely to be
published and cited.

**libcint** is the Gaussian-integral library behind PySCF.  It computes
two-electron integrals *and their derivatives* (`int2e_ip1`), and its
auto-generated integral routines are the defining shape of that computation:
very long straight-line double-precision code with extreme register pressure
and almost no control flow.  Considered and rejected: libint2 (needs a
source-generation step), Psi4 and NWChem (far too heavy for a simulator).
The algorithm is the one described in H. B. Schlegel, *J. Chem. Phys.* **77**,
3676 (1982).

**miniWeather** is a compact atmospheric-dynamics mini-app from ORNL: dry
compressible stratified non-hydrostatic flow, with C, C++ and Fortran variants
in one repository — convenient for compiler and core studies.  Chosen over
real models (WRF, MPAS) purely on size; they are orders of magnitude too large
for RTL simulation.

## Obligation summary for the release package

1. **Every directory keeps its upstream licence file in place.**  Do not
   consolidate them.
2. **GPL scope.**  GCC and Perl are independent programs aggregated in this
   suite; their copyleft does not extend to the other benchmarks or to the
   core RTL.  Work by Intensivate is BSD-2-Clause specifically so that *built*
   binaries combining our harness with those programs remain redistributable.
3. **BSMBench's citation clause is an active obligation on published
   figures** — datasheets and blog posts included, not just papers.  Full text
   and the two citations are in [`NOTICE.md`](NOTICE.md).
4. **Apache-2.0 (libcint)** requires stating changes if the tree is modified.
   Two changes are stated: removed `.git/`, and removed nine Rys-root data
   tables that nothing in this suite compiles (122.6 MiB).  Both are recorded
   in [`qchem-libcint/ORIGIN.md`](qchem-libcint/ORIGIN.md), which also gives
   their checksums so they can be restored from upstream.
5. **All workloads are original to this suite.**  No third-party benchmark
   suite's sources, inputs, reference outputs or run rules appear anywhere in
   this tree.

## Checksums for re-verification

```
f91a480ff34bf470a5b4d8045a5733a915f20fb29d72bf5e37f4e2ccdf03a7b9  gcc-2.5.8.tar.bz2
f690888369d6297ce8f6c5d75c573d063c14e4ab03319ebc1a7e959787cfc76a  perl-4.036.tar.gz
abbff613a9b6ad973b9dc60e2cdc5663eb0e08a319f990d1b3ca7d1fa16619a5  BSMBench-master.tar.gz
```

Git-sourced trees are pinned by the commit hashes in the matrix above.
