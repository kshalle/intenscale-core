/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: GPL-2.0-only
 *
 * Written for this port, but modelled on gcc-2.5.8's config/alpha/xm-alpha.h
 * and useful only for building GCC.  Labelled GPL-2.0-only for that reason.
 * See ../../../LICENSES/GPL-2.0-only.txt.
 */

/* GCC 2.5.8 host configuration for a 64-bit little-endian machine.
 *
 * gcc calls the machine the compiler *runs on* the "host", and describes it
 * with an xm-*.h header that configure links to config.h.  There is no such
 * header for a bare-metal riscv64 newlib target -- gcc 2.5.8 predates RISC-V
 * by two decades -- so this is it, modelled on config/alpha/xm-alpha.h, the
 * only 64-bit little-endian host the release shipped with.
 *
 * The same header serves as hconfig.h for the machine that runs the generator
 * programs (x86-64 Linux).  Both are 64-bit little-endian with 32-bit int and
 * 64-bit long, so the only differences are which C library facilities exist,
 * and those are keyed off __linux__ below.
 */

#define FALSE 0
#define TRUE  1

#define HOST_BITS_PER_CHAR      8
#define HOST_BITS_PER_SHORT     16
#define HOST_BITS_PER_INT       32
#define HOST_BITS_PER_LONG      64
#define HOST_BITS_PER_LONGLONG  64

/* Target description.  configure links this to the target's tm.h; here it is
   a one-line wrapper in gen/. */
#include "tm.h"

#define SUCCESS_EXIT_CODE 0
#define FAILURE_EXIT_CODE 33   /* gcc's own convention: 33 means "compiler error" */
#define FATAL_EXIT_CODE   34

/* Both libraries provide the BSD string functions under their own names, and
   both have a real alloca (gcc's builtin), so gcc's C-coded fallbacks are not
   needed. */
#define BSTRING

#ifdef __linux__
/* Building the generator programs on the build machine. */
#define USG
#else
/* The bare-metal target: newlib.  No process control, no user database, and
   nothing that wants <sys/time.h>/<sys/resource.h>. */
#define USG
#define NO_SYS_SIGLIST
#define NO_STAB_H
#endif
