# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Common build rules for the benchmark ports, in both environments.
#
# Include from a per-benchmark Makefile after setting:
#   BM_NAME    - output binary base name (e.g. xlisp)
#   BM_SRCS    - benchmark sources, built for both environments
#   BM_CFLAGS  - extra flags (include paths, -D...) for both environments
#   BM_LDLIBS  - extra libraries (e.g. -lm)
#   BM_CXX     - set to 1 if any source is C++, so the link uses g++
#
# A port whose needs differ between the two environments (see BM_ENV below)
# adds to the -BM / -LINUX variants instead of branching on BM_ENV: they are
# appended to the lists above for the matching environment only.
#   BM_SRCS_BM      BM_SRCS_LINUX
#   BM_CFLAGS_BM    BM_CFLAGS_LINUX
#   BM_LDFLAGS_BM   BM_LDFLAGS_LINUX
# This keeps the port's variables order-independent: BM_ENV is defined here,
# after the port has already set its own variables.
#
# Produces $(BM_OUT) -- $(BM_NAME).riscv bare metal, $(BM_NAME)-linux.riscv
# under Linux -- runnable under spike, qemu, or the rocket-chip Verilator
# emulator as appropriate.

BM_COMMON_DIR := $(dir $(lastword $(MAKEFILE_LIST)))

# Machine-local settings -- where the two cross toolchains, spike, qemu and
# the Verilator emulator live on this particular machine.  Optional: with the
# tools on PATH nothing here is needed.  Copy bm-site.mk.example to
# bm-site.mk and edit it; the file is not tracked, so it never carries one
# developer's paths into someone else's checkout.
-include $(BM_COMMON_DIR)bm-site.mk

# ------------------------------------------------------------------ environment
# BM_ENV selects what the benchmark runs on.  Both builds compile the same
# benchmark sources and report the same [bm] lines, tagged with env= so a
# result can never be mistaken for the other environment.
#
#   baremetal  (default)  no OS: newlib + the HTIF frontend server, M-mode
#                         from reset, exact mcycle/minstret.
#   linux                 static Linux binary, no shared-library dependencies.
#                         Counters come from rdcycle/rdinstret if the kernel
#                         allows them AND they pass the calibration check in
#                         bm_stats.c, otherwise CPU time only.
BM_ENV ?= baremetal
ifeq ($(filter $(BM_ENV),baremetal linux),)
  $(error BM_ENV must be baremetal or linux)
endif

# ---------------------------------------------------------------- toolchain
# One cross toolchain per environment.  Bare metal needs a
# riscv64-unknown-elf gcc whose newlib ships the HTIF BSP (libgloss_htif.a +
# htif.specs): riscv-tools/rocket-tools builds one, and the distribution
# gcc-riscv64-unknown-elf package will not do -- it ships no newlib at all.
# Linux needs a riscv64-unknown-linux-gnu gcc that can link -static.
# `make check-toolchain` verifies whichever one BM_ENV selects.
ifeq ($(BM_ENV),linux)
  # A Linux cross toolchain with static libc.  Override RISCV_BM_PREFIX for
  # another one; it must be able to link -static.
  RISCV_BM_PREFIX ?= riscv64-unknown-linux-gnu-
  # RISCV_LINUX_BIN, if set (usually in bm-site.mk), is the directory holding
  # that toolchain; empty means "find it on PATH".
  RISCV_BM_DIR := $(if $(RISCV_LINUX_BIN),$(RISCV_LINUX_BIN)/,)
else
  RISCV_BM_PREFIX ?= riscv64-unknown-elf-
  # Likewise RISCV_ELF_BIN for the newlib/HTIF toolchain.
  RISCV_BM_DIR := $(if $(RISCV_ELF_BIN),$(RISCV_ELF_BIN)/,)
endif

RISCV_BM_GCC    ?= $(RISCV_BM_DIR)$(RISCV_BM_PREFIX)gcc
RISCV_BM_GXX    ?= $(RISCV_BM_DIR)$(RISCV_BM_PREFIX)g++
RISCV_BM_OBJDUMP?= $(RISCV_BM_DIR)$(RISCV_BM_PREFIX)objdump

# A C++ port links with g++ so the standard library comes in; the compiler
# driver is the same in either environment, only the prefix differs.
ifeq ($(BM_CXX),1)
  RISCV_BM_LD ?= $(RISCV_BM_GXX)
else
  RISCV_BM_LD ?= $(RISCV_BM_GCC)
endif

