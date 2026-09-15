 /*******************************************************************************\
 * Copyright (c) 2008, Claudio Pica     and Agostino Patella                     *   
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

#ifndef SUN_REPR_FUNC_H
#define SUN_REPR_FUNC_H

#if (NG!=2) 
#error : Mismatch between NG and representation functions!
#endif
#if (NF!=3) 
#error : Mismatch between NF and representation functions!
#endif



 /*****
 *   _FUND_NORM2 = C(FUND)
 *   _FUND_NORM2 = C(REPR)
 */
#define _FUND_NORM2 (+5.000000000000000e-01)
#define _REPR_NORM2 (+2.000000000000000e+00)

 /*****
 *	v double matrix 3*3
 *	u complex matrix 2*2
 *	v is the representation of u, as element of SU(2)
 */
 /*****
#define _group_represent(v,u) \
REPR::GROUP_REPRESENT:(v):(u)
 */

 /*****
 *	m double matrix 3*3
 *	h real 3-vector
 *	m = sum(A) i*h(A)*rT(A)
 */
#define _algebra_represent(m,h) \
	(m).c[0] = 0.0; \
	(m).c[1] = +(h).c[2]; \
	(m).c[2] = -(h).c[1]; \
	(m).c[3] = -(h).c[2]; \
	(m).c[4] = 0.0; \
	(m).c[5] = +(h).c[0]; \
	(m).c[6] = +(h).c[1]; \
	(m).c[7] = -(h).c[0]; \
	(m).c[8] = 0.0;



 /*****
 *	h real 3-vector
 *	m double matrix 3*3
 *	h(A) = -i*ReTr[rT(A).m]/C(r)
 */
#define _algebra_project(h,m) \
	(h).c[0] = -4.999999999999999e-01*(-(m).c[5]+(m).c[7]); \
	(h).c[1] = +4.999999999999999e-01*(-(m).c[2]+(m).c[6]); \
	(h).c[2] = -4.999999999999999e-01*(-(m).c[1]+(m).c[3]);



 /*****
 *	m double matrix 2*2
 *	h real 3-vector
 *	m = sum(A) i*h(A)*T(A)
 */
#define _fund_algebra_represent(m,h) \
	(m).c[0].re = 0.0; \
	(m).c[0].im = +5.000000000000000e-01*(h).c[2]; \
	(m).c[1].re = -5.000000000000000e-01*(h).c[0]; \
	(m).c[1].im = +5.000000000000000e-01*(h).c[1]; \
	(m).c[2].re = +5.000000000000000e-01*(h).c[0]; \
	(m).c[2].im = +5.000000000000000e-01*(h).c[1]; \
	(m).c[3].re = 0.0; \
	(m).c[3].im = -5.000000000000000e-01*(h).c[2];



 /*****
 *	h real 3-vector
 *	m double matrix 2*2
 *	h(A) = -i*ReTr[T(A).m]/C(FUND)
 */
#define _fund_algebra_project(h,m) \
	(h).c[0] = -(m).c[1].re+(m).c[2].re; \
	(h).c[1] = +(m).c[1].im+(m).c[2].im; \
	(h).c[2] = -(-(m).c[0].im+(m).c[3].im);


#endif 
