# Licence audit: the core (everything outside `benchmarks/`)

Prepared after the core was published. Every claim below was checked by reading
the files in this tree — license files, in-file headers, `build.sbt` fields —
not by trusting upstream project pages or general knowledge of what a project
"usually" is licensed under. Where something could not be verified from the
tree itself, that is stated explicitly rather than assumed.

**This audit is less complete than [`benchmarks/LICENSE-AUDIT.md`](benchmarks/LICENSE-AUDIT.md)**
in one respect: `benchmarks/` had an exact commit or tarball hash recorded for
all six components *before* this audit began. Here, three of ten vendored
components (`api-config-chipsalliance`, `firrtl`, `torture`) now have a
verified exact upstream commit and were converted to real git submodules; four
more (`chisel3`, `hardfloat`, `DRAMSIM3`, `riscv-tests`) have a confirmed or
best-effort base commit but real local modifications that rule out a clean
submodule for now (see §2); the Rocket Chip base itself (`src/`) has no
recorded version at all beyond `build.sbt`'s `1.2-SNAPSHOT`.

Companion documents: [`CORE-PROVENANCE.md`](CORE-PROVENANCE.md) (the matrix in
one table), and each vendored (non-submodule) component's own `ORIGIN.md`.
The three components converted to real git submodules (`firrtl`,
`api-config-chipsalliance`, `torture`) deliberately don't have one — a real
submodule's working tree should mirror upstream exactly, so their provenance
lives here and in `CORE-PROVENANCE.md` instead of a file inside them.

## 1. Vendored upstream components

