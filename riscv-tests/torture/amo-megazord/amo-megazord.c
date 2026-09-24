// A torture test for AMOs that runs all of them randomly on all harts

#include <stdio.h>
#include "util.h"
#ifndef PAIRED_TEST_ITERATIONS
	#define PAIRED_TEST_ITERATIONS 1000
#endif
#include "torture_util.h"

#define NTESTS 10

typedef void(*testfunc) (int myidx,volatile uint64_t* dest);

void test_amoadd(int myidx,volatile uint64_t* dest) {
	paired_test(myidx, dest, amo_add_func, add_func, 0);
}

void test_amoxor(int myidx,volatile uint64_t* dest) {
	paired_test(myidx, dest, amo_xor_func, xor_func, 0);
}

void test_amoand(int myidx,volatile uint64_t* dest) {
	paired_test(myidx, dest, amo_and_func, and_func, ~0);
}

void test_amoor(int myidx,volatile uint64_t* dest) {
	paired_test(myidx, dest, amo_or_func, or_func, 0);
}

void test_amomax(int myidx,volatile uint64_t* dest) {
	paired_test(myidx, dest, amo_max_func, max_func, ~(((uint64_t)~0) >> 1));
}

void test_amomaxu(int myidx,volatile uint64_t* dest) {
	paired_test(myidx, dest, amo_maxu_func, maxu_func, 0);
}

void test_amomin(int myidx,volatile uint64_t* dest) {
	paired_test(myidx, dest, amo_min_func, min_func, (((uint64_t)~0) >> 1));
}

void test_amominu(int myidx,volatile uint64_t* dest) {
	paired_test(myidx, dest, amo_minu_func, minu_func, ~0);
}

void test_lrsc(int myidx,volatile uint64_t* dest) {
	paired_test(myidx, dest, amo_lrsc_func_and, and_func, ~0);
}

// FIXME add amoswap & lr/sc 
testfunc tests[NTESTS] = {
	test_amoswap,
	test_amoadd,
	test_amoxor,
	test_amoand,
	test_amoor,
	test_amomax,
	test_amomaxu,
	test_amomin,
	test_amominu,
	test_lrsc
};

/////////////////////////////////////////////////////////////////////////

#define TOTAL_LINES (TOTAL_HARTS/2) // min is harts/2
#define TOTAL_PAIRS (TOTAL_HARTS/2) // min is harts/2

static volatile cache_line test_mem[TOTAL_LINES];

static volatile int hart_pairs[TOTAL_HARTS];
//~ static volatile testfunc test_to_run[TOTAL_PAIRS];
static volatile int test_to_run[TOTAL_PAIRS];

static volatile int run_signals[TOTAL_HARTS];
static uint64_t __thread signal_counter = 0;

static volatile int failed = 0;

int thread_entry(int cid, int nc) {
	uint64_t seed = brand() + 0x3026c517f9edba86;
	
	init_or_wait(NULL);
	
	if(hartId > TOTAL_HARTS) {
		printf("hart %2d shouldn't be here! aborting.\n",hartId);
		return 0;
	}

	for(int i=0; i<RUNS; i++) {
		uint64_t rnd = lfsr2(seed);
		seed += 0x555555555555;
		
		if(hartId == 0) {
			// hart 0 sets up
			// - creates permutation of hart pairs
			permute(rnd,TOTAL_HARTS,(int*)hart_pairs);
			rnd = lfsr2(rnd);
			// - selects random amo op to use for each pair
			for(int i=0;i<TOTAL_HARTS>>1;i++) {
				test_to_run[i] = rnd % NTESTS;
				rnd = lfsr2(rnd);
			}

			// - updates seed used by other threads
			paired_seed = rnd;
			rnd = lfsr2(rnd);

			run_signals[hartId]++;
			debugf("hart  0 done setting up, seed %016lx\n",seed);
		} else {
			//~ debugf("hart %2d waiting\n",hartId);
			// wait for hart 0 to signal done
			while(run_signals[0] == signal_counter);
			signal_counter++;
			fence();
		}
		
		// all harts call a function according to the above
		int testno = test_to_run[hart_pairs[hartId]>>1];
		testfunc mytest = tests[testno];
		volatile uint64_t* dest = &(test_mem[hart_pairs[hartId]>>1][0]);
		debugf("hart %2d starts, idx %2d, addr %p, test %2d\n",
			hartId,hart_pairs[hartId],dest,testno
		);
		mytest(hart_pairs[hartId], dest);

		debugf("hart %2d done\n",hartId);

		if(hartId) {
			// other harts all signal that they are done
			run_signals[hartId]++;
		} else {
			debugf("hart 0 waiting\n");

			// hart 0 waits and checks results
			for(int i=1; i<TOTAL_HARTS; i++)
				while(run_signals[i] == signal_counter);
			signal_counter++;
			fence();
		}
		
		// hart 0 signals the rest again, to check failed status
		if(hartId == 0) {
			run_signals[hartId]++;
		} else {
			while(run_signals[0] == signal_counter);
			signal_counter++;
			fence();
		}
		if(failed) return 1;
	}
	debugf("done!");
}
