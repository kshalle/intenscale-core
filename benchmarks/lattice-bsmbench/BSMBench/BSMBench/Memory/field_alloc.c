 /*******************************************************************************\
 * Copyright (c) 2008, Claudio Pica                                              *   
 * All rights reserved.                                                          *
 *     	      									 *
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

 /***********************************************************************************
 *
 * File field_alloc.c
 *
 * Functions for fields allocation
 *
 *******************************************************************************/

#include <stdlib.h>
#include "suN.h"
#include "error.h"
#include "memory.h"
#include "global.h"
#include "spinor_field.h"
#include "geometry.h"
#ifdef WITH_MPI
#include <mpi.h>
#endif

void free_gfield(suNg_field *u)
{
   free(u->ptr);
#ifdef WITH_MPI
   free(u->comm_req);   
#endif
   free(u);
}

suNg_field *alloc_gfield(geometry_descriptor* type)
{
   int ix;
   suNg unity;
   suNg_field *gf;

   gf=malloc(sizeof(suNg_field));
   error(gf==NULL,1,"alloc_gfield [field_alloc.c]",
         "Could not allocate memory space for the gauge field (1)");
   gf->ptr=malloc(4*type->gsize*sizeof(suNg));
   error((gf->ptr)==NULL,1,"alloc_gfield [field_alloc.c]",
         "Could not allocate memory space for the gauge field (2)");

   gf->type=type;

#ifdef WITH_MPI
   if (type->nbuffers>0) {
     gf->comm_req=malloc(2*type->nbuffers*sizeof(MPI_Request));
     error((gf->comm_req)==NULL,1,"alloc_gfield [field_alloc.c]",
	   "Could not allocate memory space for the gauge field (3)");
     for (ix=0; ix<2*type->nbuffers; ++ix)
       gf->comm_req[ix]=MPI_REQUEST_NULL;
   } else {
     gf->comm_req=NULL;
   }
#endif

   /* set gauge field to unity */
   _suNg_unit(unity);
   for (ix=0;ix<4*type->gsize;++ix)
     *((gf->ptr)+ix)=unity;

   return gf;
}

void free_gfield_f(suNf_field *u)
{
  free(u->ptr);
#ifdef WITH_MPI
  free(u->comm_req);   
#endif
  free(u);
}

suNf_field* alloc_gfield_f(geometry_descriptor* type)
{
  int ix;
  suNf unity;
  suNf_field *gf;

  gf=malloc(sizeof(suNf_field));
  error(gf==NULL,1,"alloc_gfield [field_alloc.c]",
	"Could not allocate memory space for the gauge field (1)");
  gf->ptr=malloc(4*type->gsize*sizeof(suNf));
  error((gf->ptr)==NULL,1,"alloc_gfield [field_alloc.c]",
	"Could not allocate memory space for the gauge field (2)");

  gf->type=type;

#ifdef WITH_MPI
  if (type->nbuffers>0) {
    gf->comm_req=malloc(2*type->nbuffers*sizeof(MPI_Request));
    error((gf->comm_req)==NULL,1,"alloc_gfield [field_alloc.c]",
	  "Could not allocate memory space for the gauge field (3)");
     for (ix=0; ix<2*type->nbuffers; ++ix)
       gf->comm_req[ix]=MPI_REQUEST_NULL;
  } else {
    gf->comm_req=NULL;
  }
#endif

  /* set gauge field to unity */
  _suNf_unit(unity);
  for (ix=0;ix<4*type->gsize;++ix)
    *((gf->ptr)+ix)=unity;

  return gf;
}

