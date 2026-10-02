# Notices

## Copyright

Copyright © 2016-2026 Intensivate, Inc.

All rights are reserved except for rights expressly granted under the applicable license.

## What This Repository Currently Contains

As of this release, this repository contains the **Intensivate benchmark suite** under `benchmarks/` and the **Intensivate CPU core** (a Rocket Chip derivative) under `src/`, `chisel3/`, `firrtl/`, `hardfloat/`, `macros/`, `api-config-chipsalliance/`, `DRAMSIM3/`, and the associated build, simulation and test infrastructure at the repository root.

This matters for reading the rest of this notice, and for reading `LICENSE.md`:

- **A small number of files carry `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`.** The Intensivate Non-Commercial Hardware Source License is reproduced at `LICENSE.md` and `LICENSES/LicenseRef-Intensivate-NC-1.0.txt`. As of this release, 21 files carry that identifier and are governed by it: `set_env.sh`, `mkJunit.py`, three files under `vsim/`, and 16 Intensivate-original files under `src/` (in `subsystem/`, `rocket/`, `chisel3_compat/`, and `resources/`). Every other file in this repository is governed by whatever license its own header, its nearest `LICENSE` file, or (under `benchmarks/`) `REUSE.toml` states.
- **The core has a first-pass license audit, in [`CORE-LICENSE-AUDIT.md`](CORE-LICENSE-AUDIT.md) and [`CORE-PROVENANCE.md`](CORE-PROVENANCE.md), with per-component `ORIGIN.md` files for `src/`, `chisel3/`, `firrtl/`, `hardfloat/`, `api-config-chipsalliance/`, `DRAMSIM3/`, `riscv-tests/`, and `torture/`.** It is less complete than `benchmarks/`'s audit: no byte-for-byte upstream diff was performed, and almost none of the core's components have a recorded upstream commit or tag. `CORE-LICENSE-AUDIT.md` §5 lists the open items still needing a decision from engineering or counsel, including an undisclosed-modification question on four `rocket/` files, a missing external `difftest` dependency, and the GPL-2 patches in `patches/`. Treat the core's licensing position as provisional pending those items.
- Intensivate's own work in `benchmarks/` — the harness, the port glue, the workloads, the build and run scripts, and the documentation — is licensed under the **BSD 2-Clause** license, not under the Non-Commercial license. Those files carry `SPDX-License-Identifier: BSD-2-Clause`.
- `COMMERCIAL-LICENSE.md` and `PATENTS.md` likewise describe terms attaching to the core. No commercial license is required for anything in this repository as it currently stands.

`benchmarks/` is a self-contained licensing package. `benchmarks/LICENSE`, `benchmarks/NOTICE.md`, `benchmarks/LICENSES/`, `benchmarks/PROVENANCE.md`, `benchmarks/LICENSE-AUDIT.md` and `benchmarks/REUSE.toml` are authoritative for everything under it. This root notice summarizes; where the two differ, the files under `benchmarks/` govern.

## License

Files carrying:

`SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`

are licensed under the **Intensivate Non-Commercial Hardware Source License v1.0**.

See:

- `LICENSE.md`
- `LICENSES/LicenseRef-Intensivate-NC-1.0.txt`

This is a source-available non-commercial license. It is not represented as an OSI-approved open-source license.

Commercial use requires a separate written commercial license from Intensivate, Inc., except for the limited evaluation permission expressly stated in `LICENSE.md`.

As stated above, 21 files currently carry that identifier.

## Scope of the Intensivate License

This repository is **mixed-license**. The Intensivate Non-Commercial license applies **only** to files that are wholly Intensivate-original and that carry the SPDX identifier above.

It does **not** apply to:

- Third-party files, which remain under the licenses granted by their own copyright holders.
- Intensivate modifications made *inside* a third-party file. Those modifications are contributed under that file's existing license.
- Intensivate-original files that carry a different SPDX identifier. All of Intensivate's work under `benchmarks/` is in this category: it is BSD 2-Clause by deliberate choice.

