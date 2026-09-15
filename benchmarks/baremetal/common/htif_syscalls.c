/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Newlib syscall glue over the HTIF frontend-server proxy.
 *
 * Why this file exists: the libgloss_htif BSP shipped with the riscv-tools
 * newlib implements only _write, _exit and _sbrk.  _open, _openat, _read,
 * _close, _lseek and _fstat are stubs that set errno = ENOENT and return -1,
 * so any benchmark that reads an input file or writes an output file fails on
 * the target while working fine on the host.  These definitions live in an
 * object file that is linked ahead of libgloss_htif.a, so they take precedence
 * over the archive's stubs.
 *
 * Two translations are required and are the entire subtlety here:
 *   1. open() flags.  newlib uses BSD-ish values (O_CREAT 0x0200); fesvr hands
 *      the flags straight to the host's openat(), which expects Linux values
 *      (O_CREAT 0x40).  Passing them through unmapped silently creates the
 *      wrong kind of file descriptor.
 *   2. struct stat.  fesvr writes the Linux riscv64 layout (fesvr's
 *      riscv_stat); newlib's struct stat has a different shape.
 */

#include "htif.h"

#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <time.h>
#include <sys/times.h>
#include <sys/types.h>
#include <stddef.h>
#include <unistd.h>

extern volatile uint64_t tohost;
extern volatile uint64_t fromhost;

long bm_htif_syscall(long which, long a0, long a1, long a2,
                     long a3, long a4, long a5, long a6)
{
  volatile uint64_t magic_mem[8] __attribute__((aligned(64)));

  magic_mem[0] = (uint64_t)which;
  magic_mem[1] = (uint64_t)a0;
  magic_mem[2] = (uint64_t)a1;
  magic_mem[3] = (uint64_t)a2;
  magic_mem[4] = (uint64_t)a3;
  magic_mem[5] = (uint64_t)a4;
  magic_mem[6] = (uint64_t)a5;
  magic_mem[7] = (uint64_t)a6;
  __sync_synchronize();

  /* Device 0, command 0 == syscall; payload is the low 48 bits of the
     magic_mem address, matching libgloss_htif's own trampoline. */
  tohost = ((uint64_t)(uintptr_t)magic_mem) & 0xffffffffffffULL;
  while (fromhost == 0)
    ;
  fromhost = 0;
  __sync_synchronize();

  return (long)magic_mem[0];
}

/* fesvr returns -errno on failure.  Convert to the -1/errno convention. */
static long bm_ret(long r)
{
  if (r < 0 && r > -4096) {
    errno = (int)-r;
    return -1;
  }
  return r;
}

/* ---- open flag translation: newlib values -> Linux riscv64 values ---- */

#define LINUX_O_CREAT     0x000040
#define LINUX_O_EXCL      0x000080
#define LINUX_O_NOCTTY    0x000100
#define LINUX_O_TRUNC     0x000200
#define LINUX_O_APPEND    0x000400
#define LINUX_O_NONBLOCK  0x000800
#define LINUX_O_DSYNC     0x001000
#define LINUX_O_DIRECTORY 0x010000
#define LINUX_O_NOFOLLOW  0x020000
#define LINUX_O_CLOEXEC   0x080000
#define LINUX_O_SYNC      0x101000

static int bm_open_flags(int flags)
{
  int out = flags & O_ACCMODE;          /* O_RDONLY/O_WRONLY/O_RDWR agree */

  if (flags & O_CREAT)     out |= LINUX_O_CREAT;
  if (flags & O_EXCL)      out |= LINUX_O_EXCL;
  if (flags & O_TRUNC)     out |= LINUX_O_TRUNC;
  if (flags & O_APPEND)    out |= LINUX_O_APPEND;
  if (flags & O_NONBLOCK)  out |= LINUX_O_NONBLOCK;
  if (flags & O_NOCTTY)    out |= LINUX_O_NOCTTY;
  if (flags & O_SYNC)      out |= LINUX_O_SYNC;
#ifdef O_CLOEXEC
  if (flags & O_CLOEXEC)   out |= LINUX_O_CLOEXEC;
#endif
#ifdef O_DIRECTORY
  if (flags & O_DIRECTORY) out |= LINUX_O_DIRECTORY;
#endif
#ifdef O_NOFOLLOW
  if (flags & O_NOFOLLOW)  out |= LINUX_O_NOFOLLOW;
#endif
  return out;
}

