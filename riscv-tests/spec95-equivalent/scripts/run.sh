#!/bin/bash
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause
#
# run.sh PORT WORKLOAD NHARTS EMULATOR OUTDIR [MAXCYCLES]      (VERBOSE=1 for +verbose)
#
# Runs build/PORT-WORKLOAD-NHARTS.riscv under the RTL emulator in OUTDIR and
# writes OUTDIR/PORT.verdict, whose first line is
#   RESULT: CORRECT     only if all of these hold:
#     - the emulator passed: all harts exited and parked (tohost_exit in
#       riscv-tests/common/syscalls.c). It is run with -c so it prints
#       *** PASSED *** (without -c or +verbose it prints nothing on success);
#       a failure -- tohost fail code, timeout -- always prints *** FAILED ***.
#     - every one of the NHARTS harts printed its R_<hart> line with exit = 0,
#       i.e. ran its copy of the program to completion
#     - every copy's output matches the native reference
#       (reference/WORKLOAD/, checked by compare.py)
#   RESULT: INCORRECT   otherwise, with the reason.
# The R_<hart> lines (copy_mcycle / copy_minstret: that hart's cycles and
# instructions from program entry to exit) are the rate measurement.
#
# VERBOSE=1 also passes +verbose (one line per retired instruction, per hart;
# gigabytes for the larger runs) and then additionally requires every hart to
# have committed at program PCs and none to be stuck re-entering trap_entry;
# a watchdog stops such a trap storm early. Exits 0 only for CORRECT.
# REJUDGE=1 re-derives the verdict of an existing OUTDIR without running.
# LOGFILE=<path> puts the emulator's console output (DRAMSim3's messages,
# image loading, PASSED/FAILED, the R_ lines, the +verbose trace) there
# instead of OUTDIR/PORT.log; it is written as the run goes (tail -f it).
# DRAMSim3's statistics files (dramsim3epoch.json) go to OUTDIR, the
# emulator's working directory.
PORT=$1; WL=$2; NH=$3; EMU=$4; OUT=$5; MAXC=${6:-5000000000}
[ -n "$EMU" ] && [ -n "$OUT" ] || { echo "usage: $0 PORT WORKLOAD NHARTS EMULATOR OUTDIR [MAXCYCLES]" >&2; exit 2; }
SCRIPTS=$(cd "$(dirname "$0")" && pwd); SPEC=$(dirname "$SCRIPTS")
BIN=$SPEC/build/$PORT-$WL-$NH.riscv
[ -f "$BIN" ] || { echo "no $BIN -- build it first (make -C $SPEC $PORT WORKLOAD=$WL NHARTS=$NH)" >&2; exit 2; }
EMU=$(cd "$(dirname "$EMU")" && pwd)/$(basename "$EMU")
[ -d /opt/riscv-native/bin ] && export PATH=/opt/riscv-native/bin:$PATH
: "${RISCV_PREFIX:=riscv64-unknown-elf-}"

