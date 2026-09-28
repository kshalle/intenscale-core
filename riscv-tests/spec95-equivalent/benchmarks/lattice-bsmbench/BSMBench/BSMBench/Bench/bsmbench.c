 /********************************************************************************
 *                            HPC Benchmarking tool                              *
 * (c) 2012-2015 Ed Bennett, Biagio Lucini, Kirk Jordan, Agostino Patella,       *
 *          Claudio Pica, Antonio Rago                                           *
 * All rights reserved.                                                          *
 *                                                                               *
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
 *           Rev. D81 (2010) 094503, DOI 10.1103/PhysRevD.81.094503              *
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
 *********************************************************************************/

#define MAIN_PROGRAM

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include "io.h"
#include "random.h"
#include "error.h"
#include "geometry.h"
#include "memory.h"
#include "update.h"
#include "global.h"
#include "observables.h"
#include "suN.h"
#include "suN_types.h"
#include "dirac.h"
#include "linear_algebra.h"
#include "inverters.h"
#include "utils.h"
#include "logger.h"
#include "communications.h"

#include <assert.h>
#include <update.h>

double g_resid;

#define MIN_TIME 400.0 /* Define minimum time per test in seconds; maximum time is roughly double */

/*#define TIMING YES*/ /* To enable performance analysis using libmpihpm on Blue Gene machines */
#ifdef TIMING
void HPM_Start(char *);
void HPM_Stop(char *);
#endif

#ifdef WITH_MPI
#define get_time() MPI_Wtime()
#define time_var double
#define print_time(a) (a)

#elif defined __MACH__
#include <mach/mach_time.h>
#include <stdint.h>

#define get_time() mach_absolute_time()
#define time_var uint64_t
mach_timebase_info_data_t info;
#define print_time(a) ((double)((a)) * (double)info.numer * 1E-9 / (double)info.denom)

#else
#include <time.h>
#define time_var time_t
#define get_time() time(NULL)
#define print_time(a) ((double)((a)))

#endif

enum { _g5=0, _g5_g0g5_re };

extern spinor_field *pf;

typedef enum { bg_core = 0, bg_node, bg_card, bg_rack , unknown} bg_unit;


enum { PRECISION = 1,
       SQNORM = 2,
       MAD = 4,
       DPHI = 8
} test_set_e;

/* Bench parameters */
typedef struct _input_bench {
  int nfields;
  int niters;
  double bg_flops;
  double bg_dphi_flops;
  bg_unit reference_unit;
  double dphi_nflops;
  double sqnorm_nflops;
  double mad_nflops;
  double mass;
  int test_set;

  input_record_t read[11];
} input_bench;

#define init_input_bench(varname)					\
  {									\
    .read={								\
      {"number of spinor fields to allocate", "bench:nfields = %d", INT_T, &(varname).nfields}, \
      {"number of iterations before precision test", "bench:niters = %d", INT_T, &(varname).niters}, \
      {"reference FLOP/s on Blue Gene/P", "bench:bg_flops = %lf", DOUBLE_T, &(varname).bg_flops}, \
	  {"reference FLOP/s for Dphi test only on Blue Gene/P", "bench:bg_dphi_flops = %lf", DOUBLE_T, &(varname).bg_dphi_flops}, \
      {"reference unit on Blue Gene/P", "bench:reference_unit = %d", INT_T, &(varname).reference_unit}, \
      {"number of FLOPs in the Dphi test", "bench:dphi:nflops = %lf", DOUBLE_T, &(varname).dphi_nflops}, \
      {"number of FLOPs in the sqnorm test", "bench:sqnorm:nflops = %lf", DOUBLE_T, &(varname).sqnorm_nflops}, \
      {"number of FLOPs in the mad test", "bench:mad:nflops = %lf", DOUBLE_T, &(varname).mad_nflops}, \
      {"Dirac mass", "bench:mass = %lf", DOUBLE_T, &(varname).mass},	\
      {"which tests to run", "bench:test_set = %d", INT_T, &(varname).test_set}, \
      {NULL, NULL, 0, NULL}						\
    }									\
  }

