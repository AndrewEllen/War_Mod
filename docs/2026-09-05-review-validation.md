# Review follow-up validation — 4–5 September 2026

This follows the user review of committed `edefaa5`. The earlier pass and its evidence remain documented separately in `2026-09-04-fire-and-gameplay-validation.md`.

## Rendering and assets

- Near fire receives extra overlapping tongues inside 15 blocks, fading to the accepted density/size policy by 30 blocks. Actual camera projection governs zoom detail. The far representation and occupied footprint are retained.
- Near flame shading uses the sprite's internal pattern plus stable pixel variation, rather than a flat colour mask. Smoke is darker grey. Flame wind displacement and the duration of pressure-pulse advection are increased without changing the terrain shockfront renderer.
- Grass-like surface fuel expires and leaves nonflammable coarse dirt, rather than repeatedly relighting as fresh fuel or deleting the ground. The isolated live grass/wood patch expired; wood consumed its own blocks and lasted longer.
- The CPU renderer reuses exact cached plans and tick snapshots, removes duplicate coordinate/normal transforms, writes vertices through Minecraft's bulk entity-format writer, and reuses camera distances. Separate flame/near-flame/smoke geometry timers expose costs that extraction-only telemetry missed.
- The GPU backend remains quarantined in this checkout. No working GPU acceleration or particle self-occlusion implementation is claimed. Flame masks do not write into world depth and therefore do not punch holes in independently rendered smoke.
- The ImageGen material source is retained under `tools/visuals/blockbench/material_sources`. Generated source models, item/block assets and animated mesh atlases share those 16-pixel material tiles. The audit covered missiles, components, all 14 explosive variants, pipes, turret/radar parts, equipment and the workbench. The only flat active texture reference is the particle fallback on empty clearance models.

## Gameplay

- The workbench occupies two blocks, with one shared inventory. Either half accepts typed inputs from any side; automatic extraction exposes only finished missiles. Its inset model uses non-occluding block geometry so the ground remains visible around the base. Legacy controllers retain their inventory and attempt one safe expansion into an empty adjacent space.
- The silo centre has a selectable/collidable closed surface, meeting doors and a black throat. Ignition begins below the opening with continuous throat smoke and nozzle-anchored exhaust. Doors stay open until the missile clears them.
- ICBMs accelerate through a vertical climb and a smooth powered transition into a constant-gravity coast. Cluster payloads split into four quarter models with a wider ballistic spread; regular warheads retain their geometry. Anti-air missiles retain the moving-target interception curve after their longer vertical climb; their coast is not a free ballistic trajectory.
- The launcher uses held right-click aim/reticle and left-click fire, consumes HE rockets only, and has powered acceleration followed by drag/gravity. Its aim interaction consumes use without a punch swing.
- Silo and Anti-Air Turret ownership/ally controls are server-authoritative. Placed defences are owned; unclaimed defences and unowned/debug missiles have no friendly exemption. Missed anti-air missiles returning toward the ground remain threats regardless of owner.

## Live evidence

Tests use the existing integrated server through the Minecraft MCP tools and temporary Java attachment helpers under ignored `build/fire-lod-validation`.

- `review-fire-back/` and `review-fire-forward/`: movement across near/middle distances without the previous disappearing band in inspected frames. The simulation remains active, so these are not frozen-volume comparisons.
- `review-fire-final-lifetime/`: isolated ground/wood burn sequence and eventual ground burnout. The temporary platform was removed.
- Silo centre crosshair and standing tests succeeded. The silo UI displayed the complete HE name and “Inaccuracy”.
- Actual hoppers above and below the **right** workbench half inserted two bodies/tier-3 chips into the left inventory. With no warheads, the output chest stayed empty and inputs remained stored. Adding two HE warheads produced exactly two HE missiles carrying `war_mod:missile_guidance_tier: 3` in the output chest. Temporary hoppers/bench/chest were removed.
- `review-launcher-sequence/`: reticle, held aim, attack-key launch and visible smoke trail. The recording caught an initial right-use swing, which was then corrected to the no-swing interaction result. After restart, `review-launcher-no-swing.txt` recorded all 20 samples with held use active and swinging false, including the attack-key fire interval.
- `review-silo-coherent/`: completed 51-frame launch capture with the matching compiled client. Inspected frames show an opaque black throat, smoke before emergence, smoke around the rising missile and lingering exhaust after clearance, followed by closed meeting doors. The consumed test missile was replaced.
- Actual silo UI Claim/Unclaim changed the persisted owner as expected. Adding the test ally Notch resolved a real UUID into the server whitelist; Remove cleared it. Anti-Air Turret Claim/Unclaim was independently checked against server NBT. Both devices were restored to Player922 with empty ally lists. The expanded panels were inspected in game, including the silo at GUI scales 2 and 4; a page-label overlap and underlying launch button at modal scale were corrected afterward.

## Performance interpretation

Thirty-second JFR profiles are retained as `run/review-heavy.jfr` and `run/review-bulk.jfr`, with extracted samples in the validation directory. The first profile identified repeated per-element buffer writes as the dominant fire CPU cost. After switching to the bulk writer, that hotspot dropped substantially in the follow-up profile. The player moved and fire counts changed between recordings, so these sample counts are **not** an FPS improvement percentage or a controlled benchmark.

The initial extraction sample in this review was median 6.01 ms / p95 9.36 ms. An early cached-renderer sample was 2.81 / 3.98 ms while drawing more cards, but the world had also evolved; this is supporting telemetry, not a guaranteed performance result. Large overlapping fields still carry geometry, sorting and transparent overdraw costs.

## Checks

- Main/client compilation and the filtered fire, missile, trajectory and rocket suite passed (51 tests at the pre-ownership checkpoint).
- Ownership API compilation and focused defence tests passed at the initial ownership checkpoint.
- The final full run reported 208 tests: 207 passed, with one unchanged failure, `WarheadChunkSnapshotCoverageTest.surfaceSupportMayUseTheFullEightBlockDescent`. Its halo fixture does not meet the snapshot preflight's required vertical coverage. Nuclear snapshot code was not changed to hide this failure. `build -x test` packaged successfully afterward.
- Asset audit: 310 JSON assets parsed and all active item/blockstate model references resolved. This checks compatibility, not every possible in-game view.

One intermediate live capture (`review-silo-smoke-final/`) aborted when compiling structural ownership changes replaced classes under the older running client. Its `NoSuchMethodError` was a mixed-version test setup, not a successful launch validation. The final run starts all classes together; no further compilation is performed beneath it. That capture also exposed a below-ground throat cover; both silo variants now place the black cover just above the terrain surface.

Ownership policy tests cover friendly, allied, unowned and returning missed anti-air cases. Live UI/persistence checks do not constitute a full multiplayer combat matrix. The final package succeeded and restarted into the review world; the corrected ownership layout was inspected again at GUI scales 2 and 4. Temporary test equipment was removed and the player's inventory, creative mode, GUI scale 2 and review position were restored. The empty Anti-Air Turret clearance model still logs a missing particle fallback warning; it has no visible geometry. Developer-profile authentication/system-info warnings also remain separate from gameplay validation.
