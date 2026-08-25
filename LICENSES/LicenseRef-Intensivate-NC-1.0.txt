# Intensivate Non-Commercial Hardware Source License v1.0

**SPDX identifier:** `LicenseRef-Intensivate-NC-1.0`

Copyright © 2016-2026 Intensivate, Inc. All rights reserved except as expressly granted by this License.

This License is a source-available, non-commercial license. It is intended to permit research, education, personal experimentation, and other non-commercial work with the Licensed Materials while reserving commercial rights to Intensivate, Inc.

Commercial rights are available separately from Intensivate, Inc.

## 1. Definitions

### 1.1 Licensor

“Licensor” means Intensivate, Inc., and any successor that owns or controls the applicable copyright or Patent Claims in the Licensed Materials.

### 1.2 Licensed Materials

“Licensed Materials” means any source code, hardware-description source, documentation, scripts, configuration files, test code, models, or other material distributed by Licensor that expressly identifies this License through:

`SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0`

or that is otherwise expressly stated by Licensor to be governed by this License.

Licensed Materials include Modified Materials and Generated Materials to the extent they are derived from or incorporate the Licensed Materials.

### 1.3 Source Materials

“Source Materials” means the preferred form of the Licensed Materials for making modifications. Source Materials include, as applicable, Chisel/Scala source, software source, hardware-description source, build scripts, configuration, and other human-maintained design source.

### 1.4 Modified Materials

“Modified Materials” means modifications, adaptations, translations, extensions, derivative works, or other changes based on the Licensed Materials.

### 1.5 Generated Materials

“Generated Materials” means materials generated directly or indirectly from the Licensed Materials or Modified Materials, including, as applicable:

- FIRRTL and other intermediate representations;
- MLIR or CIRCT intermediate representations;
- Verilog, SystemVerilog, or VHDL;
- simulation models;
- synthesized or technology-mapped netlists;
- FPGA bitstreams;
- placement, routing, timing, and physical-design databases;
- layout and mask-description data, including GDSII or OASIS;
- emulation images;
- and other machine-generated representations of the design.

A Generated Material remains subject to this License when the Generated Material is derived from the Licensed Materials, regardless of whether the Generated Material reproduces this License header.

### 1.6 Covered Hardware

“Covered Hardware” means an FPGA implementation, ASIC, integrated circuit, chiplet, system-on-chip, board, packaged device, prototype, or other physical or virtual hardware implementation that implements material portions of the Licensed Materials, Modified Materials, or Generated Materials.

### 1.7 Patent Claims

“Patent Claims” means claims of patents owned or controlled by Licensor, now or later, that Licensor has authority to license and that would necessarily be infringed solely by exercising the rights expressly granted under this License with respect to the Licensed Materials.

Patent Claims do not include claims that are infringed only because of additional technology, modifications, combinations, or functionality supplied by a licensee or a third party.

### 1.8 Commercial Purpose

“Commercial Purpose” means a purpose intended for, directed toward, or materially connected with commercial advantage, monetary compensation, revenue generation, or the development, operation, support, manufacture, marketing, licensing, sale, lease, distribution, or provision of a commercial product or service.

Commercial Purpose includes, without limitation:

- incorporation into or development of a product or service offered for sale, license, lease, subscription, or other consideration;
- use in production systems or business operations;
- use to provide hosted, cloud, consulting, engineering, design, verification, fabrication, or other paid services;
- manufacture or fabrication for commercial deployment or sale;
- use in a customer, partner, or paid research deliverable;
- use in product development, competitive product development, or commercial technology development;
- use whose results are intended primarily to improve, validate, market, price, sell, or operate a commercial product or service;
- and use by or for another person or entity where the use is performed for compensation or commercial benefit.

An organization’s tax status alone does not determine whether a use is commercial. A nonprofit organization can engage in a Commercial Purpose, and a for-profit organization can engage in a Permitted Evaluation under Section 1.10.

### 1.9 Non-Commercial Purpose

“Non-Commercial Purpose” means a purpose that is not a Commercial Purpose.

Examples include personal experimentation, hobbyist use, teaching, coursework, independent academic research, publication of academic research, and public-interest research, provided that the particular use is not undertaken for a Commercial Purpose.

### 1.10 Permitted Evaluation

“Permitted Evaluation” means internal inspection, compilation, simulation, benchmarking, testing, or prototyping by a commercial organization solely to determine whether to seek a separate commercial license from Licensor.

Permitted Evaluation does not include:

- production deployment;
- customer-facing use;
- incorporation into a commercial product or service;
- manufacture for sale or commercial deployment;
- delivery of the Licensed Materials or results derived from them to a customer as paid work;
- or use of the Licensed Materials to develop or improve a commercial product beyond what is reasonably necessary to evaluate whether to obtain a commercial license.

