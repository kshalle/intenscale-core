# torture — RISC-V Torture Test Generator

## What is vendored here

UC Berkeley's riscv-torture, a randomized instruction-sequence generator used
for regression testing.

- URL: not recorded in this tree; upstream is `https://github.com/ucb-bar/riscv-torture`
- Version: dated in `README`: "Author: Yunsup Lee and Henry Cook / Date:
  January 29th, 2012 / Version: (under version control)" — no further
  identifying string
- Commit: **not recorded**
- Retrieved: unknown

## Licence

**BSD-3-Clause.** Full text: `env/LICENSE` — same Regents text as
`riscv-tests/`. The torture generator's own code has no separate top-level
`LICENSE` file of its own; it relies on the `README` attribution plus the
`env/LICENSE` file that ships alongside it (the same test environment
`riscv-tests/` uses).

## Local modifications

Not verified — no commit is recorded to diff against.

## Notes for future audit work

Contains its own `sbt-launch.jar`, byte-identical to the copy in
`hardfloat/` (confirmed by size and MD5 during this audit) but a different,
older sbt version than the one at the repo root. Also contains
`overnight/lib/mail.jar` (Sun/Oracle `javax.mail`, **CDDL 1.0** — confirmed
from its bundled `META-INF/LICENSE.txt`), used only by an offline
test-reporting script. CDDL is not otherwise represented in this repository
and has no license text under `LICENSES/`; see `CORE-LICENSE-AUDIT.md` §5
item 4.
