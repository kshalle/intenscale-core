# Notices

## Copyright

Copyright © 2016-2026 Intensivate, Inc.

All rights are reserved except for rights expressly granted under the applicable license.

## License

Files carrying:

`SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`

are licensed under the **Intensivate Non-Commercial Hardware Source License v1.0**.

See:

- `LICENSE.md`
- `LICENSES/LicenseRef-Intensivate-NC-1.0.txt`

This is a source-available non-commercial license. It is not represented as an OSI-approved open-source license.

Commercial use requires a separate written commercial license from Intensivate, Inc., except for the limited evaluation permission expressly stated in `LICENSE.md`.

## Scope of the Intensivate License

This repository is **mixed-license**. The Intensivate Non-Commercial license applies **only** to files that are wholly Intensivate-original and that carry the SPDX identifier above.

It does **not** apply to:

- Third-party files, which remain under the licenses granted by their own copyright holders.
- Intensivate modifications made *inside* a third-party file. Those modifications are contributed under that file's existing license — for example, the modifications to the SiFive-derived cache files are licensed under Apache 2.0 on the same terms as the original work, as recorded in each file's header.

Where a file carries no `SPDX-License-Identifier`, the license stated in its own header or in the nearest `LICENSE` file of the component that contains it governs. Absence of a header is not a grant under the Intensivate license.

## Patents

Technology represented in this repository may be covered by issued patents and pending patent applications owned or controlled by Intensivate, Inc.

See `PATENTS.md`.

Patent rights are granted only as expressly stated in the applicable license. No patent license arises from this notice itself.

## Generated Hardware and RTL

The license expressly addresses materials generated from the source, including FIRRTL, MLIR/CIRCT representations, Verilog/SystemVerilog, synthesized netlists, FPGA bitstreams, physical-design data, mask/layout data, and hardware implementations.

Generated files remain subject to the applicable license when derived from licensed source even if a generator does not reproduce the source-file header.

## Trademarks

No trademark, logo, trade-name, or branding rights are granted except as necessary to reproduce legal notices or accurately identify the origin of the materials.

## Third-Party Materials

This product includes software developed by third parties, as set out below. Each component remains subject to its own license; those licenses are reproduced at the paths given.

| Component | Copyright holder | License | Text |
|---|---|---|---|
| Rocket Chip generator, Chisel3, FIRRTL, Berkeley HardFloat, riscv-tests | The Regents of the University of California | BSD 3-Clause | `LICENSES/BSD-3-Clause.txt` |
| chisel-jtag | The Regents of the University of California | BSD 3-Clause (variant wording) | `LICENSES/LicenseRef-chisel-jtag.txt` |
| Composable cache, derived from the SiFive inclusive cache | SiFive, Inc. | Apache 2.0 | `LICENSE.Apache2`, `LICENSES/Apache-2.0.txt` |
| api-config-chipsalliance | ChipsAlliance | Apache 2.0 | `LICENSES/Apache-2.0.txt` |
| XiangShan-derived components | Institute of Computing Technology, Chinese Academy of Sciences; Peng Cheng Laboratory | Mulan PSL v2 | `LICENSES/MulanPSL-2.0.txt` |
| DRAMsim3 | University of Maryland Memory-Systems Research | MIT | ships with the component |
| Concurrency Kit | Samy Al Bahra; Olivier Houchard; Paul Khuong | BSD 2-Clause | ships with the component |

### Rocket Chip generator, Chisel, FIRRTL, Berkeley HardFloat, riscv-tests

Copyright (c) 2012-2019 The Regents of the University of California (Regents). All Rights Reserved.

Licensed under the BSD 3-Clause License. The canonical text is at `LICENSES/BSD-3-Clause.txt`; the chisel-jtag variant (Copyright (c) 2016 Regents) is at `LICENSES/LicenseRef-chisel-jtag.txt`. Vendored components additionally ship their own copies:

```
chisel3/src/LICENSE.txt          Chisel3
firrtl/src/LICENSE.txt           FIRRTL
hardfloat/LICENSE                Berkeley HardFloat
riscv-tests/LICENSE              riscv-tests
riscv-tests/env/LICENSE          riscv-tests environment
```

