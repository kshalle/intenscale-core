# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause
#
# spec95-equivalent bare-metal benchmarks, built for the riscv-tests runtime in
# "rate" mode (every hart runs the whole benchmark). See README.md.
#
#   make                                   all six, WORKLOAD=tiny, NHARTS=16
#   make xlisp WORKLOAD=extra_tiny NHARTS=1
#   make spike-check WORKLOAD=extra_tiny   functional check on spike (fast)
#   make reference WORKLOAD=extra_tiny     regenerate the native references
#   make clean
#
# WORKLOAD: extra_tiny | tiny | small | ref       (sizes: scripts/workloads.sh)
# NHARTS:   harts in the target RTL config        (Inten1RowConfig 1, Inten1CoreConfig 16)
# RATE_MODE: process (default) | copies           (see scripts/build.sh)
# Binaries: build/<port>-<WORKLOAD>-<NHARTS>.riscv
# Running them under the RTL emulator: make -C emulator run-spec95-equivalent-tests (README.md).

WORKLOAD ?= tiny
NHARTS   ?= 16
PORTS    := libcint miniweather perl-perl4 bsmbench xlisp gcc-cc1

all: $(PORTS)

$(PORTS):
	scripts/build.sh $@ $(WORKLOAD) $(NHARTS)

spike-check:
	@fail=0; for p in $(PORTS); do scripts/spike_check.sh $$p $(WORKLOAD) $(NHARTS) || fail=1; done; exit $$fail

reference:
	@for p in $(PORTS); do scripts/make_reference.sh $$p $(WORKLOAD) || exit 1; done

clean:
	rm -rf build

.PHONY: all $(PORTS) spike-check reference clean
