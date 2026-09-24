#include "combined.h"

#define USE_CRUDE_QUEUE
#define SPINLOCK_AMOAND

#include "workqueue/workqueue.h"

void test_workqueue_amoand(void) {
	workqueue_test();
}
