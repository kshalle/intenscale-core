// See LICENSE for license details.

// *************************************************************************
// multiply filter bencmark
// -------------------------------------------------------------------------
//
// This benchmark tests the software multiply implemenation. The
// input data (and reference data) should be generated using the
// multiply_gendata.pl perl script and dumped to a file named
// dataset1.h

#include "util.h"

#include "multiply.h"

//--------------------------------------------------------------------------
// Input/Reference Data

#include "dataset1.h"

// Dedfine the start address for the ROM.
#define ROM_ADDR          0x10008 // The ROM address shifted to be not aligned with the cache-line.
#define NUM_CHAR_ROM_TEST 64 // Covers >= two cache-lines.
#define NUM_ROM_TEST_ITR  16

volatile int* rom = (int*)ROM_ADDR;

//--------------------------------------------------------------------------
// Main
int main( int argc, char* argv[] )
{
  int i, k;
  long long int results_data[DATA_SIZE];

  // Testing the access of ROM.
  volatile int ref[NUM_CHAR_ROM_TEST];
  volatile int cmp[NUM_CHAR_ROM_TEST];
  for (i = 0; i < NUM_CHAR_ROM_TEST; i++) {
    ref[i] = rom[i];
  }
  asm volatile ("nop"); // The nop here is to prevent GCC from merging all the loops.

  for (k = 0; k < NUM_ROM_TEST_ITR; k++) {
    for (i = 0; i < NUM_CHAR_ROM_TEST; i++) {
      cmp[i] = -1;
    }
    asm volatile ("nop");
    for (i = 0; i < NUM_CHAR_ROM_TEST; i+=4) {
      int a0 = rom[i+0];
      int a1 = rom[i+1];
      int a2 = rom[i+2];
      int a3 = rom[i+3];

      cmp[i+0] = a0;
      cmp[i+1] = a1;
      cmp[i+2] = a2;
      cmp[i+3] = a3;
    }
    asm volatile ("nop");
    for (i = 0; i < NUM_CHAR_ROM_TEST; i++) {
      if(cmp[i] != ref[i]) {
        return 1;
      }
    }
    asm volatile ("nop");
  }

#if PREALLOCATE
  for (i = 0; i < DATA_SIZE; i++)
  {
    results_data[i] = input_data1[i]* input_data2[i] ;
  }
#endif

  setStats(1);
  for (i = 0; i < DATA_SIZE; i++)
  {
    results_data[i] =  input_data1[i]* input_data2[i];
  }
  setStats(0);


  // Check the results
  return verify( DATA_SIZE, results_data, verify_data );
}
