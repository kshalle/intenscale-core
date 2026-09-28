# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause
#
# workloads.sh -- the problem size of every port for every WORKLOAD. Sourced
# by build.sh (the RISC-V binary) and make_reference.sh (its native
# reference), so a binary and its reference can never disagree about what a
# size means. Expects PORT, WL and BM (benchmarks/baremetal) to be set, and
# sets the port's size parameters plus WORKLOAD_FILE (the input it reads, or
# empty).
#
# tiny / small / ref are the ports' own sizes (benchmarks/baremetal/<port>/
# Makefile). extra_tiny is the smallest problem that still spends most of its
# instructions in the benchmark's own kernel rather than start-up and host
# I/O; see README.md for how each was chosen.

workload_params() {
  case $PORT:$WL in
    # BSMBench sizes its own run by timing itself; its clock is deterministic
    # here (bm_vtime.c: every clock reading advances VT seconds), which fixes
    # the number of Dphi iterations. The lattice is in the input file.
    bsmbench:extra_tiny) BSM_INPUT=extra_tiny; BSM_HZ=10000;   BSM_VT=401 ;;  # 2x4x4x4, 1 iteration
    bsmbench:tiny)       BSM_INPUT=tiny;       BSM_HZ=10000;   BSM_VT=200 ;;  # 4x4x4x4, 7 iterations
    bsmbench:small)      BSM_INPUT=small;      BSM_HZ=100000;  BSM_VT=100 ;;
    bsmbench:ref)        BSM_INPUT=ref;        BSM_HZ=1000000; BSM_VT=100 ;;
    # primes.pl LIMIT NUMBERS-TO-FACTOR
    perl-perl4:extra_tiny) PERL_HI=100;   PERL_LO=20 ;;
    perl-perl4:tiny)       PERL_HI=400;   PERL_LO=100 ;;
    perl-perl4:small)      PERL_HI=2000;  PERL_LO=400 ;;
    perl-perl4:ref)        PERL_HI=20000; PERL_LO=3000 ;;
    # workload/xlisp-bench-<size>.lsp (the sizes differ in four parameters)
    xlisp:extra_tiny|xlisp:tiny|xlisp:small|xlisp:ref) XLISP_LSP=xlisp-bench-${WL/_/-}.lsp ;;
    # input: generated C with this many groups (workload/mkinput.py)
    gcc-cc1:extra_tiny) CC1_GROUPS=1 ;;
    gcc-cc1:tiny)       CC1_GROUPS=2 ;;
    gcc-cc1:small)      CC1_GROUPS=8 ;;
    gcc-cc1:ref)        CC1_GROUPS=64 ;;
    # centres, and whether they carry d shells
    libcint:extra_tiny) CINT_NATM=1; CINT_WITH_D=0 ;;
    libcint:tiny)       CINT_NATM=2; CINT_WITH_D=0 ;;
    libcint:small)      CINT_NATM=2; CINT_WITH_D=1 ;;
    libcint:ref)        CINT_NATM=3; CINT_WITH_D=1 ;;
    # grid and simulated time; extra_tiny = tiny, because fewer time steps
    # would make the run mostly start-up
    miniweather:extra_tiny) MW_NX=20;  MW_NZ=10; MW_ST=5 ;;
    miniweather:tiny)       MW_NX=20;  MW_NZ=10; MW_ST=5 ;;
    miniweather:small)      MW_NX=40;  MW_NZ=20; MW_ST=20 ;;
    miniweather:ref)        MW_NX=100; MW_NZ=50; MW_ST=1000 ;;
    *) echo "unknown PORT/WORKLOAD '$PORT' '$WL' (ports: bsmbench perl-perl4 xlisp gcc-cc1 libcint miniweather; workloads: extra_tiny tiny small ref)" >&2
       return 1 ;;
  esac
  case $PORT in
    bsmbench)   WORKLOAD_FILE=$BM/bsmbench/workload/bsmbench-$BSM_INPUT.input ;;
    perl-perl4) WORKLOAD_FILE=$BM/perl-perl4/workload/primes.pl ;;
    xlisp)      WORKLOAD_FILE=$BM/xlisp/workload/$XLISP_LSP ;;
    gcc-cc1)    WORKLOAD_FILE=$BM/gcc-cc1/workload/input-$CC1_GROUPS.i
                [ -s $WORKLOAD_FILE ] || python3 $BM/gcc-cc1/workload/mkinput.py $CC1_GROUPS > $WORKLOAD_FILE ;;
    *)          WORKLOAD_FILE="" ;;
  esac
  [ -z "$WORKLOAD_FILE" ] || [ -s "$WORKLOAD_FILE" ] || { echo "$PORT: workload $WORKLOAD_FILE missing" >&2; return 1; }
}
