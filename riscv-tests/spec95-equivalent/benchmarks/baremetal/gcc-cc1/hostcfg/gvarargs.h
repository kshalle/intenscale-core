/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: GPL-2.0-only
 *
 * Replaces gcc-2.5.8's gvarargs.h in this build; part of a GPL program.
 */

/* Replacement for gcc 2.5.8's gvarargs.h.
 *
 * The original implements the pre-ANSI varargs interface (`va_alist`,
 * `va_dcl`, one-argument `va_start`) on top of whichever va-*.h matches the
 * host CPU.  None of that works on a current compiler, and the definitions
 * that used it have been rewritten to the standard interface (see
 * mkstage.py), so what the sources need here is simply <stdarg.h>.
 *
 * Found ahead of the upstream copy by -I ordering plus -I-, which is what
 * stops a quoted include in an upstream source file from picking up its own
 * directory first.
 */
#ifndef BM_GVARARGS_H
#define BM_GVARARGS_H
#include <stdarg.h>
#endif
