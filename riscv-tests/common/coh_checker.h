//**************************************************************************
// Coherency checker
//--------------------------------------------------------------------------

#ifndef _COH_CHECKER_H
#define _COH_CHECKER_H

#include <stdbool.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdio.h>
#include "util.h"
#include "torture_util.h"

#ifndef COH_CHECKER_ROWS
  #define COH_CHECKER_ROWS TOTAL_HARTS
#endif
// Each hop takes ~50 cycles on average
#ifndef MAX_HOPS
  #define MAX_HOPS 512
#endif
#ifndef TOTAL_CIRCUITS
  #define TOTAL_CIRCUITS 10
#endif

#ifndef REPORT_INTERVAL_CYCLES
  #define REPORT_INTERVAL_CYCLES (5000 * 1200)
#endif

#include "stdint.h"

#define CACHE_LINE_SIZE 64

#define DATA_WORDS 3 // 8-byte words to fill the remainder of the struct

// 64 byte structure that exactly matches cache line size
typedef struct __CohStruct__
{
  uint64_t RecipientID;
  uint64_t numHops;
  uint64_t maxHops;
  uint64_t origId;
  uint64_t payload;
  uint64_t OtherData[DATA_WORDS];
} CohStruct __attribute__((aligned(64)));

static const uint64_t RCP_ID_DEFAULT = 0x1D00000000000000; // default recipient ID

// default payload for each context
static const uint64_t ROW_DATA[64] = {
  0xDA7A008080800000,  // Row  0 data
  0xDA7A008181810000,  // Row  1 data
  0xDA7A008282820000,  // Row  2 data
  0xDA7A008383830000,  // Row  3 data
  0xDA7A008484840000,  // Row  4 data
  0xDA7A008585850000,  // Row  5 data
  0xDA7A008686860000,  // Row  6 data
  0xDA7A008787870000,  // Row  7 data
  0xDA7A008888880000,  // Row  8 data
  0xDA7A008989890000,  // Row  9 data
  0xDA7A008A8A8A0000,  // Row 10 data
  0xDA7A008B8B8B0000,  // Row 11 data
  0xDA7A008C8C8C0000,  // Row 12 data
  0xDA7A008D8D8D0000,  // Row 13 data
  0xDA7A008E8E8E0000,  // Row 14 data
  0xDA7A008F8F8F0000,  // Row 15 data

  0xDA7A008080800000,  // Row  0 data
  0xDA7A008181810000,  // Row  1 data
  0xDA7A008282820000,  // Row  2 data
  0xDA7A008383830000,  // Row  3 data
  0xDA7A008484840000,  // Row  4 data
  0xDA7A008585850000,  // Row  5 data
  0xDA7A008686860000,  // Row  6 data
  0xDA7A008787870000,  // Row  7 data
  0xDA7A008888880000,  // Row  8 data
  0xDA7A008989890000,  // Row  9 data
  0xDA7A008A8A8A0000,  // Row 10 data
  0xDA7A008B8B8B0000,  // Row 11 data
  0xDA7A008C8C8C0000,  // Row 12 data
  0xDA7A008D8D8D0000,  // Row 13 data
  0xDA7A008E8E8E0000,  // Row 14 data
  0xDA7A008F8F8F0000,  // Row 15 data

  0xDA7A008080800000,  // Row  0 data
  0xDA7A008181810000,  // Row  1 data
  0xDA7A008282820000,  // Row  2 data
  0xDA7A008383830000,  // Row  3 data
  0xDA7A008484840000,  // Row  4 data
  0xDA7A008585850000,  // Row  5 data
  0xDA7A008686860000,  // Row  6 data
  0xDA7A008787870000,  // Row  7 data
  0xDA7A008888880000,  // Row  8 data
  0xDA7A008989890000,  // Row  9 data
  0xDA7A008A8A8A0000,  // Row 10 data
  0xDA7A008B8B8B0000,  // Row 11 data
  0xDA7A008C8C8C0000,  // Row 12 data
  0xDA7A008D8D8D0000,  // Row 13 data
  0xDA7A008E8E8E0000,  // Row 14 data
  0xDA7A008F8F8F0000,  // Row 15 data

  0xDA7A008080800000,  // Row  0 data
  0xDA7A008181810000,  // Row  1 data
  0xDA7A008282820000,  // Row  2 data
  0xDA7A008383830000,  // Row  3 data
  0xDA7A008484840000,  // Row  4 data
  0xDA7A008585850000,  // Row  5 data
  0xDA7A008686860000,  // Row  6 data
  0xDA7A008787870000,  // Row  7 data
  0xDA7A008888880000,  // Row  8 data
  0xDA7A008989890000,  // Row  9 data
  0xDA7A008A8A8A0000,  // Row 10 data
  0xDA7A008B8B8B0000,  // Row 11 data
  0xDA7A008C8C8C0000,  // Row 12 data
  0xDA7A008D8D8D0000,  // Row 13 data
  0xDA7A008E8E8E0000,  // Row 14 data
  0xDA7A008F8F8F0000   // Row 15 data
};

