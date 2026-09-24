# firrtl — FIRRTL intermediate representation and compiler

## What is vendored here

FIRRTL, the intermediate-representation compiler Chisel3 lowers to and that
Rocket Chip's build depends on.

- URL: not recorded in this tree; upstream is `https://github.com/chipsalliance/firrtl` (or its Berkeley-era predecessor)
- Version: `1.3-SNAPSHOT` (`build.sbt`: `organization := "edu.berkeley.cs"`, `name := "firrtl"`)
- Commit: **not recorded**
- Retrieved: unknown

## Licence

**BSD-3-Clause.** Full text: `src/LICENSE.txt` — identical Berkeley text to
`chisel3/src/LICENSE.txt`, "Copyright (c) 2014 - 2019 The Regents of the
University of California." Same era as the vendored `chisel3/`; pre-CHIPS
Alliance, pre-Apache-2.0-relicense.

Per-file headers, where present, are the bare `// See LICENSE for license
details.` pointer comment.

## Local modifications

Not verified — no commit is recorded to diff against.

## Notes for future audit work

Retains a `.github/` directory (issue/PR templates, `CODEOWNERS`, a
`.mergify.yml` referencing Travis CI and `base=master`) confirming this was
pulled from a genuine GitHub-hosted snapshot, but none of that carries a
commit hash or tag. Pinning an exact commit would require cloning `firrtl`
history and diffing — not attempted in this pass.