char input_filename[256] = "input_file";
char output_filename[256] = "mesons.out";
enum { UNKNOWN_CNFG, DYNAMICAL_CNFG, QUENCHED_CNFG };

input_bench bench_var = init_input_bench(bench_var);

typedef struct {
  char string[256];
  int t, x, y, z;
  int nc, nf;
  double b, m;
  int n;
  int type;
} filename_t;

void read_cmdline(int argc, char* argv[]) {
    int i, ai=0, ao=0, am=0;
    FILE *input=NULL;
    for (i=1;i<argc;i++) {
        if (strcmp(argv[i],"-i")==0) ai=i+1;
        else if (strcmp(argv[i],"-o")==0) ao=i+1;
        else if (strcmp(argv[i],"-m")==0) am=i;
    }
    
    if (am != 0) {
        exit(0);
    }
    
    if (ao!=0) strcpy(output_filename,argv[ao]);
    strcpy(input_filename,argv[ai]);
    
    error(ai==0,1,"parse_cmdline [bsmbench.c]",
          "Syntax: bsmbench {-i testfile.test} [-o filename.out] [-m]");

    strcpy(input_filename,"");
    strcpy(input_filename,argv[ai]);
    error((input=fopen(input_filename,"r"))==NULL,1,"parse_cmdline [bsmbench.c]" ,
              "Failed to open list file\n");
    fclose(input);
}

