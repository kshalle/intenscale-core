# intenscale-core

A high-performance, low-power, small-area enterprise-class RISC-V CPU core by
Intensivate, Inc. It is built on Rocket Chip and adds a multi-context front end:
several hardware contexts share each pipeline. The repository contains the RTL
(Chisel), a Verilator simulation flow with a cycle-accurate DRAM model
(DRAMSim3), and bare-metal tests and benchmarks.

## Documentation

This README is a quick start. The details are in:

- [Environment setup guide](https://docs.google.com/document/d/1OlcykUQS1hj4XRypEw9uOgcc1sebWiWdJ8OAlEy2pTQ/edit?tab=t.0): the Docker environment and prerequisites.
- [Simulations, benchmarks and RTL guide](https://docs.google.com/document/d/1bM5lpRt2ft-x2dN5SLFLMmIczX7vbF0up7DO86N6FQ4/edit): building and running simulations, running and adding benchmarks, configurations, debugging, and changing the RTL.

## Quick start

**1. Set up the environment.** Use the Docker image described in the
[setup guide](https://docs.google.com/document/d/1OlcykUQS1hj4XRypEw9uOgcc1sebWiWdJ8OAlEy2pTQ/edit?tab=t.0),
which has all the tools preinstalled. Alternatively, on your own Ubuntu machine, build the RISC-V toolchain from
[rocket-tools](https://github.com/freechipsproject/rocket-tools) into
`riscv-tools/` and then:

```sh
source set_env.sh        # sets RISCV and PATH
make preflight           # checks the required host tools and their versions
```

**2. Build DRAMSim3** (once per checkout):

```sh
cd DRAMSIM3 && mkdir -p build && cd build && cmake -DCOSIM=1 .. && make && cd ../..
```

**3. Build the simulator and run the tests** (`make help` lists every target):

```sh
make build                                   # Verilator emulator, Inten1CoreConfig
make run                                     # ISA tests + benchmarks
make -C emulator run-asm-tests               # ISA tests only
make -C emulator run-bmark-tests             # benchmarks only
make build CONFIG=freechips.rocketchip.system.Inten1RowConfig   # another config
```

Each test writes `emulator/output/<test>.riscv.out`, ending with
`*** PASSED *** Completed after N cycles`.

**4. Run the larger benchmark suite** (six real-program workloads; every hart
runs its own copy, and each result is checked against a reference):

```sh
make -C emulator run-spec95-equivalent-tests SPEC95EQ_WORKLOAD=extra_tiny \
     CONFIG=freechips.rocketchip.system.Inten1RowConfig
```

Each run's verdict, with IPC/CPI and wall-clock time, is written to
`emulator/output/spec95-equivalent/<test>.verdict`.
[`riscv-tests/spec95-equivalent/README.md`](riscv-tests/spec95-equivalent/README.md)
covers workload sizes and runtimes.

**5. Debug with waveforms:**

```sh
make debug && make -C emulator output/median.riscv.vcd   # open with gtkwave
```

| Config | Harts | Use |
|---|---|---|
| `Inten1RowConfig` | 1 | fastest to build and run; smoke tests |
| `Inten1CoreConfig` (default) | 16 | one core: 2 pipelines × 8 contexts |
| `Inten2CoreConfig` | 32 | two cores |
| `Inten4CoreConfig` | 64 | four cores |

Add `DRAMSIM3=0` to any target to use a fixed-latency memory model instead of
DRAMSim3. It gives the same results, faster, but without realistic memory timing.

## Repository layout

```
src/main/scala/        RTL (Chisel): core, caches, multi-context front end, configs (system/Configs.scala)
emulator/              Verilator build and test targets; results in emulator/output/
riscv-tests/           ISA tests, benchmarks, and spec95-equivalent/ (the rate-mode benchmark suite)
benchmarks/            the same benchmark suite as a standalone build for spike / QEMU
DRAMSIM3/              DRAM timing model
chisel3/ firrtl/ hardfloat/ ...   toolchain dependencies (vendored or submodules)
vsim/ regression/ torture/        VCS flow, rocket-chip regression, cache/AMO stress tests
```

## License

This repository is mixed-license:
- Intensivate's own core files (21 as of this release; [`NOTICE.md`](NOTICE.md)
  lists them) carry `LicenseRef-Intensivate-NC-1.0`, the Intensivate
  Non-Commercial Hardware Source License. It is source-available and is not an
  OSI-approved open-source license.
- The rest of the core keeps its upstream licenses.
- Intensivate's benchmark work is BSD-2-Clause.
- The benchmark suites vendor third-party programs under their own licenses,
  including GPL. BSMBench also carries a citation requirement that applies to
  published numbers.

See [`LICENSE.md`](LICENSE.md), [`CORE-LICENSE-AUDIT.md`](CORE-LICENSE-AUDIT.md)
and [`benchmarks/NOTICE.md`](benchmarks/NOTICE.md). The core is covered by
patents; see [`PATENTS.md`](PATENTS.md). Commercial use requires a separate
license ([`COMMERCIAL-LICENSE.md`](COMMERCIAL-LICENSE.md)); contact
**info@intensivate.com**.
