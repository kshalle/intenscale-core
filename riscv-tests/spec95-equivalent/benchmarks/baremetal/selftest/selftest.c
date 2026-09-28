/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Harness smoke test: everything a ported benchmark relies on, in one binary.
   Run it before debugging a port -- if this passes, the harness is not at fault.

     spike --isa=rv64imafdc selftest.riscv input.txt
     <emulator> selftest.riscv input.txt
*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <sys/stat.h>
#include <setjmp.h>
#include "bm_stats.h"

static jmp_buf jb;
static void thrower(void) { longjmp(jb, 42); }

int main(int argc, char **argv)
{
  int fail = 0;

  printf("[selftest] argc=%d", argc);
  for (int i = 0; i < argc; i++) printf(" argv[%d]=%s", i, argv[i]);
  printf("\n");

  /* heap */
/* Sized for RTL: a large memset costs nothing on a host and minutes in
     Verilator.  Raise BM_SELFTEST_MB to stress the heap on spike. */
#ifndef BM_SELFTEST_MB
#define BM_SELFTEST_MB 4
#endif
  size_t big = (size_t)BM_SELFTEST_MB << 20;
  char *p = malloc(big);
  if (!p) { printf("FAIL malloc(%d MB)\n", BM_SELFTEST_MB); fail++; }
  else { memset(p, 0xa5, big); if ((unsigned char)p[1000] != 0xa5) { printf("FAIL heap readback\n"); fail++; } free(p); }

  /* libm + floating point printf */
  double s = sqrt(2.0), e = exp(1.0);
  if (fabs(s - 1.4142135623730951) > 1e-12 || fabs(e - 2.718281828459045) > 1e-12) { printf("FAIL libm\n"); fail++; }
  printf("[selftest] sqrt(2)=%.15f exp(1)=%.15f\n", s, e);

  /* setjmp/longjmp -- xlisp and perl both need it */
  if (setjmp(jb) == 0) thrower();
  else printf("[selftest] longjmp ok\n");

  /* host file input over HTIF */
  if (argc > 1) {
    FILE *f = fopen(argv[1], "r");
    if (!f) { printf("FAIL fopen(%s) for read\n", argv[1]); fail++; }
    else {
      char buf[256];
      if (!fgets(buf, sizeof buf, f)) { printf("FAIL fgets\n"); fail++; }
      else printf("[selftest] read back: %s", buf);
      if (fseek(f, 0, SEEK_END) != 0) { printf("FAIL fseek\n"); fail++; }
      printf("[selftest] file size via ftell = %ld\n", ftell(f));
      fclose(f);
    }
    struct stat st;
    if (stat(argv[1], &st) != 0) { printf("FAIL stat(%s)\n", argv[1]); fail++; }
    else printf("[selftest] stat size = %ld\n", (long)st.st_size);
  } else {
    printf("[selftest] (no input file given, skipping read tests)\n");
  }

  /* host file output over HTIF */
  FILE *w = fopen("selftest.out", "w");
  if (!w) { printf("FAIL fopen for write\n"); fail++; }
  else {
    fprintf(w, "written from bare metal\n");
    fclose(w);
    FILE *r = fopen("selftest.out", "r");
    if (!r) { printf("FAIL reopen written file\n"); fail++; }
    else {
      char buf[64] = {0};
      fgets(buf, sizeof buf, r);
      fclose(r);
      if (strncmp(buf, "written from bare metal", 23) != 0) { printf("FAIL write/readback: '%s'\n", buf); fail++; }
      else printf("[selftest] write/readback ok\n");
    }
  }

  /* counters */
  bm_stats_start();
  volatile double acc = 0;
  for (int i = 1; i < 20000; i++) acc += 1.0 / i;
  bm_stats_stop("selftest:harmonic");
  printf("[selftest] acc=%.6f\n", acc);

  printf("[selftest] %s (%d failures)\n", fail ? "FAILED" : "PASSED", fail);
  return fail != 0;
}
