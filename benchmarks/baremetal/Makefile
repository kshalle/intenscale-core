# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Benchmark suite for the RISC-V target.
#
# Every port builds for two environments, selected with BM_ENV; the output
# binary and every [bm] line it prints are tagged with it, so a result can
# never be mistaken for the other environment.
#
#   baremetal  (default)  no OS, M-mode from reset, newlib over HTIF.
#                         Output: <port>/<port>.riscv, run on spike or RTL.
#   linux                 static Linux binary, no shared-library
#                         dependencies.  Output: <port>/<port>-linux.riscv,
#                         run under qemu-riscv64 or on a RISC-V host.
#
#   make check-toolchain            verify the toolchain for one environment
#   make all                        build every port, bare metal
#   make BM_ENV=linux all           build every port for Linux
#   make both                       build every port for both environments
#   make run                        build and run every port (spike)
#   make BM_ENV=linux run           build and run every port (qemu)
#   make clean
#
# SIZE=tiny|small|ref selects the problem size and passes through to the
# ports; see README.md for the harness design and PORT-STATUS.md for status.

PORTS = selftest miniweather xlisp perl-perl4 bsmbench \
        libcint gcc-cc1

BM_ENV ?= baremetal

.PHONY: all both run run-spike run-qemu clean check-toolchain $(PORTS)

all: $(PORTS)

$(PORTS):
	$(MAKE) -C $@ BM_ENV=$(BM_ENV)

both:
	$(MAKE) BM_ENV=baremetal all
	$(MAKE) BM_ENV=linux all

run:
	@for p in $(PORTS); do \
	  echo "=== $$p ($(BM_ENV)) ==="; \
	  $(MAKE) -s -C $$p BM_ENV=$(BM_ENV) run || echo "FAILED: $$p"; \
	done

# Named aliases, for when the environment matters more than the default.
run-spike:
	@$(MAKE) --no-print-directory BM_ENV=baremetal run

run-qemu:
	@$(MAKE) --no-print-directory BM_ENV=linux run

check-toolchain:
	@$(MAKE) --no-print-directory -C selftest BM_ENV=$(BM_ENV) check-toolchain

clean:
	@for p in $(PORTS); do $(MAKE) -s -C $$p clean; done
	rm -rf build
