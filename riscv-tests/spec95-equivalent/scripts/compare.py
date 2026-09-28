#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause
"""compare.py PORT WORKLOAD OUTDIR NHARTS

Checks every copy of a spec95-equivalent rate run against the port's native (x86 host)
reference output in ../reference/WORKLOAD/. A copy's console output is
OUTDIR/hartNN.err followed by OUTDIR/hartNN.out; bsmbench reports through its
-o file (bsmbench-hartNN.log), gcc-cc1's real output is the assembly it writes
(gcc-cc1-out-hart<N>.s). Only differences the port's PORT-NOTES document
as expected between compilers/libms are normalized away; everything else must
match exactly. Prints one line per copy and exits non-zero if any copy fails.
"""
import os, re, sys

PORT, WL, OUT, NH = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
REF = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "reference", WL)


def read(path):
    try:
        with open(path, errors="replace") as f:
            return f.read()
    except FileNotFoundError:
        return None


def num_close(a, b, rel, absolute=0.0):
    a, b = float(a), float(b)
    return abs(a - b) <= max(absolute, rel * max(abs(a), abs(b)))


def normalize(text, notes):
    lines = text.splitlines()
    out = []
    for l in lines:
        if PORT == "libcint":
            if l.startswith("[libcint] longdouble_fallback_calls="):
                continue                    # RTL-only diagnostic (bm_longdouble.c)
            m = re.match(r"\[libcint\] sum=(\S+)$", l)
            if m:                           # cancels to ~0; digits compiler-dependent
                ok = abs(float(m.group(1))) < 1e-12
                notes.append("sum=%s %s" % (m.group(1), "~0" if ok else "NOT ~0"))
                l = "[libcint] sum=~0" if ok else l
        elif PORT == "miniweather":
            if l.startswith("CPU Time:"):
                continue                    # wall clock
            m = re.match(r"d_mass:\s+(\S+)$", l)
            if m:                           # rounding noise around 0
                ok = abs(float(m.group(1))) < 1e-13
                notes.append("d_mass=%s %s" % (m.group(1), "~0" if ok else "NOT ~0"))
                l = "d_mass: ~0" if ok else l
            m = re.match(r"d_te:\s+(\S+)$", l)
            if m:
                notes.append("d_te=%s" % m.group(1))
                l = "d_te: <%s>" % m.group(1)
        elif PORT == "bsmbench":
            l = re.sub(r"completed in [0-9.]+ seconds", "completed in <t> seconds", l)
            l = re.sub(r"output file \[.*\]", "output file [<out>]", l)
            l = re.sub(r"input file \[.*/([^/\]]*)\]", r"input file [\1]", l)
            m = re.search(r"Precision test passed: delta = (\S+)", l)
            if m:
                notes.append("delta=%s" % m.group(1))
                l = "Precision test passed: delta = <d>"
            m = re.search(r"Dphi: total iterations = (\d+)", l)
            if m:
                notes.append("iterations=%s" % m.group(1))
        out.append(l)
    return out


def main():
    if PORT == "gcc-cc1":
        ref = read(os.path.join(REF, "gcc-cc1.s"))
    else:
        ref = read(os.path.join(REF, PORT + ".stdout"))
    if ref is None:
        print("no reference for %s in %s" % (PORT, REF)); return 2
    ref_notes = []
    ref_n = normalize(ref, ref_notes)
    bad = 0
    for h in range(NH):
        hh = "%02d" % h
        if PORT == "gcc-cc1":
            got = read(os.path.join(OUT, "gcc-cc1-out-hart%d.s" % h))
            err = (read(os.path.join(OUT, "hart%s.err" % hh)) or "") + (read(os.path.join(OUT, "hart%s.out" % hh)) or "")
            extra = " console=%dB" % len(err)
        elif PORT == "bsmbench":
            got = read(os.path.join(OUT, "bsmbench-hart%s.log" % hh))
            extra = ""
        else:
            e = read(os.path.join(OUT, "hart%s.err" % hh))
            o = read(os.path.join(OUT, "hart%s.out" % hh))
            got = None if (e is None and o is None) else (e or "") + (o or "")
            extra = ""
        if got is None:
            print("hart %s: MISSING output" % hh); bad += 1; continue
        notes = []
        got_n = normalize(got, notes)
        ok = got_n == ref_n
        if PORT == "miniweather" and ok is False:
            # d_te must agree numerically (libm/association differences only)
            g = [re.match(r"d_te: <(\S+)>", l) for l in got_n]
            r = [re.match(r"d_te: <(\S+)>", l) for l in ref_n]
            gl = [l for l in got_n if not l.startswith("d_te:")]
            rl = [l for l in ref_n if not l.startswith("d_te:")]
            gv = [m.group(1) for m in g if m]; rv = [m.group(1) for m in r if m]
            if gl == rl and len(gv) == len(rv) == 1 and num_close(gv[0], rv[0], 1e-3):
                ok = True; notes.append("d_te within 1e-3 of ref %s" % rv[0])
        if ok:
            print("hart %s: MATCH%s %s" % (hh, extra, " ".join(notes)))
        else:
            bad += 1
            import difflib
            d = list(difflib.unified_diff(ref_n, got_n, "reference", "hart" + hh, n=0, lineterm=""))
            print("hart %s: MISMATCH%s %s" % (hh, extra, " ".join(notes)))
            for l in d[:12]:
                print("    " + l)
    print("%s: %d/%d copies match the reference%s" % (PORT, NH - bad, NH, "" if not ref_notes else "  (ref: " + " ".join(ref_notes) + ")"))
    return 1 if bad else 0


sys.exit(main())
