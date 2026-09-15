 /*******************************************************************************\
 * Copyright (c) 2008, Agostino Patella, Claudio Pica                            *   
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

#include "linear_algebra.h"
#include "global.h"
#include <string.h>
#include "spinor_field.h"
#include "communications.h"

 /***** Re <s1,s2> */
double spinor_field_prod_re_f(spinor_field *s1, spinor_field *s2)
{
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1), *_SPINOR_PTR(s2);
	double res=0.;
	double prod;
	
	_TWO_SPINORS_FOR(s1,s2,i) {
		_spinor_prod_re_f(prod,*_SPINOR_PTR(s1),*_SPINOR_PTR(s2));
		res+=(double)prod;
	}
#ifdef WITH_MPI
	global_sum(&res,1);
#endif
	return res;
}

 /***** Re <s1,s1> */
double spinor_field_sqnorm_f(spinor_field *s1)
{
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1);
	double res=0.;
	double prod;
	
	_ONE_SPINOR_FOR(s1,i) {
		_spinor_prod_re_f(prod,*_SPINOR_PTR(s1),*_SPINOR_PTR(s1));
		res+=(double)prod;
	}
#ifdef WITH_MPI
	global_sum(&res,1);
#endif
	return res;
}

 /***** s1+=r*s2 r real */
void spinor_field_mul_add_assign_f(spinor_field *s1, double r, spinor_field *s2)
{
	double rr=(double)r;
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1), *_SPINOR_PTR(s2);
	
	_TWO_SPINORS_FOR(s1,s2,i) {
		_spinor_mul_add_assign_f(*_SPINOR_PTR(s1),rr,*_SPINOR_PTR(s2));
	}
}

 /***** s1+=c*s2 c complex */
void spinor_field_mulc_add_assign_f(spinor_field *s1, complex c, spinor_field *s2)
{
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1), *_SPINOR_PTR(s2);
	complex c1;
	c1.re=(double)c.re; c1.im=(double)c.im; 
	
	_TWO_SPINORS_FOR(s1,s2,i) {
		_spinor_mulc_add_assign_f(*_SPINOR_PTR(s1),c1,*_SPINOR_PTR(s2));
	}
}

 /***** s1=r*s2 */
void spinor_field_mul_f(spinor_field *s1, double r, spinor_field *s2)
{
	double rr=(double)r;
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1), *_SPINOR_PTR(s2);
	
	_TWO_SPINORS_FOR(s1,s2,i) {
		_spinor_mul_f(*_SPINOR_PTR(s1),rr,*_SPINOR_PTR(s2));
	}
}

 /***** r=s1-s2 */
void spinor_field_sub_f(spinor_field *r, spinor_field *s1, spinor_field *s2)
{
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(r), *_SPINOR_PTR(s1), *_SPINOR_PTR(s2);
	
	_THREE_SPINORS_FOR(s1,s2,r,i) {
		_spinor_sub_f(*_SPINOR_PTR(r),*_SPINOR_PTR(s1),*_SPINOR_PTR(s2));
	}
}

 /***** s1+=s2 */
void spinor_field_add_assign_f(spinor_field *s1, spinor_field *s2)
{
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1), *_SPINOR_PTR(s2);
	
	_TWO_SPINORS_FOR(s1,s2,i) {
		_spinor_add_assign_f(*_SPINOR_PTR(s1),*_SPINOR_PTR(s2));
	}
}

 /***** s1-=s2 */
void spinor_field_sub_assign_f(spinor_field *s1, spinor_field *s2)
{
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1), *_SPINOR_PTR(s2);
	
	_TWO_SPINORS_FOR(s1,s2,i) {
		_spinor_sub_assign_f(*_SPINOR_PTR(s1),*_SPINOR_PTR(s2));
	}
}

 /***** s1=0 */
void spinor_field_zero_f(spinor_field *s1)
{
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1);
	
	_ONE_SPINOR_FOR(s1,i) {
		_spinor_zero_f(*_SPINOR_PTR(s1));
	}
}

 /***** s1=-s2 */
void spinor_field_minus_f(spinor_field *s1, spinor_field *s2)
{
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1), *_SPINOR_PTR(s2);
	
	_TWO_SPINORS_FOR(s1,s2,i) {
		_spinor_minus_f(*_SPINOR_PTR(s1),*_SPINOR_PTR(s2));
	}
}

 /***** s1=g5*s1  */
void spinor_field_g5_assign_f(spinor_field *s1)
{
	_DECLARE_INT_ITERATOR(i);
	suNf_spinor *_SPINOR_PTR(s1);
	
	_ONE_SPINOR_FOR(s1,i) {
		_spinor_g5_assign_f(*_SPINOR_PTR(s1));
	}
}


void spinor_field_copy_f(spinor_field *s1, spinor_field *s2) {
	_TWO_SPINORS_MATCHING(s1,s2);
	memcpy(s1->ptr,s2->ptr,s1->type->gsize*sizeof(suNf_spinor));
}
