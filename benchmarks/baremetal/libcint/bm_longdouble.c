/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Long-double transcendentals that newlib's riscv64 math library omits.
 *
 * riscv64's long double is binary128.  libgcc provides the arithmetic
 * (__addtf3 and friends) but newlib provides no expl/erfl/erfcl, and libcint
 * uses them in its second-tier precision path: when the double-precision Rys
 * root finder loses accuracy, it retries in long double, and if quadmath were
 * available it would retry again in __float128.
 *
 * These fall back to the double versions, which means that retry no longer
 * buys extra precision.  That is a real limitation, so it is measured rather
 * than assumed: every call is counted, and the driver prints the count.  If
 * it comes out zero -- which it does for the basis sets shipped here -- the
 * fallback never ran and the port's numerics are unaffected.  A workload with
 * harder integrals (very diffuse or very tight exponents, high angular
 * momentum) could reach it, and the count is how you would find out.
 */

#include <math.h>

unsigned long bm_longdouble_calls = 0;

long double expl(long double x)
{
        bm_longdouble_calls++;
        return (long double)exp((double)x);
}

long double erfl(long double x)
{
        bm_longdouble_calls++;
        return (long double)erf((double)x);
}

long double erfcl(long double x)
{
        bm_longdouble_calls++;
        return (long double)erfc((double)x);
}
