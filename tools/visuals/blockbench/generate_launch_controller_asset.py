"""Generate the Launch Controller Blockbench source, runtime models, and preview."""

from __future__ import annotations

import base64
import json
import uuid
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[3]
CATALOG = ROOT / "tools/visuals/blockbench/gameplay_catalog"
ASSETS = ROOT / "src/main/resources/assets/war_mod"

MATERIALS = (
    ("launch_controller_dark", "181f26fe_fcbe162625"),
    ("launch_controller_body", "43503eff_761265bf6f"),
    ("launch_controller_stock", "838885ff_4e7dd92a24"),
    ("launch_controller_screen", "000506ff_648985b2c8"),
    ("launch_controller_green", "628335ff_47b92de9f3"),
    ("launch_controller_warning", "a1321eff_4f436a8c71"),
    ("launch_controller_brass", "a78637ff_0935624e7d"),
)

BOXES: list[tuple[str, tuple[float, ...], tuple[float, ...], int]] = []


def box(name: str, start: tuple[float, ...], end: tuple[float, ...], material: int) -> None:
    BOXES.append((name, start, end, material))


box("base_plinth", (-7.25, -8, -7.25), (7.25, -6.5, 7.25), 0)
box("lower_cabinet", (-6.75, -6.5, -6.75), (6.75, 1.8, 6.75), 0)
box("upper_console", (-6.5, 1.8, -5.2), (6.5, 7.0, 6.5), 0)
box("cabinet_skin", (-6.35, -6.1, -6.95), (6.35, 1.35, 6.35), 1)
box("access_panel", (-5.35, -5.35, -7.18), (5.35, 0.4, -6.94), 2)
box("access_recess", (-4.75, -4.75, -7.32), (4.75, -0.2, -7.17), 0)
for ix, x in enumerate((-4.45, 4.1)):
    for iy, y in enumerate((-4.45, -0.55)):
        box(f"panel_fastener_{ix}_{iy}", (x, y, -7.42), (x + 0.35, y + 0.35, -7.30), 6)
box("console_bezel", (-5.65, 2.35, -5.55), (5.65, 6.45, -5.18), 2)
box("status_screen", (-4.95, 3.15, -5.73), (1.85, 5.85, -5.53), 3)
box("screen_trace_h", (-4.3, 4.38, -5.80), (1.15, 4.62, -5.72), 4)
box("screen_trace_v", (-1.72, 3.6, -5.80), (-1.48, 5.38, -5.72), 4)
box("switch_bank", (2.45, 3.0, -5.73), (4.95, 5.95, -5.50), 0)
box("launch_guard", (2.85, 3.4, -5.88), (4.55, 4.55, -5.72), 5)
box("selector_a", (2.85, 5.0, -5.86), (3.45, 5.55, -5.72), 6)
box("selector_b", (3.75, 5.0, -5.86), (4.35, 5.55, -5.72), 2)
box("side_conduit_l", (-7.10, -4.5, -3.7), (-6.72, 0.3, -2.7), 2)
box("side_conduit_r", (6.72, -4.5, -3.7), (7.10, 0.3, -2.7), 2)
box("rear_cable_trunk", (-1.0, -5.6, 6.55), (1.0, 5.8, 7.05), 2)
box("top_service_lip", (-6.8, 6.95, -5.45), (6.8, 7.55, 6.8), 1)


def stable_id(name: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"war_mod:launch_controller:{name}"))


def uv(start: tuple[float, ...], end: tuple[float, ...], face: str) -> list[float]:
    sx, sy, sz = (end[index] - start[index] for index in range(3))
    width, height = {
        "north": (sx, sy), "south": (sx, sy),
        "east": (sz, sy), "west": (sz, sy),
        "up": (sx, sz), "down": (sx, sz),
    }[face]
    return [0.0, 0.0, round(min(16.0, width), 4), round(min(16.0, height), 4)]


def source_element(name: str, start: tuple[float, ...], end: tuple[float, ...], material: int) -> dict:
    faces = {face: {"uv": uv(start, end, face), "texture": material}
             for face in ("north", "east", "south", "west", "up", "down")}
    return {
        "name": name, "box_uv": False, "render_order": "default", "locked": False,
        "export": True, "scope": 0, "allow_mirror_modeling": True,
        "from": list(start), "to": list(end), "autouv": 1, "color": material,
        "origin": [0, 0, 0], "faces": faces, "type": "cube", "uuid": stable_id(name),
    }


def write_source() -> None:
    elements = [source_element(*entry) for entry in BOXES]
    textures = []
    for index, (name, stem) in enumerate(MATERIALS):
        source = ASSETS / f"textures/item/blockbench_palette/{stem}.png"
        raw = source.read_bytes()
        textures.append({
            "name": name, "path": "", "folder": "", "namespace": "", "id": str(index),
            "group": "", "scope": 0, "width": 16, "height": 16, "uv_width": 16,
            "uv_height": 16, "particle": index == 0, "use_as_default": index == 0,
            "layers_enabled": False, "sync_to_project": "", "file_format": "png",
            "render_mode": "default", "render_sides": "auto", "wrap_mode": "limited",
            "pbr_channel": "color", "fps": 7, "frame_time": 1,
            "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
            "visible": True, "internal": True, "saved": False,
            "uuid": stable_id(f"texture:{name}"),
            "source": "data:image/png;base64," + base64.b64encode(raw).decode("ascii"),
        })
    model = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": "launch_controller", "model_identifier": "", "visible_box": [1, 1, 0],
        "variable_placeholders": "", "multi_file_ruleset": None,
        "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": 16, "height": 16},
        "elements": elements, "groups": [],
        "outliner": [{"uuid": stable_id("root"), "isOpen": True,
                      "children": [element["uuid"] for element in elements]}],
        "textures": textures,
    }
    path = CATALOG / "machines/launch_controller.bbmodel"
    path.write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")


