#include "combined.h"

#define USE_CRUDE_QUEUE
#define SPINLOCK_AMOMINU

#include "workqueue/workqueue.h"

void test_workqueue_amominu(void) {
	workqueue_test();
}
