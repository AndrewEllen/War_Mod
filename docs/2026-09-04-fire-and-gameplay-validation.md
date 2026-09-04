# Fire LOD and gameplay validation - 4 September 2026

Implemented on `codex/repair/nuclear-semantic-parity-fire-vfx`. Changes are uncommitted.

## Changes

- Fire hierarchy snapshots preserve occupied cells through the existing paged protocol instead of selecting a small subset of hosts. Aggregates retain one-block vertical strata and surface anchors, avoiding representatives inside terrain. Stable occupancy seeds, fractional card counts and continuous projected-size weights reduce transition changes. Particle area is redistributed between density and size without a distance multiplier on the fire envelope.
- The debug stick reserves capacity for its requested brush, rather than allowing one replacement and discarding the rest at the active-patch cap.
- Missile assembly uses a body, a Tier 1/2/3 chip and a warhead/controller. All 48 combinations are in creative inventory. New silos are 5x5; existing 3x3 structures retain their footprint. Guidance belongs to the missile. Loaded missiles stay hidden and launch through an opening/rise/flight/closing sequence. See [missile assembly](missile_assembly.md).
- Mod container screens use visible, bevelled slots and Minecraft-style grey panels. The display panel has a flat black face and bezel. Equipment models, gun grips/scales, ammunition and inventory scales were updated through the existing Blockbench export pipeline. Artillery uses a field carriage; the Anti-Air Turret item follows its deployed geometry; the Remote Display is a tablet.
- Anti-Air Turret aiming permits vertical shots. The handheld launcher fires HE only, retains a world-space smoke trail, and transitions from powered flight to a ballistic coast. Its mesh is suppressed within the camera's near field, and ignition particles are clear of the camera.

## Live checks

The existing singleplayer world was backed up before restarting. Tests used Minecraft server/client MCP plus temporary Java attachment helpers under ignored `build/fire-lod-validation`. These helpers operated vanilla item use and camera movement and recorded actual game frames. Temporary platforms were removed; the original inventory, selected empty slot, position, creative mode, FOV 90 and first-person camera were restored. The completed client remains running at 1920x1017.

- Near/middle/far hillside views and forward/backward movement: no recurrence of the large missing middle band in the inspected recordings. A small isolated brush was traversed over approximately 125 blocks in both directions. A second hillside sweep captured 82 full-size frames. Fire simulation remained active, so these are visual observations rather than a frozen-volume numerical comparison.
- FOV 90 to 30 and a real held spyglass: detail increased at the same position. The spyglass recording uses the final camera projection while the base FOV option remains 90.
- Debug stick: Size 1 reported one placed surface; Size 6 reported 73 at the same intensity, with visibly broader placement.
- Workbench: top body hopper, north chip hopper and east controller hopper produced three Tier 3 self-destruct interceptors, extracted by the bottom hopper into a chest. The saved output contained `war_mod:missile_guidance_tier: 3`.
- Silo: a newly placed 5x5 silo was loaded with two Tier 3 interceptors. Two redstone launch cycles were recorded. Each opened, raised its missile, launched and closed. The first returned to READY with one missile; the second returned to EMPTY with no reserved missile. Stored missiles remained hidden between launches.
- UI: workbench and silo screens inspected in game. Empty-silo guidance now displays dashes; loaded/reserved missiles supply the actual chip values.
- Display panel: unassembled tiles showed black faces/red dots; rebuilding the 2x2 rectangle produced a continuous black display with the existing yellow standby indicator and no protruding model geometry.
- Launcher: smoke remained behind the moving rocket and flight was visually continuous. A legacy nuclear-mode launcher stack was used; its mode component was removed and it followed the HE launch path. The final near-camera guard removed the full-screen rear-cap frame.
- Service pistol inspected in third person at the corrected scale. Other equipment received source/export previews and asset validation; not every held-item pose and turret engagement was individually exercised in game.

## CPU renderer measurements

The GPU route was already quarantined in this checkout. Runtime reported `CPU_FALLBACK`, `readiness=FAILED` and zero GPU allocation. GPU scheduling changes have static test coverage; no live GPU performance claim is made.

The following samples are fire extraction CPU time, not whole-frame FPS. The active simulation and camera visibility changed between samples; these are not a controlled benchmark.

| View | Flame cards | Smoke cards | Extraction median | Extraction p95 |
|---|---:|---:|---:|---:|
| Near, 854x480 | 13,679 | 10,035 | 9.49 ms | 15.01 ms |
| Middle, 854x480 | 9,613 | 8,022 | 5.38 ms | 7.69 ms |
| Far, 854x480 | 4,513 | 4,378 | 1.79 ms | 2.95 ms |
| Middle, FOV 30, 854x480 | 12,939 | 8,788 | 5.44 ms | 8.89 ms |
| After full-size sweep, 1920x1017 | 10,484 | 6,086 | 6.02 ms | 10.01 ms |

## Build and tests

- `gradlew build -x test`: passed, including main/client compilation and packaged JAR.
- Focused fire, GPU scheduling/routing, assembly, footprint and Phalanx tests: 59 passed, zero failures.
- Full suite: 196 tests, 195 passed. The remaining failure is `WarheadChunkSnapshotCoverageTest.surfaceSupportMayUseTheFullEightBlockDescent`. Its unchanged fixture supplies halo Y=-5..9, while surface derivation/preflight requires Y=-13..1. The test and implementation match HEAD; nuclear snapshot code was not changed here.
- All 180 exported runtime model files / 2,922 elements: no inverted bounds or coordinates outside the vanilla [-16,32] element range. The legacy Tier 3 support overflow was split across its existing upper/lower blocks. Final restart reported no model-load errors.
- `git diff --check`: passed.

## Local evidence

Evidence is retained under ignored `build/fire-lod-validation` (a clean build can remove it):

- `silo-cycle.mp4`, `silo-cycle/`, `silo-last-cycle/`: sampled launch recordings and raw frames.
- `fire-distance-sweep.mp4`, `fire-back-sweep/`, `fire-forward-sweep/`, `fire-1920-sweep/`: camera sweeps and per-frame statistics. The first two sweeps sampled at 250ms; the full-size sweep requested 100ms and records actual elapsed timestamps.
- `spyglass-final/`, `launcher-flight/`, `launcher-verified/`: actual use and flight recordings.
- `screenshots/panel-final.png`, `screenshots/silo-ui-final.png`: UI/model evidence (the silo image predates the final empty-guidance text polish).
- `final-build.log`, `final-full-test.log`, `full-suite-results/`, `final-focused-test.log`: build/test evidence.
- `world-before-restart/`: world backup made before the first restart with new registrations.
