// Minimal thread-safe ring buffer, using a single spinlock

// This header expects a type queue_entry to be defined by the including
// code - see workqueue.h.

// The AMO*** instruction used to implement the spinlock can be 
// selected by defining a compiler macro - see below.

#ifndef __QUEUE_SIMPLE_H__
#define __QUEUE_SIMPLE_H__

#include <stdio.h>
#include <string.h>
#include "util.h"
#include "torture_util.h"

// These function templates cover most of the AMOxxx instructions.
#define DEFINE_AMO_INSN(NAME,WORD,DWORD) \
const static WORD NAME##_w(volatile WORD* d, WORD op) { \
	WORD rd; \
	asm volatile (#NAME ".w %0, %1, %2;" :  "=r" (rd) : "r" (op), "A" (*d) ); \
	return rd; \
} \
const static DWORD NAME##_d(volatile DWORD* d, DWORD op) { \
	DWORD rd; \
	asm volatile (#NAME ".d %0, %1, %2;" :  "=r" (rd) : "r" (op), "A" (*d) ); \
	return rd; \
} \
const static DWORD NAME(volatile DWORD* d, DWORD op) { return NAME##_d(d,op); }

#define DEFINE_AMO_INSN_UNSIGNED(NAME) DEFINE_AMO_INSN(NAME,uint32_t,uint64_t)
#define DEFINE_AMO_INSN_SIGNED(NAME) DEFINE_AMO_INSN(NAME,int32_t,int64_t)

DEFINE_AMO_INSN_UNSIGNED(amoswap)
DEFINE_AMO_INSN_SIGNED(amomin)
DEFINE_AMO_INSN_UNSIGNED(amominu)
DEFINE_AMO_INSN_SIGNED(amomax)
DEFINE_AMO_INSN_UNSIGNED(amomaxu)
DEFINE_AMO_INSN_UNSIGNED(amoadd)
DEFINE_AMO_INSN_UNSIGNED(amoand)
DEFINE_AMO_INSN_UNSIGNED(amoor)
DEFINE_AMO_INSN_UNSIGNED(amoxor)
DEFINE_AMO_INSN_UNSIGNED(sc)

const static uint64_t lr_w(volatile uint64_t* d) {
	uint32_t rd;
	asm volatile ("lr.w %0, %1;" : "=r" (rd) : "A" (*d) );
	return rd;
}

const static uint64_t lr_d(volatile uint64_t* d) {
	uint64_t rd;
	asm volatile ("lr.d %0, %1;" : "=r" (rd) : "A" (*d) );
	return rd;
}
const static uint64_t lr(volatile uint64_t* d) { return lr_d(d); }

// functionally equivalent to amoswap.d, using lr.d/sc.d
static uint64_t lrsc_swap(volatile uint64_t* dest, uint64_t src) {
	// Quick first try
	uint64_t x = lr_d(dest);
	if(!sc_d(dest,src)) return x;

	// tuned to last at most ~ 1e6 cycles or so
	for(int fail_delay = 4;fail_delay <= 8192; fail_delay  <<= 1) {
		int fail_delay = 1;
		for(int failures = 0;failures < 64;failures++) {
			x = lr_d(dest);
			if(!sc_d(dest,src)) return x;
			delay_cycles((brand() & (fail_delay-1))+fail_delay);
		}
	}
	// took too long, give up
	abort();
}

// Non-atomic swap for testing
static uint64_t swap(volatile uint64_t* dest, uint64_t src) {
	uint64_t t = *dest;
	*dest = src;
	return t;
}

#define QUEUE_SIZE 8

static uint64_t __thread wait_seed;

typedef uint64_t spinlock;

static void spinlock_init(volatile spinlock* lock);
static int spinlock_trywait(volatile spinlock* lock);
static void spinlock_release(volatile spinlock* lock);

#define DEFINE_BASIC_SPINLOCK_WITH_AMO(AMO,INITVAL,LOCKVAL) \
static void spinlock_init(volatile spinlock* lock) { *lock = INITVAL; } \
static int spinlock_trywait(volatile spinlock* lock) { return AMO(lock,LOCKVAL) == INITVAL; } \
static void spinlock_release(volatile spinlock* lock) { *lock = INITVAL; } \

static void spinlock_wait(volatile spinlock* lock) {
	// used by amo-stall.c
	#ifdef AMO_STALL_TEST
		while(!spinlock_trywait(lock));
	#else
		WITH_BACKOFF(100,spinlock_trywait(lock));
	#endif
}

// Select AMO*** instruction to be used for spinlock
#ifdef SPINLOCK_AMOMAX
#define SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(amomax,0,1);
#endif

#ifdef SPINLOCK_AMOMAXU
#define SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(amomaxu,0,1);
#endif

#ifdef SPINLOCK_AMOMIN
#define SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(amomin,1,0);
#endif

#ifdef SPINLOCK_AMOMINU
#define SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(amominu,1,0);
#endif

#ifdef SPINLOCK_AMOAND
#define SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(amoand,1,0);
#endif

#ifdef SPINLOCK_AMOOR
#define SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(amoor,0,1);
#endif

#ifdef SPINLOCK_AMOADD
#define SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(amoadd,2,3);
#endif

#ifdef SPINLOCK_AMOSWAP
#define SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(amoswap,0,678);
#endif

// Broken non-atomic version - use this to test that code detects it
#ifdef SPINLOCK_SWAP
#define SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(swap,2,3);
#endif

// Default
#ifndef SPINLOCKS_DEFINED
DEFINE_BASIC_SPINLOCK_WITH_AMO(lrsc_swap,0,1);
#endif

typedef struct workqueue {
	queue_entry* volatile head;
	queue_entry* volatile tail;
	spinlock lock;
	queue_entry data[QUEUE_SIZE];
} workqueue;

void queue_init(struct workqueue* queue) {
	spinlock_init(&queue->lock);
	queue->head = queue->tail = queue->data;
}

static void queue_destroy(struct workqueue* queue) {
	spinlock_wait(&queue->lock);
}

static int queue_try_enqueue(struct workqueue* queue, queue_entry* entry) {
	spinlock_wait(&queue->lock);
	fence();

	if ((queue->head-queue->tail+QUEUE_SIZE+1) % QUEUE_SIZE == 0) {
		spinlock_release(&queue->lock);
		return 0;
	}

	queue_entry* head = (queue_entry*)queue->head;
	memcpy(head,entry,sizeof(queue_entry));
	if(++head >= queue->data + QUEUE_SIZE) head = queue->data;
	queue->head = head;
	fence();
	spinlock_release(&queue->lock);
	return 1;
}

static void queue_enqueue(struct workqueue* queue, queue_entry* entry) {
	WITH_BACKOFF(10,queue_try_enqueue(queue,entry));
}

static int queue_try_dequeue(struct workqueue* queue, queue_entry* entry) {
	spinlock_wait(&queue->lock);
	fence();
	int notempty = queue->head != queue->tail;
	if (queue->head == queue->tail) {
		spinlock_release(&queue->lock);
		return 0;
	}
	
	queue_entry* tail = (queue_entry*)queue->tail;
	memcpy(entry,tail,sizeof(queue_entry));
	if(++tail >= queue->data + QUEUE_SIZE) tail = queue->data;
	queue->tail = tail;

	fence();
	spinlock_release(&queue->lock);
	return 1;
}

static void queue_dequeue(struct workqueue* queue, queue_entry* entry) {
	WITH_BACKOFF(10,queue_try_dequeue(queue,entry));
}

static int queue_isempty(struct workqueue* queue) {
	return queue->head == queue->tail;
}

#endif
