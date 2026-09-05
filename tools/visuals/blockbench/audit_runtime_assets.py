"""Fast integrity audit for generated War Mod model and texture assets."""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageStat


ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "war_mod"


def model_exists(reference: str) -> bool:
    namespace, _, value = reference.partition(":")
    if not value:
        value = namespace
        namespace = "war_mod"
    if namespace == "minecraft":
        return True
    return namespace == "war_mod" and (ASSETS / "models" / f"{value}.json").exists()


def texture_path(reference: str) -> Path | None:
    if reference.startswith("#"):
        return None
    namespace, _, value = reference.partition(":")
    if not value:
        value = namespace
        namespace = "war_mod"
    if namespace == "minecraft":
        return None
    if namespace != "war_mod":
        return Path("<unknown-namespace>")
    return ASSETS / "textures" / f"{value}.png"


def main() -> None:
    missing_models: list[str] = []
    missing_textures: list[str] = []
    referenced_textures: set[Path] = set()
    visible_textures: set[Path] = set()
    tiny_uvs: list[str] = []
    model_count = 0
    for path in sorted((ASSETS / "models").rglob("*.json")):
        model_count += 1
        document = json.loads(path.read_text(encoding="utf-8"))
        parent = document.get("parent")
        if parent and not model_exists(parent):
            missing_models.append(f"{path.relative_to(ASSETS)} -> {parent}")
        textures = document.get("textures", {})
        has_geometry = bool(document.get("elements"))
        for reference in textures.values():
            if not isinstance(reference, str):
                continue
            target = texture_path(reference)
            if target is None:
                continue
            if not target.exists():
                missing_textures.append(f"{path.relative_to(ASSETS)} -> {reference}")
            else:
                referenced_textures.add(target)
                if has_geometry:
                    visible_textures.add(target)
        for element in document.get("elements", []):
            size = [abs(float(element["to"][axis]) - float(element["from"][axis])) for axis in range(3)]
            for face_name, face in element.get("faces", {}).items():
                uv = face.get("uv")
                if not uv:
                    continue
                span = (abs(float(uv[2]) - float(uv[0])), abs(float(uv[3]) - float(uv[1])))
                face_area = {
                    "north": size[0] * size[1], "south": size[0] * size[1],
                    "east": size[2] * size[1], "west": size[2] * size[1],
                    "up": size[0] * size[2], "down": size[0] * size[2],
                }.get(face_name, 0)
                if face_area >= 8 and max(span) < 1:
                    tiny_uvs.append(f"{path.name}:{element.get('name')}:{face_name}:{span}")

    for root_name in ("blockstates", "items"):
        for path in sorted((ASSETS / root_name).rglob("*.json")):
            document = json.loads(path.read_text(encoding="utf-8"))
            stack = [document]
            while stack:
                value = stack.pop()
                if isinstance(value, dict):
                    for key, child in value.items():
                        if key == "model" and isinstance(child, str) and not child.startswith("minecraft:") and not model_exists(child):
                            missing_models.append(f"{path.relative_to(ASSETS)} -> {child}")
                        else:
                            stack.append(child)
                elif isinstance(value, list):
                    stack.extend(value)

    low_detail: list[str] = []
    palette = [path for path in referenced_textures if "blockbench_palette" in path.parts]
    for path in palette:
        if path not in visible_textures:
            continue
        image = Image.open(path).convert("RGBA")
        colours = {pixel for pixel in image.getdata() if pixel[3] > 16}
        luma_stddev = ImageStat.Stat(image.convert("L")).stddev[0]
        if len(colours) < 2 or luma_stddev < 0.25:
            low_detail.append(f"{path.name}: colours={len(colours)} luma_sd={luma_stddev:.2f}")

    print(f"models={model_count} referenced_textures={len(referenced_textures)} palette_tiles={len(palette)}")
    print(f"missing_models={len(missing_models)} missing_textures={len(missing_textures)} low_detail_tiles={len(low_detail)} suspicious_uvs={len(tiny_uvs)}")
    for heading, values in (("MISSING MODEL", missing_models), ("MISSING TEXTURE", missing_textures), ("LOW DETAIL", low_detail), ("TINY UV", tiny_uvs)):
        for value in values[:40]:
            print(f"{heading}: {value}")
    if missing_models or missing_textures or low_detail or tiny_uvs:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
