#!/bin/bash
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause
#
# build.sh PORT WORKLOAD NHARTS          (RATE_MODE=process|copies, default process)
#
# Builds one spec95-equivalent port for an NHARTS-hart RTL config in "rate" mode: every
# hart runs the whole program independently, like the NHARTS processes of a
# rate run. WORKLOAD picks the problem size (see workload() below).
# Output: build/PORT-WORKLOAD-NHARTS.riscv (see ../README.md).
#
# The port is compiled as one complete program -- its own code, newlib, libm,
# libgcc and the harness's newlib syscall glue (benchmarks/baremetal/common) --
# and partially linked with every symbol prefixed h0_ (h1_, ... in copies
# mode), so it can be linked beside the unmodified riscv-tests runtime.
#
#   process (default): one copy of the code, shared by all harts; each hart
#     gets its own writable data (program globals, C-library state) through
#     its own page table -- see process_dispatch.c. This is how the processes
#     of one binary share text pages, so instruction caches shared between
#     contexts behave as in a real rate run.
#   copies: NHARTS private copies of the whole program side by side, one per
#     hart. Simpler, but NHARTS times the instruction footprint.
#
# The final link is Makefrag-baremetal's compile_template (link.mk) with the
# unmodified riscv-tests common/crt.S, syscalls.c and test.ld. crt.S's
# per-hart thread_entry(cid, nc) is the dispatcher's, which runs the program
# and then its own exit() (atexit handlers, stdio flush), whose final _exit()
# lands in the runtime's tohost_exit() -- the same all-harts-parked exit as
# every riscv-tests benchmark.
#
# Only these symbols are shared between the program and the runtime:
#   tohost fromhost hostAccessSema  -- the HTIF mailbox (taken under its lock)
#   _exit                            -- defined by the dispatcher
#   __heap_end                       -- end of the image (heap slices start above)
# Any other strong undefined symbol in the program is a build error.
set -e
PORT=$1; WL=$2; NH=$3
[ -n "$PORT" ] && [ -n "$WL" ] && [ -n "$NH" ] || { echo "usage: $0 PORT WORKLOAD NHARTS" >&2; exit 2; }

SCRIPTS=$(cd "$(dirname "$0")" && pwd)
SPEC=$(dirname "$SCRIPTS")                 # riscv-tests/spec95-equivalent
RT=$(dirname "$SPEC")                      # riscv-tests
BENCH=$SPEC/benchmarks
BM=$BENCH/baremetal
CC=$BM/common
STUBS=$SCRIPTS/stubs
BUILD=$SPEC/build
# BUILD_TAG (environment) names a variant build, e.g. spike_check.sh's
# -DSPIKE runtime, so it cannot overwrite the RTL binary.
NAME=$PORT-$WL-$NH${BUILD_TAG:+-$BUILD_TAG}
: "${RISCV_PREFIX:=riscv64-unknown-elf-}"
[ -d /opt/riscv-native/bin ] && export PATH=/opt/riscv-native/bin:$PATH
GCC=${RISCV_PREFIX}gcc; GXX=${RISCV_PREFIX}g++
NM=${RISCV_PREFIX}nm; OBJCOPY=${RISCV_PREFIX}objcopy; OBJDUMP=${RISCV_PREFIX}objdump; READELF=${RISCV_PREFIX}readelf

MODE=${RATE_MODE:-process}
case $MODE in
  process) NCOPY=1 ;;
  copies)  NCOPY=$NH ;;
  *) echo "RATE_MODE must be process or copies" >&2; exit 2 ;;
esac

# Per-hart stack+TLS (crt.S's HART_STACK_SIZE) and heap slice size
# (bm_rate_heap.c). The heap slices start at 0x80000000 (BM_DRAM_BASE),
# inside DRAM (0x20000000 + 27.5 GiB) and clear of the image and the stacks.
if [ "$NH" = 1 ]; then
  STACK="-DHART_STACK_SIZE=0x800000 -DBM_HEAP_MB=512"
