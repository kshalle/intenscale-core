# Licence audit: the core (everything outside `benchmarks/`)

Prepared after the core was published. Every claim below was checked by reading
the files in this tree — license files, in-file headers, `build.sbt` fields —
not by trusting upstream project pages or general knowledge of what a project
"usually" is licensed under. Where something could not be verified from the
tree itself, that is stated explicitly rather than assumed.

**This audit is less complete than [`benchmarks/LICENSE-AUDIT.md`](benchmarks/LICENSE-AUDIT.md).**
That one was built before publication, with fresh upstream clones diffed
byte-for-byte against the vendored tree and an exact commit or tarball hash
recorded for all six components. This one was built *after* publication, as a
first pass over a much larger tree (~1,900 files, ~35MB, at least twelve
distinct upstream origins), most of which carry **no recorded version, tag or
commit hash** in this checkout. No byte-for-byte upstream diff was performed
for anything here. Treat this as a provenance inventory and a punch list, not
a closed audit.

Companion documents: [`CORE-PROVENANCE.md`](CORE-PROVENANCE.md) (the matrix in
one table), and each component's own `ORIGIN.md` where one exists.

## 1. Vendored upstream components

| Component | Path | Identity (evidence) | Licence (evidence) | Version/commit pinned? |
|---|---|---|---|---|
| Rocket Chip | `src/`, `macros/` | `build.sbt`: `organization := "edu.berkeley.cs"`; `pomExtra` names `https://github.com/chipsalliance/rocket-chip` | **Apache-2.0** (`LICENSE`, `LICENSE.SiFive`, both verbatim Apache License 2.0, Copyright 2016-2017 SiFive, Inc.) **and BSD-3-Clause** (`LICENSE.Berkeley`, Copyright 2012-2014 Regents of the University of California) — a genuine dual-tree, not a single license; which one governs a given file is stated by that file's own `// See LICENSE.X` pointer comment | No. `build.sbt` `version := "1.2-SNAPSHOT"`; no tag/commit found anywhere in the tree |
| chisel3 | `chisel3/` | `chisel3/build.sbt`: `organization := "edu.berkeley.cs"`, `name := "chisel3"`, `version := "3.3-SNAPSHOT"` | **BSD-3-Clause** (`chisel3/src/LICENSE.txt`, "Copyright (c) 2014 - 2019 The Regents of the University of California") — the pre-CHIPS-Alliance Berkeley license, not the later Apache-2.0 relicense | No |
| firrtl | `firrtl/` | `firrtl/build.sbt`: `organization := "edu.berkeley.cs"`, `name := "firrtl"`, `version := "1.3-SNAPSHOT"` | **BSD-3-Clause** (`firrtl/src/LICENSE.txt`, identical Berkeley text, 2014-2019) | No |
| hardfloat | `hardfloat/` | `hardfloat/build.sbt`: `name := "hardfloat"`, `version := "1.3-SNAPSHOT"`; per-file headers name John R. Hauser as author | **BSD-3-Clause** (`hardfloat/LICENSE`, "Copyright (c) 2010-2015, The Regents of the University of California") | No |
| api-config-chipsalliance | `api-config-chipsalliance/` | `design/craft/src/config/Config.scala` header: package `chipsalliance.rocketchip`, "Copyright 2016-2019 SiFive, Inc." | **Apache-2.0** (`api-config-chipsalliance/LICENSE`, full standard text, full per-file headers present) | No |
| DRAMSim3 | `DRAMSIM3/` | No README; identified only by `DRAMSIM3/LICENSE`'s copyright line | **MIT** (`DRAMSIM3/LICENSE`, "Copyright (c) 2019, University of Maryland Memory-Systems Research") | No |
| riscv-tests | `riscv-tests/` | No README | **BSD-3-Clause** (`riscv-tests/LICENSE`, `riscv-tests/env/LICENSE`, "Copyright (c) 2012-2015, The Regents of the University of California") | No |
| riscv-torture | `torture/` | `torture/README`: "RISC-V Torture Test Generator / Author: Yunsup Lee and Henry Cook / Date: January 29th, 2012" | **BSD-3-Clause** (`torture/env/LICENSE`, same Regents text; no separate top-level LICENSE for the generator itself, relies on `README` attribution) | No |
| chisel-jtag | `src/main/scala/jtag/` | All 6 files: `// See LICENSE.jtag for license details.` | **BSD-3-Clause-style**, tracked here as `LicenseRef-chisel-jtag` (`LICENSE.jtag`, `LICENSES/LicenseRef-chisel-jtag.txt`; Copyright 2016 The Regents of the University of California) | No |
| rocket-chip-inclusive-cache | `src/main/scala/cache/` | **Already documented** in a pre-existing `src/main/scala/cache/cache_update.md`: swapped in from `https://github.com/chipsalliance/rocket-chip-inclusive-cache` | Governed by the same SiFive/Apache-2.0 headers as the rest of `src/` (Copyright 2019, SiFive, Inc., full long-form Apache-2.0 header, not the one-line pointer) | **Yes** — commit `f2e2c92bd1efb55c4007c98cae201a0864e2fd4a` (2023-01-04), recorded with the specific adaptations made (package renames, one SRAM helper signature change, one string-interpolator swap) |
| SuperLU_MT_3.1 | `DRAMSIM3/ext/SuperLU_MT_3.1/` | Vendored via `DRAMSIM3/.gitmodules` (`https://github.com/umd-memsys/SuperLU_MT_3.1.git`), present as 455 real files, not a stub | **BSD-3-Clause** (`ext/SuperLU_MT_3.1/License.txt`, "Copyright (c) 2003, The Regents of the University of California, through Lawrence Berkeley National Laboratory," includes the endorsement clause) | No — `.gitmodules` names the repo but no commit is recorded in this checkout |
| fmt | `DRAMSIM3/ext/fmt/` | Header library | **BSD-2-Clause-style** (`LICENSE.rst`, "Copyright (c) 2012 - 2016/present, Victor Zverovich," no endorsement clause) | No |
| nlohmann/json | `DRAMSIM3/ext/headers/json.hpp` | Single header | **MIT** (in-file `SPDX-License-Identifier: MIT`, Copyright Niels Lohmann) | No (but the header itself is easy to re-fetch by its own declared version if needed) |
| Catch2 | `DRAMSIM3/ext/headers/catch.hpp` | Single header, "v2.7.0" in comments | **Boost Software License 1.0** (Two Blue Cubes Ltd) — the only BSL-1.0 code in this repository | No |
| inih / INIReader | `DRAMSIM3/ext/headers/{INIReader.h,INIHLICENSE.txt}` | Single header + license file | **BSD-3-Clause ("New BSD")**, Ben Hoyt | No |
| args.hxx | `DRAMSIM3/ext/headers/args.hxx` | Single header | **MIT-style**, Taylor C. Richberger | No |

