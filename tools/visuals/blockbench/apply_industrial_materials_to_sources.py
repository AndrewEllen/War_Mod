"""Apply the ImageGen-derived industrial material library to Blockbench sources.

The source models keep their authored average colours and alpha masks, while the
flat embedded swatches are replaced by repeatable 16 px material detail.  The
accepted Phalanx source is deliberately excluded; its runtime renderer uses a
byte-for-byte snapshot atlas.
"""

from __future__ import annotations

import base64
import copy
import io
import json
import os
import time
import uuid
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance


ROOT = Path(__file__).resolve().parents[3]
VISUAL_ROOT = ROOT / "tools" / "visuals" / "blockbench"
MATERIAL_ROOT = VISUAL_ROOT / "material_sources"


def material_name(texture_name: str) -> str:
    name = texture_name.lower()
    if "targeting_chip" in name and any(token in name for token in ("body", "green")):
        return "circuit_board"
    if "targeting_chip" in name and "stock" in name:
        return "gunmetal"
    if "artillery_shell" in name and "stock" in name:
        return "brushed_steel"
    if "_tnt_stock" in name:
        return "gunmetal"
    if any(token in name for token in ("screen", "radar", "glow")):
        return "radar_cross"
    if "concrete" in name:
        return "concrete"
    if any(token in name for token in ("brass", "copper", "driving_band", "driving band")):
        return "brass"
    if any(token in name for token in ("hose", "rubber", "grip", "pad", "stock")):
        return "rubber"
    if any(token in name for token in ("soot", "exhaust", "nozzle", "bore")):
        return "soot_metal"
    if any(token in name for token in ("shaft", "black")):
        return "shaft_black"
    if any(token in name for token in ("warning", "red")):
        return "warning_red"
    if any(token in name for token in ("steel", "metal", "jaw", "rail", "hinge", "wrench", "blue")):
        return "brushed_steel"
    if any(token in name for token in ("shadow", "dark")):
        return "gunmetal"
    if any(token in name for token in ("body", "shell", "casing", "warhead", "fin", "green", "olive", "paint", "yield", "tip")):
        return "olive_paint"
    return "painted_steel"


def ensure_special_materials() -> None:
    """Derive specialised 16 px surfaces from the generated base materials."""
    target = MATERIAL_ROOT / "circuit_board.png"
    image = Image.open(MATERIAL_ROOT / "olive_paint.png").convert("RGBA")
    pixels = image.load()
    for y in range(16):
        for x in range(16):
            red, green, blue, alpha = pixels[x, y]
            pixels[x, y] = (max(18, round(red * 0.45)), min(118, round(green * 0.9 + 18)), max(22, round(blue * 0.58)), alpha)
    draw = ImageDraw.Draw(image)
    copper, gold, package = (177, 116, 42, 255), (218, 174, 63, 255), (13, 27, 24, 255)
    draw.line(((1, 3), (5, 3), (5, 7), (9, 7)), fill=copper)
    draw.line(((14, 4), (11, 4), (11, 9), (7, 9), (7, 13), (3, 13)), fill=copper)
    draw.line(((2, 11), (5, 11), (5, 9)), fill=gold)
    draw.rectangle((6, 5, 10, 10), fill=package, outline=(48, 56, 54, 255))
    for x, y in ((1, 3), (14, 4), (2, 11), (3, 13), (9, 7)):
        draw.rectangle((x - 1, y - 1, x, y), fill=gold)
    image.save(target, optimize=True)


def decode_source(source: str) -> Image.Image:
    encoded = source.split(",", 1)[1]
    return Image.open(io.BytesIO(base64.b64decode(encoded))).convert("RGBA")


def average_opaque_colour(image: Image.Image) -> tuple[int, int, int]:
    pixels = [(r, g, b) for r, g, b, a in image.getdata() if a > 32]
    if not pixels:
        return (128, 128, 128)
    return tuple(round(sum(pixel[channel] for pixel in pixels) / len(pixels)) for channel in range(3))


def override_colour(model_path: Path, texture_name: str, colour: tuple[int, int, int]) -> tuple[int, int, int]:
    model = model_path.stem.lower()
    name = texture_name.lower()
    if model.startswith("targeting_chip") and "stock" in name:
        return (78, 88, 87)
    if model == "artillery_shell" and "stock" in name:
        return (142, 148, 143)
    if model.endswith("_tnt") and "stock" in name:
        return (54, 62, 62)
    if model == "fire_extinguisher" and any(token in name for token in ("accent", "body", "red")):
        return (185, 47, 42)
    if model == "pipe_wrench" and any(token in name for token in ("body", "wrench")):
        return (137, 146, 148)
    if model == "pipe_wrench" and "stock" in name:
        return (98, 106, 108)
    if model.startswith("missile_silo") and any(token in name for token in ("door", "steel", "metal")):
        return (89, 100, 102)
    return colour


def tint_material(material: Image.Image, target: tuple[int, int, int], alpha: Image.Image | None) -> Image.Image:
    source = material.convert("RGBA").resize((16, 16), Image.Resampling.NEAREST)
    luminances = [0.2126 * r + 0.7152 * g + 0.0722 * b for r, g, b, a in source.getdata() if a]
    mean = max(1.0, sum(luminances) / max(1, len(luminances)))
    pixels = []
    for r, g, b, a in source.getdata():
        luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
        factor = min(1.42, max(0.58, 0.20 + 0.82 * (luma / mean)))
        pixels.append((*[min(255, round(component * factor)) for component in target], a))
    result = Image.new("RGBA", source.size)
    result.putdata(pixels)
    result = ImageEnhance.Contrast(result).enhance(1.04)
    if alpha is not None:
        alpha = alpha.resize((16, 16), Image.Resampling.NEAREST)
        result.putalpha(alpha)
    return result


