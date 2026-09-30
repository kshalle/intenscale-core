/* SPDX-FileCopyrightText: 2026 Intensivate, Inc.
   SPDX-License-Identifier: BSD-2-Clause */
/* Process-control backends newlib's abort()/raise() pull into every rate
   copy. There is no process or signal facility on this target: each fails
   the way the real call would on a host that refused it. Weak, so a port's
   own stub (e.g. perl_extra_stubs.c) takes precedence. */
#include <errno.h>
__attribute__((weak)) int _kill(int pid, int sig) { (void)pid; (void)sig; errno = ENOSYS; return -1; }
__attribute__((weak)) int _getpid(void) { return 1; }
