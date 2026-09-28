/* Process-control backends newlib pulls in for perl (via raise()/abort()
   paths) that the harness does not provide. There is no process or signal
   facility on this target; each fails the way the real call would on a host
   that refused it. _exit comes from riscv-tests/common/syscalls.c. */
#include <errno.h>
int _kill(int pid, int sig) { (void)pid; (void)sig; errno = ENOSYS; return -1; }
int _getpid(void) { return 1; }
