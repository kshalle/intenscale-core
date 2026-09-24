# intenscale-core

High Performance low power small area Enterprise level CPU core

## What is in this repository today

This repository contains the **Intensivate benchmark suite** under [`benchmarks/`](benchmarks/) and the **Intensivate CPU core** (a Rocket Chip derivative) under `src/`, `chisel3/`, `firrtl/`, `hardfloat/`, `macros/`, `api-config-chipsalliance/`, `DRAMSIM3/`, and the associated build, simulation and test infrastructure.

Start at [`benchmarks/README.md`](benchmarks/README.md). The core has not yet received the file-by-file license review `benchmarks/` received; see [`NOTICE.md`](NOTICE.md) for what that means in practice.

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

The files below describe the terms that apply to core files carrying the identifier in this section — 21 files as of this release; see [`NOTICE.md`](NOTICE.md) for exactly which ones. The core's third-party components are inventoried in [`CORE-LICENSE-AUDIT.md`](CORE-LICENSE-AUDIT.md) and [`CORE-PROVENANCE.md`](CORE-PROVENANCE.md); that audit is less complete than `benchmarks/`'s (no upstream commit is pinned for most components) and lists several open items still needing a decision from engineering or counsel.

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
