# Licence audit: benchmarks/

Prepared for public release.  Every row below was checked by reading the files
in this tree, not by trusting the upstream project pages.  Licence texts are in
[`LICENSES/`](LICENSES/), copied verbatim from the upstreams.

Companion documents: [`PROVENANCE.md`](PROVENANCE.md) (why each replacement was
chosen), [`NOTICE.md`](NOTICE.md) (attributions and the obligations that follow
us into publications), and each `application directories ORIGIN.md`.

## 1. Vendored upstream code

Each tree is byte-identical to the upstream release except for the removal of
`.git/`, `.github/` and CVS metadata, which is recorded in its `ORIGIN.md`.

| Tree | SPDX | Licence file in tree | Verified |
|---|---|---|---|
| `compiler-gcc/gcc-2.5.8` | `GPL-2.0-only` | `COPYING`; also `COPYING.LIB` (the Library GPL, shipped by upstream but covering no source in this tree) | yes |
| `interpreter-perl/perl-4.036` | `GPL-1.0-or-later OR Artistic-1.0` | `Copying`, `Artistic` | yes |
| `interpreter-xlisp/xlisp-plus` | `MIT` | `LICENSE.txt` | yes |
| `lattice-bsmbench/BSMBench` | `LicenseRef-BSMBench-BSD-3-Clause-plus-citation` | `LICENSE` | yes |
| `qchem-libcint/libcint` | `Apache-2.0` | `LICENSE`, `AUTHORS` | yes |
| `weather-miniweather/miniWeather` | `BSD-2-Clause` | `LICENSE` | yes |

### Verified against upstream, 2026-09-20

The "byte-identical" claim above was re-checked by refetching each upstream and
diffing it against the vendored tree, rather than by trusting the checksums
recorded in the `ORIGIN.md` files.  All six pass.

| Tree | Check | Result |
|---|---|---|
| `gcc-2.5.8` | SHA-256 of `gcc-2.5.8.tar.bz2` from gcc.gnu.org, then full diff | hash matches; **0 differences** |
| `perl-4.036` | SHA-256 of `perl-4.036.tar.gz` from cpan.org, then full diff | hash matches; **0 differences** |
| `BSMBench` | SHA-256 of `BSMBench-master.tar.gz` from gitlab.com, then full diff | hash matches; **0 differences** |
| `xlisp-plus` | clone at `81bc021f`, then full diff | only `doc/XLISPPLUS3.{pdf,docx}` absent — the documented removal |
| `libcint` | clone at `3d36c4f4`, then full diff | only `.github/` and the nine Rys-root tables absent (123 MB) — the documented removal; `src/roots_for_x0.dat` (75,385 bytes) correctly kept |
| `miniWeather` | clone at `b001069e`, then full diff | only `documentation/intro_to_openacc.{pdf,pptx}` absent — the documented removal |

All three recorded commits still exist upstream.  Not one file differs in
content: every difference found is a deletion already recorded in the relevant
`ORIGIN.md`, and nothing was added or edited.

Two artefacts that look like differences but are not: `perl-4.036/t/perl` is a
symlink to `../perl` that is broken in the upstream tarball itself and is
equally broken here, so a dereferencing `diff` reports an error on both sides;
and `perl-4.036/t/c` is a symlink to `TEST`.  Compare that tree with
`diff --no-dereference`.

Notes on two of them:

- **`LICENSES/MIT.txt`** is XLISP-PLUS's own `LICENSE.txt`, which is the MIT
  text *plus* its copyright chain (Betz; Tierney for the XLISP-STAT parts;
  Hewlett-Packard for `UNIXSTUF.C` from Winterp; then named contributors).
  Keeping the upstream file rather than a bare MIT text preserves that chain.
  The relicensing of the HP-derived file is upstream's assertion, made with
  permission from Almy and Betz per the repository's README; we rely on it and
  do not compile that file (the port builds `linuxstuff.c` and `unixprim.c`).
- **`dfftpack` has been removed.**  It shipped no licence file -- its terms
  rested on one sentence in its `README` and a `permission` file containing
  only the note "prehistoric email from author" -- and nothing in the suite
  compiled it.  Rather than carry that into a public release for unused code,
  it was dropped; see `weather-miniweather/ORIGIN.md` for what it was for and
  where to get FFTPACK under clear terms if it is ever needed.

## 2. Third-party notices *inside* the upstream trees

Checked by scanning every source file for copyright lines.  All of these are
distributed by their upstream under that upstream's licence; none imposes
additional terms, and all notices are preserved because the trees are
unmodified.  Listed so a reviewer does not have to rediscover them.

