# DRAMSIM3 — memory system simulator

**Stays vendored, not a submodule** — see "Local modifications" below.

## What is vendored here

DRAMsim3, forked for XiangShan's "Kunminghu" core cosimulation work —
**not** vanilla `umd-memsys/DRAMsim3`. The branch name (`cosim-kmh`) and the
vendored `xiangshan_kmh_default.ini` filename both reference this.

- URL: `https://github.com/OpenXiangShan/DRAMsim3.git`, branch `cosim-kmh`
- Commit: `1d2a9bf` ("Merge pull request #3 from OpenXiangShan/fix-clock")
- Verified: full recursive diff against this commit found only 15
  differences total (listed below), confirming this is the correct base.
  `ext/fmt/` and `ext/headers/{json.hpp,args.hxx,catch.hpp,INIReader.h,
  INIHLICENSE.txt}` are confirmed part of **this fork's own tree** at this
  commit, not separately vendored.
- Its own submodule, `ext/SuperLU_MT_3.1`, pins to
  `68739c5a23cc48c9a13d28793834e4fee95d33ee` ("openmp make file that
  actually works") — byte-for-byte identical to the vendored copy (the only
  difference is the stray `make.log` build artifact already removed before
  publication).

## Licence

**MIT.** Full text: `LICENSE` — "Copyright (c) 2019, University of Maryland
Memory-Systems Research."

## Local modifications

**Real LPDDR5 memory-controller support, not present anywhere in the fork's
601-commit history across its 10 branches:**

- `src/configuration.cc` / `src/configuration.h` — adds `LPDDR5` to the
  `DRAMProtocol` enum, a `bankgroup_enable` config field, and an `IsLP5()`
  accessor.
- `src/cosimulation.cc` — adds `"LPDDR5"` to the protocol-string dispatch.
- `configs/DDR4_8Gb_x8_3200.ini` — retuned timings and channel/address-mapping
  changes.
- Five new config files: `intenscore_DDR4_4Gb_x8_3200_2ch_2ra_16GB.ini`,
  `intenscore_DDR4_8Gb_x8_3200_1ch_2ra_16GB.ini`, `LPDDR5_16Gb_x8_6400.ini`,
  `DDR4_2Gb_x8_3200.ini`, `DDR4_2Gb_x8_3200_dualrank.ini`.
- `scripts/trace_gen.py` — adds grouped-burst read/write traffic generation
  (fixed-size read bursts followed by a write burst sized to the target
  ratio) alongside the base's per-request random mix.
- `scripts/latency-bw.py` — new file, no upstream counterpart.

Having actually read the diffs: this is ordinary DRAM-protocol-support and
traffic-generation engineering work — adding a newer JEDEC memory standard to
a simulator that already supports several — not deeply proprietary core
microarchitecture. Still real, undisclosed content sitting inside
permissively-licensed (MIT) files with no Intensivate notice.

The full diff is extracted to `INTENSIVATE-LPDDR5.patch` in this directory,
with `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`, for licensing
clarity.

**Not reverted from the live tree.** `emulator/Makefile` actively builds
against `configs/intenscore_DDR4_8Gb_x8_3200_1ch_2ra_16GB.ini`
(`-DDRAMSIM3_CONFIG=...`) — removing it to restore a pristine base would
break the build, and nothing here lets this audit verify a rebuild
afterward. The patch documents the delta; it does not get applied or
reverted.

## Why not a submodule

Real, load-bearing local modifications rule out a clean submodule. Would
need Intensivate to fork `OpenXiangShan/DRAMsim3` (or push to their own
branch of it) with this content committed, and point a submodule there.
