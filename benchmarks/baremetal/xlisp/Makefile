# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Lisp interpreter workload, bare metal: XLISP on the RISC-V target.
#
# Upstream XLISP-PLUS compiles for a bare-metal target essentially unchanged:
# 29 of its 30 translation units build as-is, and the thirtieth (its Linux OS
# layer) needs only the <sys/termios.h> that newlib's riscv64-unknown-elf
# target omits.  That header lives in shim/, and the terminal and process
# calls it declares are no-ops in bm_os_stubs.c.  No upstream file is edited.
#
#   make                       build with the default (small) workload
#   make run                   functional check on spike
#   make run SIZE=tiny         smallest workload, for RTL bring-up
#   make BM_ENV=linux run      the same, as a static Linux binary under qemu
#
# The workload arrives on stdin, which the frontend server proxies from the
# host:  xlisp -b < workload.lsp

.DEFAULT_GOAL := all

BM_NAME = xlisp

UPSTREAM = ../../interpreter-xlisp/xlisp-plus/sources

# The Linux build's object list (see $(UPSTREAM)/makelinux), plus our stubs.
BM_SRCS = \
  $(UPSTREAM)/xlisp.c   $(UPSTREAM)/xlbfun.c  $(UPSTREAM)/xlcont.c  \
  $(UPSTREAM)/xldbug.c  $(UPSTREAM)/dldmem.c  $(UPSTREAM)/xleval.c  \
  $(UPSTREAM)/xlfio.c   $(UPSTREAM)/xlglob.c  $(UPSTREAM)/dlimage.c \
  $(UPSTREAM)/xlinit.c  $(UPSTREAM)/xlio.c    $(UPSTREAM)/xljump.c  \
  $(UPSTREAM)/xllist.c  $(UPSTREAM)/xlmath.c  $(UPSTREAM)/xlobj.c   \
  $(UPSTREAM)/xlpp.c    $(UPSTREAM)/xlprin.c  $(UPSTREAM)/xlread.c  \
  $(UPSTREAM)/xlstr.c   $(UPSTREAM)/xlsubr.c  $(UPSTREAM)/xlsym.c   \
  $(UPSTREAM)/xlsys.c   $(UPSTREAM)/linuxstuff.c $(UPSTREAM)/unixprim.c \
  $(UPSTREAM)/xlseq.c   $(UPSTREAM)/xlstruct.c $(UPSTREAM)/xlftab.c \
  $(UPSTREAM)/xlmath2.c $(UPSTREAM)/xlmath3.c $(UPSTREAM)/xlbignum.c

# Bare metal only: newlib has no <sys/termios.h> (shim/) and no tcgetattr /
# tcsetattr (bm_os_stubs.c).  Under Linux both come from glibc, and shim/ must
# stay off the include path so it cannot shadow the real header.
BM_SRCS_BM   = bm_os_stubs.c
BM_CFLAGS_BM = -I shim -DSTSZ=$(BM_STACK_BYTES)

# The default Linux thread stack is 8 MB; leave XLISP a little margin under it.
BM_CFLAGS_LINUX = -DSTSZ=7340032

# STSZ tells XLISP how much C stack it may recurse into before it refuses; it
# must match the stack the environment actually gives it (see above), not the
# 8 MB the upstream Linux makefile assumes.  -DLINUX selects upstream's Linux
# OS layer in both cases: bare metal, the harness stands in for the kernel.
BM_CFLAGS = -DLINUX -I $(UPSTREAM) -w

# tiny  ~11 M instructions  -- RTL bring-up
# small ~50 M instructions  -- a routine RTL run
# ref   ~470 M instructions -- spike/host measurement
SIZE ?= small
WORKLOAD = workload/xlisp-bench-$(SIZE).lsp

include ../common/bm.mk

# XLISP loads init.lsp from the working directory at startup.  Stage the
# upstream copy next to the binary at build time rather than vendoring it.
init.lsp: ../../interpreter-xlisp/xlisp-plus/lsp/init.lsp
	cp $< $@

all: $(BM_OUT) init.lsp

# How to drive this port; `make run` uses it for whichever
# environment the build selected (spike for baremetal, qemu for linux).
BM_RUN_ARGS = -b
BM_RUN_STDIN = $(WORKLOAD)