/* ---- struct stat translation: fesvr's Linux layout -> newlib's ---- */

struct bm_linux_stat {
  uint64_t dev;
  uint64_t ino;
  uint32_t mode;
  uint32_t nlink;
  uint32_t uid;
  uint32_t gid;
  uint64_t rdev;
  uint64_t __pad1;
  uint64_t size;
  uint32_t blksize;
  uint32_t __pad2;
  uint64_t blocks;
  uint64_t atime;
  uint64_t __pad3;
  uint64_t mtime;
  uint64_t __pad4;
  uint64_t ctime;
  uint64_t __pad5;
  uint32_t __unused4;
  uint32_t __unused5;
};

static void bm_copy_stat(const struct bm_linux_stat *ls, struct stat *st)
{
  memset(st, 0, sizeof(*st));
  st->st_dev     = ls->dev;
  st->st_ino     = ls->ino;
  st->st_mode    = ls->mode;
  st->st_nlink   = ls->nlink;
  st->st_uid     = ls->uid;
  st->st_gid     = ls->gid;
  st->st_rdev    = ls->rdev;
  st->st_size    = ls->size;
  st->st_blksize = ls->blksize;
  st->st_blocks  = ls->blocks;
  st->st_atime   = ls->atime;
  st->st_mtime   = ls->mtime;
  st->st_ctime   = ls->ctime;
}

/* ---- the syscalls newlib needs ---- */

int _openat(int dirfd, const char *name, int flags, int mode)
{
  return (int)bm_ret(bm_htif_syscall(BM_SYS_openat, dirfd, (long)name,
                                     (long)(strlen(name) + 1),
                                     bm_open_flags(flags), mode, 0, 0));
}

int _open(const char *name, int flags, ...)
{
  int mode = 0666;
  if (flags & O_CREAT) {
    va_list ap;
    va_start(ap, flags);
    mode = va_arg(ap, int);
    va_end(ap);
  }
  return _openat(BM_AT_FDCWD, name, flags, mode);
}

ssize_t _read(int fd, void *buf, size_t len)
{
  return (ssize_t)bm_ret(bm_htif_syscall(BM_SYS_read, fd, (long)buf,
                                         (long)len, 0, 0, 0, 0));
}

ssize_t _write(int fd, const void *buf, size_t len)
{
  return (ssize_t)bm_ret(bm_htif_syscall(BM_SYS_write, fd, (long)buf,
                                         (long)len, 0, 0, 0, 0));
}

int _close(int fd)
{
  if (fd < 3)   /* keep the console usable after a benchmark closes stdio */
    return 0;
  return (int)bm_ret(bm_htif_syscall(BM_SYS_close, fd, 0, 0, 0, 0, 0, 0));
}

off_t _lseek(int fd, off_t off, int whence)
{
  return (off_t)bm_ret(bm_htif_syscall(BM_SYS_lseek, fd, (long)off,
                                       whence, 0, 0, 0, 0));
}