| Component | Path | Identity (evidence) | Licence (evidence) | Version/commit pinned? |
|---|---|---|---|---|
| Rocket Chip | `src/`, `macros/` | `build.sbt`: `organization := "edu.berkeley.cs"`; `pomExtra` names `https://github.com/chipsalliance/rocket-chip` | **Apache-2.0** (`LICENSE`, `LICENSE.SiFive`) **and BSD-3-Clause** (`LICENSE.Berkeley`) — per file, via each file's own `// See LICENSE.X` pointer | No. `build.sbt` `version := "1.2-SNAPSHOT"`; no tag/commit found |
| chisel3 | `chisel3/` (vendored, not a submodule) | `build.sbt`: `version := "3.3-SNAPSHOT"` | **BSD-3-Clause** (`src/LICENSE.txt`, 2014-2019 Regents) | **Yes, base commit found** — `9f620e06bacc2882068adfd4972ec2e9a87ea723` on `chipsalliance/chisel.git`. Exactly one file differs from it; see §2 |
| firrtl | `firrtl/` (**submodule**) | `build.sbt`: `version := "1.3-SNAPSHOT"` | **BSD-3-Clause** (`src/LICENSE.txt`) | **Yes, exact** — `7c6f58d986e67b3d0662a4cd6654a68f9cc52cf9` on `chipsalliance/firrtl.git`. Zero real content differences |
| hardfloat | `hardfloat/` (vendored, not a submodule) | `build.sbt`: `version := "1.3-SNAPSHOT"`; per-file headers name John R. Hauser | **BSD-3-Clause** (`LICENSE`, 2010-2015 Regents) | **Best-effort, not exact** — closest candidate is tag `v1.3-20200227-SNAPSHOT` on `ucb-bar/berkeley-hardfloat.git`; real local modifications on top, see §2 |
| api-config-chipsalliance | `api-config-chipsalliance/` (**submodule**) | `Config.scala` header: package `chipsalliance.rocketchip`, "Copyright 2016-2019 SiFive, Inc." | **Apache-2.0** (`LICENSE`) | **Yes, exact** — `fd8df1105a92065425cd353b6855777e35bd79b4`. Verified against all 54 commits in the repo's history |
| DRAMSim3 | `DRAMSIM3/` (vendored, not a submodule) | No README; identified by `LICENSE`'s copyright line and confirmed as a XiangShan fork, not vanilla upstream | **MIT** (`LICENSE`, 2019 UMD) | **Yes, exact base** — `1d2a9bf` on `OpenXiangShan/DRAMsim3.git`, branch `cosim-kmh`. Real local modifications on top, see §2 |
| riscv-tests | `riscv-tests/` (vendored, not a submodule) | No README | **BSD-3-Clause** (`LICENSE`, `env/LICENSE`, 2012-2015 Regents) | **No — not found.** The one candidate checked (via `riscv-tools.hash`'s cross-reference) was confirmed wrong (148 diffs, real content differences); see §2 |
| riscv-torture | `torture/` (**submodule**) | `torture/README`: "RISC-V Torture Test Generator / Author: Yunsup Lee and Henry Cook" | **BSD-3-Clause** (`env/LICENSE`) | **Yes, exact** — `bf4481102f1048888370ff73ce98e97f4f361165` on `ucb-bar/riscv-torture.git`. Byte-identical across all 51 files |
| chisel-jtag | `src/main/scala/jtag/` | All 6 files: `// See LICENSE.jtag for license details.` | **BSD-3-Clause-style**, tracked here as `LicenseRef-chisel-jtag` | No |
| rocket-chip-inclusive-cache | `src/main/scala/cache/` | Documented in a pre-existing `cache_update.md`: swapped in from `chipsalliance/rocket-chip-inclusive-cache` | Same SiFive/Apache-2.0 headers as the rest of `src/` | **Yes** — `f2e2c92bd1efb55c4007c98cae201a0864e2fd4a` (2023-01-04), with adaptations recorded |
| SuperLU_MT_3.1 | `DRAMSIM3/ext/SuperLU_MT_3.1/` | Nested submodule of `DRAMSIM3`'s own upstream | **BSD-3-Clause** (`License.txt`, LBNL, endorsement clause) | **Yes, exact** — `68739c5a23cc48c9a13d28793834e4fee95d33ee`, comes pinned via `DRAMSIM3`'s own tree at `1d2a9bf`. Byte-identical (aside from the already-removed stray `make.log`) |
| fmt | `DRAMSIM3/ext/fmt/` | Confirmed part of the `OpenXiangShan/DRAMsim3` fork's own tree, not separately vendored | **BSD-2-Clause-style** (`LICENSE.rst`, Victor Zverovich) | Comes with the `DRAMSIM3` base commit above |
| nlohmann/json | `DRAMSIM3/ext/headers/json.hpp` | Confirmed part of the same fork's tree | **MIT** (in-file SPDX tag, Niels Lohmann) | Comes with the `DRAMSIM3` base commit above |
| Catch2 | `DRAMSIM3/ext/headers/catch.hpp` | Confirmed part of the same fork's tree; "v2.7.0" in comments | **Boost Software License 1.0** (Two Blue Cubes Ltd) — the only BSL-1.0 code in this repository | Comes with the `DRAMSIM3` base commit above |
| inih / INIReader | `DRAMSIM3/ext/headers/{INIReader.h,INIHLICENSE.txt}` | Confirmed part of the same fork's tree | **BSD-3-Clause ("New BSD")**, Ben Hoyt | Comes with the `DRAMSIM3` base commit above |
| args.hxx | `DRAMSIM3/ext/headers/args.hxx` | Confirmed part of the same fork's tree | **MIT-style**, Taylor C. Richberger | Comes with the `DRAMSIM3` base commit above |

## 2. Local modifications discovered while attempting submodule conversion

The plan to convert the seven "pure vendor, no Intensivate content" components
to git submodules (see `README.md`/session discussion) assumed all seven were
clean. Finding and verifying exact upstream commits for each disproved that
assumption for four of them. This section documents what was found. **None of
the affected live source files were reverted or deleted** — see the
per-component reasoning below; each modification is instead extracted to a
standalone patch file for licensing clarity.

### chisel3 — one file, undisclosed, possibly a real behavioral bug

Base commit `9f620e06b` matches the entire tree exactly except
`core/src/main/scala/chisel3/Reg.scala`. The no-init `Reg.apply[T](t: T)`
overload was changed to unconditionally build a reset-to-zero register
instead of the base's uninitialized one — while the docstring immediately
above it, in the same file, still says *"reset is ignored."* Extracted to
`chisel3/INTENSIVATE-REG-INIT.patch`. **This is flagged for engineering
review, not just licensing** — it may be an intentional design choice or an
unintended side effect, and this audit cannot tell which. Not reverted:
nothing here lets this audit verify a rebuild is safe without it.

### hardfloat — real additions, less alarming than first suspected

Best-match base tag `v1.3-20200227-SNAPSHOT` differs from the vendored copy
in `FMA.scala` (new file, no upstream history anywhere) and 12 other `.scala`
files. Having read the actual diffs: most of the change is a mechanical
Chisel2-to-Chisel3 API port (`extends Module` → `extends chisel3.RawModule`,
implicit `io` bundles → explicit `IO(...)`), plus a small priority-encoder
optimization in `primitives.scala` whose core logic is commented in the
source itself as *"copied from rocket-chip repo."* Not novel proprietary
floating-point algorithm content, as originally feared when this was first
flagged mid-investigation. Extracted to
`hardfloat/INTENSIVATE-FPU-EXTENSIONS.patch`. Not reverted:
`src/main/scala/tile/FPU.scala` actively instantiates `hardfloat.FMA` —
removing it would break the build.

### DRAMSIM3 — real LPDDR5 support, not deeply proprietary either

Base commit `1d2a9bf` (a XiangShan-specific fork, not vanilla upstream DRAMSim3)
matches exactly except for genuine LPDDR5 protocol support added to
`src/configuration.{cc,h}` and `src/cosimulation.cc`, five new config files
(two named `intenscore_*.ini`), a retuned `DDR4_8Gb_x8_3200.ini`, and traffic-
generation additions to `scripts/trace_gen.py` plus a new `scripts/latency-bw.py`.
Having read the actual diffs: this is ordinary DRAM-protocol and
traffic-generator engineering — adding a newer JEDEC standard to a simulator
that already supports several older ones — not core CPU microarchitecture.
Extracted to `DRAMSIM3/INTENSIVATE-LPDDR5.patch`. Not reverted:
`emulator/Makefile` actively builds against
`configs/intenscore_DDR4_8Gb_x8_3200_1ch_2ra_16GB.ini` — removing it would
break the build.

### riscv-tests — no confirmed base, plus an unaccounted-for file

No exact upstream commit was found (the one lead — a cross-reference through
`riscv-tools.hash` — was checked and ruled out: 148 real content differences).
Separately, `isa/rv64in/cpy.S` exists in the vendored copy but nowhere in
upstream riscv-tests history, and `rv64in` isn't a real riscv-tests naming
convention. Its origin and license are unaccounted for. Not resolved.

### Why none of the four became submodules

A submodule has to be a clean pointer to unmodified upstream history. All
four have real content that either has no upstream counterpart at all
(`FMA.scala`, the DRAMSIM3 LPDDR5 files, `cpy.S`) or diverges from every
known upstream commit (`Reg.scala`). Converting any of them to a submodule as
originally planned would have silently discarded that content on the next
`git submodule update`. Doing this properly — for `chisel3`, `hardfloat`, and
`DRAMSIM3` — would mean Intensivate forking the relevant upstream repo,
committing this content there, and pointing a submodule at that fork.
`riscv-tests` needs its base commit resolved and `cpy.S`'s origin explained
first.

## 3. Third-party code found *inside* the Rocket Chip tree that is not Rocket Chip

`src/` is not uniformly SiFive/Berkeley-licensed Rocket Chip; it contains a
distinct, separately licensed component adapted from the XiangShan/DiffTest
project, plus a contribution credited to a third company.

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
directory on disk. Confirm before this is treated as final whether that
dependency was meant to ship and got missed, or is intentionally out of scope.

**Orphan license entities named by the repo's own tooling, with no matching
file found:** `scripts/{copyright-file,modify-copyright,authors}` are Rocket
Chip's own header-generation tools; they map `git blame`-majority authorship
to entities including Cambridge (Wei Song; post-2016 Matthew Naylor),
Microsoft (Ken McMillan), and LGE (SeungRyeol Lee). No `LICENSE.Cambridge`,
`LICENSE.Microsoft`, or `LICENSE.LGE` exists in this repo, and a direct grep
of every `// See LICENSE.X` pointer actually present in `src/` found only
`Berkeley`, `SiFive`, `jtag`, and one generic `// See LICENSE`. No
currently-manifesting gap, but recorded here so it isn't rediscovered from
scratch if more Rocket Chip source is ever added.

## 4. Files Intensivate wrote (carry `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`)

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
kept in sync with this count. §2's four components add real but
*unheadered* Intensivate content on top of this — not counted here since
they don't carry the SPDX identifier, but tracked via their own `ORIGIN.md`
and `INTENSIVATE-*.patch` files instead.

### Undisclosed-modification flag (within `src/` itself)

Four files in `src/main/scala/rocket/` — `Consts.scala`, `instrUnit.scala`,
`NBDcache.scala`, `pipeUnit.scala` — sit alongside the four Intensivate-headered
files above, share their unusual (non-Rocket-Chip-convention) names or recent
modification timestamps, but carry **only** the stock
`// See LICENSE.Berkeley` / `// See LICENSE.SiFive` pointer, with no
Intensivate notice and no comment stating a modification. This needs a
decision from whoever owns these files, not a guess from this audit.

Separately, `src/main/scala/system/IntenScaleChip.scala` — a file whose name
strongly suggests Intensivate-original top-level chip integration — carries
only the bare `// See LICENSE.SiFive for license details.` pointer, no
Intensivate header. Same flag.

**Scope note:** `src/main/scala/` has roughly 300+ `.scala` files. This audit
sampled every subdirectory and was exhaustive for the Mulan search and for
`rocket/`, but did not read every file in bulk directories like `tile/`,
`tilelink/`, and `interrupts/` individually — those were spot-checked and
found uniform. A full per-file pass has not been done.

## 5. Obligations that attach to this release

| Obligation | Source | What it requires |
|---|---|---|
| **State changes** | Apache-2.0 §4(b) (Rocket Chip base, api-config-chipsalliance) | If any vendored file is modified, the modification must be stated. The four `rocket/` files (§4) and the four §2 components are the open items; the documented `cache/` swap already states its changes in `cache_update.md` |
| **Retain notices** | all BSD/Apache/MIT components | Not independently re-verified file-by-file the way `benchmarks/` was for every component; confirmed clean for the three now-submoduled components specifically |
| **No endorsement** | BSD-3-Clause (Berkeley, chisel3, firrtl, hardfloat, riscv-tests, torture, jtag, SuperLU_MT_3.1) | Do not use "The Regents of the University of California" or any named individual contributor to endorse or promote products derived from this software |
| **Corresponding source for GPL-2 patches** | `patches/{linux.patch,qemu.patch}` | Both patch GPL-2 upstream projects (Linux kernel, QEMU). Both are ordinary unified diffs — no wholesale copy of GPL source was found in either. Whether shipping a diff against unmodified upstream GPL source (which this repo does not itself vendor) creates any distribution obligation is a legal question this audit does not resolve; flagged for counsel below |
| **Boost Software License 1.0 attribution** | `DRAMSIM3/ext/headers/catch.hpp` | Retain the license notice; the only file under this specific license in the repository |

## 6. Open items for IP counsel / engineering

1. **GPL-2 patches (`patches/linux.patch`, `patches/qemu.patch`)** — not a
   standard redistribution scenario, same category as `benchmarks/`'s BSMBench
   citation clause. Needs a legal read.
2. **The four undisclosed-modification `rocket/` files** and
   **`IntenScaleChip.scala`'s missing header** — needs an answer from
   whoever owns this code.
3. **Missing `difftest` package source** — confirm whether shipping without
   it is intentional.
4. **`torture/overnight/lib/mail.jar`** (CDDL 1.0) — confirmed genuinely
   upstream (§1), not locally added, but CDDL is not otherwise represented in
   this repository and has no license text under `LICENSES/`.
5. **`chisel3/INTENSIVATE-REG-INIT.patch`** — an engineering decision, not
   just a licensing one: is the reset-to-zero behavior of a documented
   "no-init" `Reg` intentional?
6. **`hardfloat/INTENSIVATE-FPU-EXTENSIONS.patch`** and
   **`DRAMSIM3/INTENSIVATE-LPDDR5.patch`** — real content now isolated and
   licensed on their own terms; the base trees they sit on are pinned to a
   verified (hardfloat: best-effort) commit. If a clean submodule is wanted
   for either eventually, it requires forking the relevant upstream first.
7. **`riscv-tests`** — no confirmed base commit, and `isa/rv64in/cpy.S` has
   no known upstream origin. Also contains compiled build artifacts
   (`*.riscv.dump`, an `intMul` binary) that shouldn't ship — a housekeeping
   issue separate from the provenance question.
8. **`src/main/scala/README`** linked two Google Docs by ID and an
   Intensivate GitHub wiki page; pulled in commit `060dc77` pending a check
   of their sharing settings. The exposure during the window it was public
   cannot be undone by editing the file now.
9. **Root `Makefile`** contains internal CI conventions (Jenkins-style
   branch/workspace variables, a `docker build` step). Not a licensing issue,
   but worth a scope decision on whether it should ship publicly.