| Tree | Additional holders found | Assessment |
|---|---|---|
| `gcc-2.5.8` | Stephen L. Moshier (`real.c`, contributed); Bob Corbett & Stallman (bison skeleton); Steven Pemberton/CWI (`enquire.c`) | All carry "Copyright (C) FSF" + GPL-2 headers; contributors credited, no separate terms |
| `perl-4.036` | Diomidis Spinellis, Tom Dinger, Jack Hudler, Regents of the University of California (1980) | In the MS-DOS/other-platform subtrees and in comments; perl's own terms apply.  `doio.c`'s "Tom Dinger" line is an attribution comment, not a notice |
| `xlisp-plus` | Luke Tierney (XLISP-STAT), Hewlett-Packard (Winterp) | Named in `LICENSE.txt` and MIT-granted there |
| `BSMBench` | Claudio Pica, Agostino Patella (2008-2014, HiRep origins) | Per-file BSD-3 headers consistent with the top-level licence; **no GPL anywhere in the tree** (checked explicitly, since HiRep-derived code sometimes is) |
| `libcint` | Qiming Sun only | Apache-2.0 throughout |
| `miniWeather` | ORNL/NCCS (2018), NVIDIA (2021) | Both named in the BSD-2 `LICENSE` |

## 3. Files we wrote

These are the harness, the port glue, the workloads and the documentation.
Their licence is a decision for Intensivate; see "Open items" below.  The
inventory is exact -- generated and build-time files are excluded because they
are `.gitignore`d and do not ship.

| Group | Files | Nature |
|---|---|---|
| Harness | `baremetal/common/{htif.h,htif_syscalls.c,bm_posix_stubs.c,bm_stats.{c,h},bm_main.c,bm_cxx.c,bm.mk}` | Original work: HTIF syscall glue, heap/stack setup, counters, POSIX stubs |
| Harness scripts | `baremetal/scripts/run-verilator.sh`, `baremetal/Makefile`, per-port `Makefile`s | Original work |
| Self-test | `baremetal/selftest/{selftest.c,Makefile,input.txt}` | Original work |
| Shims | `miniweather/shim/{mpi.h,pnetcdf.h}`, `xlisp/shim/sys/termios.h`, `bsmbench/shim/suN*.h` | Original declarations for standard or upstream-generated interfaces |
| Port stubs | `xlisp/bm_os_stubs.c`, `perl-perl4/bm_os_stubs.c`, `libcint/bm_longdouble.c` | Original work |
| Drivers | `libcint/libcint-driver.c` | Original work, written against libcint's public API |
| Generators | `perl-perl4/mkconfig.py`, `libcint/mkheaders.sh`, `gcc-cc1/{mkgen.sh,mkstage.py}`, `gcc-cc1/workload/mkinput.py`, `perl-perl4/workload/mkwords.awk` | Original work |
| Workloads | `xlisp/workload/*.lsp`, `perl-perl4/workload/{primes.pl,anagram.pl}`, `bsmbench/workload/*.input`, `perl-perl4/workload/words-*.txt` | Original work.  **No third-party benchmark suite's inputs, harness, reference outputs or run rules appear anywhere in this tree** |
| Documentation | `README.md`, `PROVENANCE.md`, `LICENSE-AUDIT.md`, `NOTICE.md`, `baremetal/*.md`, `application directories ORIGIN.md`, per-port `PORT-NOTES.md` | Original work |

### Three of our files are not simply "ours"

1. **`baremetal/perl-perl4/config.h` is a derivative of perl's own
   `config.h`** -- `mkconfig.py` copies it and switches 71 defines off.  It
   therefore carries perl's licence (`GPL-1.0-or-later OR Artistic-1.0`), not
   ours.  Upstream's generated `config.h` has no notice of its own, so the
   derived file needs one added; see "Open items".
2. **`baremetal/gcc-cc1/hostcfg/xm-bm.h`** is an original file, but it was
   written by modelling `gcc-2.5.8/config/alpha/xm-alpha.h` and exists only to
   build GCC.  The conservative label is `GPL-2.0-only`; see "Open items".
