# lattice-bsmbench — BSMBench 1.0

The lattice gauge theory workload: SU(2) Monte Carlo with Dirac operator
applications over a 4-D lattice.

## What is vendored here

`BSMBench/` — BSMBench 1.0, extracted from the HiRep lattice code by the
Swansea/Edinburgh/Southern Denmark lattice group and purpose-built as a
portable supercomputer benchmark.

- URL: https://gitlab.com/edbennett/BSMBench
  (archive: `/-/archive/master/BSMBench-master.tar.gz`)
- Tarball sha256: `abbff613a9b6ad973b9dc60e2cdc5663eb0e08a319f990d1b3ca7d1fa16619a5`
- Retrieved: 2026-09-07
- Paper: BSMBench, arXiv:1401.3733

## Licence — read this one

BSD-3-Clause **plus a mandatory citation clause**.  Full text in
`BSMBench/LICENSE`.  Copyright (c) 2012 Claudio Pica, Agostino Patella,
Antonio Rago, Luigi Del Debbio, Biagio Lucini, Edward Bennett; individual
sources also carry Copyright (c) 2008-2014 Claudio Pica and Agostino Patella
from the HiRep origins.

Standard BSD obligations (retain notice in source and binary redistributions,
no endorsement) **and** clause 3, which is an affirmative obligation on us:

> "Any publication in any form derived from the use of this software or any
> modification of it must refer explicitly to the original BSMBench package
> (including the official URL) and cite the following two publications:"
> [1] Del Debbio, Patella, Pica, Phys. Rev. D81 (2010) 094503
> [2] Del Debbio, Lucini, Patella, Pica, Rago, Phys. Rev. D80 (2009) 074507

Practical effect: any datasheet, whitepaper, blog post or conference paper
quoting numbers from this benchmark must name BSMBench, link its URL, and cite
both papers.  This is not OSI-standard BSD — flag it to whoever signs off on
marketing collateral.  Checked explicitly: there is **no GPL anywhere** in the
tree, which is worth knowing because HiRep-derived code sometimes is.

## Why this implementation

Lattice gauge theory is a distinct and useful workload class: regular,
vectorisable double-precision stencil work over a working set far larger than
cache, with a well-defined correctness check.  BSMBench additionally lets the
compute-versus-communication ratio be dialled from its input file, which is
useful for core-level studies, and it ships a correctness test of its own —
it inverts the Dirac operator with conjugate gradient and reports the
residual.

Legacy codes in this class are research software distributed without public
licences.  BSMBench exists precisely to be published, benchmarked and cited.

## Local modifications

None to source.  No version-control metadata was present in the archive.

## Notes for benchmark use

Upstream's build is driven by `make.sh <machine-config>` and defaults to MPI;
it also supports a non-MPI build (`NO_MPI=YES`), which is what the bare-metal
port in `../baremetal/bsmbench` uses.  Upstream's own input sets are sized for
supercomputers (the smallest is a 64x32x32x32 lattice); the port ships its own,
sized for simulation.
