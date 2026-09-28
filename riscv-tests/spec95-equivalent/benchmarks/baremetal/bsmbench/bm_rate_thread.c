/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* "Rate" mode entry point: every hart runs bsmbench's own main() on its own
 * heap partition (common/bm_rate_heap.c). Workload path is a compile-time
 * constant (BM_WORKLOAD_PATH, same convention as rowcore's BM_ARGV1..4) --
 * bsmbench needs no runtime argv beyond that, so it's hardcoded here rather
 * than routed through getmainvars(), which vanilla thread_entry() only
 * calls for hart 0. BSMBench writes its whole report through its logger to
 * the -o file, which fesvr opens host-side; each hart gets its own
 * (bsmbench-hartNN.log in the emulator's working directory), as each copy of
 * a rate run would, so the reports can be checked copy by copy. */

#include <stdio.h>

#ifndef BM_WORKLOAD_PATH
#error "BM_WORKLOAD_PATH must be defined at compile time"
#endif

int main(int argc, char *argv[]);

int thread_entry(int cid, int nc)
{
  (void)nc;
  char out[32];     /* per-hart: on this hart's own stack */
  snprintf(out, sizeof out, "bsmbench-hart%02d.log", cid);
  char *argv[] = { "bsmbench", "-i", BM_WORKLOAD_PATH, "-o", out, 0 };
  return main(5, argv);
}
