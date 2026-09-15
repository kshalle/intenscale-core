/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Cycle/instruction accounting for a benchmark run.
 *
 * Wrap the region of interest so that setup -- reading inputs, which is
 * host-speed and not part of the workload -- is excluded from the numbers:
 *
 *   bm_stats_start();
 *   ... workload ...
 *   bm_stats_stop("xlisp:boyer");
 *
 * Two environments are supported and every reported line says which one it
 * was measured in, because the numbers are not equivalent:
 *
 *   baremetal  mcycle/minstret read directly in M-mode.  Exact.
 *   linux      rdcycle/rdinstret if the kernel permits them from user mode
 *              AND they pass a calibration check, otherwise CPU time only.
 *              Shared machine, so noisier.
 */
#ifndef BM_STATS_H
#define BM_STATS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void bm_stats_start(void);
void bm_stats_stop(const char *label);

uint64_t bm_cycle(void);
uint64_t bm_instret(void);

/* "baremetal" or "linux" -- what the [bm] lines report as env=. */
const char *bm_env(void);

/* Nonzero if hardware counters were readable AND verified to be counting
 * this program's instructions. */
int bm_counters_available(void);

/* Why, in one word, for the [bm] lines:
 *   hardware    counters read and verified against a known instruction count
 *   trapped     rdcycle/rdinstret raise SIGILL from user mode
 *   unrelated   they answer, but not about this program (qemu returns a
 *               host-clock value) -- see bm_stats.c
 *   unverifiable they answer, but the check could not be run (its 16 MiB
 *               buffer would not allocate), so they are not trusted
 * Bare metal is always "hardware". */
const char *bm_counters_status(void);


#ifdef __cplusplus
}
#endif

#endif /* BM_STATS_H */
