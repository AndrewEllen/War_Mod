# Nuclear terrain preparation evidence report

Date: 2026-08-30
Branch: `codex/fire-aftermath-vfx-repair`
Platform: Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, Java 25

## Evidence boundary

The pre-change branch had no deterministic end-to-end benchmark or tracked-client
completion acknowledgement. The "before" column below is therefore a source audit,
not a fabricated timing result. The "after" figures are deterministic compiler-fixture
measurements from JUnit on this workstation. They prove footprint coverage, immutable
plan generation, mutation counts, memory estimates, and stable hashes; they do not
prove live chunk-generation latency, server commit latency, lighting correctness,
save/reload persistence, client render completion, network byte cost, MSPT, or frame
time.

## Before and after

| Evidence | Before: source-audited branch | After: implemented prepared path |
| --- | --- | --- |
| Target lease | Fixed radius-three (7x7) target window plus simulation corridor | Exact circle/chunk footprint unioned with a radius-one (3x3) minimum; load-only tickets |
| Pre-impact reach | Aftermath discovery deliberately capped at 64 blocks | Every required chunk is acquired, snapshotted, compiled, and published before normal detonation |
| Missing chunks | Deferred aftermath work; crater preparation could skip unavailable columns | Missing required chunks keep the request unready; no incomplete plan publication |
| Impact work | Live surface/tree/state/biome discovery and per-block writes continued after impact | Revision validation plus immutable per-chunk block, fire, and 64-bit biome-mask plans |
| Expansion | Shell-coupled discovery and queues | Deterministic radial activation buckets, ticks 0 through 18; tick 19 is the deadline guard |
| Bulk writes | Individual block mutations | Direct section palette writes only for classified inert states; fluids, redstone, block entities, modded and unclassified states use the semantic path |
| Client completion evidence | No per-impact terrain completion signal | Tracking-only full chunk/light packets followed by ordered impact markers and client ACK telemetry |
| Lifecycle | No footprint-wide, reference-counted preparation owner | Retargetable preparation handles, overlap reference counts, and explicit cancel/release paths |

## Deterministic flat-fixture reports

The fixture is a primitive, natural flat world snapshot compiled independently for
each exact yield footprint. The timer covers worker compilation only and excludes
Minecraft bootstrap. `semantic mutations = 0` is expected for this stone/dirt fixture
and demonstrates that ordinary terrain stays on the bulk-safe path.

| Yield | Radius | Required chunks | Changed chunks | Sections | Blocks | Biome quarts | Semantic mutations | Snapshot estimate | Plan estimate | Compile time | Stable hash |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Tactical | 281 | 1,033 | 749 | 1,310 | 391,516 | 282,983 | 0 | 17,051,008 B | 5,307,960 B | 161.754 ms | `fc8704a0af795cb0` |
| Strategic | 340 | 1,497 | 1,095 | 1,924 | 660,705 | 467,395 | 0 | 29,764,992 B | 8,830,408 B | 88.066 ms | `f4e5e6edeb17982e` |
| Heavy | 420 | 2,269 | 1,650 | 2,917 | 1,150,727 | 796,562 | 0 | 49,517,440 B | 15,209,176 B | 142.981 ms | `eb6b5f6afcca3925` |

Exact chunk counts above use an impact at `(8, 8)` within the impact chunk. Counts
change with sub-chunk alignment by design. The calculator's centre, edge, and corner
cases are covered separately.

## Runtime acceptance procedure

JUnit cannot exercise real chunk generation, the threaded light engine, player chunk
tracking, client rendering, or persistence. For each yield and delivery mode (ICBM,
artillery, and timed TNT), run a dedicated server plus tracked client and export:

```text
/warmod performance reset
<launch the scenario and wait for completion>
/warmod performance report
```

Reports are written beneath the world's `war_mod_diagnostics` directory. Acceptance
requires: ready before visual impact; no normal fallback; first authoritative mutation
on impact tick; last block, biome, lighting, and tracked-client ACK no later than impact
tick + 19; no ticket/plan leak; and recorded MSPT/frame-time percentiles. The current
mapped full-chunk packet API does not expose compressed wire bytes, so diagnostics
report that field as unavailable (`-1`) rather than inventing a byte count.

