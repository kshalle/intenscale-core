# See LICENSE for license details.

#*****************************************************************************
# fmin.S
#-----------------------------------------------------------------------------
#
# Test f{min|max}.d instructinos.
#

#include "riscv_test.h"
#include "test_macros.h"

RVTEST_RV64UF
RVTEST_CODE_BEGIN


.option rvc
nop
nop
nop
la   a4, tdatXX;
la   a2, tdatXX;
addi a5, a4, 112

# This loop is inspired from Gensim.
GENSIM_LOOP:
flw     ft5, 0(a4)
flw     ft4, 0(a2)
flw     ft3, 4(a4)
flw     ft2, 4(a2)
flw     ft1, 8(a4)
flw     ft0, 8(a2)
flw     fa1, 12(a4)
flw     fa2, 12(a2)
c.addi  a4, 16
fmadd.s fa4, ft5, ft4, fa4
fmadd.s fa3, ft3, ft2, fa3
fmadd.s fa5, ft1, ft0, fa5
fmadd.s fa0, fa1, fa2, fa0
c.addi  a2, 16
bne     a5, a4, GENSIM_LOOP

# Clear the flags.
fsflags	a2,zero

#if __riscv_xlen == 32
    # Replace the function with the 32-bit variant defined in test_macros.h
    #undef TEST_FP_OP2_D
    #define TEST_FP_OP2_D TEST_FP_OP2_D32
#endif

  #-------------------------------------------------------------
  # Arithmetic tests
  #-------------------------------------------------------------

  TEST_FP_OP2_D( 2,  fmin.d, 0,        1.0,        2.5,        1.0 );
  TEST_FP_OP2_D( 3,  fmin.d, 0,    -1235.1,    -1235.1,        1.1 );
  TEST_FP_OP2_D( 4,  fmin.d, 0,    -1235.1,        1.1,    -1235.1 );
  TEST_FP_OP2_D( 5,  fmin.d, 0,    -1235.1,        NaN,    -1235.1 );
  TEST_FP_OP2_D( 6,  fmin.d, 0, 0.00000001, 3.14159265, 0.00000001 );
  TEST_FP_OP2_D( 7,  fmin.d, 0,       -2.0,       -1.0,       -2.0 );

  TEST_FP_OP2_D(12,  fmax.d, 0,        2.5,        2.5,        1.0 );
  TEST_FP_OP2_D(13,  fmax.d, 0,        1.1,    -1235.1,        1.1 );
  TEST_FP_OP2_D(14,  fmax.d, 0,        1.1,        1.1,    -1235.1 );
  TEST_FP_OP2_D(15,  fmax.d, 0,    -1235.1,        NaN,    -1235.1 );
  TEST_FP_OP2_D(16,  fmax.d, 0, 3.14159265, 3.14159265, 0.00000001 );
  TEST_FP_OP2_D(17,  fmax.d, 0,       -1.0,       -1.0,       -2.0 );

  # FMIN(sNaN, x) = x
  TEST_FP_OP2_D(20,  fmax.d, 0x10, 1.0, sNaN, 1.0);
  # FMIN(qNaN, qNaN) = canonical NaN
  TEST_FP_OP2_D(21,  fmax.d, 0x00, qNaN, NaN, NaN);

  # -0.0 < +0.0
  TEST_FP_OP2_D(30,  fmin.d, 0,       -0.0,       -0.0,        0.0 );
  TEST_FP_OP2_D(31,  fmin.d, 0,       -0.0,        0.0,       -0.0 );
  TEST_FP_OP2_D(32,  fmax.d, 0,        0.0,       -0.0,        0.0 );
  TEST_FP_OP2_D(33,  fmax.d, 0,        0.0,        0.0,       -0.0 );

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

tdatXX:
  .dword 0x00ff00ff00ff00ff
  .dword 0x00ff00ff00ff00ff
  .dword 0x00ff00ff00ff00ff
  .dword 0x00ff00ff00ff00ff
  .dword 0xff00ff00ff00ff00
  .dword 0x0ff00ff00ff00ff0
  .dword 0x0ff00ff00ff00ff0
  .dword 0x0ff00ff00ff00ff0
  .dword 0x0ff00ff00ff00ff0
  .dword 0x0ff00ff00ff00ff0
  .dword 0x0ff00ff00ff00ff0
  .dword 0x0ff00ff00ff00ff0
  .dword 0x0ff00ff00ff00ff0
  .dword 0x0ff00ff00ff00ff0
  .dword 0x0ff00ff00ff00ff0
  .dword 0xf00ff00ff00ff00f

RVTEST_DATA_END
