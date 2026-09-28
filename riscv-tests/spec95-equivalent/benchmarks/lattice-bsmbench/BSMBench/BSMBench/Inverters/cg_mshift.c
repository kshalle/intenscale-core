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

#include "inverters.h"
#include "linear_algebra.h"
#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include "global.h"
#include "memory.h"
#include "logger.h"
#include <assert.h>


 /*****
 * performs the multi-shifted CG inversion:
 * out[i] = (M-(par->shift[i]))^-1 in
 * returns the number of cg iterations done.
 */
static int cg_mshift_core(short int *sflags, mshift_par *par, spinor_operator M, spinor_field *in, spinor_field *out){

  spinor_field *k,*r,*Mk;
  spinor_field *p;
  double omega, oldomega, gamma;
  double alpha, lambda, delta;
  double innorm2;
  double *z1, *z2, *z3;

  int i;
  int cgiter;
  unsigned short notconverged;

  /* fare qualche check sugli input */
  assert(par->n>0);
#ifndef CHECK_SPINOR_MATCHING
  for(i=0;i<par->n;++i)
    _TWO_SPINORS_MATCHING(in,&out[i]);
#endif

  /* allocate spinors fields and aux real variables */
  p = alloc_spinor_field_f(3+par->n,in->type);
  k=p+par->n;
  r=k+1;
  Mk=r+1;

  z1 = malloc(sizeof(*z1)*(par->n));
  z2 = malloc(sizeof(*z2)*(par->n));
  z3 = malloc(sizeof(*z3)*(par->n));

  /* init recursion */
  cgiter = 0;
  omega = 1.;
  gamma = 0.;
  innorm2=spinor_field_sqnorm_f(in);
  if(par->n==1) { /* non multishift case */
    /* use out[0] as initial guess */
    M(Mk,&out[0]);
    ++cgiter;
    spinor_field_mul_add_assign_f(Mk,-par->shift[0],&out[0]);
    spinor_field_sub_f(r,in,Mk);

  } else { /* initial guess = 0 for multishift */
    spinor_field_copy_f(r, in);
  }
  spinor_field_copy_f(k, r);
  delta=spinor_field_sqnorm_f(r);
  for (i=0; i<(par->n); ++i) {
    z1[i]=z2[i]=1.;
    spinor_field_copy_f(&p[i], r);
    if(par->n!=1) spinor_field_zero_f(&out[i]);
    sflags[i]=1;
  }

  /* cg recursion */
  do {
	  /*printf("recursin' %d\n", cgiter);*/
    M(Mk,k);
    alpha = spinor_field_prod_re_f(k,Mk);
    oldomega = omega;
    omega = - delta/alpha;
    for (i=0; i<(par->n); ++i) {
      if(sflags[i]) {
        z3[i] = oldomega*z1[i]*z2[i]/(omega*gamma*(z1[i]-z2[i])+z1[i]*oldomega*(1.+par->shift[i]*omega));
        spinor_field_mul_add_assign_f(&out[i],-omega*z3[i]/z2[i],&p[i]);
      }
    }
    spinor_field_mul_add_assign_f(r,omega,Mk);
    lambda=spinor_field_sqnorm_f(r);
    gamma=lambda/delta;
    delta=lambda;

    spinor_field_mul_f(k,gamma,k);
    spinor_field_add_assign_f(k,r);
    notconverged=0; /* assume that all vectors have converged */
    for (i=0; i<(par->n); ++i) {
      /* check convergence of vectors */
      if(delta*z3[i]*z3[i]<par->err2*innorm2) sflags[i]=0; 
      if(sflags[i]){
        notconverged++;
        spinor_field_mul_f(&p[i],gamma*z3[i]*z3[i]/(z2[i]*z2[i]),&p[i]);
        spinor_field_mul_add_assign_f(&p[i],z3[i],r);
        z1[i]=z2[i];
        z2[i]=z3[i];
      }
    }

    /* Uncomment this to print cg recursion parameters 
       lprintf("CGTEST",0,"[ %d ] alpha=%e\n",cgiter,alpha);
       lprintf("CGTEST",0,"[ %d ] omega=%e\n",cgiter,omega);
       lprintf("CGTEST",0,"[ %d ] still runnning=%d\n",cgiter,notconverged);
	   for (i=0;i<par->n;++i) lprintf("CGTEST",0,"z3[%d]=%e; test=[%e,%e]\n",i,z3[i],delta*z3[i]*z3[i],par->err2*innorm2);
       lprintf("CGTEST",0,"\n[ %d ] gamma=%e\n",cgiter,gamma);
       lprintf("CGTEST",0,"[ %d ] delta=%e\n",cgiter,delta);
    */

    ++cgiter;
  } while ((par->max_iter==0 || cgiter<par->max_iter) && notconverged);

  /* test results */
  for(i=0;i<par->n;++i){
	  /*printf("testin'\n");*/
    double norm;
    M(Mk,&out[i]);
    ++cgiter;
    spinor_field_mul_add_assign_f(Mk,-par->shift[i],&out[i]);
    spinor_field_sub_f(Mk,Mk,in);
    norm=spinor_field_sqnorm_f(Mk)/spinor_field_sqnorm_f(in);
    sflags[i]=1;
    if (fabs(norm)>par->err2){
      sflags[i]=0;
      //lprintf("INVERTER",30,"CG failed on vect %d: err2 = %1.8e > %1.8e\n",i,norm,par->err2);
    } else {
      //lprintf("INVERTER",20,"CG inversion: err2 = %1.8e < %1.8e\n",norm,par->err2);
    }
  }

  /* free memory */
  free_spinor_field(p);
  free(z1); free(z2); free(z3);

  /* return number of cg iter */
  return cgiter;
}

int cg_mshift(mshift_par *par, spinor_operator M, spinor_field *in, spinor_field *out){ 
  int cgiter,msiter;
  int i;
  mshift_par par_save=*par;
  short int sflags[par->n];

  cgiter=cg_mshift_core(sflags, par, M, in, out);
  msiter=cgiter; /* save multishift iterations for logging */

  par->n=1;
  for(i=0;i<par_save.n;++i) {
    while (sflags[i]==0) {
      par->shift=par_save.shift+i;
      cgiter+=cg_mshift_core(sflags+i, par, M, in, out+i);
      //if((++rep)%5==0)
      //  lprintf("INVERTER",-10,"CG_mshift recursion = %d (precision too high?)\n",rep);
    }
  }

  *par=par_save;

  //lprintf("INVERTER",10,"CG_mshift: MVM = %d/%d\n",msiter,cgiter);

  return cgiter;
}
