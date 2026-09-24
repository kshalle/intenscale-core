# riscv-tests — RISC-V ISA test suite

**Stays vendored, not a submodule** — no exact upstream commit was found.

## What is vendored here

The RISC-V ISA compliance test suite.

- URL: `https://github.com/riscv-software-src/riscv-tests` (current name;
  formerly `riscv/riscv-tests`)
- Commit: **not found**. `riscv-tools.hash` at the repo root pins commit
  `e2c6d1577a75f506fe992c3eb20a75174504476e` in `chipsalliance/rocket-tools`
  (a *different* wrapper repo, not this test suite itself), whose own
  submodule reference points `riscv-tests` at
  `19bfdab48c2a6da4a2c67d5779757da7b073811d` (2020-03-30) — checked, and it
  is **not** the right commit: a full recursive diff against it found 148
  differences, including genuine content differences in real upstream test
  files, not just additions.
- Direction established but not resolved: the vendored copy's `isa/` has only
  19 subdirectories, while current upstream has 40+ (adds `zba/zbb/zbc/zbkb/
  zbkx/zbs/zfh/zicond/...` extension categories) — so this checkout predates
  all of those, but postdates the March 2020 candidate above (real files
  still differ). Narrowing further to an exact commit was not completed.

## Licence

**BSD-3-Clause.** Full text: `LICENSE`, `env/LICENSE` — "Copyright (c)
2012-2015, The Regents of the University of California."

## Unresolved anomaly

`isa/rv64in/cpy.S` exists in the vendored copy but **does not exist anywhere
in upstream riscv-tests** — not on current `master`, not discoverable via
search. `rv64in` is not a real riscv-tests naming convention (real ones are
`rv64{ui,um,ua,uf,ud,uc,mi,si,...}`). This looks like locally-added content
with unaccounted-for origin and license. Not resolved by this audit — needs
an answer from whoever added it.

## Other housekeeping note

The vendored tree contains compiled build artifacts (`*.riscv.dump` files, an
`intMul` binary) that a clean source vendor wouldn't have. Not a provenance
question, but shouldn't ship — flagged for cleanup separately from the
licensing question.

## Why not a submodule

No confirmed exact upstream commit exists to pin, and there's at least one
file with no known upstream origin at all. Converting to a submodule now
would either point at the wrong content (the rejected March 2020 candidate)
or require first resolving `cpy.S`'s origin and finding the real commit —
neither done here.
