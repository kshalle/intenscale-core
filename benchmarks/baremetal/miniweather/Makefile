# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Atmospheric dynamics workload, bare metal: miniWeather (serial).
#
# The upstream source is compiled unmodified from ../../weather-miniweather.
# Everything this port needs lives in shim/ (single-rank MPI, pnetcdf
# declarations) plus the HTIF/newlib harness in ../common.
#
#   make            build the default (small) size
#   make SIZE=tiny  smallest useful problem, for a first RTL run
#   make SIZE=ref   upstream's own problem size (host/spike only)
#   make run               functional check on spike
#   make BM_ENV=linux run  the same, as a static Linux binary under qemu

BM_NAME = miniweather

UPSTREAM_DIR = ../../weather-miniweather/miniWeather/c
BM_SRCS      = $(UPSTREAM_DIR)/miniWeather_serial.cpp

# C++ port: link with g++ so libstdc++ comes in (std::cout, std::chrono).
BM_CXX = 1

SIZE ?= small

ifeq ($(SIZE),tiny)
  NX = 20
  NZ = 10
  SIM_TIME = 5
else ifeq ($(SIZE),small)
  NX = 40
  NZ = 20
  SIM_TIME = 20
else ifeq ($(SIZE),ref)
  NX = 100
  NZ = 50
  SIM_TIME = 1000
else
  $(error SIZE must be one of tiny, small, ref)
endif

# _OUT_FREQ = -1 disables every call to output(), which is the only pnetcdf
# user.  NO_INFORM drops the per-timestep printf: over HTIF each line is a
# host round trip and would dominate an RTL run.
BM_CFLAGS = -I shim \
            -D_NX=$(NX) -D_NZ=$(NZ) -D_SIM_TIME=$(SIM_TIME) \
            -D_OUT_FREQ=-1 -D_DATA_SPEC=DATA_SPEC_THERMAL \
            -DNO_INFORM -DBM_SIZE_NAME='"$(SIZE)"'

BM_CONFIG_TAG = $(SIZE)-$(NX)x$(NZ)-$(SIM_TIME)

include ../common/bm.mk

# How to drive this port; `make run` uses it for whichever
# environment the build selected (spike for baremetal, qemu for linux).
BM_RUN_ARGS = 