## Automated verification

```text
.\gradlew.bat test --no-daemon
.\gradlew.bat build --no-daemon
```

Result on 2026-08-30: both commands passed; the suite executed 123 JUnit tests.

The flat-fixture test is `WarheadFlatFixtureScenarioTest`. Unit coverage also includes
exact footprints, ticket flags, lease reference counting and retarget deltas, revision
counters, immutable plan determinism, crater precedence, semantic-path classification,
cursor consumption, and terrain marker payload round trips.

## Remaining verified boundaries

* The mapped `ThreadedLevelLightEngine.updateChunkStatus` invoker and full-chunk/light
  packet construction compile and package successfully, but have not been exercised in
  a live dedicated-server/client run. Lighting, heightmap persistence, biome save/reload,
  and tracked render completion therefore remain runtime acceptance items.
* Pre-impact revision invalidation is selective by chunk, then the worker recompiles that
  chunk. It does not yet extract and recompile only one changed section. Impact-time
  section revision checks still force expected-state validation for every affected cell
  in a changed section.
* Terminal incoming-warhead, artillery, rocket pending-impact, and timed-charge entities
  persist enough data to rebuild preparation after reload. The pre-separation ICBM
  carrier remains an in-memory `IcbmFlightController`, so a restart during that carrier
  phase is not reconstructed by this change.
* Vanilla full-chunk packets provide ordered block, biome, light, and render application.
  The marker/ACK proves the client callback ran after those packets; Fabric does not expose
  the vanilla packet codec's isolated decode time or compressed wire byte count here.

## File-by-file change map