def write_manifest() -> None:
    path = CATALOG / "gameplay_model_manifest.json"
    manifest = json.loads(path.read_text(encoding="utf-8"))
    manifest = [entry for entry in manifest if entry.get("id") != "launch_controller"]
    entry = {"id": "launch_controller", "category": "machines", "kind": "utility",
             "cubes": len(BOXES), "meshes": 0,
             "model": "machines/launch_controller.bbmodel",
             "preview": "previews/machines/launch_controller.png"}
    insertion = next((index + 1 for index, value in enumerate(manifest)
                      if value.get("id") == "missile_workbench"), 0)
    manifest.insert(insertion, entry)
    path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def runtime_element(name: str, start: tuple[float, ...], end: tuple[float, ...], material: int) -> dict:
    return {
        "name": name,
        "from": [round(value + 8, 4) for value in start],
        "to": [round(value + 8, 4) for value in end],
        "faces": {face: {"uv": uv(start, end, face), "texture": f"#t{material}"}
                  for face in ("north", "east", "south", "west", "up", "down")},
    }


def write_runtime() -> None:
    textures = {f"t{index}": f"war_mod:block/blockbench_palette/{stem}"
                for index, (_, stem) in enumerate(MATERIALS)}
    textures["particle"] = "#t0"
    model = {"ambientocclusion": False, "textures": textures,
             "elements": [runtime_element(*entry) for entry in BOXES]}
    (ASSETS / "models/block/launch_controller.json").write_text(
        json.dumps(model, separators=(",", ":")) + "\n", encoding="utf-8")
    item_model = {
        "parent": "war_mod:block/launch_controller",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.72, .72, .72]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [.38, .38, .38]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [.62, .62, .62]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [.45, .45, .45]},
            "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [0, 0, 0], "scale": [.45, .45, .45]},
            "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [.36, .36, .36]},
            "thirdperson_lefthand": {"rotation": [75, 135, 0], "translation": [0, 2.5, 0], "scale": [.36, .36, .36]},
        },
    }
    (ASSETS / "models/item/launch_controller.json").write_text(
        json.dumps(item_model, separators=(",", ":")) + "\n", encoding="utf-8")
    (ASSETS / "items/launch_controller.json").write_text(
        '{"model":{"type":"minecraft:model","model":"war_mod:item/launch_controller"}}\n',
        encoding="utf-8")
    variants = {"facing=north": {"model": "war_mod:block/launch_controller"}}
    for facing, rotation in (("east", 90), ("south", 180), ("west", 270)):
        variants[f"facing={facing}"] = {"model": "war_mod:block/launch_controller", "y": rotation}
    (ASSETS / "blockstates/launch_controller.json").write_text(
        json.dumps({"variants": variants}, separators=(",", ":")) + "\n", encoding="utf-8")


def write_preview() -> None:
    canvas = Image.new("RGBA", (512, 512), (25, 29, 31, 255))
    draw = ImageDraw.Draw(canvas)

    def paste(rect: tuple[int, int, int, int], material: int) -> None:
        x0, y0, x1, y1 = rect
        stem = MATERIALS[material][1]
        tile = Image.open(ASSETS / f"textures/block/blockbench_palette/{stem}.png").convert("RGBA")
        tile = tile.resize((x1 - x0, y1 - y0), Image.Resampling.NEAREST)
        canvas.alpha_composite(tile, (x0, y0))
        draw.rectangle(rect, outline=(10, 12, 13, 255), width=3)

    paste((74, 228, 438, 457), 0)
    paste((88, 238, 424, 444), 1)
    paste((102, 258, 410, 416), 2)
    paste((119, 276, 393, 399), 0)
    paste((80, 75, 432, 238), 0)
    paste((90, 86, 422, 226), 1)
    paste((112, 105, 400, 211), 2)
    paste((132, 126, 298, 187), 3)
    draw.line((145, 157, 284, 157), fill=(78, 128, 73, 255), width=7)
    draw.line((214, 137, 214, 176), fill=(78, 128, 73, 255), width=7)
    paste((313, 122, 381, 194), 0)
    paste((325, 137, 369, 167), 5)
    paste((326, 175, 341, 190), 6)
    paste((352, 175, 367, 190), 2)
    draw.text((74, 474), "Launch Controller - north/operator face", fill=(220, 224, 222, 255))
    path = CATALOG / "previews/machines/launch_controller.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(path)


def main() -> None:
    if len(BOXES) != 22:
        raise RuntimeError(f"Expected 22 cubes, found {len(BOXES)}")
    for _, stem in MATERIALS:
        for domain in ("block", "item"):
            path = ASSETS / f"textures/{domain}/blockbench_palette/{stem}.png"
            if not path.exists():
                raise FileNotFoundError(path)
    write_source()
    write_manifest()
    write_runtime()
    write_preview()
    print("Generated Launch Controller: 22 cubes, Blockbench source, runtime models, and preview.")


if __name__ == "__main__":
    main()
