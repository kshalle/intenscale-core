# See LICENSE for license details.

#*****************************************************************************
# fdiv.S
#-----------------------------------------------------------------------------
#
# Test f{div|sqrt}.s instructions.
#

#include "riscv_test.h"
#include "test_macros.h"

RVTEST_RV64UF
RVTEST_CODE_BEGIN

  #-------------------------------------------------------------
  # Arithmetic tests
  #-------------------------------------------------------------
  
  li a4, 1000
  la  a2, SCRATCH_AREA
  la  a3, SCRATCH_AREA
  la  s6, SCRATCH_AREA
  la  a0, test_2_data
  flw fa5, 0(a0)
  flw fs7, 4(a0)
  flw fa4, 8(a0)
  la  a0, test_3_data
  flw fa0, 0(a0)
  flw fa3, 0(a0)

repeat:
fmul.d	fa5,fs7,fa5
fdiv.d	fa5,fa5,fa4
fadd.d	fa4,fa5,fa5
fmul.d	fa3,fa3,fa5
fsd	fa5,-8(s6)
fmul.d	fa0,fa4,fa0
	fsd	fa3,0(a2)
	fsd	fa0,0(a3)
	
	addi a4, a4, -1
	bnez a4, repeat

.pushsection .data
SCRATCH_AREA:
.float 0.0
.float 0.0
.popsection

  
  TEST_FP_OP2_S(2,  fdiv.s, 1, 1.1557273520668288, 3.14159265, 2.71828182 );
  TEST_FP_OP2_S(3,  fdiv.s, 1,-0.9991093838555584,      -1234,     1235.1 );
  TEST_FP_OP2_S(4,  fdiv.s, 0,         3.14159265, 3.14159265,        1.0 );

  TEST_FP_OP1_S(5,  fsqrt.s, 1, 1.7724538498928541, 3.14159265 );
  TEST_FP_OP1_S(6,  fsqrt.s, 0,                100,      10000 );

  TEST_FP_OP1_S_DWORD_RESULT(7,  fsqrt.s, 0x10, 0x7FC00000, -1.0 );

  TEST_FP_OP1_S(8,  fsqrt.s, 1, 13.076696, 171.0);

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA
  
RVTEST_DATA_END
