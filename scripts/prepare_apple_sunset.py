#!/usr/bin/env python3
"""Bundle a nonpersonal, freely licensed mountain-lake photograph for Apple Style TV.

Source: Mark de Jong / Unsplash, Marlborough Sounds, New Zealand
https://unsplash.com/photos/P46SQFCLEIs (Unsplash License).
Network problems do not break the APK: locally generated abstract art is the fallback.
"""
from pathlib import Path
from PIL import Image, ImageEnhance, ImageOps, ImageDraw, ImageFilter
from io import BytesIO
from urllib.request import Request, urlopen
import sys

out = Path(sys.argv[1])
fallback = Path(sys.argv[2])
out.parent.mkdir(parents=True, exist_ok=True)
url = ("https://images.unsplash.com/photo-1609815633364-9090f5a291fd"
       "?auto=format&fit=crop&w=2400&q=85&fm=jpg")
try:
    request = Request(url, headers={"User-Agent":"AppleStyleTV/3.8"})
    with urlopen(request, timeout=35) as reply:
        raw = reply.read(12_000_000)
    src = Image.open(BytesIO(raw)).convert("RGB")
    print("Loaded freely licensed Unsplash landscape", src.size)
except Exception as error:
    print("Using bundled offline illustration fallback:", str(error))
    src = Image.open(fallback).convert("RGB")

im = ImageOps.fit(src, (1920,1080), method=Image.Resampling.LANCZOS, centering=(.5,.51))
im = ImageEnhance.Color(im).enhance(1.19)
# Blue-violet twilight grade and a warm sunset glow. Shadows preserve UI contrast.
tint = Image.new("RGBA", im.size)
draw=ImageDraw.Draw(tint)
for y in range(im.height):
    t = y / im.height
    alpha = int(14 + 48*t + 18*max(0, 1-t/.4))
    color = (20, 23, 77, alpha) if y < 670 else (12, 15, 49, min(160,alpha+33))
    draw.line((0,y,1920,y), fill=color, width=1)
im = Image.alpha_composite(im.convert("RGBA"), tint).convert("RGB")
im.save(out,"JPEG",quality=86,optimize=True,subsampling=0)
print("Created scenic lake wallpaper:", out, out.stat().st_size)
