#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause
"""profile.py BINARY < spike-commit-log

Instruction profile of a spike run (spike -l), by function: reads spike's
per-instruction log on stdin and prints each function's share of the retired
instructions. Used to size workloads (how much of a run is the benchmark's
kernel versus start-up), see README.md.
"""
import bisect, collections, os, subprocess, sys

binary = sys.argv[1]
prefix = os.environ.get("RISCV_PREFIX", "riscv64-unknown-elf-")
syms = []
for line in subprocess.run([prefix + "nm", "-n", binary], capture_output=True, text=True).stdout.splitlines():
    f = line.split()
    # functions only: skip local labels (.L*, possibly h<N>_-prefixed)
    if len(f) == 3 and f[1] in "tTwW" and not f[2].split("_", 1)[-1].startswith(".L") and not f[2].startswith(".L"):
        syms.append((int(f[0], 16), f[2]))
syms.sort()
addrs = [a for a, _ in syms]
count = collections.Counter()
total = 0
for line in sys.stdin:
    # "core   0: 0x0000000020001234 (0x00001141) c.addi sp, -16"
    i = line.find(": 0x")
    if i < 0:
        continue
    try:
        pc = int(line[i + 2:i + 20], 16)
    except ValueError:
        continue
    if pc < 0x20000000:
        continue
    k = bisect.bisect_right(addrs, pc) - 1
    count[syms[k][1] if k >= 0 else "?"] += 1
    total += 1
print("total program instructions: %d" % total)
for name, n in count.most_common(int(os.environ.get("TOP", "25"))):
    print("%6.2f%%  %10d  %s" % (100.0 * n / total, n, name))
