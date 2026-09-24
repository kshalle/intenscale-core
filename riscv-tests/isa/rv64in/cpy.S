# See LICENSE for license details.

#*****************************************************************************
# cpy.S
#-----------------------------------------------------------------------------
#
# This test implements a simple copy loop.
#

#include "riscv_test.h"
#include "test_macros.h"

#define ARR_SIZE 4096*2

RVTEST_RV64UF
RVTEST_CODE_BEGIN

  csrr a0, 0xb # Load mhartid.

  # Initialize the page first.
  la s0, src
  la s1, src  # The destination address is the same as the source, this is just for testing.
  li s2, ARR_SIZE
  add s2, s0, s2
  li s4, 0
memInit:
  sd s4, 0(s1)
  sd s4, 8(s1)
  sd s4, 16(s1)
  sd s4, 24(s1)
  addi s0, s0, 32
  addi s1, s1, 32
  blt s0, s2, memInit

  # The copy loop.
  la s0, src
  la s1, src  # The destination address is the same as the source, this is just for testing.
  li s2, ARR_SIZE
  add s2, s0, s2
rep:
  ld s4, 0(s0)
  ld s5, 8(s0)
  ld s6, 16(s0)
  ld s7, 24(s0)
  sd s4, 0(s1)
  sd s5, 8(s1)
  sd s6, 16(s1)
  sd s7, 24(s1)
  addi s0, s0, 32
  addi s1, s1, 32
  blt s0, s2, rep

  # Just increase the test number
  li  TESTNUM, 3;

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

.align 3
src:
.space ARR_SIZE

RVTEST_DATA_END
