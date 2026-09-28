 /*******************************************************************************\
 * Copyright (c) 2008, Claudio Pica                                              *   
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

 /***********************************************************************************
 *
 * File su3.h
 *
 * Type definitions and macros for SU(N) matrices and spinors  
 *
 *******************************************************************************/

#ifndef SUN_H
#define SUN_H

#include "suN_types.h"

 /***********************************************************************************
 *
 * The following macros are the same for single and double precision types
 *
 * Depending on the macro, arguments are variables of type suN_vector and suN
 * (or suN_vector_flt and suN_flt)
 *
 *******************************************************************************/

 /***** r=0 */
#define _vector_zero_g(r) \
   _complex_0((r).c[0]); \
   _complex_0((r).c[1]); \
   _complex_0((r).c[2])

 /***** r=-s */
#define _vector_minus_g(r,s) \
   _complex_minus((r).c[0],(s).c[0]); \
   _complex_minus((r).c[1],(s).c[1]); \
   _complex_minus((r).c[2],(s).c[2])

 /***** r=k*s (k real) */
#define _vector_mul_g(r,k,s) \
   _complex_mulr((r).c[0],(k),(s).c[0]); \
   _complex_mulr((r).c[1],(k),(s).c[1]); \
   _complex_mulr((r).c[2],(k),(s).c[2])

 /***** r=z*s (z complex) */
#define _vector_mulc_g(r,z,s) \
   _complex_mul((r).c[0],(z),(s).c[0]); \
   _complex_mul((r).c[1],(z),(s).c[1]); \
   _complex_mul((r).c[2],(z),(s).c[2])

 /***** r=s1+s2 */
