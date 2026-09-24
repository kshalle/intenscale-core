#include <stdio.h>
#include "util.h"
#include "torture_util.h"
#include "interrupts.h"

#define REPORT_INTERVAL 2000000

static unsigned int COUNT_TO = 4000LL;
static uint64_t next_report_at;

extern uint64_t interrupt_counters[TOTAL_HARTS];

int thread_entry(int cid, int nc) {
	init_or_wait(NULL);
	next_report_at = read_csr(mcycle);
	
	setup_timer_interrupts();
	while(interrupt_count < COUNT_TO) {
		if(hartId == 0) {
			uint64_t mcycle = read_csr(mcycle);
			if(next_report_at < mcycle) {
				printf("%d interrupts at cycle %ld\n",interrupt_counters[0],mcycle);
				next_report_at = mcycle + REPORT_INTERVAL;
			}
		}
	}
	if(hartId == 0) printf("done?\n");

	return 0;
}
