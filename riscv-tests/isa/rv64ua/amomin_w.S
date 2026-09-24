# See LICENSE for license details.

#*****************************************************************************
# amomin_d.S
#-----------------------------------------------------------------------------
#
# Test amomin.w instruction.
#

#include "riscv_test.h"
#include "test_macros.h"

RVTEST_RV64U
RVTEST_CODE_BEGIN

  csrr a0, 0xb # Load mhartid.
  TEST_CASE(2, a4, 0xffffffff80000000, \
    la a3, amo_operand; \
    li a1, 8; \
    mul a1, a1, a0; \
    add a3, a3, a1; \
    li a0, 0xffffffff80000000; \
    li a1, 0xfffffffffffff800; \
    sw a0, 0(a3); \
    amomin.w	a4, a1, 0(a3); \
  )

  TEST_CASE(3, a5, 0xffffffff80000000, lw a5, 0(a3))

  TEST_CASE(4, a4, 0, \
    li a1, 0xffffffffffffffff; \
    sw x0, 0(a3); \
    amomin.w	a4, a1, 0(a3); \
  )

  TEST_CASE(5, a5, 0xffffffffffffffff, lw a5, 0(a3))

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END

  .bss
  .align 3
amo_operand:
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
  .dword 0
