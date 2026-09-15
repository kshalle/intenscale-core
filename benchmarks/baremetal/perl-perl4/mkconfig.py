#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

"""Generate the bare-metal config.h for the perl 4.036 port.

perl 4's config.h is a Configure-generated file, and Configure cannot run on a
target with no shell.  Rather than hand-maintain a 900-line header (or patch
the vendored tree), this script derives ours from the copy that ships in
perl-4.036: it switches off the facilities a bare-metal RISC-V target does not
have and rewrites the handful of values that must change.  Every change is
annotated in the output, so the result is auditable against upstream.

    python3 mkconfig.py            # rewrite ./config.h
"""
import os
import sys

UPSTREAM = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        '../../interpreter-perl/perl-4.036/config.h')

# Facilities the target does not have.  perl 4 has portable fallback paths for
# all of these; the point of turning them off is to select those paths.
OFF = {
    # processes, signals, job control
    'HAS_VFORK': 'no processes',
    'I_VFORK': 'no processes',
    'HAS_WAIT4': 'no processes',
    'HAS_WAITPID': 'no processes',
    'HAS_KILLPG': 'no process groups',
    'HAS_GETPGRP': 'no process groups',
    'HAS_SETPGRP': 'no process groups',
    'HAS_GETPRIORITY': 'no scheduler',
    'HAS_SETPRIORITY': 'no scheduler',
    'CSH': 'no shell to exec',
    # users and groups
    'HAS_GETGROUPS': 'no user database',
    'I_GRP': 'no user database',
    'I_PWD': 'no user database',
    'HAS_CRYPT': 'no libcrypt',
    'HAS_SETEGID': 'no uids',
    'HAS_SETEUID': 'no uids',
    'HAS_SETREGID': 'no uids',
    'HAS_SETREUID': 'no uids',
    'HAS_SETRGID': 'no uids',
    'HAS_SETRUID': 'no uids',
    # sockets and SysV IPC
    'HAS_SOCKET': 'no network stack',
    'HAS_SOCKETPAIR': 'no network stack',
    'I_NETINET_IN': 'no network stack',
    'HAS_HTONL': 'no network stack',
    'HAS_HTONS': 'no network stack',
    'HAS_NTOHL': 'no network stack',
    'HAS_NTOHS': 'no network stack',
    'HAS_MSG': 'no SysV IPC',
    'HAS_MSGCTL': 'no SysV IPC',
    'HAS_MSGGET': 'no SysV IPC',
    'HAS_MSGRCV': 'no SysV IPC',
    'HAS_MSGSND': 'no SysV IPC',
    'HAS_SEM': 'no SysV IPC',
    'HAS_SEMCTL': 'no SysV IPC',
    'HAS_SEMGET': 'no SysV IPC',
    'HAS_SEMOP': 'no SysV IPC',
    'HAS_SHM': 'no SysV IPC',
    'HAS_SHMAT': 'no SysV IPC',
    'HAS_SHMCTL': 'no SysV IPC',
    'HAS_SHMDT': 'no SysV IPC',
    'HAS_SHMGET': 'no SysV IPC',
    'HAS_SYSCALL': 'no syscall(2)',
    # filesystem: fesvr proxies open/read/write/stat, but nothing else
    'HAS_READDIR': 'no directory streams',
    'HAS_REWINDDIR': 'no directory streams',
    'HAS_SEEKDIR': 'no directory streams',
    'HAS_TELLDIR': 'no directory streams',
    'I_DIRENT': 'no directory streams',
    'HAS_MKDIR': 'no mkdir wrapper in newlib',
    'HAS_RMDIR': 'no rmdir wrapper in newlib',
    'HAS_RENAME': 'no rename wrapper in newlib',
    'HAS_TRUNCATE': 'no truncate wrapper in newlib',
    'HAS_SYMLINK': 'no symlinks',
    'HAS_LSTAT': 'no symlinks',
    'HAS_FCHMOD': 'no permissions',
    'HAS_FCHOWN': 'no permissions',
    'HAS_FLOCK': 'no locking',
    'HAS_FCNTL': 'no fcntl wrapper',
    'HAS_SELECT': 'nothing to poll',
    'HAS_DUP2': 'no dup2 wrapper',
    'I_SYSIOCTL': 'no ioctl',
    'I_SYS_FILE': 'no <sys/file.h>',
    'HAS_NDBM': 'no dbm libraries',
    'HAS_ODBM': 'no dbm libraries',
    'I_UTIME': 'no utime',
    # toolchain
    'I_VARARGS': ('modern gcc refuses <varargs.h>; perl has a '
                  'fixed-argument fallback for exactly this case'),
    # perl internals
    'MYMALLOC': "use newlib's allocator over the harness _sbrk",
    'STDSTDIO': "reaches into FILE internals (_ptr/_cnt); newlib's differ",
    'SAFE_BCOPY': 'unused once HAS_BCOPY is off',
    'HAS_BCOPY': 'prefer memcpy',
    'HAS_BCMP': 'prefer memcmp',
    'HAS_BZERO': 'prefer memset',
}