LOG=${LOGFILE:-$OUT/$PORT.log}; V=$OUT/$PORT.verdict
case $LOG in /*) ;; *) LOG=$(pwd)/$LOG ;; esac
if [ "${REJUDGE:-0}" = 1 ]; then
  [ -f "$LOG" ] || { echo "REJUDGE: no $LOG" >&2; exit 2; }
  cd "$OUT"; START=$(stat -c %Y "$LOG"); END=$START
  [ -f "$OUT/$PORT.wall" ] && read START END < "$OUT/$PORT.wall"
  [ -f "$V.running" ] || { sed -n '/running: pid=/p' "$V" 2>/dev/null > "$V.running"; }
else
rm -rf "$OUT"; mkdir -p "$OUT" "$(dirname "$LOG")"; cd "$OUT" || exit 2
# XLISP loads init.lsp from its working directory; fesvr resolves it on the
# host side, relative to the emulator's working directory, i.e. OUTDIR.
[ "$PORT" = xlisp ] && cp $SPEC/benchmarks/interpreter-xlisp/xlisp-plus/lsp/init.lsp .

TRAP=$(${RISCV_PREFIX}nm "$BIN" | awk '$3=="trap_entry"{print substr($1,7)}')
VFLAG=""; [ "${VERBOSE:-0}" = 1 ] && VFLAG=+verbose
START=$(date +%s)
"$EMU" -c +max-cycles=$MAXC $VFLAG "$BIN" > "$LOG" 2>&1 &
EPID=$!
echo "$PORT $WL NHARTS=$NH running: pid=$EPID started=$(date '+%F %T') emulator=$(basename $EMU)" > "$V.running"
while kill -0 $EPID 2>/dev/null; do
  sleep 60
  if [ -n "$VFLAG" ]; then
    n=$(tail -c 4000000 "$LOG" | grep -c " $TRAP ")
    if [ "$n" -gt 2000 ]; then
      echo "watchdog: trap storm ($n trap_entry commits in the log tail), stopping" >> "$V.running"
      kill $EPID; sleep 2; kill -9 $EPID 2>/dev/null
    fi
  fi
done
wait $EPID 2>/dev/null
echo $? > "$OUT/$PORT.exitcode"
END=$(date +%s)
echo "$START $END" > "$OUT/$PORT.wall"
fi

EMU_RES=$(grep -aoE '\*\*\* (PASSED|FAILED) \*\*\*.*' "$LOG" | tail -1)
grep -aE '^R_[0-9]+: copy_mcycle' "$LOG" | sort -t_ -k2 -n > "$OUT/$PORT.copies"
NDONE=$(grep -c ' exit = 0$' "$OUT/$PORT.copies")
python3 $SCRIPTS/compare.py $PORT $WL "$OUT" $NH > "$OUT/$PORT.compare" 2>&1; CMP=$?
WHY=""
ERC=$(cat "$OUT/$PORT.exitcode" 2>/dev/null)
if [[ "$EMU_RES" == *FAILED* ]]; then WHY="$WHY emulator: $EMU_RES;"
elif [[ "$EMU_RES" != *PASSED* ]] && [ "${ERC:-0}" != 0 ]; then WHY="$WHY emulator exited $ERC without a PASSED line;"
fi
[ "$NDONE" -eq "$NH" ] || WHY="$WHY only $NDONE/$NH harts exited 0;"
[ $CMP -eq 0 ] || WHY="$WHY output: $(tail -1 $OUT/$PORT.compare);"
if [ -n "$VFLAG" ]; then
  awk -v T=" $TRAP " '/^C[0-9]+_[0-9]+: / { h=$1; n[h]++; if ($3 ~ /^0020/) p[h]++; if (index($0,T)) t[h]++ }
    END { for (h in n) printf "%s total=%d prog=%d trap_entry=%d\n", h, n[h], p[h]+0, t[h]+0 }' "$LOG" | sort > "$OUT/$PORT.harts"
  HP=$(awk '{split($3,a,"="); if (a[2]>0) c++} END{print c+0}' "$OUT/$PORT.harts")
  TMAX=$(awk '{split($4,a,"="); if (a[2]>m) m=a[2]} END{print m+0}' "$OUT/$PORT.harts")
  [ "$HP" -eq "$NH" ] || WHY="$WHY only $HP/$NH harts reached program code;"
  [ "$TMAX" -le 16 ] || WHY="$WHY trap storm (trap_entry x$TMAX);"
fi
if [ -z "$WHY" ]; then RES=CORRECT; else RES=INCORRECT; fi
MEANC=$(awk '{s+=$4} END{if (NR) printf "%.0f", s/NR}' "$OUT/$PORT.copies")
# Per copy: IPC = copy_minstret / copy_mcycle (tile clock). Throughput: all
# copies' instructions over the longest copy's cycles, i.e. the aggregate IPC
# of the harts running concurrently (a lower bound on span: harts start their
# copies a little apart).
awk '{ c=$4; i=$7; if (c>0) printf "%s ipc = %.4f cpi = %.3f\n", $1, i/c, c/i }' "$OUT/$PORT.copies" > "$OUT/$PORT.ipc"
PERF=$(awk '{ c=$4; i=$7; n++; si+=i; sc+=c; if (c>m) m=c; if (c>0) sipc+=i/c }
  END { if (n && m>0 && si>0) printf "perf: IPC per copy (mean) = %.4f  CPI per copy = %.3f  throughput IPC (%d copies) = %.4f  [instrs %d, longest copy %d cycles]",
          sipc/n, sc/si, n, si/m, si, m; else print "perf: n/a (no R_ lines)" }' "$OUT/$PORT.copies")
# Wall-clock time of the emulator run and the simulation speed it implies
# (raw testbench cycles, as in "Completed after N cycles", per second).
WALL=$((END-START)); RAWC=$(grep -aoE 'Completed after [0-9]+ cycles' "$LOG" | tail -1 | grep -oE '[0-9]+')
TIME=$(printf "time: wall = %dh%02dm%02ds (%d s)" $((WALL/3600)) $((WALL%3600/60)) $((WALL%60)) $WALL)
[ -n "$RAWC" ] && [ $WALL -gt 0 ] && TIME="$TIME  emulator = $RAWC raw cycles  sim speed = $((RAWC/WALL)) cycles/s"
{
  echo "RESULT: $RES  $PORT $WL NHARTS=$NH${WHY:+ --$WHY}"
  echo "emulator: ${EMU_RES:-<no PASSED/FAILED line: run without -c; no FAILED line and exit status ${ERC:-not recorded}>}"
  echo "$PERF"
  echo "$TIME"
  echo "copies exited 0: $NDONE/$NH   mean copy_mcycle: ${MEANC:-n/a}   wall: $((WALL/60)) min"
  echo "emulator log: $LOG"
  echo "--- per-copy counters (R_<hart>: cycles and instructions from program entry to exit):"
  cat "$OUT/$PORT.copies"
  echo "--- per-copy IPC / CPI (tile clock; the emulator's raw cycle count is 2x):"
  cat "$OUT/$PORT.ipc"
  echo "--- per-copy output check against reference/$WL:"
  cat "$OUT/$PORT.compare"
  [ -f "$OUT/$PORT.harts" ] && { echo "--- per-hart commits (+verbose):"; cat "$OUT/$PORT.harts"; }
  cat "$V.running"
} > "$V"
rm -f "$V.running"
head -1 "$V"
[ $RES = CORRECT ]
