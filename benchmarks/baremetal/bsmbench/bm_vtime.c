/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* BSMBench's clock, for the Linux build.
 *
 * BSMBench decides how much work to do by timing itself: each test doubles
 * its iteration count until its own elapsed-time measurement passes
 * MIN_TIME, which upstream fixes at 400 seconds in Bench/bsmbench.c and does
 * not expose on the command line.  It reads the clock with time(NULL): once
 * before the loop, once per doubling round, so after r rounds it believes
 * that r clock-steps have elapsed and it has done 2^r - 1 iterations.
 *
 * On the bare-metal target there is no wall clock, so the harness derives
 * time() from mcycle and the clock rate the target is told it has
 * (BM_CPU_HZ) -- see ../common/htif_syscalls.c.  400 simulated seconds costs
 * 400 * BM_CPU_HZ cycles, which is what lets a run finish in a simulator.
 *
 * Under Linux, time() is the real clock, and 400 real seconds per test on an
 * emulated target is not a benchmark anyone will wait for.  So this file
 * wraps time() (-Wl,--wrap=time, Linux build only).
 *
 * What it must NOT do is derive the clock from how fast the machine happens
 * to be.  That was the first version of this file, and it was wrong: the
 * amount of work then depended on the host.  The same tiny run did 7
 * iterations bare metal, 1 on one x86 host and 3 on another, because the
 * ratio between a calibration loop's speed and this workload's speed is not
 * a constant across machines and qemu builds.  A benchmark whose problem
 * size changes with the machine measuring it cannot be compared with
 * anything, including itself.
 *
 * So the clock here is synthetic and deterministic: each reading advances it
 * by exactly BM_VTIME_STEP seconds, no timing involved.  The number of
 * doubling rounds is then fixed at floor(MIN_TIME / BM_VTIME_STEP) + 1 on
 * every machine, and the Makefile picks the step per size so that it equals
 * the number the bare-metal run does.  The two builds therefore perform
 * identical work, and repeat runs are bit-for-bit repeatable.
 *
 * The "test completed in N seconds" figures BSMBench prints are then
 * synthetic too, as are its FLOP/s -- they always were in this port.  Read
 * the harness's [bm] line.
 */
#include <time.h>

/* Seconds per clock reading.  MIN_TIME is 400, so 200 gives 3 doubling
 * rounds (7 iterations) and 100 gives 5 (31 iterations). */
#ifndef BM_VTIME_STEP
#define BM_VTIME_STEP 100
#endif

time_t __wrap_time(time_t *tloc)
{
  static unsigned long readings;
  time_t v = (time_t)(readings++ * (unsigned long)BM_VTIME_STEP);

  if (tloc)
    *tloc = v;
  return v;
}
