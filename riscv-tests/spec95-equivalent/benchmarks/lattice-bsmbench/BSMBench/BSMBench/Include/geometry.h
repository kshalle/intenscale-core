 /*******************************************************************************\
 * Copyright (c) 2008-2014, Agostino Patella, Claudio Pica                       *   
 * All rights reserved.                                                          * 
 * Redistribution and use in source and binary forms, with or without            *
 * modification, are permitted provided that the following conditions are met:   *
 *                                                                               *
 * 1)  Redistributions of source code must retain the above copyright notice,    *
 *     this list of conditions and the following disclaimer.                     *
 * 2)  Redistributions in binary form must reproduce the above copyright notice, *
 *     this list of conditions and the following disclaimer in the documentation *
 *     and/or other materials provided with the distribution.                    *
 * 3)  Any publication in any form derived from the use of this software or any  *
 *     modification of it must refer explicitly to the original BSMBench package *
 *     (including the official URL http://github.com/blucini/BSMBench)           *
 *     and cite the following two publications:                                  *
 *       [1] L. Del Debbio, A. Patella, C. Pica, "Higher representations on the  *
 *           lattice: Numerical simulations. SU(2) with adjoint fermions", Phys. *
 * 	  Rev. D81 (2010) 094503, DOI 10.1103/PhysRevD.81.094503                 *
 *       [2] L. Del Debbio, B. Lucini, A. Patella, C. Pica, A. Rago, "Conformal  *
 *       versus confining scenario in SU(2) with adjoint fermions", Phys.Rev.    *
 *       D80 (2009) 074507, DOI 10.1103/PhysRevD.80.074507                       *
 * 4)  The names of the contributors may be used to endorse or promote products  *
 *     derived from this software without specific prior written permission.     *
 *                                                                               *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"   *
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE     *
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE    *
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE     *
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR           *
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF          *
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS      *
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN       *
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)       *
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF        *
 * THE POSSIBILITY OF SUCH DAMAGE.                                               *
 \*******************************************************************************/

#ifndef GEOMETRY_H
#define GEOMETRY_H

 /***** this define the width of the borders for parallel dimensions
 * For different actions it must be modified
 */
#define BORDERSIZE 1

typedef struct _geometry_descriptor {
  unsigned int local_master_pieces, total_master_pieces;
  unsigned int *master_start, *master_end;
  unsigned int ncopies;
  unsigned int *copy_from, *copy_to, *copy_len;
  unsigned int nbuffers;
  unsigned int *rbuf_len, *sbuf_len;
  unsigned int *rbuf_from_proc, *rbuf_start;
  unsigned int *sbuf_to_proc, *sbuf_start;
  unsigned int gsize;
} geometry_descriptor;



#include "hr_omp.h"

//Loop over pieces of given type
#define _PIECE_FOR(type,ip) \
_OMP_PRAGMA ( _omp_parallel )\
  for(int ip=0;\
      ip<(type)->local_master_pieces;\
      ip++ )

//Loop over sites of piece ip of given type
#define _SITE_FOR_RED(type,ip,is,redop1,redop2) \
_OMP_PRAGMA ( _omp_for redop1 redop2  )\
  for(int is=(type)->master_start[ip]; \
      is<=(type)->master_end[ip]; \
      is++ )

#define _SITE_FOR(type,ip,is) _SITE_FOR_RED(type,ip,is,,)
#define _SITE_FOR_SUM(type,ip,is,...) _SITE_FOR_RED(type,ip,is,_omp_sum(__VA_ARGS__),)
#define _SITE_FOR_MAX(type,ip,is,...) _SITE_FOR_RED(type,ip,is,_omp_max(__VA_ARGS__),)
#define _SITE_FOR_MIN(type,ip,is,...) _SITE_FOR_RED(type,ip,is,_omp_min(__VA_ARGS__),)


//Loop over sites of all pieces of given type
//do an openmp sum over the variables in the redop list
#define _MASTER_FOR_RED(type,is,redop1,redop2) \
  _PIECE_FOR((type),_master_for_ip_##is)    \
  _SITE_FOR_RED((type),_master_for_ip_##is,is,redop1,redop2)

#define _MASTER_FOR(type,is) _MASTER_FOR_RED(type,is,,)
#define _MASTER_FOR_SUM(type,is,...) _MASTER_FOR_RED(type,is,_omp_sum(__VA_ARGS__),)
#define _MASTER_FOR_MAX(type,is,...) _MASTER_FOR_RED(type,is,_omp_max(__VA_ARGS__),)
#define _MASTER_FOR_MIN(type,is,...) _MASTER_FOR_RED(type,is,_omp_min(__VA_ARGS__),)


int setup_process(int *argc, char ***argv);
int finalize_process(void);

void origin_coord(int *c);
void glb_to_proc(int *g, int *p);

int geometry_init(void);
void geometry_mpi_eo(void);
void geometry_mem_alloc(void);
int proc_up(int id, int dir);
int proc_dn(int id, int dir);

#endif