### 1.11 Permitted Purpose

“Permitted Purpose” means a Non-Commercial Purpose or a Permitted Evaluation.

## 2. Acceptance

You accept this License by exercising any right granted by it.

If you do not accept this License, you receive no rights under this License.

## 3. Copyright and Design Rights Grant

Subject to all terms and conditions of this License, Licensor grants you a worldwide, royalty-free, non-exclusive license, solely for a Permitted Purpose, to:

1. access, view, study, and analyze the Licensed Materials;
2. reproduce the Licensed Materials;
3. modify the Licensed Materials and create Modified Materials;
4. compile, elaborate, translate, simulate, emulate, synthesize, place, route, and otherwise process the Licensed Materials;
5. create and use Generated Materials;
6. implement the Licensed Materials, Modified Materials, or Generated Materials in FPGA, ASIC, or other hardware;
7. make or have made non-commercial prototypes and evaluation prototypes;
8. use Covered Hardware solely for a Permitted Purpose; and
9. redistribute the Licensed Materials, Modified Materials, and Generated Materials as expressly permitted by Section 6.

No right is granted for a Commercial Purpose except the limited Permitted Evaluation described in Section 1.10.

## 4. Patent License

### 4.1 Limited Patent Grant

Subject to all terms and conditions of this License, Licensor grants you a worldwide, royalty-free, non-exclusive patent license under the Patent Claims solely to the extent necessary to exercise the rights granted in Section 3 for a Permitted Purpose.

This patent license includes the right, solely for a Permitted Purpose, to make, have made, use, test, evaluate, and operate Covered Hardware implementing the Licensed Materials.

### 4.2 No Commercial Patent Rights

No patent license is granted for a Commercial Purpose other than the limited Permitted Evaluation described in Section 1.10.

Commercial manufacture, commercial deployment, sale, licensing, provision of services, or other Commercial Purpose requires a separate written commercial license from Licensor.

### 4.3 No Implied Patent Rights

Except for the express patent license in Section 4.1, no patent rights are granted by implication, estoppel, exhaustion, or otherwise.

The patent license applies only to Patent Claims necessarily infringed by the Licensed Materials themselves. It does not extend to claims infringed because of additional technology or combinations supplied by you or a third party.

### 4.4 Patent Litigation

If you or an entity acting on your behalf initiates patent litigation alleging that the Licensed Materials or their authorized use infringe a patent owned or controlled by you, the patent license granted to you under this Section 4 terminates as of the date that litigation is filed.

## 5. Contractors and Service Providers

You may provide the minimum Licensed Materials reasonably necessary to a contractor or service provider solely so that the contractor or service provider can perform work on your behalf for your Permitted Purpose, including simulation, EDA processing, FPGA services, prototyping, or fabrication of non-commercial or evaluation prototypes.

The contractor or service provider:

1. may act only on your behalf and for your Permitted Purpose;
2. receives no independent right to use the Licensed Materials;
3. must be bound by obligations that protect the Licensed Materials at least as strongly as this License for the work performed on your behalf; and
4. must return, delete, or retain the Licensed Materials only as legally required or as necessary to complete the permitted service.

A contractor’s receipt of ordinary compensation for performing such services does not, by itself, convert your otherwise Permitted Purpose into a Commercial Purpose, provided the contractor receives no independent right to exploit the Licensed Materials.

## 6. Redistribution

You may redistribute the Licensed Materials, Modified Materials, or Generated Materials only for a Permitted Purpose and only if all of the following conditions are satisfied:

1. You provide a copy of this License with the distribution, or a clear link to an unmodified copy of this License that is supplied with the same distribution.
2. You preserve all copyright, license, attribution, and patent notices included by Licensor.
3. You preserve applicable SPDX identifiers.
4. You clearly identify files or portions that you modified.
5. You license your modifications to the Licensed Materials under this same License when distributing those modifications.
6. You do not impose terms that purport to authorize Commercial Purpose use of the Licensed Materials.
7. If you distribute Generated Materials or Covered Hardware to another person, you make the corresponding Source Materials and your modifications to those Source Materials reasonably available to that recipient under this License, to the extent you have the right to do so.
8. You do not represent that Licensor endorses your modification, implementation, product, research result, or organization.

Each recipient receives rights, if any, directly from Licensor under this License. You do not receive authority to grant broader rights on Licensor’s behalf.

## 7. Commercial Licensing

Licensor reserves all rights for Commercial Purpose use except the limited Permitted Evaluation expressly granted by this License.

A separate written commercial license is required for, among other things:

- commercial product development;
- commercial deployment;
- incorporation into proprietary commercial products;
- production manufacture;
- commercial FPGA or ASIC deployment;
- sale or distribution of Covered Hardware for commercial use;
- commercial cloud or hosted services;
- and other Commercial Purpose use.

