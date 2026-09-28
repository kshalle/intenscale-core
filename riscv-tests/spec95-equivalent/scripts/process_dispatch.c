/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Process-mode rate dispatcher (scripts/build.sh with RATE_MODE=process).
 *
 * Every hart runs the whole program -- one image, one copy of its code --
 * the way the copies of a rate run are separate processes of one
 * binary: code and read-only data are shared, but each hart has its own
 * writable data. Each hart gets its own Sv39 page table that maps everything
 * identity, except the image's writable range [bm_data_start, _end) (the
 * program's .data/.sdata/.sbss/.bss, C library state included), which it maps
 * to a private physical copy at the same virtual addresses. The hart stays in
 * M-mode with mstatus.MPRV set (MPP=S), so only data accesses are translated:
 * instruction fetch stays physical, which is fine because code is identity
 * mapped, and everything the riscv-tests runtime does in M-mode (mhartid,
 * the delay CSR, tohost_exit's barrier on rowActive/rowFail in .tohost, below
 * the private range) works unchanged. Per-hart stacks/TLS (crt.S) and heap
 * slices (bm_rate_heap.c) are already physically separate and stay identity
 * mapped. fesvr works on physical memory, so the harness translates pointer
 * arguments that fall in the private range (bm_pa, htif_syscalls.c).
 */

#include <stdint.h>
#include <fcntl.h>
#include "util.h"

void tohost_exit(uintptr_t code) __attribute__((noreturn));
void printstr(const char *s);

/* The program: one copy, every symbol prefixed h0_ by scripts/build.sh. */
extern int h0_thread_entry(int cid, int nc);
extern void h0_exit(int code) __attribute__((noreturn));
extern int h0_open(const char *path, int flags, ...);
extern void h0_bm_redirect_std(int fd, long host_fd);
extern unsigned long h0_bm_proc_lo, h0_bm_proc_hi;
extern long h0_bm_proc_off;
extern void (*__start_h0_init_array[])(void) __attribute__((weak));
extern void (*__stop_h0_init_array[])(void) __attribute__((weak));

extern char bm_data_start[];   /* process.ld: ADDR(.data) */
extern char _end[];            /* test.ld */

#define PAGE      4096UL
#define MEGA      (2UL << 20)
#define GIGA      (1UL << 30)
/* Physical placement: page tables below 1 GiB, above the image and all hart
   stacks; the private data copies between 1 GiB and the heap slices, which
   start at 0x80000000 (bm_rate_heap.c's BM_DRAM_BASE). */
#define PT_BASE   0x3C000000UL
#define PT_SIZE   (1UL << 20)
#define PRIV_BASE 0x40000000UL
#define PRIV_TOP  0x80000000UL

/* PTE_* from encoding.h. U is set (with mstatus.SUM) so the mapping holds whether a trap return
   leaves MPP at S or U. */
#define LEAF(pa)  ((((uint64_t)(pa) >> 12) << 10) | PTE_V | PTE_R | PTE_W | PTE_X | PTE_U | PTE_A | PTE_D)
#define TABLE(pa) ((((uint64_t)(pa) >> 12) << 10) | PTE_V)

static void fail(const char *s) __attribute__((noreturn));
static void fail(const char *s)
{
  printstr(s);
  tohost_exit(3);
}

