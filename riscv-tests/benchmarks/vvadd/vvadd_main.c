// See LICENSE for license details.

//**************************************************************************
// Vector-vector add benchmark
//--------------------------------------------------------------------------
//
// This benchmark uses adds to vectors and writes the results to a
// third vector. The input data (and reference data) should be
// generated using the vvadd_gendata.pl perl script and dumped
// to a file named dataset1.h.
#include <stdio.h>
#include "util.h"

//--------------------------------------------------------------------------
// Input/Reference Data

#include "dataset1.h"

//--------------------------------------------------------------------------
// vvadd function

void vvadd( int n, int a[], int b[], int c[] )
{
  int i;
  for ( i = 0; i < n; i++ )
    c[i] = a[i] + b[i];
}

//--------------------------------------------------------------------------
// Main
__attribute__((aligned(512))) volatile long rowCntr1  = 0;
__attribute__((aligned(512))) volatile long rowCntr2 = 0;
__attribute__((aligned(512))) volatile long counter = 0;

#define NUM_PAGES 16
#define PAGE_SIZE 0x2000
#define NUM_REP1 100
#define NUM_REP2 100
__attribute__((aligned(PAGE_SIZE))) volatile long lock = 0;
__attribute__((aligned(PAGE_SIZE))) volatile char arrXX[NUM_PAGES*PAGE_SIZE];

__attribute__((aligned(512))) int lfsrStatus[512];
unsigned int lfsr_rand(int numBits) {
  unsigned int hartId, lfsr, i, lsb;
  hartId = read_csr(mhartid);
  lfsr = lfsrStatus[hartId];
  for(i = 0; i < numBits; i++) {
    lsb = lfsr & 0x1;
    lfsr >>= 1;
    if (lsb) lfsr ^= 0xb400;
  }
  lfsrStatus[hartId] = lfsr;
  return(lfsr & ((1 << numBits) - 1));
}

void arch_write_lock()
{
	int tmp;
	int hartId = read_csr(mhartid);

	while(1) {
	int success = 0;
	arrXX[(hartId & 0xf)*PAGE_SIZE] = 0;
	__asm__ __volatile__(
		"1:	lr.w	%1, %0\n"
		"	bnez	%1, 3f\n"
		"	li	%1, -1\n"
		"	sc.w.aq	%1, %1, %0\n"
		"   bnez    %1, 1b\n"
		"   li %2, 1\n"
		"3:\n"
		: "+A" (lock), "=&r" (tmp), "+&r" (success)
		:: "memory");
	if(success) {
		arrXX[(hartId & 0x7)*PAGE_SIZE+1] = 0;
		break;
	}
	arrXX[(hartId & 0x7)*PAGE_SIZE+2] = 0;
	int delay = 0x1f + lfsr_rand(4);
	delay_cycles(delay);
	}
}

void arch_write_lock2()
{

	int tmp = 1, busy;
	int hartId = read_csr(mhartid);

	while(1) {
		tmp = 1;
		if(lock == 0) {
			arrXX[(hartId & 0xf)*PAGE_SIZE] = 0;
			__asm__ __volatile__ (
				"amoswap.w.aq %0, %2, %1"
				: "=r" (busy), "+A" (lock)
				: "r" (tmp)
				: "memory");
			if(!busy) break;
		}
    	int delay = 0x8 + lfsr_rand(2);
    	delay_cycles(delay);
	}
}

