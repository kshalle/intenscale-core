# Provenance and licence matrix: the core

Where each component of the core came from, what licence it carries, and what
obligations travel with it. Compiled after publication; every row was checked
against the files in this tree, not against upstream project pages.

Per-component detail is in each directory's `ORIGIN.md` where one exists. The
narrative findings — the XiangShan/DiffTest subset inside `src/`, the
undisclosed-modification flags, the open items for counsel — are in
[`CORE-LICENSE-AUDIT.md`](CORE-LICENSE-AUDIT.md); this file is the matrix only.

## Matrix

| Component | Path | Upstream version | Licence | Commit/tag pinned? | Obligations |
|---|---|---|---|---|---|
| Rocket Chip | `src/`, `macros/` | `1.2-SNAPSHOT` (`build.sbt`) | Apache-2.0 (`LICENSE.SiFive`) **and** BSD-3-Clause (`LICENSE.Berkeley`), per-file | No | State changes to Apache-2.0-covered files; retain notices |
| rocket-chip-inclusive-cache | `src/main/scala/cache/` | — | Same as Rocket Chip (SiFive/Apache-2.0 headers) | **Yes, exact** — `f2e2c92bd1efb55c4007c98cae201a0864e2fd4a` (2023-01-04) | Documented in `cache_update.md`: package renames, one SRAM helper signature change, one string-interpolator swap |
| chisel-jtag | `src/main/scala/jtag/` | — | `LicenseRef-chisel-jtag` (BSD-3-Clause-style, Regents 2016) | No | Retain notice |
| XiangShan / DiffTest | 11 files across `src/main/scala/{device,util,common}` and `src/main/resources/csrc/` | — | Mulan PSL v2 | No | Retain notice; see licence text at `LICENSES/MulanPSL-2.0.txt` |
| chisel3 | `chisel3/` (vendored) | `3.3-SNAPSHOT` | BSD-3-Clause | **Base: yes, exact** — `9f620e06bacc2882068adfd4972ec2e9a87ea723`. One file (`Reg.scala`) modified on top — see `chisel3/INTENSIVATE-REG-INIT.patch` | Retain notice; local modification is an engineering-review flag, not just licensing — see `CORE-LICENSE-AUDIT.md` §2 |
| firrtl | `firrtl/` (**submodule**) | `1.3-SNAPSHOT` | BSD-3-Clause | **Yes, exact** — `7c6f58d986e67b3d0662a4cd6654a68f9cc52cf9`, zero real differences | Retain notice |
| hardfloat | `hardfloat/` (vendored) | `1.3-SNAPSHOT` | BSD-3-Clause | **Base: best-effort, not exact** — closest is tag `v1.3-20200227-SNAPSHOT`. Real additions on top (`FMA.scala` + 12 modified files) — see `hardfloat/INTENSIVATE-FPU-EXTENSIONS.patch` | Retain notice; modification documented, base tree not reverted (live dependency in `tile/FPU.scala`) |
| api-config-chipsalliance | `api-config-chipsalliance/` (**submodule**) | — | Apache-2.0 | **Yes, exact** — `fd8df1105a92065425cd353b6855777e35bd79b4`, verified against all 54 commits in the repo's history | State changes; retain notices |
| DRAMSim3 | `DRAMSIM3/` (vendored) | — | MIT | **Base: yes, exact** — `1d2a9bf` on `OpenXiangShan/DRAMsim3.git` (a XiangShan-specific fork, not vanilla upstream). Real LPDDR5 additions on top — see `DRAMSIM3/INTENSIVATE-LPDDR5.patch` | Retain notice; modification documented, base tree not reverted (live dependency in `emulator/Makefile`) |
| SuperLU_MT_3.1 | `DRAMSIM3/ext/SuperLU_MT_3.1/` | — | BSD-3-Clause | **Yes, exact** — `68739c5a23cc48c9a13d28793834e4fee95d33ee`, pinned via `DRAMSIM3`'s own tree, byte-identical | Retain notice, no endorsement |
| fmt | `DRAMSIM3/ext/fmt/` | — | BSD-2-Clause-style | Comes with the `DRAMSIM3` base commit — confirmed part of that fork's own tree | Retain notice |
| nlohmann/json | `DRAMSIM3/ext/headers/json.hpp` | — | MIT | Comes with the `DRAMSIM3` base commit | Retain notice |
| Catch2 | `DRAMSIM3/ext/headers/catch.hpp` | `v2.7.0` (in-file comment) | Boost Software License 1.0 | Comes with the `DRAMSIM3` base commit | Retain notice |
| inih / INIReader | `DRAMSIM3/ext/headers/{INIReader.h,INIHLICENSE.txt}` | — | BSD-3-Clause | Comes with the `DRAMSIM3` base commit | Retain notice, no endorsement |
| args.hxx | `DRAMSIM3/ext/headers/args.hxx` | — | MIT-style | Comes with the `DRAMSIM3` base commit | Retain notice |
| riscv-tests | `riscv-tests/` (vendored) | — | BSD-3-Clause | **No — not found.** One candidate ruled out (148 real diffs); separately, `isa/rv64in/cpy.S` has no known upstream origin at all | Retain notice, no endorsement; `cpy.S`'s origin/license is an open item |
| riscv-torture | `torture/` (**submodule**) | dated 2012-01-29 (`README`) | BSD-3-Clause (`env/LICENSE`) | **Yes, exact** — `bf4481102f1048888370ff73ce98e97f4f361165`, byte-identical across all 51 files | Retain notice, no endorsement |
| javax.mail | `torture/overnight/lib/mail.jar` | — | CDDL 1.0 | Confirmed genuinely upstream (first appears in an early ancestor of the pinned `torture` commit), not locally added | Not yet resolved — see open items in `CORE-LICENSE-AUDIT.md` §6 |

## Why no "why these were chosen" section

Unlike `benchmarks/`, which selected each workload deliberately from several
candidates, these are the toolchain and simulation dependencies Rocket Chip
itself requires to build and run — chisel3/firrtl compile the RTL, hardfloat
provides the floating-point units, api-config-chipsalliance provides the
config API, DRAMSim3/riscv-tests/torture/mail.jar support simulation and
regression. There was no comparative selection process to record.

## GPL-2 patches (not vendored source, recorded here for completeness)

`patches/{linux.patch,qemu.patch}` are diffs against the Linux kernel and QEMU
(both GPL-2), neither of which is vendored in this repository. Both are
ordinary unified diffs with no wholesale copy of upstream source found in
either. See `CORE-LICENSE-AUDIT.md` §5 item 1 for the open legal question.

## What full pinning required, and what it found

Cloning each relevant upstream repository and diffing candidate commits
against what's vendored here — the same method `benchmarks/PROVENANCE.md`
used, but starting from zero recorded version strings for most of these —
resolved base commits for six of seven attempted (`riscv-tests` remains
unresolved) and converted three to real git submodules
(`api-config-chipsalliance`, `firrtl`, `torture`). It also disproved the
assumption that the other four were clean, unmodified vendor copies: `chisel3`,
`hardfloat`, and `DRAMSIM3` each carry real local modifications on top of
their base commit, now extracted to `INTENSIVATE-*.patch` files in their
respective directories; `riscv-tests` additionally has one file
(`isa/rv64in/cpy.S`) with no known upstream origin at all. See
`CORE-LICENSE-AUDIT.md` §2 for the detail.
