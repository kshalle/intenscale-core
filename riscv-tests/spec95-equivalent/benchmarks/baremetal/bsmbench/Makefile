# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Lattice gauge theory workload, bare metal: BSMBench, SU(2) Monte Carlo.
#
# Upstream is compiled unmodified from ../../lattice-bsmbench.  Upstream's
# own build (make.sh + Make/MkFlags) drives per-directory makefiles, generates
# suN*.h headers into its Include directory, and defaults to MPI; this port
# compiles the same sources directly, in the non-MPI configuration that
# upstream supports (NO_MPI=YES in make.sh), with the headers supplied from
# shim/ instead of generated into the vendored tree.
#
#   make                   build
#   make run               functional check on spike
#   make BM_ENV=linux run  the same, as a static Linux binary under qemu

.DEFAULT_GOAL := all

BM_NAME  = bsmbench
UPSTREAM = ../../lattice-bsmbench/BSMBench/BSMBench

BM_SRCS = \
  $(UPSTREAM)/Bench/bsmbench.c \
  $(UPSTREAM)/Error/error.c \
  $(UPSTREAM)/Geometry/communications.c \
  $(UPSTREAM)/Geometry/geometry_init.c \
  $(UPSTREAM)/Geometry/geometry_mpi_eo.c \
  $(UPSTREAM)/Inverters/cg_mshift.c \
  $(UPSTREAM)/Inverters/linear_algebra.c \
  $(UPSTREAM)/IO/logger.c \
  $(UPSTREAM)/IO/read_input.c \
  $(UPSTREAM)/Memory/field_alloc.c \
  $(UPSTREAM)/Memory/spinor_field_alloc.c \
  $(UPSTREAM)/Observables/operators.c \
  $(UPSTREAM)/Random/gauss.c \
  $(UPSTREAM)/Random/random_fields.c \
  $(UPSTREAM)/Random/random_suNg.c \
  $(UPSTREAM)/Update/Dphi.c \
  $(UPSTREAM)/Update/random_spinor_field.c \
  $(UPSTREAM)/Update/random_su2.c \
  $(UPSTREAM)/Update/update_Dphi.c \
  $(UPSTREAM)/Utils/boundary_conditions.c

# Upstream's MkFlags.proto, minus -DWITH_MPI.  NG = 2 is expressed by which
# suN*.h the shim pulls in, exactly as upstream's generated headers do.
BM_MACROS = -DANTIPERIODIC_BC_T -DUPDATE_EO -DNDEBUG \
            -DREPR_ADJOINT -DREPR_NAME=\"REPR_ADJOINT\"

# BSMBench sizes itself by the clock: each test doubles its iteration count
# until its own wall-clock measurement exceeds MIN_TIME, which upstream fixes
# at 400 seconds (it is an HPC benchmark, and the constant is not overridable
# from the command line).  On a bare-metal target "wall clock" is whatever the
# harness derives from mcycle, so the honest way to size a run is to tell the
# target what its clock rate is: BM_CPU_HZ.  400 simulated seconds then costs
# 400 * BM_CPU_HZ cycles per test.
#
# This is also why the FLOP/s figures BSMBench prints are meaningless here --
# read the harness's "[bm] ... cycles=" line instead.
SIZE ?= small
# Measured on spike: tiny ~16 M cycles, small ~80 M, ref ~700 M.  Each size
# pairs a lattice (in the input file) with the clock rate the target believes
# it has, because the lattice sets the fixed cost of the precision test and
# the clock sets how long the Dphi test runs.
# BM_VTIME_STEP is the Linux build's equivalent: seconds per clock reading,
# chosen so that build does the same number of doubling rounds -- and so the
# same work -- as the bare-metal run at this size.  See bm_vtime.c.
ifeq ($(SIZE),tiny)
  BM_CPU_HZ     = 10000
  BM_VTIME_STEP = 200
else ifeq ($(SIZE),small)
  BM_CPU_HZ     = 100000
  BM_VTIME_STEP = 100
else ifeq ($(SIZE),ref)
  BM_CPU_HZ     = 1000000
  BM_VTIME_STEP = 100
else
  $(error SIZE must be one of tiny, small, ref)
endif

# The Linux build has a real clock, so BM_CPU_HZ has to be imposed on it: see
# bm_vtime.c, which replaces the time() BSMBench sizes its runs with.  Bare
# metal needs no wrapper -- the harness already derives time() from mcycle.
BM_SRCS_LINUX    = bm_vtime.c
BM_CFLAGS_LINUX  = -DBM_VTIME_STEP=$(BM_VTIME_STEP)
BM_LDFLAGS_LINUX = -Wl,--wrap=time

BM_CONFIG_TAG = $(SIZE)-hz$(BM_CPU_HZ)-vt$(BM_VTIME_STEP)
INPUT = workload/bsmbench-$(SIZE).input

# -I- so shim/suN*.h win over any copies a previous upstream build left in
# the vendored Include directory (a quoted include searches the including
# file's own directory first, which is that same directory).
BM_CFLAGS = -std=gnu99 -I shim -I- -I $(UPSTREAM)/Include $(BM_MACROS) \
            -DBM_CPU_HZ=$(BM_CPU_HZ)

include ../common/bm.mk

all: $(BM_OUT)

# How to drive this port; `make run` uses it for whichever
# environment the build selected (spike for baremetal, qemu for linux).
BM_RUN_ARGS = -i $(INPUT) -o /dev/stdout
