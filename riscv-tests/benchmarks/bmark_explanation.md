# riscv-tests Benchmark Behavior Reference

This document explains, benchmark by benchmark, what each `riscv-tests/benchmarks/*.riscv`
binary actually does when it runs on this SoC, and specifically how many harts get
involved and in what way. It was written after tracing several of these benchmarks
through their `.riscv.dump` disassembly, their C sources, and (for `mt-vvadd`) direct
VCD waveform confirmation, on `Inten2CoreConfig` (`NUM_PHY_CORES=2, NUM_PUS=2,
NUM_CTXT=8` -> 32 logical harts total, `mhartid` 0-31, all uniquely wired and
confirmed against waveforms).

## Boot mechanism, common to every benchmark

Every benchmark links against the same `riscv-tests/common/crt.S` and
`riscv-tests/common/syscalls.c`. This is what actually decides how many harts
"do work" for a given binary, independent of the benchmark's own code:

1. **All 32 harts boot simultaneously** from the same reset vector into `_start`
   (`crt.S`). Each computes its own private 64KB stack region from its own
   `mhartid` (`csrr a0, mhartid`; `mul a1, a2, a0; add tp, tp, a1`). The
   hart-limiting spin loop that would park harts beyond a fixed count is present
   in source but **commented out**:
   ```asm
   # get core id
   csrr a0, mhartid
   # for now, assume only 1 core
   ##  li a1, 1
   ##1:bgeu a0, a1, 1b
   ```
   So nothing gates how many harts proceed - all 32 always run.

2. Every hart then falls through to `_init(cid=mhartid, nc)`, where **`nc` is a
   hardcoded literal, not the real hart count**:
   ```asm
   # a0 is hartid
   li a1, 1   # FIXME - set to actual number of harts
   j _init
   ```
   `nc` is always `1`, regardless of how many harts are actually present. This is
   an upstream `riscv-tests` placeholder (present verbatim in `i-rocket-chip` too),
   not something introduced by this project.

3. `_init` (`syscalls.c`) calls `thread_entry(cid, nc)`. If the benchmark does not
   define its own `thread_entry`, a weak default is used:
   ```c
   // multi-threaded programs override this function.
   // nc is currently invalid
   int __attribute__((weak)) thread_entry(int cid, int nc)
   {
     if (cid == 0) { ... return main(...); }     // only hart 0 runs main()
     return extra_thread_entry(cid, nc);         // everyone else: no-op, returns 0
   }
   ```
   So for any benchmark that just defines `main()`, **only hart 0 ever does real
   work**; the other 31 harts boot, immediately no-op, and exit.

