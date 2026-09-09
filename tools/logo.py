"""The app mark: a division sign chalked on a slate.

One definition of the geometry, emitted twice. The launcher icon's layers are
vector (`ic_launcher_{background,foreground,monochrome}.xml`, plus
`ic_app_mark.xml` for the in-app mark), and everything that has to be a bitmap
is rendered from the same strokes: the legacy density mipmaps. A change to the
mark therefore cannot leave one form showing the old one.

Three things the icon has to respect, all handled below:

- Only the centre 72 of the 108 canvas is ever visible, and launcher masks
  differ, so every stroke stays inside the centred circle of diameter 66.
  Asserted on every run.
- `VectorDrawable` has no text, no filters and no blur. The sign is drawn as
  chalk strokes rather than set in a font, and the slate's smudges are radial
  gradients fading to transparent rather than blurred shapes.
- Chalk is uneven. The bar is a wobbled curve rather than a straight line, and
  its opacity varies along its length, which a gradient on `strokeColor`
  expresses and the rasteriser reproduces from the same stops.

Run from the repo root:  python tools/logo.py
"""
import math
import os

from PIL import Image, ImageChops, ImageDraw

VIEWPORT = 108.0
SAFE_CENTRE = 54.0
SAFE_RADIUS = 33.0

# --- the slate ---------------------------------------------------------------

SLATE_TOP = (0x3A, 0x42, 0x3F)
SLATE_BOTTOM = (0x1B, 0x21, 0x1F)

# Wiped chalk dust. (cx, cy, radius, alpha at the centre) in canvas units;
# each fades to nothing at its edge, which is what makes it read as a smudge
# rather than a shape.
SMUDGES = [
    (34.0, 30.0, 30.0, 0.16),
    (78.0, 62.0, 26.0, 0.13),
    (52.0, 88.0, 34.0, 0.09),
    (86.0, 18.0, 18.0, 0.08),
]

# --- the mark ----------------------------------------------------------------

CHALK = (0xF1, 0xF6, 0xF3)

# Where the bar's opacity gradient runs, and how it varies: chalk presses
# harder in some places than others, and runs out towards the ends.
BAR_FADE = {
    "from": (23.0, 54.0),
    "to": (85.0, 54.0),
    "stops": [(0.0, 0.42), (0.14, 0.92), (0.38, 0.70), (0.58, 1.0), (0.82, 0.76), (1.0, 0.38)],
}

# Nothing here is straight or symmetrical: the bar rises slightly to the right
# and waves, and the dots sit a little off the same vertical.
MARK = [
    {
        "d": [
            ("M", (28.5, 56.6)),
            ("Q", (41.0, 53.6), (54.0, 54.7)),
            ("Q", (67.0, 55.8), (79.5, 52.4)),
        ],
        "w": 9.0,
        "fade": BAR_FADE,
    },
    # A dot is a stroke shorter than it is wide, so the round caps close it
    # into a blob. VectorDrawable will not reliably render a zero-length path.
    {"d": [("M", (52.8, 38.2)), ("L", (53.7, 38.9))], "w": 9.4, "alpha": 0.94},
    {"d": [("M", (54.9, 69.9)), ("L", (55.5, 70.5))], "w": 8.6, "alpha": 0.82},
]

# --- geometry ----------------------------------------------------------------


def _flatten(sub, steps=48):
    """Bezier subpath to a polyline, for rasterising and for measuring."""
    pts, cur = [], None
    for seg in sub:
        if seg[0] in ("M", "L"):
            cur = seg[1]
            pts.append(cur)
        elif seg[0] == "Q":
            (cx, cy), (ex, ey) = seg[1], seg[2]
            sx, sy = cur
            for i in range(1, steps + 1):
                t = i / steps
                u = 1 - t
                pts.append((u * u * sx + 2 * u * t * cx + t * t * ex,
                            u * u * sy + 2 * u * t * cy + t * t * ey))
            cur = (ex, ey)
    return pts


def _bounds():
    xs, ys = [], []
    for st in MARK:
        half = st["w"] / 2
        for x, y in _flatten(st["d"]):
            xs += [x - half, x + half]
            ys += [y - half, y + half]
    return min(xs), min(ys), max(xs), max(ys)


def _assert_inside_safe_circle():
    worst = 0.0
    for st in MARK:
        half = st["w"] / 2
        for x, y in _flatten(st["d"]):
            worst = max(worst, math.hypot(x - SAFE_CENTRE, y - SAFE_CENTRE) + half)
    assert worst <= SAFE_RADIUS, f"mark reaches {worst:.2f} of {SAFE_RADIUS}"
    return worst


def _translate(sub, dx, dy):
    return [(seg[0], *[(x + dx, y + dy) for x, y in seg[1:]]) for seg in sub]


# --- vector output -----------------------------------------------------------


