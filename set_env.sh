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

# source this file
#echo "........NOTE THIS SCRIPT MUST BE CALLED FROM THE CORRECT PATH........"
#echo

export RISCV=$PWD/riscv-tools
export PATH=$RISCV/bin:$PATH
export LD_LIBRARY_PATH=$RISCV/lib:$LD_LIBRARY_PATH
