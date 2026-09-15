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

#include <math.h>

#include "global.h"
#include "utils.h"
#include "suN.h"
#include "communications.h"


#include "observables.h"


void apply_bc(){
#if defined(ANTIPERIODIC_BC_T) && !defined(ROTATED_SF) && !defined(BASIC_SF)
  if(COORD[0]==0) {
    int index;
    int ix,iy,iz;
    suNf *u;
    for (ix=0;ix<X_EXT;++ix) for (iy=0;iy<Y_EXT;++iy) for (iz=0;iz<Z_EXT;++iz){
      index=ipt_ext(2*T_BORDER,ix,iy,iz);
      if(index!=-1) {
	u=pu_gauge_f(index,0);
	_suNf_minus(*u,*u);
      }
    }
  }
#endif

#ifdef ANTIPERIODIC_BC_X
  if(COORD[1]==0) {
    int index;
    int it,iy,iz;
    suNf *u;
    for (it=0;it<T_EXT;++it)
      for (iy=0;iy<Y_EXT;++iy)
	for (iz=0;iz<Z_EXT;++iz){
	  index=ipt_ext(it,2*X_BORDER,iy,iz);
	  if(index!=-1) {
	    suNf *u=pu_gauge_f(index,1);
	    _suNf_minus(*u,*u);
	  }
	}
  }
#endif
  
#ifdef ANTIPERIODIC_BC_Y
  if(COORD[2]==0) {
    int index;
    int ix,it,iz;
    suNf *u;
    for (it=0;it<T_EXT;++it)
      for (ix=0;ix<X_EXT;++ix)
	for (iz=0;iz<Z_EXT;++iz){
	  index=ipt_ext(it,ix,2*Y_BORDER,iz);
	  if(index!=-1) {
	    suNf *u=pu_gauge_f(index,2);
	    _suNf_minus(*u,*u);
	  }
	}
  }
#endif
  
#ifdef ANTIPERIODIC_BC_Z
  if(COORD[3]==0) {
    int index;
    int ix,iy,it;
    suNf *u;
    for (it=0;it<T_EXT;++it)
      for (ix=0;ix<X_EXT;++ix)
	for (iy=0;iy<Y_EXT;++iy){
	  index=ipt_ext(it,ix,iy,2*Z_BORDER);
	  if(index!=-1) {
	    suNf *u=pu_gauge_f(index,3);
	    _suNf_minus(*u,*u);
	  }
	}
  }
#endif
}
