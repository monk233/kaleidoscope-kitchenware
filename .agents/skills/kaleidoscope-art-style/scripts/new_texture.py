#!/usr/bin/env python3
"""Deterministic texture generator for the Kaleidoscope art style.

Every pattern is drawn from a colour ladder (bright -> dark) so the output always
sits inside the series palette. Identical arguments always produce identical
pixels, which keeps generated resources reproducible in version control.

Examples:
    python new_texture.py out.png --kind brick --ramp "#4E5E60,#3C4A4C,#2F3A3C" --seed 7
    python new_texture.py out.png --kind plank --species oak
    python new_texture.py out.png --kind roof_tile --ramp "#55636C,#38424A,#2A3138"
"""
from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

from PIL import Image

SKILL_ROOT = Path(__file__).resolve().parent.parent
PALETTE_FILE = SKILL_ROOT / "references" / "palette.json"
OUTLINE = (0x5E, 0x37, 0x23, 255)


def parse_hex(value: str) -> tuple[int, int, int, int]:
    value = value.strip().lstrip("#")
    if len(value) != 6:
        raise argparse.ArgumentTypeError(f"expected RRGGBB, got '{value}'")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


def parse_ramp(value: str) -> list[tuple[int, int, int, int]]:
    ramp = [parse_hex(part) for part in value.split(",") if part.strip()]
    if len(ramp) < 2:
        raise argparse.ArgumentTypeError("ramp needs at least two colours, bright to dark")
    return ramp


def ramp_for_species(species: str) -> list[tuple[int, int, int, int]]:
    data = json.loads(PALETTE_FILE.read_text(encoding="utf-8"))
    ladder = data["wood_species"].get(species)
    if not ladder:
        known = ", ".join(sorted(data["wood_species"]))
        raise SystemExit(f"unknown species '{species}'; known: {known}")
    shared = data["shared"]
    return [parse_hex(c) for c in ladder] + [parse_hex(shared["outline_dark"]), parse_hex(shared["outline_deepest"])]


def blank(size: int) -> Image.Image:
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def px(img: Image.Image, x: int, y: int, colour: tuple[int, int, int, int]) -> None:
    img.putpixel((x % img.width, y % img.height), colour)


def draw_brick(img: Image.Image, ramp: list[tuple[int, int, int, int]], rng: random.Random) -> None:
    size = img.width
    row_height = max(4, size // 4)
    brick_width = max(6, size // 2)
    for y in range(size):
        row = y // row_height
        offset = (row % 2) * (brick_width // 2)
        for x in range(size):
            local_y = y % row_height
            in_mortar = local_y == row_height - 1 or (x + offset) % brick_width == 0
            if in_mortar:
                px(img, x, y, ramp[-1])
            elif local_y == 0:
                px(img, x, y, ramp[0])
            else:
                px(img, x, y, ramp[1 + rng.randrange(max(1, len(ramp) - 2))])


def draw_roof_tile(img: Image.Image, ramp: list[tuple[int, int, int, int]], rng: random.Random) -> None:
    size = img.width
    ridge = max(4, size // 4)
    for y in range(size):
        depth = min(len(ramp) - 1, y * len(ramp) // size)
        for x in range(size):
            if x % ridge == 0:
                px(img, x, y, ramp[-1])
            elif x % ridge == 1:
                px(img, x, y, ramp[0])
            else:
                px(img, x, y, ramp[depth])


def draw_plank(img: Image.Image, ramp: list[tuple[int, int, int, int]], rng: random.Random) -> None:
    size = img.width
    board = max(4, size // 4)
    for y in range(size):
        for x in range(size):
            if y % board == board - 1:
                px(img, x, y, ramp[-1])
            elif y % board == 0:
                px(img, x, y, ramp[0])
            else:
                px(img, x, y, ramp[1])
    for _ in range(size // 4):
        board_index = rng.randrange(size // board)
        x = rng.randrange(size)
        length = rng.randint(2, 5)
        y = board_index * board + 1 + rng.randrange(max(1, board - 2))
        for step in range(length):
            px(img, x + step, y, ramp[min(len(ramp) - 1, 2)])


def draw_plaster(img: Image.Image, ramp: list[tuple[int, int, int, int]], rng: random.Random) -> None:
    size = img.width
    for y in range(size):
        for x in range(size):
            px(img, x, y, ramp[0])
    for _ in range(size * size // 12):
        x, y = rng.randrange(size), rng.randrange(size)
        px(img, x, y, ramp[1 if len(ramp) > 1 else 0])
    for i in range(size):
        px(img, i, size - 1, ramp[-1])
        px(img, size - 1, i, ramp[-1])


def draw_tile_floor(img: Image.Image, ramp: list[tuple[int, int, int, int]], rng: random.Random) -> None:
    size = img.width
    tile = max(4, size // 2)
    for y in range(size):
        for x in range(size):
            if x % tile == 0 or y % tile == 0:
                px(img, x, y, ramp[-1])
            elif x % tile == 1 or y % tile == 1:
                px(img, x, y, ramp[0])
            else:
                px(img, x, y, ramp[1 if len(ramp) > 1 else 0])


def draw_lattice(img: Image.Image, ramp: list[tuple[int, int, int, int]], rng: random.Random) -> None:
    size = img.width
    bar, clear = max(1, size // 8), max(2, size // 4)
    for y in range(size):
        for x in range(size):
            on_frame = x < bar or y < bar or x >= size - bar or y >= size - bar
            on_grid = x % clear < bar or y % clear < bar
            if on_frame or on_grid:
                px(img, x, y, ramp[0] if x % clear < bar else ramp[1 if len(ramp) > 1 else 0])


PATTERNS = {
    "brick": draw_brick,
    "roof_tile": draw_roof_tile,
    "plank": draw_plank,
    "plaster": draw_plaster,
    "tile_floor": draw_tile_floor,
    "lattice": draw_lattice,
}


def build(kind: str, ramp, size: int, seed: int) -> Image.Image:
    img = blank(size)
    PATTERNS[kind](img, ramp, random.Random(seed))
    return img


def main() -> None:
    parser = argparse.ArgumentParser(description="Kaleidoscope-style texture generator")
    parser.add_argument("output", type=Path)
    parser.add_argument("--kind", required=True, choices=sorted(PATTERNS))
    parser.add_argument("--size", type=int, default=16)
    parser.add_argument("--seed", type=int, default=0)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--ramp", type=parse_ramp, help="bright-to-dark hex ladder, comma separated")
    group.add_argument("--species", help="wood species from references/palette.json")
    args = parser.parse_args()

    ramp = args.ramp if args.ramp else ramp_for_species(args.species)
    image = build(args.kind, ramp, args.size, args.seed)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.output)
    data = image.load()
    colours = set()
    opaque = 0
    for y in range(image.height):
        for x in range(image.width):
            colour = data[x, y]
            colours.add(colour)
            if colour[3] == 255:
                opaque += 1
    print(f"{args.output} {image.size[0]}x{image.size[1]} colors={len(colours)} opaque={opaque}")


if __name__ == "__main__":
    main()
