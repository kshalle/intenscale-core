#ifndef __INTERRUPTS_H_
#define __INTERRUPTS_H_

#include <stdio.h>
#include "util.h"
#include "torture_util.h"

// Interrupts for extra randomness
// setup_timer_interrupts() sets up the local timer on the running hart,
// and sets up the ISR to keep the timer interrupt firing, while
// occasionally doing reads/writes of spands of memory to flush some
// cache.

// To add this to a program, simply include this file and then have
// all harts call setup_timer_interrupts() after init_or_wait().

// This is in timer units, which are each 100 CPU cycles according to my test
// (80 cycles on spike)
#ifndef ISR_MAX_DELTA
	#define ISR_MAX_DELTA 0x100
#endif

#ifndef ISR_MAX_DELAY
	#define ISR_MAX_DELAY 128
#endif

#ifndef ARRAY_SCAN_RND_MAX
	#define ARRAY_SCAN_RND_MAX 128
#endif

// Setting this sets the size of timer_space.
#ifndef TIMER_ISR_ARRAY_BITS
	#define TIMER_ISR_ARRAY_BITS 14
#endif

#define MTIME           (*(volatile uint64_t *)(0x02000000 + 0xbff8))
#define MTIMECMP        ((volatile uint64_t *)(0x02000000 + 0x4000))

static volatile uint64_t interrupt_count = 0;

void enable_timer_interrupts()
{
    set_csr(mie, MIP_MTIP);
    set_csr(mstatus, MSTATUS_MIE);
}

static uint64_t __thread interrupt_seed;

static void next_interrupt() {
	uint64_t new_mtime = MTIME + (interrupt_seed % ISR_MAX_DELTA);
	interrupt_seed = lfsr2(interrupt_seed);
	//~ debugf("next interrupt at 0x%lx\n",new_mtime);

	MTIMECMP[hartId] = new_mtime;
}

#define TIMER_INT_ARRAY_SIZE (1<<TIMER_ISR_ARRAY_BITS)

//~ static uint64_t timer_space[TOTAL_HARTS][TIMER_INT_ARRAY_SIZE];

// Making this a normal static array would cause crt to dutifully
// write 0s to the area at start, which takes too long in verilator.
// So for now we have it in the middle of nowhere in the 2G memory space.
static uint64_t* timer_space = (uint64_t*)0xa0000000;
static uint64_t timer_space2[TOTAL_HARTS];
uint64_t interrupt_counters[TOTAL_HARTS];

// Note: wait_host_free, and thus printf, is not reentrant, so don't
// be surprised if calling printf from an ISR causes hangs.
static uintptr_t timer_handler(uint64_t cause, uintptr_t epc, uint64_t regs[32]) {
	uint64_t mtime = MTIME;
	uint64_t mcycle = read_csr(mcycle);
	uint64_t minstret = read_csr(minstret);
	
	int c = ++interrupt_counters[hartId];
	//~ if(c % 2 == 0 && c) printf("Hart %2d: %9d interrupts\n",hartId,c);
	
	//~ debugf("int %2d hart %2d, mtime = %06lx, mcycle=%06lx\n",
		//~ interrupt_count,hartId,mtime,mcycle
	//~ );
	
	delay_cycles(interrupt_seed % ISR_MAX_DELAY);
	
	// occasionally do an array scan to steal some cache.
	uint64_t* p = timer_space + (1<<TIMER_ISR_ARRAY_BITS) * hartId;
	int to_scan = 1 << ((interrupt_seed >> 24) % (TIMER_ISR_ARRAY_BITS+1));
	switch((interrupt_seed >> 16) % (ARRAY_SCAN_RND_MAX*2)) {
		case 0: {
			//~ debugf("hart %2d doing a scan of  %9d elements\n",hartId,to_scan);
			uint64_t acc = 0;
			for(int i=0;i<to_scan;i++) acc += p[i];
			timer_space2[hartId] = acc;
			break;
		}
		case 1: {
			//~ debugf("hart %2d doing a write of %9d elements\n",hartId,to_scan);
			for(int i=0;i<to_scan;i++) p[i] = interrupt_seed + i;
			break;
		}
	}
	
	AMOADD(interrupt_count,1);
	next_interrupt();
	return epc;
}

static void setup_timer_interrupts() {
	debugf("hart %2d setting up timer\n",hartId);
	interrupt_counters[hartId] = 0;
	interrupt_seed = brand() + 0x92a248a41c71a850;
	
	if(hartId == 0) {
		set_interrupt_handler(7,timer_handler);
	}
	//~ debugf("MTIME = 0x%lx\n",MTIME);
	enable_timer_interrupts();
	next_interrupt();
}

static uint64_t seed = 0x3e8273b60cd1ffd3;

#endif
