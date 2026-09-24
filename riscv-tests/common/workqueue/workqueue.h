#include <stdint.h>
#include <stdio.h>
#include "util.h"
#include "torture_util.h"

/******************************************************************
Queue-based AMO test

Tests atomics by exerecising a thread-safe queue that uses them

This test is divdeded into two parts - the queue itself, and the test 
that uses it.  Either component can be swapped independently (although 
only one of the latter exists as of this writing).  The interface 
between them is defined below.  The implementation of the interface to 
be used is selected by including the corresponding header file.

The test itself exercises the queue by dividing the set of threads into 
producers and workers, and having each set do items of "work" on an 
array of integers.  Each producer (atomically) adds 1 to a certain item 
in the array, and adds a job to the queue to add 100000 to the same 
item. All of the workers repeatedly selecting an entry from the queue 
and performing the work of adding to the given item, until there is no 
more work to do. At the end of the whole process the array is checked 
to ensure that every element is a multiple of 100001, as it should be 
if all work items were consumed and done properly, as well as checking 
that the correct total number of operations was done.

Note the queue implementation is not required by the test to pass 
enqueued items to consumers in any particular order.  This means it 
could be implemented using something besides an actual queue, e.g. a 
stack or hash map.
******************************************************************/

/////////////////////////////////////////////
// Abstract queue interface

typedef volatile uint64_t* queue_entry;

struct workqueue;

// Initialise a queue
static void queue_init(struct workqueue* queue);
// Destroy a queue (may be a no-op)
static void queue_destroy(struct workqueue* queue);
// Enqueue an item, waiting until space is free if necessary
static void queue_enqueue(struct workqueue* queue, queue_entry* entry);
// Dequeue an item, waiting for one to be enqueued if necessary
static void queue_dequeue(struct workqueue* queue, queue_entry* entry);
// Dequeue an item and return non-0, or immediately return 0 if queue is empty
static int queue_try_dequeue(struct workqueue* queue, queue_entry* entry);
// Return non-0 if queue is empty, 0 otherwise
static int queue_isempty(struct workqueue* queue);

/////////////////////////////////////////////
// Above is implemented via include below

#ifdef LONG_RUN
	#define WORKQUEUE_ITERATIONS 1000
#endif

// Select a queue implementation
#ifdef USE_CRUDE_QUEUE
	#include "queue_simple.h"
#else
	#include "queue_ck.h"
#endif

#ifndef WORKQUEUE_WORK_AREA_SIZE
#define WORKQUEUE_WORK_AREA_SIZE 8
#endif

#ifndef WORKQUEUE_ITERATIONS
#define WORKQUEUE_ITERATIONS DEFAULT_ITERATIONS
#endif

#ifndef WORKQUEUE_THREADS
#define WORKQUEUE_THREADS TOTAL_HARTS
#endif

#ifndef PRODUCERS
#define PRODUCERS (WORKQUEUE_THREADS/2)
#endif

#define CONSUMERS (WORKQUEUE_THREADS-PRODUCERS)

#ifndef WORKQUEUE_MAX_DELAY
#define WORKQUEUE_MAX_DELAY 128
#endif

static volatile uint64_t work_area[WORKQUEUE_WORK_AREA_SIZE];

static uint64_t __thread rnd = 0;

static workqueue queue;

static volatile uint64_t workqueue_producers_done = 0;
static volatile uint64_t workqueue_consumers_done = 0;

extern struct thread_barrier workqueue_barrier;

static void workqueue_init(int threads) {
	thread_barrier_init(&workqueue_barrier,threads);
}

static void workqueue_test() {
	if(WORKQUEUE_THREADS < PRODUCERS+1) {
		printf("No consumers??? aborting.\n");
		exit(1);
	}
	
	thread_barrier_wait(&workqueue_barrier);
	if(!rnd) rnd = brand();
	thread_barrier_wait(&workqueue_barrier);
	
	if(threadIdx == 0) {
		workqueue_producers_done = 0;
		for(int i=0; i<WORKQUEUE_WORK_AREA_SIZE; i++) work_area[i] = 0;
		queue_init(&queue);
	}
	
	thread_barrier_wait(&workqueue_barrier);

	if(threadIdx < PRODUCERS) {
		debugf("workqueue: hart %2d running as producer for %d iterations\n",hartId,WORKQUEUE_ITERATIONS);
		for(int i=0; i<WORKQUEUE_ITERATIONS; i++) {
			int d = rnd % WORKQUEUE_WORK_AREA_SIZE;
			volatile uint64_t* p = work_area + d;
			//~ debugf("> %p %d\n",p,d);
			AMOADD(*p,1);
			queue_enqueue(&queue,&p);
			rnd = lfsr2(rnd);
		}
		AMOADD(workqueue_producers_done,1);

		//~ debugf("hart %2d waiting for queue to empty\n",hartId);
		while(!queue_isempty(&queue));
	} else {
		uint64_t delay_time = 1;
		debugf("workqueue: hart %2d running as consumer\n",hartId);
		while((workqueue_producers_done < PRODUCERS) || !queue_isempty(&queue)) {
			volatile uint64_t* p;
			if(queue_try_dequeue(&queue,&p)) {
				//~ debugf("< %p %d\n",p,*p);
				AMOADD(*p,100000ULL);
				delay_time = 1;
			} else {
				delay_cycles(delay_time);
				delay_time = delay_time*2+1;
			}
		}
	}

	debugf("workqueue: hart %2d done\n",hartId);
	thread_barrier_wait(&workqueue_barrier);

	if(threadIdx) return; // thread 0 checks, others leave
	fence();
	
	uint64_t total = 0;
	for(int i = 0; i < WORKQUEUE_WORK_AREA_SIZE; i++) {
		debugf("%3d %13ld\n",i,work_area[i]);
		if(work_area[i] % 100001ULL) {
			printf("FAIL workqueue check: at %p, value %ld is not a multiple of 100001\n",
				&work_area[i],work_area[i]
			);
			abort();
		}
		total += work_area[i];
	}
	
	debugf("total %lu\n",total);
	if(total != 100001ULL * PRODUCERS * WORKQUEUE_ITERATIONS) abort();
}

#ifndef WORKQUEUE_RUNS
#define WORKQUEUE_RUNS RUNS
#endif