### SiFive inclusive cache and contributions

Copyright 2019 SiFive, Inc.

Licensed under the Apache License, Version 2.0.

The files under `src/main/scala/cache/` are derived from the SiFive inclusive cache, https://github.com/sifive/block-inclusivecache-sifive, and have been modified by Intensivate. Per-file modification notices are carried in the source headers as required by Apache License 2.0 section 4(b). Those headers reference `LICENSE.Apache2`, which is present at the repository root for that reason; its text is reproduced under `LICENSES/Apache-2.0.txt` for REUSE tooling.

**These files are not licensed under the Intensivate Non-Commercial license, and neither are Intensivate's modifications to them.**

SiFive also holds copyright in portions of Berkeley HardFloat and of api-config-chipsalliance.

### api-config-chipsalliance

Copyright ChipsAlliance. Licensed under the Apache License, Version 2.0. See `api-config-chipsalliance/LICENSE`.

### XiangShan-derived components

```
Copyright (c) 2020-2022 Institute of Computing Technology,
                        Chinese Academy of Sciences
Copyright (c) 2020-2022 Peng Cheng Laboratory
```

Licensed under Mulan PSL v2. See `LICENSES/MulanPSL-2.0.txt`, or obtain a copy at http://license.coscl.org.cn/MulanPSL2

THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.

Derived from XiangShan (https://github.com/OpenXiangShan/XiangShan). Files:

```
src/main/scala/device/AXI4Memory.scala
src/main/scala/device/AXI4RAM.scala
src/main/scala/device/AXI4SlaveModule.scala
src/main/scala/common/Mem.scala
src/main/scala/util/BitUtils.scala
src/main/scala/util/Hold.scala
src/main/resources/csrc/ram.cc
src/main/resources/csrc/ram.h
src/main/resources/csrc/compress.cc
src/main/resources/csrc/compress.h
```

### DRAMsim3

Copyright (c) 2019 University of Maryland Memory-Systems Research. Licensed under the MIT License. See `DRAMSIM3/LICENSE`.

DRAMsim3 bundles the following third-party components under `DRAMSIM3/ext/`:

```
fmt              Copyright (c) 2012-present Victor Zverovich
                 BSD 2-Clause. See DRAMSIM3/ext/fmt/LICENSE.rst

inih             Copyright (c) 2009 Ben Hoyt
                 BSD 3-Clause. See DRAMSIM3/ext/headers/INIHLICENSE.txt

nlohmann/json    Copyright (c) 2013-2019 Niels Lohmann
                 MIT License.

Catch2           Copyright (c) 2019 Two Blue Cubes Ltd.
                 Boost Software License 1.0.

UTF-8 decoder    Copyright (c) 2008-2009 Bjoern Hoehrmann
                 MIT License.

Grisu            Copyright (c) 2009 Florian Loitsch
                 MIT License.
```

STT-MRAM timing parameters in DRAMsim3 are derived from work whose copyright holder is BSC-CNS, authored by Kazi Asifuzzaman, Rommel Sanchez Verdejo and Petar Radojkovic (Barcelona Supercomputing Center). **Citation is required:**

> Kazi Asifuzzaman, Rommel Sanchez Verdejo, Petar Radojkovic. "Enabling a reliable STT-MRAM main memory simulation." MEMSYS '17, Washington DC, USA, 283-292. https://doi.org/10.1145/3132402.3132416

### Concurrency Kit

```
Copyright (c) 2009-2016 Samy Al Bahra
Copyright (c) 2011-2014 Olivier Houchard
Copyright (c) 2011-2014 Paul Khuong
```

BSD 2-Clause License. Located at `riscv-tests/common/ck/`.

### Autoconf-generated files

`riscv-tests/configure` contains code generated by GNU Autoconf, Copyright (C) 1992-2012 Free Software Foundation, Inc. It is distributed under the Autoconf configure-script exception, which permits unlimited distribution without restriction on the licensing of the resulting work. It imposes no GPL obligation on this product.

**No GPL or LGPL obligation attaches to any source in this repository.**

## Commercial Licensing

Commercial licensing inquiries:

**Intensivate, Inc.**  
**Email:** info@intensivate.com  
**Web:** https://intensivate.com/
