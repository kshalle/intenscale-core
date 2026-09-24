# See LICENSE for license details.

#*****************************************************************************
# ma_addr.S
#-----------------------------------------------------------------------------
#
# Test misaligned ld/st trap.
#

#include "riscv_test.h"
#include "test_macros.h"

RVTEST_RV64M
RVTEST_CODE_BEGIN

  .align 2
  .option norvc

  la s0, data

  # Chose the corresponding data for the running ROW.
  csrr a0, 0xb # Load mhartid.
  li t1, 272
  mul t1, t1, a0
  add s0, s0, t1

  # indicate it's a load test
  li s1, CAUSE_MISALIGNED_LOAD

#define SEXT(x, n) ((-((x) >> ((n)-1)) << (n)) | ((x) & ((1 << (n))-1)))

/* Check that a misaligned load either writes the correct value, or
   takes an exception and performs no writeback.  */
#define MISALIGNED_LOAD_TEST(testnum, insn, base, offset, res) \
  li TESTNUM, testnum; \
  la t2, 1f; \
  addi t1, base, offset; \
  insn t1, offset(base); \
  li t2, res; \
  bne t1, t2, fail; \
1:

  MISALIGNED_LOAD_TEST(2,  lh,  s0, 1, SEXT(0xbbcc, 16))
  MISALIGNED_LOAD_TEST(3,  lhu, s0, 1, 0xbbcc)
  MISALIGNED_LOAD_TEST(4,  lh,  s0, 3, SEXT(0x99aa, 16))
  MISALIGNED_LOAD_TEST(5,  lhu, s0, 3, 0x99aa)
  MISALIGNED_LOAD_TEST(6,  lh,  s0, 5, SEXT(0x7788, 16))
  MISALIGNED_LOAD_TEST(7,  lhu, s0, 5, 0x7788)
  MISALIGNED_LOAD_TEST(8,  lh,  s0, 7, SEXT(0x5566, 16))
  MISALIGNED_LOAD_TEST(9,  lhu, s0, 7, 0x5566)

  MISALIGNED_LOAD_TEST(20,  lw,  s0, 1, SEXT(0x99aabbcc, 32))
  MISALIGNED_LOAD_TEST(21,  lw,  s0, 2, SEXT(0x8899aabb, 32))
  MISALIGNED_LOAD_TEST(22,  lw,  s0, 3, SEXT(0x778899aa, 32))
  MISALIGNED_LOAD_TEST(23,  lw,  s0, 5, SEXT(0x55667788, 32))
  MISALIGNED_LOAD_TEST(24,  lw,  s0, 6, SEXT(0x44556677, 32))
  MISALIGNED_LOAD_TEST(25,  lw,  s0, 7, SEXT(0x33445566, 32))

#if __riscv_xlen == 64
  MISALIGNED_LOAD_TEST(30,  lwu, s0, 1, 0x99aabbcc)
  MISALIGNED_LOAD_TEST(31,  lwu, s0, 2, 0x8899aabb)
  MISALIGNED_LOAD_TEST(32,  lwu, s0, 3, 0x778899aa)
  MISALIGNED_LOAD_TEST(33,  lwu, s0, 5, 0x55667788)
  MISALIGNED_LOAD_TEST(34,  lwu, s0, 6, 0x44556677)
  MISALIGNED_LOAD_TEST(35,  lwu, s0, 7, 0x33445566)

  MISALIGNED_LOAD_TEST(40, ld, s0, 1, 0x5566778899aabbcc)
  MISALIGNED_LOAD_TEST(41, ld, s0, 2, 0x445566778899aabb)
  MISALIGNED_LOAD_TEST(42, ld, s0, 3, 0x33445566778899aa)
  MISALIGNED_LOAD_TEST(43, ld, s0, 4, 0x2233445566778899)
  MISALIGNED_LOAD_TEST(44, ld, s0, 5, 0x1122334455667788)
  MISALIGNED_LOAD_TEST(45, ld, s0, 6, 0xee11223344556677)
  MISALIGNED_LOAD_TEST(46, ld, s0, 7, 0xffee112233445566)
#endif

  # indicate it's a store test
  li s1, CAUSE_MISALIGNED_STORE

/* Check that a misaligned store has some effect and takes no exception,
   or takes no effect and generates an exception.  This is not very
   thorough.  */
#define MISALIGNED_STORE_TEST(testnum, insn, base, offset, size) \
  li TESTNUM, testnum; \
  la t2, 1f; \
  addi t1, base, offset; \
  insn x0, offset(base); \
  lb t1, (offset - 1)(base); \
  beqz t1, fail; \
  lb t1, (offset + size)(base); \
  beqz t1, fail; \
  lb t1, (offset + 0)(base); \
  bnez t1, fail; \
  lb t1, (offset + size - 1)(base); \
  bnez t1, fail; \
1:

  MISALIGNED_STORE_TEST(22,  sh,  s0, 1, 2)
  MISALIGNED_STORE_TEST(23,  sw,  s0, 5, 4)
  MISALIGNED_STORE_TEST(24,  sw,  s0, 10, 4)
  MISALIGNED_STORE_TEST(25,  sw,  s0, 15, 4)

#if __riscv_xlen == 64
  MISALIGNED_STORE_TEST(26, sd, s0, 25, 8)
  MISALIGNED_STORE_TEST(27, sd, s0, 34, 8)
  MISALIGNED_STORE_TEST(28, sd, s0, 43, 8)
  MISALIGNED_STORE_TEST(29, sd, s0, 52, 8)
  MISALIGNED_STORE_TEST(30, sd, s0, 61, 8)
  MISALIGNED_STORE_TEST(31, sd, s0, 70, 8)
  MISALIGNED_STORE_TEST(32, sd, s0, 79, 8)
#endif

  TEST_PASSFAIL

  .align 3
  .global mtvec_handler
mtvec_handler:
  csrr t0, mcause
  bne t0, s1, fail

  csrr t0, mtval
  bne t0, t1, fail

  lb t0, (t0)
  beqz t0, fail

  csrw mepc, t2
  mret

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

data:
  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80
  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80

  .align 3
.word 0xaabbccdd
.word 0x66778899
.word 0x22334455
.word 0xeeffee11
.fill 0xff, 1, 80


  TEST_DATA

RVTEST_DATA_END
