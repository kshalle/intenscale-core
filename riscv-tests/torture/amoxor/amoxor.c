#include <stdio.h>
#include "util.h"
#include "torture_util.h"

int thread_entry(int cid, int nc) {
	basic_paired_test("amoxor", amo_xor_func, xor_func, 0);
}