// work area, where all the work happens
static volatile CohStruct CohArr[COH_CHECKER_ROWS] __attribute__((aligned(4096)));
// for storing the "private" random value by each hart.
// only payload is used here; the same struct is used for alignment
static volatile CohStruct private[COH_CHECKER_ROWS] __attribute__((aligned(4096))); // for storing thread "private" data
static volatile bool init_ready[COH_CHECKER_ROWS]; // assumed to initially be false
static volatile bool harts_done[COH_CHECKER_ROWS]; // assumed to initially be false
static volatile uint64_t failed = 0;
static volatile uint64_t circuits_done = 0;
static volatile int stop = 0;

static void coh_checker_iteration(uint64_t lfsr_val, volatile CohStruct *CohArray) {
  uint8_t  errors = 0;
  uint64_t tmp[DATA_WORDS]; for (uint8_t i=0;i<DATA_WORDS;i++) tmp[i] = 0;
  
  for (int row=0; row<COH_CHECKER_ROWS; row++) {
    volatile CohStruct * thisRow = &CohArray[row];

    if((thisRow->RecipientID & 0xff) == threadIdx ) {
      // access rows that contain hart's id in the RecipientID field
      // Perform read-write-write operation on the data array
      for(uint8_t dw=0; dw<DATA_WORDS; dw++) tmp[dw] = thisRow->OtherData[dw];
      // write some data into the OtherData array
      // to help with debugging of .vcd
      for(uint8_t dw=0; dw<DATA_WORDS; dw++) thisRow->OtherData[dw] = 0xFFFFFFFF00000000 | ((uint64_t)row)<<16 | ((uint64_t)threadIdx)<<8 | (uint64_t)dw; 
      for(uint8_t dw=0; dw<DATA_WORDS; dw++) thisRow->OtherData[dw] = tmp[dw]; // Write back previous data
      AMOADD(thisRow->numHops,1);
      AMOADD(thisRow->payload,1);
      if (thisRow->numHops > thisRow->maxHops) {
        if (thisRow->origId == threadIdx) {
          // This hart "owns" this row, so check its data
          //~ debugf("hart %d checking row %2d\n",hartId,row);
          for (uint8_t dw=0;dw<DATA_WORDS;dw++)               
            if(tmp[dw] != (ROW_DATA[row] | row)) errors++;
          if(thisRow->payload != private[threadIdx].payload + thisRow->numHops) errors++;
          
          AMOADD(circuits_done,1);
          if(circuits_done >= TOTAL_CIRCUITS) {
            stop = 1;
            return;
          }

          thisRow->numHops = 0;
          thisRow->maxHops = lfsr_val % MAX_HOPS;
          private[threadIdx].payload = thisRow->payload = lfsr_val;

          fence();
          thisRow->RecipientID = RCP_ID_DEFAULT | ((lfsr_val >> 16) % COH_CHECKER_ROWS); // Select the next hop's hart id

          if(errors) {
            printf("hart %2d found %2d error(s) in row %2d\n",hartId,errors,row);
            failed = 1;
            fence();
            stop = 1;
            return;
          }

        } else {
          // "send" the row back to its owner
          fence();
          thisRow->RecipientID = thisRow->origId;
        }
      } else {
        fence();
        thisRow->RecipientID = RCP_ID_DEFAULT | (lfsr_val % COH_CHECKER_ROWS); // Select the next hop's hart id
      }
      //~ debugf("hart %2d row %2d new ID = %016lx\n",hartId,row,thisRow->RecipientID);
    }
  }
}

