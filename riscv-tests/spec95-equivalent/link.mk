# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause
#
# Final link of one spec95-equivalent rate image through the stock riscv-tests rules
# (Makefrag-baremetal's compile_template, common/crt.S + syscalls.c +
# test.ld), exactly as the riscv-tests benchmarks are linked. Invoked by
# scripts/build.sh, from build/, with NAME (the dispatcher's directory under
# build/) and RT (riscv-tests) set; the program itself comes in through
# RISCV_LINK_OPTS. Not meant to be run by hand.
tests = $(NAME)
include $(RT)/Makefrag-baremetal
