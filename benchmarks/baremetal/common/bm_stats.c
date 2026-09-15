/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

#include "bm_stats.h"
#include <stdio.h>

#ifdef BM_LINUX
/* Under Linux the hardware counters may not be usable, in two different ways.
 *
 * They may trap: rdcycle and rdinstret raise SIGILL unless the kernel enables
 * them for user mode, and most kernels do not.  That case is easy -- probe
 * behind a SIGILL handler.
 *
 * Worse, they may answer, and answer about something else.  Under
 * qemu-riscv64 both instructions read a host-clock-derived value: over a loop
 * retiring exactly 2,000,000 instructions they returned 3,107,395 /
 * 2,539,752 / 2,948,596 on three consecutive runs.  A counter that returns a
 * plausible-looking wrong number is more dangerous than one that faults, so
 * the probe does not stop at "did it trap": it runs a loop whose retired
 * instruction count is known exactly and requires the counter to agree
 * exactly.  Being within a factor of two is not evidence of anything -- as
 * those three numbers show, and as their 22% spread over identical work shows
 * more plainly still.
*/
#include <setjmp.h>
#include <signal.h>
#include <stdlib.h>
#include <time.h>

static sigjmp_buf bm_probe_jmp;
static volatile int bm_probe_active;

static void bm_sigill(int sig)
{
  (void)sig;
  if (bm_probe_active)
    siglongjmp(bm_probe_jmp, 1);
}

static uint64_t bm_read_cycle(void)
{
  uint64_t v;
  __asm__ __volatile__ ("rdcycle %0" : "=r" (v));
  return v;
}

static uint64_t bm_read_instret(void)
{
  uint64_t v;
  __asm__ __volatile__ ("rdinstret %0" : "=r" (v));
  return v;
}

static uint64_t bm_cpu_ns(void)
{
  struct timespec ts;
  if (clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &ts) != 0)
    return 0;
  return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

/* Both loops are written in assembly because the instruction count has to be
 * exact, and what a C loop compiles to is at the compiler's discretion.
 *
 * Loop A: two instructions per iteration, the decrement and the branch.  The
 * loop is left by that same branch falling through, so the count is exactly
 * 2 * iterations.
 *
 * Loop B: three per iteration -- a load whose result is the next address, the
 * decrement, the branch.  Same idea, and the load depends on the previous
 * load, so the host cannot overlap them.
 *
 * The iteration counts are chosen to make the two instruction totals equal,
 * so the comparison needs no scaling.  Five trials each: about 3 M
 * instructions and a few tens of milliseconds in total, paid once per run and
 * outside the measured region. */
#define BM_CALIB_A_ITERS 150000ULL
#define BM_CALIB_B_ITERS 100000ULL
#define BM_CALIB_INSNS   (2ULL * BM_CALIB_A_ITERS)   /* == 3 * B_ITERS */
#define BM_CALIB_TRIALS  5

/* 16 MiB, larger than any last-level cache this is likely to meet, as a
 * power of two so an odd stride permutes it exactly. */
#define BM_CHASE_BYTES (16ULL << 20)
#define BM_CHASE_SLOTS (BM_CHASE_BYTES / sizeof(unsigned long))

static void bm_calib_loop_a(void)
{
  uint64_t n = BM_CALIB_A_ITERS;

  __asm__ __volatile__ ("1:\n\t"
                        "addi %0, %0, -1\n\t"
                        "bnez %0, 1b"
                        : "+r" (n) : : );
}

static unsigned long *bm_chase;

/* One cycle through every slot.  perm(i) = i * stride (mod slots) is a
 * bijection for an odd stride and a power-of-two size, and the large stride
 * keeps consecutive visits far enough apart that no prefetcher helps. */
static int bm_chase_setup(void)
{
  const unsigned long slots = BM_CHASE_SLOTS;
  const unsigned long stride = 0x9E3779B97F4A7C15UL | 1UL;
  unsigned long i;

  if (bm_chase)
    return 1;
  bm_chase = (unsigned long *)malloc(BM_CHASE_BYTES);
  if (!bm_chase)
    return 0;

  for (i = 0; i < slots; i++) {
    unsigned long here = (i * stride) & (slots - 1);
    unsigned long next = ((i + 1) * stride) & (slots - 1);
    bm_chase[here] = (unsigned long)&bm_chase[next];
  }
  return 1;
}

