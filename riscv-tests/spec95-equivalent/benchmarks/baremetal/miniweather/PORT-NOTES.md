# miniweather -- the atmospheric dynamics workload, bare metal

Upstream: `../../weather-miniweather/miniWeather/c/miniWeather_serial.cpp`
(BSD-2).  **Not edited** -- this port compiles the upstream file byte for byte.

## What this port adds

| File | Why |
|---|---|
| `shim/mpi.h` | The "serial" upstream file still includes `<mpi.h>` and calls Init/Finalize/Barrier/Allreduce.  Single-rank definitions: the reduction is a copy, the barrier a no-op, rank 0 of 1. |
| `shim/pnetcdf.h` | Only `output()` uses pnetcdf, and the build sets `_OUT_FREQ = -1`, which makes upstream skip every call to it.  The declarations satisfy the compiler; each one aborts with a message if it is ever reached, so a build that accidentally enables output fails loudly instead of writing nothing. |

`NO_INFORM` drops the per-timestep progress `printf`: over HTIF each line is a
host round trip and would dominate an RTL run.

## Running

    make run-spike SIZE=tiny
    ../scripts/run-verilator.sh miniweather.riscv

## Verification

The upstream file builds natively against the same two shims, which makes a
direct numerical comparison easy.  At `SIZE=tiny`:

| | target (spike) | host (g++ -O2) |
|---|---|---|
| `d_mass` | -2.149078e-15 | -2.344449e-15 |
| `d_te` | -3.043449e-06 | -3.043449e-06 |

`d_te` agrees exactly; `d_mass` differs in the last bits, which is round-off
at the 1e-15 level from different floating-point contraction order, not a port
error.  Both are the conservation diagnostics the model is judged by.

## What this workload does not cover

miniWeather is finite-volume, so it exercises no FFT.  A spectral or
pseudospectral workload would be a genuinely different profile, and this suite
does not currently have one.
DFFTPACK was vendored for that reason and has since been removed -- nothing
compiled it, and its licence basis was too thin to ship (see
`../../weather-miniweather/ORIGIN.md`).  Recovering the FFT character means
writing a pseudospectral driver, and taking FFTPACK from a clearly licensed
source such as scipy's BSD-3 copy.