## 2. Third-party code found *inside* the Rocket Chip tree that is not Rocket Chip

This is the most consequential finding of this audit. `src/` is not uniformly
SiFive/Berkeley-licensed Rocket Chip; it contains a distinct, separately
licensed component adapted from the XiangShan/DiffTest project, plus a
contribution credited to a third company.

**11 files carry a Mulan PSL v2 header** (quoted in full in one representative
file):

```
Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
Copyright (c) 2020-2021 Peng Cheng Laboratory

XiangShan is licensed under Mulan PSL v2.
```

or the same block naming "DiffTest" instead of "XiangShan." The files:

- `src/main/resources/csrc/{compress.cc,compress.h,ram.cc,ram.h}` — Copyright
  2020-2023 Institute of Computing Technology (ICT), CAS + 2020-2021 Peng
  Cheng Laboratory (PCL); "DiffTest is licensed under Mulan PSL v2"
- `src/main/resources/csrc/{elfloader.cc,elfloader.h}` — Copyright **2024
  Axelera AI only** (no ICT/PCL line) — Axelera's own contribution to the
  DiffTest lineage, same Mulan PSL v2 declaration
- `src/main/scala/common/Mem.scala` — ICT only; "DiffTest is licensed under
  Mulan PSL v2"
- `src/main/scala/device/AXI4Memory.scala` — ICT only (2020-2022); "XiangShan
  is licensed under Mulan PSL v2"
- `src/main/scala/device/{AXI4RAM.scala,AXI4SlaveModule.scala}` — ICT + PCL;
  "XiangShan is licensed under Mulan PSL v2"
- `src/main/scala/util/{BitUtils.scala,Hold.scala}` — ICT + PCL; "XiangShan is
  licensed under Mulan PSL v2"

