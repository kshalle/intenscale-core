# chisel3 — Chisel hardware description language

**Stays vendored, not a submodule** — see "Local modifications" below.

## What is vendored here

Chisel3, the Scala-embedded hardware description language Rocket Chip is
written in.

- URL: `https://github.com/chipsalliance/chisel.git` (the project was
  renamed from `chisel3`/`freechipsproject/chisel3`; old URLs redirect here)
- Commit: `9f620e06bacc2882068adfd4972ec2e9a87ea723` (2020-04-20, "Mux1H: note
  results unspecified unless exactly one select signal is high (#1397)")
- Verified: full recursive diff against this commit found exactly **one**
  differing file (see below); everything else — every other source file,
  `build.sbt`, `build.sc`, `project/build.properties`, `project/plugins.sbt`
  — is byte-identical.

## Licence

**BSD-3-Clause.** Full text: `src/LICENSE.txt` — "Copyright (c) 2014 - 2019
The Regents of the University of California."

## Local modifications

**One file, real and undisclosed at the file level:**
`core/src/main/scala/chisel3/Reg.scala`. The no-init `Reg.apply[T](t: T)`
overload was changed to unconditionally build a reset-to-zero register
(`DefRegInit` with `init = 0.U.asTypeOf(reg)`) instead of the base's
uninitialized `DefReg`. The docstring directly above the affected code, in
this same file, still reads *"Construct a `Reg` from a type template with no
initialization value (reset is ignored)"* — which this change makes false.

The full diff is extracted to `INTENSIVATE-REG-INIT.patch` in this directory,
with `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`, for licensing
clarity and so it can be reviewed independently of the base.

**Not reverted from the live tree.** Nothing in this repo lets this audit
verify a rebuild is safe after removing the change, so the file was left as
shipped rather than "corrected" blind. See `CORE-LICENSE-AUDIT.md` §5 for the
engineering-review flag — whether this was an intentional design decision or
an unintended side effect is unresolved, and anyone relying on the documented
"reset is ignored" behavior for a no-init `Reg` anywhere in this codebase is
silently getting reset-to-zero instead.

## Why not a submodule

The rest of the tree matches upstream exactly, but this one file doesn't, and
a submodule can't carry a permanent local modification cleanly. Converting
this to a submodule would require forking `chipsalliance/chisel`, committing
this change there, and pointing the submodule at that fork — not done here.
