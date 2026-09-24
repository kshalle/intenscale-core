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
| rocket-chip-inclusive-cache | `src/main/scala/cache/` | — | Same as Rocket Chip (SiFive/Apache-2.0 headers) | **Yes** — `f2e2c92bd1efb55c4007c98cae201a0864e2fd4a` (2023-01-04) | Documented in `cache_update.md`: package renames, one SRAM helper signature change, one string-interpolator swap |
| chisel-jtag | `src/main/scala/jtag/` | — | `LicenseRef-chisel-jtag` (BSD-3-Clause-style, Regents 2016) | No | Retain notice |
| XiangShan / DiffTest | 11 files across `src/main/scala/{device,util,common}` and `src/main/resources/csrc/` | — | Mulan PSL v2 | No | Retain notice; see licence text at `LICENSES/MulanPSL-2.0.txt` |
| chisel3 | `chisel3/` | `3.3-SNAPSHOT` | BSD-3-Clause | No | Retain notice |
| firrtl | `firrtl/` | `1.3-SNAPSHOT` | BSD-3-Clause | No | Retain notice |
| hardfloat | `hardfloat/` | `1.3-SNAPSHOT` | BSD-3-Clause | No | Retain notice |
| api-config-chipsalliance | `api-config-chipsalliance/` | — | Apache-2.0 | No | State changes; retain notices |
| DRAMSim3 | `DRAMSIM3/` | — | MIT | No | Retain notice |
| SuperLU_MT_3.1 | `DRAMSIM3/ext/SuperLU_MT_3.1/` | — | BSD-3-Clause | No (submodule reference exists in `DRAMSIM3/.gitmodules` but no commit recorded here) | Retain notice, no endorsement |
| fmt | `DRAMSIM3/ext/fmt/` | — | BSD-2-Clause-style | No | Retain notice |
| nlohmann/json | `DRAMSIM3/ext/headers/json.hpp` | — | MIT | No | Retain notice |
| Catch2 | `DRAMSIM3/ext/headers/catch.hpp` | `v2.7.0` (in-file comment) | Boost Software License 1.0 | No | Retain notice |
| inih / INIReader | `DRAMSIM3/ext/headers/{INIReader.h,INIHLICENSE.txt}` | — | BSD-3-Clause | No | Retain notice, no endorsement |
| args.hxx | `DRAMSIM3/ext/headers/args.hxx` | — | MIT-style | No | Retain notice |
| riscv-tests | `riscv-tests/` | — | BSD-3-Clause | No | Retain notice, no endorsement |
| riscv-torture | `torture/` | dated 2012-01-29 (`README`) | BSD-3-Clause (`env/LICENSE`) | No | Retain notice, no endorsement |
| javax.mail | `torture/overnight/lib/mail.jar` | — | CDDL 1.0 | No | Not yet resolved — see open items in `CORE-LICENSE-AUDIT.md` §5 |

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

## What full pinning would require

None of the components above except `cache/` carries a recorded upstream
commit or tag in this tree. Recovering one for each would mean cloning the
relevant upstream repository and diffing candidate commits against what's
vendored here until a match is found — the same method
`benchmarks/PROVENANCE.md` used, but starting from zero recorded version
strings for most of these instead of one. Not attempted in this pass; see the
"submodules" discussion for a possible way to get this for free going forward
on the seven components that carry no Intensivate-original content.