3. **Build-time derivatives that never ship** (all `.gitignore`d, listed for
   completeness): `gcc-cc1/gen/src/*` are patched copies of GCC sources
   (`GPL-2.0-only`); `gcc-cc1/gen/insn-*` and `bc-*` are GCC generator output
   (`GPL-2.0-only`); `perl-perl4/perly.{c,h}` are bison output from perl's
   grammar (perl's terms); `libcint/gen/cint*.h` are libcint template
   output (`Apache-2.0`); `xlisp/init.lsp` is copied from XLISP-PLUS
   (`MIT`).  Anyone redistributing a *built* tree redistributes these.

## 4. Obligations that attach to a public release

| Obligation | Source | What it requires |
|---|---|---|
| **Citation** | BSMBench | Any publication using results derived from it must name the BSMBench package, give its URL, and cite two Phys. Rev. D papers.  Applies to datasheets and blog posts, not just papers.  Full text in `NOTICE.md` |
| **Corresponding source** | GPL-2 (gcc), GPL-1+ (perl) | Satisfied by vendoring the complete upstream trees.  Keep them in the release; do not ship built binaries without the source |
| **State changes** | Apache-2.0 §4(b) (libcint) | Satisfied.  Two modifications are stated in `qchem-libcint/ORIGIN.md`: removed `.git/`, and removed nine Rys-root data tables that nothing here compiles (122.6 MiB), with checksums for restoring them.  The port's own changes are additive and recorded in its `PORT-NOTES.md` |
| **Retain notices** | all | Satisfied; no upstream licence, notice or copyright line has been altered anywhere |
| **No endorsement** | BSD-3 (BSMBench) | Do not use the contributors' names to promote the release.  Full text in `NOTICE.md` |

### On GPL scope

gcc and perl are independent programs that happen to be aggregated in this
repository, and their copyleft does not reach the other benchmarks or the core
RTL.  What *is* affected is a **built binary**: `cc1` and `perl` are linked
together with our harness, so the licence chosen for the harness must be
GPL-compatible if anyone is to redistribute those binaries.  This is the main
reason the harness licence needs deciding rather than defaulting.

## 5. Decisions taken

1. **Files we wrote are `BSD-2-Clause`.**  Chosen over the core's
   `LicenseRef-Intensivate-NC-1.0` and over Apache-2.0 because neither is
   GPL-compatible: this suite links our harness together with GPL programs
   (GCC 2.5.8, Perl 4.036), and a GPL-incompatible licence would make the
   resulting binaries non-redistributable.  A non-commercial term would also
   work against the third-party benchmarking a public suite invites.
2. **SPDX headers applied** to the 49 files under `baremetal/` that can
   carry a comment, in the form `SPDX-FileCopyrightText` above
   `SPDX-License-Identifier`, with this suite's licence in place of the
   core's.  Every file we wrote that can carry a comment does; the one that
   cannot (`baremetal/selftest/input.txt`, a fixture whose exact bytes are
   what the test checks) is declared in `REUSE.toml`.
3. **`perl-perl4/config.h` carries perl's terms**
   (`GPL-1.0-or-later OR Artistic-1.0`), and `mkconfig.py` now emits that
   notice, so regenerating the file cannot silently drop it.
4. **`gcc-cc1/hostcfg/*.h` are labelled `GPL-2.0-only`** -- the conservative
   reading, since they were modelled on GCC's own host headers and are useful
   only for building GCC.
5. **Files that cannot carry a header** (documentation, workload data) and the
   vendored trees are declared in `REUSE.toml`, so a REUSE run passes without
   editing a single upstream byte.
6. **Build-generated files are declared too, and each inherits the licence of
   what generated it.**  `baremetal/gcc-cc1/gen/**` is derived from GCC 2.5.8
   and is therefore `GPL-2.0-only`; `baremetal/libcint/gen/**` is Apache-2.0;
   `perly.c`/`perly.h` carry perl's terms.  None of them is tracked -- they
   are rebuilt by the generators in the tree -- but a working tree that has
   been built still passes a REUSE run.
7. **Machine-local build configuration is not shipped.**
   `baremetal/common/bm-site.mk` names directories on one developer's machine;
   it is `.gitignore`d, and `bm-site.mk.example` is the tracked template.

## 6. Still open

- **IP counsel review**, per Intensivate's internal release checklist.
  The item that most deserves their attention is the **BSMBench citation
  clause**: not a standard OSI term, and it creates an obligation on published
  figures rather than on redistribution.  (The other item raised here
  originally, dfftpack's public-domain basis, was resolved by removing it.)
- ~~**Decide whether to trim `qchem-libcint`.**~~  **Done**, and two smaller
  trims were made with it.  No upstream file was edited; files were only
  removed, and each removal is recorded with SHA-256 in the relevant
  `ORIGIN.md` so it can be restored from upstream.

  | Tree | Removed | Size | Licence position |
  |---|---|---|---|
  | `qchem-libcint` | nine Rys-root data tables | 122.6 MiB | Stated modification under Apache-2.0 §4(b) |
  | `weather-miniweather` | `intro_to_openacc.{pptx,pdf}` | 5.9 MiB | BSD-2-Clause requires no statement; recorded anyway |
  | `interpreter-xlisp` | `XLISPPLUS3.{pdf,docx}` | 1.3 MiB | MIT requires no statement; recorded anyway |

  Nothing removed is compiled, read or linked by this suite.  The libcint
  tenth table, `src/roots_for_x0.dat` (76 KB), *is* required and was kept; the
  port was rebuilt and rerun after the removal with its checksums unchanged to
  every printed digit.  Together these take a fresh clone from about 40 MB to
  about 10 MB.
