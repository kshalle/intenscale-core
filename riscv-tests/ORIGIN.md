# riscv-tests — RISC-V ISA test suite

## What is vendored here

The RISC-V Foundation / UC Berkeley ISA compliance test suite, used to
validate the core's instruction execution.

- URL: not recorded in this tree (no README); upstream is `https://github.com/riscv-software-src/riscv-tests`
- Version: **not recorded**
- Commit: **not recorded**
- Retrieved: unknown

## Licence

**BSD-3-Clause.** Full text: `LICENSE` and `env/LICENSE` — "Copyright (c)
2012-2015, The Regents of the University of California," standard
retain-notice / no-endorsement terms.

## Local modifications

Not verified — no commit is recorded to diff against.

## Notes for future audit work

`riscv-tools.hash` (repo root) pins a commit for the separate `riscv-tools`
*toolchain* (`e2c6d1577a75f506fe992c3eb20a75174504476e`) used to build/run
these tests — that is not a version reference for the test-suite source
vendored here, and shouldn't be confused with one.
