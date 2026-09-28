/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* "Rate" mode entry point: every hart runs cc1's own main() on its own heap
 * partition (common/bm_rate_heap.c). Input path is a compile-time constant
 * (BM_INPUT_PATH); the output path is NOT -- gcc-cc1 writes its compiled
 * assembly to a single fixed file, and 16 harts all writing "-o out.s"
 * concurrently would corrupt each other's output. Each hart instead gets
 * its own path with its hartid embedded, built at runtime since it must
 * differ per hart. cc1's own main() is K&R-style
 * int main(argc,argv,envp); an empty envp is fine, this port never reads
 * environment variables. */

#include <stdio.h>

#ifndef BM_INPUT_PATH
#error "BM_INPUT_PATH must be defined at compile time"
#endif
#ifndef BM_OUTPUT_PREFIX
#define BM_OUTPUT_PREFIX "/tmp/gcc-cc1-out"
#endif

static inline unsigned long bm_hartid(void)
{
  unsigned long h;
  __asm__ __volatile__ ("csrr %0, mhartid" : "=r" (h));
  return h;
}

int main(int argc, char **argv, char **envp);

int thread_entry(int cid, int nc)
{
  (void)cid; (void)nc;
  char outpath[64];   /* per-hart: each hart runs on its own stack, unlike
                          a `static` local, which would be one shared buffer
                          all 16 harts raced to write/read concurrently */
  snprintf(outpath, sizeof outpath, BM_OUTPUT_PREFIX "-hart%lu.s", bm_hartid());
  char *argv[] = { "cc1", BM_INPUT_PATH, "-quiet", "-O", "-o", outpath, 0 };
  char *envp[] = { 0 };
  return main(6, argv, envp);
}
