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
 *     this list of conditions and the following disclaimer in the documefreentation *
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

#include <stdlib.h>
#include "suN.h"
#include "error.h"
#include "memory.h"
#include "geometry.h"
#include "linear_algebra.h"
#ifdef WITH_MPI
#include <mpi.h>
#endif


spinor_field* alloc_spinor_field_f(unsigned int n, geometry_descriptor* type)
{
	suNf_spinor *p;
	spinor_field *s;
	unsigned int i;
#ifdef WITH_MPI /* MPI variables */
	MPI_Request *r;
#endif 
    
    if (n==0) return NULL;
    
	s=malloc(n*sizeof(spinor_field));
	error(s==NULL,1,"alloc_spinor_field_f [field_alloc.c]",
		  "Could not allocate memory space for the spinor field (1)");
	p=malloc(n*type->gsize*sizeof(suNf_spinor));
	error(p==NULL,1,"alloc_spinor_field_f [field_alloc.c]",
		  "Could not allocate memory space for the spinor field (2)");

#ifdef WITH_MPI
	if ((type->nbuffers)>0) {
	  r=malloc(n*2*type->nbuffers*sizeof(MPI_Request));
	  error(r==NULL,1,"alloc_spinor_field_f [field_alloc.c]",
		"Could not allocate memory space for the spinor field (3)");
	  for (i=0; i<n*2*type->nbuffers; ++i)
	    r[i]=MPI_REQUEST_NULL;
	} else {
	  r=NULL;
	}	
#endif

	for(i=0; i<n; ++i) {
	  s[i].ptr=p+i*type->gsize;
	  s[i].type=type;
#ifdef WITH_MPI
	  if (r==NULL) {
	    s[i].comm_req=NULL;
	  } else {
	    s[i].comm_req=r+i*2*type->nbuffers;
	  }
#endif
	}

	return s;
}

void free_spinor_field(spinor_field *s)
{
	free(s->ptr);
#ifdef WITH_MPI
	free(s->comm_req);
#endif
	free(s);
}
