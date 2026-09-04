# Military material source atlas

The source atlas was generated with Codex's built-in ImageGen tool on 2026-09-04, then split and downsampled into the adjacent 16 x 16 material tiles for the Blockbench catalog generator.

Prompt intent: a seamless 3 x 3 Minecraft pixel-art albedo atlas containing weathered concrete, gunmetal, olive painted steel, a black silo shaft, brushed steel, brass, red warning paint, a green radar crosshair with no contact dots, and soot-darkened metal. The prompt explicitly excluded perspective, baked lighting, neon LEDs, text, logos, and watermarks.

`generate_gameplay_model_catalog_via_mcp.ps1` tints these albedo templates to each model's established palette, embeds them in the saved `.bbmodel` files, and exports the same pixels to runtime item/block models.

`export_gameplay_runtime_assets.ps1` also packs neutralized versions into the
48 x 48 dynamic-mesh atlas, assigns real atlas UVs to animated missiles and
doors, and derives matching material tiles for the placed item pipe, anti-air
turret base, and legacy registered warhead item IDs. These derivatives are generated
from this directory and should not be painted independently.
