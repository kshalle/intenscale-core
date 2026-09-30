/* SPDX-FileCopyrightText: 2026 Intensivate, Inc.
   SPDX-License-Identifier: BSD-2-Clause */
/* Minimal newlib syscall-backend stubs needed to link xlisp.riscv under the
 * riscv-tests/benchmarks Makefrag-baremetal + crt.S/test.ld rate-mode
 * recipe. htif_syscalls.c and bm_posix_stubs.c (from
 * /work/benchmarks-baremetal/common/) cover almost everything xlisp's libc
 * usage needs, but xlisp additionally pulls in newlib's signalr.o and
 * linkr.o (most likely indirectly via abort()/raise() and rename()/remove()
 * fallback paths), which call these three underscore-prefixed reentrant
 * syscall backends that neither harness file stubs. This is a bare-metal,
 * single-image target: there is no real process/signal/hardlink facility to
 * back these, so each just fails the way the corresponding real syscall
 * would fail on a host that refused the operation.
 */
#include <errno.h>

int _kill(int pid, int sig)
{
  (void)pid; (void)sig;
  errno = ENOSYS;
  return -1;
}

int _getpid(void)
{
  return 1;
}

int _link(const char *oldpath, const char *newpath)
{
  (void)oldpath; (void)newpath;
  errno = ENOSYS;
  return -1;
}
