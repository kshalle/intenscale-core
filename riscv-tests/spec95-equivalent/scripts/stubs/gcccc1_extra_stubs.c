/* SPDX-FileCopyrightText: 2026 Intensivate, Inc.
   SPDX-License-Identifier: BSD-2-Clause */
#include <errno.h>
int _kill(int pid, int sig) { (void)pid; (void)sig; errno = ENOSYS; return -1; }
int _getpid(void) { return 1; }
