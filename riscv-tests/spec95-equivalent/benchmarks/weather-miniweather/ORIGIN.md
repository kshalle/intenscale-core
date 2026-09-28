# weather-miniweather — miniWeather

The atmospheric-dynamics workload: a dense double-precision stencil over a
compressible flow field.

## What is vendored here

`miniWeather/` — a compact atmospheric-dynamics mini-app (Matt Norman, ORNL):
dry compressible, stratified, non-hydrostatic flow with temperature, wind and
tracer transport.  C, C++ and Fortran variants are all present, which is
convenient for compiler and core studies.

- URL: https://github.com/mrnorman/miniWeather
- Commit: `b001069e1f7654914744641013e2bd408a9206fb` (2025-08-11)
- Retrieved: 2026-09-07

## Licence

**BSD-2-Clause**, text in `miniWeather/LICENSE`.  Copyright (c) 2018 National
Center for Computational Sciences, Oak Ridge National Laboratory; (c) 2021
NVIDIA Corporation.  Obligation: retain the notice.  No citation clause.

## Why this implementation

A finite-volume dynamical core gives a dense, regular double-precision stencil
workload with a built-in correctness check — the model conserves mass and
total energy, and reports both, so a port can be validated numerically rather
than by inspection.

Real models were considered and rejected on size alone: WRF (public domain)
and MPAS (BSD-3) are orders of magnitude too large for RTL simulation.

## What this workload does not cover

miniWeather is finite-volume, so it exercises no FFT.  A spectral or
pseudospectral workload would be a genuinely different profile, and this suite
does not currently have one.

DFFTPACK was briefly vendored here as the basis for a pseudospectral driver
and has been **removed**: nothing in the suite compiled it, and its licence
rested on a single sentence in a README ("The original FFTPACK was public
domain, so dfftpack is public domain too") with no licence file — not a
footing worth carrying into a public release for unused code.  If that driver
is ever written, take FFTPACK from a source with clear terms; scipy vendors it
under BSD-3-Clause.

## Local modifications

`.git/`, `.github/` and CVS metadata removed.

Two upstream documentation binaries were also removed, to keep the release
small.  They are an OpenACC tutorial deck, not miniWeather code, and nothing
in this suite builds or reads them:

    documentation/intro_to_openacc.pptx   3.3 MiB
    documentation/intro_to_openacc.pdf    2.6 MiB

BSD-2-Clause imposes no obligation to state changes; this is recorded for
accuracy, not to satisfy the licence.  `documentation/images/` and the
remaining text documentation are untouched.  `petascale_institute.md` links
to the presentation by upstream URL, so that link still resolves.  SHA-256,
to restore them from upstream:

    8095a2e8d52908c6214cbc09e0490d00c9569687fc68f3e27eccb2eee111135a  intro_to_openacc.pptx
    e5cf9ad03b3aafd7f104db762656f2db3ca9993d830f64119fc54018d06c5026  intro_to_openacc.pdf

All miniWeather **sources** are upstream and unmodified.

## Notes for benchmark use

The bare-metal port is in `../baremetal/miniweather`, and compiles the
upstream serial source byte-for-byte unmodified — everything it needs is
supplied by shim headers alongside it.
