/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Bare-metal <sys/termios.h>.
 *
 * newlib ships a <termios.h> that includes <sys/termios.h>, which does not
 * exist for the riscv64-unknown-elf target -- that single missing header is
 * the only thing that stops XLISP-PLUS's Linux OS layer (linuxstuff.c) from
 * compiling for a bare-metal target.  29 of its 30 translation units build
 * unmodified.
 *
 * There is no terminal on the target, so the struct is declared for the sake
 * of the fields linuxstuff.c touches and the calls are no-ops implemented in
 * bm_os_stubs.c.  Console I/O still works: it goes through stdio, which the
 * harness routes over HTIF.
 */
#ifndef BM_SYS_TERMIOS_H
#define BM_SYS_TERMIOS_H

#define NCCS 32

typedef unsigned char cc_t;
typedef unsigned int  speed_t;
typedef unsigned int  tcflag_t;

struct termios {
  tcflag_t c_iflag;
  tcflag_t c_oflag;
  tcflag_t c_cflag;
  tcflag_t c_lflag;
  cc_t     c_line;
  cc_t     c_cc[NCCS];
  speed_t  c_ispeed;
  speed_t  c_ospeed;
};

/* c_lflag bits used by the XLISP tty code */
#define ISIG    0000001
#define ICANON  0000002
#define ECHO    0000010
#define ECHOE   0000020
#define ECHOK   0000040
#define ECHONL  0000100
#define NOFLSH  0000200

/* c_cc indices used by the XLISP tty code */
#define VMIN    6
#define VTIME   5

#define TCSANOW   0
#define TCSADRAIN 1
#define TCSAFLUSH 2

int tcgetattr(int fd, struct termios *t);
int tcsetattr(int fd, int action, const struct termios *t);

#endif /* BM_SYS_TERMIOS_H */
