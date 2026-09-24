// See README_AMO.md for more info

#include <stdio.h>
#include "util.h"
#include "torture_util.h"

#define fence() asm volatile ("fence")

static volatile uint64_t counter = 0;
static volatile int done[TOTAL_HARTS];

int thread_entry(int cid, int nc) {
	int hartId = read_csr(mhartid);
	
	if(hartId >= TOTAL_HARTS) {
		printf("hart %d should not be here! bailing.\n",hartId);
		return 0;
	}
	
	for(int i=0; i<DEFAULT_ITERATIONS; i++) AMOADD(counter,1);
	//~ for(int i=0; i<DEFAULT_ITERATIONS; i++) counter += 1;

	done[hartId] = 1;
	//~ printf("core %d done\n",hartId);
	if(hartId) return 0;
	
	// Core 0 to check result
	// wait for other cores to finish
	for(int i=0;i<TOTAL_HARTS;i++) while(!done[i]);
	fence();
	
	if(hartId) return 0;

	printf("!!!!!! final count %d, expected %d\n",counter,TOTAL_HARTS * DEFAULT_ITERATIONS);
	int success = counter == TOTAL_HARTS * DEFAULT_ITERATIONS;
	return success ? 0 : 1;
}
