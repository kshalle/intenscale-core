#ifndef __TORTURE_UTIL_H
#define __TORTURE_UTIL_H

#include <stdlib.h>
#include "util.h"

// See README.md for more info

// Define this for a long run on FPGA
#ifdef LONG_RUN
	#define RUNS 100000000000000ULL
	#define TOTAL_CIRCUITS 100000000000000ULL
#endif

#ifndef DEFAULT_ITERATIONS
	#define DEFAULT_ITERATIONS 100
#endif

// how do we detect this?
#ifndef TOTAL_HARTS
	#define TOTAL_HARTS 64
#endif

#ifndef RUNS
	#define RUNS 1
#endif

// This should generally be log2 of number of harts per core,
// to ensure pairs are on different cores.
#ifndef PAIR_HART_ID_SHIFT
	#define PAIR_HART_ID_SHIFT 4
#endif

#define PAIR_HART_ID_BITVAL (1 << (PAIR_HART_ID_SHIFT))
#define PAIR_HART_ID_MASK (PAIR_HART_ID_BITVAL-1)

#define fence() asm volatile ("fence")

#define WITH_BACKOFF(double_iter,call) \
	do { \
		if(!call) { \
			uint64_t rnd = brand(); \
			uint64_t x = 1; int i = 0; \
			while(1) { \
				delay_cycles(rnd & x); rnd = lfsr2(rnd); \
				if(call) break; \
				if(++i >= double_iter) { i = 0; x = x*2 + 1; } \
			} \
		} \
	} while(0);

// More comprehensive LFSR
// This may not be an "optimal" LFSR but it's random enough for us for now.
static uint64_t lfsr2(uint64_t x) {
	x ^= x >> 7;
	x ^= x << 9;
	x ^= x >> 13;
	return x;
}

static int __thread hartId;          // filled in by init_or_wait()
static uint64_t __thread rand_state; // filled in by init_or_wait()
// used to "map" threads for multiple concurrent tests
extern int __thread threadIdx;
static int total_harts = TOTAL_HARTS;
static volatile int init_done = 0;

static volatile int hart_running_signals[256];

extern uint64_t rndseed;


// Init func to be called by all harts.
// Should generally be the first thing that is done in main().
// Hart 0 calls init_func(), others wait for it to finish.
static void init_or_wait(void(*init_func)()) {
	hartId = read_csr(mhartid);

	// Have all harts indicate they are running.  Hart 0 checks, and
	// aborts if the expected number of harts does not signal after
	// some time.
	// Otherwise, without checking the DTB, if there are less running
	// harts than the binary is configured for, it will likely hang.
	hart_running_signals[hartId] = hartId + 0xeeee;
	fence();
	if(hartId == 0) {
		int still_waiting;
		int wait_start = read_csr(mcycle);
		int mcycle;
		do {
			mcycle = read_csr(mcycle);
			if(mcycle-wait_start > 5e5) {
				printf("%d harts taking too long to long to report in, aborting\n",TOTAL_HARTS);
				abort();
			}
			still_waiting = 0;
			for(int i=0;i<TOTAL_HARTS;i++)
				if(hart_running_signals[i] != i+0xeeee) still_waiting = 1;
		} while(still_waiting);
		debugf("roll call complete at cycle %d (%d cycles)\n",mcycle, mcycle-wait_start);

		// In case the random seed we got in crt.S is 0, set it to
		// a default so we still get random numbers.
		if(!rndseed) rndseed = 0xcae7c1de57c82f72;
	
		if(init_func) init_func();
		init_done = 1;
	}
	fence();
	while(!init_done);
	rand_state = rndseed + hartId * 0xca691bf6768df51b;
}

static uint64_t brand() {
	return rand_state = lfsr2(rand_state);
}

// This must be exactly one cache line long, i.e. 64 bytes
typedef int64_t __attribute__((aligned(64))) cache_line[8];

// FIXME: These should be actual functions, so types are checked
#define AMOADD(dest,op2) \
	asm volatile ("amoadd.d t6, %0, %1;" :: "r" (op2), "A" (dest) : "t6")
#define AMOOR(dest,op2) \
	asm volatile ("amoor.d t6, %0, %1;" :: "r" (op2), "A" (dest) : "t6")
#define AMOAND(dest,op2) \
	asm volatile ("amoand.d t6, %0, %1;" :: "r" (op2), "A" (dest) : "t6")