# The riscv-tools gcc 9.2.0 binaries link against MPFR 3.x (libmpfr.so.4),
# which current distributions no longer ship.  If it is missing, look for a
# copy in the Ubuntu core snap rather than vendoring a binary into this repo.
# Override BM_MPFR4 to point at a libmpfr.so.4 elsewhere on the machine.
# Only the bare-metal toolchain needs it; the Linux cross gcc is newer.
ifeq ($(BM_ENV),baremetal)
BM_MPFR4 ?= $(firstword $(wildcard /snap/core*/*/usr/lib/x86_64-linux-gnu/libmpfr.so.4) \
                        $(wildcard /snap/core*/usr/lib/x86_64-linux-gnu/libmpfr.so.4) \
                        $(wildcard /usr/lib/x86_64-linux-gnu/libmpfr.so.4))
ifneq ($(BM_MPFR4),)
  BM_COMPAT_DIR := $(abspath $(BM_COMMON_DIR)../build/compat)
  export LD_LIBRARY_PATH := $(BM_COMPAT_DIR):$(LD_LIBRARY_PATH)
endif
endif

# Stack.  htif.ld sizes each hart's stack from __stack_size_min, which it
# PROVIDEs as 24 KiB -- far too little for an interpreter or a compiler doing
# deep recursion, and it overflows silently into whatever is below it.  A
# --defsym definition overrides the PROVIDE.  The heap (see _sbrk in
# htif_syscalls.c) starts above the stacks, so BM_STACK_RESERVE must stay at
# least as large as the total stack allocation.
BM_STACK_BYTES  ?= 1048576
BM_STACK_RESERVE ?= $(shell expr $(BM_STACK_BYTES) \* 4)

# BM_RTL=1 tunes a build for RTL simulation rather than spike.  The frontend
# server is reached through the debug module on real hardware, and an HTIF
# round trip costs about 151 k cycles there -- measured on the rocket
# emulator, where a program doing one write and exiting takes 1.68 M cycles
# and one doing five takes 2.29 M.  Buffering the console turns one round trip
# per line into one per 8 KiB, at the cost of not seeing output until the
# buffer fills or the run ends.  See also BM_FILE_BLKSIZE in htif_syscalls.c,
# which does the same for workload input files and is on by default.
BM_RTL ?= 0
ifeq ($(BM_RTL),1)
  BM_RTL_CFLAGS = -DBM_CONSOLE_BUFFERED -DBM_CONSOLE_BUFSIZE=8192
endif

BM_ARCH   ?= rv64imafdc
BM_ABI    ?= lp64d
BM_OPT    ?= -O2

# htif.specs      - newlib + libgloss_htif + htif.ld, no host startfiles
# htif_argv.specs - fetch argv from the frontend server, so the benchmark can
#                   be invoked as "<binary> input-file ..." exactly like on a host
BM_SPECS  ?= -specs=htif.specs -specs=htif_argv.specs

# Wrap the benchmark's main() so cycles/instructions are reported without
# editing vendored source.  Set BM_WRAP_MAIN = 0 for a port that provides its
# own driver and calls bm_stats_start()/bm_stats_stop() itself.
BM_WRAP_MAIN ?= 1
ifeq ($(BM_WRAP_MAIN),1)
  BM_WRAP_CFLAGS  = -DBM_LABEL='"$(BM_NAME)"' -Wl,--wrap=main
  BM_WRAP_SRCS    = $(BM_COMMON_DIR)bm_main.c
endif

ifeq ($(BM_ENV),linux)
  # No HTIF, no stack/heap games, no newlib specs: the kernel provides all of
  # it.  -static is the "little or no library dependence" requirement -- the
  # result has no shared-library dependencies at all.
  BM_ENV_CFLAGS = -DBM_LINUX -DBM_ISA_STR='"$(BM_ARCH)"' -static \
                  $(BM_CFLAGS_LINUX) $(BM_LDFLAGS_LINUX)
  # Only the statistics reporter: the kernel supplies everything the
  # bare-metal files stand in for.
  BM_ENV_SRCS   = $(BM_COMMON_DIR)bm_stats.c
  BM_PORT_SRCS  = $(BM_SRCS_LINUX)
  BM_SPECS      =
  BM_OUT        = $(BM_NAME)-linux.riscv
else
  BM_ENV_CFLAGS = -DBM_ISA_STR='"$(BM_ARCH)"' \
                  -DBM_STACK_RESERVE=$(BM_STACK_RESERVE) $(BM_RTL_CFLAGS) \
                  -Wl,--defsym=__stack_size_min=$(BM_STACK_BYTES) \
                  $(BM_CFLAGS_BM) $(BM_LDFLAGS_BM)
  BM_ENV_SRCS   = $(BM_COMMON_DIR)htif_syscalls.c $(BM_COMMON_DIR)bm_posix_stubs.c \
                  $(BM_COMMON_DIR)bm_stats.c $(BM_COMMON_DIR)bm_cxx.c
  BM_PORT_SRCS  = $(BM_SRCS_BM)
  BM_OUT        = $(BM_NAME).riscv