int _fstat(int fd, struct stat *st)
{
  struct bm_linux_stat ls;
  long r;

  /* Always describe the three standard descriptors as character devices.
     Claiming a regular file here (an earlier attempt at cheaper buffering)
     makes newlib treat the console as seekable and track a file offset on it,
     which does not survive the frontend server: the buffered output of a run
     was silently dropped on the rocket emulator while working fine on spike.
     Buffering is set explicitly instead -- see bm_main.c. */
  if (fd < 3) {
    memset(st, 0, sizeof(*st));
    st->st_mode = S_IFCHR;
    st->st_blksize = 1024;
    return 0;
  }

  r = bm_ret(bm_htif_syscall(BM_SYS_fstat, fd, (long)&ls, 0, 0, 0, 0, 0));
  if (r < 0)
    return -1;
  bm_copy_stat(&ls, st);

  /* Advertise a large block size so newlib gives this file a large stdio
     buffer.  Every HTIF round trip is expensive on real hardware -- measured
     at ~151 k cycles on the rocket emulator, because the frontend server is
     reached through the debug module -- so reading a workload input in 64 KiB
     chunks instead of 1 KiB saves a great deal of simulated time.  Override
     with BM_FILE_BLKSIZE. */
#ifndef BM_FILE_BLKSIZE
#define BM_FILE_BLKSIZE (64 * 1024)
#endif
  if (S_ISREG(st->st_mode) && BM_FILE_BLKSIZE > 0)
    st->st_blksize = BM_FILE_BLKSIZE;

  return 0;
}

int _stat(const char *name, struct stat *st)
{
  struct bm_linux_stat ls;
  long r = bm_ret(bm_htif_syscall(BM_SYS_fstatat, BM_AT_FDCWD, (long)name,
                                  (long)(strlen(name) + 1), (long)&ls, 0, 0, 0));
  if (r < 0)
    return -1;
  bm_copy_stat(&ls, st);
  return 0;
}

int _lstat(const char *name, struct stat *st)
{
  return _stat(name, st);
}

int _unlink(const char *name)
{
  return (int)bm_ret(bm_htif_syscall(BM_SYS_unlinkat, BM_AT_FDCWD, (long)name,
                                     (long)(strlen(name) + 1), 0, 0, 0, 0));
}

int _access(const char *name, int mode)
{
  return (int)bm_ret(bm_htif_syscall(BM_SYS_faccessat, BM_AT_FDCWD, (long)name,
                                     (long)(strlen(name) + 1), mode, 0, 0, 0));
}

/* No terminal exists on the target.  Answering honestly matters: programs
   written for a host branch on isatty() to decide between an interactive
   session and batch processing, and a bare-metal benchmark always wants the
   batch path.  (XLISP, for one, otherwise sets up raw-mode tty handling and
   then blocks forever waiting for a terminal that is not there.)  Console
   buffering does not depend on this -- _fstat reports the standard
   descriptors as character devices so that stdout stays line buffered. */
int _isatty(int fd)
{
  (void)fd;
  return 0;
}

/* Heap.
 *
 * htif.ld reserves only 128 KiB of heap (__heap_end = _end + 128K) and the
 * BSP's _sbrk refuses to go past it, so a benchmark of any size fails its
 * first large malloc.  This _sbrk replaces it and hands out memory from above
 * the per-hart stacks (which crt0 places directly after __heap_end) up to
 * BM_HEAP_MB from the base of DRAM.
 *
 * The simulator must actually have that much memory: pass
 *   <emulator> --ram-size=256MB ...
 * and keep BM_HEAP_MB below it.  spike defaults to 2 GiB, so it needs nothing.
 */
extern char __heap_end[];      /* provided by htif.ld */

#ifndef BM_DRAM_BASE
#define BM_DRAM_BASE 0x80000000UL
#endif
#ifndef BM_HEAP_MB
#define BM_HEAP_MB 192
#endif
/* Clears the per-hart stacks that sit immediately above __heap_end
   (1 MiB covers 32 harts at the 32 KiB default stack size). */
#ifndef BM_STACK_RESERVE
#define BM_STACK_RESERVE (1UL << 20)
#endif

#define BM_HEAP_TOP (BM_DRAM_BASE + ((unsigned long)BM_HEAP_MB << 20))

static char *bm_brk;