def _path_data(sub):
    out = []
    for seg in sub:
        if seg[0] == "M":
            out.append(f"M{seg[1][0]:g},{seg[1][1]:g}")
        elif seg[0] == "L":
            out.append(f"L{seg[1][0]:g},{seg[1][1]:g}")
        elif seg[0] == "Q":
            out.append(f"Q{seg[1][0]:g},{seg[1][1]:g} {seg[2][0]:g},{seg[2][1]:g}")
    return " ".join(out)


def _hex(rgb, alpha=1.0):
    return "#{:02X}{:02X}{:02X}{:02X}".format(round(alpha * 255), *rgb)


def _stroke_xml(st, rgb, dx=0.0, dy=0.0, flat=False):
    """One chalk stroke. `flat` drops the fade, for the themed-icon layer."""
    d = _path_data(_translate(st["d"], dx, dy))
    common = (f'        android:strokeWidth="{st["w"]:g}"\n'
              '        android:strokeLineCap="round"\n'
              '        android:strokeLineJoin="round"')
    fade = st.get("fade")
    if fade and not flat:
        stops = "\n".join(
            f'                <item android:color="{_hex(rgb, a)}" android:offset="{o:g}" />'
            for o, a in fade["stops"]
        )
        return f'''    <path
        android:pathData="{d}"
{common}>
        <aapt:attr name="android:strokeColor">
            <gradient
                android:startX="{fade["from"][0] + dx:g}"
                android:startY="{fade["from"][1] + dy:g}"
                android:endX="{fade["to"][0] + dx:g}"
                android:endY="{fade["to"][1] + dy:g}"
                android:type="linear">
{stops}
            </gradient>
        </aapt:attr>
    </path>'''
    alpha = 1.0 if flat else st.get("alpha", 1.0)
    return f'''    <path
        android:pathData="{d}"
        android:strokeColor="{_hex(rgb, alpha)}"
{common} />'''


def vector_mark(rgb, tint=None, flat=False):
    paths = "\n".join(_stroke_xml(st, rgb, flat=flat) for st in MARK)
    tint_attr = f'\n    android:tint="{tint}"' if tint else ""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/logo.py. Do not hand-edit. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108"{tint_attr}>
{paths}
</vector>
'''


def vector_mark_cropped(rgb=(255, 255, 255)):
    """The mark with the mask margin trimmed off, for drawing inside the app."""
    x0, y0, x1, y1 = _bounds()
    w, h = x1 - x0, y1 - y0
    paths = "\n".join(_stroke_xml(st, rgb, dx=-x0, dy=-y0) for st in MARK)
    return w / h, f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/logo.py. Do not hand-edit. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="{w:g}dp"
    android:height="{h:g}dp"
    android:viewportWidth="{w:g}"
    android:viewportHeight="{h:g}">
{paths}
</vector>
'''


