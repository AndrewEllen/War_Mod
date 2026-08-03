from __future__ import annotations

import pathlib
import struct
import zlib


ROOT = pathlib.Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "war_mod" / "textures"


def chunk(kind: bytes, data: bytes) -> bytes:
    return (
        struct.pack(">I", len(data))
        + kind
        + data
        + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    )


def write_rgba_png(
    path: pathlib.Path,
    width: int,
    height: int,
    pixels: list[tuple[int, int, int, int]],
) -> None:
    if len(pixels) != width * height:
        raise ValueError("pixel count does not match image dimensions")

    raw = bytearray()

    for y in range(height):
        raw.append(0)

        for x in range(width):
            raw.extend(pixels[y * width + x])

    png = bytearray(b"\x89PNG\r\n\x1a\n")
    png.extend(
        chunk(
            b"IHDR",
            struct.pack(
                ">IIBBBBB",
                width,
                height,
                8,
                6,
                0,
                0,
                0,
            ),
        )
    )
    png.extend(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
    png.extend(chunk(b"IEND", b""))

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def display_surface() -> list[tuple[int, int, int, int]]:
    pixels: list[tuple[int, int, int, int]] = []

    for y in range(16):
        for x in range(16):
            # Seamless, extremely restrained scan variation.
            scan = 2 if y % 4 == 1 else 0
            noise = ((x * 17 + y * 31) % 3) - 1

            pixels.append(
                (
                    max(0, min(255, 3 + noise)),
                    max(0, min(255, 13 + scan + noise)),
                    max(0, min(255, 11 + scan + noise)),
                    255,
                )
            )

    return pixels


def main() -> None:
    write_rgba_png(
        TEXTURES / "block" / "radar_display_screen.png",
        16,
        16,
        display_surface(),
    )

    write_rgba_png(
        TEXTURES / "effect" / "radar_pixel.png",
        2,
        2,
        [(255, 255, 255, 255)] * 4,
    )


if __name__ == "__main__":
    main()
