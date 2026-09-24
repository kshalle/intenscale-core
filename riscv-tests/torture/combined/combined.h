#ifndef __COMBINED_H__
#define __COMBINED_H__

// combined test:
#define TOTAL_HARTS 32    // Total harts, must match device
// #define LONG_RUN    // Run indefinitely (sets WORKQUEUE_RUNS and TOTAL_CIRCUITS)
#define WORKQUEUE_THREADS (TOTAL_HARTS/2)
#define COH_CHECKER_ROWS (TOTAL_HARTS/2)

// workqueue:
// #define WORKQUEUE_MAX_DELAY 128     // 
#define WORKQUEUE_ITERATIONS 100
#define WORKQUEUE_RUNS 100000000
// #define PRODUCERS (WORKQUEUE_THREADS/2)

// These are to be defined in individual test source files
// #define USE_CRUDE_QUEUE
// SPINLOCK_* (define only one of these)
// #define SPINLOCK_AMOADD
// #define SPINLOCK_AMOAND
// #define SPINLOCK_AMOMAX
// #define SPINLOCK_AMOMAXU
// #define SPINLOCK_AMOMIN
// #define SPINLOCK_AMOMINU
// #define SPINLOCK_AMOOR
// #define SPINLOCK_SWAP
// #define SPINLOCK_LR_SC

// coh_checker:
// #define MAX_HOPS 512
#define TOTAL_CIRCUITS 20000000

// interrupts:
// #define ISR_MAX_DELTA 0x100
// #define ISR_MAX_DELAY 120
#define TIMER_ISR_ARRAY_BITS 10
// #define ARRAY_SCAN_RND_MAX 128

// roughly every 10-20 minutes, on a verilator build of a 32-hart config
#define REPORT_INTERVAL_CYCLES (500*1200) 

#endif // __COMBINED_H__
