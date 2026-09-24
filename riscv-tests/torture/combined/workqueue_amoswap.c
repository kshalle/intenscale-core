#include "combined.h"

#define USE_CRUDE_QUEUE
#define SPINLOCK_AMOSWAP

#include "workqueue/workqueue.h"

void test_workqueue_amoswap(void) {
	workqueue_test();
}
