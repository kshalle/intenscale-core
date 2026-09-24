#include "combined.h"

#define USE_CRUDE_QUEUE
#define SPINLOCK_AMOMIN

#include "workqueue/workqueue.h"

void test_workqueue_amomin(void) {
	workqueue_test();
}
