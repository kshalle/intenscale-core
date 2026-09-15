/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* HTIF (host-target interface) syscall proxy for bare-metal RISC-V.
 *
 * The frontend server (fesvr) built into spike and the rocket-chip Verilator
 * emulator services a small set of Linux syscalls on behalf of a bare-metal
 * target.  This header exposes the raw trampoline; htif_syscalls.c layers the
 * newlib syscall glue (_open/_read/_write/...) on top of it so that ordinary
 * stdio, malloc and libm work with no OS on the target.
 *
 * Protocol (see fesvr/syscall.cc, fesvr/htif.cc in riscv-isa-sim):
 *   magic_mem[0] = syscall number, magic_mem[1..7] = args
 *   tohost   = &magic_mem   (low 48 bits; the upper bits select device/cmd 0)
 *   spin until fromhost != 0, then ack by writing fromhost = 0
 *   magic_mem[0] holds the return value, negative values are -errno
 */
#ifndef BM_HTIF_H
#define BM_HTIF_H

#include <stdint.h>

#define BM_SYS_getcwd    17
#define BM_SYS_fcntl     25
#define BM_SYS_unlinkat  35
#define BM_SYS_renameat  38
#define BM_SYS_faccessat 48
#define BM_SYS_openat    56
#define BM_SYS_close     57
#define BM_SYS_lseek     62
#define BM_SYS_read      63
#define BM_SYS_write     64
#define BM_SYS_pread     67
#define BM_SYS_pwrite    68
#define BM_SYS_fstatat   79
#define BM_SYS_fstat     80
#define BM_SYS_exit      93
#define BM_SYS_lstat   1039
#define BM_SYS_getmainvars 2011

/* fesvr's AT_FDCWD sentinel (fesvr/syscall.cc: RISCV_AT_FDCWD). */
#define BM_AT_FDCWD (-100)

#ifdef __cplusplus
extern "C" {
#endif

long bm_htif_syscall(long which, long a0, long a1, long a2,
                     long a3, long a4, long a5, long a6);

#ifdef __cplusplus
}
#endif

#endif /* BM_HTIF_H */