static void bm_calib_loop_b(void)
{
  unsigned long p = (unsigned long)&bm_chase[0];
  uint64_t n = BM_CALIB_B_ITERS;

  __asm__ __volatile__ ("1:\n\t"
                        "ld %0, 0(%0)\n\t"
                        "addi %1, %1, -1\n\t"
                        "bnez %1, 1b"
                        : "+r" (p), "+r" (n) : : "memory");
}

/* The smallest of the trials is the statistic to test.  On real hardware the
 * counter is exact, and the only thing that can inflate a reading is the hart
 * retiring instructions that are not ours -- a context switch, an interrupt --
 * which every trial is equally exposed to and which can only ever add.  So the
 * minimum is the cleanest sample, and it should be exact.
 *
 * The window is therefore tight: a couple of percent, not a factor.  It is
 * asymmetric because nothing can make the true count smaller.  Both loops
 * must pass it.  Only instret is checked -- cycles cannot be, since the whole
 * point of measuring is that cycles per instruction is unknown -- but the two
 * CSRs come from the same counter block, so an exact instret is good evidence
 * for rdcycle. */
/* Loop A is pinned tightly: a real counter is exact there, and the loop is
 * short enough (tens of microseconds) that little foreign work intrudes.
 *
 * Loop B is pinned loosely on the high side, and deliberately so.  It runs
 * for milliseconds, long enough for timer ticks and interrupts to add
 * instructions the hart really did retire, which a correct counter must
 * report.  The loose bound costs nothing, because the signal it is there to
 * catch is enormous: measured under qemu-riscv64, loop B reads
 *
 *     94,521,568 / 91,808,225 / 93,319,503 / 92,994,380 / 93,109,104
 *
 * against 300,000 expected -- 306x to 315x, five trials running.  A ceiling
 * of 2x leaves a 150x margin against that while tolerating a doubling from
 * kernel noise on real hardware.  Over the same runs loop A read 0.67x to
 * 2.65x of expected, which is why loop A alone decided nothing and why this
 * file now has two loops. */
#define BM_CALIB_A_MIN (BM_CALIB_INSNS *  98ULL / 100ULL)
#define BM_CALIB_A_MAX (BM_CALIB_INSNS * 105ULL / 100ULL)
#define BM_CALIB_B_MIN (BM_CALIB_INSNS *  98ULL / 100ULL)
#define BM_CALIB_B_MAX (BM_CALIB_INSNS * 200ULL / 100ULL)

static int bm_counters_ok = -1;                 /* -1 = not yet probed */
static const char *bm_counters_state = "unprobed";

/* Minimum instret delta over BM_CALIB_TRIALS runs of one loop. */
static uint64_t bm_calib_min(void (*loop)(void))
{
  uint64_t best = ~0ULL;
  int trial;

  for (trial = 0; trial < BM_CALIB_TRIALS; trial++) {
    uint64_t i0 = bm_read_instret();
    uint64_t d;

    loop();
    d = bm_read_instret() - i0;
    if (d < best)
      best = d;
  }
  return best;
}