Where a file carries no `SPDX-License-Identifier`, the license stated in its own header, in the nearest `LICENSE` file of the component that contains it, or in `benchmarks/REUSE.toml` governs. Absence of a header is not a grant under the Intensivate license.

## Patents

Technology represented in Intensivate's CPU core may be covered by issued patents and pending patent applications owned or controlled by Intensivate, Inc.

See `PATENTS.md`.

Patent rights are granted only as expressly stated in the applicable license. No patent license arises from this notice itself.

## Trademarks

No trademark, logo, trade-name, or branding rights are granted except as necessary to reproduce legal notices or accurately identify the origin of the materials.

SPEC and the SPEC CPU95 benchmark names (e.g. 126.gcc, 130.li, 134.perl, 145.fpppp, 103.su2cor, 102.swim) are trademarks of the Standard Performance Evaluation Corporation. They are used here only to identify the kind of program each open-source port is modelled on. This project is not affiliated with, sponsored by or endorsed by SPEC, contains no SPEC software, inputs or reference outputs, and its results are not SPEC results and must not be reported or described as SPEC CPU95 results.

## Third-Party Materials

This product includes software developed by third parties, as set out below. Each component remains subject to its own license. All are vendored under `benchmarks/`, and all license texts are reproduced in `benchmarks/LICENSES/`.

| Component | Copyright holder | License | Vendored at |
|---|---|---|---|
| GNU CC 2.5.8 | Free Software Foundation, Inc. | **GPL-2.0-only**; runtime library under the same GPL with the runtime-library special exception | `benchmarks/compiler-gcc/gcc-2.5.8` |
| Perl 4.036 | Larry Wall | **GPL-1.0-or-later OR Artistic-1.0**, at the recipient's option | `benchmarks/interpreter-perl/perl-4.036` |
| XLISP-PLUS | David Michael Betz; Luke Tierney; Hewlett-Packard Company; and others | MIT | `benchmarks/interpreter-xlisp/xlisp-plus` |
| BSMBench 1.0 | Claudio Pica; Agostino Patella; Antonio Rago; Luigi Del Debbio; Biagio Lucini; Edward Bennett | BSD 3-Clause **with an additional citation requirement** | `benchmarks/lattice-bsmbench/BSMBench` |
| libcint | Qiming Sun | Apache 2.0 | `benchmarks/qchem-libcint/libcint` |
| miniWeather | National Center for Computational Sciences, Oak Ridge National Laboratory; NVIDIA Corporation | BSD 2-Clause | `benchmarks/weather-miniweather/miniWeather` |

Full copyright statements, contributor credits and the per-tree provenance record are in `benchmarks/NOTICE.md` and `benchmarks/PROVENANCE.md`.

### Core third-party components

Full detail, evidence, and open items are in [`CORE-LICENSE-AUDIT.md`](CORE-LICENSE-AUDIT.md) and [`CORE-PROVENANCE.md`](CORE-PROVENANCE.md). Summary:

