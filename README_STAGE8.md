# Stage 8 packed explosion rendering overlay

Base commit: `bbc530ad9589370ecc60bc7f0e93d0ba5fefbddc`

This branch replaces the conventional analytical per-frame blast reconstruction with fixed-capacity packed structure-of-arrays particle fields. Particle state is advanced by game tick and interpolated for rendering. Fire retains its trajectory while cooling into smoke. Conventional impacts include a separately fed central fire spout, independent arcing particles, and a custom surface pressure-front particle field.

Nuclear impacts are submitted through `NuclearParticleCloudRenderer`, which uses persistent packed particles moving through centre-rise, cap-expansion, outer-curl, under-cap-return, and stem-re-entry regions. `VoxelImpactCloudRenderer.renderFire` and `renderSmoke` are not submitted by the active world renderer.

Terrain fragments retain connected captured block states. The client approximation adds mass-scaled launch velocity, swept multi-sample collision, impact damping, a settled state, and trails generated from recorded historical positions.

ICBM exhaust is split into dedicated full-bright core and emissive fringe passes, with separate neutral alpha-mask resources. The smoke trail is denser and uses a dedicated neutral mask.

Debug output in development builds reports active particles, particles spawned per tick, culled particles, active debris fragments, and the selected render backend.

## Validation status

Static source inspection was performed for the overlay. The active new particle source does not call `ParticleTypes` or `addParticle`, and the active world renderer does not submit the voxel nuclear renderer.

A full Gradle build and in-game renderer validation were not available in this chat execution environment. Sodium/Iris compatibility is therefore not claimed as runtime-verified. Test vanilla, Sodium, Iris without a shaderpack, and Iris with the intended shaderpack before merging to `master`.

No GitHub Actions, workflows, workers, runners, or deployment automation were created or modified.
