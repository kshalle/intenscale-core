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
 * File global.h
 *
 * Global parameters and arrays
 *
 *******************************************************************************/

#ifndef GLOBAL_H
#define GLOBAL_H

#include <stddef.h>

#ifdef MAIN_PROGRAM
#  define GLB_VAR(type,name,init) type name init
#else
#  define GLB_VAR(type,name,init) extern type name
#endif

 /***** local lattice attributes */
GLB_VAR(int,T,=0); /* local lattice size in direction T */ 
GLB_VAR(int,X,=0); /* local lattice size in direction X */
GLB_VAR(int,Y,=0); /* local lattice size in direction Y */
GLB_VAR(int,Z,=0); /* local lattice size in direction Z */
 /***** this two probably are not more needed... */
GLB_VAR(int,VOL3,=0); 
GLB_VAR(int,VOLUME,=0);

 /***** Nodes attributes
 * NP = number of processes in each direction 
 * 1 => local direction
 */
GLB_VAR(int,NP_T,=1); /* number of processes in direction T */
GLB_VAR(int,NP_X,=1); /* number of processes in direction X */
GLB_VAR(int,NP_Y,=1); /* number of processes in direction Y */
GLB_VAR(int,NP_Z,=1); /* number of processes in direction Z */

 /***** global lattice attributes */
GLB_VAR(int,GLB_T,=0); /* global size of the lattice in direction T */
GLB_VAR(int,GLB_X,=0); /* global size of the lattice in direction X */
GLB_VAR(int,GLB_Y,=0); /* global size of the lattice in direction Y */
GLB_VAR(int,GLB_Z,=0); /* global size of the lattice in direction Z */

GLB_VAR(int,T_BORDER,=0);
GLB_VAR(int,X_BORDER,=0);
GLB_VAR(int,Y_BORDER,=0);
GLB_VAR(int,Z_BORDER,=0);

GLB_VAR(int,T_EXT,=0);
GLB_VAR(int,X_EXT,=0);
GLB_VAR(int,Y_EXT,=0);
GLB_VAR(int,Z_EXT,=0);

 /***** MPI stuff */
GLB_VAR(int,WORLD_SIZE,=1); /* mpi rank for this process */
GLB_VAR(int,CART_SIZE,=1); /* mpi rank for this process */
#ifdef WITH_MPI
#include <mpi.h>
GLB_VAR(MPI_Comm,cart_comm,=MPI_COMM_NULL);
#endif

 /***** ID for this process */
GLB_VAR(int,PID,=0); /* ID of this process */

GLB_VAR(int,CID,=0); /* cartesian ID for this process */
GLB_VAR(int,COORD[4],={0}); /* cartesian coordinates for this process */
GLB_VAR(int,PSIGN,=0); /* parity of this process */

 /***** Geometry indexes */
GLB_VAR(int,*ipt, =NULL);
GLB_VAR(int,*ipt_4d,=NULL);
GLB_VAR(int,*iup,=NULL);
GLB_VAR(int,*idn,=NULL);

 /***** Geometry structures */
#define ipt(t,x,y,z) ipt[((((t)+T_BORDER)*(X_EXT)+((x)+X_BORDER))*(Y_EXT)+((y)+Y_BORDER))*(Z_EXT)+((z)+Z_BORDER)]
#define ipt_ext(t,x,y,z) ipt[(((t)*(X_EXT)+(x))*(Y_EXT)+(y))*(Z_EXT)+(z)]
#define ipt_4d(t,x) ipt_4d[(t)*(VOL3)+(x)]
#define iup(site,dir) iup[(site)*4+(dir)]
#define idn(site,dir) idn[(site)*4+(dir)]

 /***** Geometry structures */
#include "geometry.h"

GLB_VAR(geometry_descriptor,glattice,={0}); /* global lattice */
GLB_VAR(geometry_descriptor,glat_even,={0}); /* global even lattice */
GLB_VAR(geometry_descriptor,glat_odd,={0}); /* global odd lattice */


 /***** Gauge field */
#include "field_ordering.h"
#include "suN_types.h"
#include "spinor_field.h"

GLB_VAR(suNg_field,*u_gauge,=NULL);
GLB_VAR(suNf_field,*u_gauge_f,=NULL);

#define pu_gauge(ix,mu) ((u_gauge->ptr)+coord_to_index(ix,mu))
#define pu_gauge_f(ix,mu) ((u_gauge_f->ptr)+coord_to_index(ix,mu))


 /***** Boundary conditions */
#ifdef ANTIPERIODIC_BC_T
#define BC_T 1.
#else
#define BC_T 0.
#endif

#ifdef ANTIPERIODIC_BC_X
#define BC_X 1.
#else
#define BC_X 0.
#endif

#ifdef ANTIPERIODIC_BC_Y
#define BC_Y 1.
#else
#define BC_Y 0.
#endif

#ifdef ANTIPERIODIC_BC_Z
#define BC_Z 1.
#else
#define BC_Z 0.
#endif

#ifdef MAIN_PROGRAM
double bc[4]={BC_T,BC_X,BC_Y,BC_Z};
#else
extern double bc[4];
#endif

#undef BC_T
#undef BC_x
#undef BC_Y
#undef BC_Z

 /***** input parameters */
#include "input_par.h"
GLB_VAR(input_glb,glb_var,=init_input_glb(glb_var));

#undef GLB_VAR


#endif