Because of this, `nc` cannot be trusted by any benchmark for splitting work
correctly (it's always `1`), but `cid`/`mhartid` itself **is** reliable - it is
confirmed unique per hart (0-31) directly from CSRFile `io_hartid` in the VCD
waveform. Benchmarks that need real multi-hart behavior use `mhartid` directly
instead of `nc`, and those work correctly; benchmarks that rely on `nc` for
partitioning are broken by this constant.

---

## Per-benchmark behavior

### `qsort`
- **Entry point:** `main()` only (`qsort_main.c`), no `thread_entry` override.
- **Hart involvement:** 1 of 32. Only hart 0 runs the quicksort/insertion-sort
  algorithm and calls `verify()`. The other 31 harts run the weak
  `thread_entry` -> `extra_thread_entry()` no-op and exit immediately.
- **What it verifies:** single-hart integer sort correctness (recursive quicksort
  with insertion-sort fallback below a threshold), branch-heavy control flow,
  pointer arithmetic.

### `median`
- **Entry point:** `main()` only, no override.
- **Hart involvement:** 1 of 32 (same pattern as `qsort`).
- **What it verifies:** 1D three-element median filter - single-hart, memory-
  access-pattern/ALU correctness on a data-parallel-looking but sequentially
  executed kernel.

### `towers`
- **Entry point:** `main()` only, no override.
- **Hart involvement:** 1 of 32.
- **What it verifies:** Towers of Hanoi - deep recursion / call-stack correctness,
  single-hart control flow.

### `rsort`
- **Entry point:** `main()` only, no override.
- **Hart involvement:** 1 of 32.
- **What it verifies:** despite the name, this is another quicksort variant
  (`unsigned int` array) per its own source comment - single-hart sort
  correctness, similar profile to `qsort` but different data type/threshold
  behavior.

### `spmv`
- **Entry point:** `main()` only, no override.
- **Hart involvement:** 1 of 32.
- **What it verifies:** double-precision sparse matrix-vector multiply -
  single-hart FPU (double) correctness, indirect/gather memory access pattern
  (`x[idx[k]]`).

### `pmp`
- **Entry point:** `main()` only, no override (also has its own `handle_trap`).
- **Hart involvement:** 1 of 32.
- **What it verifies:** Physical Memory Protection - sets up page tables and
  scratch regions, deliberately triggers an expected trap (`CAUSE_LOAD_ACCESS`)
  and checks the trap handler recovers correctly; exits cleanly if PMP isn't
  implemented (`CAUSE_ILLEGAL_INSTRUCTION` -> `exit(0)`). Single-hart
  trap/exception-path correctness, not a compute benchmark.

### `intMul`
- **Entry point:** `main()` only, no override.
- **Hart involvement:** 1 of 32.
- **What it verifies:** integer multiply via direct `x*y`, **plus** an extra ROM
  read-back consistency test (`rom = (int*)0x10008`, reads it back across 16
  iterations in 4-word bursts and compares against a first-read reference) -
  this specifically stresses repeated/cache-line-crossing loads from a fixed
  ROM-mapped address, in addition to plain multiply correctness.

### `multiply`
- **Entry point:** `main()` only, no override.
- **Hart involvement:** 1 of 32.
- **What it verifies:** integer multiply via an explicit bit-shift-and-add
  software `multiply()` routine (32-iteration shift/add loop) rather than the
  native multiply instruction - same purpose as `intMul` without the ROM test,
  useful as a simpler, more isolated multiply-correctness check.

### `dhrystone`
- **Entry point:** own `thread_entry(cid, nc)` (`dhrystone_main.c`), **`nc`
  unused**, uses `read_csr_safe(mhartid)` directly.
- **Hart involvement:** all 32. Every hart independently runs the full classic
  Dhrystone loop, indexing into shared arrays by its own `mhartid`
  (`Ptr_Glob[coreId]`, `Next_Ptr_Glob[coreId]`) so 32 independent copies don't
  collide with each other. No barrier, no lock - each hart is fully
  self-contained and reports its own Dhrystones/sec.
- **What it verifies:** classic mixed integer/control-flow workload, run
  genuinely concurrently across all 32 harts - good pressure test for
  instruction-fetch/decode fairness across contexts and independent per-hart
  progress, but no actual inter-hart data sharing or synchronization.

### `vvadd` (plain, not `mt-vvadd`)
- **Entry point:** own `thread_entry(cid, nc)` (`vvadd_main.c`), **`nc` unused**,
  uses `read_csr(mhartid)` directly.
- **Hart involvement:** all 32, with **real cross-hart synchronization**:
  - hart 0 sets a shared flag (`rowCntr1 = 1`); every other hart spins with a
    randomized backoff delay, then does `amoadd.w %0, %2, %1` on `rowCntr1` -
    a genuine atomic increment race across all 32 harts.
  - each hart then repeatedly acquires a real shared write-lock
    (`arch_write_lock()`) around a critical section, `NUM_REP1=100` times per
    phase, twice.
  - correctness is checked via `counter == 2*NUM_REP1*rowCntr1`.
- **Confirmed empirically:** a captured passing run printed
  `Number of ROWs: 32` and `Number of lock acquisitions: 6400`
  (`2*100*32 = 6400`, exactly matching the check) - i.e., all 32 harts
  genuinely raced on the atomic counter and the lock, and it was verified to be
  self-consistent.
- **What it verifies:** this is the one benchmark in the suite that is a real,
  working, 32-way concurrent atomic/lock stress test - it exercises AMO
  correctness and lock-based mutual exclusion under genuine multi-hart
  contention, and it isn't affected by the `nc=1` bug because it never uses `nc`.

### `mm` (single/redundant matrix multiply, not `mt-matmul`)
- **Entry point:** own `thread_entry(cid, nc)` (`mm_main.c`). `cid` is used
  **only for `printf` labeling**; `nc` is used **only** in a single `barrier(nc)`
  call at the very end.
- **Hart involvement:** all 32, but **not split** - every hart allocates its own
  local `a`, `b`, `c` matrices and independently runs the *entire* matrix
  multiply (`mm()`, cache/register-blocked), then verifies its own private
  result. The only inter-hart interaction is the final barrier (currently a
  no-op self-release per hart, since `nc=1`).
- **What it verifies:** not work-splitting - this is 32-way **redundant
  concurrent execution** of the same compute+memory-heavy kernel. It's a good
  stress test for the memory subsystem under real, simultaneous, independent
  multi-hart load (cache/coherence arbitration, MSHR pressure, bandwidth
  contention). This is in fact the exact pattern that triggered the L2 MSHR-
  saturation / `ProbeUnit` watchdog issue found on `mm.riscv` under
  `Inten2CoreConfig`.

### `mt-matmul`
- **Entry point:** own `thread_entry(cid, nc)` (`mt-matmul.c`), **designed** for
  real work-splitting: `static data_t results_data[ARRAY_SIZE]` (genuinely
  shared across harts), and `matmul(coreid, ncores, ...)` partitions rows via
  `block = lda/ncores; start = block*coreid`.
- **Hart involvement (as currently built):** all 32 attempt it, but **only hart 0
  computes a correct result**. Because `nc=1` (`ncores=1`), `block = lda` (the
  whole matrix) and `start = lda*coreid`. For hart 0, `coreid=0` so `start=0`
  regardless of `nc` - it always computes the complete, correct matrix on its
  own. For every other hart, `start = lda*coreid` is a wildly out-of-bounds
  index into `A`/`C`, silently writing/reading outside the intended arrays.
  The run still shows `*** PASSED ***` only because hart 0's own full,
  independent computation is what gets checked, and the other harts' invalid
  accesses happen to land somewhere nothing verifies.
- **What it verifies today:** effectively just hart 0's full matmul (same
  category as the single-hart benchmarks, functionally) - the intended
  32-way row-partitioned matmul with shared-array coherence visibility is
  **not actually exercised** while `nc=1` holds.
