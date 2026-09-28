/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Shared per-hart heap partition for "rate" mode (every hart running the
 * workload independently -- see e.g. libcint/bm_rate_thread.c for the
 * original of this). Reused as-is (unmodified) by every spec95-equivalent port's own
 * bm_rate_thread.c, which supplies only the port-specific thread_entry().
 *
 * newlib's malloc keeps its free-list bookkeeping in the memory _sbrk hands
 * out, and that bookkeeping is not thread-safe. htif_syscalls.c's own _sbrk
 * hands every hart the same shared bm_brk pointer, so concurrent mallocs
 * across 16 harts running the same workload would race on it and corrupt
 * the heap. This weak-overrides that one (see the __attribute__ added there)
 * with an NHARTS-way disjoint slice per hart, computed once per hart from
 * its own hartid, so no two harts ever touch the same bytes and no locking
 * is needed.
 */

#include <errno.h>
#include <stddef.h>
#include <stdint.h>

extern char __heap_end[];

#ifndef NHARTS
#define NHARTS 1
#endif
#ifndef BM_DRAM_BASE
#define BM_DRAM_BASE 0x80000000UL
#endif
#ifndef BM_HEAP_MB
#define BM_HEAP_MB 192
#endif
#ifndef BM_STACK_RESERVE
#define BM_STACK_RESERVE (1UL << 20)
#endif

#define BM_HEAP_TOP (BM_DRAM_BASE + ((unsigned long)BM_HEAP_MB << 20))

static inline unsigned long bm_hartid(void)
{
  unsigned long h;
  __asm__ __volatile__ ("csrr %0, mhartid" : "=r" (h));
  return h;
}

static char *bm_brk[NHARTS];
static char *bm_zone_base[NHARTS];
static char *bm_zone_top[NHARTS];

void *_sbrk(ptrdiff_t incr)
{
  unsigned long h = bm_hartid();
  char *p_old, *p_new;

  if (bm_brk[h] == 0) {
    char *base = (char *)(((uintptr_t)__heap_end + BM_STACK_RESERVE + 15) & ~(uintptr_t)15);
    uintptr_t total, zone;
    if ((uintptr_t)base < BM_DRAM_BASE)
      base = (char *)BM_DRAM_BASE;
    total = (uintptr_t)BM_HEAP_TOP - (uintptr_t)base;
    zone  = total / NHARTS;
    bm_zone_base[h] = base + h * zone;
    bm_zone_top[h]  = base + (h + 1) * zone;
    bm_brk[h] = bm_zone_base[h];
  }

  p_old = bm_brk[h];
  p_new = p_old + incr;
  if (p_new < bm_zone_base[h] || p_new > bm_zone_top[h]) {
    errno = ENOMEM;
    return (void *)-1;
  }
  bm_brk[h] = p_new;
  return p_old;
}
