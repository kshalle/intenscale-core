/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* POSIX calls that a single-image bare-metal target cannot honour.
 *
 * Legacy programs reach for processes, users, terminals and directories
 * whether or not the platform has them, often on paths they never actually
 * take.  Each stub here fails the way its caller already handles on a host
 * that refused the operation, so a benchmark links and runs instead of
 * failing to build -- and if one of these paths really is taken, it reports a
 * plausible errno rather than corrupting state.
 *
 * These are the plain POSIX names.  The underscore-prefixed newlib syscall
 * glue that is actually backed by the frontend server lives in
 * htif_syscalls.c; nothing here duplicates it.
 *
 * Deliberately NOT stubbed: anything fesvr can really do (open, read, write,
 * close, lseek, stat).  If a stub here starts getting hit on a hot path, that
 * is a signal the workload wants a facility the target does not have, not
 * that the stub needs to be made cleverer.
 */

#include <errno.h>
#include <signal.h>
#include <string.h>
#include <stdio.h>
#include <sys/select.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

/* ---- processes ---- */
int fork(void)  { errno = ENOSYS; return -1; }
int vfork(void) { errno = ENOSYS; return -1; }
int wait(int *status) { (void)status; errno = ECHILD; return -1; }
int waitpid(int pid, int *status, int options)
{ (void)pid; (void)status; (void)options; errno = ECHILD; return -1; }

int execl(const char *path, const char *arg, ...)
{ (void)path; (void)arg; errno = ENOSYS; return -1; }
int execv(const char *path, char *const argv[])
{ (void)path; (void)argv; errno = ENOSYS; return -1; }
int execvp(const char *file, char *const argv[])
{ (void)file; (void)argv; errno = ENOSYS; return -1; }
int _execve(const char *path, char *const argv[], char *const envp[])
{ (void)path; (void)argv; (void)envp; errno = ENOSYS; return -1; }

int system(const char *cmd) { (void)cmd; errno = ENOSYS; return -1; }

FILE *popen(const char *cmd, const char *mode)
{ (void)cmd; (void)mode; errno = ENOSYS; return NULL; }
int pclose(FILE *f) { (void)f; errno = ENOSYS; return -1; }

int pipe(int fds[2]) { (void)fds; errno = ENOSYS; return -1; }

int getppid(void) { return 1; }

/* ---- identity ----
 * One privilege level, one user.  Reporting real == effective for both uid
 * and gid matters: programs that check for a setuid environment (perl does,
 * at startup) must conclude they are not running in one. */
uid_t getuid(void)  { return 0; }
uid_t geteuid(void) { return 0; }
gid_t getgid(void)  { return 0; }
gid_t getegid(void) { return 0; }

int setuid(uid_t uid) { (void)uid; errno = EPERM; return -1; }
int setgid(gid_t gid) { (void)gid; errno = EPERM; return -1; }

char *getlogin(void) { return NULL; }

/* No user or group database to iterate. */
void setpwent(void) {}
void endpwent(void) {}
void setgrent(void) {}
void endgrent(void) {}

/* ---- filesystem ----
 * fesvr's syscall proxy does have mkdirat/unlinkat/renameat, so these could
 * be wired up if a benchmark needed them; none does yet, and a stub that
 * fails is better than one that half works. */
int chdir(const char *path)  { (void)path; errno = ENOSYS; return -1; }
int chroot(const char *path) { (void)path; errno = ENOSYS; return -1; }
int chmod(const char *path, mode_t mode)
{ (void)path; (void)mode; errno = ENOSYS; return -1; }
int chown(const char *path, uid_t uid, gid_t gid)
{ (void)path; (void)uid; (void)gid; errno = ENOSYS; return -1; }
int link(const char *from, const char *to)
{ (void)from; (void)to; errno = ENOSYS; return -1; }
mode_t umask(mode_t mask) { (void)mask; return 022; }
int utime(const char *path, void *times)
{ (void)path; (void)times; errno = ENOSYS; return -1; }

int dup(int fd)  { (void)fd; errno = ENOSYS; return -1; }

/* No dup2 here on purpose: programs configured without HAS_DUP2 supply their
   own (perl 4's util.c does), and defining one as well is a link conflict. */

int ioctl(int fd, unsigned long request, ...)
{ (void)fd; (void)request; errno = ENOTTY; return -1; }

/* ---- signals and time ----
 * There is no process to signal and no job control.  Reporting success with
 * nothing installed is right for a batch workload: no signal can arrive. */
int sigaction(int sig, const struct sigaction *act, struct sigaction *oact)
{
  (void)sig; (void)act;
  /* Zeroed with memset rather than a const struct: this file has to compile
     under the C++ driver too (a C++ port pulls the harness in through g++),
     and an uninitialised const struct is an error there. */
  if (oact)
    memset(oact, 0, sizeof *oact);
  return 0;
}

int sigprocmask(int how, const sigset_t *set, sigset_t *oset)
{
  (void)how; (void)set;
  if (oset)
    *oset = 0;
  return 0;
}

unsigned int alarm(unsigned int seconds) { (void)seconds; return 0; }

/* Nothing else is running, so there is nothing to wait for and no reason to
   burn simulated cycles doing it. */
unsigned int sleep(unsigned int seconds) { (void)seconds; return 0; }

/* ---- polling ---- */
int select(int nfds, fd_set *rfds, fd_set *wfds, fd_set *efds,
           struct timeval *tv)
{ (void)nfds; (void)rfds; (void)wfds; (void)efds; (void)tv; return 0; }

/* ---- sysconf ----
 * Only the clock tick, page size and descriptor limit are ever asked for,
 * and all three have honest answers on this target. */
long sysconf(int name)
{
  switch (name) {
#ifdef _SC_CLK_TCK
  case _SC_CLK_TCK:  return (long)CLOCKS_PER_SEC;
#endif
#ifdef _SC_PAGESIZE
  case _SC_PAGESIZE: return 4096L;
#endif
#ifdef _SC_OPEN_MAX
  case _SC_OPEN_MAX: return 32L;
#endif
  default:           return -1L;
  }
}
