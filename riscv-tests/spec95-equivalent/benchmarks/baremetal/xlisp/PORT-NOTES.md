# xlisp -- the Lisp interpreter workload, bare metal

Upstream: `../../interpreter-xlisp/xlisp-plus` (XLISP-PLUS, MIT).  **Not edited.**

## What this port adds

| File | Why |
|---|---|
| `shim/sys/termios.h` | newlib's `<termios.h>` for riscv64-unknown-elf includes a `<sys/termios.h>` that the target does not ship.  That one missing header is the only thing that stopped `linuxstuff.c` compiling; the other 29 translation units built unmodified on the first attempt. |
| `bm_os_stubs.c` | The terminal and process calls XLISP's Linux OS layer makes: `tcgetattr`/`tcsetattr` (succeed, ignored), `fork`/`wait`/`execl`/`system`/`popen`/`pclose` (fail as they would on a host that could not fork), `sigaction`/`sigprocmask` (succeed with nothing installed), `select` (nothing ready), `sysconf` (real answers for clock tick and page size). |
| `workload/xlisp-bench-{tiny,small,ref}.lsp` | The workload, written for this suite: tak and deriv (classic Lisp benchmark kernels), plus a naive insertion sort and reverse/mapcar churn to keep the collector working. |

## Running

    make run-spike SIZE=tiny
    ../scripts/run-verilator.sh xlisp.riscv -b < workload/xlisp-bench-tiny.lsp

The script arrives on **stdin**, which fesvr proxies from the host.
`-b` selects XLISP's batch mode.
`init.lsp` is copied next to the binary at build time from the upstream `lsp/`
directory; XLISP loads it from the working directory at startup.

## Things that bit, and would bite the next interpreter port

- **`isatty` must report false.**  With a terminal reported present, XLISP
  sets up raw-mode input and blocks forever.  The harness now answers
  honestly for all descriptors.
- **`STSZ` must match the real stack.**  Upstream's Linux makefile declares
  `-DSTSZ=8192000`; XLISP derives its own recursion limits from that number,
  and `htif.ld` reserves 24 KiB.  The Makefile passes `$(BM_STACK_BYTES)`, and
  `bm.mk` overrides the linker script's stack size to match.
- **REPL echo is expensive.**  Every value the REPL prints is an HTIF round
  trip, so the workload keeps its driver inside a single `progn`.

## The Linux build

`make BM_ENV=linux` builds the same 29 upstream translation units plus
`linuxstuff.c`, against glibc.  Two of this port's own pieces then drop out:
`shim/sys/termios.h` and the `tcgetattr`/`tcsetattr` stubs exist only because
newlib's `riscv64-unknown-elf` target omits that header, and glibc has both.
`shim/` also has to leave the include path there, or it would shadow the real
header.  `STSZ` follows the stack the environment actually provides -- 1 MiB
bare metal, just under the default 8 MiB thread stack on Linux.

One output difference, and it is cosmetic: XLISP announces
`; loading "init.lsp"` on `*debug-io*`, which is fd 2.  fesvr routes the
target's fd 2 to the host's *stdout*, so bare metal shows it on the same
console as everything else while Linux keeps it on stderr.  Both builds do
load `init.lsp`, and everything after that line is identical.
