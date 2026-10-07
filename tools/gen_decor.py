#!/usr/bin/env python3
"""Generate every texture and resource JSON for the decorative building blocks.

Deterministic: the same block list always produces byte-identical output, so the
generated files can live in version control. Run from the project root:

    python tools/gen_decor.py

Textures follow the kaleidoscope-art-style skill: shared outline colours, 4-5 step
ladders, outline on the outward edge, highlight row on top. Stairs / slab / wall
blockstates come from the vanilla templates in tools/vanilla_templates so the
rotation tables do not have to be hand-written.
"""
from __future__ import annotations

import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
TEMPLATES = ROOT / "tools" / "vanilla_templates"
NS = "kaleidoscope_kitchenware"

# --- palettes -----------------------------------------------------------------

BRICK = ["#7A8B8C", "#617274", "#4E5E60", "#3C4A4C", "#2F3A3C"]
ROOF = ["#6E7C84", "#55636C", "#38424A", "#2A3138"]
RIDGE = ["#93A3A3", "#7A8B8C", "#55636C", "#2A3138"]
PLASTER = ["#F2EFE6", "#E7E3D8", "#D8D3C6"]
EARTH = ["#A3814F", "#8A6A47", "#6E523A"]
FLOOR_TILE = ["#7A8B8C", "#617274", "#4E5E60", "#2F3A3C"]

# shared outline trio from the base mod, appended to every wood ladder
OUTLINE = ["#82563F", "#6D442F", "#5E3723"]
WOOD = {
    "oak": ["#C1A266", "#BA975A", "#AE8E52", "#A38249", "#937544"] + OUTLINE,
    "spruce": ["#8A663A", "#7D5D37", "#6C5532", "#5F4F2B"] + OUTLINE,
    "bamboo": ["#B9DB5F", "#ADBE4D", "#98B145", "#8A9A3D", "#809038"] + OUTLINE,
}

# name, zh, en, shape, texture spec (None = reuse the base block's texture)
DECOR = [
    ("blue_brick", "青砖", "Blue Brick", "block", {"kind": "brick", "ramp": BRICK}),
    ("blue_brick_stairs", "青砖楼梯", "Blue Brick Stairs", "stairs", None),
    ("blue_brick_slab", "青砖台阶", "Blue Brick Slab", "slab", None),
    ("blue_brick_wall", "青砖墙", "Blue Brick Wall", "wall", None),
    ("blue_roof_tile", "青瓦", "Blue Roof Tile", "block", {"kind": "roof_tile", "ramp": ROOF}),
    ("blue_roof_tile_stairs", "青瓦楼梯", "Blue Roof Tile Stairs", "stairs", None),
    ("blue_roof_tile_slab", "青瓦台阶", "Blue Roof Tile Slab", "slab", None),
    ("roof_ridge_tile", "屋脊瓦", "Roof Ridge Tile", "block", {"kind": "roof_tile", "ramp": RIDGE}),
    ("chimney", "烟囱", "Chimney", "pillar", {"kind": "brick", "ramp": BRICK}),
    ("plaster_wall", "抹灰墙", "Plaster Wall", "block", {"kind": "plaster", "ramp": PLASTER}),
    ("rammed_earth_wall", "夯土墙", "Rammed Earth Wall", "block", {"kind": "plaster", "ramp": EARTH}),
    ("stone_floor_tile", "青石地砖", "Stone Floor Tile", "block", {"kind": "tile_floor", "ramp": FLOOR_TILE}),
    ("wood_floor_board", "木地板", "Wood Floor Board", "block", {"kind": "plank", "species": "oak"}),
    ("wooden_beam", "木梁", "Wooden Beam", "pillar", {"kind": "plank", "species": "spruce"}),
    ("wooden_rafter", "椽木", "Wooden Rafter", "pillar", {"kind": "plank", "species": "oak"}),
    ("lattice_window", "木格窗", "Lattice Window", "thin", {"kind": "lattice", "species": "oak"}),
    ("bamboo_curtain", "竹帘", "Bamboo Curtain", "thin", {"kind": "curtain", "species": "bamboo"}),
]

BASE_OF = {
    "blue_brick_stairs": "blue_brick",
    "blue_brick_slab": "blue_brick",
    "blue_brick_wall": "blue_brick",
    "blue_roof_tile_stairs": "blue_roof_tile",
    "blue_roof_tile_slab": "blue_roof_tile",
}


def hexes(ramp: list[str]) -> list[tuple[int, int, int, int]]:
    return [(int(c[1:3], 16), int(c[3:5], 16), int(c[5:7], 16), 255) for c in ramp]


# --- patterns -----------------------------------------------------------------

