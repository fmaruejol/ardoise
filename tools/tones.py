"""Derive the missing tones of each M3 tonal palette from the ones the dark
scheme already pins down.

M3's tone is CIELAB L*, and a tonal palette holds hue and chroma roughly
constant along it, so a missing tone can be interpolated in LCh(ab) from the
known ones rather than guessed by eye. Validated below against tones that are
known but held out of the fit.
"""
import math

# --- sRGB <-> CIELAB (D65) ---------------------------------------------------

def srgb_to_linear(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def linear_to_srgb(c):
    v = 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055
    return v * 255.0

M = [[0.4124564, 0.3575761, 0.1804375],
     [0.2126729, 0.7151522, 0.0721750],
     [0.0193339, 0.1191920, 0.9503041]]
MINV = [[3.2404542, -1.5371385, -0.4985314],
        [-0.9692660, 1.8760108, 0.0415560],
        [0.0556434, -0.2040259, 1.0572252]]
WP = (0.95047, 1.0, 1.08883)

def hex_to_lab(h):
    h = h.lstrip('#')
    rgb = [srgb_to_linear(int(h[i:i + 2], 16)) for i in (0, 2, 4)]
    xyz = [sum(M[i][j] * rgb[j] for j in range(3)) for i in range(3)]
    f = []
    for i in range(3):
        t = xyz[i] / WP[i]
        f.append(t ** (1 / 3) if t > 216 / 24389 else (841 / 108) * t + 4 / 29)
    return (116 * f[1] - 16, 500 * (f[0] - f[1]), 200 * (f[1] - f[2]))

def lab_to_rgb(lab):
    L, a, b = lab
    fy = (L + 16) / 116
    fx, fz = fy + a / 500, fy - b / 200
    def inv(t):
        return t ** 3 if t ** 3 > 216 / 24389 else (108 / 841) * (t - 4 / 29)
    xyz = [inv(fx) * WP[0], inv(fy) * WP[1], inv(fz) * WP[2]]
    return [sum(MINV[i][j] * xyz[j] for j in range(3)) for i in range(3)]

def in_gamut(lab, eps=0.0):
    return all(-eps <= linear_to_srgb(max(c, 0.0) if c > -1e-9 else c) <= 255 + eps
               for c in lab_to_rgb(lab)) and all(c >= -1e-6 for c in lab_to_rgb(lab))

def lab_to_hex(lab):
    """Clip chroma until the colour fits sRGB, then round."""
    L, a, b = lab
    C = math.hypot(a, b)
    h = math.atan2(b, a)
    lo, hi = 0.0, C
    for _ in range(40):
        mid = (lo + hi) / 2
        if in_gamut((L, mid * math.cos(h), mid * math.sin(h))):
            lo = mid
        else:
            hi = mid
    rgb = lab_to_rgb((L, lo * math.cos(h), lo * math.sin(h)))
    out = ''.join('%02X' % max(0, min(255, round(linear_to_srgb(max(c, 0.0))))) for c in rgb)
    return '#' + out

def lch(h):
    L, a, b = hex_to_lab(h)
    return L, math.hypot(a, b), math.atan2(b, a)

def unwrap(hues):
    """Keep hue angles on one branch so interpolation does not wrap."""
    out = [hues[0]]
    for x in hues[1:]:
        prev = out[-1]
        while x - prev > math.pi:
            x -= 2 * math.pi
        while prev - x > math.pi:
            x += 2 * math.pi
        out.append(x)
    return out

def interp(xs, ys, x):
    if x <= xs[0]:
        return ys[0]
    if x >= xs[-1]:
        return ys[-1]
    for i in range(len(xs) - 1):
        if xs[i] <= x <= xs[i + 1]:
            t = (x - xs[i]) / (xs[i + 1] - xs[i])
            return ys[i] + t * (ys[i + 1] - ys[i])
    raise AssertionError

def derive(known, tone):
    """`known` is {tone: hex}; returns the hex for `tone`."""
    tones = sorted(known)
    cs = [lch(known[t])[1] for t in tones]
    hs = unwrap([lch(known[t])[2] for t in tones])
    C = interp(tones, cs, tone)
    H = interp(tones, hs, tone)
    return lab_to_hex((float(tone), C * math.cos(H), C * math.sin(H)))

def de(h1, h2):
    a, b = hex_to_lab(h1), hex_to_lab(h2)
    return math.dist(a, b)

# --- what the dark scheme pins down -----------------------------------------

PRIMARY = {20: '#003730', 30: '#005046', 40: '#006A5E', 80: '#53DBC5', 90: '#71F8E1'}
SECONDARY = {20: '#1C3531', 30: '#334B46', 80: '#B1CCC6', 90: '#CFE8E2'}
TERTIARY = {20: '#16324A', 30: '#2E4961', 80: '#ADCAE6', 90: '#CBE6FF'}
NEUTRAL = {4: '#090F0E', 6: '#0E1513', 10: '#171D1B', 12: '#1B2220',
           17: '#252C2A', 20: '#2B3230', 22: '#303735', 90: '#DEE4E1'}
NEUTRAL_VARIANT = {30: '#3F4946', 60: '#889390', 80: '#BEC9C5'}
# The amber pair is bespoke rather than from a palette, but it behaves like one.
WARNING = {20: '#3B2E1A', 80: '#F2BF67', 85: '#E0D0B4', 90: '#F6E4C4'}

# --- validation: hold out a tone that is known, and predict it ---------------

print('validation (ΔE in Lab against the known value):')
checks = [
    ('primary 40', {k: v for k, v in PRIMARY.items() if k != 40}, 40, PRIMARY[40]),
    ('primary 30', {k: v for k, v in PRIMARY.items() if k != 30}, 30, PRIMARY[30]),
    ('neutral 20', {k: v for k, v in NEUTRAL.items() if k != 20}, 20, NEUTRAL[20]),
    ('neutral 12', {k: v for k, v in NEUTRAL.items() if k != 12}, 12, NEUTRAL[12]),
    ('nvariant 60', {k: v for k, v in NEUTRAL_VARIANT.items() if k != 60}, 60,
     NEUTRAL_VARIANT[60]),
    ('secondary 30', {k: v for k, v in SECONDARY.items() if k != 30}, 30, SECONDARY[30]),
]
for name, known, tone, truth in checks:
    got = derive(known, tone)
    print(f'  {name:14s} predicted {got}  actual {truth}  ΔE {de(got, truth):.2f}')

# --- the tones the light scheme needs ---------------------------------------

print()
print('light scheme tones:')
wanted = [
    ('primary', PRIMARY, (10, 40, 90)),
    ('secondary', SECONDARY, (10, 40, 90)),
    ('tertiary', TERTIARY, (10, 40, 90)),
    ('neutral', NEUTRAL, (10, 20, 90, 92, 94, 95, 96, 98)),
    ('neutralVariant', NEUTRAL_VARIANT, (30, 50, 80, 90)),
    ('warning', WARNING, (10, 30, 40, 90)),
]
for name, known, tones in wanted:
    row = []
    for t in tones:
        value = known.get(t) or derive(known, t)
        source = 'known' if t in known else 'derived'
        row.append(f'{t}={value}({source})')
    print(f'  {name:15s} ' + '  '.join(row))
