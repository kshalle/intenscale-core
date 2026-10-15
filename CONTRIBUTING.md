# Contributing

Thank you for your interest in the Intensivate CPU Core.

Because this project uses a dual-licensing model, contribution rights must be handled carefully so that Intensivate can continue to offer both the public non-commercial license and separate commercial licenses.

## Issues, Bug Reports, and Discussion

You may open issues for:

- bug reports;
- questions;
- feature suggestions;
- documentation corrections;
- performance observations;
- and other discussion.

Please avoid attaching substantial code or RTL to an issue unless Intensivate has asked you to do so under an appropriate contributor agreement.

## Code and RTL Contributions

Do **not** submit substantial source-code, Chisel, RTL, generated RTL, testbench, or other copyrightable implementation contributions unless Intensivate has confirmed in writing that the applicable contributor agreement is in place.

A pull request may be reviewed for discussion purposes, but Intensivate may decline to merge it until the required contributor agreement, copyright license or assignment, and patent license have been completed.

## Which Files This Applies To

Only some files in this repository are Intensivate's own. The rules above apply to files that carry `SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0` (see `NOTICE.md` for the list).

- **Third-party and vendored files** (for example Rocket Chip, Chisel, hardfloat, DRAMsim3, `riscv-tests`, and the programs under `benchmarks/`) keep their upstream licenses. Intensivate does not maintain them. Please send changes to the upstream project.
- **Files under `benchmarks/` that Intensivate wrote** are BSD-2-Clause, not the non-commercial license. Please contact Intensivate before submitting substantial work there.

## Why This Is Required

The repository is dual licensed:

1. public non-commercial rights are provided under `LicenseRef-Intensivate-NC-1.0`; and
2. Intensivate separately grants proprietary commercial licenses.

To preserve that model, Intensivate must have sufficient rights to commercially relicense accepted contributions.

A contribution made only under the public repository license may not provide those rights.

## Contribution Agreement

Before merging an external implementation contribution, Intensivate should obtain a written contributor agreement that addresses, as appropriate:

- copyright ownership or a broad copyright license;
- the right for Intensivate to relicense the contribution under commercial and non-commercial terms;
- patent rights covering the contribution;
- representations that the contributor has authority to contribute the material;
- disclosure of third-party material;
- and, for contributions made by employees, authority from the contributor’s employer where required.

## Small Corrections

Intensivate may accept small corrections without a separate contributor agreement. A small correction is limited to spelling, grammar, whitespace, and comment or documentation fixes that change no code, RTL, configuration or hardware behavior. Intensivate's maintainers decide whether a change qualifies; if in doubt, ask first at **info@intensivate.com**. An accepted small correction is taken under the license of the file it corrects.

## License of Existing Repository Materials

Nothing in this file changes the license applicable to existing repository materials.

See:

- `LICENSE.md`
- `PATENTS.md`
- `NOTICE.md`
