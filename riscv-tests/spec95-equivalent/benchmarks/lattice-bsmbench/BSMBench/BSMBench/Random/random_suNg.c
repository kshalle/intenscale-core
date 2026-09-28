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
 * File random_suNg.c
 *
 * Generation of uniformly distributed SU(Ng) matrices
 *
 *******************************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include "suN.h"
#include "random.h"
#include "utils.h"

 /***** static variables for random_suNg */
static double s[4];
static suNg_vector *pu1,*pu2;


void random_su2(double rho,double s[]);


void random_suNg_unit_vector(suNg_vector *v)
{
   double norm=0.f,fact;

   while ((1.0+norm)==1.0)   {
     gauss((double*)v,(sizeof(suNg_vector)/sizeof(double)));
     _vector_prod_re_g(norm,*v,*v);
     norm=sqrt(norm);
   }

   fact=1.0/norm;
   _vector_mul_g(*v,fact,*v);
}

 /***** generates a random SU(N) matrix via SU(2) rotations */
static void rotate(void) /* same as in cabmar */
{
	  int i;
	  complex z1,z2;
	  complex *cu1, *cu2;
				  
	  cu1 = &((*pu1).c[0]);
	  cu2 = &((*pu2).c[0]);
						  
	  for (i=0; i<NG; ++i) {
		    z1.re=s[0]*(*cu1).re-s[1]*(*cu2).im+s[2]*(*cu2).re-s[3]*(*cu1).im;
		    z1.im=s[0]*(*cu1).im+s[1]*(*cu2).re+s[2]*(*cu2).im+s[3]*(*cu1).re;
		    z2.re=s[0]*(*cu2).re-s[1]*(*cu1).im-s[2]*(*cu1).re+s[3]*(*cu2).im;
		    z2.im=s[0]*(*cu2).im+s[1]*(*cu1).re-s[2]*(*cu1).im-s[3]*(*cu2).re;
		    (*cu1) = z1; 
		    (*cu2) = z2; 
		    ++cu1;
		    ++cu2;
	  }
}

void random_suNg(suNg *u) {
  int i,j;

	_suNg_unit(*u);
  pu1=(suNg_vector*)(u);
				  
  for (i=0; i<NG; ++i) {
    pu2 = pu1 + 1;
    for (j=i+1; j<NG; ++j) {
		  random_su2(0.0,s);
      rotate();
      ++pu2; 
    } 
	  ++pu1; 
  }

}