| Component | Copyright holder | License | Path |
|---|---|---|---|
| Rocket Chip | SiFive, Inc.; The Regents of the University of California | Apache-2.0 (`LICENSE.SiFive`) and BSD-3-Clause (`LICENSE.Berkeley`), per file | `src/`, `macros/` |
| rocket-chip-inclusive-cache | SiFive, Inc. | Same as Rocket Chip | `src/main/scala/cache/` |
| chisel-jtag | The Regents of the University of California | `LicenseRef-chisel-jtag` (BSD-3-Clause-style) | `src/main/scala/jtag/` |
| XiangShan / DiffTest | Institute of Computing Technology, CAS; Peng Cheng Laboratory; Axelera AI | Mulan PSL v2 | 11 files under `src/main/scala/{device,util,common}` and `src/main/resources/csrc/` |
| chisel3 | The Regents of the University of California | BSD-3-Clause | `chisel3/` |
| firrtl | The Regents of the University of California | BSD-3-Clause | `firrtl/` |
| hardfloat | The Regents of the University of California | BSD-3-Clause | `hardfloat/` |
| api-config-chipsalliance | SiFive, Inc. | Apache-2.0 | `api-config-chipsalliance/` |
| DRAMSim3 | University of Maryland Memory-Systems Research | MIT | `DRAMSIM3/` |
| SuperLU_MT_3.1 | The Regents of the University of California (LBNL) | BSD-3-Clause | `DRAMSIM3/ext/SuperLU_MT_3.1/` |
| fmt | Victor Zverovich | BSD-2-Clause-style | `DRAMSIM3/ext/fmt/` |
| nlohmann/json | Niels Lohmann | MIT | `DRAMSIM3/ext/headers/json.hpp` |
| Catch2 | Two Blue Cubes Ltd | Boost Software License 1.0 | `DRAMSIM3/ext/headers/catch.hpp` |
| inih / INIReader | Ben Hoyt | BSD-3-Clause | `DRAMSIM3/ext/headers/{INIReader.h,INIHLICENSE.txt}` |
| args.hxx | Taylor C. Richberger | MIT-style | `DRAMSIM3/ext/headers/args.hxx` |
| riscv-tests | The Regents of the University of California | BSD-3-Clause | `riscv-tests/` |
| riscv-torture | Yunsup Lee; Henry Cook; The Regents of the University of California | BSD-3-Clause | `torture/` |
| javax.mail | Oracle/Sun | CDDL 1.0 | `torture/overnight/lib/mail.jar` |

`javax.mail`, used only by an offline test-reporting script, is licensed under
**CDDL 1.0** — a license family not otherwise represented in this repository
and for which no license text is currently reproduced under `LICENSES/`.
Flagged in `CORE-LICENSE-AUDIT.md` §5 for resolution.

### GPL material is present

This repository vendors GPL-licensed source. GNU CC 2.5.8 is licensed under the GNU General Public License, version 2 only. Perl 4.036 is licensed under the GNU General Public License version 1 or later, or the Artistic License, at the recipient's option.

GCC's runtime library sources (`libgcc1.c`, `libgcc2.c`) are under that same GPL with the **runtime-library special exception**, which provides that linking them into an executable compiled with GCC does not by itself place that executable under the GPL. They are **not** LGPL. The upstream GCC release also ships `COPYING.LIB`, but no source in the vendored tree is licensed under it.

Those trees are vendored **unmodified**, as upstream releases, and are separate works from Intensivate's BSD 2-Clause harness. Anyone redistributing this repository, or a product derived from it, must satisfy the obligations of those licenses for those trees — including the corresponding-source obligation for any distributed binary built from them.

### Development Docker image

The setup script (`intensivate-intenscale-core-bootstrap.sh`) downloads a prebuilt Docker image containing the build and simulation toolchain. The image is not part of this repository and is not covered by the Intensivate license; it is a collection of third-party software, each part under its own license. It is distributed unmodified from the upstream builds listed below, apart from the configuration Intensivate adds in `/opt/cocotb_tests`, `/opt/entrypoint-link-tests.sh` and `/root/Dockerfile.mounted`.

| | |
|---|---|
| Download | Google Drive file ID `18v83VDUYBxx1qXW7w-3OW-VV5pZB-Z7M` (`INTENSCORE_DOCKER_IMAGE_FILEID` in `prepare_env.conf`) |
| File | `intenscore-docker-image.gz`, 2,044,545,509 bytes |
| File SHA-256 | `469348af2ac606149893cb789ff224796f0f2e62dd7d938bd6283cffaf45c983` |
| Loads as | `intensivate/i-rocket-mounted:0.1.0` (tagged `intensivate/intenscore:0.1.0` by the setup script) |
| Image ID | `sha256:d049ccac5fb7cd778a4711b421025a037d6d623a3c66d972e1e6e076bd095e1c` |
| Built | 2026-08-13, linux/amd64, base Ubuntu 22.04.5 LTS |