void *_sbrk(ptrdiff_t incr)
{
  char *p_old, *p_new;

  if (bm_brk == 0) {
    bm_brk = (char *)(((uintptr_t)__heap_end + BM_STACK_RESERVE + 15) & ~(uintptr_t)15);
    if ((uintptr_t)bm_brk < BM_DRAM_BASE)   /* linked somewhere unexpected */
      bm_brk = (char *)BM_DRAM_BASE;
  }

  p_old = bm_brk;
  p_new = p_old + incr;

  if (p_new < (char *)__heap_end || (uintptr_t)p_new > BM_HEAP_TOP) {
    errno = ENOMEM;
    return (void *)-1;
  }
  bm_brk = p_new;
  return p_old;
}

/* Time.  There is no wall clock on a bare-metal target, so derive one from the
   cycle counter and a nominal frequency.  Benchmarks that only diff their own
   timestamps behave sensibly; absolute time is meaningless and says so (epoch
   is boot).  Override BM_CPU_HZ to match the target clock. */
#ifndef BM_CPU_HZ
#define BM_CPU_HZ 1000000000ULL
#endif

static inline uint64_t bm_cycles(void)
{
  uint64_t c;
  __asm__ __volatile__ ("csrr %0, mcycle" : "=r" (c));
  return c;
}

int _gettimeofday(struct timeval *tv, void *tz)
{
  uint64_t c = bm_cycles();
  (void)tz;
  if (tv) {
    tv->tv_sec  = (time_t)(c / BM_CPU_HZ);
    /* Scale the remainder rather than dividing by BM_CPU_HZ/1000000: a
       benchmark may deliberately be told a clock rate below 1 MHz (BSMBench
       sizes its own run from the clock), and that form divides by zero. */
    tv->tv_usec = (suseconds_t)(((c % BM_CPU_HZ) * 1000000ULL) / BM_CPU_HZ);
  }
  return 0;
}

clock_t _times(struct tms *buf)
{
  /* newlib's clock() reports in CLOCKS_PER_SEC ticks.  Split the division so
     the result is right for any BM_CPU_HZ, above or below CLOCKS_PER_SEC, and
     so the multiply cannot overflow for realistic cycle counts. */
  uint64_t c = bm_cycles();
  clock_t t = (clock_t)((c / BM_CPU_HZ) * CLOCKS_PER_SEC
                        + ((c % BM_CPU_HZ) * CLOCKS_PER_SEC) / BM_CPU_HZ);
  if (buf) {
    buf->tms_utime  = t;
    buf->tms_stime  = 0;
    buf->tms_cutime = 0;
    buf->tms_cstime = 0;
  }
  return t;
}

/* std::chrono::steady_clock (libstdc++) needs clock_gettime.  Derive it from
   the cycle counter like the rest of the time functions here. */
int clock_gettime(int clk_id, struct timespec *tp)
{
  uint64_t c = bm_cycles();
  (void)clk_id;
  if (tp) {
    tp->tv_sec  = (time_t)(c / BM_CPU_HZ);
    tp->tv_nsec = (long)(((c % BM_CPU_HZ) * 1000000000ULL) / BM_CPU_HZ);
  }
  return 0;
}

time_t _time(time_t *t)
{
  time_t s = (time_t)(bm_cycles() / BM_CPU_HZ);
  if (t)
    *t = s;
  return s;
}

/* There is one directory on a bare-metal target: whatever the frontend
   server's own working directory is.  Report it as "." so that code which
   builds paths from getcwd() produces host-relative paths that fesvr can
   actually open.  Both spellings are defined because newlib does not wrap
   _getcwd. */
char *_getcwd(char *buf, size_t size)
{
  if (size < 2) { errno = ERANGE; return 0; }
  buf[0] = '.'; buf[1] = '\0';
  return buf;
}

char *getcwd(char *buf, size_t size)
{
  return _getcwd(buf, size);
}
