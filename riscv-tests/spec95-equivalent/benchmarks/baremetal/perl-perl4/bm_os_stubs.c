/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* sys_errlist / sys_nerr for the perl 4.036 port.
 *
 * perl 4 formats $! by indexing sys_errlist, which newlib does not export.
 * Rather than set sys_nerr to 0 -- which would turn every "$!" into "Unknown
 * error" and make a failing benchmark much harder to diagnose -- the table is
 * filled from strerror() before main runs, so error text matches what the C
 * library would say.
 */
#include <errno.h>
#include <string.h>

#define BM_NERR 128

char *sys_errlist[BM_NERR];
int sys_nerr = BM_NERR;

__attribute__((constructor))
static void bm_init_errlist(void)
{
  int i;
  for (i = 0; i < BM_NERR; i++)
    sys_errlist[i] = strerror(i);
}
