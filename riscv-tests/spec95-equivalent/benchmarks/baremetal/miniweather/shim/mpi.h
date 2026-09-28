/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* Single-rank MPI shim for bare-metal builds.
 *
 * miniWeather_serial.cpp still includes <mpi.h> and calls Init/Finalize/
 * Barrier/Allreduce even though it runs on one rank.  Rather than edit the
 * upstream source, this header satisfies those calls locally: the reduction
 * is a copy, the barrier is a no-op, and the rank is always 0 of 1.
 *
 * Not an MPI implementation -- it covers exactly the symbols this benchmark
 * uses, and deliberately nothing else.
 */
#ifndef BM_MPI_SHIM_H
#define BM_MPI_SHIM_H

#include <stdint.h>
#include <string.h>

typedef int MPI_Comm;
typedef int MPI_Datatype;
typedef int MPI_Op;
typedef int MPI_Info;
typedef long long MPI_Offset;

#define MPI_COMM_WORLD 0
#define MPI_INFO_NULL  0
#define MPI_SUM        0
#define MPI_DOUBLE     ((MPI_Datatype)8)
#define MPI_SUCCESS    0

static inline int MPI_Init(int *argc, char ***argv) { (void)argc; (void)argv; return MPI_SUCCESS; }
static inline int MPI_Finalize(void) { return MPI_SUCCESS; }
static inline int MPI_Barrier(MPI_Comm c) { (void)c; return MPI_SUCCESS; }
static inline int MPI_Comm_size(MPI_Comm c, int *n) { (void)c; *n = 1; return MPI_SUCCESS; }
static inline int MPI_Comm_rank(MPI_Comm c, int *r) { (void)c; *r = 0; return MPI_SUCCESS; }

static inline int MPI_Allreduce(const void *sendbuf, void *recvbuf, int count,
                                MPI_Datatype dt, MPI_Op op, MPI_Comm comm)
{
  (void)op; (void)comm;
  /* One rank: the reduction of a single contribution is that contribution. */
  memcpy(recvbuf, sendbuf, (size_t)count * (size_t)dt);
  return MPI_SUCCESS;
}

#endif /* BM_MPI_SHIM_H */
