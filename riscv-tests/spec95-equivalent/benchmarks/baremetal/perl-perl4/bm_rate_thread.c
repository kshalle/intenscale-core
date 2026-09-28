/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* "Rate" mode entry point: every hart runs perl's own main() on its own
 * heap partition (common/bm_rate_heap.c). Script path + args are compile-
 * time constants (BM_SCRIPT_PATH/BM_ARGV_HI/BM_ARGV_LO, same convention as
 * rowcore's BM_ARGV1..3) -- hardcoded here rather than routed through
 * getmainvars(), which vanilla thread_entry() only calls for hart 0.
 * perl's own main() is K&R-style int main(argc,argv,env); an empty envp is
 * fine since this port never reads environment variables. */

#ifndef BM_SCRIPT_PATH
#error "BM_SCRIPT_PATH must be defined at compile time"
#endif
#ifndef BM_ARGV_HI
#error "BM_ARGV_HI must be defined at compile time"
#endif
#ifndef BM_ARGV_LO
#error "BM_ARGV_LO must be defined at compile time"
#endif

int main(int argc, char **argv, char **env);

int thread_entry(int cid, int nc)
{
  (void)cid; (void)nc;
  char *argv[] = { "perl", BM_SCRIPT_PATH, BM_ARGV_HI, BM_ARGV_LO, 0 };
  char *envp[] = { 0 };
  return main(4, argv, envp);
}