static void enter_process(int h)
{
  uintptr_t lo = (uintptr_t)bm_data_start & ~(PAGE - 1);
  uintptr_t hi = ((uintptr_t)_end + PAGE - 1) & ~(PAGE - 1);
  uintptr_t stride = (hi - lo + MEGA - 1) & ~(MEGA - 1);
  uintptr_t priv = PRIV_BASE + (uintptr_t)h * stride;
  uint64_t *root = (uint64_t *)(PT_BASE + (uintptr_t)h * PT_SIZE);
  uint64_t *l1 = root + 512, *l0 = l1 + 512;
  uintptr_t p, va;
  int i, j, m, k = 0;

  if (hi > GIGA || priv + stride > PRIV_TOP)
    fail("process mode: image does not fit the private-copy layout\n");

  /* This hart's own copy of the writable data, as the loader left it. No
     hart writes the original range: each switches to its copy first. */
  for (p = lo; p < hi; p += 8)
    *(volatile uint64_t *)(priv + (p - lo)) = *(volatile uint64_t *)p;

  for (i = 0; i < 512; i++)
    root[i] = l1[i] = 0;
  root[0] = TABLE(l1);                  /* first GiB: MMIO + image, finer */
  for (i = 1; i < 32; i++)
    root[i] = LEAF((uintptr_t)i * GIGA); /* rest of DRAM: identity */
  for (j = 0; j < 512; j++) {
    va = (uintptr_t)j * MEGA;
    if (va + MEGA <= lo || va >= hi) {
      l1[j] = LEAF(va);
      continue;
    }
    uint64_t *t = l0 + 512 * k++;
    if ((uintptr_t)(t + 512) > PT_BASE + (uintptr_t)(h + 1) * PT_SIZE)
      fail("process mode: page-table area too small\n");
    for (m = 0; m < 512; m++) {
      uintptr_t pva = va + (uintptr_t)m * PAGE;
      t[m] = LEAF(pva >= lo && pva < hi ? priv + (pva - lo) : pva);
    }
    l1[j] = TABLE(t);
  }

  __sync_synchronize();
  asm volatile ("csrw satp, %0; sfence.vma" :: "r" ((8UL << 60) | ((uintptr_t)root >> 12)) : "memory");
  asm volatile ("csrc mstatus, %0" :: "r" (MSTATUS_MPP) : "memory");
  asm volatile ("csrs mstatus, %0" :: "r" ((1UL << 11) | MSTATUS_SUM | MSTATUS_MPRV) : "memory");

  /* From here on this hart's data accesses see its own copy. */
  h0_bm_proc_lo = lo;
  h0_bm_proc_hi = hi;
  h0_bm_proc_off = (long)(priv - lo);
}

/* Per-hart counters over the program (entry to _exit), reported as R_<hart>
   lines at exit: the rate metric's per-copy cycles and instructions. These
   are in the private range, so each hart's are its own. */
static uint64_t start_cycle, start_instret;

static void put_dec(char **p, uint64_t v)
{
  char d[24];
  int n = 0;
  do { d[n++] = '0' + v % 10; v /= 10; } while (v);
  while (n) *(*p)++ = d[--n];
}

static void put_str(char **p, const char *s)
{
  while (*s) *(*p)++ = *s++;
}

void __attribute__((noreturn)) _exit(int code)
{
  uint64_t c = read_csr(mcycle), i = read_csr(minstret);
  char buf[112], *p = buf;
  put_str(&p, "R_");
  put_dec(&p, read_csr(mhartid));
  put_str(&p, ": copy_mcycle = ");
  put_dec(&p, c - start_cycle);
  put_str(&p, " copy_minstret = ");
  put_dec(&p, i - start_instret);
  put_str(&p, " exit = ");
  put_dec(&p, (uint64_t)(unsigned)code);
  *p++ = '\n';
  *p = 0;
  printstr(buf);
  tohost_exit(code);
}

int thread_entry(int cid, int nc)
{
  void (**f)(void);
  char out[16] = "hartNN.out", err[16] = "hartNN.err";

  enter_process(cid);

  /* The program's own static constructors (e.g. C++ iostream's), as a C
     runtime's startup would run them; riscv-tests' crt.S runs none. */
  for (f = __start_h0_init_array; f < __stop_h0_init_array; f++)
    (*f)();

  /* This hart's fds 1 and 2 go to its own files in the emulator's working
     directory, so every copy's output can be checked on its own. */
  out[4] = err[4] = '0' + cid / 10;
  out[5] = err[5] = '0' + cid % 10;
  h0_bm_redirect_std(1, h0_open(out, O_WRONLY | O_CREAT | O_TRUNC, 0644));
  h0_bm_redirect_std(2, h0_open(err, O_WRONLY | O_CREAT | O_TRUNC, 0644));

  start_cycle = read_csr(mcycle);
  start_instret = read_csr(minstret);
  h0_exit(h0_thread_entry(cid, nc));
}