Commercial licensing inquiries may be directed to:

**Intensivate, Inc.**  
**Email:** info@intensivate.com  
**Web:** https://intensivate.com/

A separate commercial license may grant rights broader than, different from, or in place of the rights granted by this License.

## 8. Ownership

Licensor retains all right, title, and interest in and to the Licensed Materials except for the rights expressly granted by this License.

Your original modifications remain owned by their respective copyright owners, subject to the rights required for distribution under Section 6 and any separate contributor agreement applicable to contributions submitted to Licensor.

This License does not transfer ownership of any patent, copyright, mask-work right, trademark, trade secret, or other intellectual-property right.

## 9. Trademarks and Names

This License does not grant permission to use the trademarks, service marks, trade names, logos, or branding of Intensivate, Inc., except as reasonably necessary to reproduce required legal notices or accurately describe the origin of the Licensed Materials.

## 10. Third-Party Materials

Third-party materials included in or used with the repository remain subject to their respective licenses.

This License applies only to materials for which Licensor has identified `LicenseRef-Intensivate-NC-1.0` or otherwise expressly stated that this License applies.

## 11. Contributions

Nothing in this License obligates Licensor to accept contributions.

Submission of code or other material to Licensor does not, by itself, give Licensor the right to commercially relicense that contribution. Licensor may require a separate contributor license agreement, copyright assignment, patent license, or other written agreement before accepting a contribution.

## 12. Compliance and Termination

Your rights under this License automatically terminate if you materially violate this License.

If a violation is inadvertent and is fully cured within thirty (30) days after you first receive written notice of the violation from Licensor, Licensor may confirm reinstatement in writing.

Reinstatement is not automatic for:

- Commercial Purpose use outside the limited Permitted Evaluation;
- intentional removal or falsification of copyright, license, or patent notices;
- or conduct covered by Section 4.4.

Termination of this License does not limit any remedies available to Licensor for conduct occurring before termination.

## 13. Disclaimer of Warranty

THE LICENSED MATERIALS ARE PROVIDED “AS IS” AND “AS AVAILABLE,” WITHOUT WARRANTY OF ANY KIND, EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE.

TO THE MAXIMUM EXTENT PERMITTED BY LAW, LICENSOR DISCLAIMS ALL WARRANTIES, INCLUDING WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, NON-INFRINGEMENT, ACCURACY, AND FITNESS OR SAFETY FOR USE IN ANY PARTICULAR HARDWARE, SOFTWARE, OR SYSTEM.

YOU ARE SOLELY RESPONSIBLE FOR VERIFYING THE LICENSED MATERIALS AND ANY HARDWARE IMPLEMENTATION BEFORE USE.

## 14. Limitation of Liability

TO THE MAXIMUM EXTENT PERMITTED BY LAW, LICENSOR AND ITS OFFICERS, DIRECTORS, EMPLOYEES, CONTRIBUTORS, AND AGENTS WILL NOT BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, CONSEQUENTIAL, OR OTHER DAMAGES ARISING FROM OR RELATED TO THE LICENSED MATERIALS OR THIS LICENSE, INCLUDING LOSS OF DATA, PROFITS, REVENUE, BUSINESS, GOODWILL, OR USE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.

WHERE APPLICABLE LAW DOES NOT PERMIT COMPLETE EXCLUSION OF LIABILITY, LIABILITY WILL BE LIMITED TO THE MAXIMUM EXTENT PERMITTED BY LAW.

## 15. No Safety-Critical Representation

Licensor makes no representation that the Licensed Materials are designed, tested, certified, or suitable for safety-critical, life-support, medical, automotive-safety, aviation, nuclear, weapons, or other applications in which failure could cause death, personal injury, or significant physical or environmental damage.

## 16. Severability

If any provision of this License is held unenforceable, that provision will be interpreted to the minimum extent necessary to make it enforceable where possible, and the remaining provisions will remain in effect.

## 17. No Waiver

A failure by Licensor to enforce a provision of this License does not waive the right to enforce that provision or any other provision later.

## 18. Entire License; Separate Agreements

This License states the complete public license granted by Licensor for the Licensed Materials identified as governed by `LicenseRef-Intensivate-NC-1.0`.

A separate written agreement between Licensor and a particular licensee may modify or replace these terms for that licensee.

## 19. License Version

This is **Intensivate Non-Commercial Hardware Source License v1.0**.

No “or later version” permission is granted. A file licensed under this version remains governed by this version unless Licensor expressly relicenses that file.

---

**License identifier:** `LicenseRef-Intensivate-NC-1.0`  
**Licensor:** Intensivate, Inc.  
**Commercial licensing:** info@intensivate.com
