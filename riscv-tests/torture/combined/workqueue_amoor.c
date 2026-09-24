#include "combined.h"

#define USE_CRUDE_QUEUE
#define SPINLOCK_AMOOR

#include "workqueue/workqueue.h"

void test_workqueue_amoor(void) {
	workqueue_test();
}