| File | Change |
| --- | --- |
| `WarMod.java` | Registers the load-only ticket, preparation coordinator, and commit coordinator in deterministic order. |
| `IcbmConstants.java` | Names the radius-one preparation minimum separately from the radius-three terminal simulation window. |
| `IcbmChunkTicketController.java` | Keeps the carrier simulation corridor separate while starting/retargeting full-footprint preparation. |
| `IcbmFlightController.java` | Holds unexpected nuclear collision impacts and cancels preparation on interception, explicit cancel, and launch failure. |
| `WarheadLaunchService.java` | Starts preparation on spawn and transfers the carrier root preparation once to a single terminal or grouped cluster union. |
| `ArtilleryLaunchService.java` | Starts terrain preparation immediately after successful shell insertion. |
| `ArtilleryWarheadEntity.java` | Owns restart-safe impact data, holds before an unready impact, transfers a grouped split plan, and persists custom-fire mode. |
| `TimedWarheadTntBlock.java`, `TimedWarheadTntItem.java` | Start preparation immediately after a timed charge is armed or thrown. |
| `TimedWarheadTntEntity.java` | Retargets after any three-dimensional drift over two blocks, holds at fuse one, groups child plans, and persists exact preparation inputs. |
| `IncomingWarheadEntity.java` | Rebuilds preparation from persisted terminal data, holds pre-impact, and releases ownership on interception/removal. |
| `RocketProjectileEntity.java` | Persists and holds a nuclear collision point until its rebuilt plan is ready. |
| `WarheadFootprint.java`, `WarheadFootprintCalculator.java` | Central authoritative radii plus exact circle/chunk intersection and 3x3 union. |
| `WarheadPreparationTicketType.java` | Adds the radius-zero, `FLAG_LOADING`-only full-chunk ticket. |
| `WarheadPreparationLeaseManager.java`, `WarheadPreparationLeaseTarget.java`, `WarheadLeaseReferenceCounter.java`, `WarheadLeaseDelta.java` | Bounded prioritized acquisition, real FULL-chunk readiness, overlap reference counts, retarget diffs, expiry, and cleanup. |
| `WarheadPreparationRequest.java`, `WarheadPreparationHandle.java`, `PreparationState.java`, `PreparationProgress.java`, `CancellationReason.java` | Defines request ownership and the observable preparation state machine. |
| `PreparedImpactSpec.java`, `WarheadSnapshotRequirement.java`, `WarheadChunkSnapshot.java`, `WarheadStatePalette.java`, `WarheadSnapshotFlags.java`, `WarheadWorldSnapshotter.java` | Defines immutable primitive server-thread snapshots and conservative bulk/semantic classification. |
| `WarheadPlanCompiler.java`, `PreparedImpactPlan.java`, `PreparedChunkPlan.java`, `PreparedSectionPlan.java`, `PreparedBiomeSectionPlan.java`, `PreparedFireMutation.java`, `PreparedMutationPhase.java`, `PlanStatistics.java` | Worker-only deterministic crater, surface, vegetation, glass, structure, fire, biome-mask, and radial-bucket compilation. |
| `WarheadPreparationCoordinator.java`, `ConsumedPreparedImpact.java` | Owns snapshots, worker publication, cache sharing, revision invalidation, plan consumption, and deterministic release. |
| `WarheadSectionRevisionCounter.java`, `WarheadChunkRevisionAccess.java`, `LevelChunkWarheadRevisionMixin.java` | Tracks normal and bulk section mutations for plan validation. |
| `WarheadPreparedCommitManager.java`, `WarheadLightEngineAccess.java`, `ThreadedLevelLightEngineWarheadMixin.java` | Performs direct palette writes for inert states, semantic fallback for exceptional states, biome masks, heightmap/light repair, radial deadlines, tracking-only chunk sync, and ACK completion. |
| `WarheadImpactService.java` | Consumes the ready plan before visual impact; normal prepared impacts do no authoritative terrain discovery and explicit fallback is reported/cancelled. |
| `WarheadExplosionWorkManager.java`, `TestExplosionService.java` | Adds entity-only detonation and semantic mutation entry points; retains bounded debris and isolated developer fallback behavior. |
| `WarheadPreImpactPreparationManager.java` | Delegates authoritative terrain work to the coordinator and retains only bounded read-only debris sampling. |
| `WarheadGlassShockwaveManager.java`, `LongMutationCursor.java` | Uses central radii, removes head-removal queue behavior, fixes missing-chunk fallback publication, and remains isolated from the prepared normal path. |
| `WarheadImpactChunkLeaseManager.java` | Narrows the old lease to small simulation/retention windows rather than preparation readiness. |
| `ClientboundWarheadTerrainCommitPayload.java`, `ServerboundWarheadTerrainCommitAckPayload.java`, `WarheadVisualNetworking.java` | Registers ordered terrain completion markers and serverbound acknowledgements. |
| `ClientWarheadNetworking.java`, `ClientPerformanceTelemetry.java` | Processes markers on the render thread, ACKs them, tracks final latency/counts/gaps, and clears lifecycle state on disconnect/level change. |
| `WarheadLifecycleDiagnostics.java`, `WarModPerformanceDiagnostics.java` | Adds per-impact lifecycle, memory, ticket, commit, light, sync, conflict, MSPT, leak, and SLA reporting. |
| `war_mod.mixins.json` | Registers the revision and light-engine mixins. |
| `NuclearShellCouplingContractTest.java` | Removes the brittle source-string contract test. |
| `LongMutationCursorTest.java`, `WarheadFootprintCalculatorTest.java`, `WarheadPreparationTicketTypeTest.java`, `WarheadLeaseBookkeepingTest.java`, `WarheadSectionRevisionCounterTest.java` | Adds behavioral cursor, footprint, ticket, lease, retarget, and revision tests. |
| `PreparedPlanModelTest.java`, `WarheadPlanCompilerTest.java`, `WarheadFlatFixtureScenarioTest.java` | Adds immutability, determinism, precedence, semantic-path, worker isolation, hashes, memory, and all-yield fixture coverage. |
| `TimedWarheadTntPreparationTest.java`, `ClientboundWarheadTerrainCommitPayloadTest.java` | Covers three-dimensional TNT retargeting and terrain marker codec round trips. |
