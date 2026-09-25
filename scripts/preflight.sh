#!/bin/bash
# SPDX-FileCopyrightText: 2016-2026 Intensivate, Inc.
# SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0
#
# This file is part of the Intensivate CPU Core.
#
# Licensed under the Intensivate Non-Commercial Hardware Source
# License v1.0. Commercial use requires a separate written license
# from Intensivate, Inc.
#
# Full license: LICENSE.md
# Patent notice: PATENTS.md
#
# Checks that the host tools the Makefile's build/run targets depend on
# are actually present, and prints their versions, before handing off
# to emulator/, vsim/ or regression/. Run directly as `make preflight`,
# or let `make build`/`debug`/`verilator`/`run*` pull it in automatically.
#
# Missing *required* tools fail the check (exit 1); missing *optional*
# tools only warn, since they're only needed for specific targets
# (docker for CI, spike-dasm for disassembly, etc).

status=0
missing=0

note() { printf '  %s\n' "$1"; }
ok()   { printf '[ok]   %-12s %s\n' "$1" "$2"; }
warn() { printf '[warn] %s\n' "$1"; }
fail() { printf '[FAIL] %s\n' "$1"; status=1; missing=$((missing + 1)); }

# check_required NAME VERSION_CMD REASON
check_required() {
	local name="$1" version_cmd="$2" reason="$3"
	if command -v "$name" >/dev/null 2>&1; then
		ok "$name" "$($version_cmd 2>&1 | head -1)"
	else
		fail "$name not found -- required to $reason"
	fi
}

# check_optional NAME VERSION_CMD REASON
check_optional() {
	local name="$1" version_cmd="$2" reason="$3"
	if command -v "$name" >/dev/null 2>&1; then
		ok "$name" "$($version_cmd 2>&1 | head -1)"
	else
		warn "$name not found -- $reason (optional)"
	fi
}

echo "==> Checking build prerequisites (see README.md, 'Building and running the core')"
echo

# --- Core toolchain used to build firrtl/chisel3, the emulator and the tests ---
check_required git      "git --version"      "check out and update submodules"
check_required make     "make --version"     "drive this build"
check_required g++      "g++ --version"      "compile the C++ emulator harness (needs C++11)"
check_required java     "java -version"      "run sbt/FIRRTL (sbt-launch.jar)"
check_required python3  "python3 --version"  "run scripts/vlsi_mem_gen and other build-time scripts"
check_required wget     "wget --version"     "fetch the pinned Verilator 4.028 source tarball"
check_required bison    "bison --version"    "build Verilator from source"
check_required flex     "flex --version"     "build Verilator from source"
check_required cmake    "cmake --version"    "build DRAMSim3 (requires CMake >= 3.0)"

# bison's generated code is what the documented workaround in
# emulator/Makefrag-verilator exists for; flag it explicitly rather than
# waiting for the build to fail on a "verilog.h: No such file" error.
if command -v bison >/dev/null 2>&1; then
	bison_version=$(bison --version 2>&1 | head -1 | grep -oE '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -1)
	if [ -n "$bison_version" ]; then
		warn "bison $bison_version may need the pinned-Verilator workaround in emulator/Makefrag-verilator (confirmed necessary with bison 3.8.2)"
	fi
fi

# --- RISC-V cross toolchain (built separately; see set_env.sh / README.md) ---
if command -v riscv64-unknown-elf-gcc >/dev/null 2>&1; then
	ok "riscv64-unknown-elf-gcc" "$(riscv64-unknown-elf-gcc --version | head -1)"
else
	fail "riscv64-unknown-elf-gcc not found -- required to build bootrom/, riscv-tests/ and the benchmarks. Set RISCV and PATH (see set_env.sh) or build riscv-tools/rocket-tools first"
fi

# --- Link-time libraries the emulator needs (best-effort; pkg-config itself is optional) ---
if command -v pkg-config >/dev/null 2>&1; then
	if pkg-config --exists zlib; then
		ok "zlib" "$(pkg-config --modversion zlib)"
	else
		warn "zlib not found via pkg-config -- emulator links against -lz"
	fi
	if pkg-config --exists libzstd; then
		ok "libzstd" "$(pkg-config --modversion libzstd)"
	else
		warn "libzstd not found via pkg-config -- emulator links against -lzstd"
	fi
else
	warn "pkg-config not found -- can't verify zlib/libzstd dev packages are installed (emulator links against -lz -lzstd)"
fi

# --- Optional tools used by specific targets only ---
# spike-dasm has no --version flag worth trusting; just confirm it's on PATH.
if command -v spike-dasm >/dev/null 2>&1; then
	ok "spike-dasm" "$(command -v spike-dasm)"
else
	warn "spike-dasm not found -- disassembly in run-*-tests output will be skipped without it (optional)"
fi
check_optional docker "docker --version" "only needed for the CI 'DockerCopy' target"

echo
if [ "$status" -ne 0 ]; then
	echo "Preflight failed: $missing required tool(s) missing. See README.md, 'Building and running the core'."
else
	echo "Preflight OK: all required tools present."
fi

exit $status
