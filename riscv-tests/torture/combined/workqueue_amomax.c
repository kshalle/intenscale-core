#include "combined.h"

#define USE_CRUDE_QUEUE
#define SPINLOCK_AMOMAX

#include "workqueue/workqueue.h"

void test_workqueue_amomax(void) {
	workqueue_test();
}