endif

BM_ALL_CFLAGS = -march=$(BM_ARCH) -mabi=$(BM_ABI) -mcmodel=medany $(BM_OPT) \
                -fno-common -I$(BM_COMMON_DIR) $(BM_ENV_CFLAGS) \
                $(BM_WRAP_CFLAGS) $(BM_CFLAGS)

# Ports whose problem size is compiled in set BM_CONFIG_TAG (to $(SIZE), say).
# The stamp makes the binary depend on that choice, so switching size relinks
# instead of silently running the previously built one.
ifneq ($(BM_CONFIG_TAG),)
  BM_STAMP = .bm-config-$(BM_CONFIG_TAG)
$(BM_STAMP):
	@rm -f .bm-config-*
	@touch $@
endif

# htif_syscalls.o must precede -lgloss_htif so that its real file-I/O
# definitions win over the BSP's ENOENT stubs.
$(BM_OUT): $(BM_SRCS) $(BM_PORT_SRCS) $(BM_ENV_SRCS) $(BM_WRAP_SRCS) \
           $(BM_COMMON_DIR)bm.mk $(BM_STAMP) | bm-compat
	$(RISCV_BM_LD) $(BM_ALL_CFLAGS) $(BM_SPECS) \
	    -x c $(BM_ENV_SRCS) $(BM_WRAP_SRCS) -x none \
	    $(BM_SRCS) $(BM_PORT_SRCS) $(BM_LDLIBS) -lm -o $@

$(BM_OUT).dump: $(BM_OUT)
	$(RISCV_BM_OBJDUMP) -D $< > $@

# ------------------------------------------------------------------- running
# A port sets BM_RUN_ARGS (target arguments) and optionally BM_RUN_STDIN (a
# file to redirect onto stdin).  `make run` then does the right thing for the
# environment the port was built for, so the two builds are driven the same
# way.
QEMU  ?= qemu-riscv64
SPIKE ?= spike --isa=$(BM_ARCH)

BM_REDIR = $(if $(BM_RUN_STDIN),< $(BM_RUN_STDIN),)

# BM_RUN is the command that runs this port's binary in the selected
# environment.  A port with more than one workload uses it directly for the
# extra ones (see perl-perl4's run-anagram).
ifeq ($(BM_ENV),linux)
  BM_RUN = $(QEMU) ./$(BM_OUT)
else
  BM_RUN = $(SPIKE) $(BM_OUT)
endif

# Every port defines `all` (the default goal), so `run` picks up whatever
# workload files the port generates alongside the binary.
.DEFAULT_GOAL := all
.PHONY: all run run-spike run-qemu
all: $(BM_OUT)

run: all
	$(BM_RUN) $(BM_RUN_ARGS) $(BM_REDIR)

ifeq ($(BM_ENV),linux)
run-qemu: run
run-spike:
	@echo "run-spike needs the bare-metal build: make BM_ENV=baremetal run"; exit 1
else
run-spike: run
run-qemu:
	@echo "run-qemu needs the Linux build: make BM_ENV=linux run"; exit 1
endif

.PHONY: bm-compat check-toolchain clean
bm-compat:
ifneq ($(BM_MPFR4),)
	@mkdir -p $(BM_COMPAT_DIR)
	@test -e $(BM_COMPAT_DIR)/libmpfr.so.4 || ln -sf $(BM_MPFR4) $(BM_COMPAT_DIR)/libmpfr.so.4
endif

check-toolchain:
	@echo "    BM_ENV=$(BM_ENV), linking with $(RISCV_BM_LD)"
ifeq ($(BM_ENV),linux)
	@$(RISCV_BM_GCC) -print-file-name=libc.a | grep -q / \
	  && echo "OK  static libc: $$($(RISCV_BM_GCC) -print-file-name=libc.a)" \
	  || { echo "FAIL $(RISCV_BM_GCC) has no static libc.a"; exit 1; }
else
	@$(RISCV_BM_GCC) -print-file-name=libgloss_htif.a | grep -q / \
	  && echo "OK  HTIF newlib BSP: $$($(RISCV_BM_GCC) -print-file-name=libgloss_htif.a)" \
	  || { echo "FAIL $(RISCV_BM_GCC) has no libgloss_htif.a (wrong toolchain)"; exit 1; }
endif

clean:
	rm -f $(BM_NAME).riscv $(BM_NAME)-linux.riscv *.riscv.dump *.o .bm-config-*
