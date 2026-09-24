# hardfloat — HardFloat IEEE floating-point package

## What is vendored here

HardFloat, John R. Hauser's IEEE floating-point arithmetic package, used by
Rocket Chip's FPU.

- URL: not recorded in this tree; upstream is `https://github.com/ucb-bar/berkeley-hardfloat`
- Version: `1.3-SNAPSHOT` (`build.sbt`: `name := "hardfloat"`, `scalaVersion := "2.12.8"`)
- Commit: **not recorded**
- Retrieved: unknown

## Licence

**BSD-3-Clause.** Full text: `LICENSE` — "Copyright (c) 2010-2015, The
Regents of the University of California."

Per-file headers are a full attribution block (more complete than
chisel3/firrtl's bare pointer comment), e.g.: *"This Chisel source file is
part of a pre-release version of the HardFloat IEEE Floating-Point Arithmetic
Package, by John R. Hauser... Copyright 2010-2017 The Regents of the
University of California."*

## Local modifications

Not verified — no commit is recorded to diff against.

## Notes for future audit work

Contains its own `sbt-launch.jar` (1.1MB) — a second copy of the same
standard sbt launcher already present at the repo root and in `torture/`.
Confirmed a legitimate standard sbt build-tool binary via `file`/size/MD5
comparison during this audit, not a licensing anomaly.
