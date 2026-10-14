# src/, macros/ — Rocket Chip core

Intensivate's CPU core: a Rocket Chip derivative, plus a distinct component
adapted from XiangShan/DiffTest, plus Intensivate-original files interleaved
throughout the same package tree.

## What is vendored here

Rocket Chip (`freechips.rocketchip.*` packages under `src/main/scala/`,
`src/main/resources/`, and `macros/`).

- URL: `https://github.com/chipsalliance/rocket-chip` (named directly in
  `build.sbt`'s `pomExtra`)
- Version: `1.2-SNAPSHOT` (`build.sbt`)
- Commit: **not recorded**. Best available fork point: upstream `50bb13d78`
  ("Merge pull request #1643 from freechipsproject/update-run-recipes",
  2018-09-25), the merge base between Intensivate's `i-rocket-chip` history
  and `chipsalliance/rocket-chip`. The core has diverged heavily since (it
  uses later chisel3 and has 2020-2024 local edits), so this is the point
  where the histories split, not a snapshot the tree matches. `i-rocket-chip`
  history before 2018-11-15 ("Added branch master from IntenCoreN_chip") is
  not available.
- Retrieved: unknown

## Licence

**Dual: Apache-2.0 and BSD-3-Clause, per file.** Files carry one of:

- `// See LICENSE.SiFive for license details.` — Apache License 2.0,
  Copyright 2016-2017 SiFive, Inc. Full text: `LICENSE.SiFive` /
  `LICENSES/LicenseRef-SiFive-Apache-2.0.txt`.
- `// See LICENSE.Berkeley for license details.` — BSD-3-Clause, Copyright
  2012-2014 The Regents of the University of California. Full text:
  `LICENSE.Berkeley`.
- `// See LICENSE.jtag for license details.` (all 6 files in
  `src/main/scala/jtag/`) — BSD-3-Clause-style, Copyright 2016 The Regents.
  Full text: `LICENSE.jtag` / `LICENSES/LicenseRef-chisel-jtag.txt`.

Most files carry both the SiFive and Berkeley pointer together.

## A distinct component lives inside this tree: XiangShan / DiffTest

11 files are not Rocket Chip at all — they carry a **Mulan PSL v2** header
naming the Institute of Computing Technology (Chinese Academy of Sciences),
Peng Cheng Laboratory, and (for two files) Axelera AI:

- `src/main/resources/csrc/{compress.cc,compress.h,ram.cc,ram.h}`
- `src/main/resources/csrc/{elfloader.cc,elfloader.h}` — Axelera AI only (2024)
- `src/main/scala/common/Mem.scala`
- `src/main/scala/device/{AXI4Memory,AXI4RAM,AXI4SlaveModule}.scala`
- `src/main/scala/util/{BitUtils,Hold}.scala`

Full text: `LICENSE.MulanPSL2` / `LICENSES/MulanPSL-2.0.txt`.

These files (plus three Intensivate-original DPI-C glue files under
`src/main/resources/vsrc/`) reference an external `difftest` package
(`import difftest.common.DifftestMem`) that has **no source anywhere in this
repository** — see `CORE-LICENSE-AUDIT.md` §5 item 3.

## Local modifications

**Not independently verified against a fresh upstream clone** — no commit is
recorded to clone against. What can be said from reading the tree itself:

- `src/main/scala/cache/` was deliberately swapped in from a different
  upstream (`chipsalliance/rocket-chip-inclusive-cache`, commit
  `f2e2c92bd1efb55c4007c98cae201a0864e2fd4a`) replacing a prior
  Intensivate-original cache implementation. Fully documented, including the
  exact adaptations made, in `src/main/scala/cache/cache_update.md` (pre-existing,
  not written for this audit).
- 21 files across `subsystem/`, `rocket/`, `chisel3_compat/`, and
  `resources/` are Intensivate-original, correctly headered with
  `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`. Listed in full in
  `CORE-LICENSE-AUDIT.md` §3.
- Four files (`rocket/{Consts,instrUnit,NBDcache,pipeUnit}.scala`) and
  `system/IntenScaleChip.scala` are flagged as possibly modified or
  possibly-Intensivate-original without a header stating so. **Unresolved —
  see `CORE-LICENSE-AUDIT.md` §5 item 2.**

- `bootrom/{bootrom.S,linker.ld,Makefile}` come from upstream Rocket Chip.
  `linker.ld` and `Makefile` are unchanged. `bootrom.S` is modified
  (`DRAM_BASE` 0x20000000 instead of 0x80000000, plus `li a2, 0xfff` /
  `csrw 0xf, a2`). `bootrom.img` is no longer tracked: the committed copy did
  not match `bootrom.S`; it is built by `bootrom/Makefile` (invoked from
  `Makefrag`).
- Seven files carry no licence header, upstream or here, and therefore fall
  under Rocket Chip's repository-level licences (Apache-2.0 / BSD-3-Clause):
  `resources/csrc/verilator.h`, `resources/vsrc/EICG_wrapper.v`,
  `devices/debug/{DebugRomContents,abstract_commands,dm_registers}.scala`,
  `prci/{ClockDomain,TestClockSource}.scala`. Compared with a current upstream
  clone: `verilator.h`, `DebugRomContents.scala` and `abstract_commands.scala`
  are byte-identical; `EICG_wrapper.v`, `dm_registers.scala`,
  `ClockDomain.scala` and `TestClockSource.scala` differ (Intensivate edits or
  an older upstream base; `EICG_wrapper.v` has 2024 Intensivate commits).

## Notes for future audit work

- No README, CHANGELOG, or dated comment anywhere in `src/` pins a specific
  upstream Rocket Chip commit or release.
- `src/main/scala/README` originally linked two Google Docs and an internal
  GitHub wiki page; redacted in commit `060dc77` pending a sharing-settings
  check — see `CORE-LICENSE-AUDIT.md` §5 item 6.
- `scripts/{copyright-file,modify-copyright,authors}` (Rocket Chip's own
  header-generation tooling) name Cambridge, Microsoft, and LGE as possible
  copyright holders elsewhere in Rocket Chip's history. None of those
  entities' code was found to actually be present in this tree (only
  Berkeley/SiFive/jtag pointers appear), but if more Rocket Chip source is
  ever pulled in, check for this before assuming BSD/Apache coverage.