int thread_entry(int cid, int nc)
{
  int results_data[DATA_SIZE];
  int hartId, delay;
  int one = 1;
  int tmp = 1;
  int busy = 0;
  int i, k;
  int currVal = 0;
  hartId = read_csr(mhartid);
  lfsrStatus[hartId] = (i + 0x8000 + (hartId << 4)) & 0xffff;
  
  if(hartId == 0) {
    delay = 0x1fff;
    delay_cycles(delay);
    rowCntr1 = 1;
  }
  else {
    while(rowCntr1 ==0) {
      delay = 0x3f + lfsr_rand(4);
      delay_cycles(delay);
    }
    __asm__ __volatile__ (
      "amoadd.w %0, %2, %1"
      : "=r" (tmp), "+A" (rowCntr1)
      : "r" (one)
      : "memory");
  }

  arrXX[hartId*PAGE_SIZE + 256] = 0;
  for(i=0; i < NUM_REP1; i++) {
    // Try to obtain the lock.
    arch_write_lock();

    // Update the number of lock acqustions.
    counter++;
    arrXX[hartId*PAGE_SIZE + 256]++;

    // Release the lock.
    __asm__ __volatile__ (
     "amoswap.w.rl x0, x0, %0"
     : "=A" (lock)
     :: "memory");
  }
  if(arrXX[hartId*PAGE_SIZE + 256] != (NUM_REP1 & 0xff))  return 1;

  arrXX[hartId*PAGE_SIZE + 256] = 0;
  for(i=0; i < NUM_REP1; i++) {
    // Try to obtain the lock.
    arch_write_lock2();

    // Update the number of lock acqustions.
    counter++;
    arrXX[hartId*PAGE_SIZE + 256]++;

    // Release the lock.
    __asm__ __volatile__ (
     "amoswap.w.rl x0, x0, %0"
     : "=A" (lock)
     :: "memory");
  }
  if(arrXX[hartId*PAGE_SIZE + 256] != (NUM_REP1 & 0xff))  return 1;
  
  // Increment rowCntr2 to indicate finish.
  __asm__ __volatile__ (
    "amoadd.w %0, %2, %1"
    : "=r" (tmp), "+A" (rowCntr2)
    : "r" (one)
    : "memory");

  delay = 0x7fff;
  delay_cycles(delay);

  while(rowCntr1 != rowCntr2) {
    delay = 0xff;
    __asm__ __volatile__ ("csrw   0xf, %0" : "+r" (delay)); 
  }
  if(hartId == 0) {
    printf("Number of ROWs: %d\n", rowCntr1);
    printf("Number of lock acquisitions: %d\n\n", counter);
  }
  if(counter != (2 * NUM_REP1 * rowCntr1)) return 1;

  if(hartId < 8) {
    for(k=0; k < NUM_PAGES;k++) {
      arrXX[k*PAGE_SIZE + hartId] = currVal;
      arrXX[k*PAGE_SIZE + hartId + 8] = currVal;
      arrXX[k*PAGE_SIZE + hartId + 16] = currVal;
      arrXX[k*PAGE_SIZE + hartId + 24] = currVal;
      arrXX[k*PAGE_SIZE + 64 + hartId] = currVal;
      arrXX[k*PAGE_SIZE + 64 + hartId + 8] = currVal;
      arrXX[k*PAGE_SIZE + 64 + hartId + 16] = currVal;
      arrXX[k*PAGE_SIZE + 64 + hartId + 24] = currVal;
    }
    for(i=0; i < NUM_REP2; i++) {
      currVal += hartId + 1;
      for(k=0; k < NUM_PAGES;k++) {
        int a = arrXX[k*PAGE_SIZE + hartId];
        int b = arrXX[k*PAGE_SIZE + hartId+8];
        int c = arrXX[k*PAGE_SIZE + hartId+16];
        int d = arrXX[k*PAGE_SIZE + hartId+24];
        arrXX[k*PAGE_SIZE + hartId] = currVal&0xff;
        arrXX[k*PAGE_SIZE + hartId+8] = (currVal*2)&0xff;
        arrXX[k*PAGE_SIZE + hartId+16] = (currVal*3)&0xff;
        arrXX[k*PAGE_SIZE + hartId+24] = (currVal*4)&0xff;

        if(a != ((currVal-hartId-1)&0xff)) return 1;
        if(b != (((currVal-hartId-1)*2)&0xff)) return 1;
        if(c != (((currVal-hartId-1)*3)&0xff)) return 1;
        if(d != (((currVal-hartId-1)*4)&0xff)) return 1;


        a = arrXX[k*PAGE_SIZE + 64 + hartId];
        b = arrXX[k*PAGE_SIZE + 64 + hartId+8];
        arrXX[k*PAGE_SIZE + hartId + 64] = currVal&0xff;
        arrXX[k*PAGE_SIZE + hartId+8 + 64] = (currVal*2)&0xff;

        if(a != ((currVal-hartId-1)&0xff)) return 1;
        if(b != (((currVal-hartId-1)*2)&0xff)) return 1;
      }
    }
  }

#if PREALLOCATE
  // If needed we preallocate everything in the caches
  vvadd( DATA_SIZE, input1_data, input2_data, results_data );
#endif

  // Do the vvadd
  setStats(1);
  vvadd( DATA_SIZE, input1_data, input2_data, results_data );
  setStats(0);

  // Check the results
  return verify( DATA_SIZE, results_data, verify_data );
}
