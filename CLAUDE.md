# Working in this repository

This repository is published publicly. It mixes Intensivate's own
source-available code with many third-party components. **Before every commit
(and before every push), run the licence and third-party IP check below and
report the result.** Do not skip it for "small" changes: most past findings came
from small additions (a prebuilt binary, a copied jar, a doc that named a
trademark).

## Licence map (what applies where)

- Intensivate's core files: `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`
  (non-commercial, source-available, **not** open source; patents apply, see
  `PATENTS.md`). Never describe the project as open source, Apache, or MIT.
- Intensivate's benchmark glue, scripts and docs: `BSD-2-Clause`.
- Everything else keeps its upstream licence (Apache-2.0, BSD, MIT, MulanPSL-2.0,
  BSL-1.0, GPL-2.0-only for gcc, GPL-1.0-or-later OR Artistic-1.0 for perl).
- Vendored upstream trees are kept byte-identical to upstream. Do not edit
  files inside them; put our changes in separate files or `*.patch` files
  (see `CORE-LICENSE-AUDIT.md`, `CORE-PROVENANCE.md`, each `ORIGIN.md`).
- BSMBench carries a citation requirement that applies to published numbers;
  see `benchmarks/NOTICE.md`.

## Pre-commit licence check

Look at the staged change (`git diff --cached --stat` and the diff itself) and
answer each item. If any answer is "yes" or "unsure", stop and tell the user
before committing.

1. **New third-party code or data?** Anything copied, vendored, downloaded or
   generated from another project. It needs: the upstream licence text in the
   tree or `LICENSES/`, an `ORIGIN.md` entry (source URL, commit or version,
   checksum), an entry in `NOTICE.md` / `CORE-PROVENANCE.md`, and a licence we
   can actually redistribute. No licence file means no inclusion.
2. **New binaries or build output?** No jars, `.a`, `.o`, executables, CMake
   output, simulator output, `.riscv` files or `generated-src`. They carry
   third-party code without notices and leak local paths. Download or build
   them in the bootstrap script or Makefile instead, and add them to
   `.gitignore`.
3. **SPDX headers on every new file we wrote.** Core files:
   `LicenseRef-Intensivate-NC-1.0`; benchmark glue under
   `riscv-tests/spec95-equivalent` and `benchmarks`: `BSD-2-Clause`. Files that
   cannot carry a comment go in the relevant `REUSE.toml`. For an edited
   upstream file, keep its original header and do not relabel it.
4. **Upstream files modified?** Do not edit a vendored tree. If it is
   unavoidable, record it in that tree's `ORIGIN.md` and the audit documents.
5. **Trademarks and names.** SPEC / SPEC CPU95 names may appear only as
   "modelled on" references with the non-affiliation statement in `NOTICE.md`.
   Never claim SPEC results, compatibility or endorsement. Do not use
   third-party or contributor names to endorse the project (BSD no-endorsement
   clauses).
6. **Claims about licensing in docs.** Anything saying what the licence is must
   match `LICENSE.md`. If you touch `README.md`, `NOTICE.md` or any skill file,
   re-read the licence statements in them.
7. **Leaks.** Grep the staged diff for local paths (`/home/`, `/tmp/`,
   `/workspace/`), personal emails, tokens, keys and passwords. Only
   `info@intensivate.com` belongs in the repo.
8. **Audit documents up to date?** If the change adds, removes or alters a
   third-party component, update `CORE-LICENSE-AUDIT.md` (and
   `benchmarks/LICENSE-AUDIT.md` for benchmarks) in the same commit.

Useful commands:

    git diff --cached --name-status
    git diff --cached --diff-filter=A --name-only          # new files
    git diff --cached | grep -n -E '/home/|/tmp/|/workspace/|BEGIN .*PRIVATE|ghp_|AKIA|password'
    git diff --cached --name-only | xargs -r file | grep -v -E 'text|empty'   # binaries

## Reporting

In the commit summary to the user, state which checks ran and what they
found, including anything skipped and why. Do not say "license check passed"
unless every item above was actually checked against the staged change.

## Pushing

Do not push without being asked. Never force-push, and never rewrite history
without explicit approval (the history is public). Commit messages end with the
co-author trailer the session instructs.
