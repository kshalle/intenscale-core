// An implementation of the ring buffer that is effectively a wrapper
// for concurrencykit:
// https://github.com/concurrencykit/ck

/********************************************************************
This is a simple wrapper to implement a queue as required by 
workqueue.h, using concurrencykit's ring buffer.

The upstream concurrencykit has platform-specific definitions for 
various low-level operations such as atomics and barriers, but has no 
definitions for RISC-V.  Instead it falls back to GCC instrinsics.  For 
the ring buffer, this means we use LR/SC combinations to implement 
compare-and-swap.
********************************************************************/

// This header expects a type queue_entry to be defined by the including
// code - see workqueue.h.

#ifndef __QUEUE_CK_H__
#define __QUEUE_CK_H__

#define CK_MD_RMO  // Get ck to add fence insns in appropriate places
#include <ck_ring.h>
#include "util.h"

#define QUEUE_SIZE 32 // Must be a power of 2

typedef struct workqueue {
	ck_ring_t ring;
	ck_ring_buffer_t buffer[QUEUE_SIZE];
} workqueue;

static void queue_init(struct workqueue* queue) {
	ck_ring_init(&queue->ring,QUEUE_SIZE);
}

static void queue_destroy(struct workqueue* queue) {
}

static void queue_enqueue(struct workqueue* queue, queue_entry* entry) {
	while(!ck_ring_enqueue_mpmc(&queue->ring, queue->buffer, *entry)) {
		delay_cycles(100);
	}
}

static void queue_dequeue(struct workqueue* queue, queue_entry* entry) {
	ck_ring_dequeue_mpmc(&queue->ring, queue->buffer, entry);
}

static int queue_try_dequeue(struct workqueue* queue, queue_entry* entry) {
	return ck_ring_trydequeue_mpmc(&queue->ring, queue->buffer, entry);
}

static int queue_isempty(struct workqueue* queue) {
	return ck_ring_size(&queue->ring) == 0;
}

#endif // __QUEUE_CK_H__
