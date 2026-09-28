/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* "Rate" mode entry point: every hart runs miniWeather_serial's own main()
 * on its own heap partition (common/bm_rate_heap.c). No argv needed -- the
 * grid size/sim-time are compile-time -D macros (see ports/miniweather.sh),
 * and main() takes no meaningful arguments. extern "C" since this is linked
 * into a C++ program by the C++ driver (matches bm_main.c's own convention
 * for the same reason). */

#ifdef __cplusplus
extern "C" {
#endif

int main(int argc, char **argv);

int thread_entry(int cid, int nc)
{
  (void)cid; (void)nc;
  char *argv[] = { "miniweather", 0 };
  return main(1, argv);
}

#ifdef __cplusplus
}
#endif
