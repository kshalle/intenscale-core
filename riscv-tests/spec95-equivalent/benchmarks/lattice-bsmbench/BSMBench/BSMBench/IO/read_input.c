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

#include "io.h"
#include "error.h"
#include "global.h"
#include "logger.h"
#include <memory.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

static void mpi_broadcast_parameters(input_record_t crec[]) {
#ifdef WITH_MPI
  int stringlen=0;
  for (; crec->name!=NULL; ++crec) {
    if(crec->descr==NULL) continue;
    switch(crec->type) {
      case INT_T:
        MPI_Bcast(crec->ptr,1,MPI_INT,0,MPI_COMM_WORLD);
        break;
      case LONG_T:
	MPI_Bcast(crec->ptr,1,MPI_LONG,0,MPI_COMM_WORLD);
	break;
      case UNSIGNED_T:
        MPI_Bcast(crec->ptr,1,MPI_UNSIGNED,0,MPI_COMM_WORLD);
        break;
      case DOUBLE_T:
        MPI_Bcast(crec->ptr,1,MPI_DOUBLE,0,MPI_COMM_WORLD);
        break;
      case STRING_T:
        stringlen=strlen(crec->ptr)+1;
        MPI_Bcast(&stringlen,1,MPI_INT,0,MPI_COMM_WORLD);
        MPI_Bcast(crec->ptr,stringlen,MPI_CHAR,0,MPI_COMM_WORLD);
        break;
      default:
        error(1,1,"read_input " __FILE__, "Unknown type in input descriptor\n");
    }
  }
#endif
  
}

int read_input(input_record_t irec[], char *filename) {
  FILE *inputfile;
  fpos_t pos;
  char buf[256];
  int npar=0, *found=NULL;
  input_record_t *crec=NULL;

  if (irec==NULL || irec->name==NULL) return 2; /* no input parameter specified */

  /* when using mpi only PID==0 reads the input file */
  if (PID!=0) {
    mpi_broadcast_parameters(irec);
    return 0;
  }

  error((inputfile=fopen(filename,"r"))==NULL,1,"read_input " __FILE__ ,
      "Failed to open input file\n");

  /* count the input parameters */
  for (crec=irec, npar=0; crec->name!=NULL; ++crec) { ++npar; }
  found=calloc(npar,sizeof(*found)); /* set found to zero */

  do {
    int count=0;
    int nowarn=0;
    /*skip white space*/
    nowarn=fscanf(inputfile," ");

    fgetpos(inputfile,&pos);

    /* skip comments */
    nowarn=fscanf(inputfile,"//%n",&count);
    if(count==2) { nowarn=fscanf(inputfile,"%*[^\n]"); goto NEXTLOOP; } if(feof(inputfile)) break; fsetpos(inputfile,&pos);

    /* read variables as described in irec */
    for (count=0; count<npar; ++count) {
      if(irec[count].descr==NULL) continue;
      if(fscanf(inputfile, irec[count].descr, irec[count].ptr)==1) {
        found[count]=1;
        goto NEXTLOOP;
      } 
      if(feof(inputfile)) goto ENDLOOP; 
      fsetpos(inputfile,&pos);
    }

    nowarn=fscanf(inputfile,"%s",buf);
    lprintf("READINPUT",10000,"Ignoring unknown token: [%s]\n",buf);

NEXTLOOP:

    if(ferror(inputfile)) {
      lprintf("READINPUT",0,"Cannot read input file.\n");
      perror(0);
      return 1;
    }

  } while (!feof(inputfile));

ENDLOOP:

  fclose(inputfile);

  while(npar>0) {
    --npar;
    if (found[npar]==0 && irec[npar].descr!=NULL) 
      lprintf("READINPUT",0,
          "Warning: input parameter [%s] not found in [%s]!\n",
          irec[npar].name, filename );
  }
  free(found);

  mpi_broadcast_parameters(irec);

  return 0;
}





