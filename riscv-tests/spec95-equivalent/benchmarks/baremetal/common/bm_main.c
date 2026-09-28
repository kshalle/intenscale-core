/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Uniform cycle/instruction accounting without touching upstream sources.
 *
 * The port is linked with -Wl,--wrap=main, so the call the C runtime makes to
 * main() lands here and the benchmark's own main is reachable as __real_main.
 *
 * Why the linker and not -Dmain=bm_target_main: C++ grants main an implicit
 * "return 0" when control falls off the end, and that rule is keyed to the
 * function actually being called main.  Renaming it with the preprocessor
 * silently turns a benchmark whose main lacks a return statement (miniWeather
 * is one) into undefined behaviour -- observed as a garbage exit status and
 * part of the program re-executing at -O2.  Wrapping at link time leaves the
 * function named main, so the language rule still applies.
 *
 * This measures the whole program.  For a narrower region of interest, set
 * BM_WRAP_MAIN = 0 and call bm_stats_start()/bm_stats_stop() from a
 * port-local driver instead.
 */
#include "bm_stats.h"
#include <stdio.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

int __real_main(int argc, char **argv);

#ifndef BM_ISA_STR
#define BM_ISA_STR "unknown"
#endif

#ifndef BM_CONSOLE_BUFSIZE
#define BM_CONSOLE_BUFSIZE 8192
#endif

#ifndef BM_LABEL
#define BM_LABEL "benchmark"
#endif

/* Reported through atexit as well as on the return path: plenty of
   benchmarks leave through exit() rather than returning from main (perl and
   gcc both do), and without this the counters would simply never print. */
static int bm_reported;

static void bm_report(void)
{
  if (!bm_reported) {
    bm_reported = 1;
    bm_stats_stop(BM_LABEL);
  }
}

int __wrap_main(int argc, char **argv)
{
  int rc;

#ifdef BM_CONSOLE_BUFFERED
  /* One HTIF round trip costs ~151 k cycles on RTL, so line-buffered output
     can cost more than the benchmark.  Buffer the console explicitly rather
     than by misdescribing the descriptor: newlib only line-buffers a
     character device, and telling it the console is a regular file made it
     track a file offset there and silently drop the output. */
  {
    static char bm_console_buf[BM_CONSOLE_BUFSIZE];
    setvbuf(stdout, bm_console_buf, _IOFBF, sizeof bm_console_buf);
  }
#endif

  printf("[bm] env=%s name=%s isa=%s counters=%s\n",
         bm_env(), BM_LABEL, BM_ISA_STR, bm_counters_status());
  fflush(NULL);

  bm_stats_start();
  atexit(bm_report);

  rc = __real_main(argc, argv);

  bm_report();
  printf("[bm] env=%s name=%s exit=%d\n", bm_env(), BM_LABEL, rc);
  return rc;
}

#ifdef __cplusplus
}
#endif
