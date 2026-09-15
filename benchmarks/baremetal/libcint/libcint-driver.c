/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Driver for the quantum chemistry workload: two-electron integral
 * derivatives.
 *
 * Evaluates libcint's int2e_ip1 -- the gradient of (ij|kl) with respect to
 * the first centre -- over every shell quartet of a small molecule.  The
 * algorithm is the one described in H. B. Schlegel, J. Chem. Phys. 77, 3676
 * (1982).
 *
 * The machine characteristics are the point of using this kernel: libcint's
 * autocode routines are long stretches of straight-line double-precision
 * arithmetic with very high register pressure and almost no control flow.
 *
 * The molecule and basis are built here rather than read from a file so the
 * benchmark has no input dependency and no licensing question: a linear chain
 * of BM_NATM centres, each carrying a contracted s, p and (optionally) d
 * shell.  Angular momentum is what drives the cost, so BM_WITH_D is the main
 * size knob.
 */

#include <math.h>
#include <stdio.h>
#include <stdlib.h>

#include "cint.h"
#include "cint_funcs.h"

#ifndef BM_LINUX
/* Bare metal only: see bm_longdouble.c.  glibc has the long-double
   transcendentals, so under Linux there is no fallback to count. */
extern unsigned long bm_longdouble_calls;
#endif

#ifndef BM_NATM
#define BM_NATM 3
#endif
#ifndef BM_WITH_D
#define BM_WITH_D 1
#endif
#ifndef BM_REPEATS
#define BM_REPEATS 1
#endif

#define MAX_ATM 8
#define SHELLS_PER_ATM (2 + BM_WITH_D)
#define MAX_BAS (MAX_ATM * SHELLS_PER_ATM)

/* A tiny fixed basis.  Exponents and contraction coefficients are arbitrary
   but chemically plausible magnitudes -- what matters for the benchmark is the
   number of primitives and the angular momenta, not the chemistry. */
static const double s_exp[3] = { 8.4, 1.7, 0.42 };
static const double s_coef[3] = { 0.15, 0.53, 0.44 };
static const double p_exp[2] = { 2.2, 0.55 };
static const double p_coef[2] = { 0.36, 0.71 };
static const double d_exp[1] = { 0.95 };
static const double d_coef[1] = { 1.0 };

static int add_shell(int *bas, int n, double *env, int *off, int atom,
                     int ang, int nprim, const double *e, const double *c)
{
        int i;
        bas[ATOM_OF  + BAS_SLOTS * n] = atom;
        bas[ANG_OF   + BAS_SLOTS * n] = ang;
        bas[NPRIM_OF + BAS_SLOTS * n] = nprim;
        bas[NCTR_OF  + BAS_SLOTS * n] = 1;
        bas[PTR_EXP  + BAS_SLOTS * n] = *off;
        for (i = 0; i < nprim; i++)
                env[*off + i] = e[i];
        *off += nprim;
        bas[PTR_COEFF + BAS_SLOTS * n] = *off;
        for (i = 0; i < nprim; i++)
                env[*off + i] = c[i] * CINTgto_norm(ang, e[i]);
        *off += nprim;
        return n + 1;
}

