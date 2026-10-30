#!/usr/bin/env python3
"""Generate Apple Style TV's built-in 16:9 abstract wallpaper; no personal photos.

The file is created before Android resource compilation in GitHub Actions.
"""
from PIL import Image, ImageDraw, ImageFilter
from math import sin, exp
from pathlib import Path
import sys

W, H = 1600, 900

def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))

pic = Image.new("RGB", (W, H))
pixels = pic.load()
for y in range(H):
    v = y / H
    for x in range(W):
        u = x / W
        top = mix((7, 24, 58), (102, 65, 132), u ** 1.4)
        lower = mix((9, 34, 83), (192, 92, 141), u ** 1.3)
        color = mix(top, lower, min(1, v * .93))
        glow = exp(-((u - .84) / .26) ** 2 - ((v - .50) / .24) ** 2) * .73
        pixels[x, y] = mix(color, (255, 171, 116), glow)

def ribbon(points, start, end, opacity=255):
    global pic
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon(points, fill=opacity)
    layer = Image.new("RGB", (W, H))
    values = layer.load()
    for y in range(H):
        v = y / H
        for x in range(W):
            u = x / W
            t = .55 * v + .15 * u if u < .5 else .42 * v + .38 * u
            values[x, y] = mix(start, end, t)
    pic.paste(layer, (0, 0), mask)

def curve(fn):
    line = [(x, max(-100, min(H + 100, int(fn(x / W) * H))))
            for x in range(-10, W + 12, 4)]
    return line + [(W + 12, H + 12), (-10, H + 12)]

def stroke(fn, color, opacity=145, blur=8):
    global pic
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(layer).line(
        [(x, int(fn(x / W) * H)) for x in range(0, W, 3)],
        fill=tuple(color) + (opacity,), width=3, joint="curve")
    pic = Image.alpha_composite(
        pic.convert("RGBA"), layer.filter(ImageFilter.GaussianBlur(blur))).convert("RGB")
    pic = Image.alpha_composite(pic.convert("RGBA"), layer).convert("RGB")

upper = lambda u: .24 + .14 * u + .09 * sin(1.05 * 3.14159 * u + .4)
ribbon(curve(upper), (73, 125, 211), (129, 78, 182))
stroke(upper, (182, 198, 255), 115, 4)

right = lambda u: .63 - .29 * (max(0, u) ** 1.6) + .035 * sin(5 * u)
stroke(right, (247, 166, 181), 115, 14)

middle = lambda u: .41 + .19 * u + .11 * sin(3.5 * u - .6)
ribbon(curve(middle), (54, 91, 172), (137, 70, 148), 248)
stroke(middle, (161, 168, 246), 165, 7)

peach = lambda u: .49 + .20 * u + .13 * sin(4.9 * u + .85)
ribbon(curve(peach), (67, 86, 161), (215, 99, 132), 208)
stroke(peach, (248, 163, 177), 115, 13)

front = lambda u: .48 + .56 * (max(0, u) ** 1.8) - .025 * sin(6.4 * u)
ribbon(curve(front), (8, 29, 71), (39, 38, 106))
stroke(front, (102, 132, 216), 160, 10)

soft = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(soft).ellipse((990, 180, 1910, 850), fill=(255, 127, 124, 35))
soft = soft.filter(ImageFilter.GaussianBlur(155))
pic = Image.alpha_composite(pic.convert("RGBA"), soft).convert("RGB")

output = Path(sys.argv[1])
output.parent.mkdir(parents=True, exist_ok=True)
pic.resize((1920, 1080), Image.Resampling.LANCZOS).save(
    output, "JPEG", quality=86, optimize=True, subsampling=0)
print(f"Created built-in Apple Style TV background: {output} ({output.stat().st_size} bytes)")
