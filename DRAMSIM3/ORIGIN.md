# DRAMSIM3 — memory system simulator

## What is vendored here

DRAMsim3, a cycle-accurate DRAM simulator used for memory-system simulation
alongside the core.

- URL: not recorded in this tree (no README); upstream is `https://github.com/umd-memsys/DRAMsim3`
- Version: **not recorded**
- Commit: **not recorded**
- Retrieved: unknown

## Licence

**MIT.** Full text: `LICENSE` — "Copyright (c) 2019, University of Maryland
Memory-Systems Research." DRAMsim3's own `src/*.cc`/`*.h` carry no per-file
copyright headers; only the top-level `LICENSE` covers them.

## Nested vendored dependencies (under `ext/`)

`DRAMSIM3/.gitmodules` declares one submodule, `ext/SuperLU_MT_3.1` →
`https://github.com/umd-memsys/SuperLU_MT_3.1.git`. It is present as real
vendored content (455 files: `SRC/`, `TESTING/`, `EXAMPLE/`, `CBLAS/`, `DOC/`),
not a stub, but no `.git` directory exists — it was flattened into plain
files, not kept as an actual submodule of this repository.

| Dependency | Path | Licence | Evidence |
|---|---|---|---|
| SuperLU_MT_3.1 | `ext/SuperLU_MT_3.1/` | BSD-3-Clause | `License.txt`: "Copyright (c) 2003, The Regents of the University of California, through Lawrence Berkeley National Laboratory," includes endorsement clause |
| fmt | `ext/fmt/` | BSD-2-Clause-style | `LICENSE.rst`: "Copyright (c) 2012 - 2016/present, Victor Zverovich," no endorsement clause |
| nlohmann/json | `ext/headers/json.hpp` | MIT | In-file `SPDX-License-Identifier: MIT`, Copyright Niels Lohmann |
| Catch2 | `ext/headers/catch.hpp` | Boost Software License 1.0 | v2.7.0 (in-file comment), Two Blue Cubes Ltd — **the only BSL-1.0 code found anywhere in this repository** |
| inih / INIReader | `ext/headers/{INIReader.h,INIHLICENSE.txt}` | BSD-3-Clause ("New BSD") | Ben Hoyt |
| args.hxx | `ext/headers/args.hxx` | MIT-style | Taylor C. Richberger |

None of these five single-header/library dependencies has a recorded version
or commit in this tree beyond what's stated above (Catch2's v2.7.0 comment).

## Local modifications

Not verified for DRAMSim3 itself or for SuperLU_MT_3.1 — no commit is
recorded to diff against for either.

## Notes for future audit work

No `.gitmodules` exists at the repository root — `DRAMSIM3` itself is plain
vendored content, not tracked as a submodule of the parent repo, despite
having its own internal `.gitmodules` for `SuperLU_MT_3.1`. A stray build log
(`ext/SuperLU_MT_3.1/TESTING/MATGEN/make.log`) was found and removed before
publication as harmless build output that shouldn't ship.