# Values that must change rather than disappear.
NEWVAL = {
    'BYTEORDER': ('#define BYTEORDER 0x1234\t/* riscv64 is little-endian; the '
                  'upstream config.h was generated on a big-endian host */\n'),
    'PRIVLIB': '#define PRIVLIB "."\t/* library path: the frontend server\'s cwd */\n',
    'SCRIPTDIR': '#define SCRIPTDIR "."\t/* no /usr/local on a bare-metal target */\n',
    'BIN': '#define BIN "."\t/* no /usr/local on a bare-metal target */\n',
    'CPPSTDIN': '#define CPPSTDIN "cpp"\t/* only reached by perl -P, which cannot run here */\n',
}


def main():
    out, disabled, changed = [], [], []
    for line in open(UPSTREAM):
        parts = line.split()
        if len(parts) >= 2 and parts[0] == '#define':
            name = parts[1]
            if name in NEWVAL:
                out.append(NEWVAL[name])
                changed.append(name)
                continue
            if name in OFF:
                out.append('/* #define %s -- disabled: %s */\n'
                           % (name, OFF[name]))
                disabled.append(name)
                continue
        out.append(line)

    missing = (set(OFF) | set(NEWVAL)) - set(disabled) - set(changed)
    header = '''/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: GPL-1.0-or-later OR Artistic-1.0
 *
 * Derived from the Configure-generated config.h that ships in perl-4.036
 * (Copyright (C) Larry Wall), by ./mkconfig.py.  It therefore carries perl's
 * own terms, not this suite's licence: the GNU General Public License version
 * 1 or later, or the Artistic License, at your option.
 * See ../../LICENSES/GPL-1.0-or-later.txt and Artistic-1.0.txt.
 */

/* config.h for the bare-metal perl 4.036 port -- GENERATED, do not edit.
 *
 * Regenerate with:  python3 mkconfig.py
 *
 * Derived from the Configure-generated config.h that ships in perl-4.036
 * (July 1992).  %d defines are switched off (each annotated in place with the
 * reason) and %d are given new values: %s.
 * The upstream tree is not touched: this copy is force-included ahead of it
 * (see the Makefile), and both files share the "#ifndef config_h" guard, so
 * the upstream one then expands to nothing.
 *
 * Switching these off is the same trimming any bare-metal perl needs: no
 * /etc/passwd lookups, no setuid behaviour, no processes, no sockets.
 */

''' % (len(disabled), len(changed), ', '.join(sorted(changed)))

    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           'config.h'), 'w') as f:
        f.write(header + ''.join(out))

    print('config.h: %d defines disabled, %d changed' % (len(disabled), len(changed)))
    if missing:
        print('warning: not found in upstream config.h: %s'
              % ', '.join(sorted(missing)), file=sys.stderr)


if __name__ == '__main__':
    main()
