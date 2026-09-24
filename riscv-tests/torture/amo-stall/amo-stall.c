// This test demonstrates an issue where harts can be caused to stall
// indefinitely from multiple concurrent AMOs hogging certain bits of
// memory access logic.
#define TOTAL_HARTS 16
#define REPORT_INTERVAL_CYCLES 200000
#define WORKQUEUE_RUNS 10
#define WORKQUEUE_ITERATIONS 100
#define USE_CRUDE_QUEUE
//~ #define SPINLOCK_AMOAND
#define SPINLOCK_LR_SC
#define AMO_STALL_TEST

#include "workqueue/workqueue.h"

static uint64_t next_report_at;

void init() {
	workqueue_init(WORKQUEUE_THREADS);
}

int thread_entry(int cid, int nc) {
	init_or_wait(init);
	
	threadIdx = hartId;
	
	if(hartId >= WORKQUEUE_THREADS) {
		printf("Unexpected hart %2d, bailing\n",hartId);
		return 0;
	}
	
	next_report_at = read_csr(mcycle);
	
	for(uint64_t i=0; i<WORKQUEUE_RUNS; i++) {
		workqueue_test();
		if(!hartId) {
			uint64_t mcycle = read_csr(mcycle);
			if(mcycle > next_report_at) {
				printf("run %d complete at cycle %ld\n",i,mcycle);
				next_report_at = mcycle + REPORT_INTERVAL_CYCLES;
			}
		}
	}
	
	if(!hartId) printf("%d runs complete in %ld cycles\n",WORKQUEUE_RUNS,read_csr(mcycle));
	return 0;
}