#define _vector_add_g(r,s1,s2) \
   _complex_add((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_add((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_add((r).c[2],(s1).c[2],(s2).c[2])

 /***** r=s1-s2 */
#define _vector_sub_g(r,s1,s2) \
   _complex_sub((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_sub((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_sub((r).c[2],(s1).c[2],(s2).c[2])

 /***** r=s1+i*s2 */
#define _vector_i_add_g(r,s1,s2) \
   _complex_i_add((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_i_add((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_i_add((r).c[2],(s1).c[2],(s2).c[2])

 /***** r=s1-i*s2 */
#define _vector_i_sub_g(r,s1,s2) \
   _complex_i_sub((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_i_sub((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_i_sub((r).c[2],(s1).c[2],(s2).c[2])

 /***** r+=s */
#define _vector_add_assign_g(r,s) \
   _complex_add_assign((r).c[0],(s).c[0]); \
   _complex_add_assign((r).c[1],(s).c[1]); \
   _complex_add_assign((r).c[2],(s).c[2])

 /***** r-=s */
#define _vector_sub_assign_g(r,s) \
   _complex_sub_assign((r).c[0],(s).c[0]); \
   _complex_sub_assign((r).c[1],(s).c[1]); \
   _complex_sub_assign((r).c[2],(s).c[2])

 /***** r+=i*s */
#define _vector_i_add_assign_g(r,s) \
   _complex_i_add_assign((r).c[0],(s).c[0]); \
   _complex_i_add_assign((r).c[1],(s).c[1]); \
   _complex_i_add_assign((r).c[2],(s).c[2])

 /***** r-=i*s */
#define _vector_i_sub_assign_g(r,s) \
   _complex_i_sub_assign((r).c[0],(s).c[0]); \
   _complex_i_sub_assign((r).c[1],(s).c[1]); \
   _complex_i_sub_assign((r).c[2],(s).c[2])

 /***** k=Re(r^*s) */
#define _vector_prod_re_g(k,r,s) \
   (k)=_complex_prod_re((r).c[0],(s).c[0]);\
   (k)+=_complex_prod_re((r).c[1],(s).c[1]); \
   (k)+=_complex_prod_re((r).c[2],(s).c[2])

 /***** k=Im(r*s) */
#define _vector_prod_im_g(k,r,s) \
   (k)=_complex_prod_im((r).c[0],(s).c[0]);\
   (k)+=_complex_prod_im((r).c[1],(s).c[1]); \
   (k)+=_complex_prod_im((r).c[2],(s).c[2])

 /***** r+=z*s (z complex) */
#define _vector_mulc_add_assign_g(r,z,s) \
   _complex_mul_assign((r).c[0],(z),(s).c[0]); \
   _complex_mul_assign((r).c[1],(z),(s).c[1]); \
   _complex_mul_assign((r).c[2],(z),(s).c[2])

 /***** r+=k*s (k real) */
#define _vector_mul_add_assign_g(r,k,s) \
   _complex_mulr_assign((r).c[0],(k),(s).c[0]); \
   _complex_mulr_assign((r).c[1],(k),(s).c[1]); \
   _complex_mulr_assign((r).c[2],(k),(s).c[2])

 /***** r=k1*s1+k2*s2 (k1,k2 real, s1,s2 vectors) */
#define _vector_lc_g(r,k1,s1,k2,s2) \
   _complex_rlc((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
   _complex_rlc((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
   _complex_rlc((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2])

 /***** r+=k1*s1+k2*s2 (k1,k2 real, s1,s2 vectors) */
#define _vector_lc_add_assign_g(r,k1,s1,k2,s2) \
   _complex_rlc_assign((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
   _complex_rlc_assign((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
   _complex_rlc_assign((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2])

 /***** r=z1*s1+z2*s2 (z1,z2 complex, s1,s2 vectors) */
#define _vector_clc_g(r,z1,s1,z2,s2) \
   _complex_clc((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
   _complex_clc((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
   _complex_clc((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2])

 /***** r=z1*s1+z2*s2 (z1,z2 complex, s1,s2 vectors) */
#define _vector_clc_add_assign_g(r,z1,s1,z2,s2) \
   _complex_clc_assign((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
   _complex_clc_assign((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
   _complex_clc_assign((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2])

 /***** z+=r^*s (c complex) */
#define _vector_prod_assign_g(z,r,s) \
   _complex_prod_assign((z),(r).c[0],(s).c[0]); \
   _complex_prod_assign((z),(r).c[1],(s).c[1]); \
   _complex_prod_assign((z),(r).c[2],(s).c[2])

 /***** r-=z*s (z complex) */
#define _vector_project_g(r,z,s) \
   _complex_mul_sub_assign((r).c[0],(z),(s).c[0]); \
   _complex_mul_sub_assign((r).c[1],(z),(s).c[1]); \
   _complex_mul_sub_assign((r).c[2],(z),(s).c[2])

 /***** SU(N) matrix u times SU(N) vector s */
 /***** r=u*s */
#define _suNg_multiply(r,u,s) \
      _complex_mul((r).c[0],(u).c[0],(s).c[0]);\
      _complex_mul_assign((r).c[0],(u).c[1],(s).c[1]); \
      _complex_mul_assign((r).c[0],(u).c[2],(s).c[2]); \
      _complex_mul((r).c[1],(u).c[3],(s).c[0]);\
      _complex_mul_assign((r).c[1],(u).c[4],(s).c[1]); \
      _complex_mul_assign((r).c[1],(u).c[5],(s).c[2]); \
      _complex_mul((r).c[2],(u).c[6],(s).c[0]);\
      _complex_mul_assign((r).c[2],(u).c[7],(s).c[1]); \
      _complex_mul_assign((r).c[2],(u).c[8],(s).c[2])

 /***** SU(N) matrix u^dagger times SU(N) vector s */
 /***** r=(u^dagger)*s */
#define _suNg_inverse_multiply(r,u,s) \
      _complex_mul_star((r).c[0],(s).c[0],(u).c[0]);\
      _complex_mul_star_assign((r).c[0],(s).c[1],(u).c[3]); \
      _complex_mul_star_assign((r).c[0],(s).c[2],(u).c[6]); \
      _complex_mul_star((r).c[1],(s).c[0],(u).c[1]);\
      _complex_mul_star_assign((r).c[1],(s).c[1],(u).c[4]); \
      _complex_mul_star_assign((r).c[1],(s).c[2],(u).c[7]); \
      _complex_mul_star((r).c[2],(s).c[0],(u).c[2]);\
      _complex_mul_star_assign((r).c[2],(s).c[1],(u).c[5]); \
      _complex_mul_star_assign((r).c[2],(s).c[2],(u).c[8])

 /***** r+=k*s (k real) */
#define _algebra_vector_mul_add_assign_g(r,k,s) \
      (r).c[0]+=(k)*(s).c[0]; \
      (r).c[1]+=(k)*(s).c[1]; \
      (r).c[2]+=(k)*(s).c[2]; \
      (r).c[3]+=(k)*(s).c[3]; \
      (r).c[4]+=(k)*(s).c[4]; \
      (r).c[5]+=(k)*(s).c[5]; \
      (r).c[6]+=(k)*(s).c[6]; \
      (r).c[7]+=(k)*(s).c[7]

 /***** r=k*s (k real) */
#define _algebra_vector_mul_g(r,k,s) \
      (r).c[0]=(k)*(s).c[0]; \
      (r).c[1]=(k)*(s).c[1]; \
      (r).c[2]=(k)*(s).c[2]; \
      (r).c[3]=(k)*(s).c[3]; \
      (r).c[4]=(k)*(s).c[4]; \
      (r).c[5]=(k)*(s).c[5]; \
      (r).c[6]=(k)*(s).c[6]; \
      (r).c[7]=(k)*(s).c[7]

 /***** r=0  */
#define _algebra_vector_zero_g(r) \
      (r).c[0]=0.; \
      (r).c[1]=0.; \
      (r).c[2]=0.; \
      (r).c[3]=0.; \
      (r).c[4]=0.; \
      (r).c[5]=0.; \
      (r).c[6]=0.; \
      (r).c[7]=0.

 /***** k=|v|^2  */
#define _algebra_vector_sqnorm_g(k,r) \
   (k)=((r).c[0]*(r).c[0])+ \
       ((r).c[1]*(r).c[1])+ \
       ((r).c[2]*(r).c[2])+ \
       ((r).c[3]*(r).c[3])+ \
       ((r).c[4]*(r).c[4])+ \
       ((r).c[5]*(r).c[5])+ \
       ((r).c[6]*(r).c[6])+ \
       ((r).c[7]*(r).c[7])

 /***********************************************************************************
 *
 * Macros for SU(N) matrices
 *
 * Arguments are variables of type suN
 *
 *******************************************************************************/

 /***** u=v^dagger */
#define _suNg_dagger(u,v) \
   _complex_star((u).c[0],(v).c[0]); \
   _complex_star((u).c[1],(v).c[3]); \
   _complex_star((u).c[2],(v).c[6]); \
   _complex_star((u).c[3],(v).c[1]); \
   _complex_star((u).c[4],(v).c[4]); \
   _complex_star((u).c[5],(v).c[7]); \
   _complex_star((u).c[6],(v).c[2]); \
   _complex_star((u).c[7],(v).c[5]); \
   _complex_star((u).c[8],(v).c[8])

 /***** u=v*w */
#define _suNg_times_suNg(u,v,w) \
      _complex_mul((u).c[0],(v).c[0],(w).c[0]);\
      _complex_mul_assign((u).c[0],(v).c[1],(w).c[3]); \
      _complex_mul_assign((u).c[0],(v).c[2],(w).c[6]); \
      _complex_mul((u).c[1],(v).c[0],(w).c[1]);\
      _complex_mul_assign((u).c[1],(v).c[1],(w).c[4]); \
      _complex_mul_assign((u).c[1],(v).c[2],(w).c[7]); \
      _complex_mul((u).c[2],(v).c[0],(w).c[2]);\
      _complex_mul_assign((u).c[2],(v).c[1],(w).c[5]); \
      _complex_mul_assign((u).c[2],(v).c[2],(w).c[8]); \
      _complex_mul((u).c[3],(v).c[3],(w).c[0]);\
      _complex_mul_assign((u).c[3],(v).c[4],(w).c[3]); \
      _complex_mul_assign((u).c[3],(v).c[5],(w).c[6]); \
      _complex_mul((u).c[4],(v).c[3],(w).c[1]);\
      _complex_mul_assign((u).c[4],(v).c[4],(w).c[4]); \
      _complex_mul_assign((u).c[4],(v).c[5],(w).c[7]); \
      _complex_mul((u).c[5],(v).c[3],(w).c[2]);\
      _complex_mul_assign((u).c[5],(v).c[4],(w).c[5]); \
      _complex_mul_assign((u).c[5],(v).c[5],(w).c[8]); \
      _complex_mul((u).c[6],(v).c[6],(w).c[0]);\
      _complex_mul_assign((u).c[6],(v).c[7],(w).c[3]); \
      _complex_mul_assign((u).c[6],(v).c[8],(w).c[6]); \
      _complex_mul((u).c[7],(v).c[6],(w).c[1]);\
      _complex_mul_assign((u).c[7],(v).c[7],(w).c[4]); \
      _complex_mul_assign((u).c[7],(v).c[8],(w).c[7]); \
      _complex_mul((u).c[8],(v).c[6],(w).c[2]);\
      _complex_mul_assign((u).c[8],(v).c[7],(w).c[5]); \
      _complex_mul_assign((u).c[8],(v).c[8],(w).c[8])

 /***** u=v*w^+ */
#define _suNg_times_suNg_dagger(u,v,w) \
      _complex_mul_star((u).c[0],(v).c[0],(w).c[0]);\
      _complex_mul_star_assign((u).c[0],(v).c[1],(w).c[1]); \
      _complex_mul_star_assign((u).c[0],(v).c[2],(w).c[2]); \
      _complex_mul_star((u).c[1],(v).c[0],(w).c[3]);\
      _complex_mul_star_assign((u).c[1],(v).c[1],(w).c[4]); \
      _complex_mul_star_assign((u).c[1],(v).c[2],(w).c[5]); \
      _complex_mul_star((u).c[2],(v).c[0],(w).c[6]);\
      _complex_mul_star_assign((u).c[2],(v).c[1],(w).c[7]); \
      _complex_mul_star_assign((u).c[2],(v).c[2],(w).c[8]); \
      _complex_mul_star((u).c[3],(v).c[3],(w).c[0]);\
      _complex_mul_star_assign((u).c[3],(v).c[4],(w).c[1]); \
      _complex_mul_star_assign((u).c[3],(v).c[5],(w).c[2]); \
      _complex_mul_star((u).c[4],(v).c[3],(w).c[3]);\
      _complex_mul_star_assign((u).c[4],(v).c[4],(w).c[4]); \
      _complex_mul_star_assign((u).c[4],(v).c[5],(w).c[5]); \
      _complex_mul_star((u).c[5],(v).c[3],(w).c[6]);\
      _complex_mul_star_assign((u).c[5],(v).c[4],(w).c[7]); \
      _complex_mul_star_assign((u).c[5],(v).c[5],(w).c[8]); \
      _complex_mul_star((u).c[6],(v).c[6],(w).c[0]);\
      _complex_mul_star_assign((u).c[6],(v).c[7],(w).c[1]); \
      _complex_mul_star_assign((u).c[6],(v).c[8],(w).c[2]); \
      _complex_mul_star((u).c[7],(v).c[6],(w).c[3]);\
      _complex_mul_star_assign((u).c[7],(v).c[7],(w).c[4]); \
      _complex_mul_star_assign((u).c[7],(v).c[8],(w).c[5]); \
      _complex_mul_star((u).c[8],(v).c[6],(w).c[6]);\
      _complex_mul_star_assign((u).c[8],(v).c[7],(w).c[7]); \
      _complex_mul_star_assign((u).c[8],(v).c[8],(w).c[8])

 /***** u=v^+*w */
#define _suNg_dagger_times_suNg(u,v,w) \
      _complex_mul_star((u).c[0],(w).c[0],(v).c[0]);\
      _complex_mul_star_assign((u).c[0],(w).c[3],(v).c[3]); \
      _complex_mul_star_assign((u).c[0],(w).c[6],(v).c[6]); \
      _complex_mul_star((u).c[1],(w).c[1],(v).c[0]);\
      _complex_mul_star_assign((u).c[1],(w).c[4],(v).c[3]); \
      _complex_mul_star_assign((u).c[1],(w).c[7],(v).c[6]); \
      _complex_mul_star((u).c[2],(w).c[2],(v).c[0]);\
      _complex_mul_star_assign((u).c[2],(w).c[5],(v).c[3]); \
      _complex_mul_star_assign((u).c[2],(w).c[8],(v).c[6]); \
      _complex_mul_star((u).c[3],(w).c[0],(v).c[1]);\
      _complex_mul_star_assign((u).c[3],(w).c[3],(v).c[4]); \
      _complex_mul_star_assign((u).c[3],(w).c[6],(v).c[7]); \
      _complex_mul_star((u).c[4],(w).c[1],(v).c[1]);\
      _complex_mul_star_assign((u).c[4],(w).c[4],(v).c[4]); \
      _complex_mul_star_assign((u).c[4],(w).c[7],(v).c[7]); \
      _complex_mul_star((u).c[5],(w).c[2],(v).c[1]);\
      _complex_mul_star_assign((u).c[5],(w).c[5],(v).c[4]); \
      _complex_mul_star_assign((u).c[5],(w).c[8],(v).c[7]); \
      _complex_mul_star((u).c[6],(w).c[0],(v).c[2]);\
      _complex_mul_star_assign((u).c[6],(w).c[3],(v).c[5]); \
      _complex_mul_star_assign((u).c[6],(w).c[6],(v).c[8]); \
      _complex_mul_star((u).c[7],(w).c[1],(v).c[2]);\
      _complex_mul_star_assign((u).c[7],(w).c[4],(v).c[5]); \
      _complex_mul_star_assign((u).c[7],(w).c[7],(v).c[8]); \
      _complex_mul_star((u).c[8],(w).c[2],(v).c[2]);\
      _complex_mul_star_assign((u).c[8],(w).c[5],(v).c[5]); \
      _complex_mul_star_assign((u).c[8],(w).c[8],(v).c[8])

 /***** u=0 */
#define _suNg_zero(u) \
    _complex_0((u).c[0]);\
    _complex_0((u).c[1]);\
    _complex_0((u).c[2]);\
    _complex_0((u).c[3]);\
    _complex_0((u).c[4]);\
    _complex_0((u).c[5]);\
    _complex_0((u).c[6]);\
    _complex_0((u).c[7]);\
    _complex_0((u).c[8])

 /***** u=1 */
#define _suNg_unit(u) \
   _complex_1((u).c[0]);\
   _complex_0((u).c[1]);\
   _complex_0((u).c[2]);\
   _complex_0((u).c[3]);\
   _complex_1((u).c[4]);\
   _complex_0((u).c[5]);\
   _complex_0((u).c[6]);\
   _complex_0((u).c[7]);\
   _complex_1((u).c[8])

 /***** u=-v */
#define _suNg_minus(u,v) \
   _complex_minus((u).c[0],(v).c[0]);\
   _complex_minus((u).c[1],(v).c[1]);\
   _complex_minus((u).c[2],(v).c[2]);\
   _complex_minus((u).c[3],(v).c[3]);\
   _complex_minus((u).c[4],(v).c[4]);\
   _complex_minus((u).c[5],(v).c[5]);\
   _complex_minus((u).c[6],(v).c[6]);\
   _complex_minus((u).c[7],(v).c[7]);\
   _complex_minus((u).c[8],(v).c[8])

 /***** u=r*v (u,v mat, r real) */
#define _suNg_mul(u,r,v) \
   _complex_mulr((u).c[0],(r),(v).c[0]);\
   _complex_mulr((u).c[1],(r),(v).c[1]);\
   _complex_mulr((u).c[2],(r),(v).c[2]);\
   _complex_mulr((u).c[3],(r),(v).c[3]);\
   _complex_mulr((u).c[4],(r),(v).c[4]);\
   _complex_mulr((u).c[5],(r),(v).c[5]);\
   _complex_mulr((u).c[6],(r),(v).c[6]);\
   _complex_mulr((u).c[7],(r),(v).c[7]);\
   _complex_mulr((u).c[8],(r),(v).c[8])

 /***** u+=v */
#define _suNg_add_assign(u,v) \
   _complex_add_assign((u).c[0],(v).c[0]);\
   _complex_add_assign((u).c[1],(v).c[1]);\
   _complex_add_assign((u).c[2],(v).c[2]);\
   _complex_add_assign((u).c[3],(v).c[3]);\
   _complex_add_assign((u).c[4],(v).c[4]);\
   _complex_add_assign((u).c[5],(v).c[5]);\
   _complex_add_assign((u).c[6],(v).c[6]);\
   _complex_add_assign((u).c[7],(v).c[7]);\
   _complex_add_assign((u).c[8],(v).c[8])

 /***** u-=v */
#define _suNg_sub_assign(u,v) \
   _complex_sub_assign((u).c[0],(v).c[0]);\
   _complex_sub_assign((u).c[1],(v).c[1]);\
   _complex_sub_assign((u).c[2],(v).c[2]);\
   _complex_sub_assign((u).c[3],(v).c[3]);\
   _complex_sub_assign((u).c[4],(v).c[4]);\
   _complex_sub_assign((u).c[5],(v).c[5]);\
   _complex_sub_assign((u).c[6],(v).c[6]);\
   _complex_sub_assign((u).c[7],(v).c[7]);\
   _complex_sub_assign((u).c[8],(v).c[8])

 /***** k=| u |2 ) */
#define _suNg_sqnorm(k,u) \
   (k)=0.;\
   (k)+=_complex_prod_re((u).c[0],(u).c[0]); \
   (k)+=_complex_prod_re((u).c[1],(u).c[1]); \
   (k)+=_complex_prod_re((u).c[2],(u).c[2]); \
   (k)+=_complex_prod_re((u).c[3],(u).c[3]); \
   (k)+=_complex_prod_re((u).c[4],(u).c[4]); \
   (k)+=_complex_prod_re((u).c[5],(u).c[5]); \
   (k)+=_complex_prod_re((u).c[6],(u).c[6]); \
   (k)+=_complex_prod_re((u).c[7],(u).c[7]); \
   (k)+=_complex_prod_re((u).c[8],(u).c[8])

 /***** k=| 1 - u |2 ) */
#define _suNg_sqnorm_m1(k,u) \
   (k)=\
    +_complex_prod_m1_re((u).c[0],(u).c[0])\
    +_complex_prod_re((u).c[1],(u).c[1])\
    +_complex_prod_re((u).c[2],(u).c[2])\
    +_complex_prod_re((u).c[3],(u).c[3])\
    +_complex_prod_m1_re((u).c[4],(u).c[4])\
    +_complex_prod_re((u).c[5],(u).c[5])\
    +_complex_prod_re((u).c[6],(u).c[6])\
    +_complex_prod_re((u).c[7],(u).c[7])\
    +_complex_prod_m1_re((u).c[8],(u).c[8])

 /***** k=Re Tr (u) */
#define _suNg_trace_re(k,u) \
   (k)=_complex_re((u).c[0])+ \
       _complex_re((u).c[4])+ \
       _complex_re((u).c[8])

 /***** k=Im Tr (u) */
#define _suNg_trace_im(k,u) \
   (k)=_complex_im((u).c[0])+ \
       _complex_im((u).c[4])+ \
       _complex_im((u).c[8])

 /***** u=v - v^+ -1/N Tr(v - v^+)*I */
#define _suNg_2TA(u,v) \
   {\
      double _trim; _suNg_trace_im(_trim,(v)); _trim*=(2./3.);\
      (u).c[0].re= 0.; \
      (u).c[0].im= 2.*(v).c[0].im-_trim; \
      (u).c[1].re= (v).c[1].re-(v).c[3].re; \
      (u).c[1].im= (v).c[3].im+(v).c[1].im; \
      (u).c[3].re= -(u).c[1].re; \
      (u).c[3].im= (u).c[1].im; \
      (u).c[2].re= (v).c[2].re-(v).c[6].re; \
      (u).c[2].im= (v).c[6].im+(v).c[2].im; \
      (u).c[6].re= -(u).c[2].re; \
      (u).c[6].im= (u).c[2].im; \
      (u).c[4].re= 0.; \
      (u).c[4].im= 2.*(v).c[4].im-_trim; \
      (u).c[5].re= (v).c[5].re-(v).c[7].re; \
      (u).c[5].im= (v).c[7].im+(v).c[5].im; \
      (u).c[7].re= -(u).c[5].re; \
      (u).c[7].im= (u).c[5].im; \
      (u).c[8].re= 0.; \
      (u).c[8].im= 2.*(v).c[8].im-_trim; \
   }((void)0) 

 /***** u=0.5(v - v^+) -1/(2N) Tr(v - v^+)*I */
#define _suNg_TA(u,v) \
   {\
      double _trim; _suNg_trace_im(_trim,(v)); _trim*=(1./3.);\
      (u).c[0].re= 0.; \
      (u).c[0].im= (v).c[0].im-_trim; \
      (u).c[1].re= 0.5*((v).c[1].re-(v).c[3].re); \
      (u).c[1].im= 0.5*((v).c[3].im+(v).c[1].im); \
      (u).c[3].re= -(u).c[1].re; \
      (u).c[3].im= (u).c[1].im; \
      (u).c[2].re= 0.5*((v).c[2].re-(v).c[6].re); \
      (u).c[2].im= 0.5*((v).c[6].im+(v).c[2].im); \
      (u).c[6].re= -(u).c[2].re; \
      (u).c[6].im= (u).c[2].im; \
      (u).c[4].re= 0.; \
      (u).c[4].im= (v).c[4].im-_trim; \
      (u).c[5].re= 0.5*((v).c[5].re-(v).c[7].re); \
      (u).c[5].im= 0.5*((v).c[7].im+(v).c[5].im); \
      (u).c[7].re= -(u).c[5].re; \
      (u).c[7].im= (u).c[5].im; \
      (u).c[8].re= 0.; \
      (u).c[8].im= (v).c[8].im-_trim; \
   }((void)0) 

 /***** This fuction compute the hmc force matrix */
 /***** this fuction accumulates on the original matrix u */
#define _suNg_FMAT(u,s) \
   _complex_mul_star_assign((u).c[0],(s).c[0].c[0],(s).c[2].c[0]); \
   _complex_mul_star_assign((u).c[0],(s).c[1].c[0],(s).c[3].c[0]);\
   _complex_mul_star_assign((u).c[1],(s).c[0].c[0],(s).c[2].c[1]); \
   _complex_mul_star_assign((u).c[1],(s).c[1].c[0],(s).c[3].c[1]);\
   _complex_mul_star_assign((u).c[2],(s).c[0].c[0],(s).c[2].c[2]); \
   _complex_mul_star_assign((u).c[2],(s).c[1].c[0],(s).c[3].c[2]);\
   _complex_mul_star_assign((u).c[3],(s).c[0].c[1],(s).c[2].c[0]); \
   _complex_mul_star_assign((u).c[3],(s).c[1].c[1],(s).c[3].c[0]);\
   _complex_mul_star_assign((u).c[4],(s).c[0].c[1],(s).c[2].c[1]); \
   _complex_mul_star_assign((u).c[4],(s).c[1].c[1],(s).c[3].c[1]);\
   _complex_mul_star_assign((u).c[5],(s).c[0].c[1],(s).c[2].c[2]); \
   _complex_mul_star_assign((u).c[5],(s).c[1].c[1],(s).c[3].c[2]);\
   _complex_mul_star_assign((u).c[6],(s).c[0].c[2],(s).c[2].c[0]); \
   _complex_mul_star_assign((u).c[6],(s).c[1].c[2],(s).c[3].c[0]);\
   _complex_mul_star_assign((u).c[7],(s).c[0].c[2],(s).c[2].c[1]); \
   _complex_mul_star_assign((u).c[7],(s).c[1].c[2],(s).c[3].c[1]);\
   _complex_mul_star_assign((u).c[8],(s).c[0].c[2],(s).c[2].c[2]); \
   _complex_mul_star_assign((u).c[8],(s).c[1].c[2],(s).c[3].c[2])

 /***********************************************************************************
 *
 * Macros for spinors
 *
 * Arguments are variables of type spinors
 *
 *******************************************************************************/

 /*****  r=0  (r spinor) */
#define _spinor_zero_g(r) \
  _vector_zero_g((r).c[0]); \
  _vector_zero_g((r).c[1]); \
  _vector_zero_g((r).c[2]); \
  _vector_zero_g((r).c[3])

 /*****  s=g5*r (r,s spinors, g5 matrix) */
#define _spinor_g5_g(s,r) \
  (s).c[0]=(r).c[0]; \
  (s).c[1]=(r).c[1]; \
  _vector_minus_g((s).c[2],(r).c[2]); \
  _vector_minus_g((s).c[3],(r).c[3])

 /*****  r=g5*r (r,s spinors, g5 matrix) */
#define _spinor_g5_assign_g(r) \
  _vector_minus_g((r).c[2],(r).c[2]); \
  _vector_minus_g((r).c[3],(r).c[3])

 /*****  s=-r (r,s spinors) */
#define _spinor_minus_g(s,r) \
  _vector_minus_g((s).c[0],(r).c[0]); \
  _vector_minus_g((s).c[1],(r).c[1]); \
  _vector_minus_g((s).c[2],(r).c[2]); \
  _vector_minus_g((s).c[3],(r).c[3])

 /*****  r=k*s (k real; r,s spinors) */
#define _spinor_mul_g(r,k,s) \
  _vector_mul_g((r).c[0],k,(s).c[0]); \
  _vector_mul_g((r).c[1],k,(s).c[1]); \
  _vector_mul_g((r).c[2],k,(s).c[2]); \
  _vector_mul_g((r).c[3],k,(s).c[3])

 /*****  r=z*s (z complex; r,s spinors) */
#define _spinor_mulc_g(r,z,s) \
  _vector_mulc_g((r).c[0],z,(s).c[0]); \
  _vector_mulc_g((r).c[1],z,(s).c[1]); \
  _vector_mulc_g((r).c[2],z,(s).c[2]); \
  _vector_mulc_g((r).c[3],z,(s).c[3])

 /*****  r+=z*s (z complex; r,s spinors) */
#define _spinor_mulc_add_assign_g(r,z,s) \
  _vector_mulc_add_assign_g((r).c[0],(z),(s).c[0]); \
  _vector_mulc_add_assign_g((r).c[1],(z),(s).c[1]); \
  _vector_mulc_add_assign_g((r).c[2],(z),(s).c[2]); \
  _vector_mulc_add_assign_g((r).c[3],(z),(s).c[3])

 /*****  r+=k*s (k real; r,s spinors) */
#define _spinor_mul_add_assign_g(r,k,s) \
  _vector_mul_add_assign_g((r).c[0],(k),(s).c[0]); \
  _vector_mul_add_assign_g((r).c[1],(k),(s).c[1]); \
  _vector_mul_add_assign_g((r).c[2],(k),(s).c[2]); \
  _vector_mul_add_assign_g((r).c[3],(k),(s).c[3])

 /*****  r=k1*s1+k2*s2 (k1,k2 real; r,s1,s2 spinors) */
#define _spinor_lc_g(r,k1,s1,k2,s2) \
  _vector_lc_g((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
  _vector_lc_g((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
  _vector_lc_g((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2]); \
  _vector_lc_g((r).c[3],(k1),(s1).c[3],(k2),(s2).c[3])

 /*****  r+=k1*s1+k2*s2 (k1,k2 real; r,s1,s2 spinors) */
#define _spinor_lc_add_assign_g(r,k1,s1,k2,s2) \
  _vector_lc_add_assign_g((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
  _vector_lc_add_assign_g((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
  _vector_lc_add_assign_g((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2]); \
  _vector_lc_add_assign_g((r).c[3],(k1),(s1).c[3],(k2),(s2).c[3])

 /*****  r=z1*s1+z2*s2 (z1,z2 complex; r,s1,s2 spinors) */
#define _spinor_clc_g(r,z1,s1,z2,s2) \
  _vector_clc_g((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
  _vector_clc_g((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
  _vector_clc_g((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2]); \
  _vector_clc_g((r).c[3],(z1),(s1).c[3],(z2),(s2).c[3])

 /*****  r+=z1*s1+z2*s2 (z1,z2 complex; r,s1,s2 spinors) */
#define _spinor_clc_add_assign_g(r,z1,s1,z2,s2) \
  _vector_clc_add_assign_g((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
  _vector_clc_add_assign_g((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
  _vector_clc_add_assign_g((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2]); \
  _vector_clc_add_assign_g((r).c[3],(z1),(s1).c[3],(z2),(s2).c[3])

 /*****  r=s1+s2 (r,s1,s2 spinors) */
#define _spinor_add_g(r,s1,s2) \
  _vector_add_g((r).c[0],(s1).c[0],(s2).c[0]); \
  _vector_add_g((r).c[1],(s1).c[1],(s2).c[1]); \
  _vector_add_g((r).c[2],(s1).c[2],(s2).c[2]); \
  _vector_add_g((r).c[3],(s1).c[3],(s2).c[3])

 /*****  r=s1-s2 (r,s1,s2 spinors) */
#define _spinor_sub_g(r,s1,s2) \
  _vector_sub_g((r).c[0],(s1).c[0],(s2).c[0]); \
  _vector_sub_g((r).c[1],(s1).c[1],(s2).c[1]); \
  _vector_sub_g((r).c[2],(s1).c[2],(s2).c[2]); \
  _vector_sub_g((r).c[3],(s1).c[3],(s2).c[3])

 /*****  r+=s (r,s spinors) */
#define _spinor_add_assign_g(r,s) \
  _vector_add_assign_g((r).c[0],(s).c[0]); \
  _vector_add_assign_g((r).c[1],(s).c[1]); \
  _vector_add_assign_g((r).c[2],(s).c[2]); \
  _vector_add_assign_g((r).c[3],(s).c[3])

 /*****  r-=s (r,s spinors) */
#define _spinor_sub_assign_g(r,s) \
  _vector_sub_assign_g((r).c[0],(s).c[0]); \
  _vector_sub_assign_g((r).c[1],(s).c[1]); \
  _vector_sub_assign_g((r).c[2],(s).c[2]); \
  _vector_sub_assign_g((r).c[3],(s).c[3])

 /*****  r+=i*s (r,s spinors) */
#define _spinor_i_add_assign_g(r,s) \
  _vector_i_add_assign_g((r).c[0],(s).c[0]); \
  _vector_i_add_assign_g((r).c[1],(s).c[1]); \
  _vector_i_add_assign_g((r).c[2],(s).c[2]); \
  _vector_i_add_assign_g((r).c[3],(s).c[3])

 /*****  r-=i*s (r,s spinors) */
#define _spinor_i_sub_assign_g(r,s) \
  _vector_i_sub_assign_g((r).c[0],(s).c[0]); \
  _vector_i_sub_assign_g((r).c[1],(s).c[1]); \
  _vector_i_sub_assign_g((r).c[2],(s).c[2]); \
  _vector_i_sub_assign_g((r).c[3],(s).c[3])

 /***** k=Real part of the scalar product r*s (r,s spinors) */
#define _spinor_prod_re_g(k,r,s) \
   { \
      double _tmp; (k)=0.; \
      _vector_prod_re_g(_tmp,(r).c[0],(s).c[0]); (k)+=_tmp;\
      _vector_prod_re_g(_tmp,(r).c[1],(s).c[1]); (k)+=_tmp;\
      _vector_prod_re_g(_tmp,(r).c[2],(s).c[2]); (k)+=_tmp;\
      _vector_prod_re_g(_tmp,(r).c[3],(s).c[3]); (k)+=_tmp;\
   }((void)0) 

 /***** k=Im part of the scalar product r*s (r,s spinors) */
#define _spinor_prod_im_g(k,r,s) \
   { \
      double _tmp; (k)=0.; \
      _vector_prod_im_g(_tmp,(r).c[0],(s).c[0]); (k)+=_tmp;\
      _vector_prod_im_g(_tmp,(r).c[1],(s).c[1]); (k)+=_tmp;\
      _vector_prod_im_g(_tmp,(r).c[2],(s).c[2]); (k)+=_tmp;\
      _vector_prod_im_g(_tmp,(r).c[3],(s).c[3]); (k)+=_tmp;\
   }((void)0) 

 /***** z+=r*s (r,s spinors, z complex) */
#define _spinor_prod_assign_g(z,r,s) \
  _vector_prod_assign_g((z),(r).c[0],(s).c[0]); \
  _vector_prod_assign_g((z),(r).c[1],(s).c[1]); \
  _vector_prod_assign_g((z),(r).c[2],(s).c[2]); \
  _vector_prod_assign_g((z),(r).c[3],(s).c[3])

 /***** k=Real part of the scalar product (g5*r)*s (r,s spinors) */
#define _spinor_g5_prod_re_g(k,r,s) \
   { \
      double _tmp; (k)=0.; \
      _vector_prod_re_g(_tmp,(r).c[0],(s).c[0]); (k)+=_tmp;\
      _vector_prod_re_g(_tmp,(r).c[1],(s).c[1]); (k)+=_tmp;\
      _vector_prod_re_g(_tmp,(r).c[2],(s).c[2]); (k)-=_tmp;\
      _vector_prod_re_g(_tmp,(r).c[3],(s).c[3]); (k)-=_tmp;\
   }((void)0) 

 /***** k=Imaginary part of the scalar product (g5*r)*s (r,s spinors) */
#define _spinor_g5_prod_im_g(k,r,s) \
   { \
      double _tmp; (k)=0.; \
      _vector_prod_im_g(_tmp,(r).c[0],(s).c[0]); (k)+=_tmp;\
      _vector_prod_im_g(_tmp,(r).c[1],(s).c[1]); (k)+=_tmp;\
      _vector_prod_im_g(_tmp,(r).c[2],(s).c[2]); (k)-=_tmp;\
      _vector_prod_im_g(_tmp,(r).c[3],(s).c[3]); (k)-=_tmp;\
   }((void)0) 

 /***** r-=z*s (z complex; r,s spinors) */
#define _spinor_project_g(r,z,s) \
  _vector_project_g((r).c[0],z,(s).c[0]); \
  _vector_project_g((r).c[1],z,(s).c[1]); \
  _vector_project_g((r).c[2],z,(s).c[2]); \
  _vector_project_g((r).c[3],z,(s).c[3])

 /***** r=(1-g0)/2 * s (r,s spinors) */
#define _spinor_pminus_g(r,s) \
  _vector_add_g((r).c[0],(s).c[0],(s).c[2]); \
  _vector_add_g((r).c[1],(s).c[1],(s).c[3]); \
  _vector_mul_g((r).c[0],0.5,(r).c[0]); \
  _vector_mul_g((r).c[1],0.5,(r).c[1]); \
  (r).c[2] = (r).c[0]; \
  (r).c[3] = (r).c[1]

 /***** r=(1+g0)/2 * s (r,s spinors) */
#define _spinor_pplus_g(r,s) \
  _vector_sub_g((r).c[0],(s).c[0],(s).c[2]); \
  _vector_sub_g((r).c[1],(s).c[1],(s).c[3]); \
  _vector_mul_g((r).c[0],0.5,(r).c[0]); \
  _vector_mul_g((r).c[1],0.5,(r).c[1]); \
  _vector_mul_g((r).c[2],-1.,(r).c[0]); \
  _vector_mul_g((r).c[3],-1.,(r).c[1])

 /***********************************************************************************
 *
 * The following macros are the same for single and double precision types
 *
 * Depending on the macro, arguments are variables of type suN_vector and suN
 * (or suN_vector_flt and suN_flt)
 *
 *******************************************************************************/

 /***** r=0 */
#define _vector_zero_f(r) \
   _complex_0((r).c[0]); \
   _complex_0((r).c[1]); \
   _complex_0((r).c[2])

 /***** r=-s */
#define _vector_minus_f(r,s) \
   _complex_minus((r).c[0],(s).c[0]); \
   _complex_minus((r).c[1],(s).c[1]); \
   _complex_minus((r).c[2],(s).c[2])

 /***** r=k*s (k real) */
#define _vector_mul_f(r,k,s) \
   _complex_mulr((r).c[0],(k),(s).c[0]); \
   _complex_mulr((r).c[1],(k),(s).c[1]); \
   _complex_mulr((r).c[2],(k),(s).c[2])

 /***** r=z*s (z complex) */
#define _vector_mulc_f(r,z,s) \
   _complex_mul((r).c[0],(z),(s).c[0]); \
   _complex_mul((r).c[1],(z),(s).c[1]); \
   _complex_mul((r).c[2],(z),(s).c[2])

 /***** r=s1+s2 */
#define _vector_add_f(r,s1,s2) \
   _complex_add((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_add((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_add((r).c[2],(s1).c[2],(s2).c[2])

 /***** r=s1-s2 */
#define _vector_sub_f(r,s1,s2) \
   _complex_sub((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_sub((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_sub((r).c[2],(s1).c[2],(s2).c[2])

 /***** r=s1+i*s2 */
#define _vector_i_add_f(r,s1,s2) \
   _complex_i_add((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_i_add((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_i_add((r).c[2],(s1).c[2],(s2).c[2])

 /***** r=s1-i*s2 */
#define _vector_i_sub_f(r,s1,s2) \
   _complex_i_sub((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_i_sub((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_i_sub((r).c[2],(s1).c[2],(s2).c[2])

 /***** r+=s */
#define _vector_add_assign_f(r,s) \
   _complex_add_assign((r).c[0],(s).c[0]); \
   _complex_add_assign((r).c[1],(s).c[1]); \
   _complex_add_assign((r).c[2],(s).c[2])

 /***** r-=s */
#define _vector_sub_assign_f(r,s) \
   _complex_sub_assign((r).c[0],(s).c[0]); \
   _complex_sub_assign((r).c[1],(s).c[1]); \
   _complex_sub_assign((r).c[2],(s).c[2])

 /***** r+=i*s */
#define _vector_i_add_assign_f(r,s) \
   _complex_i_add_assign((r).c[0],(s).c[0]); \
   _complex_i_add_assign((r).c[1],(s).c[1]); \
   _complex_i_add_assign((r).c[2],(s).c[2])

 /***** r-=i*s */
#define _vector_i_sub_assign_f(r,s) \
   _complex_i_sub_assign((r).c[0],(s).c[0]); \
   _complex_i_sub_assign((r).c[1],(s).c[1]); \
   _complex_i_sub_assign((r).c[2],(s).c[2])

 /***** k=Re(r^*s) */
#define _vector_prod_re_f(k,r,s) \
   (k)=_complex_prod_re((r).c[0],(s).c[0]);\
   (k)+=_complex_prod_re((r).c[1],(s).c[1]); \
   (k)+=_complex_prod_re((r).c[2],(s).c[2])

 /***** k=Im(r*s) */
#define _vector_prod_im_f(k,r,s) \
   (k)=_complex_prod_im((r).c[0],(s).c[0]);\
   (k)+=_complex_prod_im((r).c[1],(s).c[1]); \
   (k)+=_complex_prod_im((r).c[2],(s).c[2])

 /***** r+=z*s (z complex) */
#define _vector_mulc_add_assign_f(r,z,s) \
   _complex_mul_assign((r).c[0],(z),(s).c[0]); \
   _complex_mul_assign((r).c[1],(z),(s).c[1]); \
   _complex_mul_assign((r).c[2],(z),(s).c[2])

 /***** r+=k*s (k real) */
#define _vector_mul_add_assign_f(r,k,s) \
   _complex_mulr_assign((r).c[0],(k),(s).c[0]); \
   _complex_mulr_assign((r).c[1],(k),(s).c[1]); \
   _complex_mulr_assign((r).c[2],(k),(s).c[2])

 /***** r=k1*s1+k2*s2 (k1,k2 real, s1,s2 vectors) */
#define _vector_lc_f(r,k1,s1,k2,s2) \
   _complex_rlc((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
   _complex_rlc((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
   _complex_rlc((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2])

 /***** r+=k1*s1+k2*s2 (k1,k2 real, s1,s2 vectors) */
#define _vector_lc_add_assign_f(r,k1,s1,k2,s2) \
   _complex_rlc_assign((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
   _complex_rlc_assign((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
   _complex_rlc_assign((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2])

 /***** r=z1*s1+z2*s2 (z1,z2 complex, s1,s2 vectors) */
#define _vector_clc_f(r,z1,s1,z2,s2) \
   _complex_clc((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
   _complex_clc((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
   _complex_clc((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2])

 /***** r=z1*s1+z2*s2 (z1,z2 complex, s1,s2 vectors) */
#define _vector_clc_add_assign_f(r,z1,s1,z2,s2) \
   _complex_clc_assign((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
   _complex_clc_assign((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
   _complex_clc_assign((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2])

 /***** z+=r^*s (c complex) */
#define _vector_prod_assign_f(z,r,s) \
   _complex_prod_assign((z),(r).c[0],(s).c[0]); \
   _complex_prod_assign((z),(r).c[1],(s).c[1]); \
   _complex_prod_assign((z),(r).c[2],(s).c[2])

 /***** r-=z*s (z complex) */
#define _vector_project_f(r,z,s) \
   _complex_mul_sub_assign((r).c[0],(z),(s).c[0]); \
   _complex_mul_sub_assign((r).c[1],(z),(s).c[1]); \
   _complex_mul_sub_assign((r).c[2],(z),(s).c[2])

 /***** SU(N) matrix u times SU(N) vector s */
 /***** r=u*s */
#define _suNf_multiply(r,u,s) \
      _complex_mul((r).c[0],(u).c[0],(s).c[0]);\
      _complex_mul_assign((r).c[0],(u).c[1],(s).c[1]); \
      _complex_mul_assign((r).c[0],(u).c[2],(s).c[2]); \
      _complex_mul((r).c[1],(u).c[3],(s).c[0]);\
      _complex_mul_assign((r).c[1],(u).c[4],(s).c[1]); \
      _complex_mul_assign((r).c[1],(u).c[5],(s).c[2]); \
      _complex_mul((r).c[2],(u).c[6],(s).c[0]);\
      _complex_mul_assign((r).c[2],(u).c[7],(s).c[1]); \
      _complex_mul_assign((r).c[2],(u).c[8],(s).c[2])

 /***** SU(N) matrix u^dagger times SU(N) vector s */
 /***** r=(u^dagger)*s */
#define _suNf_inverse_multiply(r,u,s) \
      _complex_mul_star((r).c[0],(s).c[0],(u).c[0]);\
      _complex_mul_star_assign((r).c[0],(s).c[1],(u).c[3]); \
      _complex_mul_star_assign((r).c[0],(s).c[2],(u).c[6]); \
      _complex_mul_star((r).c[1],(s).c[0],(u).c[1]);\
      _complex_mul_star_assign((r).c[1],(s).c[1],(u).c[4]); \
      _complex_mul_star_assign((r).c[1],(s).c[2],(u).c[7]); \
      _complex_mul_star((r).c[2],(s).c[0],(u).c[2]);\
      _complex_mul_star_assign((r).c[2],(s).c[1],(u).c[5]); \
      _complex_mul_star_assign((r).c[2],(s).c[2],(u).c[8])

 /***********************************************************************************
 *
 * Macros for SU(N) matrices
 *
 * Arguments are variables of type suN
 *
 *******************************************************************************/

 /***** u=v^dagger */
#define _suNf_dagger(u,v) \
   _complex_star((u).c[0],(v).c[0]); \
   _complex_star((u).c[1],(v).c[3]); \
   _complex_star((u).c[2],(v).c[6]); \
   _complex_star((u).c[3],(v).c[1]); \
   _complex_star((u).c[4],(v).c[4]); \
   _complex_star((u).c[5],(v).c[7]); \
   _complex_star((u).c[6],(v).c[2]); \
   _complex_star((u).c[7],(v).c[5]); \
   _complex_star((u).c[8],(v).c[8])

 /***** u=v*w */
#define _suNf_times_suNf(u,v,w) \
      _complex_mul((u).c[0],(v).c[0],(w).c[0]);\
      _complex_mul_assign((u).c[0],(v).c[1],(w).c[3]); \
      _complex_mul_assign((u).c[0],(v).c[2],(w).c[6]); \
      _complex_mul((u).c[1],(v).c[0],(w).c[1]);\
      _complex_mul_assign((u).c[1],(v).c[1],(w).c[4]); \
      _complex_mul_assign((u).c[1],(v).c[2],(w).c[7]); \
      _complex_mul((u).c[2],(v).c[0],(w).c[2]);\
      _complex_mul_assign((u).c[2],(v).c[1],(w).c[5]); \
      _complex_mul_assign((u).c[2],(v).c[2],(w).c[8]); \
      _complex_mul((u).c[3],(v).c[3],(w).c[0]);\
      _complex_mul_assign((u).c[3],(v).c[4],(w).c[3]); \
      _complex_mul_assign((u).c[3],(v).c[5],(w).c[6]); \
      _complex_mul((u).c[4],(v).c[3],(w).c[1]);\
      _complex_mul_assign((u).c[4],(v).c[4],(w).c[4]); \
      _complex_mul_assign((u).c[4],(v).c[5],(w).c[7]); \
      _complex_mul((u).c[5],(v).c[3],(w).c[2]);\
      _complex_mul_assign((u).c[5],(v).c[4],(w).c[5]); \
      _complex_mul_assign((u).c[5],(v).c[5],(w).c[8]); \
      _complex_mul((u).c[6],(v).c[6],(w).c[0]);\
      _complex_mul_assign((u).c[6],(v).c[7],(w).c[3]); \
      _complex_mul_assign((u).c[6],(v).c[8],(w).c[6]); \
      _complex_mul((u).c[7],(v).c[6],(w).c[1]);\
      _complex_mul_assign((u).c[7],(v).c[7],(w).c[4]); \
      _complex_mul_assign((u).c[7],(v).c[8],(w).c[7]); \
      _complex_mul((u).c[8],(v).c[6],(w).c[2]);\
      _complex_mul_assign((u).c[8],(v).c[7],(w).c[5]); \
      _complex_mul_assign((u).c[8],(v).c[8],(w).c[8])

 /***** u=v*w^+ */
#define _suNf_times_suNf_dagger(u,v,w) \
      _complex_mul_star((u).c[0],(v).c[0],(w).c[0]);\
      _complex_mul_star_assign((u).c[0],(v).c[1],(w).c[1]); \
      _complex_mul_star_assign((u).c[0],(v).c[2],(w).c[2]); \
      _complex_mul_star((u).c[1],(v).c[0],(w).c[3]);\
      _complex_mul_star_assign((u).c[1],(v).c[1],(w).c[4]); \
      _complex_mul_star_assign((u).c[1],(v).c[2],(w).c[5]); \
      _complex_mul_star((u).c[2],(v).c[0],(w).c[6]);\
      _complex_mul_star_assign((u).c[2],(v).c[1],(w).c[7]); \
      _complex_mul_star_assign((u).c[2],(v).c[2],(w).c[8]); \
      _complex_mul_star((u).c[3],(v).c[3],(w).c[0]);\
      _complex_mul_star_assign((u).c[3],(v).c[4],(w).c[1]); \
      _complex_mul_star_assign((u).c[3],(v).c[5],(w).c[2]); \
      _complex_mul_star((u).c[4],(v).c[3],(w).c[3]);\
      _complex_mul_star_assign((u).c[4],(v).c[4],(w).c[4]); \
      _complex_mul_star_assign((u).c[4],(v).c[5],(w).c[5]); \
      _complex_mul_star((u).c[5],(v).c[3],(w).c[6]);\
      _complex_mul_star_assign((u).c[5],(v).c[4],(w).c[7]); \
      _complex_mul_star_assign((u).c[5],(v).c[5],(w).c[8]); \
      _complex_mul_star((u).c[6],(v).c[6],(w).c[0]);\
      _complex_mul_star_assign((u).c[6],(v).c[7],(w).c[1]); \
      _complex_mul_star_assign((u).c[6],(v).c[8],(w).c[2]); \
      _complex_mul_star((u).c[7],(v).c[6],(w).c[3]);\
      _complex_mul_star_assign((u).c[7],(v).c[7],(w).c[4]); \
      _complex_mul_star_assign((u).c[7],(v).c[8],(w).c[5]); \
      _complex_mul_star((u).c[8],(v).c[6],(w).c[6]);\
      _complex_mul_star_assign((u).c[8],(v).c[7],(w).c[7]); \
      _complex_mul_star_assign((u).c[8],(v).c[8],(w).c[8])

 /***** u=v^+*w */
#define _suNf_dagger_times_suNf(u,v,w) \
      _complex_mul_star((u).c[0],(w).c[0],(v).c[0]);\
      _complex_mul_star_assign((u).c[0],(w).c[3],(v).c[3]); \
      _complex_mul_star_assign((u).c[0],(w).c[6],(v).c[6]); \
      _complex_mul_star((u).c[1],(w).c[1],(v).c[0]);\
      _complex_mul_star_assign((u).c[1],(w).c[4],(v).c[3]); \
      _complex_mul_star_assign((u).c[1],(w).c[7],(v).c[6]); \
      _complex_mul_star((u).c[2],(w).c[2],(v).c[0]);\
      _complex_mul_star_assign((u).c[2],(w).c[5],(v).c[3]); \
      _complex_mul_star_assign((u).c[2],(w).c[8],(v).c[6]); \
      _complex_mul_star((u).c[3],(w).c[0],(v).c[1]);\
      _complex_mul_star_assign((u).c[3],(w).c[3],(v).c[4]); \
      _complex_mul_star_assign((u).c[3],(w).c[6],(v).c[7]); \
      _complex_mul_star((u).c[4],(w).c[1],(v).c[1]);\
      _complex_mul_star_assign((u).c[4],(w).c[4],(v).c[4]); \
      _complex_mul_star_assign((u).c[4],(w).c[7],(v).c[7]); \
      _complex_mul_star((u).c[5],(w).c[2],(v).c[1]);\
      _complex_mul_star_assign((u).c[5],(w).c[5],(v).c[4]); \
      _complex_mul_star_assign((u).c[5],(w).c[8],(v).c[7]); \
      _complex_mul_star((u).c[6],(w).c[0],(v).c[2]);\
      _complex_mul_star_assign((u).c[6],(w).c[3],(v).c[5]); \
      _complex_mul_star_assign((u).c[6],(w).c[6],(v).c[8]); \
      _complex_mul_star((u).c[7],(w).c[1],(v).c[2]);\
      _complex_mul_star_assign((u).c[7],(w).c[4],(v).c[5]); \
      _complex_mul_star_assign((u).c[7],(w).c[7],(v).c[8]); \
      _complex_mul_star((u).c[8],(w).c[2],(v).c[2]);\
      _complex_mul_star_assign((u).c[8],(w).c[5],(v).c[5]); \
      _complex_mul_star_assign((u).c[8],(w).c[8],(v).c[8])

 /***** u=0 */
#define _suNf_zero(u) \
    _complex_0((u).c[0]);\
    _complex_0((u).c[1]);\
    _complex_0((u).c[2]);\
    _complex_0((u).c[3]);\
    _complex_0((u).c[4]);\
    _complex_0((u).c[5]);\
    _complex_0((u).c[6]);\
    _complex_0((u).c[7]);\
    _complex_0((u).c[8])

 /***** u=1 */
#define _suNf_unit(u) \
   _complex_1((u).c[0]);\
   _complex_0((u).c[1]);\
   _complex_0((u).c[2]);\
   _complex_0((u).c[3]);\
   _complex_1((u).c[4]);\
   _complex_0((u).c[5]);\
   _complex_0((u).c[6]);\
   _complex_0((u).c[7]);\
   _complex_1((u).c[8])

 /***** u=-v */
#define _suNf_minus(u,v) \
   _complex_minus((u).c[0],(v).c[0]);\
   _complex_minus((u).c[1],(v).c[1]);\
   _complex_minus((u).c[2],(v).c[2]);\
   _complex_minus((u).c[3],(v).c[3]);\
   _complex_minus((u).c[4],(v).c[4]);\
   _complex_minus((u).c[5],(v).c[5]);\
   _complex_minus((u).c[6],(v).c[6]);\
   _complex_minus((u).c[7],(v).c[7]);\
   _complex_minus((u).c[8],(v).c[8])

 /***** u=r*v (u,v mat, r real) */
#define _suNf_mul(u,r,v) \
   _complex_mulr((u).c[0],(r),(v).c[0]);\
   _complex_mulr((u).c[1],(r),(v).c[1]);\
   _complex_mulr((u).c[2],(r),(v).c[2]);\
   _complex_mulr((u).c[3],(r),(v).c[3]);\
   _complex_mulr((u).c[4],(r),(v).c[4]);\
   _complex_mulr((u).c[5],(r),(v).c[5]);\
   _complex_mulr((u).c[6],(r),(v).c[6]);\
   _complex_mulr((u).c[7],(r),(v).c[7]);\
   _complex_mulr((u).c[8],(r),(v).c[8])

 /***** u+=v */
#define _suNf_add_assign(u,v) \
   _complex_add_assign((u).c[0],(v).c[0]);\
   _complex_add_assign((u).c[1],(v).c[1]);\
   _complex_add_assign((u).c[2],(v).c[2]);\
   _complex_add_assign((u).c[3],(v).c[3]);\
   _complex_add_assign((u).c[4],(v).c[4]);\
   _complex_add_assign((u).c[5],(v).c[5]);\
   _complex_add_assign((u).c[6],(v).c[6]);\
   _complex_add_assign((u).c[7],(v).c[7]);\
   _complex_add_assign((u).c[8],(v).c[8])

 /***** u-=v */
#define _suNf_sub_assign(u,v) \
   _complex_sub_assign((u).c[0],(v).c[0]);\
   _complex_sub_assign((u).c[1],(v).c[1]);\
   _complex_sub_assign((u).c[2],(v).c[2]);\
   _complex_sub_assign((u).c[3],(v).c[3]);\
   _complex_sub_assign((u).c[4],(v).c[4]);\
   _complex_sub_assign((u).c[5],(v).c[5]);\
   _complex_sub_assign((u).c[6],(v).c[6]);\
   _complex_sub_assign((u).c[7],(v).c[7]);\
   _complex_sub_assign((u).c[8],(v).c[8])

 /***** k=| u |2 ) */
#define _suNf_sqnorm(k,u) \
   (k)=0.;\
   (k)+=_complex_prod_re((u).c[0],(u).c[0]); \
   (k)+=_complex_prod_re((u).c[1],(u).c[1]); \
   (k)+=_complex_prod_re((u).c[2],(u).c[2]); \
   (k)+=_complex_prod_re((u).c[3],(u).c[3]); \
   (k)+=_complex_prod_re((u).c[4],(u).c[4]); \
   (k)+=_complex_prod_re((u).c[5],(u).c[5]); \
   (k)+=_complex_prod_re((u).c[6],(u).c[6]); \
   (k)+=_complex_prod_re((u).c[7],(u).c[7]); \
   (k)+=_complex_prod_re((u).c[8],(u).c[8])

 /***** k=| 1 - u |2 ) */
#define _suNf_sqnorm_m1(k,u) \
   (k)=\
    +_complex_prod_m1_re((u).c[0],(u).c[0])\
    +_complex_prod_re((u).c[1],(u).c[1])\
    +_complex_prod_re((u).c[2],(u).c[2])\
    +_complex_prod_re((u).c[3],(u).c[3])\
    +_complex_prod_m1_re((u).c[4],(u).c[4])\
    +_complex_prod_re((u).c[5],(u).c[5])\
    +_complex_prod_re((u).c[6],(u).c[6])\
    +_complex_prod_re((u).c[7],(u).c[7])\
    +_complex_prod_m1_re((u).c[8],(u).c[8])

 /***** k=Re Tr (u) */
#define _suNf_trace_re(k,u) \
   (k)=_complex_re((u).c[0])+ \
       _complex_re((u).c[4])+ \
       _complex_re((u).c[8])

 /***** k=Im Tr (u) */
#define _suNf_trace_im(k,u) \
   (k)=_complex_im((u).c[0])+ \
       _complex_im((u).c[4])+ \
       _complex_im((u).c[8])

 /***** u=v - v^+ -1/N Tr(v - v^+)*I */
#define _suNf_2TA(u,v) \
   {\
      double _trim; _suNf_trace_im(_trim,(v)); _trim*=(2./3.);\
      (u).c[0].re= 0.; \
      (u).c[0].im= 2.*(v).c[0].im-_trim; \
      (u).c[1].re= (v).c[1].re-(v).c[3].re; \
      (u).c[1].im= (v).c[3].im+(v).c[1].im; \
      (u).c[3].re= -(u).c[1].re; \
      (u).c[3].im= (u).c[1].im; \
      (u).c[2].re= (v).c[2].re-(v).c[6].re; \
      (u).c[2].im= (v).c[6].im+(v).c[2].im; \
      (u).c[6].re= -(u).c[2].re; \
      (u).c[6].im= (u).c[2].im; \
      (u).c[4].re= 0.; \
      (u).c[4].im= 2.*(v).c[4].im-_trim; \
      (u).c[5].re= (v).c[5].re-(v).c[7].re; \
      (u).c[5].im= (v).c[7].im+(v).c[5].im; \
      (u).c[7].re= -(u).c[5].re; \
      (u).c[7].im= (u).c[5].im; \
      (u).c[8].re= 0.; \
      (u).c[8].im= 2.*(v).c[8].im-_trim; \
   }((void)0) 

 /***** u=0.5(v - v^+) -1/(2N) Tr(v - v^+)*I */
#define _suNf_TA(u,v) \
   {\
      double _trim; _suNf_trace_im(_trim,(v)); _trim*=(1./3.);\
      (u).c[0].re= 0.; \
      (u).c[0].im= (v).c[0].im-_trim; \
      (u).c[1].re= 0.5*((v).c[1].re-(v).c[3].re); \
      (u).c[1].im= 0.5*((v).c[3].im+(v).c[1].im); \
      (u).c[3].re= -(u).c[1].re; \
      (u).c[3].im= (u).c[1].im; \
      (u).c[2].re= 0.5*((v).c[2].re-(v).c[6].re); \
      (u).c[2].im= 0.5*((v).c[6].im+(v).c[2].im); \
      (u).c[6].re= -(u).c[2].re; \
      (u).c[6].im= (u).c[2].im; \
      (u).c[4].re= 0.; \
      (u).c[4].im= (v).c[4].im-_trim; \
      (u).c[5].re= 0.5*((v).c[5].re-(v).c[7].re); \
      (u).c[5].im= 0.5*((v).c[7].im+(v).c[5].im); \
      (u).c[7].re= -(u).c[5].re; \
      (u).c[7].im= (u).c[5].im; \
      (u).c[8].re= 0.; \
      (u).c[8].im= (v).c[8].im-_trim; \
   }((void)0) 

 /***** This fuction compute the hmc force matrix */
 /***** this fuction accumulates on the original matrix u */
#define _suNf_FMAT(u,s) \
   _complex_mul_star_assign((u).c[0],(s).c[0].c[0],(s).c[2].c[0]); \
   _complex_mul_star_assign((u).c[0],(s).c[1].c[0],(s).c[3].c[0]);\
   _complex_mul_star_assign((u).c[1],(s).c[0].c[0],(s).c[2].c[1]); \
   _complex_mul_star_assign((u).c[1],(s).c[1].c[0],(s).c[3].c[1]);\
   _complex_mul_star_assign((u).c[2],(s).c[0].c[0],(s).c[2].c[2]); \
   _complex_mul_star_assign((u).c[2],(s).c[1].c[0],(s).c[3].c[2]);\
   _complex_mul_star_assign((u).c[3],(s).c[0].c[1],(s).c[2].c[0]); \
   _complex_mul_star_assign((u).c[3],(s).c[1].c[1],(s).c[3].c[0]);\
   _complex_mul_star_assign((u).c[4],(s).c[0].c[1],(s).c[2].c[1]); \
   _complex_mul_star_assign((u).c[4],(s).c[1].c[1],(s).c[3].c[1]);\
   _complex_mul_star_assign((u).c[5],(s).c[0].c[1],(s).c[2].c[2]); \
   _complex_mul_star_assign((u).c[5],(s).c[1].c[1],(s).c[3].c[2]);\
   _complex_mul_star_assign((u).c[6],(s).c[0].c[2],(s).c[2].c[0]); \
   _complex_mul_star_assign((u).c[6],(s).c[1].c[2],(s).c[3].c[0]);\
   _complex_mul_star_assign((u).c[7],(s).c[0].c[2],(s).c[2].c[1]); \
   _complex_mul_star_assign((u).c[7],(s).c[1].c[2],(s).c[3].c[1]);\
   _complex_mul_star_assign((u).c[8],(s).c[0].c[2],(s).c[2].c[2]); \
   _complex_mul_star_assign((u).c[8],(s).c[1].c[2],(s).c[3].c[2])

 /***********************************************************************************
 *
 * Macros for spinors
 *
 * Arguments are variables of type spinors
 *
 *******************************************************************************/

 /*****  r=0  (r spinor) */
#define _spinor_zero_f(r) \
  _vector_zero_f((r).c[0]); \
  _vector_zero_f((r).c[1]); \
  _vector_zero_f((r).c[2]); \
  _vector_zero_f((r).c[3])

 /*****  s=g5*r (r,s spinors, g5 matrix) */
#define _spinor_g5_f(s,r) \
  (s).c[0]=(r).c[0]; \
  (s).c[1]=(r).c[1]; \
  _vector_minus_f((s).c[2],(r).c[2]); \
  _vector_minus_f((s).c[3],(r).c[3])

 /*****  r=g5*r (r,s spinors, g5 matrix) */
#define _spinor_g5_assign_f(r) \
  _vector_minus_f((r).c[2],(r).c[2]); \
  _vector_minus_f((r).c[3],(r).c[3])

 /*****  s=-r (r,s spinors) */
#define _spinor_minus_f(s,r) \
  _vector_minus_f((s).c[0],(r).c[0]); \
  _vector_minus_f((s).c[1],(r).c[1]); \
  _vector_minus_f((s).c[2],(r).c[2]); \
  _vector_minus_f((s).c[3],(r).c[3])

 /*****  r=k*s (k real; r,s spinors) */
#define _spinor_mul_f(r,k,s) \
  _vector_mul_f((r).c[0],k,(s).c[0]); \
  _vector_mul_f((r).c[1],k,(s).c[1]); \
  _vector_mul_f((r).c[2],k,(s).c[2]); \
  _vector_mul_f((r).c[3],k,(s).c[3])

 /*****  r=z*s (z complex; r,s spinors) */
#define _spinor_mulc_f(r,z,s) \
  _vector_mulc_f((r).c[0],z,(s).c[0]); \
  _vector_mulc_f((r).c[1],z,(s).c[1]); \
  _vector_mulc_f((r).c[2],z,(s).c[2]); \
  _vector_mulc_f((r).c[3],z,(s).c[3])

 /*****  r+=z*s (z complex; r,s spinors) */
#define _spinor_mulc_add_assign_f(r,z,s) \
  _vector_mulc_add_assign_f((r).c[0],(z),(s).c[0]); \
  _vector_mulc_add_assign_f((r).c[1],(z),(s).c[1]); \
  _vector_mulc_add_assign_f((r).c[2],(z),(s).c[2]); \
  _vector_mulc_add_assign_f((r).c[3],(z),(s).c[3])

 /*****  r+=k*s (k real; r,s spinors) */
#define _spinor_mul_add_assign_f(r,k,s) \
  _vector_mul_add_assign_f((r).c[0],(k),(s).c[0]); \
  _vector_mul_add_assign_f((r).c[1],(k),(s).c[1]); \
  _vector_mul_add_assign_f((r).c[2],(k),(s).c[2]); \
  _vector_mul_add_assign_f((r).c[3],(k),(s).c[3])

 /*****  r=k1*s1+k2*s2 (k1,k2 real; r,s1,s2 spinors) */
#define _spinor_lc_f(r,k1,s1,k2,s2) \
  _vector_lc_f((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
  _vector_lc_f((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
  _vector_lc_f((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2]); \
  _vector_lc_f((r).c[3],(k1),(s1).c[3],(k2),(s2).c[3])

 /*****  r+=k1*s1+k2*s2 (k1,k2 real; r,s1,s2 spinors) */
#define _spinor_lc_add_assign_f(r,k1,s1,k2,s2) \
  _vector_lc_add_assign_f((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
  _vector_lc_add_assign_f((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
  _vector_lc_add_assign_f((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2]); \
  _vector_lc_add_assign_f((r).c[3],(k1),(s1).c[3],(k2),(s2).c[3])

 /*****  r=z1*s1+z2*s2 (z1,z2 complex; r,s1,s2 spinors) */
#define _spinor_clc_f(r,z1,s1,z2,s2) \
  _vector_clc_f((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
  _vector_clc_f((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
  _vector_clc_f((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2]); \
  _vector_clc_f((r).c[3],(z1),(s1).c[3],(z2),(s2).c[3])

 /*****  r+=z1*s1+z2*s2 (z1,z2 complex; r,s1,s2 spinors) */
#define _spinor_clc_add_assign_f(r,z1,s1,z2,s2) \
  _vector_clc_add_assign_f((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
  _vector_clc_add_assign_f((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
  _vector_clc_add_assign_f((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2]); \
  _vector_clc_add_assign_f((r).c[3],(z1),(s1).c[3],(z2),(s2).c[3])

 /*****  r=s1+s2 (r,s1,s2 spinors) */
#define _spinor_add_f(r,s1,s2) \
  _vector_add_f((r).c[0],(s1).c[0],(s2).c[0]); \
  _vector_add_f((r).c[1],(s1).c[1],(s2).c[1]); \
  _vector_add_f((r).c[2],(s1).c[2],(s2).c[2]); \
  _vector_add_f((r).c[3],(s1).c[3],(s2).c[3])

 /*****  r=s1-s2 (r,s1,s2 spinors) */
#define _spinor_sub_f(r,s1,s2) \
  _vector_sub_f((r).c[0],(s1).c[0],(s2).c[0]); \
  _vector_sub_f((r).c[1],(s1).c[1],(s2).c[1]); \
  _vector_sub_f((r).c[2],(s1).c[2],(s2).c[2]); \
  _vector_sub_f((r).c[3],(s1).c[3],(s2).c[3])

 /*****  r+=s (r,s spinors) */
#define _spinor_add_assign_f(r,s) \
  _vector_add_assign_f((r).c[0],(s).c[0]); \
  _vector_add_assign_f((r).c[1],(s).c[1]); \
  _vector_add_assign_f((r).c[2],(s).c[2]); \
  _vector_add_assign_f((r).c[3],(s).c[3])

 /*****  r-=s (r,s spinors) */
#define _spinor_sub_assign_f(r,s) \
  _vector_sub_assign_f((r).c[0],(s).c[0]); \
  _vector_sub_assign_f((r).c[1],(s).c[1]); \
  _vector_sub_assign_f((r).c[2],(s).c[2]); \
  _vector_sub_assign_f((r).c[3],(s).c[3])

 /*****  r+=i*s (r,s spinors) */
#define _spinor_i_add_assign_f(r,s) \
  _vector_i_add_assign_f((r).c[0],(s).c[0]); \
  _vector_i_add_assign_f((r).c[1],(s).c[1]); \
  _vector_i_add_assign_f((r).c[2],(s).c[2]); \
  _vector_i_add_assign_f((r).c[3],(s).c[3])

 /*****  r-=i*s (r,s spinors) */
#define _spinor_i_sub_assign_f(r,s) \
  _vector_i_sub_assign_f((r).c[0],(s).c[0]); \
  _vector_i_sub_assign_f((r).c[1],(s).c[1]); \
  _vector_i_sub_assign_f((r).c[2],(s).c[2]); \
  _vector_i_sub_assign_f((r).c[3],(s).c[3])

 /***** k=Real part of the scalar product r*s (r,s spinors) */
#define _spinor_prod_re_f(k,r,s) \
   { \
      double _tmp; (k)=0.; \
      _vector_prod_re_f(_tmp,(r).c[0],(s).c[0]); (k)+=_tmp;\
      _vector_prod_re_f(_tmp,(r).c[1],(s).c[1]); (k)+=_tmp;\
      _vector_prod_re_f(_tmp,(r).c[2],(s).c[2]); (k)+=_tmp;\
      _vector_prod_re_f(_tmp,(r).c[3],(s).c[3]); (k)+=_tmp;\
   }((void)0) 

 /***** k=Im part of the scalar product r*s (r,s spinors) */
#define _spinor_prod_im_f(k,r,s) \
   { \
      double _tmp; (k)=0.; \
      _vector_prod_im_f(_tmp,(r).c[0],(s).c[0]); (k)+=_tmp;\
      _vector_prod_im_f(_tmp,(r).c[1],(s).c[1]); (k)+=_tmp;\
      _vector_prod_im_f(_tmp,(r).c[2],(s).c[2]); (k)+=_tmp;\
      _vector_prod_im_f(_tmp,(r).c[3],(s).c[3]); (k)+=_tmp;\
   }((void)0) 

 /***** z+=r*s (r,s spinors, z complex) */
#define _spinor_prod_assign_f(z,r,s) \
  _vector_prod_assign_f((z),(r).c[0],(s).c[0]); \
  _vector_prod_assign_f((z),(r).c[1],(s).c[1]); \
  _vector_prod_assign_f((z),(r).c[2],(s).c[2]); \
  _vector_prod_assign_f((z),(r).c[3],(s).c[3])

 /***** k=Real part of the scalar product (g5*r)*s (r,s spinors) */
#define _spinor_g5_prod_re_f(k,r,s) \
   { \
      double _tmp; (k)=0.; \
      _vector_prod_re_f(_tmp,(r).c[0],(s).c[0]); (k)+=_tmp;\
      _vector_prod_re_f(_tmp,(r).c[1],(s).c[1]); (k)+=_tmp;\
      _vector_prod_re_f(_tmp,(r).c[2],(s).c[2]); (k)-=_tmp;\
      _vector_prod_re_f(_tmp,(r).c[3],(s).c[3]); (k)-=_tmp;\
   }((void)0) 

 /***** k=Imaginary part of the scalar product (g5*r)*s (r,s spinors) */
#define _spinor_g5_prod_im_f(k,r,s) \
   { \
      double _tmp; (k)=0.; \
      _vector_prod_im_f(_tmp,(r).c[0],(s).c[0]); (k)+=_tmp;\
      _vector_prod_im_f(_tmp,(r).c[1],(s).c[1]); (k)+=_tmp;\
      _vector_prod_im_f(_tmp,(r).c[2],(s).c[2]); (k)-=_tmp;\
      _vector_prod_im_f(_tmp,(r).c[3],(s).c[3]); (k)-=_tmp;\
   }((void)0) 

 /***** r-=z*s (z complex; r,s spinors) */
#define _spinor_project_f(r,z,s) \
  _vector_project_f((r).c[0],z,(s).c[0]); \
  _vector_project_f((r).c[1],z,(s).c[1]); \
  _vector_project_f((r).c[2],z,(s).c[2]); \
  _vector_project_f((r).c[3],z,(s).c[3])

 /***** r=(1-g0)/2 * s (r,s spinors) */
#define _spinor_pminus_f(r,s) \
  _vector_add_f((r).c[0],(s).c[0],(s).c[2]); \
  _vector_add_f((r).c[1],(s).c[1],(s).c[3]); \
  _vector_mul_f((r).c[0],0.5,(r).c[0]); \
  _vector_mul_f((r).c[1],0.5,(r).c[1]); \
  (r).c[2] = (r).c[0]; \
  (r).c[3] = (r).c[1]

 /***** r=(1+g0)/2 * s (r,s spinors) */
#define _spinor_pplus_f(r,s) \
  _vector_sub_f((r).c[0],(s).c[0],(s).c[2]); \
  _vector_sub_f((r).c[1],(s).c[1],(s).c[3]); \
  _vector_mul_f((r).c[0],0.5,(r).c[0]); \
  _vector_mul_f((r).c[1],0.5,(r).c[1]); \
  _vector_mul_f((r).c[2],-1.,(r).c[0]); \
  _vector_mul_f((r).c[3],-1.,(r).c[1])


#endif
