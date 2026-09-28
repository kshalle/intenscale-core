/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* The terminal calls XLISP's Linux OS layer makes.
 *
 * Everything else it needs -- fork, wait, exec, system, popen, sigaction,
 * select, sysconf -- is generic enough to live in the harness
 * (../common/bm_posix_stubs.c), so only the termios pair is here.
 *
 * XLISP only touches the tty when it believes there is one.  The harness
 * reports isatty() false for every descriptor, so this code is not reached
 * during a benchmark run; it exists so the port links.
 */
#include <string.h>
#include <sys/termios.h>

int tcgetattr(int fd, struct termios *t)
{
  (void)fd;
  if (t)
    memset(t, 0, sizeof *t);
  return 0;
}

int tcsetattr(int fd, int action, const struct termios *t)
{
  (void)fd; (void)action; (void)t;
  return 0;
}
