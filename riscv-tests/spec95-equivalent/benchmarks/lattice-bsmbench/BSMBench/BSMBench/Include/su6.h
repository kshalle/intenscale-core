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
 * File su6.h
 *
 * Type definitions and macros for SU(6) matrices and spinors  
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
   _complex_0((r).c[2]); \
   _complex_0((r).c[3]); \
   _complex_0((r).c[4]); \
   _complex_0((r).c[5])

 /***** r=-s */
#define _vector_minus_g(r,s) \
   _complex_minus((r).c[0],(s).c[0]); \
   _complex_minus((r).c[1],(s).c[1]); \
   _complex_minus((r).c[2],(s).c[2]); \
   _complex_minus((r).c[3],(s).c[3]); \
   _complex_minus((r).c[4],(s).c[4]); \
   _complex_minus((r).c[5],(s).c[5])

 /***** r=k*s (k real) */
#define _vector_mul_g(r,k,s) \
   _complex_mulr((r).c[0],(k),(s).c[0]); \
   _complex_mulr((r).c[1],(k),(s).c[1]); \
   _complex_mulr((r).c[2],(k),(s).c[2]); \
   _complex_mulr((r).c[3],(k),(s).c[3]); \
   _complex_mulr((r).c[4],(k),(s).c[4]); \
   _complex_mulr((r).c[5],(k),(s).c[5])

 /***** r=z*s (z complex) */
#define _vector_mulc_g(r,z,s) \
   _complex_mul((r).c[0],(z),(s).c[0]); \
   _complex_mul((r).c[1],(z),(s).c[1]); \
   _complex_mul((r).c[2],(z),(s).c[2]); \
   _complex_mul((r).c[3],(z),(s).c[3]); \
   _complex_mul((r).c[4],(z),(s).c[4]); \
   _complex_mul((r).c[5],(z),(s).c[5])

 /***** r=s1+s2 */
