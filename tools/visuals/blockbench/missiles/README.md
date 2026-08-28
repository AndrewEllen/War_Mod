# War Mod Blockbench Missile Family

Editable Blockbench source models for every registered War Mod missile item.
These files are design sources for a follow-up integration pass; they do not
replace the current generated item models or Java in-flight renderer yet.

## Design contract

- All geometry is voxel-authored from cubes. There are no smooth meshes.
- The body uses overlapping square shells and small 45-degree corner columns
  to produce a cuboidal-round, approximately octagonal profile.
- Every yield has unique proportions and yield-specific equipment while
  retaining the same body, fin, engine and payload design language.
- Cluster variants add four external canisters and a warning collar around the
  payload cone.
- Export basenames exactly match the existing item model IDs.

## Single missiles

| Yield | Blockbench source | Distinguishing feature |
|---|---|---|
| High Explosive | `single/high_explosive_missile.bbmodel` | Compact body and fuze access panel |
| High-Capacity HE | `single/high_capacity_he_missile.bbmodel` | Extended body and shoulder ribs |
| Conventional | `single/conventional_missile.bbmodel` | Balanced profile and data panel |
| Heavy Conventional | `single/heavy_conventional_missile.bbmodel` | Wider body and external armour plates |
| Tactical Nuclear | `single/tactical_nuclear_missile.bbmodel` | Nuclear markers and forward canards |
| Strategic Nuclear | `single/strategic_nuclear_missile.bbmodel` | Long-range body and paired guidance boxes |
| Heavy Nuclear | `single/heavy_nuclear_missile.bbmodel` | Largest body with reinforced mid and payload braces |

## Cluster missiles

| Yield | Blockbench source |
|---|---|
| High Explosive | `cluster/high_explosive_cluster_missile.bbmodel` |
| High-Capacity HE | `cluster/high_capacity_he_cluster_missile.bbmodel` |
| Conventional | `cluster/conventional_cluster_missile.bbmodel` |
| Heavy Conventional | `cluster/heavy_conventional_cluster_missile.bbmodel` |
| Tactical Nuclear | `cluster/tactical_nuclear_cluster_missile.bbmodel` |
| Strategic Nuclear | `cluster/strategic_nuclear_cluster_missile.bbmodel` |
| Heavy Nuclear | `cluster/heavy_nuclear_cluster_missile.bbmodel` |

## Supporting files

- `missile_model_manifest.json` provides machine-readable IDs, categories,
  delivery modes, colours, geometry counts and relative paths.
- `previews/single/` and `previews/cluster/` contain matching PNG previews.
- `../generate_missile_family_via_mcp.ps1` reproduces the complete family by
  driving the Blockbench MCP server at `http://127.0.0.1:3000/bb-mcp`.

## Follow-up integration boundary

The next pass must decide how the source geometry maps into both existing
consumers:

1. generated inventory item models under
   `src/main/resources/assets/war_mod/models/item/`; and
2. the procedural in-flight missile renderer used by the silo and rocket
   systems.

Keep the registered IDs, yield semantics and single/cluster delivery modes
unchanged while integrating the visual geometry.