| Component | Version | License | Where |
|---|---|---|---|
| Ubuntu 22.04 packages (652, from `archive.ubuntu.com` jammy `main`, `universe`) | as installed | Per package: mostly GPL, LGPL, BSD, MIT, Apache-2.0, BSL-1.0; copyright files in `/usr/share/doc/<package>/copyright` | system |
| RISC-V GNU toolchain: GCC, binutils, GDB, newlib | GCC 10.2.0, binutils 2.35; built from `riscv-collab/riscv-gnu-toolchain` at its state on 2020-12-01 | GPL-3.0-or-later (GCC with the GCC Runtime Library Exception); newlib under various permissive licenses | `/opt/riscv-native` |
| Spike (riscv-isa-sim) | v1.1.0 | BSD-3-Clause | `/opt/riscv-native/bin/spike` |
| Verilator | v4.034 (two builds) | LGPL-3.0-only OR Artistic-2.0 | `/opt/verilator-4.034`, `/opt/verilator-cocotb` |
| bison | 3.0.4 (Ubuntu `3.0.4.dfsg-1build1`, extracted from the package) | GPL-3.0-or-later with the Bison exception | `/opt/bison-3.0.4` |
| OpenJDK | 1.8.0_492 (Ubuntu package) | GPL-2.0 with the Classpath Exception | system |
| sbt, plus about 660 cached build dependency jars | sbt 1.3.4 | Apache-2.0 (sbt); per-jar licenses for the cache (Apache-2.0, BSD, MIT) | `/opt/sbt`, `/home/intens4` |
| riscv-tests (upstream copy) | `riscv-software-src/riscv-tests` at its state on 2020-12-01 | BSD-3-Clause | `/opt/riscv-upstream-tests-src` |
| cocotb, cocotb-bus, pytest, pluggy, Pygments, and other Python packages | cocotb 2.0.1 | BSD-3-Clause, MIT, Apache-2.0 OR BSD-2-Clause, BSD-2-Clause, PSF-2.0; **scapy 2.7.0 is GPL-2.0-only** | `/opt/cocotb-venv` |
| Intensivate repository `riscv-tests` copy | this repository | BSD-3-Clause (upstream) with Intensivate changes | `/opt/riscv-org-tests` |

**GPL source.** The image contains binaries of GPL and LGPL software (the toolchain, bison, OpenJDK, scapy, and Ubuntu's GPL packages). Source for the Ubuntu packages is available from the Ubuntu archive for the versions installed. Source for the toolchain is the `riscv-collab/riscv-gnu-toolchain` repository and its submodules; the exact commits used are being recorded (the image was built against a date, not a commit). To request the corresponding source for anything in this image, write to **info@intensivate.com**. Anyone who redistributes the image must meet these source obligations themselves.

**Open items** (also in `CORE-LICENSE-AUDIT.md`): exact toolchain commit pins; license texts for the source-built tools inside the image (none are installed under `/opt/riscv-native` today); a published package manifest; whether scapy can be removed; licenses for three Python packages whose metadata is blank (`cocotb-bus`, `exceptiongroup`, `setuptools`).

### Citation requirement (BSMBench)

Clause 3 of BSMBench's license is an affirmative obligation, not a notice-retention term. Any publication in any form derived from the use of that software, or of any modification of it, must refer explicitly to the original BSMBench package, including its official URL, and cite the two papers listed in `benchmarks/NOTICE.md`.

This reaches datasheets, marketing material and blog posts that quote numbers from the `bsmbench` port, not only academic papers. Whoever signs off on published performance figures needs to know.

### No endorsement (BSMBench)

Clause 4 of BSMBench's license: the names of its copyright holders and contributors may not be used to endorse or promote products derived from the software without specific prior written permission. Do not use their names in marketing material, product naming or press materials for anything built on this suite.

## Commercial Licensing

Commercial licensing inquiries:

**Intensivate, Inc.**  
**Email:** info@intensivate.com  
**Web:** https://intensivate.com/