`LICENSE.MulanPSL2` and `LICENSES/MulanPSL-2.0.txt` already exist at the repo
root and cover this correctly.

**Open dependency gap, not a licensing defect but worth recording here:**
several of these files, plus three Intensivate-original DPI-C glue files
(`src/main/resources/vsrc/{MemRWHelper,MemoryRequestHelper,MemoryResponseHelper}.v`),
import or call into a `difftest` package (`import difftest.common.DifftestMem`,
`difftest_ram_read`/`difftest_ram_write`). **No source for that `difftest`
package exists anywhere in this repository** — no `.scala` files, no mention
in `build.sbt`/`ivydependencies.json`/`Makefile`/`Makefrag`, no `/lib/`
directory on disk. The 11 files above are self-contained under their own
Mulan PSL v2 terms regardless, but the build as shipped cannot actually
exercise DiffTest without that external dependency being supplied separately.
Confirm before publication was final whether that dependency was meant to
ship and got missed, or is intentionally out of scope.

**Orphan license entities named by the repo's own tooling, with no matching
file found:** `scripts/{copyright-file,modify-copyright,authors}` are Rocket
Chip's own header-generation tools; they map `git blame`-majority authorship
to entities including Cambridge (Wei Song; post-2016 Matthew Naylor),
Microsoft (Ken McMillan), and LGE (SeungRyeol Lee). **No `LICENSE.Cambridge`,
`LICENSE.Microsoft`, or `LICENSE.LGE` exists in this repo**, and a direct grep
of every `// See LICENSE.X` pointer actually present in `src/` found only
`Berkeley`, `SiFive`, `jtag`, and one generic `// See LICENSE`. So there is no
currently-manifesting gap — but the tooling's existence is evidence that
Cambridge/Microsoft/LGE-authored code exists somewhere in upstream Rocket
Chip's history, and if any file from that history is ever added to this tree,
its license file will need to be added too. Recorded here so it isn't
rediscovered from scratch.

## 3. Files Intensivate wrote (carry `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`)

21 files, all `SPDX-FileCopyrightText: Intensivate, Inc.` / `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`:

| Group | Files |
|---|---|
| Root build scripts | `set_env.sh`, `mkJunit.py` |
| Simulation scripts | `vsim/{testNc.do,testNcWave.do,dumpSaif.do}` |
| RTL — subsystem | `src/main/scala/subsystem/{TLCacheCork,L2Macro,L1L2CacheWiring,L2BaseTile,PcieResetSync,ClockRouting,ConfigsPrint}.scala` |
| RTL — rocket | `src/main/scala/rocket/{regfset,csrUnit,Buses,ctxtUnit}.scala` |
| RTL — compat | `src/main/scala/chisel3_compat/InlineCompat.scala` |
| RTL — resources | `src/main/resources/csrc/config.h`, `src/main/resources/vsrc/{MemoryRequestHelper,MemRWHelper,MemoryResponseHelper}.v` |

This is the complete list as of this audit; `NOTICE.md` and `README.md` are
kept in sync with this count.

### Undisclosed-modification flag

Four files in `src/main/scala/rocket/` — `Consts.scala`, `instrUnit.scala`,
`NBDcache.scala`, `pipeUnit.scala` — sit alongside the four Intensivate-headered
files above, share their unusual (non-Rocket-Chip-convention) names or recent
modification timestamps, but carry **only** the stock
`// See LICENSE.Berkeley` / `// See LICENSE.SiFive` pointer, with no
Intensivate notice and no comment stating a modification. Neither BSD nor
Apache-2.0 strictly requires an in-file "modified" notice for this kind of
change, so this is not necessarily a license violation — but if Intensivate
did modify these files, that is currently undisclosed at the file level, and
if they are wholly Intensivate-original despite the header, the header is
simply wrong. This needs a decision from whoever owns these files, not a
guess from this audit.

Separately, `src/main/scala/system/IntenScaleChip.scala` — a file whose name
strongly suggests Intensivate-original top-level chip integration — carries
only the bare `// See LICENSE.SiFive for license details.` pointer, no
Intensivate header. Same flag: either the header is missing or the file is
less Intensivate-original than its name implies.

