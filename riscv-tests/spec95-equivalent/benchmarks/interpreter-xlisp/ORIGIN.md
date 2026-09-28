# interpreter-xlisp — XLISP-PLUS

The Lisp interpreter workload: a compact tree-walking interpreter with a
mark-and-sweep collector.

## What is vendored here

`xlisp-plus/` — XLISP-PLUS, the maintained descendant of David Betz's
XLISP 2.1 (Tom Almy and others; published to GitHub by Blake McBride with
permission from both Almy and Betz).

- URL: https://github.com/blakemcbride/XLISP-PLUS
- Commit: `81bc021f987d1367fac007d64fd46cdf236202b5` (2026-08-21)
- Retrieved: 2026-09-07

## Licence

**MIT.**  Full text in `xlisp-plus/LICENSE.txt`, with the copyright chain
spelled out: portions © 1983-1988 David Michael Betz; XLISP-STAT portions
© 1988 Luke Tierney; `UNIXSTUF.C` © 1989 Hewlett-Packard (Niels Mayer); other
contributions released without restriction by Almy, Pettersson, Holtz,
Greenblatt, Whedbee, McBride, Sarasúa, Yadlowsky and Zidlicky.

This matters: older XLISP notices are "freely distributable" but not
OSI-grade, and the XLISP-PLUS project page notes that some contributors
historically reserved rights for commercial use.  The MIT relicence in this
repository is the clean path — keep `LICENSE.txt` intact and attribution is
satisfied.  The HP-derived file is not compiled by the port here (it builds
`linuxstuff.c` and `unixprim.c`).

## Why this implementation

Tree-walking interpretation is a distinctive machine workload: deep non-tail
recursion, cons-cell allocation against a collector, symbol lookup, and
pointer chasing with deliberately poor locality.  XLISP-PLUS is small enough
to run in a simulator and needs almost no porting — 29 of its 30 translation
units compile for a bare-metal target unmodified.

Considered and not used: Betz's later byte-code XLISP
(https://github.com/dbetz/xlisp, also MIT), because the tree-walking
interpreter is the more interesting workload; and larger Common Lisp
implementations, which do not fit a simulator.

## Local modifications

`.git/` and `.github/` removed.

Two upstream documentation binaries were also removed, to keep the release
small.  Both are the XLISP-PLUS reference manual in redistributable formats;
nothing in this suite builds or reads them:

    doc/XLISPPLUS3.pdf    1.1 MiB
    doc/XLISPPLUS3.docx   247 KiB

MIT imposes no obligation to state changes; this is recorded for accuracy, not
to satisfy the licence.  The plain-text documentation in `doc/` -- `read.me`,
`readme.upd` and `doc/Obsolete/` -- is untouched, and covers the same
material.  SHA-256, to restore them from upstream:

    a8fca783e39341bd46124f185109f92d90a776bf117060ad3fe0e1f9bfa95273  XLISPPLUS3.pdf
    b38191b86fc9b407e9bf44e7357d4e4107e251ed3cdfe40d65dacb47ce54bd5a  XLISPPLUS3.docx

All XLISP-PLUS **sources** and the `lsp/` library are upstream and unmodified.

## Notes for benchmark use

The bare-metal port is in `../baremetal/xlisp`.  Its workload is a set of
Lisp kernels written for this suite (`workload/xlisp-bench-*.lsp`), driving
recursion, symbolic differentiation, a naive insertion sort and destructive
list surgery so that garbage collection is part of what gets measured.
