/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* "Rate" mode entry point: every hart runs the benchmark's own main(), not
 * just hart 0 (see common/bm_rate_heap.c for the per-hart heap partition
 * this relies on). libcint-driver.c's main() takes no arguments. */

int main(void);

int thread_entry(int cid, int nc)
{
  (void)cid; (void)nc;
  return main();
}
