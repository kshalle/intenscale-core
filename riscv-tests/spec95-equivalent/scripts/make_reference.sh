#!/bin/bash
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause
#
# make_reference.sh PORT WORKLOAD
#
# Builds PORT natively for the host (x86-64 Linux, host gcc) with exactly the
# same translation units, macros and WORKLOAD sizes as the RISC-V build
# (workloads.sh), runs it the way the bare-metal entry point
# (benchmarks/baremetal/<port>/bm_rate_thread.c) calls main(), and writes the
# output to reference/WORKLOAD/PORT.stdout (gcc-cc1: reference/WORKLOAD/
# gcc-cc1.s, the assembly it produces). compare.py checks every copy of an
# RTL or spike run against it. -ffast-math is left out on purpose (the
# ports' own Linux builds don't use it); compare.py only tolerates the
# differences the ports' PORT-NOTES document as compiler/libm dependent.
set -e
PORT=$1; WL=$2
[ -n "$PORT" ] && [ -n "$WL" ] || { echo "usage: $0 PORT WORKLOAD" >&2; exit 2; }
SCRIPTS=$(cd "$(dirname "$0")" && pwd); SPEC=$(dirname "$SCRIPTS")
BENCH=$SPEC/benchmarks; BM=$BENCH/baremetal
. $SCRIPTS/workloads.sh
workload_params || exit 2
REF=$SPEC/reference/$WL; mkdir -p $REF
D=$SPEC/build/native/$PORT-$WL; rm -rf $D; mkdir -p $D/src; cd $D
HOSTCC=${HOSTCC:-gcc}; HOSTCXX=${HOSTCXX:-g++}
# Makefrag-baremetal's RISCV_GCC_OPTS minus the RISC-V-only options and
# -ffast-math.
COMMON="-DPREALLOCATE=1 -DNHARTS=1 -std=gnu99 -O2 -fno-common -fno-builtin-printf"
# Same symlink layout as the RISC-V build, so quoted #includes resolve the
# same way; the bare-metal entry point is left out, and so are the stubs
# glibc provides itself (xlisp's terminal calls, libcint's long-double
# fallback). perl's bm_os_stubs.c stays: it supplies sys_errlist/sys_nerr,
# which current glibc no longer exports.
awk -v p=$PORT '$1==p && $2 ~ /\.c$/ {print $2, $3}' $SCRIPTS/port-layout.txt | while read -r n t; do
  case $PORT:$n in *:bm_rate_thread.c|xlisp:bm_os_stubs.c|libcint:bm_longdouble.c) continue ;; esac
  ln -s "${t/@B@/$BENCH}" src/$n
done
case $PORT in
  libcint)
    P=$BM/libcint; U=$BENCH/qchem-libcint/libcint
    $HOSTCC $COMMON -I$P/gen -I$U/include -I$U/src -DBM_NATM=$CINT_NATM -DBM_WITH_D=$CINT_WITH_D -DBM_LINUX -o prog src/*.c -lm
    ARGS=() ;;
  miniweather)
    P=$BM/miniweather; U=$BENCH/weather-miniweather/miniWeather/c
    $HOSTCXX -I$P/shim -I$BM/common -D_NX=$MW_NX -D_NZ=$MW_NZ -D_SIM_TIME=$MW_ST -D_OUT_FREQ=-1 \
      -D_DATA_SPEC=DATA_SPEC_THERMAL -DNO_INFORM -DBM_SIZE_NAME=\"$WL\" -DPREALLOCATE=1 -DNHARTS=1 -O2 -fno-common \
      -o prog $U/miniWeather_serial.cpp
    ARGS=() ;;
  perl-perl4)
    P=$BM/perl-perl4; U=$BENCH/interpreter-perl/perl-4.036
    $HOSTCC $COMMON -include $P/config.h -I$P -I$U -w -DI_TIME -o prog src/*.c -lm
    ARGS=($WORKLOAD_FILE $PERL_HI $PERL_LO) ;;
  bsmbench)
    P=$BM/bsmbench; U=$BENCH/lattice-bsmbench/BSMBench/BSMBench
    $HOSTCC $COMMON -I$P/shim -I- -I$U/Include -DANTIPERIODIC_BC_T -DUPDATE_EO -DNDEBUG -DREPR_ADJOINT \
      -DREPR_NAME=\"REPR_ADJOINT\" -DBM_CPU_HZ=$BSM_HZ -DBM_VTIME_STEP=$BSM_VT \
      -o prog src/*.c $P/bm_vtime.c -Wl,--wrap=time -lm 2>/dev/null
    ARGS=(-i $WORKLOAD_FILE -o /dev/stdout) ;;
  xlisp)
    U=$BENCH/interpreter-xlisp/xlisp-plus/sources
    $HOSTCC $COMMON -DLINUX -DSTSZ=0x40000 -I$U -w -o prog src/*.c -lm
    cp $BENCH/interpreter-xlisp/xlisp-plus/lsp/init.lsp .
    ARGS=(-b) ;;
  gcc-cc1)
    P=$BM/gcc-cc1; U=$BENCH/compiler-gcc/gcc-2.5.8
    $HOSTCC $COMMON -w -fcommon -I$P/hostcfg -I$P/gen/src -I$P/gen -I$U -I$U/config -DIN_GCC -o prog src/*.c -lm
    ARGS=($WORKLOAD_FILE -quiet -O -o $D/out.s) ;;
esac
# argv[0] as the bare-metal entry point sets it; empty environment (bare
# metal passes none); stdout+stderr unbuffered so the file is in print order
# (bare metal: each copy's stderr then stdout, see compare.py).
A0=$PORT; [ $PORT = perl-perl4 ] && A0=perl; [ $PORT = gcc-cc1 ] && A0=cc1
IN=/dev/null; [ $PORT = xlisp ] && IN=$WORKLOAD_FILE
env -i PATH=/usr/bin:/bin stdbuf -o0 -e0 bash -c 'exec -a "$0" "$@"' "$A0" ./prog "${ARGS[@]}" < $IN > $REF/$PORT.stdout 2>&1 \
  || { echo "$PORT $WL: native run failed, see $REF/$PORT.stdout" >&2; exit 1; }
[ $PORT = gcc-cc1 ] && cp $D/out.s $REF/gcc-cc1.s
echo "$PORT $WL: reference $REF/$PORT.stdout ($(wc -l < $REF/$PORT.stdout) lines)$([ $PORT = gcc-cc1 ] && echo ", gcc-cc1.s $(wc -l < $REF/gcc-cc1.s) lines")"
