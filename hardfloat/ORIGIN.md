# hardfloat — HardFloat IEEE floating-point package

**Stays vendored, not a submodule** — see "Local modifications" below.

## What is vendored here

HardFloat, John R. Hauser's IEEE floating-point arithmetic package, used by
Rocket Chip's FPU.

- URL: `https://github.com/ucb-bar/berkeley-hardfloat.git`
- Best-matching tag: `v1.3-20200227-SNAPSHOT` (commit `26dfe5b`, 2020-02-27) —
  **not confirmed exact**. This is the closest of several candidates checked
  (16 remaining diff items vs. 19+ for adjacent tags/commits), matching on
  both `version := "1.3-SNAPSHOT"` and `scalaVersion := "2.12.8"`. `build.sbt`
  and `.gitignore` differ from this tag in ways consistent with later,
  ordinary upstream version drift (a plain `1.3-SNAPSHOT` version string and
  a `chisel3 -> "3.2-SNAPSHOT"` dependency bump, plus removed publish/POM
  metadata) rather than Intensivate authorship — not chased to an exact
  match.

## Licence

**BSD-3-Clause.** Full text: `LICENSE` — "Copyright (c) 2010-2015, The
Regents of the University of California."

## Local modifications

**Real, and substantive — not whitespace/formatting noise:**

- `src/main/scala/FMA.scala` is an entirely new file with **no history
  anywhere in upstream**, checked across every branch and tag.
- 12 files (`CompareRecFN.scala`, `DivSqrtRecF64_mulAddZ31.scala`,
  `DivSqrtRecF64.scala`, `DivSqrtRecFN_small.scala`, `INToRecFN.scala`,
  `MulAddRecFN.scala`, `primitives.scala`, `rawFloatFromFN.scala`,
  `rawFloatFromIN.scala`, `RecFNToIN.scala`, `RecFNToRecFN.scala`,
  `RoundAnyRawFNToRecFN.scala`) differ from the base tag.

Having actually read the diffs (not just counted them): most of this is
**Chisel2-to-Chisel3 API migration** — `extends Module` → `extends
chisel3.RawModule`, implicit `val io = new Bundle {...}` → explicit `val io =
IO(new Bundle {...})` — applied mechanically across the affected files.
`primitives.scala` additionally gains two small utility classes (`Assemble`,
`Encoder`) and a priority-encoder-tree optimization
(`opt_countLeadingZeros`/`EncTreeO`), the latter's core logic commented in
the source itself as *"This is copied from rocket-chip repo."* This is real
engineering work, not novel proprietary algorithm content as originally
feared when this was first flagged.

The full diff is extracted to `INTENSIVATE-FPU-EXTENSIONS.patch` in this
directory, with `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`, for
licensing clarity.

**Not reverted from the live tree.** `src/main/scala/tile/FPU.scala` actively
instantiates `hardfloat.FMA` (`val fpm = Module(new hardfloat.FMA(sigWidth))`)
— removing `FMA.scala` to restore a pristine base would break the build, and
nothing here lets this audit verify a rebuild afterward. The patch documents
the delta; it does not get applied or reverted.

## Why not a submodule

Real, load-bearing local modifications on top of an unconfirmed base commit
rule out a clean submodule. Would need Intensivate to fork
`ucb-bar/berkeley-hardfloat`, commit this content there (ideally against a
confirmed-exact base), and point a submodule at that fork.