#define AMOMIN(dest,op2) \
	asm volatile ("amomin.d t6, %0, %1;" :: "r" (op2), "A" (dest) : "t6")
#define AMOMAX(dest,op2) \
	asm volatile ("amomax.d t6, %0, %1;" :: "r" (op2), "A" (dest) : "t6")
#define AMOXOR(dest,op2) \
	asm volatile ("amoxor.d t6, %0, %1;" :: "r" (op2), "A" (dest) : "t6")
#define AMOMINU(dest,op2) \
	asm volatile ("amominu.d t6, %0, %1;" :: "r" (op2), "A" (dest) : "t6")
#define AMOMAXU(dest,op2) \
	asm volatile ("amomaxu.d t6, %0, %1;" :: "r" (op2), "A" (dest) : "t6")
#define AMOSWAP(dest,op2) \
	asm volatile ("amoswap.d %0, %1, %2;" :  "=r" (op2) : "r" (op2), "A" (dest) )
#define AMOLRD(dest,op2) \
	asm volatile ("lr.d %0, %1;" :  "=r" (op2) : "A" (dest) )
#define AMOSCD(dest,op2) \
	asm volatile ("sc.d %0, %1, %2;" :  "=r" (op2) : "r" (op2), "A" (dest) )
#define LD(dest,op2) \
	asm volatile ("ld %0, %1;" :  "=r" (op2) : "A" (dest) )
#define SD(dest,op2) \
	asm volatile ("sd %0, %1;" :  "=r" (op2) :  "A" (dest) )
#define LR(dest,op2) \
	asm volatile ("lr.d %0, %1;" :: "r" (op2), "A" (dest))
#define SC(dest,val,ret) \
	asm volatile ("sc.d %0, %1, %2;" : "=r"(ret) : "r" (val), "A" (dest) : "t6")
#define AND(dest,op2) \
	asm volatile ("and %0, %1, %2;" :  "=r" (temp) : "r" (temp), "r" (src) );

/////////////////////////////////////////////////////////////////////
// for pair-wise tests

#ifndef PAIRED_TEST_ITERATIONS
	#define PAIRED_TEST_ITERATIONS DEFAULT_ITERATIONS
#endif

static uint64_t paired_seed = 0x123456789abcdef;
//~ static volatile cache_line accumulator_table[16];
static uint64_t paired_test_operands[TOTAL_HARTS];
static uint64_t __thread partner_last_signal = 0;
static volatile uint64_t signals[TOTAL_HARTS];

static void sync_with_partner(int hartidx) {
	fence();
	signals[hartidx]++;
	while(signals[hartidx^1] == partner_last_signal);
	partner_last_signal++;
	fence();
}

typedef void(*paired_amo)(volatile uint64_t* dest,uint64_t src);
typedef uint64_t(*paired_crude_op)(uint64_t a,uint64_t b);

// spike runs each core 5000 insns at a time, so a large random delay is
// needed to detect atomiticy violations.
#ifdef SPIKE
	// Spike runs one core for 5000 insns at a time, so
	// a larger delay is needed to cause conflicts
	#define DELAY_RND_MASK 0x3fff
#else
	#define DELAY_RND_MASK 0xf
#endif

// This tests AMOs by running them in paired harts on a single address
// it accepts functions for the AMO itself, a "normal" version
// for checking, and an intial value.
static inline void paired_test(
	int hartidx, volatile uint64_t* addr,
	paired_amo amofunc,paired_crude_op crude_op,
	uint64_t initval
) {
	uint64_t rnd = lfsr2(paired_seed + hartId * 0xa319a34ca6);
	uint64_t operand;
	
	int rightside = hartidx & 1;
	
	for(int i=0; i<PAIRED_TEST_ITERATIONS; i++) {
		// Setup: both sides generate random operands, left side inits dest to 0
		if(!rightside) *addr = initval;
		operand = paired_test_operands[hartidx] = rnd;
		rnd = lfsr2(rnd);
		
		sync_with_partner(hartidx);
		
		// Run
		// Random delay to defeat skew, coarse scheduling etc 
		// may need tuning depending on platform
		delay_cycles(rnd & DELAY_RND_MASK);
		amofunc(addr,operand);

		sync_with_partner(hartidx);
		
		// Left side checks
		if(!rightside) {
			uint64_t expected = crude_op(operand,paired_test_operands[hartidx^1]);

			//~ debugf("...%04lx <op> ...%04lx = ...%04lx\n",
				//~ operand & 0xffff,paired_test_operands[hartidx^1] & 0xffff,*addr & 0xffff
			//~ );
		
			if(*addr != expected) {
				printf("thread %2d idx %2d error, iter %5d expected ...%04lx, got ...%04lx\n",
					hartId,hartidx,i,expected & 0xffff,*addr & 0xffff
				);
				abort();
			}
		}

		sync_with_partner(hartidx);
	}
}