def encode_source(image: Image.Image) -> str:
    stream = io.BytesIO()
    image.save(stream, format="PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(stream.getvalue()).decode("ascii")


def apply_materials(path: Path, document: dict) -> int:
    changed = 0
    for texture in document.get("textures", []):
        source = texture.get("source", "")
        if not source.startswith("data:image/png;base64,"):
            continue
        original = decode_source(source)
        target = override_colour(path, texture.get("name", ""), average_opaque_colour(original))
        material = Image.open(MATERIAL_ROOT / f"{material_name(texture.get('name', ''))}.png").convert("RGBA")
        alpha = original.getchannel("A") if original.size == (16, 16) and min(original.getchannel("A").getextrema()) < 255 else None
        updated = tint_material(material, target, alpha)
        texture["source"] = encode_source(updated)
        texture["width"] = 16
        texture["height"] = 16
        texture["uv_width"] = 16
        texture["uv_height"] = 16
        texture["saved"] = False
        changed += 1
    return changed


def replace_outliner_uuid(value, removed: set[str], replacements: dict[str, list[str]]):
    if isinstance(value, list):
        result = []
        for child in value:
            if isinstance(child, str):
                if child in removed:
                    continue
                result.extend(replacements.get(child, [child]))
            else:
                result.append(replace_outliner_uuid(child, removed, replacements))
        return result
    if isinstance(value, dict):
        return {key: replace_outliner_uuid(child, removed, replacements) for key, child in value.items()}
    return value


def remove_missile_overlap(document: dict) -> int:
    elements = document.get("elements", [])
    by_name = {element.get("name"): element for element in elements}
    removed: set[str] = set()
    replacements: dict[str, list[str]] = {}
    replacement_elements: dict[str, list[dict]] = {}
    changed = 0
    for name, core in tuple(by_name.items()):
        if not name or not name.endswith("_core"):
            continue
        prefix = name[:-5]
        x_name = prefix + "_x_shell"
        z_name = prefix + "_z_shell"
        x_shell = by_name.get(x_name)
        z_shell = by_name.get(z_name)
        if x_shell is None or z_shell is None:
            x_name = prefix + "_x"
            z_name = prefix + "_z"
            x_shell = by_name.get(x_name)
            z_shell = by_name.get(z_name)
        if x_shell is None or z_shell is None:
            continue
        removed.add(core["uuid"])
        old_uuid = z_shell["uuid"]
        north = copy.deepcopy(z_shell)
        south = copy.deepcopy(z_shell)
        north["name"] = z_name + "_north"
        south["name"] = z_name + "_south"
        north["uuid"] = str(uuid.uuid5(uuid.NAMESPACE_URL, old_uuid + ":north"))
        south["uuid"] = str(uuid.uuid5(uuid.NAMESPACE_URL, old_uuid + ":south"))
        # A 0.02 model-unit separation prevents the two inward faces from being
        # coplanar while remaining far below one rendered pixel at normal scale.
        north["to"][2] = round(float(x_shell["from"][2]) - 0.02, 4)
        south["from"][2] = round(float(x_shell["to"][2]) + 0.02, 4)
        replacements[old_uuid] = [north["uuid"], south["uuid"]]
        replacement_elements[old_uuid] = [north, south]
        removed.add(old_uuid)
        changed += 2
    if not changed:
        return 0
    rebuilt = []
    for element in elements:
        if element["uuid"] in replacement_elements:
            rebuilt.extend(replacement_elements[element["uuid"]])
        elif element["uuid"] not in removed:
            rebuilt.append(element)
    document["elements"] = rebuilt
    document["outliner"] = replace_outliner_uuid(document.get("outliner", []), removed, replacements)
    return changed


def correct_silo_surfaces(path: Path, document: dict) -> int:
    if path.stem not in {"missile_silo", "missile_silo_large"}:
        return 0
    by_name = {element.get("name"): element for element in document.get("elements", [])}
    changed = 0
    floor = by_name.get("recessed_floor")
    if floor is not None and (floor["from"][1], floor["to"][1]) != (0.1, 0.3):
        floor["from"][1], floor["to"][1] = 0.1, 0.3
        changed += 1
    if path.stem == "missile_silo_large":
        coordinates = {
            "throat_w": ("to", 0, -19.12),
            "throat_e": ("from", 0, 19.12),
            "left_hinge": ("to", 0, -19.22),
            "right_hinge": ("from", 0, 19.22),
        }
        for name, (side, axis, value) in coordinates.items():
            element = by_name.get(name)
            if element is not None and element[side][axis] != value:
                element[side][axis] = value
                changed += 1
    return changed


def main() -> None:
    ensure_special_materials()
    models = sorted(VISUAL_ROOT.glob("gameplay_catalog/**/*.bbmodel")) + sorted(VISUAL_ROOT.glob("missiles/**/*.bbmodel"))
    models = [path for path in models if path.stem != "phalanx_turret"]
    texture_count = 0
    geometry_count = 0
    for path in models:
        document = json.loads(path.read_text(encoding="utf-8"))
        texture_count += apply_materials(path, document)
        geometry_count += remove_missile_overlap(document)
        geometry_count += correct_silo_surfaces(path, document)
        payload = json.dumps(document, separators=(",", ":"), ensure_ascii=False)
        temporary = path.with_suffix(path.suffix + ".industrial.pending")
        for attempt in range(5):
            try:
                temporary.write_text(payload, encoding="utf-8")
                os.replace(temporary, path)
                break
            except OSError:
                if attempt == 4:
                    raise
                time.sleep(0.15 * (attempt + 1))
    print(f"Updated {texture_count} embedded textures across {len(models)} models; replaced {geometry_count} overlapping missile sections.")


if __name__ == "__main__":
    main()
