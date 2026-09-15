#!/bin/sh
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Generate the two headers libcint's CMake build produces with configure_file.
#
# cmake is not used here (cross-compiling a bare-metal target through it is
# more trouble than compiling the source list directly), so the same
# substitutions are done here instead.  Both generated headers land in gen/
# and nothing is written into the vendored tree.
#
#   cint.h         @cint_VERSION@ / @cint_SOVERSION@ filled in
#   cint_config.h  #cmakedefine lines resolved
#
# All the optional long-double and quadmath paths are left off: newlib's
# 128-bit long double support on riscv64 is not something a benchmark should
# depend on, and libcint falls back to double where they are absent.
set -e

UPSTREAM=${1:-../../qchem-libcint/libcint}
OUT=${2:-gen}

VERSION=$(sed -n 's/^set(cint_VERSION_MAJOR "\([0-9]*\)")/\1/p' "$UPSTREAM/CMakeLists.txt")
MINOR=$(sed -n 's/^set(cint_VERSION_MINOR "\([0-9]*\)")/\1/p' "$UPSTREAM/CMakeLists.txt")
PATCH=$(sed -n 's/^set(cint_VERSION_PATCH "\([0-9]*\)")/\1/p' "$UPSTREAM/CMakeLists.txt")
SOVERSION=$VERSION
FULL="$VERSION.$MINOR.$PATCH"

mkdir -p "$OUT"

# cint.h.in carries a #cmakedefine too (I8, the 64-bit integer interface);
# leaving it off keeps FINT as int, which is what a 32-bit-index build uses.
sed -e "s/@cint_VERSION@/$FULL/g" \
    -e "s/@cint_SOVERSION@/$SOVERSION/g" \
    -e 's|^#cmakedefine \(.*\)$|/* #undef \1 */|' \
    "$UPSTREAM/include/cint.h.in" > "$OUT/cint.h"

# Turn every #cmakedefine into the "not defined" form.
sed -e 's|^#cmakedefine \(.*\)$|/* #undef \1 */|' \
    "$UPSTREAM/src/cint_config.h.in" > "$OUT/cint_config.h"

echo "generated $OUT/cint.h (libcint $FULL) and $OUT/cint_config.h"
