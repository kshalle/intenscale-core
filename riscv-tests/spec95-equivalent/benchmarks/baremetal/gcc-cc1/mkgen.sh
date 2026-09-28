#!/bin/sh
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Build gcc 2.5.8's generator programs and run them, on the build machine.
#
# cc1 is partly generated code: eleven of its translation units are written by
# programs that read the target's machine description (config/sparc/sparc.md)
# and emit C.  Those programs must run on the machine doing the build, not on
# the target, so this step is a host build and is the reason the port cannot
# simply be handed to the cross compiler.
#
# Everything lands in gen/; the vendored tree is not written to.
#
#   ./mkgen.sh [upstream-dir] [out-dir]
set -e

UPSTREAM=${1:-../../compiler-gcc/gcc-2.5.8}
OUT=${2:-gen}
HOST_CC=${HOST_CC:-gcc}

# -std=gnu89: these are 1994 sources, and a C23-default compiler rejects
# old-style function definitions outright.
#
# Prototypes are left on (gcc 2.5.8 keys that off __STDC__): the variadic
# definitions have been rewritten to the standard form by mkstage.py, so they
# now agree with the prototypes in rtl.h and tree.h.
# -I- turns off the "search the including file's own directory first" rule, so
# the staged copies in gen/src and the replacement headers in hostcfg win over
# the upstream originals (the same trick the perl port needs for perly.h).
HOST_CFLAGS="-std=gnu89 -O1 -w -I hostcfg -I $OUT/src -I $OUT -I- -I $UPSTREAM -I $UPSTREAM/config"

mkdir -p "$OUT/obj"

# The two headers configure would have created as symlinks.  They live in the
# generated directory (which is not checked in), so they are written here
# rather than by hand -- otherwise a fresh checkout has no way to build.
cat > "$OUT/tm.h" <<'TMEOF'
/* configure links tm.h to the target machine description header.
   This port targets SPARC: a well-supported 1994 back end whose emitted code
   is never executed -- only the compiler's own work is measured. */
#include "sparc/sparc.h"
TMEOF
cat > "$OUT/tconfig.h" <<'TCEOF'
/* configure links tconfig.h to the target xm header. */
#include "sparc/xm-sparc.h"
TCEOF

# Prefer a staged copy (see mkstage.py) when one exists.
pick() {
    if [ -f "$OUT/src/$1.c" ]; then echo "$OUT/src/$1.c"; else echo "$UPSTREAM/$1.c"; fi
}

# Support objects every generator links against -- upstream's HOST_RTL plus
# obstack, and nothing more: print-rtl.o and rtlanal.o reference globals that
# only exist inside cc1 itself.
for f in rtl obstack print-rtl rtlanal; do
    $HOST_CC $HOST_CFLAGS -c "$(pick $f)" -o "$OUT/obj/$f.o"
done
# Upstream's own link lines: every generator gets rtl.o (HOST_RTL), and
# genattrtab additionally gets print-rtl.o and rtlanal.o.  Those two reference
# globals that live in emit-rtl.c (frame_pointer_rtx and friends,
# insn_name_ptr) -- genattrtab and genflags define their own copies, which is
# how upstream resolves it, so nothing needs stubbing here.
SUPPORT="$OUT/obj/rtl.o $OUT/obj/obstack.o"
ATTR_SUPPORT="$SUPPORT $OUT/obj/print-rtl.o $OUT/obj/rtlanal.o"

# The machine description.  configure links it as ./md; the generators take it
# as their only argument.
MD="$UPSTREAM/config/sparc/sparc.md"

build_and_run() {
    prog=$1; output=$2; extra=$3
    $HOST_CC $HOST_CFLAGS -c "$(pick $prog)" -o "$OUT/obj/$prog.o"
    $HOST_CC $HOST_CFLAGS -o "$OUT/obj/$prog" "$OUT/obj/$prog.o" ${extra:-$SUPPORT}
    "$OUT/obj/$prog" "$MD" > "$OUT/$output"
    printf '  %-12s -> %-16s %8d bytes\n' "$prog" "$output" "$(wc -c < "$OUT/$output")"
}

# The bytecode interpreter's tables are generated too, from bytecode.def by
# three more host programs.  cc1's expr.c, stmt.c and emit-rtl.c include
# bc-opcode.h, so these are not optional even though nothing here runs
# bytecode.  bi-parser.c ships pre-generated, so bison is not needed.
BI_OBJ=""
for f in bi-parser bi-lexer bi-reverse; do
    $HOST_CC $HOST_CFLAGS -c "$(pick $f)" -o "$OUT/obj/$f.o"
    BI_OBJ="$BI_OBJ $OUT/obj/$f.o"
done

echo "generating the bytecode tables from bytecode.def:"
bi_run() {
    prog=$1; output=$2
    $HOST_CC $HOST_CFLAGS -c "$(pick $prog)" -o "$OUT/obj/$prog.o"
    $HOST_CC $HOST_CFLAGS -o "$OUT/obj/$prog" "$OUT/obj/$prog.o" $BI_OBJ $OUT/obj/obstack.o
    "$OUT/obj/$prog" < "$UPSTREAM/bytecode.def" > "$OUT/$output"
    printf '  %-12s -> %-16s %8d bytes\n' "$prog" "$output" "$(wc -c < "$OUT/$output")"
}
bi_run bi-opcode bc-opcode.h
bi_run bi-opname bc-opname.h
bi_run bi-arity  bc-arity.h

# Order matters: genattrtab includes insn-config.h, so genconfig runs first.
echo "generating cc1's machine-dependent sources from sparc.md:"
build_and_run genconfig  insn-config.h
build_and_run genflags   insn-flags.h
build_and_run gencodes   insn-codes.h
build_and_run genattr    insn-attr.h
build_and_run genemit    insn-emit.c
build_and_run genrecog   insn-recog.c
build_and_run genopinit  insn-opinit.c
build_and_run genextract insn-extract.c
build_and_run genpeep    insn-peep.c
build_and_run genattrtab insn-attrtab.c "$ATTR_SUPPORT"
build_and_run genoutput  insn-output.c