def draw_brick(img, ramp, rng):
    size = img.width
    row_h, brick_w = max(4, size // 4), max(6, size // 2)
    for y in range(size):
        offset = ((y // row_h) % 2) * (brick_w // 2)
        for x in range(size):
            local = y % row_h
            if local == row_h - 1 or (x + offset) % brick_w == 0:
                img.putpixel((x, y), ramp[-1])
            elif local == 0:
                img.putpixel((x, y), ramp[0])
            else:
                img.putpixel((x, y), ramp[1 + rng.randrange(max(1, len(ramp) - 2))])


def draw_roof_tile(img, ramp, rng):
    size, ridge = img.width, max(4, img.width // 4)
    for y in range(size):
        depth = min(len(ramp) - 1, y * len(ramp) // size)
        for x in range(size):
            if x % ridge == 0:
                img.putpixel((x, y), ramp[-1])
            elif x % ridge == 1:
                img.putpixel((x, y), ramp[0])
            else:
                img.putpixel((x, y), ramp[depth])


def draw_plank(img, ramp, rng):
    size, board = img.width, max(4, img.width // 4)
    for y in range(size):
        for x in range(size):
            if y % board == board - 1:
                img.putpixel((x, y), ramp[-1])
            elif y % board == 0:
                img.putpixel((x, y), ramp[0])
            else:
                img.putpixel((x, y), ramp[1])
    for _ in range(size // 4):
        y = rng.randrange(size // board) * board + 1 + rng.randrange(max(1, board - 2))
        x = rng.randrange(size)
        for step in range(rng.randint(2, 5)):
            img.putpixel(((x + step) % size, y), ramp[2] if len(ramp) > 2 else ramp[-1])


def draw_plaster(img, ramp, rng):
    size = img.width
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), ramp[0])
    for _ in range(size * size // 12):
        img.putpixel((rng.randrange(size), rng.randrange(size)), ramp[min(1, len(ramp) - 1)])
    for i in range(size):
        img.putpixel((i, size - 1), ramp[-1])
        img.putpixel((size - 1, i), ramp[-1])


def draw_tile_floor(img, ramp, rng):
    size, tile = img.width, max(4, img.width // 2)
    for y in range(size):
        for x in range(size):
            if x % tile == 0 or y % tile == 0:
                img.putpixel((x, y), ramp[-1])
            elif x % tile == 1 or y % tile == 1:
                img.putpixel((x, y), ramp[0])
            else:
                img.putpixel((x, y), ramp[min(1, len(ramp) - 1)])


def draw_lattice(img, ramp, rng):
    size = img.width
    bar, gap = max(1, size // 8), max(2, size // 4)
    for y in range(size):
        for x in range(size):
            on_frame = x < bar or y < bar or x >= size - bar or y >= size - bar
            on_grid = x % gap < bar or y % gap < bar
            if on_frame or on_grid:
                img.putpixel((x, y), ramp[0] if x % gap < bar else ramp[min(1, len(ramp) - 1)])


def draw_curtain(img, ramp, rng):
    """Vertical bamboo strips with a knot line every few pixels; gaps stay transparent."""
    size = img.width
    strip, seam, node_gap = max(2, size // 6), 1, max(4, size // 4)
    for y in range(size):
        for x in range(size):
            offset = x % (strip + seam)
            if offset == strip:
                continue
            if y % node_gap == 0:
                colour = ramp[-1]
            elif offset == 0:
                colour = ramp[0]
            else:
                colour = ramp[1]
            img.putpixel((x, y), colour)


def draw_stove_face(img, ramp, rng, lit: bool = False) -> None:
    """Brick face with a dark fire mouth; the lit variant burns orange inside."""
    size = img.width
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), ramp[1] if (x + y) % 7 else ramp[0])
    for y in range(size):
        img.putpixel((0, y), ramp[-1])
        img.putpixel((size - 1, y), ramp[-1])
        img.putpixel((y, 0), ramp[-1])
        img.putpixel((y, size - 1), ramp[-1])
    mouth_x0, mouth_x1 = 3, size - 4
    mouth_y0, mouth_y1 = size // 2 - 1, size - 3
    for y in range(mouth_y0, mouth_y1 + 1):
        for x in range(mouth_x0, mouth_x1 + 1):
            img.putpixel((x, y), (0x1A, 0x14, 0x12, 255))
    if lit:
        flame = [(0xE8, 0x8E, 0x24, 255), (0xF2, 0xB1, 0x3C, 255), (0xD2, 0xAB, 0x4C, 255)]
        for y in range(mouth_y1 - 2, mouth_y1 + 1):
            for x in range(mouth_x0 + 1, mouth_x1):
                img.putpixel((x, y), flame[(x + y) % len(flame)])


def draw_stove_face_lit(img, ramp, rng):
    draw_stove_face(img, ramp, rng, lit=True)


def draw_wood_pile(img, ramp, rng):
    """Log ends seen from above: bark ring outside, growth rings inside."""
    size = img.width
    centre = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            distance = max(abs(x - centre), abs(y - centre))
            if distance > centre - 0.6:
                img.putpixel((x, y), ramp[-1])
            elif int(distance) % 2 == 0:
                img.putpixel((x, y), ramp[0])
            else:
                img.putpixel((x, y), ramp[1])


GOLD = (0xBD, 0x95, 0x38, 255)
PORCELAIN = (0xEF, 0xEE, 0xE2, 255)
PORCELAIN_SHADE = (0xD8, 0xD3, 0xC6, 255)


def draw_cabinet_front(img, ramp, rng):
    """Cabinet doors: frame, two leaves with a centre gap, brass handles."""
    size = img.width
    mid = size // 2
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), ramp[1])
    for i in range(size):
        img.putpixel((i, 0), ramp[-1])
        img.putpixel((i, size - 1), ramp[-1])
        img.putpixel((0, i), ramp[-1])
        img.putpixel((size - 1, i), ramp[-1])
    for y in range(1, size - 1):
        img.putpixel((mid, y), ramp[-1])
        img.putpixel((mid - 1, y), ramp[0])
        img.putpixel((mid + 1, y), ramp[0])
    for y in range(3, size - 4):
        img.putpixel((2, y), ramp[0])
        img.putpixel((size - 3, y), ramp[0])
    for y in range(size // 2 - 1, size // 2 + 2):
        img.putpixel((mid - 3, y), GOLD)
        img.putpixel((mid + 3, y), GOLD)


def draw_cabinet_open(img, ramp, rng):
    """Open cupboard: dark interior, a shelf, two stacked bowls."""
    size = img.width
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), ramp[-1])
    for i in range(size):
        img.putpixel((i, 0), ramp[1])
        img.putpixel((i, size - 1), ramp[1])
        img.putpixel((0, i), ramp[1])
        img.putpixel((size - 1, i), ramp[1])
    for x in range(2, size - 2):
        img.putpixel((x, size // 2), ramp[2] if len(ramp) > 2 else ramp[0])
    for base_y in (size // 2 + 3, size - 5):
        for x in range(4, size - 4):
            img.putpixel((x, base_y), PORCELAIN_SHADE)
            img.putpixel((x, base_y + 1), PORCELAIN)
            img.putpixel((x, base_y + 2), PORCELAIN_SHADE)


def draw_counter_top(img, ramp, rng):
    """Counter worktop: stone slab with a wooden rim."""
    size = img.width
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), ramp[0] if (x + y) % 5 else ramp[1])
    for i in range(size):
        img.putpixel((i, 0), ramp[2] if len(ramp) > 2 else ramp[-1])
        img.putpixel((i, size - 1), ramp[2] if len(ramp) > 2 else ramp[-1])
        img.putpixel((0, i), ramp[2] if len(ramp) > 2 else ramp[-1])
        img.putpixel((size - 1, i), ramp[2] if len(ramp) > 2 else ramp[-1])


def draw_counter_side(img, ramp, rng):
    """Counter body: panelled wood with a drawer seam and a brass pull."""
    size = img.width
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), ramp[0] if (y % 4) else ramp[1])
    for i in range(size):
        img.putpixel((0, i), ramp[-1])
        img.putpixel((size - 1, i), ramp[-1])
        img.putpixel((i, 0), ramp[-1])
        img.putpixel((i, size - 1), ramp[-1])
    for x in range(1, size - 1):
        img.putpixel((x, size // 2), ramp[-1])
    for x in range(size // 2 - 3, size // 2 + 4):
        img.putpixel((x, size // 2 + 3), GOLD)


def draw_glass_jar(img, ramp, rng, filled: bool = False) -> None:
    """Squat glass jar: pale blue body, white highlight, wooden stopper, amber contents."""
    size = img.width
    glass, glass_dark, highlight = (0xC8, 0xE4, 0xF0, 255), (0x9C, 0xC2, 0xD6, 255), (0xEF, 0xF7, 0xFA, 255)
    amber, amber_dark = (0xC2, 0x7A, 0x2E, 255), (0x9C, 0x5C, 0x1C, 255)
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), glass if (x + y) % 6 else glass_dark)
    if filled:
        for y in range(size // 2, size - 2):
            for x in range(2, size - 2):
                img.putpixel((x, y), amber if (x * y) % 5 else amber_dark)
    for y in range(0, 2):
        for x in range(3, size - 3):
            img.putpixel((x, y), ramp[1])
    for y in range(3, size - 2):
        img.putpixel((2, y), highlight)
    for i in range(size):
        img.putpixel((0, i), glass_dark)
        img.putpixel((size - 1, i), glass_dark)
        img.putpixel((i, size - 1), glass_dark)


def draw_glass_jar_filled(img, ramp, rng):
    draw_glass_jar(img, ramp, rng, filled=True)


def draw_jar_side(img, ramp, rng) -> None:
    """Clear glass bottle side seen in a reference photo: metal lid on top, glass walls
    left transparent so whatever the jar holds shows through."""
    size = img.width
    metal, metal_dark, metal_light = (0x8B, 0x8B, 0x8B, 255), (0x60, 0x65, 0x72, 255), (0xC2, 0xC7, 0xCB, 255)
    glass_edge, glass_shine = (0x9C, 0xC2, 0xD6, 255), (0xEF, 0xF7, 0xFA, 255)
    # metal lid across the top three rows
    for y in range(0, 3):
        for x in range(size):
            img.putpixel((x, y), metal_light if y == 0 else metal if (x % 3) else metal_dark)
    for x in range(size):
        img.putpixel((x, 3), metal_dark)
    # glass: only the rim, the base and one shine stripe stay opaque
    for y in range(4, size - 1):
        img.putpixel((0, y), glass_edge)
        img.putpixel((size - 1, y), glass_edge)
        img.putpixel((3, y), glass_shine)
    for x in range(size):
        img.putpixel((x, size - 1), glass_edge)


def draw_jar_side_filled(img, ramp, rng) -> None:
    """Same bottle, with seasoning visible through the glass."""
    draw_jar_side(img, ramp, rng)
    size = img.width
    amber, amber_dark = (0xC2, 0x7A, 0x2E, 255), (0x9C, 0x5C, 0x1C, 255)
    for y in range(size // 2, size - 1):
        for x in range(2, size - 2):
            img.putpixel((x, y), amber if (x * y) % 5 else amber_dark)


def draw_jar_top(img, ramp, rng, filled: bool = False) -> None:
    """Metal lid from above: brushed ring with a dark grip slot in the middle."""
    size = img.width
    metal, metal_dark, metal_light = (0x8B, 0x8B, 0x8B, 255), (0x60, 0x65, 0x72, 255), (0xC2, 0xC7, 0xCB, 255)
    for y in range(size):
        for x in range(size):
            edge = x in (0, size - 1) or y in (0, size - 1)
            inner = 3 <= x <= size - 4 and 3 <= y <= size - 4
            if edge:
                img.putpixel((x, y), metal_dark)
            elif inner:
                img.putpixel((x, y), metal_dark if (x + y) % 3 == 0 else (0x3B, 0x2A, 0x1C, 255))
            else:
                img.putpixel((x, y), metal_light if (x + y) % 2 else metal)


def draw_jar_top_filled(img, ramp, rng):
    draw_jar_top(img, ramp, rng, filled=True)


def draw_tray_glaze(img, ramp, rng) -> None:
    """White porcelain: warm white body, faint glaze shading, a pale celadon band at the rim."""
    size = img.width
    body, shade, highlight = (0xF2, 0xF2, 0xEC, 255), (0xDC, 0xDC, 0xD4, 255), (0xFF, 0xFF, 0xFB, 255)
    celadon, edge = (0xA8, 0xCF, 0xC8, 255), (0x7E, 0x9B, 0x96, 255)
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), body)
    # a few soft glaze patches, so the white does not read as a flat fill
    for _ in range(12):
        cx, cy = rng.randrange(2, size - 2), rng.randrange(2, size - 2)
        colour = shade if rng.random() < 0.6 else highlight
        radius = rng.choice((1, 1, 2))
        for dy in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                if abs(dx) + abs(dy) > radius:
                    continue
                x, y = cx + dx, cy + dy
                if 1 <= x < size - 1 and 1 <= y < size - 1:
                    img.putpixel((x, y), colour)
    for i in range(1, size - 1):
        img.putpixel((i, 1), celadon)
        img.putpixel((i, size - 2), celadon)
        img.putpixel((1, i), celadon)
        img.putpixel((size - 2, i), celadon)
    for i in range(size):
        img.putpixel((i, 0), edge)
        img.putpixel((i, size - 1), edge)
        img.putpixel((0, i), edge)
        img.putpixel((size - 1, i), edge)


def draw_tray_glaze_dark(img, ramp, rng) -> None:
    """Inner walls and base: the same porcelain, a shade cooler where shadow falls."""
    size = img.width
    body, shade, light = (0xE4, 0xE6, 0xE2, 255), (0xCB, 0xD0, 0xCC, 255), (0xF6, 0xF8, 0xF5, 255)
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), body)
    for _ in range(16):
        cx, cy = rng.randrange(size), rng.randrange(size)
        colour = shade if rng.random() < 0.6 else light
        radius = rng.choice((0, 1, 1, 2))
        for dy in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                if abs(dx) + abs(dy) > radius:
                    continue
                x, y = cx + dx, cy + dy
                if 0 <= x < size and 0 <= y < size:
                    img.putpixel((x, y), colour)


PATTERNS = {
    "brick": draw_brick,
    "roof_tile": draw_roof_tile,
    "plank": draw_plank,
    "plaster": draw_plaster,
    "tile_floor": draw_tile_floor,
    "lattice": draw_lattice,
    "curtain": draw_curtain,
    "stove_face": draw_stove_face,
    "stove_face_lit": draw_stove_face_lit,
    "wood_pile": draw_wood_pile,
    "cabinet_front": draw_cabinet_front,
    "cabinet_open": draw_cabinet_open,
    "counter_top": draw_counter_top,
    "counter_side": draw_counter_side,
    "glass_jar": draw_glass_jar,
    "glass_jar_filled": draw_glass_jar_filled,
    "tray_glaze": draw_tray_glaze,
    "tray_glaze_dark": draw_tray_glaze_dark,
    "jar_side": draw_jar_side,
    "jar_side_filled": draw_jar_side_filled,
    "jar_top": draw_jar_top,
    "jar_top_filled": draw_jar_top_filled,
}


def make_texture(spec: dict, seed: int) -> Image.Image:
    ramp = hexes(spec["ramp"]) if "ramp" in spec else hexes(WOOD[spec["species"]])
    size = spec.get("size", 16)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    PATTERNS[spec["kind"]](img, ramp, random.Random(seed))
    return img


# --- resource writers ---------------------------------------------------------

def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def tex_path(texture: str) -> str:
    return f"{NS}:block/{texture}"


def write_simple_model(name: str, shape: str, texture: str) -> None:
    t = tex_path(texture)
    if shape == "block":
        model = {"parent": "minecraft:block/cube_all", "textures": {"all": t}}
    elif shape == "pillar":
        model = {"parent": "minecraft:block/cube_column", "textures": {"end": t, "side": t}}
    else:  # thin panel hanging on the facing wall
        model = {
            "render_type": "minecraft:cutout",
            "textures": {"particle": t, "0": t},
            "elements": [{
                "from": [0, 0, 0],
                "to": [16, 16, 2],
                "faces": {face: {"uv": [0, 0, 16, 16], "texture": "#0"}
                          for face in ("north", "south", "east", "west", "up", "down")},
            }],
        }
    write_json(RES / "assets" / NS / "models" / "block" / f"{name}.json", model)


def simple_blockstate(name: str, shape: str) -> dict:
    base = {"model": f"{NS}:block/{name}"}
    if shape == "block":
        return {"variants": {"": base}}
    if shape == "pillar":
        return {"variants": {
            "axis=y": base,
            "axis=z": {**base, "x": 90, "uvlock": True},
            "axis=x": {**base, "x": 90, "y": 90, "uvlock": True},
        }}
    return {"variants": {  # thin, by facing
        "facing=north": base,
        "facing=east": {**base, "y": 90},
        "facing=south": {**base, "y": 180},
        "facing=west": {**base, "y": 270},
    }}


def write_derived_models(shape: str, name: str, texture: str) -> None:
    """Stairs / slab / wall models, reusing the base block's texture."""
    t = tex_path(texture)
    textures = {"bottom": t, "top": t, "side": t, "wall": t, "particle": t}
    if shape == "stairs":
        write_json(RES / "assets" / NS / "models" / "block" / f"{name}.json",
                   {"parent": "minecraft:block/stairs", "textures": textures})
        for suffix, parent in (("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            write_json(RES / "assets" / NS / "models" / "block" / f"{name}{suffix}.json",
                       {"parent": f"minecraft:block/{parent}", "textures": textures})
    elif shape == "slab":
        write_json(RES / "assets" / NS / "models" / "block" / f"{name}.json",
                   {"parent": "minecraft:block/slab", "textures": textures})
        write_json(RES / "assets" / NS / "models" / "block" / f"{name}_top.json",
                   {"parent": "minecraft:block/slab_top", "textures": textures})
    else:  # wall
        for suffix, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"),
                               ("_side_tall", "template_wall_side_tall")):
            write_json(RES / "assets" / NS / "models" / "block" / f"{name}{suffix}.json",
                       {"parent": f"minecraft:block/{parent}", "textures": textures})
        write_json(RES / "assets" / NS / "models" / "block" / f"{name}.json",
                   {"parent": "minecraft:block/wall_inventory", "textures": textures})


def blockstate_from_template(shape: str, name: str, base: str) -> dict:
    template = {
        "stairs": TEMPLATES / "oak_stairs.json",
        "slab": TEMPLATES / "oak_slab.json",
        "wall": TEMPLATES / "cobblestone_wall.json",
    }[shape]
    text = template.read_text(encoding="utf-8-sig")
    if shape == "stairs":
        text = (text.replace("minecraft:block/oak_stairs_outer", f"{NS}:block/{name}_outer")
                    .replace("minecraft:block/oak_stairs_inner", f"{NS}:block/{name}_inner")
                    .replace("minecraft:block/oak_stairs", f"{NS}:block/{name}"))
    elif shape == "slab":
        text = (text.replace("minecraft:block/oak_slab_top", f"{NS}:block/{name}_top")
                    .replace("minecraft:block/oak_slab", f"{NS}:block/{name}")
                    .replace("minecraft:block/oak_planks", f"{NS}:block/{base}"))
    else:
        text = (text.replace("minecraft:block/cobblestone_wall_post", f"{NS}:block/{name}_post")
                    .replace("minecraft:block/cobblestone_wall_side_tall", f"{NS}:block/{name}_side_tall")
                    .replace("minecraft:block/cobblestone_wall_side", f"{NS}:block/{name}_side")
                    .replace("minecraft:block/cobblestone", f"{NS}:block/{base}"))
    return json.loads(text)


def loot_table(name: str) -> dict:
    return {"type": "minecraft:block", "pools": [{
        "rolls": 1,
        "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}],
    }]}


def merge_lang(path: Path, entries: dict[str, str]) -> None:
    current = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    current.update(entries)
    write_json(path, current)


# --- functional blocks --------------------------------------------------------

FUNCTIONAL_TEXTURES = {
    "stove_side": {"kind": "brick", "ramp": EARTH},
    "stove_front": {"kind": "stove_face", "ramp": EARTH},
    "stove_front_lit": {"kind": "stove_face_lit", "ramp": EARTH},
    "stove_top": {"kind": "tile_floor", "ramp": FLOOR_TILE},
    "pile_side": {"kind": "plank", "species": "spruce"},
    "pile_top": {"kind": "wood_pile", "species": "spruce"},
}

FACING_Y = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
PILE_HEIGHTS = {1: 4, 2: 8, 3: 12, 4: 16}


def tex(name: str) -> str:
    return f"{NS}:block/{name}"


def cube_model(faces: dict[str, str], particle: str) -> dict:
    return {"parent": "minecraft:block/cube", "textures": {**faces, "particle": particle}}


def build_functional(textures_dir: Path) -> None:
    for seed, (name, spec) in enumerate(sorted(FUNCTIONAL_TEXTURES.items())):
        make_texture(spec, seed=101 + seed * 13).save(textures_dir / f"{name}.png")

    # stove: brick body, a fire mouth on the north face, lit variant swaps that face
    for lit in (False, True):
        write_json(RES / "assets" / NS / "models" / "block" / f"firewood_stove{'_lit' if lit else ''}.json",
                   cube_model({"up": tex("stove_top"), "down": tex("stove_side"),
                               "north": tex("stove_front_lit" if lit else "stove_front"),
                               "south": tex("stove_side"), "east": tex("stove_side"),
                               "west": tex("stove_side")}, tex("stove_side")))
    stove_variants = {}
    for facing, y in FACING_Y:
        for lit in (False, True):
            model = {"model": f"{NS}:block/firewood_stove{'_lit' if lit else ''}"}
            if y:
                model["y"] = y
            stove_variants[f"facing={facing},lit={'true' if lit else 'false'}"] = model
    write_json(RES / "assets" / NS / "blockstates" / "firewood_stove.json", {"variants": stove_variants})

    # firewood pile: one box per stack height, log ends on top
    for count, height in PILE_HEIGHTS.items():
        write_json(RES / "assets" / NS / "models" / "block" / f"firewood_pile_{count}.json", {
            "textures": {"0": tex("pile_side"), "1": tex("pile_top"), "particle": tex("pile_side")},
            "elements": [{
                "from": [0.5, 0, 0.5],
                "to": [15.5, height, 15.5],
                "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#1"},
                          "down": {"uv": [0, 0, 16, 16], "texture": "#0"},
                          "north": {"uv": [0, 16 - height, 16, 16], "texture": "#0"},
                          "south": {"uv": [0, 16 - height, 16, 16], "texture": "#0"},
                          "east": {"uv": [0, 16 - height, 16, 16], "texture": "#0"},
                          "west": {"uv": [0, 16 - height, 16, 16], "texture": "#0"}},
            }],
        })
    pile_variants = {}
    for facing, y in FACING_Y:
        for count in PILE_HEIGHTS:
            model = {"model": f"{NS}:block/firewood_pile_{count}"}
            if y:
                model["y"] = y
            pile_variants[f"facing={facing},count={count}"] = model
    write_json(RES / "assets" / NS / "blockstates" / "firewood_pile.json", {"variants": pile_variants})

    write_json(RES / "assets" / NS / "models" / "item" / "firewood_stove.json",
               {"parent": f"{NS}:block/firewood_stove"})
    write_json(RES / "assets" / NS / "models" / "item" / "firewood_pile.json",
               {"parent": f"{NS}:block/firewood_pile_4"})
    for name in ("firewood_stove", "firewood_pile"):
        write_json(RES / "data" / NS / "loot_table" / "blocks" / f"{name}.json", loot_table(name))

    # range hood was removed: venting turned out to be a gimmick nobody asked for


# --- containers and vat -------------------------------------------------------

CONTAINER_TEXTURES = {
    "vat_side": {"kind": "brick", "ramp": ["#A3814F", "#8A6A47", "#6E523A"]},
    "vat_top": {"kind": "plaster", "ramp": ["#4FA3D1", "#3B82AC", "#2A5F80"]},
    "cupboard_side": {"kind": "plank", "species": "spruce"},
    "cupboard_front": {"kind": "cabinet_front", "species": "oak"},
    "cupboard_front_open": {"kind": "cabinet_open", "species": "oak"},
    "rack_board": {"kind": "plank", "species": "spruce"},
    "counter_top": {"kind": "counter_top", "ramp": ["#B9B4A5", "#93A3A3", "#55636C", "#3F4447"]},
    "counter_side": {"kind": "counter_side", "species": "spruce"},
    "jar_side": {"kind": "jar_side", "ramp": WOOD["oak"]},
    "jar_side_filled": {"kind": "jar_side_filled", "ramp": WOOD["oak"]},
    "jar_top": {"kind": "jar_top", "ramp": WOOD["oak"]},
}

VAT_WATER_HEIGHT = {0: 4, 1: 8, 2: 12, 3: 14}
JAR_SPOTS = [(x, y) for y in (4.0, 10.0) for x in (1.5, 5.5, 9.5, 13.5)]


def box(from_xyz, to_xyz, texture_ref: str, uv=None) -> dict:
    uv = uv or [0, 0, to_xyz[0] - from_xyz[0], to_xyz[1] - from_xyz[1]]
    return {"from": from_xyz, "to": to_xyz,
            "faces": {face: {"uv": uv, "texture": texture_ref}
                      for face in ("north", "south", "east", "west", "up", "down")}}


def build_containers(textures_dir: Path) -> None:
    for seed, (name, spec) in enumerate(sorted(CONTAINER_TEXTURES.items())):
        make_texture(spec, seed=401 + seed * 11).save(textures_dir / f"{name}.png")

    # water vat: clay body with a water surface that rises with the level
    for level, water_y in VAT_WATER_HEIGHT.items():
        water = box([3, water_y, 3], [13, water_y + 1, 13], "#1", uv=[0, 0, 10, 10])
        body = [box([1, 2, 1], [3, 14, 3], "#0"), box([13, 2, 1], [15, 14, 3], "#0"),
                box([1, 2, 13], [3, 14, 15], "#0"), box([13, 2, 13], [15, 14, 15], "#0"),
                box([1, 0, 1], [15, 2, 15], "#0"), water]
        write_json(RES / "assets" / NS / "models" / "block" / f"water_vat_{level}.json",
                   {"textures": {"0": tex("vat_side"), "1": tex("vat_top"),
                                 "particle": tex("vat_side")}, "elements": body})
    vat_variants = {}
    for facing, y in FACING_Y:
        for level in VAT_WATER_HEIGHT:
            model = {"model": f"{NS}:block/water_vat_{level}"}
            if y:
                model["y"] = y
            vat_variants[f"facing={facing},level={level}"] = model
    write_json(RES / "assets" / NS / "blockstates" / "water_vat.json", {"variants": vat_variants})

    # cupboard: closed shows the doors, open shows a dark shelf instead
    for opened in (False, True):
        write_json(RES / "assets" / NS / "models" / "block" / f"cupboard{'_open' if opened else ''}.json",
                   cube_model({"up": tex("cupboard_side"), "down": tex("cupboard_side"),
                               "north": tex("cupboard_front_open" if opened else "cupboard_front"),
                               "south": tex("cupboard_side"), "east": tex("cupboard_side"),
                               "west": tex("cupboard_side")}, tex("cupboard_side")))
    cupboard_variants = {}
    for facing, y in FACING_Y:
        for opened in (False, True):
            model = {"model": f"{NS}:block/cupboard{'_open' if opened else ''}"}
            if y:
                model["y"] = y
            cupboard_variants[f"facing={facing},open={'true' if opened else 'false'}"] = model
    write_json(RES / "assets" / NS / "blockstates" / "cupboard.json", {"variants": cupboard_variants})

    # spice jars: four aligned jars, one per corner, no rotation at all.
    # Presence is per corner so the blockstate can be a multipart; the contents are shown
    # by a block entity renderer, not by the model.
    jar_size = 5.0
    jar_height = 7.0

TRAY_TEXTURES = {
    "tray_glaze": {"kind": "tray_glaze", "ramp": ["#F2F2EC", "#DCDCD4", "#FFFFFF", "#A8CFC8"]},
    "tray_glaze_dark": {"kind": "tray_glaze_dark", "ramp": ["#E4E6E2", "#CBD0CC", "#F6F8F5", "#A8CFC8"]},
}


def _tray_elements(wall_height: int, inset: int = 1) -> list:
    """A dish: thin base, walls, and a cross divider split into three so nothing interpenetrates."""
    lo, hi = inset, 16 - inset
    top = 1 + wall_height
    mid_lo, mid_hi = 8 - 1, 8 + 1
    return [
        box([lo, 0, lo], [hi, 1, hi], "#0"),
        box([lo, 1, lo], [hi, top, lo + 1], "#1"),
        box([lo, 1, hi - 1], [hi, top, hi], "#1"),
        box([lo, 1, lo + 1], [lo + 1, top, hi - 1], "#1"),
        box([hi - 1, 1, lo + 1], [hi, top, hi - 1], "#1"),
        box([mid_lo, 1, lo + 1], [mid_hi, top, hi - 1], "#1"),
        box([lo + 1, 1, mid_lo], [mid_lo, top, mid_hi], "#1"),
        box([mid_hi, 1, mid_lo], [hi - 1, top, mid_hi], "#1"),
    ]


def build_tray(textures_dir: Path) -> None:
    for seed, (name, spec) in enumerate(sorted(TRAY_TEXTURES.items())):
        make_texture(spec, seed=701 + seed * 13).save(textures_dir / f"{name}.png")

    textures = {"0": tex("tray_glaze_dark"), "1": tex("tray_glaze"), "particle": tex("tray_glaze")}
    write_json(RES / "assets" / NS / "models" / "block" / "seasoning_tray.json", {
        "render_type": "minecraft:cutout",
        "textures": textures,
        "elements": _tray_elements(2),
    })
    # the item form is much taller than the placed one, and shown at a steeper angle: at inventory
    # size a low dish reads as a flat bar whatever the lighting does
    write_json(RES / "assets" / NS / "models" / "item" / "seasoning_tray.json", {
        "textures": textures,
        "elements": _tray_elements(8, inset=0),
        "display": {
            # the dish is nine pixels tall against a block model's sixteen, so its centre sits low in
            # the slot; the translation lifts it, and in this context positive y is up
            "gui": {"rotation": [30, 225, 0], "translation": [0, 5, 0], "scale": [0.65, 0.65, 0.65]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "head": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, 90, 0], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
        },
    })
    variants = {}
    for facing, y in FACING_Y:
        model = {"model": f"{NS}:block/seasoning_tray"}
        if y:
            model["y"] = y
        variants[f"facing={facing}"] = model
    write_json(RES / "assets" / NS / "blockstates" / "seasoning_tray.json", {"variants": variants})
    # the block drops itself with its contents; an empty loot pool avoids a second, empty drop
    write_json(RES / "data" / NS / "loot_table" / "blocks" / "seasoning_tray.json",
               {"type": "minecraft:block", "pools": []})
    write_json(RES / "data" / NS / "recipe" / "seasoning_tray.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["WWW", "W W", "WWW"],
        "key": {"W": {"item": "minecraft:spruce_planks"}},
        "result": {"id": f"{NS}:seasoning_tray", "count": 1},
    })


# --- main ---------------------------------------------------------------------

def main() -> None:
    textures_dir = RES / "assets" / NS / "textures" / "block"
    textures_dir.mkdir(parents=True, exist_ok=True)

    for index, (name, _zh, _en, shape, spec) in enumerate(DECOR):
        if spec is not None:
            make_texture(spec, seed=index * 17 + 7).save(textures_dir / f"{name}.png")

        texture = name if spec is not None else BASE_OF[name]
        if shape in ("stairs", "slab", "wall"):
            write_derived_models(shape, name, texture)
            write_json(RES / "assets" / NS / "blockstates" / f"{name}.json",
                       blockstate_from_template(shape, name, texture))
        else:
            write_simple_model(name, shape, texture)
            write_json(RES / "assets" / NS / "blockstates" / f"{name}.json",
                       simple_blockstate(name, shape))

        write_json(RES / "assets" / NS / "models" / "item" / f"{name}.json",
                   {"parent": f"{NS}:block/{name}"})
        write_json(RES / "data" / NS / "loot_table" / "blocks" / f"{name}.json", loot_table(name))

    build_functional(textures_dir)
    build_containers(textures_dir)
    build_tray(textures_dir)

    write_json(RES / "assets" / NS / "lang" / "zh_cn.json",
               {f"block.{NS}.{n}": zh for n, zh, _, _, _ in DECOR}
               | {f"itemGroup.{NS}.kitchen": "森罗物语：家什"}
               | {"block." + NS + ".firewood_stove": "柴火灶",
                  "block." + NS + ".firewood_pile": "柴火堆",
                  f"tier.{NS}.low": "文火",
                  f"tier.{NS}.mid": "中火",
                  f"tier.{NS}.high": "猛火",
                  f"state.{NS}.tier": "火力：%s",
                  f"state.{NS}.status": "%s",
                  f"state.{NS}.fuel_left": "剩余燃料 %s 秒（%s）",
                  f"state.{NS}.need_fuel": "灶里没有柴火",
                  f"state.{NS}.pile_take": "取出一根柴",
                  "block." + NS + ".water_vat": "水缸",
                  "block." + NS + ".cupboard": "碗柜",
                  "block." + NS + ".seasoning_tray": "调味盘",
                  f"state.{NS}.tray_stored": "存入了 %s 个",
                  f"state.{NS}.tray_empty": "这个格子是空的",
                  f"state.{NS}.tray_wrong_kind": "这个格子已经装着别的调料了",
                  f"state.{NS}.tray_accepts_seasoning_only": "调味盘只放调料",
                  f"state.{NS}.tray_full": "这个格子放不下了",
                  f"state.{NS}.tray_returned": "把 %s 放了回去",
                  f"state.{NS}.tray_scooped": "舀起了一份 %s",
                  f"state.{NS}.tray_poured": "把 %s 下锅了",
                  f"state.{NS}.tray_wok_refused": "锅还收不了 %s",
                  "block." + NS + ".kitchen_counter": "料理台",
                  f"state.{NS}.storage_full": "已经塞满了",
                  f"state.{NS}.storage_empty": "里面是空的",
                  f"state.{NS}.storage_rejects": "这个放不进去",
                  f"state.{NS}.vat_empty": "缸里没水了",
                  f"state.{NS}.vat_hint": "拿空桶或空瓶来打水",
                  f"state.{NS}.jar_missing": "这个角上没有罐子",
                  f"state.{NS}.jar_taken": "这个角上已经有罐子了",
                  f"state.{NS}.jar_scooped": "舀出了 %s",
                  f"state.{NS}.jar_stored": "存入了 %s 个",
                  f"state.{NS}.jar_scooped_on_shovel": "锅铲上沾上了 %s",
                  f"state.{NS}.jar_returned": "把 %s 倒回了罐子",
                  f"state.{NS}.jar_poured": "把 %s 下锅了",
                  f"state.{NS}.jar_wok_refused": "锅里放不下 %s（先下油再炒）",
                  f"state.{NS}.jar_wrong_kind": "这个罐里已经装着别的调料了"})
    write_json(RES / "assets" / NS / "lang" / "en_us.json",
               {f"block.{NS}.{n}": en for n, _, en, _, _ in DECOR}
               | {f"itemGroup.{NS}.kitchen": "Kaleidoscope Kitchenware"}
               | {"block." + NS + ".firewood_stove": "Firewood Stove",
                  "block." + NS + ".firewood_pile": "Firewood Pile",
                  f"tier.{NS}.low": "Low heat",
                  f"tier.{NS}.mid": "Medium heat",
                  f"tier.{NS}.high": "High heat",
                  f"state.{NS}.tier": "Heat: %s",
                  f"state.{NS}.status": "%s",
                  f"state.{NS}.fuel_left": "Fuel left: %s s (%s)",
                  f"state.{NS}.need_fuel": "No firewood in the stove",
                  f"state.{NS}.pile_take": "Took one piece of firewood",
                  "block." + NS + ".water_vat": "Water Vat",
                  "block." + NS + ".cupboard": "Cupboard",
                  "block." + NS + ".seasoning_tray": "Seasoning Tray",
                  f"state.{NS}.tray_stored": "Stored %s",
                  f"state.{NS}.tray_empty": "This compartment is empty",
                  f"state.{NS}.tray_wrong_kind": "This compartment already holds another seasoning",
                  f"state.{NS}.tray_accepts_seasoning_only": "The tray only takes seasoning",
                  f"state.{NS}.tray_full": "This compartment is full",
                  f"state.{NS}.tray_returned": "Put back %s",
                  f"state.{NS}.tray_scooped": "Scooped up %s",
                  f"state.{NS}.tray_poured": "Added %s to the wok",
                  f"state.{NS}.tray_wok_refused": "The wok will not take %s yet",
                  "block." + NS + ".kitchen_counter": "Kitchen Counter",
                  f"state.{NS}.storage_full": "It is full",
                  f"state.{NS}.storage_empty": "It is empty",
                  f"state.{NS}.storage_rejects": "That does not belong in here",
                  f"state.{NS}.vat_empty": "The vat is empty",
                  f"state.{NS}.vat_hint": "Bring a bucket or a bottle",
                  f"state.{NS}.jar_missing": "No jar in this corner",
                  f"state.{NS}.jar_taken": "This corner already has a jar",
                  f"state.{NS}.jar_scooped": "Scooped out %s",
                  f"state.{NS}.jar_stored": "Stored %s",
                  f"state.{NS}.jar_scooped_on_shovel": "Picked up %s on the shovel",
                  f"state.{NS}.jar_returned": "Tipped %s back into the jar",
                  f"state.{NS}.jar_poured": "Added %s to the wok",
                  f"state.{NS}.jar_wok_refused": "The wok will not take %s yet (oil it first)",
                  f"state.{NS}.jar_wrong_kind": "This jar already holds a different seasoning"})

    print(f"generated {len(DECOR)} decorative blocks and 4 functional blocks into {RES}")


if __name__ == "__main__":
    main()