//--------------------------------------------------------------------------
// Main

static void coh_checker_main() {
  if(threadIdx >= COH_CHECKER_ROWS) {
    printf("thread %2d should not be running! aborting this thread.\n",threadIdx);
    return;
  }
  
  uint64_t lfsr_val = brand() + 0x82b8b66569ffa1f6;
  
  if(sizeof(CohStruct) != CACHE_LINE_SIZE) {
    if(threadIdx == 0) printf("!!! CohStruct has incorrect size!\n");
    abort();
  }
  
  // Check for natural alignment
  if(((uint64_t)CohArr) % 4096 != 0 || ((uint64_t)private) % 4096 != 0) {
    if(threadIdx == 0) printf("!!! CohStructs are not aligned!\n");
    abort();
  }
  
  //----- Initialzation ----- 
  CohArr[threadIdx].RecipientID = threadIdx;
  CohArr[threadIdx].origId = threadIdx;
  for(uint64_t dw=0; dw<DATA_WORDS; dw++) 
    CohArr[threadIdx].OtherData[dw] = ROW_DATA[threadIdx] | threadIdx;
  CohArr[threadIdx].numHops = 0;
  CohArr[threadIdx].maxHops = lfsr_val % MAX_HOPS;
  private[threadIdx].payload = CohArr[threadIdx].payload = lfsr_val;
  lfsr_val = lfsr2(lfsr_val);
  
  init_ready[threadIdx] = true;
  
  for(int i=0; i<COH_CHECKER_ROWS; i++) {
    while(!init_ready[i]) delay_cycles(20);
  }
  fence();
  
  uint64_t iterations = 0;
  uint64_t next_report_at = read_csr(mcycle) + REPORT_INTERVAL_CYCLES;

  while(!stop) {
    // Make some noise every 5-10 mins to indicate we are still running
    if((iterations & 0xfffff) == 0 && iterations)
      printf("coh_checker: hart %2d, %7d iter, %3d circuits\n",hartId,iterations,circuits_done);
    iterations++;
    coh_checker_iteration(lfsr_val, CohArr);  // let a hart pass through all the rows of CohStruct array and perform the coh checker procedure
    if(threadIdx == 0) {
      uint64_t mcycle = read_csr(mcycle);
      if(mcycle > next_report_at) {
        printf("coh_checker: %ld circuits at cycle %ld\n",
          circuits_done, mcycle
        );
        next_report_at = mcycle + REPORT_INTERVAL_CYCLES;
      }
    }
  
    lfsr_val = lfsr2(lfsr_val);
  }

  fence();
  debugf("hart %2d done\n",threadIdx);
  harts_done[threadIdx] = true;

  if(threadIdx == 0) {
    for (int i=0; i<COH_CHECKER_ROWS; i++)
      while(!harts_done[i]) delay_cycles(20);

    if (failed) {
      printf("coh_checker: FAILED!!!\n");
      abort();
    } else {
      printf("coh_checker: SUCCESS!!!\n");
    }
  }
}

#endif
