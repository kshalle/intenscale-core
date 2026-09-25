# intenscale-core

High Performance low power small area Enterprise level CPU core

## What is in this repository today

This repository contains the **Intensivate benchmark suite** under [`benchmarks/`](benchmarks/) and the **Intensivate CPU core** (a Rocket Chip derivative) under `src/`, `chisel3/`, `firrtl/`, `hardfloat/`, `macros/`, `api-config-chipsalliance/`, `DRAMSIM3/`, and the associated build, simulation and test infrastructure.

Start at [`benchmarks/README.md`](benchmarks/README.md). The core has its own file-by-file license review — [`CORE-LICENSE-AUDIT.md`](CORE-LICENSE-AUDIT.md) and [`CORE-PROVENANCE.md`](CORE-PROVENANCE.md) — but it is not as fully closed out as `benchmarks/`'s; see [`NOTICE.md`](NOTICE.md) and "Licensing of the CPU core" below for what that means in practice.

## Building and running the core

All of the build systems below (`emulator/`, `vsim/`, `regression/`) are driven from the top-level `Makefile`; run `make help` for the full target list. They all require a built RISC-V toolchain first:

```sh
export RISCV=$PWD/riscv-tools   # or: source set_env.sh
```

`riscv-tools` itself isn't part of this repository (it's excluded via `.gitignore`) and has to be built separately from [rocket-tools](https://github.com/freechipsproject/rocket-tools) before any of these targets will work; `emulator/Makefrag`'s include of the top-level `Makefrag` fails fast with a pointer back here if `$RISCV` isn't set.

The common targets:

| Target | What it does |
|---|---|
| `make build` | Elaborates `Inten1CoreConfig` through Chisel/FIRRTL and builds the fast (non-waveform) Verilator emulator. |
| `make debug` | Same, but builds the waveform-tracing (`-debug`) emulator used by `run-debug`/`run-torture-tests-debug`. |
| `make verilator` | Builds and installs just the pinned Verilator 4.028 toolchain under `emulator/verilator/` (see the bison note below). |
| `make run` | Runs the assembly and benchmark test suites against the `build` emulator. |
| `make run-debug` | Same, producing VCD/VPD waveforms from the `debug` emulator. |
| `make run-fast` | Runs tests without waveforms or disassembly output — the quickest pass/fail check. |
| `make run-torture-tests` | Runs the cache/AMO stress tests under `riscv-tests/torture`. |
| `make vsim-verilog` / `make vsim-debug` | Elaborates for the VCS/Xcelium flow in `vsim/` (needs a licensed simulator and, for gate-level runs, a foundry PDK — see `vsim/Makefile`). |
| `make regression` | Runs the full rocket-chip regression harness in `regression/` (needs `SUITE=...`; it bootstraps its own `rocket-tools` checkout, independent of `$RISCV` above). |
| `make clean` / `make distclean` | Removes emulator/vsim build outputs; `distclean` also removes the fetched Verilator source/install tree. |

Config, project and DRAMSim3 selection are the same variables `emulator/Makefrag` and `emulator/Makefile` define — e.g. `make debug CONFIG=freechips.rocketchip.system.Inten1CoreConfig` or `make run DRAMSIM3=0` — and pass straight through to the underlying build.

**Known issue:** building the pinned Verilator 4.028 release (`make verilator`) from source against a modern `bison` (confirmed with 3.8.2) fails elaborating `V3ParseGrammar.cpp` with `verilog.h: No such file or directory`. This is a bug in `verilator-4.028`'s bundled `bisonpre` script, not in this repo — the generated header is present and correct under a different name. The workaround is documented in `emulator/Makefrag-verilator`, next to the `verilator` target.

## License

**This repository is mixed-license.** Intensivate's own work in `benchmarks/` — the harness, the port glue, the workloads, the build and run scripts, and the documentation — is licensed under the **BSD 2-Clause** license and carries `SPDX-License-Identifier: BSD-2-Clause`. The suite also vendors six third-party programs under their own licenses, **including GPL-licensed source**; see [Third-party components](#third-party-components).

A small number of files — 21 as of this release — carry `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0` and are governed by the Intensivate Non-Commercial license described below, not by an OSI-approved open-source license. See [`NOTICE.md`](NOTICE.md) for exactly which files.

### Third-party components

`benchmarks/` vendors the following, unmodified, each under its own license:

| Component | License |
|---|---|
| GNU CC 2.5.8 | **GPL-2.0-only** (runtime library: same GPL with the runtime-library special exception) |
| Perl 4.036 | **GPL-1.0-or-later OR Artistic-1.0** |
| XLISP-PLUS | MIT |
| BSMBench 1.0 | BSD-3-Clause **plus a citation requirement** |
| libcint | Apache-2.0 |
| miniWeather | BSD-2-Clause |

The BSMBench citation requirement is an affirmative obligation that reaches datasheets, marketing material and blog posts quoting numbers from that port — not only academic papers. See [`NOTICE.md`](NOTICE.md).

License texts are in [`benchmarks/LICENSES/`](benchmarks/LICENSES/); the attribution inventory is in [`benchmarks/NOTICE.md`](benchmarks/NOTICE.md), the provenance record in [`benchmarks/PROVENANCE.md`](benchmarks/PROVENANCE.md), and the file-by-file position in [`benchmarks/LICENSE-AUDIT.md`](benchmarks/LICENSE-AUDIT.md).

## Licensing of the CPU core

The files below describe the terms that apply to core files carrying the identifier in this section — 21 files as of this release; see [`NOTICE.md`](NOTICE.md) for exactly which ones. The core's third-party components are inventoried in [`CORE-LICENSE-AUDIT.md`](CORE-LICENSE-AUDIT.md) and [`CORE-PROVENANCE.md`](CORE-PROVENANCE.md); that audit is still less complete than `benchmarks/`'s and lists several open items still needing a decision from engineering or counsel.

Three components with no Intensivate content on top of upstream — `firrtl/`, `api-config-chipsalliance/`, `torture/` — are real git submodules pinned at a verified upstream commit (see `.gitmodules`). Four more — `chisel3/`, `hardfloat/`, `DRAMSIM3/`, and `riscv-tests/` — turned out to carry real local modifications on top of their upstream base, so they stay vendored; for the three where that base commit is known (`chisel3`, `hardfloat`, `DRAMSIM3`), the modification itself is extracted into a standalone `INTENSIVATE-*.patch` file in that directory rather than left undocumented inside a permissively-licensed upstream file. `riscv-tests/` has no confirmed base commit and one file with no known upstream origin at all. Full detail is in `CORE-LICENSE-AUDIT.md` §1–2.

A full RTL generation (`emulator/`'s `make debug`, run after the submodule conversion) compiled and elaborated cleanly across all of this — `firrtl.jar` rebuilding from the `firrtl` submodule, and the vendored `chisel3`/`hardfloat`/`src/` trees compiling with only expected deprecation warnings — confirming none of it broke the build.

### Non-commercial source license

Core files under this license are identified by:

`SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`

and licensed under the **Intensivate Non-Commercial Hardware Source License v1.0**.

The license permits research, education, personal experimentation, other non-commercial uses, and the limited commercial-entity evaluation described in the license. It expressly covers Chisel source and derived/generated representations such as FIRRTL, MLIR/CIRCT, Verilog/SystemVerilog, synthesized netlists, FPGA bitstreams, physical-design data, and hardware prototypes.

See [`LICENSE.md`](LICENSE.md) for the complete terms. The REUSE/SPDX text is at [`LICENSES/LicenseRef-Intensivate-NC-1.0.txt`](LICENSES/LicenseRef-Intensivate-NC-1.0.txt).

This is a source-available non-commercial license. It is not represented as an OSI-approved open-source license.

### Commercial licensing

Commercial product development, commercial deployment, production manufacture, sale, commercial services, and other commercial uses of the core will require a separate written commercial license from Intensivate, Inc.

Commercial licensing inquiries: **info@intensivate.com**

See [`COMMERCIAL-LICENSE.md`](COMMERCIAL-LICENSE.md) for additional information.

### Patents

Technology implemented by the core is covered by issued patents and pending patent applications owned or controlled by Intensivate, Inc.

See [`PATENTS.md`](PATENTS.md) for the current patent notice.

Patent rights are granted only as expressly provided by the applicable license.
