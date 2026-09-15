/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Parallel-netCDF shim for bare-metal builds.
 *
 * miniWeather writes its state snapshots through pnetcdf.  The bare-metal
 * benchmark is built with _OUT_FREQ = -1, which makes the upstream source skip
 * every call to output(), so none of these functions is ever reached at run
 * time -- they exist only to satisfy the compiler without editing the upstream
 * file.  Each one traps if it is somehow called, so a build that accidentally
 * enables output fails loudly instead of silently writing nothing.
 */
#ifndef BM_PNETCDF_SHIM_H
#define BM_PNETCDF_SHIM_H

#include <stdio.h>
#include <stdlib.h>
#include "mpi.h"

#define NC_NOERR    0
#define NC_CLOBBER  0
#define NC_64BIT_DATA 0
#define NC_WRITE    0
#define NC_DOUBLE   6
#define NC_UNLIMITED 0
#define NC_GLOBAL   (-1)

static inline int bm_pnetcdf_unsupported(const char *fn)
{
  printf("[bm] pnetcdf call %s reached: this build has no file output "
         "(build with _OUT_FREQ=-1)\n", fn);
  abort();
  return -1;
}

static inline const char *ncmpi_strerror(int e) { (void)e; return "pnetcdf disabled in bare-metal build"; }

#define BM_NC_STUB(name) \
  static inline int name(void) { return bm_pnetcdf_unsupported(#name); }

/* Variadic so that the upstream call sites type-check unchanged. */
static inline int ncmpi_create(MPI_Comm c, const char *p, int m, MPI_Info i, int *id)
{ (void)c; (void)p; (void)m; (void)i; (void)id; return bm_pnetcdf_unsupported("ncmpi_create"); }
static inline int ncmpi_open(MPI_Comm c, const char *p, int m, MPI_Info i, int *id)
{ (void)c; (void)p; (void)m; (void)i; (void)id; return bm_pnetcdf_unsupported("ncmpi_open"); }
static inline int ncmpi_close(int id) { (void)id; return bm_pnetcdf_unsupported("ncmpi_close"); }
static inline int ncmpi_def_dim(int id, const char *n, MPI_Offset len, int *dimid)
{ (void)id; (void)n; (void)len; (void)dimid; return bm_pnetcdf_unsupported("ncmpi_def_dim"); }
static inline int ncmpi_def_var(int id, const char *n, int t, int nd, const int *d, int *v)
{ (void)id; (void)n; (void)t; (void)nd; (void)d; (void)v; return bm_pnetcdf_unsupported("ncmpi_def_var"); }
static inline int ncmpi_enddef(int id) { (void)id; return bm_pnetcdf_unsupported("ncmpi_enddef"); }
static inline int ncmpi_inq_varid(int id, const char *n, int *v)
{ (void)id; (void)n; (void)v; return bm_pnetcdf_unsupported("ncmpi_inq_varid"); }
static inline int ncmpi_begin_indep_data(int id) { (void)id; return bm_pnetcdf_unsupported("ncmpi_begin_indep_data"); }
static inline int ncmpi_end_indep_data(int id) { (void)id; return bm_pnetcdf_unsupported("ncmpi_end_indep_data"); }
static inline int ncmpi_put_vara_double(int id, int v, const MPI_Offset *s, const MPI_Offset *c, const double *b)
{ (void)id; (void)v; (void)s; (void)c; (void)b; return bm_pnetcdf_unsupported("ncmpi_put_vara_double"); }

static inline int ncmpi_put_vara_double_all(int id, int v, const MPI_Offset *s, const MPI_Offset *c, const double *b)
{ (void)id; (void)v; (void)s; (void)c; (void)b; return bm_pnetcdf_unsupported("ncmpi_put_vara_double_all"); }

#endif /* BM_PNETCDF_SHIM_H */
