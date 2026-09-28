# gcc-cc1 -- the C compiler workload, bare metal

Upstream: `../../compiler-gcc/gcc-2.5.8` (GPL-2.0-only).  **Not edited** --
but this is the one port that cannot compile the vendored tree as it stands;
see "The two rewrites" below.

Only `cc1` is built -- the C compiler proper, no driver, no preprocessor, no
assembler -- reading preprocessed `.i` files and emitting SPARC assembly.
Target sparc, host the bare-metal riscv64 image, build machine x86-64 Linux.

## Three machines, which is what makes this port different

gcc distinguishes the machine it *runs on* (host), the machine it *emits code
for* (target), and the machine that *builds* it.  All three differ here:

| | | supplied by |
|---|---|---|
| target | sparc | upstream `config/sparc/{sparc.h,sparc.c,sparc.md}` |
| host | bare-metal riscv64 + newlib | `hostcfg/xm-bm.h` (written for this port) |
| build | x86-64 Linux | `hostcfg/xm-bm.h`, `__linux__` branch |

gcc 2.5.8 has no host description for a 64-bit RISC-V running newlib -- it
predates RISC-V by two decades -- so `hostcfg/xm-bm.h` is it, modelled on
`config/alpha/xm-alpha.h`, the only 64-bit little-endian host the release
shipped with.  The same header serves as `hconfig.h` for the build machine:
both are 64-bit little-endian with 32-bit int, so only the C library
facilities differ.

## What this port adds

| File | Why |
|---|---|
| `hostcfg/xm-bm.h`, `config.h`, `hconfig.h` | The host descriptions above; `config.h` and `hconfig.h` are the one-line wrappers configure would have made as symlinks. |
| `gen/tm.h`, `gen/tconfig.h` | Likewise for the target: configure links these to `config/sparc/sparc.h` and `config/sparc/xm-sparc.h`. |
| `hostcfg/gvarargs.h` | Replaces gcc's own, which implements the pre-ANSI varargs interface on top of per-CPU `va-*.h` files.  What the sources need now is `<stdarg.h>`. |
| `mkgen.sh` | Builds and runs the fourteen code generators (host stage). |
| `mkstage.py` | The rewrites described below. |
| `workload/mkinput.py` | Generates the preprocessed C that cc1 compiles. |

## The host stage: cc1 is partly generated code

Eleven of cc1's translation units are written by programs that read
`sparc.md` and emit C -- `genrecog`, `genemit`, `genattrtab`, `genoutput` and
friends -- and three more (`bc-opcode.h`, `bc-opname.h`, `bc-arity.h`) come
from `bytecode.def` by way of `bi-opcode` and friends.  Those programs run on
the *build* machine, so a cross build has a host stage before it can start.
`mkgen.sh` does it; `bi-parser.c` and `c-parse.c` both ship pre-generated, so
bison is not needed anywhere.

Output, all into `gen/`:

    bc-opcode.h  bc-opname.h  bc-arity.h
    insn-config.h  insn-flags.h  insn-codes.h  insn-attr.h
    insn-emit.c  insn-recog.c  insn-opinit.c  insn-extract.c
    insn-peep.c  insn-attrtab.c  insn-output.c

## The two rewrites, and why they are unavoidable

`mkstage.py` produces modified copies under `gen/src/`, leaving the vendored
tree alone.  Both changes are mechanical, both are reported when the script
runs, and both are forced by the language rather than by the target.

**1. Pre-ANSI variadic definitions (12 functions in 8 files).**  1994 sources
write variadic functions as

    gen_rtx (va_alist)
         va_dcl
    { va_list p; enum rtx_code code; va_start (p); code = va_arg (p, enum rtx_code); ... }

That depends on the compiler implementing the `va_alist` magic parameter,
which no compiler has for twenty years.  It also cannot simply be modernised
in place: C89 requires a named parameter before `...`, and while C23 permits
`...` alone, C23 deleted the old-style parameter declarations the rest of
these files are written in.  There is no dialect that accepts the file as it
stands.  Each definition becomes

    gen_rtx (enum rtx_code bm_va_arg1, ...)
    { ... va_start (p, bm_va_arg1); code = bm_va_arg1; ... }

with the type read out of the function's own first `va_arg` call, so the
rewrite cannot disagree with the source.  The old-style `NAME ();`
declarations that go with them -- in `tree.h`, `expr.h`, `output.h`, `rtl.h`
and inside `genattrtab.c` -- have to move too: C does not allow a `()`
declaration to be completed by a variadic definition, and gcc rejects the
pair outright.

**2. obstack.h's cast-as-lvalue macros (10 sites).**  `obstack.h` grows its
buffer with `*((void **) __o->next_free)++ = datum`, a GNU extension removed
in GCC 4.0.  Each becomes a comma expression that assigns and then advances
the pointer, preserving the macros' value semantics.

Two build flags matter as much as the rewrites:

- **`-fcommon`.**  gcc 2.5.8 defines shared globals (`word_mode`,
  `class_narrowest_mode`, `local_vars_size`) as tentative definitions in
  several files at once, which every C compiler used to merge.  The harness
  builds `-fno-common`, so this port puts the old behaviour back.
- **`-I-`**, for the same reason as the perl port: the staged copies in
  `gen/src` and the replacement headers in `hostcfg` must win over the
  upstream files of the same name, and a quoted include otherwise searches the
  including file's own directory first.

## Running

    make run-spike SIZE=tiny
    ../scripts/run-verilator.sh gcc-cc1.riscv workload/input-2.i -quiet -O -o out.s

| size | input | assembly out | instructions |
|---|---|---|---|
| tiny | 2 groups, 121 lines | 419 lines | 13 M |
| small | 8 groups, 463 lines | 1,652 lines | 51 M |
| ref | 64 groups, 3,655 lines | 13,068 lines | 407 M |

`-O` matters: the optimisers are where a compiler benchmark spends its time.
The assembly is written to a host file through the frontend server, which also
makes the output easy to check.

## Verification: byte-identical SPARC assembly

The same sources and the same generated files build natively, so the two
compilers can be handed the same input and their output diffed:

    diff <(/tmp/cc1-host input.i -quiet -O -o -) \
         <(spike --isa=rv64imafdc gcc-cc1.riscv input.i -quiet -O -o -)

The 419 lines of SPARC assembly from the `tiny` input are **identical** between
the bare-metal riscv64 cc1 and a native x86-64 build.  For a compiler that is
the strongest check available: every pass -- folding, CSE, loop optimisation,
register allocation, scheduling, final output -- agreed exactly.

## Workload

`workload/mkinput.py` generates a single translation unit of plain C89 with no
`#include` at all, which makes it already preprocessed by construction -- and
avoids any dependency on the host's headers, which a 1994 parser would not
survive in any case.

One thing to know if you write your own input: **cc1 does not understand
comments.**  On a real gcc run cpp has already removed them.  A `/*` in the
input produces `parse error before '/'` and then a cascade of misleading type
errors from the failed recovery -- which is exactly how the first generated
input failed, and it looked like a struct-declaration problem rather than a
lexing one.
