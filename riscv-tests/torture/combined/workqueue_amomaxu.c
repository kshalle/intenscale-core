#include "combined.h"

#define USE_CRUDE_QUEUE
#define SPINLOCK_AMOMAXU

#include "workqueue/workqueue.h"

void test_workqueue_amomaxu(void) {
	workqueue_test();
}
