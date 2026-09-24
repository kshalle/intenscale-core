# cache/ — swapped to chipyard's stock InclusiveCache

## Source

Copied verbatim from chipyard's vendored `rocket-chip-inclusive-cache` submodule:

- Path: `generators/rocket-chip-inclusive-cache/design/craft/inclusivecache/src/`
- Commit: `f2e2c92bd1efb55c4007c98cae201a0864e2fd4a` ("Merge pull request #7 from SingularityKChen/main", 2023-01-04)
- Upstream: https://github.com/chipsalliance/rocket-chip-inclusive-cache

Note: the upstream project has moved on since this commit (chisel3 port, control-address BigInt fix,
etc. — currently ~112 commits ahead, `27df928` at time of writing). We're on the older snapshot
chipyard happens to vendor, not upstream HEAD.

Previously this folder held an Intensivate-customized cache (`ComposableCache`, derived from the
SiFive original, "functionally changed" per its own header). That version is still in git history if
needed (`git log -- src/main/scala/cache/`).

## Functional changes vs. chipyard source: none

The cache logic itself (Scheduler/MSHR/Directory/BankedStore/Source*/Sink*) is untouched. Only
build-compatibility adaptations were made, none of which change behavior:

- **Package layout**: `sifive.blocks.inclusivecache` → `freechips.rocketchip.tilelink`
  (`InclusiveCache.scala`, the top-level orchestrator) / `freechips.rocketchip.tilelink.cache`
  (everything else), matching this project's existing package convention. Imports adjusted to match.
- **`DescribedSRAM(...)` call sites** (`BankedStore.scala`, `Directory.scala`): this project's
  `util.DescribedSRAM` (outside `cache/`) returns `(SyncReadMem, OMSRAM)` instead of chipyard's
  assumed bare `SyncReadMem`. Destructured with `val (mem, _) = DescribedSRAM(...)`.
- **`cf"..."` → `s"..."`** (`MSHR.scala`, 4 assert messages): `cf` is a newer Chisel3 string
  interpolator not present in this project's vendored Chisel. All interpolated values (`from`, `to`,
  `cfg`) are plain Scala values, not hardware signals, so this is a lossless swap.
- **`Configs.scala`**: dead code (nothing references `WithInclusiveCache`; this project wires L2/L3
  directly rather than via `BankedL2Key`'s `coherenceManager` callback). Only touched to make it
  compile — its `CoherenceManagerInstantiationFn` return type here is `Option[IntOutwardNode]`, not
  chipyard stock's 3-tuple.

`Parameters.scala` has zero diff against chipyard — `require(lastLevel)` is enforced exactly as
upstream wrote it.

## Known limitation: last-level-only

This cache is architecturally last-level-only, enforced by `require(lastLevel)` in `Parameters.scala`
and confirmed against upstream's own README ("a coherent, **last-level**, inclusive cache") and full
git history (`SinkB.scala` — inbound TileLink B-channel/Probe intake — has never existed in this
project across its 112 commits). Concretely: it has no code path for handling an inbound Probe from
something further out (no `SinkB`, `Scheduler.scala` drains `io.out.b` unconditionally, `MSHR.scala`
has no non-last-level allocation branch). It can only sit directly on non-coherent memory, not above
another coherent cache.

Estimated to add: ~100 lines across `SinkB.scala` (new, ~60 lines), `Scheduler.scala` (~6 lines
wiring, priority-sensitive), `MSHR.scala` (~35 lines, reusing existing `s_pprobe`/`w_pprobeack*`
scheduling primitives already present for same-level inner-probe handling). Not done — tracked as
future work, see "Current status" below.

## Parameter differences vs. the previous (Intensivate) cache

- **`CacheParameters`**: lost `cacheID: Int` (used to offset each instance's control address;
  non-load-bearing here since callers already offset addresses themselves).
- **`InclusiveCacheMicroParameters`** (was `ComposableCacheMicroParameters`): lost `dirCode`/
  `bankCode: Code = new SECDEDCode` — SECDED protection on the cache's own Directory/BankedStore
  SRAMs. Real functional loss: the cache's internal storage is unprotected now. (Separate from
  `EccSystem`/`ECCCache`, which protects DRAM contents, not the cache's own arrays, and was never
  actually active in the build regardless — see `EccSystem.scala`.)
- **`InclusiveCache`'s constructor** (was `ComposableCache`): lost `sideband: Option[...]` (and the
  `ComposableCacheSidebandParameters`/`sidenode` port it enabled) and `haltOnUncorrectable: Boolean`
  (and the `halt_and_catch_fire` output it drove). Neither was referenced by any real call site before
  the swap.
- **Companion object**: lost `L3ControlAddress`/`ECCControlAddress` (only `L2ControlAddress` remains,
  and its value changed: `0x2010000` here vs. `0x2030000` before). `L3.scala`/`EccSystem.scala` now
  define those two as local constants instead.

## Current integration status

- **L2**: active, uses this cache (`RocketSubsystem.scala`).
- **L3**: dropped. `L3.scala`'s `L3` class is now just the clock-domain-crossing bridge
  (`L3InRatCross`/`L3OutRatCross`, needed regardless since L3's bank runs on `clk_1GHz`, a different
  domain from both L2 and the uncore) plus a `TLCacheCork` to terminate L2's now-direct-to-memory
  outer edge — no cache module in between. Kept structurally in place so re-inserting a real L3 later
  is a contained change. Plan: once L2-only passes benchmarks, add mid-level Probe support (see
  above) and bring L3 back as a second `InclusiveCache` instance.
- **ECC** (`EccSystem.scala`'s `ECCCache`): was never active before the swap either —
  `EccSystemInst` and all its wiring in `L3.scala` are commented out. Untouched by this swap.
