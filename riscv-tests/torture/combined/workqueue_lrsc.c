#include "combined.h"

#define USE_CRUDE_QUEUE
#define SPINLOCK_LRSC

#include "workqueue/workqueue.h"

void test_workqueue_lrsc(void) {
	workqueue_test();
}
