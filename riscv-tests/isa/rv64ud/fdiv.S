# See LICENSE for license details.

#*****************************************************************************
# fdiv.S
#-----------------------------------------------------------------------------
#
# Test f{div|sqrt}.d instructions.
#

#include "riscv_test.h"
#include "test_macros.h"

RVTEST_RV64UF
RVTEST_CODE_BEGIN

#if __riscv_xlen == 32
    # Replace the functions with the 32-bit variants defined in test_macros.h
    #undef TEST_FP_OP2_D
    #define TEST_FP_OP2_D TEST_FP_OP2_D32

    #undef TEST_FP_OP1_D
    #define TEST_FP_OP1_D TEST_FP_OP1_D32

    #undef TEST_FP_OP1_D_DWORD_RESULT
    #define TEST_FP_OP1_D_DWORD_RESULT TEST_FP_OP1_D32_DWORD_RESULT
#endif

  #-------------------------------------------------------------
  # Arithmetic tests
  #-------------------------------------------------------------

  TEST_FP_OP2_D( 2,  fdiv.d, 1, 4.884289506284811E-165, -6.104989241139356E-166, -1.2499237060546518E-1);
  TEST_FP_OP2_D( 10, fdiv.d, 1, -137434759168.06253, -68717379584.03125, 0.4999999999999999);
  TEST_FP_OP2_D( 3,  fdiv.d, 1,-0.9991093838555584,      -1234,     1235.1 );
  TEST_FP_OP2_D( 4,  fdiv.d, 0,         3.14159265, 3.14159265,        1.0 );

  TEST_FP_OP1_D( 6,  fsqrt.d, 0,                100,      10000 );
  TEST_FP_OP1_D( 5,  fsqrt.d, 1, 3.5355339042863776E-1, 1.2499999988358476E-1 );
  TEST_FP_OP1_D( 9,  fsqrt.d, 1, 4.1560001335645896E-39, 1.7272337110188885E-77 );

  TEST_FP_OP1_D_DWORD_RESULT(16,  fsqrt.d, 0x10,      0x7FF8000000000000,      -1.0 );

  TEST_FP_OP1_D( 7,  fsqrt.d, 1, 13.076696830622021, 171.0);

  TEST_FP_OP1_D( 8,  fsqrt.d, 1,0.00040099251863345283320230749702, 1.60795e-7);

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
