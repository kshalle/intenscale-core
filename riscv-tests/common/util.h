// See LICENSE for license details.

#ifndef __UTIL_H
#define __UTIL_H

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>

// To make everything work in spike
#ifdef SPIKE
  #define NO_DELAY_CSR
#endif

// spike doesn't support CSR 0xf. Defining NODELAY will make this work in spike.
#ifdef NO_DELAY_CSR
  // Ths is not nearly as accurate but it will only be used
  // on auxilliary platforms like spike, so accuracy isn't so important.
  // There needs to be an odd number of NOPs in here to cause
  // serialisation failures in spike with 2-insn sequences.
  #define delay_cycles(n) \
    do { for(int i=0;i<(n);i+=7) asm volatile("nop; nop; nop; nop; nop"); } while(0)
#else
  #define delay_cycles(n) asm volatile("csrw 0xf, %0" :: "r"(n))
#endif

#ifdef DEBUG
	#define debugf(...) printf(__VA_ARGS__)
#else
	#define debugf(...)
#endif

extern void setStats(int enable);

#define static_assert(cond) switch(0) { case 0: case !!(long)(cond): ; }

static int verify(int n, const volatile int* test, const int* verify)
{
  int i;
  // Unrolled for faster verification
  for (i = 0; i < n/2*2; i+=2)
  {
    int t0 = test[i], t1 = test[i+1];
    int v0 = verify[i], v1 = verify[i+1];
    if (t0 != v0) return i+1;
    if (t1 != v1) return i+2;
  }
  if (n % 2 != 0 && test[n-1] != verify[n-1])
    return n;
  return 0;
}

static int verifyDouble(int n, const volatile double* test, const double* verify)
{
  int i;
  // Unrolled for faster verification
  for (i = 0; i < n/2*2; i+=2)
  {
    double t0 = test[i], t1 = test[i+1];
    double v0 = verify[i], v1 = verify[i+1];
    int eq1 = t0 == v0, eq2 = t1 == v1;
    if (!(eq1 & eq2)) return i+1+eq1;
  }
  if (n % 2 != 0 && test[n-1] != verify[n-1])
    return n;
  return 0;
}

static void __attribute__((noinline)) barrier(int ncores)
{
  static volatile int sense;
  static volatile int count;
  static __thread int threadsense;

  __sync_synchronize();

  threadsense = !threadsense;
  if (__sync_fetch_and_add(&count, 1) == ncores-1)
  {
    count = 0;
    sense = threadsense;
  }
  // A bare tight-spin poll here has every waiting hart hammer the shared
  // `sense` cache line back-to-back for as long as it waits (which can be
  // millions of cycles when contexts finish their work at very different
  // times) -- this concentrated polling traffic can starve unrelated
  // in-flight memory ops from the remaining, still-working harts. Space
  // polls out to keep the shared line from being saturated.
  else while(sense != threadsense)
    delay_cycles(128);

  __sync_synchronize();
}

static uint64_t lfsr(uint64_t x)
{
  uint64_t bit = (x ^ (x >> 1)) & 1;
  return (x >> 1) | (bit << 62);
}

static uintptr_t insn_len(uintptr_t pc)
{
  return (*(unsigned short*)pc & 3) ? 4 : 2;
}

#ifdef __riscv
#include "encoding.h"
#endif

#define stringify_1(s) #s
#define stringify(s) stringify_1(s)
#define stats(code, iter) do { \
    unsigned long _c = -read_csr(mcycle), _i = -read_csr(minstret); \
    code; \
    _c += read_csr(mcycle), _i += read_csr(minstret); \
    if (cid == 0) \
      printf("\n%s: %ld cycles, %ld.%ld cycles/iter, %ld.%ld CPI\n", \
             stringify(code), _c, _c/iter, 10*_c/iter%10, _c/_i, 10*_c/_i%10); \
  } while(0)

// In setStats, we might trap reading uarch-specific counters.
// The trap handler will skip over the instruction and write 0,
// but only if a0 is the destination register.
#define read_csr_safe(reg) ({ register long __tmp asm("a0"); \
  asm volatile ("csrr %0, " #reg : "=r"(__tmp)); \
  __tmp; })

#define MAX_EXCEPTION_CODE 63
#define MAX_INTERRUPT_CODE 15

typedef uintptr_t (*trap_handler)(uint64_t cause, uintptr_t epc, uint64_t regs[32]);

extern trap_handler exception_handlers[MAX_EXCEPTION_CODE+1];
extern trap_handler interrupt_handlers[MAX_INTERRUPT_CODE+1];

static void set_interrupt_handler(unsigned int trapno, trap_handler handler) {
  if(trapno > MAX_INTERRUPT_CODE) {
    printf("Attempt to set invalid interrupt handler %u\n",trapno);
    exit(999);
  }
  interrupt_handlers[trapno] = handler;
}

static void set_exception_handler(unsigned int trapno, trap_handler handler) {
  if(trapno > MAX_EXCEPTION_CODE) {
    printf("Attempt to set invalid exception handler %u\n",trapno);
    exit(999);
  }
  exception_handlers[trapno] = handler;
}

extern void trace_dump();

int thread_entry(int,int);

#endif //__UTIL_H
