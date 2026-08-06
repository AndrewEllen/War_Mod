# Stage 8 packed explosion rendering overlay

Base commit: `bbc530ad9589370ecc60bc7f0e93d0ba5fefbddc`

This branch replaces conventional analytical per-frame blast reconstruction with fixed-capacity packed structure-of-arrays particle fields. Particle state advances by game tick and is interpolated during rendering. It avoids per-particle Java objects and repeated trajectory reconstruction, while still submitting billboards through Minecraft's Blaze3D custom-geometry path.

The previous analytical conventional renderer is retained as a client-side profiling baseline. Change modes without contacting the server:

- `/warmod renderer packed` (alias: `gpu`) selects the persistent packed field.
- `/warmod renderer legacy` (alias: `cpu`) selects the analytical per-frame baseline.
- `/warmod renderer status` reports the selected mode and current counters.

The packed path is the optimised renderer, but particle simulation and billboard extraction are still performed on the CPU; this branch does not claim compute-shader simulation or hardware instancing.

Conventional HE visuals now favour a broad crater-origin burst and a compact central spout. Hot fire is capped at 4.75 blocks above the impact surface, matching the requested 4-5 block target; cooled smoke can continue rising after the fire phase. Feed duration and vertical acceleration were reduced while radial ejection was increased.

Nuclear impacts are submitted through `NuclearParticleCloudRenderer`, which uses persistent packed particles moving through centre-rise, cap-expansion, outer-curl, under-cap-return, and stem-re-entry regions. `VoxelImpactCloudRenderer.renderFire` and `renderSmoke` are not submitted by the active world renderer. The profiling switch currently applies to conventional impact particles and the nuclear return-front ring; the main nuclear cloud remains on its packed Stage 8 path.

Iris handling now distinguishes an installed Iris mod from an actively enabled shader pack. Sodium alone stays on the custom War Mod pipelines. When Iris reports that a shader pack is active, War Mod selects vanilla-known entity pipelines for the affected translucent/emissive layers. This selection occurs when `WarheadRenderPipelines` is first initialised, so restart the client after enabling or disabling a shader pack before profiling.

Terrain shockfront sampling preserves the same 256-spoke, two-block-resolution final field. Work is now capped to 2,048 new surface samples per impact call and continued over subsequent ticks, reducing one-tick chunk/height-query spikes without removing terrain-following detail.

Terrain fragments retain connected captured block states. The client approximation adds mass-scaled launch velocity, swept multi-sample collision, impact damping, a settled state, and trails generated from recorded historical positions.

ICBM exhaust is split into dedicated full-bright core and emissive fringe passes, with separate neutral alpha-mask resources. The smoke trail is denser and uses a dedicated neutral mask.

Development debug output reports active particles, particles spawned per tick, culled particles, active debris fragments, and the selected render pipeline. The client command provides the profiling mode explicitly.

## Validation status

Static source inspection was performed for the overlay. The active new particle source does not call `ParticleTypes` or `addParticle`, and the active world renderer does not submit the voxel nuclear renderer.

A full Gradle build and in-game renderer validation were not available in this execution environment. Sodium/Iris compatibility is therefore not claimed as runtime-verified. Test vanilla, Sodium, Iris without a shader pack, and Iris with the intended shader pack before merging to `master`.

No GitHub Actions, workflows, workers, runners, or deployment automation were created or modified.