static void bm_probe_counters(void)
{
  struct sigaction sa, old;
  int readable;
  uint64_t best_a, best_b;

  if (bm_counters_ok >= 0)
    return;

  /* Assume the worst until measured, so a longjmp out of the middle of this
     function cannot leave a half-finished verdict behind. */
  bm_counters_ok = 0;
  bm_counters_state = "trapped";

  sa.sa_handler = bm_sigill;
  sa.sa_flags = 0;
  sigemptyset(&sa.sa_mask);
  sigaction(SIGILL, &sa, &old);

  bm_probe_active = 1;
  readable = 0;
  if (sigsetjmp(bm_probe_jmp, 1) == 0) {
    (void)bm_read_instret();
    (void)bm_read_cycle();
    readable = 1;
  }
  bm_probe_active = 0;
  sigaction(SIGILL, &old, 0);

  if (!readable)
    return;                      /* stays "trapped" */

  /* No buffer, no second loop, no way to tell a counter from a clock: refuse
     rather than fall back to the test that cannot distinguish them. */
  if (!bm_chase_setup()) {
    bm_counters_state = "unverifiable";
    return;
  }

  best_a = bm_calib_min(bm_calib_loop_a);
  best_b = bm_calib_min(bm_calib_loop_b);

  if (best_a >= BM_CALIB_A_MIN && best_a <= BM_CALIB_A_MAX &&
      best_b >= BM_CALIB_B_MIN && best_b <= BM_CALIB_B_MAX) {
    bm_counters_ok = 1;
    bm_counters_state = "hardware";
  } else {
    /* Readable, but not counting this program's instructions.  A clock fails
       on loop B: same instructions, many times the elapsed time. */
    bm_counters_state = "unrelated";
  }
}

uint64_t bm_cycle(void)   { bm_probe_counters(); return bm_counters_ok ? bm_read_cycle() : 0; }
uint64_t bm_instret(void) { bm_probe_counters(); return bm_counters_ok ? bm_read_instret() : 0; }
const char *bm_env(void)  { return "linux"; }
int bm_counters_available(void) { bm_probe_counters(); return bm_counters_ok; }
const char *bm_counters_status(void) { bm_probe_counters(); return bm_counters_state; }

static uint64_t t0;

#else  /* bare metal: M-mode, the counters are ours and count only us */

uint64_t bm_cycle(void)
{
  uint64_t v;
  __asm__ __volatile__ ("csrr %0, mcycle" : "=r" (v));
  return v;
}

uint64_t bm_instret(void)
{
  uint64_t v;
  __asm__ __volatile__ ("csrr %0, minstret" : "=r" (v));
  return v;
}

const char *bm_env(void)  { return "baremetal"; }
int bm_counters_available(void) { return 1; }
const char *bm_counters_status(void) { return "hardware"; }
#endif

static uint64_t c0, i0;

void bm_stats_start(void)
{
  fflush(NULL);          /* keep console traffic out of the measured region */
#ifdef BM_LINUX
  (void)bm_counters_available();   /* probe now: it costs ~10 ms and must not
                                      land inside the measured region */
  t0 = bm_cpu_ns();
#endif
  i0 = bm_instret();
  c0 = bm_cycle();
}

void bm_stats_stop(const char *label)
{
  uint64_t c = bm_cycle() - c0;
  uint64_t i = bm_instret() - i0;
  const char *name = label ? label : "region";

#ifdef BM_LINUX
  uint64_t ns = bm_cpu_ns() - t0;

  /* No usable counter: report the CPU time and say why there are no counts,
     rather than printing a number that looks like a measurement. */
  if (!bm_counters_available()) {
    printf("[bm] env=%s name=%s counters=%s cputime_ms=%llu.%03llu\n",
           bm_env(), name, bm_counters_status(),
           (unsigned long long)(ns / 1000000ULL),
           (unsigned long long)(ns % 1000000ULL) / 1000ULL);
    fflush(NULL);
    return;
  }
#endif

  /* Integer milli-IPC: avoids dragging floating-point printf into a build
     that may otherwise have no need for it. */
  {
    uint64_t ipc_milli = c ? (i * 1000ULL) / c : 0;
#ifdef BM_LINUX
    printf("[bm] env=%s name=%s counters=%s cycles=%llu instret=%llu"
           " ipc=%llu.%03llu cputime_ms=%llu\n",
           bm_env(), name, bm_counters_status(),
           (unsigned long long)c, (unsigned long long)i,
           (unsigned long long)(ipc_milli / 1000), (unsigned long long)(ipc_milli % 1000),
           (unsigned long long)(ns / 1000000ULL));
#else
    printf("[bm] env=%s name=%s cycles=%llu instret=%llu ipc=%llu.%03llu\n",
           bm_env(), name, (unsigned long long)c, (unsigned long long)i,
           (unsigned long long)(ipc_milli / 1000), (unsigned long long)(ipc_milli % 1000));
#endif
  }
  fflush(NULL);
}