#define _vector_add_g(r,s1,s2) \
   _complex_add((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_add((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_add((r).c[2],(s1).c[2],(s2).c[2]); \
   _complex_add((r).c[3],(s1).c[3],(s2).c[3]); \
   _complex_add((r).c[4],(s1).c[4],(s2).c[4]); \
   _complex_add((r).c[5],(s1).c[5],(s2).c[5])

 /***** r=s1-s2 */
#define _vector_sub_g(r,s1,s2) \
   _complex_sub((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_sub((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_sub((r).c[2],(s1).c[2],(s2).c[2]); \
   _complex_sub((r).c[3],(s1).c[3],(s2).c[3]); \
   _complex_sub((r).c[4],(s1).c[4],(s2).c[4]); \
   _complex_sub((r).c[5],(s1).c[5],(s2).c[5])

 /***** r=s1+i*s2 */
#define _vector_i_add_g(r,s1,s2) \
   _complex_i_add((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_i_add((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_i_add((r).c[2],(s1).c[2],(s2).c[2]); \
   _complex_i_add((r).c[3],(s1).c[3],(s2).c[3]); \
   _complex_i_add((r).c[4],(s1).c[4],(s2).c[4]); \
   _complex_i_add((r).c[5],(s1).c[5],(s2).c[5])

 /***** r=s1-i*s2 */
#define _vector_i_sub_g(r,s1,s2) \
   _complex_i_sub((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_i_sub((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_i_sub((r).c[2],(s1).c[2],(s2).c[2]); \
   _complex_i_sub((r).c[3],(s1).c[3],(s2).c[3]); \
   _complex_i_sub((r).c[4],(s1).c[4],(s2).c[4]); \
   _complex_i_sub((r).c[5],(s1).c[5],(s2).c[5])

 /***** r+=s */
#define _vector_add_assign_g(r,s) \
   _complex_add_assign((r).c[0],(s).c[0]); \
   _complex_add_assign((r).c[1],(s).c[1]); \
   _complex_add_assign((r).c[2],(s).c[2]); \
   _complex_add_assign((r).c[3],(s).c[3]); \
   _complex_add_assign((r).c[4],(s).c[4]); \
   _complex_add_assign((r).c[5],(s).c[5])

 /***** r-=s */
#define _vector_sub_assign_g(r,s) \
   _complex_sub_assign((r).c[0],(s).c[0]); \
   _complex_sub_assign((r).c[1],(s).c[1]); \
   _complex_sub_assign((r).c[2],(s).c[2]); \
   _complex_sub_assign((r).c[3],(s).c[3]); \
   _complex_sub_assign((r).c[4],(s).c[4]); \
   _complex_sub_assign((r).c[5],(s).c[5])

 /***** r+=i*s */
#define _vector_i_add_assign_g(r,s) \
   _complex_i_add_assign((r).c[0],(s).c[0]); \
   _complex_i_add_assign((r).c[1],(s).c[1]); \
   _complex_i_add_assign((r).c[2],(s).c[2]); \
   _complex_i_add_assign((r).c[3],(s).c[3]); \
   _complex_i_add_assign((r).c[4],(s).c[4]); \
   _complex_i_add_assign((r).c[5],(s).c[5])

 /***** r-=i*s */
#define _vector_i_sub_assign_g(r,s) \
   _complex_i_sub_assign((r).c[0],(s).c[0]); \
   _complex_i_sub_assign((r).c[1],(s).c[1]); \
   _complex_i_sub_assign((r).c[2],(s).c[2]); \
   _complex_i_sub_assign((r).c[3],(s).c[3]); \
   _complex_i_sub_assign((r).c[4],(s).c[4]); \
   _complex_i_sub_assign((r).c[5],(s).c[5])

 /***** k=Re(r^*s) */
#define _vector_prod_re_g(k,r,s) \
   (k)=_complex_prod_re((r).c[0],(s).c[0]);\
   (k)+=_complex_prod_re((r).c[1],(s).c[1]); \
   (k)+=_complex_prod_re((r).c[2],(s).c[2]); \
   (k)+=_complex_prod_re((r).c[3],(s).c[3]); \
   (k)+=_complex_prod_re((r).c[4],(s).c[4]); \
   (k)+=_complex_prod_re((r).c[5],(s).c[5])

 /***** k=Im(r*s) */
#define _vector_prod_im_g(k,r,s) \
   (k)=_complex_prod_im((r).c[0],(s).c[0]);\
   (k)+=_complex_prod_im((r).c[1],(s).c[1]); \
   (k)+=_complex_prod_im((r).c[2],(s).c[2]); \
   (k)+=_complex_prod_im((r).c[3],(s).c[3]); \
   (k)+=_complex_prod_im((r).c[4],(s).c[4]); \
   (k)+=_complex_prod_im((r).c[5],(s).c[5])

 /***** r+=z*s (z complex) */
#define _vector_mulc_add_assign_g(r,z,s) \
   _complex_mul_assign((r).c[0],(z),(s).c[0]); \
   _complex_mul_assign((r).c[1],(z),(s).c[1]); \
   _complex_mul_assign((r).c[2],(z),(s).c[2]); \
   _complex_mul_assign((r).c[3],(z),(s).c[3]); \
   _complex_mul_assign((r).c[4],(z),(s).c[4]); \
   _complex_mul_assign((r).c[5],(z),(s).c[5])

 /***** r+=k*s (k real) */
#define _vector_mul_add_assign_g(r,k,s) \
   _complex_mulr_assign((r).c[0],(k),(s).c[0]); \
   _complex_mulr_assign((r).c[1],(k),(s).c[1]); \
   _complex_mulr_assign((r).c[2],(k),(s).c[2]); \
   _complex_mulr_assign((r).c[3],(k),(s).c[3]); \
   _complex_mulr_assign((r).c[4],(k),(s).c[4]); \
   _complex_mulr_assign((r).c[5],(k),(s).c[5])

 /***** r=k1*s1+k2*s2 (k1,k2 real, s1,s2 vectors) */
#define _vector_lc_g(r,k1,s1,k2,s2) \
   _complex_rlc((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
   _complex_rlc((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
   _complex_rlc((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2]); \
   _complex_rlc((r).c[3],(k1),(s1).c[3],(k2),(s2).c[3]); \
   _complex_rlc((r).c[4],(k1),(s1).c[4],(k2),(s2).c[4]); \
   _complex_rlc((r).c[5],(k1),(s1).c[5],(k2),(s2).c[5])

 /***** r+=k1*s1+k2*s2 (k1,k2 real, s1,s2 vectors) */
#define _vector_lc_add_assign_g(r,k1,s1,k2,s2) \
   _complex_rlc_assign((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
   _complex_rlc_assign((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
   _complex_rlc_assign((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2]); \
   _complex_rlc_assign((r).c[3],(k1),(s1).c[3],(k2),(s2).c[3]); \
   _complex_rlc_assign((r).c[4],(k1),(s1).c[4],(k2),(s2).c[4]); \
   _complex_rlc_assign((r).c[5],(k1),(s1).c[5],(k2),(s2).c[5])

 /***** r=z1*s1+z2*s2 (z1,z2 complex, s1,s2 vectors) */
#define _vector_clc_g(r,z1,s1,z2,s2) \
   _complex_clc((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
   _complex_clc((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
   _complex_clc((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2]); \
   _complex_clc((r).c[3],(z1),(s1).c[3],(z2),(s2).c[3]); \
   _complex_clc((r).c[4],(z1),(s1).c[4],(z2),(s2).c[4]); \
   _complex_clc((r).c[5],(z1),(s1).c[5],(z2),(s2).c[5])

 /***** r=z1*s1+z2*s2 (z1,z2 complex, s1,s2 vectors) */
#define _vector_clc_add_assign_g(r,z1,s1,z2,s2) \
   _complex_clc_assign((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
   _complex_clc_assign((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
   _complex_clc_assign((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2]); \
   _complex_clc_assign((r).c[3],(z1),(s1).c[3],(z2),(s2).c[3]); \
   _complex_clc_assign((r).c[4],(z1),(s1).c[4],(z2),(s2).c[4]); \
   _complex_clc_assign((r).c[5],(z1),(s1).c[5],(z2),(s2).c[5])

 /***** z+=r^*s (c complex) */
#define _vector_prod_assign_g(z,r,s) \
   _complex_prod_assign((z),(r).c[0],(s).c[0]); \
   _complex_prod_assign((z),(r).c[1],(s).c[1]); \
   _complex_prod_assign((z),(r).c[2],(s).c[2]); \
   _complex_prod_assign((z),(r).c[3],(s).c[3]); \
   _complex_prod_assign((z),(r).c[4],(s).c[4]); \
   _complex_prod_assign((z),(r).c[5],(s).c[5])

 /***** r-=z*s (z complex) */
#define _vector_project_g(r,z,s) \
   _complex_mul_sub_assign((r).c[0],(z),(s).c[0]); \
   _complex_mul_sub_assign((r).c[1],(z),(s).c[1]); \
   _complex_mul_sub_assign((r).c[2],(z),(s).c[2]); \
   _complex_mul_sub_assign((r).c[3],(z),(s).c[3]); \
   _complex_mul_sub_assign((r).c[4],(z),(s).c[4]); \
   _complex_mul_sub_assign((r).c[5],(z),(s).c[5])

 /***** SU(N) matrix u times SU(N) vector s */
 /***** r=u*s */
#define _suNg_multiply(r,u,s) \
   {\
      int _i,_j,_k=0;for (_i=0; _i<6; ++_i){\
         _complex_mul((r).c[_i],(u).c[_k],(s).c[0]); ++_k; _j=1;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[1]); ++_k;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[2]); ++_k;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[3]); ++_k;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[4]); ++_k;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[5]); ++_k;\
      }\
   }((void)0) 

 /***** SU(N) matrix u^dagger times SU(N) vector s */
 /***** r=(u^dagger)*s */
#define _suNg_inverse_multiply(r,u,s) \
   {\
      int _i,_j,_k=0;for (_i=0; _i<6; ++_i){\
         _complex_mul_star((r).c[_i],(s).c[0],(u).c[_k]); _j=1;\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[1],(u).c[_k]);\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[2],(u).c[_k]);\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[3],(u).c[_k]);\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[4],(u).c[_k]);\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[5],(u).c[_k]);\
         _k-=29;\
      }\
   }((void)0) 

 /***** r+=k*s (k real) */
#define _algebra_vector_mul_add_assign_g(r,k,s) \
   {\
      int _i;for (_i=0; _i<32; ){\
         (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
      }\
      (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
      (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
      (r).c[_i]+=(k)*(s).c[_i]; ++_i;\
   }((void)0) 

 /***** r=k*s (k real) */
#define _algebra_vector_mul_g(r,k,s) \
   {\
      int _i;for (_i=0; _i<32; ){\
         (r).c[_i]=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]=(k)*(s).c[_i]; ++_i;\
         (r).c[_i]=(k)*(s).c[_i]; ++_i;\
      }\
      (r).c[_i]=(k)*(s).c[_i]; ++_i;\
      (r).c[_i]=(k)*(s).c[_i]; ++_i;\
      (r).c[_i]=(k)*(s).c[_i]; ++_i;\
   }((void)0) 

 /***** r=0  */
#define _algebra_vector_zero_g(r) \
   {\
      int _i;for (_i=0; _i<32; ){\
         (r).c[_i]=0.; ++_i;\
         (r).c[_i]=0.; ++_i;\
         (r).c[_i]=0.; ++_i;\
         (r).c[_i]=0.; ++_i;\
         (r).c[_i]=0.; ++_i;\
         (r).c[_i]=0.; ++_i;\
         (r).c[_i]=0.; ++_i;\
         (r).c[_i]=0.; ++_i;\
      }\
      (r).c[_i]=0.; ++_i;\
      (r).c[_i]=0.; ++_i;\
      (r).c[_i]=0.; ++_i;\
   }((void)0) 

 /***** k=|v|^2  */
#define _algebra_vector_sqnorm_g(k,r) \
   {\
      int _i,_n=0;\
      (k)=0.;\
      for (_i=0; _i<32; ){\
         (k)+=((r).c[_i]*(r).c[_i])+ \
              ((r).c[_i+1]*(r).c[_i+1])+ \
              ((r).c[_i+2]*(r).c[_i+2])+ \
              ((r).c[_i+3]*(r).c[_i+3])+ \
              ((r).c[_i+4]*(r).c[_i+4])+ \
              ((r).c[_i+5]*(r).c[_i+5])+ \
              ((r).c[_i+6]*(r).c[_i+6])+ \
              ((r).c[_i+7]*(r).c[_i+7]);\
         _i+=8;\
      }\
      (k)+=((r).c[_i]*(r).c[_i])+ \
           ((r).c[_i+1]*(r).c[_i+1])+ \
           ((r).c[_i+2]*(r).c[_i+2]);\
   }((void)0) 

 /***********************************************************************************
 *
 * Macros for SU(N) matrices
 *
 * Arguments are variables of type suN
 *
 *******************************************************************************/

 /***** u=v^dagger */
#define _suNg_dagger(u,v) \
   {\
      int _i,_n=0,_k=0;\
      for (_i=0; _i<6; ++_i){\
         _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k-=29;\
      }\
   }((void)0) 

 /***** u=v*w */
#define _suNg_times_suNg(u,v,w) \
   {\
      int _i,_y,_n=0,_k=0,_l=0;\
      for (_i=0; _i<6; ++_i){\
         for (_y=0; _y<6; ++_y){\
            _complex_mul((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_n; _k-=5; _l-=29;\
         } _k+=6; _l=0;\
      }\
   }((void)0) 

 /***** u=v*w^+ */
#define _suNg_times_suNg_dagger(u,v,w) \
   {\
      int _i,_y,_n=0,_k=0,_l=0;\
      for (_i=0; _i<6; ++_i){\
         for (_y=0; _y<6; ++_y){\
            _complex_mul_star((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_n; _k-=5; ++_l;\
         } _k+=6; _l=0;\
      }\
   }((void)0) 

 /***** u=v^+*w */
#define _suNg_dagger_times_suNg(u,v,w) \
   {\
      int _i,_y,_n=0,_k=0,_l=0;\
      for (_i=0; _i<6; ++_i){\
         for (_y=0; _y<6; ++_y){\
            _k=_y; _l=_i;\
            _complex_mul_star((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            ++_n;\
         }\
      }\
   }((void)0) 

 /***** u=0 */
#define _suNg_zero(u) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_0((u).c[_i]); ++_i;\
         _complex_0((u).c[_i]); ++_i;\
         _complex_0((u).c[_i]); ++_i;\
         _complex_0((u).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** u=1 */
#define _suNg_unit(u) \
   {\
      _suNg_zero((u));\
      _complex_1((u).c[0]);\
      _complex_1((u).c[7]);\
      _complex_1((u).c[14]);\
      _complex_1((u).c[21]);\
      _complex_1((u).c[28]);\
      _complex_1((u).c[35]);\
   }((void)0) 

 /***** u=-v */
#define _suNg_minus(u,v) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_minus((u).c[_i],(v).c[_i]); ++_i;\
         _complex_minus((u).c[_i],(v).c[_i]); ++_i;\
         _complex_minus((u).c[_i],(v).c[_i]); ++_i;\
         _complex_minus((u).c[_i],(v).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** u=r*v (u,v mat, r real) */
#define _suNg_mul(u,r,v) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_mulr((u).c[_i],(r),(v).c[_i]); ++_i;\
         _complex_mulr((u).c[_i],(r),(v).c[_i]); ++_i;\
         _complex_mulr((u).c[_i],(r),(v).c[_i]); ++_i;\
         _complex_mulr((u).c[_i],(r),(v).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** u+=v */
#define _suNg_add_assign(u,v) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_add_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_add_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_add_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_add_assign((u).c[_i],(v).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** u-=v */
#define _suNg_sub_assign(u,v) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_sub_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_sub_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_sub_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_sub_assign((u).c[_i],(v).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** k=| u |2 ) */
#define _suNg_sqnorm(k,u) \
   {\
      int _i;\
      (k)=0.;\
      for (_i=0; _i<36; ){\
         (k)+=_complex_prod_re((u).c[_i],(u).c[_i]); ++_i;\
         (k)+=_complex_prod_re((u).c[_i],(u).c[_i]); ++_i;\
         (k)+=_complex_prod_re((u).c[_i],(u).c[_i]); ++_i;\
         (k)+=_complex_prod_re((u).c[_i],(u).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** k=| 1 - u |2 ) */
#define _suNg_sqnorm_m1(k,u) \
   (k)=\
    +_complex_prod_m1_re((u).c[0],(u).c[0])\
    +_complex_prod_re((u).c[1],(u).c[1])\
    +_complex_prod_re((u).c[2],(u).c[2])\
    +_complex_prod_re((u).c[3],(u).c[3])\
    +_complex_prod_re((u).c[4],(u).c[4])\
    +_complex_prod_re((u).c[5],(u).c[5])\
    +_complex_prod_re((u).c[6],(u).c[6])\
    +_complex_prod_m1_re((u).c[7],(u).c[7])\
    +_complex_prod_re((u).c[8],(u).c[8])\
    +_complex_prod_re((u).c[9],(u).c[9])\
    +_complex_prod_re((u).c[10],(u).c[10])\
    +_complex_prod_re((u).c[11],(u).c[11])\
    +_complex_prod_re((u).c[12],(u).c[12])\
    +_complex_prod_re((u).c[13],(u).c[13])\
    +_complex_prod_m1_re((u).c[14],(u).c[14])\
    +_complex_prod_re((u).c[15],(u).c[15])\
    +_complex_prod_re((u).c[16],(u).c[16])\
    +_complex_prod_re((u).c[17],(u).c[17])\
    +_complex_prod_re((u).c[18],(u).c[18])\
    +_complex_prod_re((u).c[19],(u).c[19])\
    +_complex_prod_re((u).c[20],(u).c[20])\
    +_complex_prod_m1_re((u).c[21],(u).c[21])\
    +_complex_prod_re((u).c[22],(u).c[22])\
    +_complex_prod_re((u).c[23],(u).c[23])\
    +_complex_prod_re((u).c[24],(u).c[24])\
    +_complex_prod_re((u).c[25],(u).c[25])\
    +_complex_prod_re((u).c[26],(u).c[26])\
    +_complex_prod_re((u).c[27],(u).c[27])\
    +_complex_prod_m1_re((u).c[28],(u).c[28])\
    +_complex_prod_re((u).c[29],(u).c[29])\
    +_complex_prod_re((u).c[30],(u).c[30])\
    +_complex_prod_re((u).c[31],(u).c[31])\
    +_complex_prod_re((u).c[32],(u).c[32])\
    +_complex_prod_re((u).c[33],(u).c[33])\
    +_complex_prod_re((u).c[34],(u).c[34])\
    +_complex_prod_m1_re((u).c[35],(u).c[35])

 /***** k=Re Tr (u) */
#define _suNg_trace_re(k,u) \
   (k)=_complex_re((u).c[0])+ \
       _complex_re((u).c[7])+ \
       _complex_re((u).c[14])+ \
       _complex_re((u).c[21])+ \
       _complex_re((u).c[28])+ \
       _complex_re((u).c[35])

 /***** k=Im Tr (u) */
#define _suNg_trace_im(k,u) \
   (k)=_complex_im((u).c[0])+ \
       _complex_im((u).c[7])+ \
       _complex_im((u).c[14])+ \
       _complex_im((u).c[21])+ \
       _complex_im((u).c[28])+ \
       _complex_im((u).c[35])

 /***** u=v - v^+ -1/N Tr(v - v^+)*I */
#define _suNg_2TA(u,v) \
   {\
      double _trim; _suNg_trace_im(_trim,(v)); _trim*=(2./6.);\
      (u).c[0].re= 0.; \
      (u).c[0].im= 2.*(v).c[0].im-_trim; \
      (u).c[1].re= (v).c[1].re-(v).c[6].re; \
      (u).c[1].im= (v).c[6].im+(v).c[1].im; \
      (u).c[6].re= -(u).c[1].re; \
      (u).c[6].im= (u).c[1].im; \
      (u).c[2].re= (v).c[2].re-(v).c[12].re; \
      (u).c[2].im= (v).c[12].im+(v).c[2].im; \
      (u).c[12].re= -(u).c[2].re; \
      (u).c[12].im= (u).c[2].im; \
      (u).c[3].re= (v).c[3].re-(v).c[18].re; \
      (u).c[3].im= (v).c[18].im+(v).c[3].im; \
      (u).c[18].re= -(u).c[3].re; \
      (u).c[18].im= (u).c[3].im; \
      (u).c[4].re= (v).c[4].re-(v).c[24].re; \
      (u).c[4].im= (v).c[24].im+(v).c[4].im; \
      (u).c[24].re= -(u).c[4].re; \
      (u).c[24].im= (u).c[4].im; \
      (u).c[5].re= (v).c[5].re-(v).c[30].re; \
      (u).c[5].im= (v).c[30].im+(v).c[5].im; \
      (u).c[30].re= -(u).c[5].re; \
      (u).c[30].im= (u).c[5].im; \
      (u).c[7].re= 0.; \
      (u).c[7].im= 2.*(v).c[7].im-_trim; \
      (u).c[8].re= (v).c[8].re-(v).c[13].re; \
      (u).c[8].im= (v).c[13].im+(v).c[8].im; \
      (u).c[13].re= -(u).c[8].re; \
      (u).c[13].im= (u).c[8].im; \
      (u).c[9].re= (v).c[9].re-(v).c[19].re; \
      (u).c[9].im= (v).c[19].im+(v).c[9].im; \
      (u).c[19].re= -(u).c[9].re; \
      (u).c[19].im= (u).c[9].im; \
      (u).c[10].re= (v).c[10].re-(v).c[25].re; \
      (u).c[10].im= (v).c[25].im+(v).c[10].im; \
      (u).c[25].re= -(u).c[10].re; \
      (u).c[25].im= (u).c[10].im; \
      (u).c[11].re= (v).c[11].re-(v).c[31].re; \
      (u).c[11].im= (v).c[31].im+(v).c[11].im; \
      (u).c[31].re= -(u).c[11].re; \
      (u).c[31].im= (u).c[11].im; \
      (u).c[14].re= 0.; \
      (u).c[14].im= 2.*(v).c[14].im-_trim; \
      (u).c[15].re= (v).c[15].re-(v).c[20].re; \
      (u).c[15].im= (v).c[20].im+(v).c[15].im; \
      (u).c[20].re= -(u).c[15].re; \
      (u).c[20].im= (u).c[15].im; \
      (u).c[16].re= (v).c[16].re-(v).c[26].re; \
      (u).c[16].im= (v).c[26].im+(v).c[16].im; \
      (u).c[26].re= -(u).c[16].re; \
      (u).c[26].im= (u).c[16].im; \
      (u).c[17].re= (v).c[17].re-(v).c[32].re; \
      (u).c[17].im= (v).c[32].im+(v).c[17].im; \
      (u).c[32].re= -(u).c[17].re; \
      (u).c[32].im= (u).c[17].im; \
      (u).c[21].re= 0.; \
      (u).c[21].im= 2.*(v).c[21].im-_trim; \
      (u).c[22].re= (v).c[22].re-(v).c[27].re; \
      (u).c[22].im= (v).c[27].im+(v).c[22].im; \
      (u).c[27].re= -(u).c[22].re; \
      (u).c[27].im= (u).c[22].im; \
      (u).c[23].re= (v).c[23].re-(v).c[33].re; \
      (u).c[23].im= (v).c[33].im+(v).c[23].im; \
      (u).c[33].re= -(u).c[23].re; \
      (u).c[33].im= (u).c[23].im; \
      (u).c[28].re= 0.; \
      (u).c[28].im= 2.*(v).c[28].im-_trim; \
      (u).c[29].re= (v).c[29].re-(v).c[34].re; \
      (u).c[29].im= (v).c[34].im+(v).c[29].im; \
      (u).c[34].re= -(u).c[29].re; \
      (u).c[34].im= (u).c[29].im; \
      (u).c[35].re= 0.; \
      (u).c[35].im= 2.*(v).c[35].im-_trim; \
   }((void)0) 

 /***** u=0.5(v - v^+) -1/(2N) Tr(v - v^+)*I */
#define _suNg_TA(u,v) \
   {\
      double _trim; _suNg_trace_im(_trim,(v)); _trim*=(1./6.);\
      (u).c[0].re= 0.; \
      (u).c[0].im= (v).c[0].im-_trim; \
      (u).c[1].re= 0.5*((v).c[1].re-(v).c[6].re); \
      (u).c[1].im= 0.5*((v).c[6].im+(v).c[1].im); \
      (u).c[6].re= -(u).c[1].re; \
      (u).c[6].im= (u).c[1].im; \
      (u).c[2].re= 0.5*((v).c[2].re-(v).c[12].re); \
      (u).c[2].im= 0.5*((v).c[12].im+(v).c[2].im); \
      (u).c[12].re= -(u).c[2].re; \
      (u).c[12].im= (u).c[2].im; \
      (u).c[3].re= 0.5*((v).c[3].re-(v).c[18].re); \
      (u).c[3].im= 0.5*((v).c[18].im+(v).c[3].im); \
      (u).c[18].re= -(u).c[3].re; \
      (u).c[18].im= (u).c[3].im; \
      (u).c[4].re= 0.5*((v).c[4].re-(v).c[24].re); \
      (u).c[4].im= 0.5*((v).c[24].im+(v).c[4].im); \
      (u).c[24].re= -(u).c[4].re; \
      (u).c[24].im= (u).c[4].im; \
      (u).c[5].re= 0.5*((v).c[5].re-(v).c[30].re); \
      (u).c[5].im= 0.5*((v).c[30].im+(v).c[5].im); \
      (u).c[30].re= -(u).c[5].re; \
      (u).c[30].im= (u).c[5].im; \
      (u).c[7].re= 0.; \
      (u).c[7].im= (v).c[7].im-_trim; \
      (u).c[8].re= 0.5*((v).c[8].re-(v).c[13].re); \
      (u).c[8].im= 0.5*((v).c[13].im+(v).c[8].im); \
      (u).c[13].re= -(u).c[8].re; \
      (u).c[13].im= (u).c[8].im; \
      (u).c[9].re= 0.5*((v).c[9].re-(v).c[19].re); \
      (u).c[9].im= 0.5*((v).c[19].im+(v).c[9].im); \
      (u).c[19].re= -(u).c[9].re; \
      (u).c[19].im= (u).c[9].im; \
      (u).c[10].re= 0.5*((v).c[10].re-(v).c[25].re); \
      (u).c[10].im= 0.5*((v).c[25].im+(v).c[10].im); \
      (u).c[25].re= -(u).c[10].re; \
      (u).c[25].im= (u).c[10].im; \
      (u).c[11].re= 0.5*((v).c[11].re-(v).c[31].re); \
      (u).c[11].im= 0.5*((v).c[31].im+(v).c[11].im); \
      (u).c[31].re= -(u).c[11].re; \
      (u).c[31].im= (u).c[11].im; \
      (u).c[14].re= 0.; \
      (u).c[14].im= (v).c[14].im-_trim; \
      (u).c[15].re= 0.5*((v).c[15].re-(v).c[20].re); \
      (u).c[15].im= 0.5*((v).c[20].im+(v).c[15].im); \
      (u).c[20].re= -(u).c[15].re; \
      (u).c[20].im= (u).c[15].im; \
      (u).c[16].re= 0.5*((v).c[16].re-(v).c[26].re); \
      (u).c[16].im= 0.5*((v).c[26].im+(v).c[16].im); \
      (u).c[26].re= -(u).c[16].re; \
      (u).c[26].im= (u).c[16].im; \
      (u).c[17].re= 0.5*((v).c[17].re-(v).c[32].re); \
      (u).c[17].im= 0.5*((v).c[32].im+(v).c[17].im); \
      (u).c[32].re= -(u).c[17].re; \
      (u).c[32].im= (u).c[17].im; \
      (u).c[21].re= 0.; \
      (u).c[21].im= (v).c[21].im-_trim; \
      (u).c[22].re= 0.5*((v).c[22].re-(v).c[27].re); \
      (u).c[22].im= 0.5*((v).c[27].im+(v).c[22].im); \
      (u).c[27].re= -(u).c[22].re; \
      (u).c[27].im= (u).c[22].im; \
      (u).c[23].re= 0.5*((v).c[23].re-(v).c[33].re); \
      (u).c[23].im= 0.5*((v).c[33].im+(v).c[23].im); \
      (u).c[33].re= -(u).c[23].re; \
      (u).c[33].im= (u).c[23].im; \
      (u).c[28].re= 0.; \
      (u).c[28].im= (v).c[28].im-_trim; \
      (u).c[29].re= 0.5*((v).c[29].re-(v).c[34].re); \
      (u).c[29].im= 0.5*((v).c[34].im+(v).c[29].im); \
      (u).c[34].re= -(u).c[29].re; \
      (u).c[34].im= (u).c[29].im; \
      (u).c[35].re= 0.; \
      (u).c[35].im= (v).c[35].im-_trim; \
   }((void)0) 

 /***** This fuction compute the hmc force matrix */
 /***** this fuction accumulates on the original matrix u */
#define _suNg_FMAT(u,s) \
   {\
      int _i,_j,_n=0;\
      for (_i=0; _i<6; ++_i){\
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[0]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[0]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[1]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[1]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[2]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[2]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[3]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[3]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[4]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[4]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[5]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[5]); \
         ++_n; \
      }\
   }((void)0) 

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
   _complex_0((r).c[2]); \
   _complex_0((r).c[3]); \
   _complex_0((r).c[4]); \
   _complex_0((r).c[5])

 /***** r=-s */
#define _vector_minus_f(r,s) \
   _complex_minus((r).c[0],(s).c[0]); \
   _complex_minus((r).c[1],(s).c[1]); \
   _complex_minus((r).c[2],(s).c[2]); \
   _complex_minus((r).c[3],(s).c[3]); \
   _complex_minus((r).c[4],(s).c[4]); \
   _complex_minus((r).c[5],(s).c[5])

 /***** r=k*s (k real) */
#define _vector_mul_f(r,k,s) \
   _complex_mulr((r).c[0],(k),(s).c[0]); \
   _complex_mulr((r).c[1],(k),(s).c[1]); \
   _complex_mulr((r).c[2],(k),(s).c[2]); \
   _complex_mulr((r).c[3],(k),(s).c[3]); \
   _complex_mulr((r).c[4],(k),(s).c[4]); \
   _complex_mulr((r).c[5],(k),(s).c[5])

 /***** r=z*s (z complex) */
#define _vector_mulc_f(r,z,s) \
   _complex_mul((r).c[0],(z),(s).c[0]); \
   _complex_mul((r).c[1],(z),(s).c[1]); \
   _complex_mul((r).c[2],(z),(s).c[2]); \
   _complex_mul((r).c[3],(z),(s).c[3]); \
   _complex_mul((r).c[4],(z),(s).c[4]); \
   _complex_mul((r).c[5],(z),(s).c[5])

 /***** r=s1+s2 */
#define _vector_add_f(r,s1,s2) \
   _complex_add((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_add((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_add((r).c[2],(s1).c[2],(s2).c[2]); \
   _complex_add((r).c[3],(s1).c[3],(s2).c[3]); \
   _complex_add((r).c[4],(s1).c[4],(s2).c[4]); \
   _complex_add((r).c[5],(s1).c[5],(s2).c[5])

 /***** r=s1-s2 */
#define _vector_sub_f(r,s1,s2) \
   _complex_sub((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_sub((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_sub((r).c[2],(s1).c[2],(s2).c[2]); \
   _complex_sub((r).c[3],(s1).c[3],(s2).c[3]); \
   _complex_sub((r).c[4],(s1).c[4],(s2).c[4]); \
   _complex_sub((r).c[5],(s1).c[5],(s2).c[5])

 /***** r=s1+i*s2 */
#define _vector_i_add_f(r,s1,s2) \
   _complex_i_add((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_i_add((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_i_add((r).c[2],(s1).c[2],(s2).c[2]); \
   _complex_i_add((r).c[3],(s1).c[3],(s2).c[3]); \
   _complex_i_add((r).c[4],(s1).c[4],(s2).c[4]); \
   _complex_i_add((r).c[5],(s1).c[5],(s2).c[5])

 /***** r=s1-i*s2 */
#define _vector_i_sub_f(r,s1,s2) \
   _complex_i_sub((r).c[0],(s1).c[0],(s2).c[0]); \
   _complex_i_sub((r).c[1],(s1).c[1],(s2).c[1]); \
   _complex_i_sub((r).c[2],(s1).c[2],(s2).c[2]); \
   _complex_i_sub((r).c[3],(s1).c[3],(s2).c[3]); \
   _complex_i_sub((r).c[4],(s1).c[4],(s2).c[4]); \
   _complex_i_sub((r).c[5],(s1).c[5],(s2).c[5])

 /***** r+=s */
#define _vector_add_assign_f(r,s) \
   _complex_add_assign((r).c[0],(s).c[0]); \
   _complex_add_assign((r).c[1],(s).c[1]); \
   _complex_add_assign((r).c[2],(s).c[2]); \
   _complex_add_assign((r).c[3],(s).c[3]); \
   _complex_add_assign((r).c[4],(s).c[4]); \
   _complex_add_assign((r).c[5],(s).c[5])

 /***** r-=s */
#define _vector_sub_assign_f(r,s) \
   _complex_sub_assign((r).c[0],(s).c[0]); \
   _complex_sub_assign((r).c[1],(s).c[1]); \
   _complex_sub_assign((r).c[2],(s).c[2]); \
   _complex_sub_assign((r).c[3],(s).c[3]); \
   _complex_sub_assign((r).c[4],(s).c[4]); \
   _complex_sub_assign((r).c[5],(s).c[5])

 /***** r+=i*s */
#define _vector_i_add_assign_f(r,s) \
   _complex_i_add_assign((r).c[0],(s).c[0]); \
   _complex_i_add_assign((r).c[1],(s).c[1]); \
   _complex_i_add_assign((r).c[2],(s).c[2]); \
   _complex_i_add_assign((r).c[3],(s).c[3]); \
   _complex_i_add_assign((r).c[4],(s).c[4]); \
   _complex_i_add_assign((r).c[5],(s).c[5])

 /***** r-=i*s */
#define _vector_i_sub_assign_f(r,s) \
   _complex_i_sub_assign((r).c[0],(s).c[0]); \
   _complex_i_sub_assign((r).c[1],(s).c[1]); \
   _complex_i_sub_assign((r).c[2],(s).c[2]); \
   _complex_i_sub_assign((r).c[3],(s).c[3]); \
   _complex_i_sub_assign((r).c[4],(s).c[4]); \
   _complex_i_sub_assign((r).c[5],(s).c[5])

 /***** k=Re(r^*s) */
#define _vector_prod_re_f(k,r,s) \
   (k)=_complex_prod_re((r).c[0],(s).c[0]);\
   (k)+=_complex_prod_re((r).c[1],(s).c[1]); \
   (k)+=_complex_prod_re((r).c[2],(s).c[2]); \
   (k)+=_complex_prod_re((r).c[3],(s).c[3]); \
   (k)+=_complex_prod_re((r).c[4],(s).c[4]); \
   (k)+=_complex_prod_re((r).c[5],(s).c[5])

 /***** k=Im(r*s) */
#define _vector_prod_im_f(k,r,s) \
   (k)=_complex_prod_im((r).c[0],(s).c[0]);\
   (k)+=_complex_prod_im((r).c[1],(s).c[1]); \
   (k)+=_complex_prod_im((r).c[2],(s).c[2]); \
   (k)+=_complex_prod_im((r).c[3],(s).c[3]); \
   (k)+=_complex_prod_im((r).c[4],(s).c[4]); \
   (k)+=_complex_prod_im((r).c[5],(s).c[5])

 /***** r+=z*s (z complex) */
#define _vector_mulc_add_assign_f(r,z,s) \
   _complex_mul_assign((r).c[0],(z),(s).c[0]); \
   _complex_mul_assign((r).c[1],(z),(s).c[1]); \
   _complex_mul_assign((r).c[2],(z),(s).c[2]); \
   _complex_mul_assign((r).c[3],(z),(s).c[3]); \
   _complex_mul_assign((r).c[4],(z),(s).c[4]); \
   _complex_mul_assign((r).c[5],(z),(s).c[5])

 /***** r+=k*s (k real) */
#define _vector_mul_add_assign_f(r,k,s) \
   _complex_mulr_assign((r).c[0],(k),(s).c[0]); \
   _complex_mulr_assign((r).c[1],(k),(s).c[1]); \
   _complex_mulr_assign((r).c[2],(k),(s).c[2]); \
   _complex_mulr_assign((r).c[3],(k),(s).c[3]); \
   _complex_mulr_assign((r).c[4],(k),(s).c[4]); \
   _complex_mulr_assign((r).c[5],(k),(s).c[5])

 /***** r=k1*s1+k2*s2 (k1,k2 real, s1,s2 vectors) */
#define _vector_lc_f(r,k1,s1,k2,s2) \
   _complex_rlc((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
   _complex_rlc((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
   _complex_rlc((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2]); \
   _complex_rlc((r).c[3],(k1),(s1).c[3],(k2),(s2).c[3]); \
   _complex_rlc((r).c[4],(k1),(s1).c[4],(k2),(s2).c[4]); \
   _complex_rlc((r).c[5],(k1),(s1).c[5],(k2),(s2).c[5])

 /***** r+=k1*s1+k2*s2 (k1,k2 real, s1,s2 vectors) */
#define _vector_lc_add_assign_f(r,k1,s1,k2,s2) \
   _complex_rlc_assign((r).c[0],(k1),(s1).c[0],(k2),(s2).c[0]); \
   _complex_rlc_assign((r).c[1],(k1),(s1).c[1],(k2),(s2).c[1]); \
   _complex_rlc_assign((r).c[2],(k1),(s1).c[2],(k2),(s2).c[2]); \
   _complex_rlc_assign((r).c[3],(k1),(s1).c[3],(k2),(s2).c[3]); \
   _complex_rlc_assign((r).c[4],(k1),(s1).c[4],(k2),(s2).c[4]); \
   _complex_rlc_assign((r).c[5],(k1),(s1).c[5],(k2),(s2).c[5])

 /***** r=z1*s1+z2*s2 (z1,z2 complex, s1,s2 vectors) */
#define _vector_clc_f(r,z1,s1,z2,s2) \
   _complex_clc((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
   _complex_clc((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
   _complex_clc((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2]); \
   _complex_clc((r).c[3],(z1),(s1).c[3],(z2),(s2).c[3]); \
   _complex_clc((r).c[4],(z1),(s1).c[4],(z2),(s2).c[4]); \
   _complex_clc((r).c[5],(z1),(s1).c[5],(z2),(s2).c[5])

 /***** r=z1*s1+z2*s2 (z1,z2 complex, s1,s2 vectors) */
#define _vector_clc_add_assign_f(r,z1,s1,z2,s2) \
   _complex_clc_assign((r).c[0],(z1),(s1).c[0],(z2),(s2).c[0]); \
   _complex_clc_assign((r).c[1],(z1),(s1).c[1],(z2),(s2).c[1]); \
   _complex_clc_assign((r).c[2],(z1),(s1).c[2],(z2),(s2).c[2]); \
   _complex_clc_assign((r).c[3],(z1),(s1).c[3],(z2),(s2).c[3]); \
   _complex_clc_assign((r).c[4],(z1),(s1).c[4],(z2),(s2).c[4]); \
   _complex_clc_assign((r).c[5],(z1),(s1).c[5],(z2),(s2).c[5])

 /***** z+=r^*s (c complex) */
#define _vector_prod_assign_f(z,r,s) \
   _complex_prod_assign((z),(r).c[0],(s).c[0]); \
   _complex_prod_assign((z),(r).c[1],(s).c[1]); \
   _complex_prod_assign((z),(r).c[2],(s).c[2]); \
   _complex_prod_assign((z),(r).c[3],(s).c[3]); \
   _complex_prod_assign((z),(r).c[4],(s).c[4]); \
   _complex_prod_assign((z),(r).c[5],(s).c[5])

 /***** r-=z*s (z complex) */
#define _vector_project_f(r,z,s) \
   _complex_mul_sub_assign((r).c[0],(z),(s).c[0]); \
   _complex_mul_sub_assign((r).c[1],(z),(s).c[1]); \
   _complex_mul_sub_assign((r).c[2],(z),(s).c[2]); \
   _complex_mul_sub_assign((r).c[3],(z),(s).c[3]); \
   _complex_mul_sub_assign((r).c[4],(z),(s).c[4]); \
   _complex_mul_sub_assign((r).c[5],(z),(s).c[5])

 /***** SU(N) matrix u times SU(N) vector s */
 /***** r=u*s */
#define _suNf_multiply(r,u,s) \
   {\
      int _i,_j,_k=0;for (_i=0; _i<6; ++_i){\
         _complex_mul((r).c[_i],(u).c[_k],(s).c[0]); ++_k; _j=1;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[1]); ++_k;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[2]); ++_k;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[3]); ++_k;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[4]); ++_k;\
         _complex_mul_assign((r).c[_i],(u).c[_k],(s).c[5]); ++_k;\
      }\
   }((void)0) 

 /***** SU(N) matrix u^dagger times SU(N) vector s */
 /***** r=(u^dagger)*s */
#define _suNf_inverse_multiply(r,u,s) \
   {\
      int _i,_j,_k=0;for (_i=0; _i<6; ++_i){\
         _complex_mul_star((r).c[_i],(s).c[0],(u).c[_k]); _j=1;\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[1],(u).c[_k]);\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[2],(u).c[_k]);\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[3],(u).c[_k]);\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[4],(u).c[_k]);\
         _k+=6; _complex_mul_star_assign((r).c[_i],(s).c[5],(u).c[_k]);\
         _k-=29;\
      }\
   }((void)0) 

 /***********************************************************************************
 *
 * Macros for SU(N) matrices
 *
 * Arguments are variables of type suN
 *
 *******************************************************************************/

 /***** u=v^dagger */
#define _suNf_dagger(u,v) \
   {\
      int _i,_n=0,_k=0;\
      for (_i=0; _i<6; ++_i){\
         _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k+=6; _complex_star((u).c[_n],(v).c[_k]);\
         ++_n; _k-=29;\
      }\
   }((void)0) 

 /***** u=v*w */
#define _suNf_times_suNf(u,v,w) \
   {\
      int _i,_y,_n=0,_k=0,_l=0;\
      for (_i=0; _i<6; ++_i){\
         for (_y=0; _y<6; ++_y){\
            _complex_mul((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; _l+=6; _complex_mul_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_n; _k-=5; _l-=29;\
         } _k+=6; _l=0;\
      }\
   }((void)0) 

 /***** u=v*w^+ */
#define _suNf_times_suNf_dagger(u,v,w) \
   {\
      int _i,_y,_n=0,_k=0,_l=0;\
      for (_i=0; _i<6; ++_i){\
         for (_y=0; _y<6; ++_y){\
            _complex_mul_star((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_k; ++_l; _complex_mul_star_assign((u).c[_n],(v).c[_k],(w).c[_l]);\
            ++_n; _k-=5; ++_l;\
         } _k+=6; _l=0;\
      }\
   }((void)0) 

 /***** u=v^+*w */
#define _suNf_dagger_times_suNf(u,v,w) \
   {\
      int _i,_y,_n=0,_k=0,_l=0;\
      for (_i=0; _i<6; ++_i){\
         for (_y=0; _y<6; ++_y){\
            _k=_y; _l=_i;\
            _complex_mul_star((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            _k+=6; _l+=6; _complex_mul_star_assign((u).c[_n],(w).c[_k],(v).c[_l]);\
            ++_n;\
         }\
      }\
   }((void)0) 

 /***** u=0 */
#define _suNf_zero(u) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_0((u).c[_i]); ++_i;\
         _complex_0((u).c[_i]); ++_i;\
         _complex_0((u).c[_i]); ++_i;\
         _complex_0((u).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** u=1 */
#define _suNf_unit(u) \
   {\
      _suNf_zero((u));\
      _complex_1((u).c[0]);\
      _complex_1((u).c[7]);\
      _complex_1((u).c[14]);\
      _complex_1((u).c[21]);\
      _complex_1((u).c[28]);\
      _complex_1((u).c[35]);\
   }((void)0) 

 /***** u=-v */
#define _suNf_minus(u,v) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_minus((u).c[_i],(v).c[_i]); ++_i;\
         _complex_minus((u).c[_i],(v).c[_i]); ++_i;\
         _complex_minus((u).c[_i],(v).c[_i]); ++_i;\
         _complex_minus((u).c[_i],(v).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** u=r*v (u,v mat, r real) */
#define _suNf_mul(u,r,v) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_mulr((u).c[_i],(r),(v).c[_i]); ++_i;\
         _complex_mulr((u).c[_i],(r),(v).c[_i]); ++_i;\
         _complex_mulr((u).c[_i],(r),(v).c[_i]); ++_i;\
         _complex_mulr((u).c[_i],(r),(v).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** u+=v */
#define _suNf_add_assign(u,v) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_add_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_add_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_add_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_add_assign((u).c[_i],(v).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** u-=v */
#define _suNf_sub_assign(u,v) \
   {\
      int _i;for (_i=0; _i<36; ){\
         _complex_sub_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_sub_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_sub_assign((u).c[_i],(v).c[_i]); ++_i;\
         _complex_sub_assign((u).c[_i],(v).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** k=| u |2 ) */
#define _suNf_sqnorm(k,u) \
   {\
      int _i;\
      (k)=0.;\
      for (_i=0; _i<36; ){\
         (k)+=_complex_prod_re((u).c[_i],(u).c[_i]); ++_i;\
         (k)+=_complex_prod_re((u).c[_i],(u).c[_i]); ++_i;\
         (k)+=_complex_prod_re((u).c[_i],(u).c[_i]); ++_i;\
         (k)+=_complex_prod_re((u).c[_i],(u).c[_i]); ++_i;\
      }\
   }((void)0) 

 /***** k=| 1 - u |2 ) */
#define _suNf_sqnorm_m1(k,u) \
   (k)=\
    +_complex_prod_m1_re((u).c[0],(u).c[0])\
    +_complex_prod_re((u).c[1],(u).c[1])\
    +_complex_prod_re((u).c[2],(u).c[2])\
    +_complex_prod_re((u).c[3],(u).c[3])\
    +_complex_prod_re((u).c[4],(u).c[4])\
    +_complex_prod_re((u).c[5],(u).c[5])\
    +_complex_prod_re((u).c[6],(u).c[6])\
    +_complex_prod_m1_re((u).c[7],(u).c[7])\
    +_complex_prod_re((u).c[8],(u).c[8])\
    +_complex_prod_re((u).c[9],(u).c[9])\
    +_complex_prod_re((u).c[10],(u).c[10])\
    +_complex_prod_re((u).c[11],(u).c[11])\
    +_complex_prod_re((u).c[12],(u).c[12])\
    +_complex_prod_re((u).c[13],(u).c[13])\
    +_complex_prod_m1_re((u).c[14],(u).c[14])\
    +_complex_prod_re((u).c[15],(u).c[15])\
    +_complex_prod_re((u).c[16],(u).c[16])\
    +_complex_prod_re((u).c[17],(u).c[17])\
    +_complex_prod_re((u).c[18],(u).c[18])\
    +_complex_prod_re((u).c[19],(u).c[19])\
    +_complex_prod_re((u).c[20],(u).c[20])\
    +_complex_prod_m1_re((u).c[21],(u).c[21])\
    +_complex_prod_re((u).c[22],(u).c[22])\
    +_complex_prod_re((u).c[23],(u).c[23])\
    +_complex_prod_re((u).c[24],(u).c[24])\
    +_complex_prod_re((u).c[25],(u).c[25])\
    +_complex_prod_re((u).c[26],(u).c[26])\
    +_complex_prod_re((u).c[27],(u).c[27])\
    +_complex_prod_m1_re((u).c[28],(u).c[28])\
    +_complex_prod_re((u).c[29],(u).c[29])\
    +_complex_prod_re((u).c[30],(u).c[30])\
    +_complex_prod_re((u).c[31],(u).c[31])\
    +_complex_prod_re((u).c[32],(u).c[32])\
    +_complex_prod_re((u).c[33],(u).c[33])\
    +_complex_prod_re((u).c[34],(u).c[34])\
    +_complex_prod_m1_re((u).c[35],(u).c[35])

 /***** k=Re Tr (u) */
#define _suNf_trace_re(k,u) \
   (k)=_complex_re((u).c[0])+ \
       _complex_re((u).c[7])+ \
       _complex_re((u).c[14])+ \
       _complex_re((u).c[21])+ \
       _complex_re((u).c[28])+ \
       _complex_re((u).c[35])

 /***** k=Im Tr (u) */
#define _suNf_trace_im(k,u) \
   (k)=_complex_im((u).c[0])+ \
       _complex_im((u).c[7])+ \
       _complex_im((u).c[14])+ \
       _complex_im((u).c[21])+ \
       _complex_im((u).c[28])+ \
       _complex_im((u).c[35])

 /***** u=v - v^+ -1/N Tr(v - v^+)*I */
#define _suNf_2TA(u,v) \
   {\
      double _trim; _suNf_trace_im(_trim,(v)); _trim*=(2./6.);\
      (u).c[0].re= 0.; \
      (u).c[0].im= 2.*(v).c[0].im-_trim; \
      (u).c[1].re= (v).c[1].re-(v).c[6].re; \
      (u).c[1].im= (v).c[6].im+(v).c[1].im; \
      (u).c[6].re= -(u).c[1].re; \
      (u).c[6].im= (u).c[1].im; \
      (u).c[2].re= (v).c[2].re-(v).c[12].re; \
      (u).c[2].im= (v).c[12].im+(v).c[2].im; \
      (u).c[12].re= -(u).c[2].re; \
      (u).c[12].im= (u).c[2].im; \
      (u).c[3].re= (v).c[3].re-(v).c[18].re; \
      (u).c[3].im= (v).c[18].im+(v).c[3].im; \
      (u).c[18].re= -(u).c[3].re; \
      (u).c[18].im= (u).c[3].im; \
      (u).c[4].re= (v).c[4].re-(v).c[24].re; \
      (u).c[4].im= (v).c[24].im+(v).c[4].im; \
      (u).c[24].re= -(u).c[4].re; \
      (u).c[24].im= (u).c[4].im; \
      (u).c[5].re= (v).c[5].re-(v).c[30].re; \
      (u).c[5].im= (v).c[30].im+(v).c[5].im; \
      (u).c[30].re= -(u).c[5].re; \
      (u).c[30].im= (u).c[5].im; \
      (u).c[7].re= 0.; \
      (u).c[7].im= 2.*(v).c[7].im-_trim; \
      (u).c[8].re= (v).c[8].re-(v).c[13].re; \
      (u).c[8].im= (v).c[13].im+(v).c[8].im; \
      (u).c[13].re= -(u).c[8].re; \
      (u).c[13].im= (u).c[8].im; \
      (u).c[9].re= (v).c[9].re-(v).c[19].re; \
      (u).c[9].im= (v).c[19].im+(v).c[9].im; \
      (u).c[19].re= -(u).c[9].re; \
      (u).c[19].im= (u).c[9].im; \
      (u).c[10].re= (v).c[10].re-(v).c[25].re; \
      (u).c[10].im= (v).c[25].im+(v).c[10].im; \
      (u).c[25].re= -(u).c[10].re; \
      (u).c[25].im= (u).c[10].im; \
      (u).c[11].re= (v).c[11].re-(v).c[31].re; \
      (u).c[11].im= (v).c[31].im+(v).c[11].im; \
      (u).c[31].re= -(u).c[11].re; \
      (u).c[31].im= (u).c[11].im; \
      (u).c[14].re= 0.; \
      (u).c[14].im= 2.*(v).c[14].im-_trim; \
      (u).c[15].re= (v).c[15].re-(v).c[20].re; \
      (u).c[15].im= (v).c[20].im+(v).c[15].im; \
      (u).c[20].re= -(u).c[15].re; \
      (u).c[20].im= (u).c[15].im; \
      (u).c[16].re= (v).c[16].re-(v).c[26].re; \
      (u).c[16].im= (v).c[26].im+(v).c[16].im; \
      (u).c[26].re= -(u).c[16].re; \
      (u).c[26].im= (u).c[16].im; \
      (u).c[17].re= (v).c[17].re-(v).c[32].re; \
      (u).c[17].im= (v).c[32].im+(v).c[17].im; \
      (u).c[32].re= -(u).c[17].re; \
      (u).c[32].im= (u).c[17].im; \
      (u).c[21].re= 0.; \
      (u).c[21].im= 2.*(v).c[21].im-_trim; \
      (u).c[22].re= (v).c[22].re-(v).c[27].re; \
      (u).c[22].im= (v).c[27].im+(v).c[22].im; \
      (u).c[27].re= -(u).c[22].re; \
      (u).c[27].im= (u).c[22].im; \
      (u).c[23].re= (v).c[23].re-(v).c[33].re; \
      (u).c[23].im= (v).c[33].im+(v).c[23].im; \
      (u).c[33].re= -(u).c[23].re; \
      (u).c[33].im= (u).c[23].im; \
      (u).c[28].re= 0.; \
      (u).c[28].im= 2.*(v).c[28].im-_trim; \
      (u).c[29].re= (v).c[29].re-(v).c[34].re; \
      (u).c[29].im= (v).c[34].im+(v).c[29].im; \
      (u).c[34].re= -(u).c[29].re; \
      (u).c[34].im= (u).c[29].im; \
      (u).c[35].re= 0.; \
      (u).c[35].im= 2.*(v).c[35].im-_trim; \
   }((void)0) 

 /***** u=0.5(v - v^+) -1/(2N) Tr(v - v^+)*I */
#define _suNf_TA(u,v) \
   {\
      double _trim; _suNf_trace_im(_trim,(v)); _trim*=(1./6.);\
      (u).c[0].re= 0.; \
      (u).c[0].im= (v).c[0].im-_trim; \
      (u).c[1].re= 0.5*((v).c[1].re-(v).c[6].re); \
      (u).c[1].im= 0.5*((v).c[6].im+(v).c[1].im); \
      (u).c[6].re= -(u).c[1].re; \
      (u).c[6].im= (u).c[1].im; \
      (u).c[2].re= 0.5*((v).c[2].re-(v).c[12].re); \
      (u).c[2].im= 0.5*((v).c[12].im+(v).c[2].im); \
      (u).c[12].re= -(u).c[2].re; \
      (u).c[12].im= (u).c[2].im; \
      (u).c[3].re= 0.5*((v).c[3].re-(v).c[18].re); \
      (u).c[3].im= 0.5*((v).c[18].im+(v).c[3].im); \
      (u).c[18].re= -(u).c[3].re; \
      (u).c[18].im= (u).c[3].im; \
      (u).c[4].re= 0.5*((v).c[4].re-(v).c[24].re); \
      (u).c[4].im= 0.5*((v).c[24].im+(v).c[4].im); \
      (u).c[24].re= -(u).c[4].re; \
      (u).c[24].im= (u).c[4].im; \
      (u).c[5].re= 0.5*((v).c[5].re-(v).c[30].re); \
      (u).c[5].im= 0.5*((v).c[30].im+(v).c[5].im); \
      (u).c[30].re= -(u).c[5].re; \
      (u).c[30].im= (u).c[5].im; \
      (u).c[7].re= 0.; \
      (u).c[7].im= (v).c[7].im-_trim; \
      (u).c[8].re= 0.5*((v).c[8].re-(v).c[13].re); \
      (u).c[8].im= 0.5*((v).c[13].im+(v).c[8].im); \
      (u).c[13].re= -(u).c[8].re; \
      (u).c[13].im= (u).c[8].im; \
      (u).c[9].re= 0.5*((v).c[9].re-(v).c[19].re); \
      (u).c[9].im= 0.5*((v).c[19].im+(v).c[9].im); \
      (u).c[19].re= -(u).c[9].re; \
      (u).c[19].im= (u).c[9].im; \
      (u).c[10].re= 0.5*((v).c[10].re-(v).c[25].re); \
      (u).c[10].im= 0.5*((v).c[25].im+(v).c[10].im); \
      (u).c[25].re= -(u).c[10].re; \
      (u).c[25].im= (u).c[10].im; \
      (u).c[11].re= 0.5*((v).c[11].re-(v).c[31].re); \
      (u).c[11].im= 0.5*((v).c[31].im+(v).c[11].im); \
      (u).c[31].re= -(u).c[11].re; \
      (u).c[31].im= (u).c[11].im; \
      (u).c[14].re= 0.; \
      (u).c[14].im= (v).c[14].im-_trim; \
      (u).c[15].re= 0.5*((v).c[15].re-(v).c[20].re); \
      (u).c[15].im= 0.5*((v).c[20].im+(v).c[15].im); \
      (u).c[20].re= -(u).c[15].re; \
      (u).c[20].im= (u).c[15].im; \
      (u).c[16].re= 0.5*((v).c[16].re-(v).c[26].re); \
      (u).c[16].im= 0.5*((v).c[26].im+(v).c[16].im); \
      (u).c[26].re= -(u).c[16].re; \
      (u).c[26].im= (u).c[16].im; \
      (u).c[17].re= 0.5*((v).c[17].re-(v).c[32].re); \
      (u).c[17].im= 0.5*((v).c[32].im+(v).c[17].im); \
      (u).c[32].re= -(u).c[17].re; \
      (u).c[32].im= (u).c[17].im; \
      (u).c[21].re= 0.; \
      (u).c[21].im= (v).c[21].im-_trim; \
      (u).c[22].re= 0.5*((v).c[22].re-(v).c[27].re); \
      (u).c[22].im= 0.5*((v).c[27].im+(v).c[22].im); \
      (u).c[27].re= -(u).c[22].re; \
      (u).c[27].im= (u).c[22].im; \
      (u).c[23].re= 0.5*((v).c[23].re-(v).c[33].re); \
      (u).c[23].im= 0.5*((v).c[33].im+(v).c[23].im); \
      (u).c[33].re= -(u).c[23].re; \
      (u).c[33].im= (u).c[23].im; \
      (u).c[28].re= 0.; \
      (u).c[28].im= (v).c[28].im-_trim; \
      (u).c[29].re= 0.5*((v).c[29].re-(v).c[34].re); \
      (u).c[29].im= 0.5*((v).c[34].im+(v).c[29].im); \
      (u).c[34].re= -(u).c[29].re; \
      (u).c[34].im= (u).c[29].im; \
      (u).c[35].re= 0.; \
      (u).c[35].im= (v).c[35].im-_trim; \
   }((void)0) 

 /***** This fuction compute the hmc force matrix */
 /***** this fuction accumulates on the original matrix u */
#define _suNf_FMAT(u,s) \
   {\
      int _i,_j,_n=0;\
      for (_i=0; _i<6; ++_i){\
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[0]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[0]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[1]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[1]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[2]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[2]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[3]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[3]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[4]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[4]); \
         ++_n; \
         _complex_mul_star_assign((u).c[_n],(s).c[0].c[_i],(s).c[2].c[5]); \
         _complex_mul_star_assign((u).c[_n],(s).c[1].c[_i],(s).c[3].c[5]); \
         ++_n; \
      }\
   }((void)0) 

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
