# compiler-gcc — GCC 2.5.8

The C compiler workload.  Only `cc1` is built from this tree: the compiler
proper, with no driver, preprocessor or assembler, reading preprocessed C and
emitting SPARC assembly.

## What is vendored here

`gcc-2.5.8/` — GCC 2.5.8, the last release of the 2.5 series.

- URL: https://gcc.gnu.org/pub/gcc/old-releases/gcc-2/gcc-2.5.8.tar.bz2
  (mirror: https://ftp.gnu.org/old-gnu/gcc/gcc-2.5.8.tar.gz)
- Tarball sha256: `f91a480ff34bf470a5b4d8045a5733a915f20fb29d72bf5e37f4e2ccdf03a7b9`
- Upstream date: 1994-01-24
- Retrieved: 2026-09-07

## Licence

**GPL-2.0-only.**  Full text in `gcc-2.5.8/COPYING`; the runtime library
carries `gcc-2.5.8/COPYING.LIB` (LGPL).  Redistribution is unrestricted
provided the licence text and notices stay intact and recipients can obtain
the corresponding source — which vendoring the complete tree accomplishes.

The sources credit contributions from Stephen L. Moshier (floating-point
emulation in `real.c`), Bob Corbett and Richard Stallman (parser skeleton) and
Steven Pemberton/CWI (`enquire.c`).  All carry FSF copyright and GPL-2
headers; none imposes separate terms.

GPL applies to this compiler as its own independent program.  It does not
reach the other benchmarks in this suite or the core RTL; they are separate
works merely aggregated in the same package.

## Why this version

A real optimising compiler is the canonical irregular integer workload —
pointer chasing over graph structures, unpredictable branches, a large
instruction footprint, heavy allocation.  2.5.8 is small enough to run in RTL
simulation, self-contained enough to cross-build without a modern
autotools/GMP stack, and complete enough to run every classical optimisation
pass.

## Local modifications

None.  Tree is upstream as extracted.

## Notes for benchmark use

- Building 1994-era GCC with a modern host compiler needs K&R-era fixes;
  https://github.com/decompals/old-gcc carries working patches for 2.5.7 and
  later.  The bare-metal port in `../baremetal/gcc-cc1` handles this itself and
  documents what it had to do.
- The port generates its own preprocessed C input; see
  `../baremetal/gcc-cc1/workload/mkinput.py`.
