// Long-running combined coh_checker and queue test

#include "combined.h"
#include "interrupts.h"
#include "coh_checker.h"
#include "coh_checker.h"
#include "workqueue/workqueue.h"

static uint64_t workqueue_next_report_cycle;
static struct thread_barrier sideb_barrier;

extern uint64_t interrupt_counters[TOTAL_HARTS];

static void init() {
	workqueue_init(WORKQUEUE_THREADS);
	thread_barrier_init(&sideb_barrier,WORKQUEUE_THREADS);
}

typedef void(*testfunc)(void);

extern void test_workqueue_amoadd(void);
extern void test_workqueue_amomax(void);
extern void test_workqueue_amomaxu(void);
extern void test_workqueue_amomin(void);
extern void test_workqueue_amominu(void);
extern void test_workqueue_amoswap(void);
extern void test_workqueue_amoand(void);
extern void test_workqueue_amoor(void);
extern void test_workqueue_lrsc(void);

#define N_SIDEB_TESTS 3
testfunc sideb_tests[] = {
	test_workqueue_amoadd,
	test_workqueue_amoand,
	test_workqueue_amomax,
	test_workqueue_amomaxu,
	test_workqueue_amomin,
	test_workqueue_amominu,
	test_workqueue_amoor,
	test_workqueue_amoswap,
	test_workqueue_lrsc
};
static int testno = 0;
testfunc current_test;

int thread_entry(int cid, int nc) {
	init_or_wait(init);
	
	setup_timer_interrupts();
	
	threadIdx = hartId / 2;
	
	if(hartId%2) {
		// coherence checker
		// periodic report in coh_checker.h
		coh_checker_main();
		printf("hart %2d: coh_checker test done at cycle %d\n",hartId,read_csr(mcycle));
	} else {
		workqueue_next_report_cycle = read_csr(mcycle) + REPORT_INTERVAL_CYCLES;
		for(uint64_t i=0; i<WORKQUEUE_RUNS; i++) {
			if(threadIdx == 0) {
				current_test = sideb_tests[testno];
				testno = (++testno) % N_SIDEB_TESTS;
			
				uint64_t mcycle = read_csr(mcycle);
				if(mcycle >= workqueue_next_report_cycle) {
					printf("workqueue: %d runs at cycle %ld\n",i,mcycle);
					workqueue_next_report_cycle = mcycle + REPORT_INTERVAL_CYCLES;
					printf("hart 0 got %ld interrupts\n",
						interrupt_counters[0]
					);
				}
			}
			
			thread_barrier_wait(&sideb_barrier);
			
			current_test();
		}
		printf("hart %2d: workqueue test done at cycle %d\n",hartId,read_csr(mcycle));
	}
	return 0;
}
