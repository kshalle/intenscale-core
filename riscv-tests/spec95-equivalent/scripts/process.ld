/* SPDX-FileCopyrightText: 2026 Intensivate, Inc.
   SPDX-License-Identifier: BSD-2-Clause */
/* Implicit linker script, added to test.ld's link in RATE_MODE=process:
   where the image's writable data starts. [bm_data_start, _end) is what each
   hart gets its own copy of (process_dispatch.c). */
bm_data_start = ADDR(.data);
