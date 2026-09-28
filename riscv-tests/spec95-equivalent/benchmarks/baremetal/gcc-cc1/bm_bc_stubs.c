/*
 * SPDX-FileCopyrightText: 2026 Intensivate, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 */

/* gcc 2.5.8's "bytecode" back end (bc-emit.c, bc-optab.c -- an experimental,
 * long-abandoned interpreted-output mode, unrelated to the SPARC assembly
 * this port actually generates) uses pre-ANSI K&R varargs that our modern
 * stdarg.h can't parse, and mkstage.py's rewrite pass (see gcc-cc1.sh) does
 * not touch it. Rather than hand-patch vendored 1994 sources, this stubs the
 * ~30 bc_* symbols that expr.c/varasm.c/emit-rtl.c reference unconditionally
 * (though only when output_bytecode is set, which the workload here never
 * does -- it asks for plain assembly). Every function aborts if actually
 * called, so a real invocation fails loudly instead of silently doing
 * nothing.
 *
 * K&R-declared functions (empty-paren prototypes in bc-emit.h/bc-optab.h)
 * are not signature-checked at the call site, so a plain no-arg definition
 * links against every call regardless of the arguments the caller passes.
 * The optab_* symbols are data (arrays of small enum-only structs declared
 * in bc-optab.h); rather than pull in gcc's own internal header chain
 * (tree.h/rtl.h and friends) just to name their exact types, each is
 * declared as a plain uninitialized COMMON symbol of generous size -- their
 * contents are never read, since nothing here ever sets output_bytecode.
 */

extern void abort(void);

#define BC_STUB(name) void name(void) { abort(); }

BC_STUB(bc_align_bytecode)
BC_STUB(bc_align_const)
BC_STUB(bc_begin_function)
BC_STUB(bc_data)
BC_STUB(bc_define_pointer)
BC_STUB(bc_emit)
BC_STUB(bc_emit_bytecode)
BC_STUB(bc_emit_bytecode_const)
BC_STUB(bc_emit_bytecode_labeldef)
BC_STUB(bc_emit_bytecode_labelref)
BC_STUB(bc_emit_code_labelref)
BC_STUB(bc_emit_const)
BC_STUB(bc_emit_const_labeldef)
BC_STUB(bc_emit_const_labelref)
BC_STUB(bc_emit_const_skip)
BC_STUB(bc_emit_data_labeldef)
BC_STUB(bc_emit_instruction)
BC_STUB(bc_emit_labelref)
BC_STUB(bc_emit_trampoline)
BC_STUB(bc_end_function)
BC_STUB(bc_expand_binary_operation)
BC_STUB(bc_expand_conversion)
BC_STUB(bc_expand_increment)
BC_STUB(bc_expand_truth_conversion)
BC_STUB(bc_expand_unary_operation)
BC_STUB(bc_gen_rtx)
BC_STUB(bc_get_bytecode_label)
BC_STUB(bc_globalize_label)
BC_STUB(bc_text)
BC_STUB(bc_write_file)
BC_STUB(bc_xstrdup)

__asm__(
  ".comm optab_plus_expr, 64, 8\n"
  ".comm optab_minus_expr, 64, 8\n"
  ".comm optab_mult_expr, 64, 8\n"
  ".comm optab_trunc_div_expr, 64, 8\n"
  ".comm optab_trunc_mod_expr, 64, 8\n"
  ".comm optab_rdiv_expr, 64, 8\n"
  ".comm optab_bit_and_expr, 64, 8\n"
  ".comm optab_bit_ior_expr, 64, 8\n"
  ".comm optab_bit_xor_expr, 64, 8\n"
  ".comm optab_lshift_expr, 64, 8\n"
  ".comm optab_rshift_expr, 64, 8\n"
  ".comm optab_truth_and_expr, 64, 8\n"
  ".comm optab_truth_or_expr, 64, 8\n"
  ".comm optab_lt_expr, 64, 8\n"
  ".comm optab_le_expr, 64, 8\n"
  ".comm optab_ge_expr, 64, 8\n"
  ".comm optab_gt_expr, 64, 8\n"
  ".comm optab_eq_expr, 64, 8\n"
  ".comm optab_ne_expr, 64, 8\n"
  ".comm optab_negate_expr, 64, 8\n"
  ".comm optab_bit_not_expr, 64, 8\n"
  ".comm optab_truth_not_expr, 64, 8\n"
  ".comm optab_predecrement_expr, 64, 8\n"
  ".comm optab_preincrement_expr, 64, 8\n"
  ".comm optab_postdecrement_expr, 64, 8\n"
  ".comm optab_postincrement_expr, 64, 8\n"
);