**Scope note:** `src/main/scala/` has roughly 300+ `.scala` files. This audit
sampled every subdirectory and was exhaustive for the Mulan search and for
`rocket/`, but did not read every file in bulk directories like `tile/`,
`tilelink/`, and `interrupts/` individually — those were spot-checked and
found uniform. A full per-file pass has not been done.

## 4. Obligations that attach to this release

| Obligation | Source | What it requires |
|---|---|---|
| **State changes** | Apache-2.0 §4(b) (Rocket Chip base, api-config-chipsalliance) | If any vendored file is modified, the modification must be stated. The four undisclosed-modification files above are the open item; the documented `cache/` swap already states its changes in `cache_update.md` |
| **Retain notices** | all BSD/Apache/MIT components | Not independently re-verified file-by-file the way `benchmarks/` was (no byte-for-byte upstream diff performed); no altered notice was found in the sampling done |
| **No endorsement** | BSD-3-Clause (Berkeley, chisel3, firrtl, hardfloat, riscv-tests, torture, jtag, SuperLU_MT_3.1) | Do not use "The Regents of the University of California" or any named individual contributor to endorse or promote products derived from this software |
| **Corresponding source for GPL-2 patches** | `patches/{linux.patch,qemu.patch}` | Both patch GPL-2 upstream projects (Linux kernel, QEMU). Both are ordinary unified diffs (11KB/431 lines and 668B/14 lines respectively) — no wholesale copy of GPL source was found in either. Whether shipping a diff against unmodified upstream GPL source (which this repo does not itself vendor) creates any distribution obligation is a legal question this audit does not resolve; flagged for counsel below |
| **Boost Software License 1.0 attribution** | `DRAMSIM3/ext/headers/catch.hpp` | Retain the license notice; BSL-1.0 is permissive but is the only file under this specific license in the repository — don't assume it's covered by the BSD/MIT/Apache language used elsewhere |

## 5. Open items for IP counsel / engineering

1. **GPL-2 patches (`patches/linux.patch`, `patches/qemu.patch`)** — same
   category of question `benchmarks/`' BSMBench citation clause was: not a
   standard redistribution scenario. These are diffs against GPL-2 projects
   that are not themselves vendored in this repo. Needs a legal read on
   whether that's sufficient distance or whether something more is owed.
2. **The four undisclosed-modification `rocket/` files** and
   **`IntenScaleChip.scala`'s missing header** — needs an answer from
   whoever owns this code: either add the Intensivate header (if modified/
   original) or leave as-is with a note confirming it's genuinely unmodified
   upstream.
3. **Missing `difftest` package source** — confirm whether the DiffTest
   integration was meant to ship complete. As shipped, 11 Mulan-licensed files
   and 3 Intensivate DPI-C glue files reference a dependency that doesn't
   exist in this tree.
4. **`torture/overnight/lib/mail.jar`** (Sun/Oracle `javax.mail`, **CDDL
   1.0**) — already flagged in `NOTICE.md`. CDDL is not otherwise represented
   in this repository and has no license text under `LICENSES/`.
5. **No version/commit is pinned** for chisel3, firrtl, hardfloat,
   api-config-chipsalliance, DRAMSim3, riscv-tests, torture, or the Rocket
   Chip base itself (only the `cache/` swap and `riscv-tools.hash` — which
   pins the separate `riscv-tools` *toolchain*, not any source in this repo,
   to commit `e2c6d1577a75f506fe992c3eb20a75174504476e` — are pinned).
   Recommend recording an upstream commit/tag for each the next time any of
   them is touched, the way `benchmarks/` does.
6. **`src/main/scala/README`** linked two Google Docs by ID and an
   Intensivate GitHub wiki page; the links were pulled in commit `060dc77`
   pending a check of their sharing settings, since that file was public for
   one prior commit before the redaction. If those documents are shared
   more broadly than intended, this audit cannot detect that — it needs to be
   checked directly against the sharing settings, and the exposure during the
   window it was public cannot be undone by editing the file now.
7. **Root `Makefile`** contains internal CI conventions (`required_branch=master_sync`,
   Jenkins-style `$(BRANCH_NAME)`/`$(WORKSPACE)`/`$(GIT_BRANCH)` variables, a
   `docker build . -t rocket_$(date -I)` step). Not a licensing issue, but
   worth a scope decision on whether internal build/branch conventions should
   ship in a public release.