else
  STACK="-DHART_STACK_SIZE=0x400000 -DBM_HEAP_MB=512"
fi
# Same code-generation options as Makefrag-baremetal's RISCV_GCC_OPTS.
OPTS="-DPREALLOCATE=1 -DNHARTS=$NH -mcmodel=medany -static -O2 -ffast-math -fno-common -fno-builtin-printf -march=rv64imafdc"
CSTD="-std=gnu99"
LIBS="-lc -lm -lgcc"
EXTRA_SRCS=""; CXX_SRC=""; LDRFLAGS=""

# ---------------------------------------------------------------- workloads
# The size parameters come from workloads.sh (shared with make_reference.sh).
. $SCRIPTS/workloads.sh
workload_params || exit 2
case $PORT in
  bsmbench)
    P=$BM/bsmbench; U=$BENCH/lattice-bsmbench/BSMBench/BSMBench
    # BSMBench sizes its own run by timing itself (doubling the Dphi
    # iteration count until 400 s have passed). Timed by mcycle, the work
    # would depend on how fast the target is, so it uses the port's
    # deterministic clock (bm_vtime.c, as its Linux build does).
    PCFLAGS="-I$P/shim -I- -I$U/Include -DANTIPERIODIC_BC_T -DUPDATE_EO -DNDEBUG -DREPR_ADJOINT -DREPR_NAME=\"REPR_ADJOINT\" -DBM_CPU_HZ=$BSM_HZ -DBM_WORKLOAD_PATH=\"$WORKLOAD_FILE\" -DBM_VTIME_STEP=$BSM_VT"
    EXTRA_SRCS="$P/bm_vtime.c"
    LDRFLAGS="-Wl,--wrap=time" ;;
  perl-perl4)
    P=$BM/perl-perl4; U=$BENCH/interpreter-perl/perl-4.036
    PCFLAGS="-include $P/config.h -I$P -I$U -w -DBM_SCRIPT_PATH=\"$WORKLOAD_FILE\" -DBM_ARGV_HI=\"$PERL_HI\" -DBM_ARGV_LO=\"$PERL_LO\""
    EXTRA_SRCS="$STUBS/perl_extra_stubs.c" ;;
  xlisp)
    P=$BM/xlisp; U=$BENCH/interpreter-xlisp/xlisp-plus/sources
    PCFLAGS="-DLINUX -DSTSZ=0x40000 -I$U -I$P/shim -DBM_WORKLOAD_PATH=\"$WORKLOAD_FILE\" -w"
    EXTRA_SRCS="$STUBS/xlisp_extra_stubs.c" ;;
  gcc-cc1)
    # cc1 writes its assembly to a per-hart file in the emulator's working
    # directory (gcc-cc1-out-hart<N>.s).
    P=$BM/gcc-cc1; U=$BENCH/compiler-gcc/gcc-2.5.8
    PCFLAGS="-w -fcommon -I$P/hostcfg -I$P/gen/src -I$P/gen -I$U -I$U/config -DIN_GCC -DBM_INPUT_PATH=\"$WORKLOAD_FILE\" -DBM_OUTPUT_PREFIX=\"gcc-cc1-out\""
    EXTRA_SRCS="$STUBS/gcccc1_extra_stubs.c" ;;
  libcint)
    P=$BM/libcint; U=$BENCH/qchem-libcint/libcint
    PCFLAGS="-I$P/gen -I$U/include -I$U/src -DBM_NATM=$CINT_NATM -DBM_WITH_D=$CINT_WITH_D" ;;
  miniweather)
    P=$BM/miniweather; U=$BENCH/weather-miniweather/miniWeather/c
    PCFLAGS=""
    CXX_SRC=$U/miniWeather_serial.cpp
    CXXFLAGS="-I$P/shim -I$CC -D_NX=$MW_NX -D_NZ=$MW_NZ -D_SIM_TIME=$MW_ST -D_OUT_FREQ=-1 -D_DATA_SPEC=DATA_SPEC_THERMAL -DNO_INFORM -DBM_SIZE_NAME=\"$WL\""
    EXTRA_SRCS="$STUBS/dso_handle.c"
    LIBS="-lstdc++ $LIBS" ;;
