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


#ifndef INPUT_PAR_H
#define INPUT_PAR_H

typedef enum _datatype_t {
  INT_T,
  LONG_T,
  UNSIGNED_T,
  DOUBLE_T,
  STRING_T
} datatype_t;

typedef struct _input_record_t {
  char *name;
  char *descr;
  datatype_t type;
  void *ptr;
} input_record_t;

 /***** Global or common variables */
typedef struct _input_glb {
  /* global size of lattice and processing grid */
  /* THIS ARE DEFINED GLOBALLY !!! */
  /* int GLB_T, GLB_X, GLB_Y, GLB_Z; */
  /* int NP_T, NP_X, NP_Y, NP_Z; */

  /* random numbers */
  int random_seed;

  /* for the reading function */
  input_record_t read[10];
  
} input_glb;

#define init_input_glb(varname) \
{ \
  .read={\
    {"GLB_T", "GLB_T = %d", INT_T, &GLB_T},\
    {"GLB_X", "GLB_X = %d", INT_T, &GLB_X},\
    {"GLB_Y", "GLB_Y = %d", INT_T, &GLB_Y},\
    {"GLB_Z", "GLB_Z = %d", INT_T, &GLB_Z},\
    {"NP_T", "NP_T = %d", INT_T, &NP_T},\
    {"NP_X", "NP_X = %d", INT_T, &NP_X},\
    {"NP_Y", "NP_Y = %d", INT_T, &NP_Y},\
    {"NP_Z", "NP_Z = %d", INT_T, &NP_Z},\
    {"random seed", "seed = %d", INT_T, &(varname).random_seed},\
    {NULL, NULL, 0, NULL}\
  },\
}


#endif /* INPUT_PAR_H */