int main(void)
{
        int natm = BM_NATM;
        int nbas = 0;
        int atm[MAX_ATM * ATM_SLOTS];
        int bas[MAX_BAS * BAS_SLOTS];
        double env[4096];
        int off = PTR_ENV_START;
        int i, j, k, l, rep;

        if (natm > MAX_ATM) {
                printf("[libcint] BM_NATM %d exceeds MAX_ATM %d\n", natm, MAX_ATM);
                return 1;
        }

        /* A linear chain, 2.4 Bohr apart. */
        for (i = 0; i < natm; i++) {
                atm[CHARGE_OF + ATM_SLOTS * i] = 6;
                atm[PTR_COORD + ATM_SLOTS * i] = off;
                atm[NUC_MOD_OF + ATM_SLOTS * i] = 1;
                env[off + 0] = 0.0;
                env[off + 1] = 0.0;
                env[off + 2] = 2.4 * i;
                off += 3;
        }

        for (i = 0; i < natm; i++) {
                nbas = add_shell(bas, nbas, env, &off, i, 0, 3, s_exp, s_coef);
                nbas = add_shell(bas, nbas, env, &off, i, 1, 2, p_exp, p_coef);
#if BM_WITH_D
                nbas = add_shell(bas, nbas, env, &off, i, 2, 1, d_exp, d_coef);
#endif
        }

        /* Largest shell block, and the cache libcint wants for it.  Passing a
           NULL cache asks the routine for the size it needs rather than
           computing anything. */
        int dmax = 0, nao = 0;
        for (i = 0; i < nbas; i++) {
                int d = CINTcgto_spheric(i, bas);
                nao += d;
                if (d > dmax)
                        dmax = d;
        }

        size_t cache_size = 0;
        for (i = 0; i < nbas; i++) {
                int shls[4] = { i, i, i, i };
                size_t need = (size_t)int2e_ip1_sph(NULL, NULL, shls, atm, natm,
                                                    bas, nbas, env, NULL, NULL);
                if (need > cache_size)
                        cache_size = need;
        }

        double *cache = malloc(cache_size * sizeof(double));
        double *buf = malloc((size_t)dmax * dmax * dmax * dmax * 3 * sizeof(double));
        if (cache == NULL || buf == NULL) {
                printf("[libcint] out of memory (cache %lu doubles)\n",
                       (unsigned long)cache_size);
                return 1;
        }

        printf("[libcint] %d centres, %d shells, %d spherical functions\n",
               natm, nbas, nao);
        printf("[libcint] d shells %s, cache %lu doubles, %d repeats\n",
               BM_WITH_D ? "on" : "off", (unsigned long)cache_size, BM_REPEATS);

        CINTOpt *opt = NULL;
        int2e_ip1_optimizer(&opt, atm, natm, bas, nbas, env);

        /* Every shell quartet, three derivative components each.  The
           checksums are summed in a fixed order so a host build and a target
           build can be compared directly. */
        double sum = 0.0, abssum = 0.0, sqsum = 0.0;
        long quartets = 0, nonzero = 0, values = 0;

        for (rep = 0; rep < BM_REPEATS; rep++) {
                sum = abssum = sqsum = 0.0;
                quartets = nonzero = values = 0;

                for (i = 0; i < nbas; i++)
                for (j = 0; j < nbas; j++)
                for (k = 0; k < nbas; k++)
                for (l = 0; l < nbas; l++) {
                        int shls[4] = { i, j, k, l };
                        int n = CINTcgto_spheric(i, bas) * CINTcgto_spheric(j, bas)
                              * CINTcgto_spheric(k, bas) * CINTcgto_spheric(l, bas) * 3;
                        int has = int2e_ip1_sph(buf, NULL, shls, atm, natm,
                                                bas, nbas, env, opt, cache);
                        quartets++;
                        if (!has)
                                continue;   /* screened out */
                        nonzero++;
                        for (int m = 0; m < n; m++) {
                                double v = buf[m];
                                sum += v;
                                abssum += fabs(v);
                                sqsum += v * v;
                        }
                        values += n;
                }
        }

        CINTdel_optimizer(&opt);

        printf("[libcint] quartets=%ld evaluated=%ld values=%ld\n",
               quartets, nonzero, values);
        printf("[libcint] sum=%.14e\n", sum);
        printf("[libcint] abssum=%.14e\n", abssum);
        printf("[libcint] sqsum=%.14e\n", sqsum);
#ifndef BM_LINUX
        /* See bm_longdouble.c: nonzero means libcint fell back to its
           long-double precision path, where this port has only double
           accuracy available. */
        printf("[libcint] longdouble_fallback_calls=%lu\n", bm_longdouble_calls);
#endif
        printf("[libcint] done\n");

        free(buf);
        free(cache);
        return 0;
}