esac
# The binary opens its workload by this absolute path at run time (fesvr
# opens it host-side), so the tree must stay where it was built.

OBJ=$BUILD/obj/$NAME; rm -rf $OBJ; mkdir -p $OBJ/src
INCS="-I$RT/env -I$RT/common -I$RT/common/ck -I$CC"

# 1. Compile the program: every port translation unit, the harness's newlib
#    glue and the port's stubs. The port's own units are compiled through a
#    symlink layout (port-layout.txt) rather than at their real paths: a
#    quoted #include searches the including file's own directory first, and
#    the ports rely on their replacement headers (e.g. gcc-cc1's
#    gen/src/obstack.h) winning over the upstream copies next to the sources.
awk -v p=$PORT '$1==p {print $2, $3}' $SCRIPTS/port-layout.txt | while read -r n t; do
  ln -s "${t/@B@/$BENCH}" "$OBJ/src/$n"
done
SRCS=$(awk -v p=$PORT -v d=$OBJ/src '$1==p && $2 ~ /\.c$/ {print d "/" $2}' $SCRIPTS/port-layout.txt)
i=0
for s in $SRCS $CC/htif_syscalls.c $CC/bm_posix_stubs.c $CC/bm_rate_heap.c $STUBS/rate_common_stubs.c $EXTRA_SRCS; do
  $GCC $INCS $OPTS $CSTD $PCFLAGS $STACK -c $s -o $OBJ/$i.$(basename $s .c).o
  i=$((i+1))
done
[ -z "$CXX_SRC" ] || $GXX $INCS $OPTS $CXXFLAGS $STACK -c $CXX_SRC -o $OBJ/cxx.o

