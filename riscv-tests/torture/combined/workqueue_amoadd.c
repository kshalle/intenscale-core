#include "combined.h"

#define USE_CRUDE_QUEUE
#define SPINLOCK_AMOADD

#include "workqueue/workqueue.h"

void test_workqueue_amoadd(void) {
	workqueue_test();
}
