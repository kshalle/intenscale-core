#!/bin/sh
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Run a bare-metal benchmark on the rocket-chip Verilator emulator.
#
#   scripts/run-verilator.sh <binary.riscv> [target args...]
#   scripts/run-verilator.sh li-xlisp/xlisp.riscv -b < li-xlisp/workload/xlisp-bench-tiny.lsp
#
# Environment:
#   BM_EMULATOR   path to the emulator binary
#   BM_FESVR_LIB  directory holding libfesvr.so (the emulator links it
#                 dynamically and does not record an rpath)
#   BM_DRAMSIM3   DRAMSim3 .ini.  Only needed for an emulator built in another
#                 tree (the path is compiled in); a binary rebuilt here finds
#                 its own.  Passing it is harmless either way.
#   BM_RAM        simulated DRAM size (default 256MB; must exceed the heap
#                 ceiling the ports are built with, BM_HEAP_MB)
#
# Build the port with BM_RTL=1 first (make BM_RTL=1): an HTIF round trip costs
# about 151 k simulated cycles here, so a line-buffered console can dominate
# everything else.
set -e

# Find the rocket-chip checkout by walking up from this script rather than by
# counting "../" -- this tree has been rearranged once (benchmarks/ moved
# under open_source/), and a hard-coded depth breaks silently when it is.
# Override with ROCKET=<path>.
bm_find_rocket() {
    d=$(cd "$(dirname "$0")" && pwd)
    while [ "$d" != / ]; do
        for c in "$d/i-rocket-chip" "$d/open_source/i-rocket-chip"; do
            [ -d "$c" ] && { echo "$c"; return 0; }
        done
        d=$(dirname "$d")
    done
    return 1
}
ROCKET=${ROCKET:-$(bm_find_rocket)}
# Prefer the relaxed-watchdog build when it exists.  The design carries two
# debug watchdogs (NBDcache.scala:322 and :623) that fire when an MSHR sits
# unchanged for 16,384 cycles -- shorter than one HTIF round trip -- so any
# benchmark doing frontend-server I/O trips them on the standard build.  The
# relaxed binary raises just those two thresholds and leaves every other
# assertion live.  See PORT-STATUS.md.  Override with BM_EMULATOR.
if [ -z "$BM_EMULATOR" ] && [ -x "$ROCKET/emulator/emulator-relaxed-watchdog" ]; then
    BM_EMULATOR=$ROCKET/emulator/emulator-relaxed-watchdog
fi
BM_EMULATOR=${BM_EMULATOR:-$(ls "$ROCKET"/emulator/emulator-freechips.rocketchip.system-*[!t] 2>/dev/null | head -1)}
BM_FESVR_LIB=${BM_FESVR_LIB:-$ROCKET/riscv-tools/lib}
BM_DRAMSIM3=${BM_DRAMSIM3:-$ROCKET/DRAMSIM3/configs/DDR4_8Gb_x8_3200.ini}
BM_RAM=${BM_RAM:-256MB}

[ -x "$BM_EMULATOR" ] || { echo "no emulator: set BM_EMULATOR" >&2; exit 1; }
echo "emulator: $BM_EMULATOR" >&2
[ $# -ge 1 ] || { echo "usage: $0 <binary.riscv> [args...]" >&2; exit 1; }

BIN=$1; shift

# -b takes a size argument; --ram-size=SIZE is not accepted by this build.
# stdbuf -oL: the emulator lingers after the target exits (its JTAG
# remote-bitbang listener keeps the process alive), so a run that is killed by
# an outer timeout loses whatever is still sitting in the emulator's own
# stdout buffer -- which is the tail of the benchmark's output.  fesvr's
# "*** PASSED ***" goes to stderr and survives, which makes the loss look like
# the target stopped early when it did not.  Line-buffering costs nothing here
# and removes the whole failure mode.
LD_LIBRARY_PATH=$BM_FESVR_LIB:$LD_LIBRARY_PATH \
exec stdbuf -oL "$BM_EMULATOR" -y "$BM_DRAMSIM3" -c -b "$BM_RAM" "$BIN" "$@"
