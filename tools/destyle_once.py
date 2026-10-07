"""One-off: replace the noisy textures with structured ones, and detail the rack and vat models."""

import pathlib
import re

GEN = pathlib.Path(__file__).resolve().parent.parent / "tools" / "gen_decor.py"
text = GEN.read_text(encoding="utf-8")

# --- tray: flat china white, one rim line, one inner shadow line. Nothing scattered ---
tray = '''def draw_tray_glaze(img, ramp, rng) -> None:
    """Porcelain: flat china white, one shared-outline rim, one inner shadow line."""
    size = img.width
    body, shade, edge = (0xEF, 0xEE, 0xE2, 255), (0xDA, 0xD7, 0xC3, 255), (0x5E, 0x37, 0x23, 255)
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), body)
    band = max(2, size // 12)
    for i in range(band, size - band):
        img.putpixel((i, band), shade)
        img.putpixel((i, size - band - 1), shade)
        img.putpixel((band, i), shade)
        img.putpixel((size - band - 1, i), shade)
    for i in range(size):
        img.putpixel((i, 0), edge)
        img.putpixel((i, size - 1), edge)
        img.putpixel((0, i), edge)
        img.putpixel((size - 1, i), edge)
'''

tray_dark = '''def draw_tray_glaze_dark(img, ramp, rng) -> None:
    """Inner walls: one step down from the china white, with a single seam line."""
    size = img.width
    body, shade = (0xDA, 0xD7, 0xC3, 255), (0xC2, 0xBF, 0xAC, 255)
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), body)
    seam = size // 2
    for i in range(size):
        img.putpixel((i, seam), shade)
'''

vat = '''def draw_vat_clay(img, ramp, rng) -> None:
    """A thrown pot: even vertical throwing marks, a lit band under the rim, a shadowed belly.
    Regular marks rather than scattered specks - the series reads as structure, not noise."""
    size = img.width
    body, dark, light, edge = (0xA3, 0x81, 0x4F, 255), (0x8A, 0x6A, 0x47, 255), \\
        (0xB8, 0x95, 0x5E, 255), (0x5E, 0x37, 0x23, 255)
    step = max(1, size // 16)
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), body)
    for x in range(0, size, step * 2):
        for y in range(size):
            img.putpixel((x, y), dark)
    for x in range(size):
        for y in range(step, step * 2):
            img.putpixel((x, y), light)
        for y in range(size - step * 3, size - step * 2):
            img.putpixel((x, y), dark)
    for i in range(size):
        img.putpixel((i, 0), edge)
        img.putpixel((i, size - 1), edge)
        img.putpixel((0, i), edge)
        img.putpixel((size - 1, i), edge)
'''

text = re.sub(r"def draw_tray_glaze\(img, ramp, rng\) -> None:.*?(?=\ndef )", tray, text, flags=re.S)
text = re.sub(r"def draw_tray_glaze_dark\(img, ramp, rng\) -> None:.*?(?=\ndef |\nPATTERNS)", tray_dark, text, flags=re.S)
text = re.sub(r"def draw_vat_clay\(img, ramp, rng\) -> None:.*?(?=\ndef )", vat, text, flags=re.S)

# --- rack: give the shelves slats, so the frame carries some detail ---
old_rack = re.search(r"    # two trays, four posts and two rails.*?\n    \]", text, re.S)
if old_rack:
    new_rack = '''    # two trays, four posts, two rails, and slats across each tray so the frame has detail
    elements = [
        box([2, 0, 2], [14, 1, 12], "#0"),
        box([2, 7, 2], [14, 8, 12], "#0"),
        box([2, 14, 12], [14, 15, 13], "#1"),
        box([2, 1, 12], [14, 2, 13], "#1"),
        box([1, 0, 1], [2, 16, 2], "#1"),
        box([14, 0, 1], [15, 16, 2], "#1"),
        box([1, 0, 12], [2, 16, 13], "#1"),
        box([14, 0, 12], [15, 16, 13], "#1"),
    ]
    for shelf_y in (1, 8):
        for x in range(3, 14, 3):
            elements.append(box([x, shelf_y, 3], [x + 1, shelf_y + 1, 11], "#1"))
'''
    text = text[:old_rack.start()] + new_rack + text[old_rack.end():]

# --- vat model: more rings, gently tapering, so the silhouette reads round ---
old_vat = re.search(r"    # water vat: hollow like a cauldron.*?\n(?:.*?\n)*?    for level, water_y in VAT_WATER_HEIGHT\.items\(\):", text)
if old_vat:
    new_vat = '''    # water vat: seven rings tapering in and out, which is as round as block model elements
    # get; a hollow body like a cauldron, with the level deciding how high the water sits
    vat_rings = [(0.0, 1.0, 3.0), (1.0, 2.5, 4.2), (2.5, 5.0, 5.0), (5.0, 8.0, 5.6),
                 (8.0, 11.0, 5.2), (11.0, 13.5, 4.6), (13.5, 15.0, 4.2)]
    for level, water_y in VAT_WATER_HEIGHT.items():
        elements = []
        for y0, y1, half in vat_rings:
            elements.append(box([8 - half, y0, 8 - half], [8 + half, y1, 8 + half], "#0"))
        if level > 0:
            elements.append({
                "from": [2.4, water_y, 2.4], "to": [13.6, water_y + 0.4, 13.6],
                "faces": {face: {"uv": [0, 0, 11, 11], "texture": "#water", "tintindex": 0}
                          for face in ("north", "south", "east", "west", "up", "down")},
            })
        write_json(RES / "assets" / NS / "models" / "block" / f"water_vat_{level}.json",
                   {"render_type": "minecraft:cutout",
                    "textures": {"0": tex("vat_side"), "water": WATER,
                                 "particle": tex("vat_side")}, "elements": elements})

'''
    text = text[:old_vat.start()] + new_vat + text[old_vat.end():]

GEN.write_text(text, encoding="utf-8")
