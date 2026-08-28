# War Mod Gameplay Model Catalogue

This directory contains editable Blockbench source models and preview renders for the mod's registered gameplay equipment. The repaired catalogue uses cube-only voxel geometry, distinct silhouettes, and a restrained olive/gunmetal/coyote military palette. Small cyan, blue, green, and hazard-red details are reserved for optics, tier identification, controls, and yield markings.

## Categories

- `firearms/`: pistol, assault rifle, sniper rifle, and rocket launcher.
- `projectiles/`: three small fired bullets, falling warhead, artillery shell, and HE rocket.
- `missiles/anti_air/`: visually related but tier-distinct Mk I and Mk II interceptors.
- `machines/`: artillery cannon, radar station, 3x3 launch silo, Phalanx turret, radar display, and guidance supports.
- `machines/supports/`: tier 1 through tier 3 guidance-support progression.
- `equipment/`: radar gun, target and remote-launch designators, radar linking tool, pipe/wrench, fire hose, and extinguisher.
- `ammunition/`: firearm magazines and anti-air gun ammunition crate.
- `explosives/tnt/`: single and cluster TNT blocks for all seven yields.
- `debug/`: the three registered test/debug sticks, kept apart from production equipment.
- `previews/`: mirrors the source hierarchy with one PNG per model.

`gameplay_model_manifest.json` is the machine-readable index. All paths are relative to this directory.

## Articulation contracts

The artillery model uses this hierarchy and keeps the chassis/tracks outside both moving groups:

```text
artillery_root
|- fixed_base
`- yaw_turret (pivot 0, 8, 0)
   `- pitch_barrel (pivot 0, 14, -3)
```

Rotate `yaw_turret` around Y for traverse. Rotate `pitch_barrel` around X for elevation. The tracked carriage and stabilisers remain fixed.

The radar model uses this hierarchy:

```text
radar_root
|- fixed_foundation
`- yaw_head (pivot 0, 18, 0)
   `- pitch_dish (pivot 0, 25, 0; authored pitch 18 degrees)
```

Rotate `yaw_head` around Y for sweep. `pitch_dish` contains the faceted reflector, rim, support arms, and feed horn, with a separate elevation pivot if the renderer later needs adjustable tilt.

The launch-silo foundation is exactly 48 by 48 model units, matching a 3x3 block footprint at 16 model units per block. The shaft, blast walls, split doors, rails, controls, and bay lights remain separately named for integration.

Tier 1 through Tier 3 guidance supports deliberately share the same braced-mast base. Each tier adds processors and then sensor/crown hardware, producing a verified 11 -> 18 -> 25 cube progression.

## Runtime integration

These files are the editable source of truth for the replacement runtime assets. `../export_gameplay_runtime_assets.ps1` exports the embedded palette textures, baked item/block JSON, exact 3x3 silo parts, and the generated client cube data used by articulated/projectile renderers. The MCP catalogue generator runs that exporter automatically after rebuilding the `.bbmodel` files and previews.

Artillery and radar group pivots are consumed by their existing block-entity renderers. Falling warheads, artillery shells, HE rockets, anti-air missiles, and the three fired bullet heads use the generated client mesh data while retaining their existing movement, trails, and effects. Collision and voxel shapes are intentionally unchanged. Final scale, hand transforms, and animation appearance still require visual acceptance in Minecraft.

Regenerate the complete catalogue through `../generate_gameplay_model_catalog_via_mcp.ps1` while the Blockbench MCP plugin is running, or rerun only the runtime export with `../export_gameplay_runtime_assets.ps1` after editing saved models.
