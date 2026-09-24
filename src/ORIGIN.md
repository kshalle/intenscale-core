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
- Commit: **not recorded**
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
