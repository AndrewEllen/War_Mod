# Industrial material source atlases

The current source atlas, `industrial_material_atlas_imagegen_v2.png`, was generated with Codex's built-in ImageGen tool on 2026-09-05, then split and downsampled into the adjacent 16 x 16 material tiles for the Blockbench catalogue generator. `military_material_atlas_imagegen.png` is retained as the earlier source revision.

Prompt intent for v2: an orthographic 3 x 3 Minecraft pixel-art albedo atlas containing galvanized steel, gunmetal, painted machinery steel, poured concrete, olive missile casing, brass, safety red paint, rubber, and a black silo shaft. It requested visible 16 px seams, rivets, scratches, ribs, aggregate, and panel variation while excluding perspective, baked lighting, neon LEDs, text, logos, and watermarks.

`circuit_board.png` is a deterministic PCB derivative of the ImageGen olive-paint tile, with copper traces and a dark IC package. `industrial_material_tiles_preview_v2.png` is the enlarged review sheet for the source tiles.

`generate_gameplay_model_catalog_via_mcp.ps1` tints these albedo templates to each model's established palette, embeds them in the saved `.bbmodel` files, and exports the same pixels to runtime item/block models.

`export_gameplay_runtime_assets.ps1` also packs neutralized versions into the
48 x 48 dynamic-mesh atlas, assigns real atlas UVs to animated missiles and
doors, and derives matching material tiles for the placed item pipe, anti-air
turret base, and legacy registered warhead item IDs. These derivatives are generated
from this directory and should not be painted independently.

The accepted Anti-Air Turret world renderer is deliberately pinned to the
byte-identical `textures/phalanx_material_atlas.png` snapshot. The global atlas
may therefore improve other dynamic models without silently changing that
accepted turret material pass.
