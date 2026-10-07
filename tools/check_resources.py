#!/usr/bin/env python3
"""Self-check for generated resources: every JSON parses, every block has its files.

Run after tools/gen_decor.py:

    python tools/check_resources.py

Exits non-zero on the first problem so it can gate a build.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from gen_decor import DECOR, NS  # noqa: E402

RES = ROOT / "src" / "main" / "resources"
problems: list[str] = []


def require(path: Path) -> None:
    if not path.exists():
        problems.append(f"missing: {path.relative_to(ROOT)}")


for path in RES.rglob("*.json"):
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001
        problems.append(f"bad json {path.relative_to(ROOT)}: {exc}")

for name, zh, en, shape, _spec in DECOR:
    require(RES / "assets" / NS / "blockstates" / f"{name}.json")
    require(RES / "assets" / NS / "models" / "block" / f"{name}.json")
    require(RES / "assets" / NS / "models" / "item" / f"{name}.json")
    require(RES / "data" / NS / "loot_table" / "blocks" / f"{name}.json")
    if shape in ("stairs", "slab") or shape == "block":
        pass  # texture requirement handled below
    texture = name if _spec is not None else {"blue_brick_stairs": "blue_brick",
                                              "blue_brick_slab": "blue_brick",
                                              "blue_brick_wall": "blue_brick",
                                              "blue_roof_tile_stairs": "blue_roof_tile",
                                              "blue_roof_tile_slab": "blue_roof_tile"}[name]
    require(RES / "assets" / NS / "textures" / "block" / f"{texture}.png")

zh_lang = json.loads((RES / "assets" / NS / "lang" / "zh_cn.json").read_text(encoding="utf-8"))
en_lang = json.loads((RES / "assets" / NS / "lang" / "en_us.json").read_text(encoding="utf-8"))

for name, texture in (("firewood_stove", "stove_side"),):
    require(RES / "assets" / NS / "blockstates" / f"{name}.json")
    require(RES / "assets" / NS / "models" / "item" / f"{name}.json")
    require(RES / "data" / NS / "loot_table" / "blocks" / f"{name}.json")
    require(RES / "assets" / NS / "textures" / "block" / f"{texture}.png")
    for key in (f"block.{NS}.{name}", f"tier.{NS}.low", f"state.{NS}.need_fuel"):
        if key not in zh_lang or key not in en_lang:
            problems.append(f"missing lang key {key}")
for name, zh, en, _shape, _spec in DECOR:
    key = f"block.{NS}.{name}"
    if zh_lang.get(key) != zh:
        problems.append(f"zh_cn mismatch for {key}: {zh_lang.get(key)!r} != {zh!r}")
    if en_lang.get(key) != en:
        problems.append(f"en_us mismatch for {key}: {en_lang.get(key)!r} != {en!r}")

if problems:
    print("FAILED")
    for problem in problems:
        print(" -", problem)
    sys.exit(1)

# every item/block model must point at a model that actually exists
models_dir = RES / "assets" / NS / "models"
ALLOWED_ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0}


def bounds(element: dict) -> tuple:
    """3D bounding box (x0, y0, z0, x1, y1, z1) after the element's own y rotation."""
    x0, y0, z0 = element["from"]
    x1, y1, z1 = element["to"]
    rotation = element.get("rotation")
    if not isinstance(rotation, dict) or rotation.get("axis") != "y":
        return min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1)
    angle = math.radians(float(rotation["angle"]))
    ox, _, oz = rotation["origin"]
    xs, zs = [], []
    for cx, cz in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        dx, dz = cx - ox, cz - oz
        xs.append(ox + dx * math.cos(angle) + dz * math.sin(angle))
        zs.append(oz - dx * math.sin(angle) + dz * math.cos(angle))
    return min(xs), min(y0, y1), min(zs), max(xs), max(y0, y1), max(zs)


for folder in ("item", "block"):
    for model_file in (models_dir / folder).glob("*.json"):
        body = json.loads(model_file.read_text(encoding="utf-8"))
        parent = body.get("parent", "")
        if parent.startswith(f"{NS}:block/"):
            target = models_dir / "block" / (parent.split(":", 1)[1].split("/", 1)[1] + ".json")
            if not target.exists():
                problems.append(f"{folder}/{model_file.name} points at missing model {parent}")
        boxes = []
        for element in body.get("elements", []):
            rotation = element.get("rotation")
            if isinstance(rotation, dict) and float(rotation.get("angle", 0)) not in ALLOWED_ANGLES:
                problems.append(f"{folder}/{model_file.name} uses illegal element rotation "
                                f"{rotation.get('angle')} (only -45/-22.5/0/22.5/45 are allowed)")
            box = bounds(element)
            if box[0] < -0.01 or box[2] < -0.01 or box[3] > 16.01 or box[5] > 16.01:
                problems.append(f"{folder}/{model_file.name} has an element sticking out of the "
                                f"block in x/z: {tuple(round(v, 2) for v in box)}")
            boxes.append(box)
        # elements may stack (a shelf on a rim) but must never interpenetrate in all three axes
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                a, b = boxes[i], boxes[j]
                if (a[0] < b[3] - 0.01 and b[0] < a[3] - 0.01
                        and a[1] < b[4] - 0.01 and b[1] < a[4] - 0.01
                        and a[2] < b[5] - 0.01 and b[2] < a[5] - 0.01):
                    problems.append(f"{folder}/{model_file.name} has interpenetrating elements "
                                    f"{tuple(round(v, 2) for v in a)} and {tuple(round(v, 2) for v in b)}")

if problems:
    print("FAILED (model references)")
    for problem in problems:
        print(" -", problem)
    sys.exit(1)

print(f"OK: {len(DECOR)} blocks, all json parsed, textures and lang entries present")
