/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* C++ support symbols that the HTIF specs' empty *startfile leaves undefined.
 *
 * htif.specs suppresses crtbegin/crtend, so a C++ port loses __dso_handle,
 * which libstdc++ and every translation unit with a static object reference
 * when they register destructors through __cxa_atexit.  On a bare-metal image
 * there is only one "shared object", so its identity can be any unique
 * address; pointing the handle at itself is the conventional definition.
 *
 * Harmless to link into a pure C port -- it defines one word of data.
 */
void *__dso_handle = (void *)&__dso_handle;