- **To get its intended coverage:** would need `nc` to reflect the real hart
  count (fixing the `crt.S` constant, out of scope of this doc) - at which
  point it would become a genuine test of cross-hart write partitioning into
  one shared array plus barrier-synchronized read-back, which none of the
  other benchmarks currently cover.

### `mt-vvadd`
- **Entry point:** own `thread_entry(cid, nc)` (`mt-vvadd.c`), **designed** for
  real work-splitting via `vvadd(cid, nc, n, x, y, z)`'s
  `for (i=cid; i<n; i+=nc)` loop and `barrier(nc)`.
- **Hart involvement (as currently built):** all 32 run it, but not split -
  broken two independent ways:
  1. `nc=1` (same `crt.S` constant as above) means every hart strides by 1
     starting at its own `cid=mhartid` (0-31), so every hart covers
     (almost) the *entire* 1000-element array on its own, not a 1/32 slice.
  2. `results_data` is declared as a plain (non-`static`) local array in
     `thread_entry`, despite a comment claiming it's "static ... visible to
     both threads" - each hart actually gets its own private stack copy, so
     even if `nc` were fixed, results wouldn't merge into one shared array the
     way `mt-matmul`'s does.
- **Confirmed empirically via VCD:** all 32 (PU, ctxt) combinations show
  12,658-15,304 `vvadd` instruction retirements each (consistent with each hart
  doing close to the full, twice-repeated 1000-element loop independently), and
  every hart's last retired instruction lands at the identical address
  (`0x20001832`, end of `tohost_exit`) within a ~250-cycle window of each other -
  i.e., genuinely all 32 ran to completion, redundantly, not in parallel-split
  fashion. Only `cid==0` ever calls `verifyDouble()`.
- **What it verifies today:** effectively the same as `dhrystone`/`vvadd`
  (32-way independent redundant execution, no real cross-hart data
  partitioning), plus incidental exercise of the tile's dcache-to-L2 master
  path under 32-way concurrent access (this benchmark's failure mode was
  central to the `xbarPrefetchMergeDcacheOut` serialization bug investigation).

---

## Summary table

| Benchmark  | Entry point         | Harts doing real work | Cross-hart interaction | Primary verification value |
|---|---|---|---|---|
| `qsort`    | `main()`             | 1 of 32   | none                          | single-hart sort correctness |
| `median`   | `main()`             | 1 of 32   | none                          | single-hart filter/memory pattern |
| `towers`   | `main()`             | 1 of 32   | none                          | single-hart recursion/call-stack |
| `rsort`    | `main()`             | 1 of 32   | none                          | single-hart sort correctness (variant) |
| `spmv`     | `main()`             | 1 of 32   | none                          | single-hart double-precision FPU + gather access |
| `pmp`      | `main()`             | 1 of 32   | none                          | single-hart trap/PMP handling |
| `intMul`   | `main()`             | 1 of 32   | none                          | single-hart multiply + ROM read-back |
| `multiply` | `main()`             | 1 of 32   | none                          | single-hart software multiply |
| `dhrystone`| own `thread_entry`, `nc` unused | 32 of 32 | none (per-hart array slots via `mhartid`) | 32-way independent concurrent execution, fetch/decode fairness |
| `vvadd`    | own `thread_entry`, `nc` unused | 32 of 32 | **real**: shared atomic counter + real lock | genuine 32-way AMO/lock contention correctness (empirically confirmed) |
| `mm`       | own `thread_entry`, `nc` only for final barrier | 32 of 32 | barrier only (rendezvous, not data) | 32-way redundant concurrent memory/cache pressure (found the L2 MSHR watchdog bug) |
| `mt-matmul`| own `thread_entry`, designed to use `nc` | 32 attempt it, only hart 0 valid | intended: shared array + row partition (currently broken by `nc=1`) | intended: cross-hart write-partition + coherence visibility - **not currently exercised** |
| `mt-vvadd` | own `thread_entry`, designed to use `nc` | 32 attempt it, only hart 0 checked | intended: strided partition (broken by `nc=1` **and** non-`static` result array) | intended: same as above - **not currently exercised**; incidentally stresses dcache-to-L2 master path under 32-way load |

**Net picture:** the suite covers single-hart correctness (8 benchmarks), genuine
32-way independent/redundant concurrent execution (`dhrystone`, `mm`), and real
cross-hart atomic/lock contention (`vvadd`). It does **not** currently exercise
genuine partitioned-work-with-shared-result cross-hart coherence visibility,
because both benchmarks designed for that (`mt-matmul`, `mt-vvadd`) are
undermined by the same `nc=1` placeholder in `riscv-tests/common/crt.S`.
