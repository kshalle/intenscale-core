# chisel3 — Chisel hardware description language

## What is vendored here

Chisel3, the Scala-embedded hardware description language Rocket Chip is
written in.

- URL: not recorded in this tree; upstream is `https://github.com/chipsalliance/chisel3` (or its Berkeley-era predecessor)
- Version: `3.3-SNAPSHOT` (`build.sbt`: `organization := "edu.berkeley.cs"`, `name := "chisel3"`)
- Commit: **not recorded**
- Retrieved: unknown

## Licence

**BSD-3-Clause.** Full text: `src/LICENSE.txt` — "Copyright (c) 2014 - 2019
The Regents of the University of California." This is the pre-CHIPS-Alliance
Berkeley license, not the Apache-2.0 relicense current Chisel3 ships under —
confirming this is an old (pre-2019/2020) snapshot, not a current checkout.

Per-file headers, where present, are the bare `// See LICENSE for license
details.` pointer comment.

## Local modifications

Not verified — no commit is recorded to diff against, and no byte-for-byte
comparison against upstream was performed.

## Notes for future audit work

The `3.3-SNAPSHOT` version string and Berkeley-only copyright (no SiFive line
anywhere in this directory) place this checkout in Chisel3's UC-Berkeley-era
history, before the CHIPS Alliance transition. Pinning an exact commit would
require cloning `chisel3` history around that period and diffing against this
tree's file contents — not attempted in this pass.
