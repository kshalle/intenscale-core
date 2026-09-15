/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* suN_repr_func.h for NG = 2.
 *
 * Upstream's build copies su2_repr_func.h over this name in its own Include
 * directory (see BSMBench/Include/Makefile) as a generated build artifact.
 * Including it instead of copying keeps the vendored tree untouched, and
 * -I- in the Makefile is what makes this file win over the generated copy
 * that a previous upstream build may have left behind.
 */
#include "su2_repr_func.h"