# 2. Partially link it with its own C library. -d allocates common symbols
#    here; rate_ldr.ld gathers its static constructors into one .init_array.
$GCC -march=rv64imafdc -mcmodel=medany -nostdlib -nostartfiles -r -Wl,-d \
  -Wl,-u,open $LDRFLAGS -Wl,-T,$SCRIPTS/rate_ldr.ld -o $OBJ/image.o $OBJ/*.o \
  -Wl,--start-group $LIBS -Wl,--end-group

SHARED="tohost fromhost hostAccessSema _exit __heap_end"
# Weak undefined references (e.g. newlib's optional __fini_array_start) are
# fine: they resolve to 0 in the final link, exactly as in a normal link.
UNDEF=$($NM -u $OBJ/image.o | awk '$1=="U" {print $NF}' | sort -u)
BAD=$(for u in $UNDEF; do case " $SHARED " in *" $u "*) ;; *) echo $u;; esac; done)
[ -z "$BAD" ] || { echo "ERROR: $PORT has unresolved symbols: $(echo $BAD)" >&2; exit 1; }

# 3. NCOPY copies, every symbol prefixed h<N>_ except the shared ones.
#    (objcopy applies --redefine-sym before --prefix-symbols, hence 2 passes.)
REDEF=""; for s in $SHARED; do REDEF="$REDEF --redefine-sym @@_$s=$s"; done
COPIES=""
for ((h=0; h<NCOPY; h++)); do
  $OBJCOPY --prefix-symbols=h${h}_ $OBJ/image.o $OBJ/h$h.tmp.o
  $OBJCOPY ${REDEF//@@/h$h} --rename-section .init_array=h${h}_init_array $OBJ/h$h.tmp.o $OBJ/h$h.o
  rm -f $OBJ/h$h.tmp.o
  COPIES="$COPIES $OBJ/h$h.o"
done

# 4. Dispatcher: the benchmark's only source in build/NAME/.
rm -rf $BUILD/$NAME; mkdir -p $BUILD/$NAME
if [ $MODE = process ]; then
  cp $SCRIPTS/process_dispatch.c $BUILD/$NAME/rate_dispatch.c
  PROCLD=$SCRIPTS/process.ld
else
  $SCRIPTS/copies_dispatch.sh $NH > $BUILD/$NAME/rate_dispatch.c
  PROCLD=""
fi

# 5. Final link through the stock riscv-tests rules. The program goes last on
#    the link line: crt.S reaches _init with a plain j (+-1 MiB), so the
#    runtime's own code must stay next to .text.init. RATE_RUNTIME_CFLAGS
#    goes to the runtime's own compile only (e.g. -DSPIKE, see spike_check.sh).
# The link is one step: run it without an outer make's jobserver flags
# (which it cannot use from inside this script, and would warn about).
rm -f $BUILD/$NAME.riscv
env -u MAKEFLAGS -u MFLAGS make -s -C $BUILD -f $SPEC/link.mk RT=$RT NAME=$NAME src_dir=. common_dir=$RT/common \
  incs="-I$RT/env -I$RT/common -I$RT/common/ck" \
  RISCV_PREFIX=$RISCV_PREFIX NHARTS=$NH EXTRA_CFLAGS="$STACK $RATE_RUNTIME_CFLAGS" \
  RISCV_LINK_OPTS="-static -nostdlib -nostartfiles -Wl,--defsym=__heap_end=_end $COPIES $PROCLD -lgcc -T $RT/common/test.ld" \
  $NAME.riscv
B=$BUILD/$NAME.riscv

# 6. Checks.
$OBJDUMP --disassemble=memset $B | grep -q 'jal.*<memset>' \
  && { echo "ERROR: runtime memset calls itself (riscv-tests/common/syscalls.c needs the memset fix)" >&2; exit 1; }
$OBJDUMP --disassemble=h0_bm_htif_syscall $B | grep -q 'sc.w.aq' \
  || { echo "ERROR: $PORT host I/O has no mailbox lock" >&2; exit 1; }
$NM $B | grep -q " T h$((NCOPY-1))_thread_entry$" || { echo "ERROR: missing copy $((NCOPY-1))" >&2; exit 1; }
$NM $B | grep -qE ' [TW] h0__vfprintf_r$' || { echo "ERROR: $PORT does not contain newlib's printf" >&2; exit 1; }
NC=$($OBJDUMP -d $B --start-address=0x20000150 --stop-address=0x20000170 | grep -oE 'li\s+a1,[0-9]+' | grep -oE '[0-9]+$')
[ "$NC" = "$NH" ] || { echo "ERROR: crt.S nc=$NC, expected $NH" >&2; exit 1; }
if [ $MODE = process ]; then
  # Every writable section except the runtime's shared .tohost must lie in
  # [bm_data_start, _end), the range each hart gets its own copy of. (TLS
  # sections are templates only; each hart's TLS block is crt.S's.)
  DS=$($NM $B | awk '$3=="bm_data_start"{print $1}'); DE=$($NM $B | awk '$3=="_end"{print $1}')
  OUT=$($READELF -SW $B | awk -v ds=$((16#$DS)) -v de=$((16#$DE)) '
    $0 ~ /^ *\[ *[0-9]+\]/ { sub(/^ *\[ *[0-9]+\] */, ""); name=$1; addr=strtonum("0x"$3); size=strtonum("0x"$5); flags=$7
      if (flags ~ /W/ && flags ~ /A/ && flags !~ /T/ && name != ".tohost" && size > 0 && (addr < ds || addr + size > de)) print name }')
  [ -z "$OUT" ] || { echo "ERROR: writable sections outside the per-hart range: $OUT" >&2; exit 1; }
fi
echo "BUILD_OK $NAME mode=$MODE $(stat -c %s $B) bytes -> $B"
