# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

BM_NAME  = selftest
BM_SRCS  = selftest.c
include ../common/bm.mk

# How to drive this port; `make run` uses it for whichever
# environment the build selected (spike for baremetal, qemu for linux).
BM_RUN_ARGS = input.txt
