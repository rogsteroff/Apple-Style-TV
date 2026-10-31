#!/usr/bin/env python3
"""Bundle a nonpersonal, freely licensed mountain-lake photograph for Apple Style TV.

Source: Dzo / Unsplash, snow-capped mountains over a tranquil lake at sunset.
https://unsplash.com/photos/PtzS6h6dk2E (Unsplash License).
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
url = ("https://images.unsplash.com/photo-1765527219001-2810d2689423"
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
im = ImageEnhance.Brightness(im).enhance(.77)
im = ImageEnhance.Color(im).enhance(1.28)
# Blue-violet twilight grade and a warm sunset glow. Shadows preserve UI contrast.
tint = Image.new("RGBA", im.size)
draw=ImageDraw.Draw(tint)
for y in range(im.height):
    t = y / im.height
    alpha = int(37 + 35*t + 25*max(0, 1-t/.4))
    color = (16, 21, 62, alpha) if y < 670 else (10, 15, 49, min(165,alpha+39))
    draw.line((0,y,1920,y), fill=color, width=1)
im = Image.alpha_composite(im.convert("RGBA"), tint)
# Warm right-side twilight light and the corresponding reflection.
glow = Image.new("RGBA", im.size, (0,0,0,0))
gd = ImageDraw.Draw(glow)
gd.ellipse((1230, 110, 2360, 850), fill=(255, 140, 99, 57))
gd.ellipse((1400, 545, 2070, 1330), fill=(243, 126, 100, 22))
glow = glow.filter(ImageFilter.GaussianBlur(150))
im = Image.alpha_composite(im, glow).convert("RGB")
im.save(out,"JPEG",quality=86,optimize=True,subsampling=0)
print("Created scenic lake wallpaper:", out, out.stat().st_size)
