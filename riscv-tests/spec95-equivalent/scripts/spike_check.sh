#!/bin/bash
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause
#
# spike_check.sh PORT WORKLOAD NHARTS
#
# Functional check on spike (the ISA simulator: seconds to minutes instead of
# hours): builds PORT for WORKLOAD/NHARTS with riscv-tests' -DSPIKE runtime
# (spike has no IntenCore delay CSR), runs NHARTS harts over the RTL's DRAM
# map (0x20000000, 8 GiB) and checks every copy's output against
# reference/WORKLOAD. Checks the program, the harness and the rate mechanism
# -- not the RTL. Use it before committing hours of RTL time to a new size.
PORT=$1; WL=$2; NH=$3
SCRIPTS=$(cd "$(dirname "$0")" && pwd); SPEC=$(dirname "$SCRIPTS")
[ -d /opt/riscv-native/bin ] && export PATH=/opt/riscv-native/bin:$PATH
OUT=$SPEC/build/spike/$PORT-$WL-$NH; LOGB=$SPEC/build/spike/$PORT-$WL-$NH.build.log
mkdir -p $SPEC/build/spike
BUILD_TAG=spike RATE_RUNTIME_CFLAGS=-DSPIKE $SCRIPTS/build.sh $PORT $WL $NH > $LOGB 2>&1 \
  || { echo "$PORT $WL NHARTS=$NH: BUILD_FAILED ($LOGB)"; exit 1; }
rm -rf $OUT; mkdir -p $OUT; cd $OUT
[ "$PORT" = xlisp ] && cp $SPEC/benchmarks/interpreter-xlisp/xlisp-plus/lsp/init.lsp .
timeout 7200 spike --isa=rv64imafdc -p$NH -m0x20000000:0x200000000 $SPEC/build/$PORT-$WL-$NH-spike.riscv > console.log 2>&1
RC=$?
python3 $SCRIPTS/compare.py $PORT $WL $OUT $NH > compare.txt 2>&1; CMP=$?
NR=$(grep -c '^R_[0-9]*: copy_mcycle.* exit = 0$' console.log)
echo "$PORT $WL NHARTS=$NH: spike_exit=$RC copies_exited_0=$NR/$NH outputs: $(tail -1 compare.txt)"
[ $RC -eq 0 ] && [ $CMP -eq 0 ] && [ "$NR" -eq "$NH" ]
