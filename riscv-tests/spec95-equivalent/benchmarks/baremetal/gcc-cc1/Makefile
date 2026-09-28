# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# C compiler workload, bare metal: gcc 2.5.8's cc1 on the RISC-V target.
#
# Only cc1 is built -- the compiler proper, no driver, no preprocessor, no
# assembler -- reading preprocessed .i files and emitting SPARC assembly.  The
# target is sparc (config/sparc/sparc.h, sparc.md), the host (the machine cc1
# runs on) is this bare-metal riscv64 image, and the build machine runs the
# generator programs.  SPARC is chosen as a well-supported 1994 back end; the
# emitted code is never executed, only the compiler's own work is measured.
#
#   ./mkstage.py    rewrite the pre-ANSI variadic definitions into gen/src
#   ./mkgen.sh      build and run the generators -> gen/insn-*.[ch]
#   make            build cc1 for the target
#   make run        compile a .i file on spike
#   make BM_ENV=linux run   the same, as a static Linux binary under qemu
#
# Both scripts are prerequisites below, so plain `make` does all three.

.DEFAULT_GOAL := all

BM_NAME  = gcc-cc1
UPSTREAM = ../../compiler-gcc/gcc-2.5.8

# Upstream's OBJS, C_OBJS and BC_OBJS from Makefile.in, minus the generated
# insn-* files (built into gen/) and aux-output.c (which configure links to
# the target's config/sparc/sparc.c).
GCC_OBJS = toplev version tree print-tree stor-layout fold-const \
           function stmt expr calls expmed explow optabs varasm \
           rtl print-rtl rtlanal emit-rtl real \
           dbxout sdbout dwarfout xcoffout \
           integrate jump cse loop unroll flow stupid combine \
           regclass local-alloc global reload reload1 caller-save \
           reorg sched final recog reg-stack \
           getpwd convert obstack
GCC_C_OBJS = c-parse c-lang c-lex c-pragma c-decl c-typeck c-convert \
             c-aux-info c-common c-iterate
GCC_BC_OBJS = bc-emit bc-optab

GEN_SRCS = gen/insn-emit.c gen/insn-recog.c gen/insn-opinit.c \
           gen/insn-extract.c gen/insn-peep.c gen/insn-attrtab.c \
           gen/insn-output.c

# A staged copy wins over the upstream original (see mkstage.py).
pick = $(if $(wildcard gen/src/$(1).c),gen/src/$(1).c,$(UPSTREAM)/$(1).c)

BM_SRCS = $(foreach f,$(GCC_OBJS) $(GCC_C_OBJS) $(GCC_BC_OBJS),$(call pick,$(f))) \
          $(GEN_SRCS) $(UPSTREAM)/config/sparc/sparc.c

# -I- for the same reason as the perl port: gen/src and hostcfg must win over
# the upstream copies of the same file names, and a quoted include otherwise
# searches the including file's own directory first.
# -fcommon: gcc 2.5.8 declares shared globals as tentative definitions in
# several files at once (word_mode, class_narrowest_mode, local_vars_size),
# which was fine when every C compiler merged them into common symbols.  The
# harness builds with -fno-common, so this puts the old behaviour back for
# this port only.  Comes after the harness flags, so it wins.
BM_CFLAGS = -std=gnu89 -w -fcommon -I hostcfg -I gen/src -I gen -I- \
            -I $(UPSTREAM) -I $(UPSTREAM)/config \
            -DIN_GCC

# Cancel make's built-in .y -> .c rule.  Upstream ships the generated
# c-parse.c, current bison rejects its 1994 grammar ("$$ for the midrule has
# no declared type"), and with `make -B` the implicit rule would try anyway --
# writing into the vendored tree, which nothing here is allowed to do.
%.c: %.y

include ../common/bm.mk

gen/insn-attrtab.c gen/insn-output.c: mkgen.sh mkstage.py
	python3 mkstage.py $(UPSTREAM) gen/src
	./mkgen.sh $(UPSTREAM) gen

$(BM_OUT): gen/insn-attrtab.c gen/insn-output.c

all: $(BM_OUT)

# Input size is the number of function groups in the generated translation
# unit; cost scales linearly with it.  Measured on spike:
#   tiny   2 groups   121 lines    13 M instructions
#   small  8 groups   463 lines    51 M
#   ref   64 groups  3655 lines   407 M
SIZE ?= small
ifeq ($(SIZE),tiny)
  GROUPS = 2
else ifeq ($(SIZE),small)
  GROUPS = 8
else ifeq ($(SIZE),ref)
  GROUPS = 64
else
  $(error SIZE must be one of tiny, small, ref)
endif

workload/input-$(GROUPS).i: workload/mkinput.py
	python3 $< $(GROUPS) > $@

all: workload/input-$(GROUPS).i

# How to drive this port; `make run` uses it for whichever
# environment the build selected (spike for baremetal, qemu for linux).
BM_RUN_ARGS = workload/input-$(GROUPS).i -quiet -O -o $(BM_NAME)-out.s