// AMOSWAP gets its own special test because it doesn't work with
// paired_test above
static inline void test_amoswap(int hartidx, volatile uint64_t* dest) {
	uint64_t rnd = lfsr2(paired_seed + hartId * 0xa319a34ca6);
	uint64_t operand;
	int failed = 0;
	
	int rightside = hartidx & 1;
	
	for(int i=0; i<PAIRED_TEST_ITERATIONS;i++) {
		// Setup: both sides generate random operands, left side inits dest to 0
		// Set the 2 LSBs to ensure all 3 values involved are unique
		if(!rightside) *dest = 2;
		operand = paired_test_operands[hartidx] = (rnd & ~3) | (rightside ? 1 : 0);
		rnd = lfsr2(rnd);
		
		sync_with_partner(hartidx);

		// Run
		delay_cycles(rnd & DELAY_RND_MASK);
		AMOSWAP(*dest,operand);
		//~ { uint64_t t = *dest; *dest = operand; operand = t; }
		paired_test_operands[hartidx] = operand;

		sync_with_partner(hartidx);

		// Left side checks
		if(!rightside) {
			// To check that the two AMOSWAP operations worked, we check
			// that the 3 results are all different, and none of them
			// are in their original location.
			uint64_t partner_operand = paired_test_operands[hartidx^1];
			if(
				(partner_operand & 3) == 1 ||
				(operand & 3) == 0 ||
				(*dest & 3) == 2 ||
				*dest == operand ||
				*dest == partner_operand ||
				operand == partner_operand
			) {
				printf("thread %2d error, iter %5d got: ...%04lx ...%04lx ...%04lx\n",
					hartId,i,
					*dest & 0xffff, operand & 0xffff, partner_operand & 0xffff
				);
				abort();
			}
		}
		sync_with_partner(hartidx);
	}
}

static volatile cache_line basic_test_acc[16];
static volatile int paired_test_done[TOTAL_HARTS];

#define pair_address_idx() ( \
		(hartId & ~((PAIR_HART_ID_MASK <<1)|1)) | \
		((hartId & PAIR_HART_ID_MASK) << 1) | \
		((hartId >> PAIR_HART_ID_SHIFT) & 1) \
	)

static void basic_paired_test(
	char* name,paired_amo amofunc,paired_crude_op crude_op, uint64_t initval
) {
	init_or_wait(NULL);
	
	if(!hartId) {
		debugf("%s: running on %d threads for %d iterations\n",
			name, total_harts, PAIRED_TEST_ITERATIONS
		);
	}
	
	if((2<<PAIR_HART_ID_SHIFT) > total_harts) {
		if(hartId == 0) printf("PAIR_HART_ID_SHIFT is invalid for %d total harts\n",total_harts);
		exit(1);
	}

	if(hartId >= total_harts) {
		printf("hart %d should not be here! bailing.\n",hartId);
		return;
	}
	
	int idx = pair_address_idx();
	volatile uint64_t* dest = &basic_test_acc[idx >> 1][((idx >> 4)&1)];
	paired_test(idx, dest, amofunc, crude_op, initval);
	
	debugf("hart %2d done\n",hartId);

	paired_test_done[hartId] = 1;
	if(hartId) return;
	for(int i=0;i<total_harts;i++) while(!paired_test_done[i]);
}

// Generates a permutation of integers 0..n-1 in an array
static void permute(uint32_t seed,int n,int* out) {
	int a[16];
	
	uint16_t x = seed;
	
	for(int i=0;i<n;i++) out[i] = i;
	for(int i=n-1; i; i--) {
		x = lfsr2(x);
		int p = x % (i+1);
		uint64_t t = out[p];
		out[p] = out[i];
		out[i] = t;
	}
}

// Operation functions for paired tests
// bad_xxx version is non-atomic version for testing the tests

static void amo_add_func(volatile uint64_t* dest,uint64_t src) { AMOADD(*dest,src); }
static void bad_amo_add_func(volatile uint64_t* dest,uint64_t src) { *dest += src; }
static uint64_t add_func(uint64_t lhs,uint64_t rhs) { return lhs + rhs; }

