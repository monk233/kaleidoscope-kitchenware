#!/usr/bin/env python3
"""Isometric preview of a block model, so the geometry and textures can be eyeballed without
launching the game.

    python tools/preview_model.py out.png <model.json> [x_offset] [<model.json> [x_offset]] ...

The model is turned so its north face looks at the viewer, which is the face a stove's fire mouth is
painted on. Each visible face is texture mapped through the same affine projection the geometry
uses, so a wrong uv or a texture on the wrong face is obvious before the game ever starts.
"""
from __future__ import annotations

import json
import math
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets"
SCALE = int(os.environ.get("PREVIEW_SCALE", "14"))  # pixels per model unit
COS30, SIN30 = math.cos(math.radians(30)), math.sin(math.radians(30))
FACES = {
    "north": (0, 0, -1),
    "south": (0, 0, 1),
    "east": (1, 0, 0),
    "west": (-1, 0, 0),
    "up": (0, 1, 0),
    "down": (0, -1, 0),
}
# what the camera can see once the model is turned to face it: the north face, the up face, and
# the west side (the turn flips x and z, so the camera ends up at +x/+y/+z of the rotated model)
VISIBLE = {"north", "west", "up"}


def project(x: float, y: float, z: float) -> tuple[float, float]:
    """Turn the model so north faces the viewer, then axonometric projection (y up on screen)."""
    x, z = 16 - x, 16 - z
    return (x - z) * COS30 * SCALE, ((x + z) * SIN30 - y) * SCALE


def face_corners(box, normal) -> list[tuple[float, float, float]]:
    x0, y0, z0 = box["from"]
    x1, y1, z1 = box["to"]
    nx, ny, nz = normal
    if nz:
        z = z0 if nz < 0 else z1
        return [(x0, y1, z), (x1, y1, z), (x1, y0, z), (x0, y0, z)]
    if nx:
        x = x0 if nx < 0 else x1
        return [(x, y1, z1), (x, y1, z0), (x, y0, z0), (x, y0, z1)]
    y = y0 if ny < 0 else y1
    return [(x0, y, z1), (x1, y, z1), (x1, y, z0), (x0, y, z0)]


def texture(ref: str, model: dict) -> Image.Image:
    while ref.startswith("#"):
        ref = model.get("textures", {})[ref[1:]]
    namespace, path = ref.split(":", 1)
    file = ASSETS / namespace / "textures" / f"{path}.png"
    if not file.exists():
        print(f"missing texture {ref}", file=sys.stderr)
        return Image.new("RGBA", (1, 1), (255, 0, 255, 255))
    return Image.open(file).convert("RGBA")


def render(models: list[tuple[dict, float, float]], out: Path) -> None:
    quads = []
    for model, offset_x, offset_y in models:
        for element in model.get("elements", []):
            box = {"from": [element["from"][0] + offset_x, element["from"][1] + offset_y, element["from"][2]],
                   "to": [element["to"][0] + offset_x, element["to"][1] + offset_y, element["to"][2]]}
            for face, normal in FACES.items():
                spec = element.get("faces", {}).get(face)
                if spec is None or face not in VISIBLE:
                    continue
                corners = face_corners(box, normal)
                screen = [project(*corner) for corner in corners]
                # camera space depth along (1, 1, 1) after the turn; larger is nearer
                depth = sum((16 - x) + y + (16 - z) for x, y, z in corners) / 4
                quads.append((depth, screen, spec, texture(spec["texture"], model)))
    quads.sort(key=lambda quad: quad[0])

    xs = [point[0] for _, screen, _, _ in quads for point in screen]
    ys = [point[1] for _, screen, _, _ in quads for point in screen]
    pad = 4
    left, top = min(xs) - pad, min(ys) - pad
    width, height = int(max(xs) - left + pad), int(max(ys) - top + pad)
    canvas = Image.new("RGBA", (width, height), (28, 28, 32, 255))

    for _, screen, spec, image in quads:
        p0, p1, p2, p3 = [(x - left, y - top) for x, y in screen]
        # the projection is affine, so the quad is a parallelogram: map (s, t) -> screen
        basis = np.array([[p1[0] - p0[0], p3[0] - p0[0]], [p1[1] - p0[1], p3[1] - p0[1]]], dtype=float)
        if abs(np.linalg.det(basis)) < 1e-6:
            continue
        inverse = np.linalg.inv(basis)
        x0, x1 = int(math.floor(min(p[0] for p in (p0, p1, p2, p3)))), int(math.ceil(max(p[0] for p in (p0, p1, p2, p3))))
        y0, y1 = int(math.floor(min(p[1] for p in (p0, p1, p2, p3)))), int(math.ceil(max(p[1] for p in (p0, p1, p2, p3))))
        x0, y0 = max(x0, 0), max(y0, 0)
        x1, y1 = min(x1, width), min(y1, height)
        if x1 <= x0 or y1 <= y0:
            continue
        grid_x, grid_y = np.meshgrid(np.arange(x0, x1) + 0.5 - p0[0], np.arange(y0, y1) + 0.5 - p0[1])
        s = inverse[0, 0] * grid_x + inverse[0, 1] * grid_y
        t = inverse[1, 0] * grid_x + inverse[1, 1] * grid_y
        inside = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
        if not inside.any():
            continue
        u0, v0, u1, v1 = spec.get("uv", [0, 0, 16, 16])
        pixels = np.asarray(image, dtype=np.uint8)
        tex_x = np.clip(((u0 + s * (u1 - u0)) / 16 * image.width).astype(int), 0, image.width - 1)
        tex_y = np.clip(((v0 + t * (v1 - v0)) / 16 * image.height).astype(int), 0, image.height - 1)
        patch = np.array(canvas, dtype=np.uint8)
        target = patch[y0:y1, x0:x1]
        sampled = pixels[tex_y, tex_x]
        target[inside] = sampled[inside]
        canvas = Image.fromarray(patch, mode="RGBA")

    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out)
    print(f"wrote {out} ({width}x{height}, {len(quads)} faces)")


def main() -> None:
    if len(sys.argv) < 3:
        print(__doc__)
        raise SystemExit(2)
    out = Path(sys.argv[1])
    args = sys.argv[2:]
    models = []
    index = 0
    while index < len(args):
        model = json.loads(Path(args[index]).read_text(encoding="utf-8"))
        offset_x = offset_y = 0.0
        if index + 1 < len(args) and not args[index + 1].endswith(".json"):
            parts = args[index + 1].split(",")
            offset_x = float(parts[0])
            offset_y = float(parts[1]) if len(parts) > 1 else 0.0
            index += 1
        index += 1
        models.append((model, offset_x, offset_y))
    render(models, out)


if __name__ == "__main__":
    main()