def vector_background():
    smudges = "\n".join(
        f'''    <path android:pathData="M{cx - r:g},{cy - r:g}h{2 * r:g}v{2 * r:g}h-{2 * r:g}z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:centerX="{cx:g}"
                android:centerY="{cy:g}"
                android:gradientRadius="{r:g}"
                android:type="radial">
                <item android:color="{_hex((255, 255, 255), a)}" android:offset="0.0" />
                <item android:color="{_hex((255, 255, 255), 0.0)}" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>'''
        for cx, cy, r, a in SMUDGES
    )
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/logo.py. Do not hand-edit. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="0" android:startY="0"
                android:endX="0" android:endY="108"
                android:type="linear">
                <item android:color="{_hex(SLATE_TOP)}" android:offset="0.0" />
                <item android:color="{_hex(SLATE_BOTTOM)}" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
{smudges}
</vector>
'''


# --- raster output -----------------------------------------------------------

SUPERSAMPLE = 4
LEGACY_CORNER = 0.22


def _linear(size, top, bottom):
    strip = Image.new("RGB", (1, size))
    for y in range(size):
        t = y / max(size - 1, 1)
        strip.putpixel((0, y), tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return strip.resize((size, size))


def _radial_tile(radius_px, alpha):
    n = 128
    tile = Image.new("L", (n, n), 0)
    px = tile.load()
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - (n - 1) / 2, y - (n - 1) / 2) / ((n - 1) / 2)
            px[x, y] = 0 if d >= 1 else round(alpha * 255 * (1 - d) ** 2)
    return tile.resize((radius_px * 2, radius_px * 2), Image.BICUBIC)


def _smudged(image, scale, offset, smudges, relative_to=None):
    white = Image.new("RGBA", image.size, (255, 255, 255, 255))
    w, h = image.size
    for cx, cy, r, a in smudges:
        if relative_to:
            px, py, rp = cx * w, cy * h, max(2, round(r * h))
        else:
            px, py, rp = cx * scale + offset[0], cy * scale + offset[1], max(2, round(r * scale))
        mask = Image.new("L", image.size, 0)
        mask.paste(_radial_tile(rp, a), (round(px) - rp, round(py) - rp))
        image = Image.composite(white, image, mask)
    return image


def _resample(pts, spacing):
    out, carry = [pts[0]], 0.0
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        seg = math.hypot(x1 - x0, y1 - y0)
        if seg == 0:
            continue
        t = spacing - carry
        while t <= seg:
            out.append((x0 + (x1 - x0) * t / seg, y0 + (y1 - y0) * t / seg))
            t += spacing
        carry = (carry + seg) % spacing
    out.append(pts[-1])
    return out


def _alpha_at(stops, t):
    if t <= stops[0][0]:
        return stops[0][1]
    if t >= stops[-1][0]:
        return stops[-1][1]
    for (o0, a0), (o1, a1) in zip(stops, stops[1:]):
        if o0 <= t <= o1:
            k = 0.0 if o1 == o0 else (t - o0) / (o1 - o0)
            return a0 + (a1 - a0) * k
    return stops[-1][1]


def _fade_ramp(size, fade, scale, offset):
    """The same gradient the vector uses, as a horizontal alpha ramp."""
    px0 = fade["from"][0] * scale + offset[0]
    px1 = fade["to"][0] * scale + offset[0]
    strip = Image.new("L", (size[0], 1))
    row = strip.load()
    for x in range(size[0]):
        t = 0.0 if px1 == px0 else (x - px0) / (px1 - px0)
        row[x, 0] = round(_alpha_at(fade["stops"], min(1.0, max(0.0, t))) * 255)
    return strip.resize(size)


def _draw_mark(image, scale, offset=(0.0, 0.0), rgb=CHALK):
    """Stamp each stroke as a coverage mask, then composite chalk through it.

    Pillow's wide polyline mitres each segment separately, which on a curve
    leaves spikes along the outer edge, and it has no per-stroke alpha at all.
    A disc per sample gives round caps and joins, and the mask carries the fade.
    """
    ink = Image.new("RGBA", image.size, rgb + (255,))
    for st in MARK:
        r = st["w"] * scale / 2
        cover = Image.new("L", image.size, 0)
        draw = ImageDraw.Draw(cover)
        pts = [(x * scale + offset[0], y * scale + offset[1]) for x, y in _flatten(st["d"])]
        for x, y in _resample(pts, max(0.35, r * 0.25)):
            draw.ellipse([x - r, y - r, x + r, y + r], fill=255)
        if "fade" in st:
            cover = ImageChops.multiply(cover, _fade_ramp(image.size, st["fade"], scale, offset))
        elif st.get("alpha", 1.0) < 1.0:
            cover = cover.point(lambda v, a=st["alpha"]: round(v * a))
        image = Image.composite(ink, image, cover)
    return image


def icon(size, circular=False):
    s = size * SUPERSAMPLE
    scale = s / VIEWPORT
    image = _linear(s, SLATE_TOP, SLATE_BOTTOM).convert("RGBA")
    image = _smudged(image, scale, (0.0, 0.0), SMUDGES)
    image = _draw_mark(image, scale)

    cut_mask = Image.new("L", (s, s), 0)
    cut = ImageDraw.Draw(cut_mask)
    if circular:
        cut.ellipse([0, 0, s - 1, s - 1], fill=255)
    else:
        cut.rounded_rectangle([0, 0, s - 1, s - 1], radius=s * LEGACY_CORNER, fill=255)
    image.putalpha(cut_mask)
    return image.resize((size, size), Image.LANCZOS)


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    reach = _assert_inside_safe_circle()
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    res = os.path.join(root, "app", "src", "main", "res")
    drawable = os.path.join(res, "drawable")
    print(f"mark reaches {reach:.2f} of the {SAFE_RADIUS} safe radius")

    def write(path, text):
        with open(path, "w", newline="\n") as f:
            f.write(text)

    write(os.path.join(drawable, "ic_launcher_background.xml"), vector_background())
    write(os.path.join(drawable, "ic_launcher_foreground.xml"), vector_mark(CHALK))
    # The themed layer is recoloured by the system and often lands on a solid
    # ground, where a faded stroke reads as a rendering fault rather than chalk.
    write(os.path.join(drawable, "ic_launcher_monochrome.xml"),
          vector_mark((255, 255, 255), tint="#FF000000", flat=True))
    aspect, mark_xml = vector_mark_cropped()
    write(os.path.join(drawable, "ic_app_mark.xml"), mark_xml)
    print(f"vector layers written; in-app mark aspect {aspect:.3f}")

    for density, size in DENSITIES.items():
        out = os.path.join(res, f"mipmap-{density}")
        os.makedirs(out, exist_ok=True)
        icon(size).save(os.path.join(out, "ic_launcher.webp"), "WEBP", lossless=True)
        icon(size, circular=True).save(
            os.path.join(out, "ic_launcher_round.webp"), "WEBP", lossless=True
        )
    print(f"mipmaps: {', '.join(DENSITIES)}")


if __name__ == "__main__":
    main()