static void amo_and_func(volatile uint64_t* dest,uint64_t src) { AMOAND(*dest,src); }
static void bad_amo_and_func(volatile uint64_t* dest,uint64_t src) { *dest &= src; }
static uint64_t and_func(uint64_t lhs,uint64_t rhs) { return lhs & rhs; }

static void amo_or_func(volatile uint64_t* dest,uint64_t src) { AMOOR(*dest,src); }
static void bad_amo_or_func(volatile uint64_t* dest,uint64_t src) { *dest |= src; }
static uint64_t or_func(uint64_t lhs,uint64_t rhs) { return lhs | rhs; }

static void amo_xor_func(volatile uint64_t* dest,uint64_t src) { AMOXOR(*dest,src); }
static void bad_amo_xor_func(volatile uint64_t* dest,uint64_t src) { *dest ^= src; }
static uint64_t xor_func(uint64_t lhs,uint64_t rhs) { return lhs ^ rhs; }

static void amo_max_func(volatile uint64_t* dest,uint64_t src) { AMOMAX(*dest,src); }
static void bad_amo_max_func(volatile uint64_t* dest,uint64_t src) {
	volatile int64_t* sdest = (volatile int64_t*)dest;
	int64_t ssrc = (int64_t)src;
	if(*sdest < ssrc) *sdest = ssrc;
}
static uint64_t max_func(uint64_t lhs,uint64_t rhs) {
	int64_t slhs = (int64_t)lhs;
	int64_t srhs = (int64_t)rhs;
	return slhs > srhs ? slhs : srhs;
}

static void amo_maxu_func(volatile uint64_t* dest,uint64_t src) { AMOMAXU(*dest,src); }
static void bad_amo_maxu_func(volatile uint64_t* dest,uint64_t src) { if(*dest < src) *dest = src; }
static uint64_t maxu_func(uint64_t lhs,uint64_t rhs) { return (lhs > rhs) ? lhs : rhs; }

static void amo_min_func(volatile uint64_t* dest,uint64_t src) { AMOMIN(*dest,src); }
static void bad_amo_min_func(volatile uint64_t* dest,uint64_t src) {
	volatile int64_t* sdest = (volatile int64_t*)dest;
	int64_t ssrc = (int64_t)src;
	if(*sdest > ssrc) *sdest = ssrc;
}
static uint64_t min_func(uint64_t lhs,uint64_t rhs) {
	int64_t slhs = (int64_t)lhs;
	int64_t srhs = (int64_t)rhs;
	return slhs < srhs ? slhs : srhs;
}

static void amo_minu_func(volatile uint64_t* dest,uint64_t src) { AMOMINU(*dest,src); }
static void bad_amo_minu_func(volatile uint64_t* dest,uint64_t src) { if(*dest > src) *dest = src; }
static uint64_t minu_func(uint64_t lhs,uint64_t rhs) { return (lhs < rhs) ? lhs : rhs; }

// replicates AMOADD
static void lrsc_func(volatile uint64_t* dest,uint64_t src) {
	uint64_t fail,x;
	do {
		LR(*dest,x);
		x += src;
		SC(*dest,x,fail);
	} while(fail);
}

// replicates AMOAND 
static void amo_lrsc_func_and(volatile uint64_t* dest,uint64_t src) 
{ // An implementation of AMOAND function using AMOLRD and AMOSCD 
	uint64_t temp;
	do{
		AMOLRD(*dest,temp);//load a value from address pointed by dest and store it in temp  
		AND(src,temp); // Perform temp = temp & src; // This is a non-atomic and operation 
		AMOSCD(*dest,temp); // store temp to *dest, and return a zero-value in case of success and non-zero in case of error 
	}while(temp);

}

struct thread_barrier {
	volatile uint64_t seq;
	volatile uint64_t c;
	volatile uint64_t signal;
	volatile int nthreads;
};

static void thread_barrier_init(struct thread_barrier* b,int nthreads) {
	b->seq = b->c = b->signal = 0;
	b->nthreads = nthreads;
}

static void thread_barrier_wait(struct thread_barrier* b) {
	uint64_t t = b->seq;
	fence();
	if (__sync_fetch_and_add(&b->c, 1) == b->nthreads-1) {
		b->c = 0;
		b->seq = t+1;
		fence();
		b->signal = t+1;
	} else {
		while(b->signal == t);
	}
}

#endif // __TORTURE_UTIL_H
