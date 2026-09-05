"""Render a compact orthographic review sheet from the exported/source models."""

from __future__ import annotations

import base64
import io
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "war_mod"
VISUALS = ROOT / "tools" / "visuals" / "blockbench"
OUTPUT = VISUALS / "previews" / "review_materials_v2.png"
PANEL = 300


def load_item(name: str):
    document = json.loads((ASSETS / "models" / "item" / f"{name}.json").read_text(encoding="utf-8"))
    textures = {}
    for key, value in document.get("textures", {}).items():
        if not isinstance(value, str) or value.startswith("#"):
            continue
        namespace, _, path = value.partition(":")
        if not path:
            path = namespace
        target = ASSETS / "textures" / f"{path}.png"
        if target.exists():
            textures[key] = Image.open(target).convert("RGBA")
    return document, textures


def load_bbmodel(path: Path):
    document = json.loads(path.read_text(encoding="utf-8"))
    textures = {}
    for texture in document.get("textures", []):
        source = texture.get("source", "")
        if source.startswith("data:image/png;base64,"):
            textures[str(texture["id"])] = Image.open(io.BytesIO(base64.b64decode(source.split(",", 1)[1]))).convert("RGBA")
    return document, textures


def rotate(point, origin, degrees):
    angle = math.radians(degrees)
    x, y = point[0] - origin[0], point[1] - origin[1]
    return (origin[0] + x * math.cos(angle) - y * math.sin(angle), origin[1] + x * math.sin(angle) + y * math.cos(angle))


def render(document, textures, view="front"):
    elements = document.get("elements", [])
    if view == "front":
        axis_a, axis_b, depth, face = 0, 1, 2, "north"
        ordered = sorted(elements, key=lambda e: (e["from"][2] + e["to"][2]) / 2, reverse=True)
    else:
        axis_a, axis_b, depth, face = 0, 2, 1, "up"
        ordered = sorted(elements, key=lambda e: (e["from"][1] + e["to"][1]) / 2)
    mins = [min(e["from"][axis] for e in elements) for axis in range(3)]
    maxs = [max(e["to"][axis] for e in elements) for axis in range(3)]
    span_a, span_b = maxs[axis_a] - mins[axis_a], maxs[axis_b] - mins[axis_b]
    scale = min(250 / max(span_a, 1), 250 / max(span_b, 1))
    offset_a = (PANEL - span_a * scale) / 2 - mins[axis_a] * scale
    offset_b = (PANEL - span_b * scale) / 2 + maxs[axis_b] * scale
    canvas = Image.new("RGBA", (PANEL, PANEL), (28, 31, 32, 255))
    for element in ordered:
        corners = [
            (element["from"][axis_a], element["from"][axis_b]),
            (element["to"][axis_a], element["from"][axis_b]),
            (element["to"][axis_a], element["to"][axis_b]),
            (element["from"][axis_a], element["to"][axis_b]),
        ]
        rotation = element.get("rotation")
        angle = 0.0
        origin3 = element.get("origin", (0, 0, 0))
        if isinstance(rotation, dict):
            wanted_axis = "z" if view == "front" else "y"
            if rotation.get("axis") == wanted_axis:
                angle = float(rotation.get("angle", 0))
                origin3 = rotation.get("origin", origin3)
        elif isinstance(rotation, list) and len(rotation) == 3:
            angle = float(rotation[2] if view == "front" else rotation[1])
        if angle:
            origin = (origin3[axis_a], origin3[axis_b])
            corners = [rotate(point, origin, angle) for point in corners]
        polygon = [(round(a * scale + offset_a), round(offset_b - b * scale)) for a, b in corners]
        faces = element.get("faces", {})
        face_data = faces.get(face) or next(iter(faces.values()), {})
        texture_key = str(face_data.get("texture", ""))
        if texture_key.startswith("#"):
            texture_key = texture_key[1:]
        texture = textures.get(texture_key)
        if texture is None:
            colour = (105, 110, 108, 255)
            ImageDraw.Draw(canvas).polygon(polygon, fill=colour, outline=(18, 20, 20, 255))
            continue
        left = min(point[0] for point in polygon); right = max(point[0] for point in polygon)
        top = min(point[1] for point in polygon); bottom = max(point[1] for point in polygon)
        if right <= left or bottom <= top:
            continue
        tile = texture.resize((right - left + 1, bottom - top + 1), Image.Resampling.NEAREST)
        mask = Image.new("L", canvas.size)
        ImageDraw.Draw(mask).polygon(polygon, fill=255)
        layer = Image.new("RGBA", canvas.size)
        layer.alpha_composite(tile, (left, top))
        canvas = Image.composite(layer, canvas, mask)
        ImageDraw.Draw(canvas).line(polygon + [polygon[0]], fill=(15, 17, 17, 190), width=1)
    return canvas


def main():
    entries = []
    for title, item in (("Targeting chip - front", "targeting_chip_tier_2"), ("Pipe wrench - front", "pipe_wrench"), ("High explosive", "high_explosive_tnt"), ("Conventional missile", "conventional_missile")):
        entries.append((title, *load_item(item), "front"))
    entries.append(("Artillery shell", *load_bbmodel(VISUALS / "gameplay_catalog" / "projectiles" / "artillery_shell.bbmodel"), "front"))
    entries.append(("Silo doors - top", *load_bbmodel(VISUALS / "gameplay_catalog" / "machines" / "missile_silo_large.bbmodel"), "top"))
    sheet = Image.new("RGB", (PANEL * 3, (PANEL + 36) * 2), (18, 20, 21))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=16)
    for index, (title, document, textures, view) in enumerate(entries):
        x = (index % 3) * PANEL
        y = (index // 3) * (PANEL + 36)
        sheet.paste(render(document, textures, view).convert("RGB"), (x, y))
        draw.text((x + 10, y + PANEL + 8), title, fill=(230, 232, 228), font=font)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
