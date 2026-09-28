/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* "Rate" mode entry point: every hart runs xlisp's own main() on its own
 * heap partition (common/bm_rate_heap.c). xlisp reads its workload from
 * stdin (xlisp -b < workload.lsp) -- if all 16 harts shared host fd 0 they
 * would race over one stream, each getting a garbled partial read. Instead,
 * each hart freopen()s the workload path independently before calling
 * main(), giving each its own full, independent read (same fix the old
 * rowcore harness used for the identical reason -- see its own port notes).
 */

#include <stdio.h>

#ifndef BM_WORKLOAD_PATH
#error "BM_WORKLOAD_PATH must be defined at compile time"
#endif

int main(int argc, char *argv[]);

int thread_entry(int cid, int nc)
{
  (void)cid; (void)nc;
  char *argv[] = { "xlisp", "-b", 0 };
  if (!freopen(BM_WORKLOAD_PATH, "r", stdin))
    return 1;
  return main(2, argv);
}
