# Second review implementation and validation checklist

Baseline: `191c289`. Checked boxes mean implemented, not automatically accepted in game. Preserve the accepted turret geometry and distant flame clustering.

## Assembly, missiles and ownership

- [x] Workbench stores components and previews the result; manual or automated extraction consumes the recipe. Legacy stored output remains recoverable.
- [x] Render body and warhead models joined in the two-block cradle, correctly oriented for all four block facings.
- [x] Fix silo door overlap and sample exposed lighting above the controller; retain the accepted launch animation.
- [x] One higher ICBM arc with continuous powered/coasting motion, no reverse horizontal travel or second climb.
- [x] Random X/Z inaccuracy resolved at launch and shared with radar, trajectory and impact.
- [x] Random four-piece cluster separation while retaining equal payload shares.
- [x] Four independent radar tracks after cluster separation; each retires at its own impact. Regular warheads retain the carrier's single track.
- [x] Moving, attenuated missile engine sound; remove the stationary silo sound source.
- [x] Unclaimed silo missiles remain unowned regardless of who initiated launch.
- [x] Claimed missiles retain every owner/ally affiliation; defence must recognize all of them. Missed returning interceptors remain hostile.
- [x] Barrel heat/cooling, muzzle flame/smoke and small shot shake within six blocks.

## Nuclear destruction and aftermath

- [x] Reuse root flight preparation at impact, correct impact ETA and keep active destruction leases alive.
- [x] Move expensive snapshot classification to workers; require the neighbouring snapshot halo before capture.
- [x] Prioritize sealed impacts and rotate equal-priority jobs to prevent starvation.
- [x] Preserve incremental chunk commits and prioritize central crater work.
- [x] Clear vegetation, flowers and trees above crater columns, including plants above the motion-blocking height.
- [x] Charred grey short/tall dry-grass textures.
- [x] Heavy-impact live run began terrain commits on the impact tick and completed 957,901 block changes across 1,676 chunks in 506 ticks. Its terminal lease ended CLEAN. A subsequent tested handoff guard prevents the spent carrier restarting the transferred preparation; that final guard has not had another full nuclear benchmark.

## Fire visibility — independent of nuclear aftermath

- [x] Monotonic replication generations across server fire-state recreation prevent ordinary fires being rejected as stale.
- [x] Fire, smoke and ember rendering stays within terrain render distance and available supporting chunks; spyglass detail does not extend that boundary.
- [x] Live field accepted 825 packets with zero rejected/stale packets after restart and rendered 2,966 flame / 2,486 smoke cards in the recorded view. Explicit expiry/re-ignition and distance-edge acceptance remain manual checks.

## Fire motion and appearance

- [x] Integrate wind history instead of moving entire particle paths when wind changes; smoothly reconcile ember prediction and cell geometry updates.
- [x] Monotonic visual clock and stale-sample rejection address the Wind time reversed crash and world-clock rollback.
- [x] Softer close flame edges and opacity, varied shape/rotation/scale; distant flame clustering preserved.
- [x] Taller large outdoor smoke plumes, less excessive horizontal drift and darker smoke across distances.
- [x] Conventional explosions push outward only; nuclear explosions have longer outward and inward phases.
- [x] Live client-only -250-tick rollback and restoration preserved monotonic visual time with over 9,000 cells; client remained running through missile launch and impact.
- [ ] Final subjective near-flame/distant-smoke appearance and visible-terrain boundary acceptance. Performance numbers are view-dependent; no blanket FPS improvement is claimed.

## Fire fuel and lifespan

- [x] Ordinary fire turns grass blocks into regular dirt, allowing vanilla grass to spread back; nuclear aftermath remains separate.
- [x] Dirt and dirt paths are not fuel.
- [x] Natural spread, preheating, embers, merges, dormant simulation and save/load inherit a 12-minute root deadline.
- [x] Heat and spread taper during the final 90 seconds. Natural descendants cannot renew the deadline; explicit ignition can start a new root.
- [ ] Long-duration gameplay acceptance for expiry and grass regrowth; policy and persistence tests cover the deadline contract.

## Models, textures and naming

- [x] ImageGen-derived material textures on explosives, shells, missiles/bodies, launcher, guns, ammunition, extinguisher, hose, wrench, tablet, chips, pipes and workbench.
- [x] Preserve accepted turret model/materials; replace solid shell colours with metal surfaces.
- [x] Correct chip/wrench hand orientation and remove overlapping missile/body surfaces.
- [x] Rename user-facing TNT names to Explosive, preserving registry IDs.

## Launch smoke and controller follow-up

- [x] Ground-anchored launch smoke persists independently after the carrier leaves; stronger upward venting during ignition/emergence.
- [x] Small, staggered smoke particles expand from the opening into a rising volume: bounded 960 ICBM / 96 interceptor particles with stable distance-fading cohorts.
- [x] Use the explosion smoke sprite and native translucent-particle phase after water, with a low alpha cutoff for gradual fades.
- [x] Pause-safe monotonic presentation clock, cached terrain support, loaded-chunk/render-distance gating; no distant ground-cloud compression.
- [x] Three-second accelerating silo emergence, continuous ascent handoff, longer door clearance, white/light-grey dense smoke feeding the rising missile.
- [x] Larger exhaust, white-hot additive core attenuated through fog, normal terrain depth testing; fixed interceptor exhaust UVs.
- [x] Launch Controller links multiple UUID-bound silos, with remote group launch and one salvo per redstone rising edge.
- [x] Controller screen, model, crafting/loot assets and live interaction checks.
- [x] Controller Linking Tool reuses the radar linking tool model; selecting once allows repeated idempotent silo additions. Existing remote linking remains available.
- [x] Final combined build: 250 tests, 77 suites, zero failures/errors. Controller/tool live checks passed; smoke live rendering checked separately from subjective appearance acceptance.

## Earlier verification record

- Asset audit: 184 runtime models, 166 referenced textures, 138 material tiles; no missing textures, low-detail placeholders or suspicious UV references.
- Live checks before the clock crash: workbench parts visible and retained until extraction; hopper extracted exactly two missiles and consumed two recipes; both owned turrets fired at an unowned silo launch; exposed silo doors rendered as metal rather than black; chip/wrench fronts inspected.
- Final complete build passed **238 tests across 71 suites**, zero failures/errors: `build/fire-lod-validation/review2-arc-radar-build.log`.
- The first spline fix still had an S bend despite forward travel and one coast apex. Replaced it with a circular powered bend tangent to the ballistic coast. Sampling the actual loaded old/new classes across the same 3,123 samples measured **89 / 0 pitch increases**. Additional tests cover 18 range/launch-altitude combinations. See [trajectory comparison](assets/icbm-arc-comparison-2026-09-05.png).
- Live cluster handoff: one carrier became four child tracks with one terminal plan apiece; all four retired separately. A simultaneous regular missile retained its original root track.
- Crash evidence: `run/crash-reports/crash-2026-09-05_02.10.13-client.txt`. Fire wind history threw when the corrected client world clock moved backwards; this was a fire-renderer regression rather than an unexplained missile failure.
- Subjective audio, turret heat/shake strength and final material appearance remain in-game acceptance items.