int main(int argc,char *argv[])
{
	/*necessary to initialise timer on OS X */
#if defined(__MACH__) && !defined(WITH_MPI)
	mach_timebase_info(&info);
#endif
    int k, i;
    char tmp[256];/*, *cptr;*/
	
	int fail = 0;

    /* setup process id and communications */
    read_cmdline(argc, argv);
    setup_process(&argc,&argv);

    /* logger setup */
    /* disable logger for MPI processes != 0 */
    logger_setlevel(0,30);
    if (PID!=0) { logger_disable(); }
    if (PID==0) {
        sprintf(tmp,">%s",output_filename); logger_stdout(tmp);
        sprintf(tmp,"err_%d",PID); freopen(tmp,"w",stderr);
    }
    
    lprintf("MAIN",0,"PId =  %d [world_size: %d]\n\n",PID,WORLD_SIZE);
    lprintf("MAIN",0,"input file [%s]\n",input_filename);
    lprintf("MAIN",0,"output file [%s]\n",output_filename);    

    /* read input file */
	int input_error = 0;
    input_error += read_input(glb_var.read, input_filename);
    input_error += read_input(bench_var.read, input_filename);
	
	if(input_error) {
		lprintf("MAIN",0,"Error reading input file %s. Terminating.\n", input_filename);
		return 1;
	}

	/* setup communication geometry */
    if (geometry_init() == 1) {
        finalize_process();
        return 0;
    }
    lprintf("MAIN",0,"Gauge group: SU(%d)\n",NG);
    lprintf("MAIN",0,"Fermion representation: " REPR_NAME " [dim=%d]\n",NF);
    lprintf("MAIN",0,"global size is %dx%dx%dx%d\n",GLB_T,GLB_X,GLB_Y,GLB_Z);
    lprintf("MAIN",0,"proc grid is %dx%dx%dx%d\n",NP_T,NP_X,NP_Y,NP_Z);
    lprintf("MAIN",0,"Fermion boundary conditions: %.2f,%.2f,%.2f,%.2f\n",bc[0],bc[1],bc[2],bc[3]);
    
    /* setup lattice geometry */
    geometry_mpi_eo();
    lprintf("MAIN",0,"Geometry buffers: %d\n",glattice.nbuffers);

    lprintf("MAIN",0,"local size is %dx%dx%dx%d\n",T,X,Y,Z);
    lprintf("MAIN",0,"extended local size is %dx%dx%dx%d\n",T_EXT,X_EXT,Y_EXT,Z_EXT);
    lflush();
	
    rand_init(glb_var.random_seed + PID);

    /* alloc global gauge fields */
    u_gauge=alloc_gfield(&glattice);
#ifndef REPR_FUNDAMENTAL
    u_gauge_f=alloc_gfield_f(&glattice);
#else
	u_gauge_f=(suNf_field *)((void*)u_gauge);
#endif

    
	/* ALLOCATE SPINOR FIELDS */
	int nfields = bench_var.nfields;
	/*int niters = bench_var.niters;*/
	int niters_sqnorm = 0;
	int niters_mad = 0;
	int niters_dphi = 0;
	double sqnorm_time = 0.;
	double mad_time = 0.;
	double dphi_time = 0.;
	int stop = -1;
	double incrementor = 0.;
	
	spinor_field* test_fields[nfields];
	for(k = 0; k < nfields; k++) {
		test_fields[k] = alloc_spinor_field_f(1, &glattice);
		gaussian_spinor_field(test_fields[k]);
	}
	
	/* Precision test */
	if(bench_var.test_set & PRECISION) {
	  time_var prec_start, prec_end;
	  mshift_par inv_par;
	  inv_par.n = 1;
	  inv_par.shift = malloc(sizeof(double) * 1);
	  *(inv_par.shift) = 1.0;
	  inv_par.err2 = 0.001;
	  inv_par.max_iter = 10;
	  
#ifdef TIMING
	  HPM_Start("precision");
#endif	
	  for(i = 0; i < bench_var.niters; i++) {
	    /*printf("%d\n",i);*/
	    Dphi(bench_var.mass, test_fields[1],test_fields[0]);
	    spinor_field_sub_assign_f(test_fields[1],test_fields[0]);
	    Dphi(bench_var.mass, test_fields[0],test_fields[1]);
	    spinor_field_sub_assign_f(test_fields[0],test_fields[1]);
	    
	  }
	  prec_start = get_time();
	  cg_mshift(&inv_par, &H2, test_fields[0], test_fields[1]);
#ifdef TIMING
	  HPM_Stop("precision");
#endif
	  prec_end = get_time();
	
	  double prec_time = print_time(prec_end - prec_start);
	  lprintf("BENCH", 0, "Precision test completed in %.2lf seconds\n", prec_time);
	  
	  double delta;
	  H2(test_fields[2],test_fields[1]);
	  spinor_field_mul_add_assign_f(test_fields[2],-1.0,test_fields[1]);
	  spinor_field_sub_f(test_fields[2],test_fields[2],test_fields[0]);
	  delta=spinor_field_sqnorm_f(test_fields[2])/spinor_field_sqnorm_f(test_fields[0]);
	  
	  if(isfinite(delta))
	    {
	      if (fabs(delta) > 0.9)
		{
		  fail += 1;
		  lprintf("BENCH",0,"Precision test failed: delta = %.3le\n",delta);
		} 
	      else
		{
		  lprintf("BENCH",0,"Precision test passed: delta = %.3le\n",delta);
		}
	    }
	  else
	    {
	      lprintf("BENCH",0,"Precision test indeterminate: delta = %.3le\n", delta);
	    }
	}
	else {
	  lprintf("BENCH",0,"Precision test disabled.\n");
	  fflush(stdout);
	}
	
#ifdef WITH_MPI
	MPI_Barrier(MPI_COMM_WORLD);
#endif

	lflush();

	if(bench_var.test_set & SQNORM) {
	  niters_sqnorm = 1;
	  incrementor = 0.0;
	  /* spinor_field_sqnorm_f each spinor */
	  time_var sqnorm_start, sqnorm_end;
	  sqnorm_start = get_time();
	  stop = 0;

#ifdef TIMING
	  HPM_Start("sqnorm");
#endif	
	  do
	    {
	      for(i = 0; i < niters_sqnorm; i++) {
		for(k = 0; k < nfields; k++) {
		  incrementor += spinor_field_sqnorm_f(test_fields[k]);
#ifdef WITH_MPI
		  MPI_Barrier(MPI_COMM_WORLD);
#endif
		}
	      }
	      niters_sqnorm *= 2;
	      sqnorm_end = get_time();
#ifdef WITH_MPI
	      MPI_Barrier(MPI_COMM_WORLD);
	      if(PID == 0)
		{
		  if(print_time(sqnorm_end - sqnorm_start) > MIN_TIME)
		    {
		      stop = 1;
		    } 
		}
	      MPI_Bcast(&stop, 1, MPI_INT, 0, MPI_COMM_WORLD);
#else
	      if(print_time(sqnorm_end - sqnorm_start) > MIN_TIME) stop = 1;
#endif
	    } while (!stop);	
	  niters_sqnorm -= 1; /* total number of iterations = 2^(number of loops) - 1 */
	  
#ifdef TIMING
	  HPM_Stop("sqnorm");
#endif		
	  sqnorm_end = get_time();
	  sqnorm_time = print_time(sqnorm_end - sqnorm_start);
	  lprintf("BENCH", 0, "spinor_field_sqnorm: test completed in %.2lf seconds\n", sqnorm_time);
	  lprintf("MAIN", 0, "spinor_field_sqnorm: total incrementor = %.3le\n", incrementor);
	  incrementor = 0.;
#ifdef WITH_MPI
	  lprintf("BENCH", 0, "spinor_field_sqnorm: Average FLOP/s per process: %.3le\n", bench_var.sqnorm_nflops * (double) niters_sqnorm * (double) nfields / ((double) WORLD_SIZE * sqnorm_time));
#endif
	  lprintf("BENCH", 0, "spinor_field_sqnorm: Average total FLOP/s: %.3le\n", bench_var.sqnorm_nflops * (double) niters_sqnorm * (double) nfields / sqnorm_time);
	  //		lprintf("TEMP",0,"spinor_field_sqnorm: total iterations = %d\n", niters_sqnorm);
	
	  lflush();
	} else {
	  lprintf("BENCH", 0, "spinor_field_sqnorm: disabled.\n");
	  fflush(stdout);
	}
	

	if(bench_var.test_set & MAD) {
	  /* a += b * z */
	  niters_mad = 1;
	  complex z;
	  z.re = 0.214;
	  z.im = 0.413;
	  time_var mad_start, mad_end;
	  mad_start = get_time();
	  stop = 0;
#ifdef WITH_MPI
	  MPI_Barrier(MPI_COMM_WORLD);
#endif

#ifdef TIMING
	  HPM_Start("mad");
#endif	
	  do
	    {
	      //	  printf("entering do, niters = %d\n", niters_mad);
	      for(i = 0; i < niters_mad; i++) {
		for(k = 0; k < nfields; k += 2)
		  {
		    spinor_field_mulc_add_assign_f(test_fields[k],z,test_fields[k+1]);
#ifdef WITH_MPI
		    MPI_Barrier(MPI_COMM_WORLD);
#endif
		  }
	      }
	      niters_mad *= 2;
	      mad_end = get_time();
#ifdef WITH_MPI
	      if(PID == 0)
		{
		  if(print_time(mad_end - mad_start) > MIN_TIME)
		    {
		      stop = 1;
		    } 
		}
	      MPI_Bcast(&stop, 1, MPI_INT, 0, MPI_COMM_WORLD);
#else
	      if(print_time(mad_end - mad_start) > MIN_TIME) stop = 1;
#endif
	    } while (!stop);
	  niters_mad -= 1; /* total number of iterations = 2^(number of loops) - 1 */
	  
#ifdef TIMING
	  HPM_Stop("mad");
#endif
	  
	  mad_end = get_time();
	
	  mad_time = print_time(mad_end - mad_start);
	  lprintf("BENCH", 0, "mad: test completed in %.2lf seconds\n", mad_time);
	  incrementor = 0.;
#ifdef WITH_MPI
	  lprintf("BENCH", 0, "mad: Average FLOP/s per process: %.3le\n", bench_var.mad_nflops * (double) niters_mad * (double) nfields / ((double) WORLD_SIZE * mad_time));
#endif
	  lprintf("BENCH", 0, "mad: Average total FLOP/s: %.3le\n", bench_var.mad_nflops * (double) niters_mad * (double) nfields / mad_time);
	  //	lprintf("TEMP",0,"mad: total iterations = %d\n", niters_mad);
	
	  lflush();
	} else {
	  lprintf("BENCH", 0, "mad: Test disabled.\n");
	  fflush(stdout);
	}

	if(bench_var.test_set & DPHI) {
	  time_var dphi_start, dphi_end;
	  /* Dphi */
	  niters_dphi = 1;
	  stop = 0;
#ifdef WITH_MPI
	  MPI_Barrier(MPI_COMM_WORLD);
#endif
	  
	  dphi_start = get_time();
#ifdef TIMING
	  HPM_Start("Dphi");
#endif
	  
	  do
	    {
	      for(i = 0; i < niters_dphi; i++) {
		for(k = 0; k < nfields; k += 2)
		  {
		    Dphi(bench_var.mass, test_fields[k],test_fields[k+1]);
#ifdef WITH_MPI
		    MPI_Barrier(MPI_COMM_WORLD);
#endif
		  }
	      }
	      niters_dphi *= 2;
	      dphi_end = get_time();
#ifdef WITH_MPI
	      if(PID == 0)
		{
		  if(print_time(dphi_end - dphi_start) > MIN_TIME)
		    {
		      stop = 1;
		    }
		}
	      MPI_Bcast(&stop, 1, MPI_INT, 0, MPI_COMM_WORLD);
	      
#else
	      if(print_time(dphi_end - dphi_start) > MIN_TIME) stop = 1;
#endif
	    } while (!stop);
	  niters_dphi -= 1; /* total number of iterations = 2^(number of loops) - 1 */
#ifdef TIMING
	  HPM_Stop("Dphi");
#endif
	  
	  dphi_end = get_time();
	  
	  dphi_time = print_time(dphi_end - dphi_start);
	  lprintf("BENCH", 0, "Dphi: test completed in %.2lf seconds\n", dphi_time);
	  
#ifdef WITH_MPI
	  lprintf("BENCH", 0, "Dphi: Average FLOP/s per process: %.3le\n", bench_var.dphi_nflops * (double) niters_dphi * (double) nfields / ((double) WORLD_SIZE * dphi_time));
#endif
	  
	  lprintf("BENCH", 0, "Dphi: Average total FLOP/s: %.3le\n", bench_var.dphi_nflops * (double) niters_dphi * (double) nfields / dphi_time);
	  lprintf("TEMP",0,"Dphi: total iterations = %d\n", niters_dphi);
	} else {
	  lprintf("BENCH", 0, "Dphi: Test disabled.\n");
	  fflush(stdout);
	}
	char unit_name[11];
    strcpy(unit_name, "");
    switch (bench_var.reference_unit) {
		case bg_core:
			strcpy(unit_name, "core");
			break;
		case bg_node:
			strcpy(unit_name, "node");
			break;
		case bg_card:
			strcpy(unit_name, "node card");
			break;
		case bg_rack:
			strcpy(unit_name, "rack");
			break;
		case unknown:
			strcpy(unit_name, "equivalent");
			break;
    }

    if(bench_var.test_set & DPHI) {
      if(bench_var.reference_unit != unknown) lprintf("BENCH",0,"Dphi performance is %1.2lf times that of a Blue Gene/P %s.\n", bench_var.dphi_nflops * (double) niters_dphi * (double) nfields / (dphi_time * bench_var.bg_dphi_flops), unit_name);
      
      
      lflush();
    }
    if(fail) {
      lprintf("BENCH",0,"Tests FAILED with status %d\n",fail); }
    else {
      lprintf("BENCH",0,"All tests completed successfully.\n"); }
    
    lflush();

    double total_flopses =  (bench_var.sqnorm_nflops * niters_sqnorm + bench_var.mad_nflops * niters_mad + bench_var.dphi_nflops * niters_dphi) * (double) nfields / (sqnorm_time + mad_time + dphi_time);
    lprintf("BENCH", 0, "Total average FLOP/s: %.3le\n", total_flopses);
    lprintf("BENCH", 0, "Total average FLOP/s per process: %.3le\n", total_flopses / (double) WORLD_SIZE);
    lflush();

    if(bench_var.reference_unit != unknown) lprintf("BENCH",0,"Performance is %1.2lf times that of a Blue Gene/P %s.\n", total_flopses/bench_var.bg_flops, unit_name);
    lflush();
    finalize_process();
	
    free_gfield(u_gauge);
#ifndef REPR_FUNDAMENTAL
    free_gfield_f(u_gauge_f);
#endif
    
    return 0;
}
