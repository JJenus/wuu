"""Prepare Wuu raster assets from the supplied blue-on-white logo (Pillow required).

Run: python3 tools/prepare-brand.py [source-image]
The source is read-only. This is color-key cleanup, not a vector reconstruction.
"""

from pathlib import Path
import sys

from PIL import Image, ImageChops, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
source = (
    Path(sys.argv[1])
    if len(sys.argv) > 1
    else ROOT / "public/photo_2026-10-21_20-47-14.jpg"
)
image = Image.open(source).convert("RGB")
clean = Image.new("RGBA", image.size)
pixels = []
for red, green, blue in image.getdata():
    # The supplied artwork is blue with nearly zero red. Recover edge alpha
    # against its white JPEG matte, and remove neutral background/JPEG noise.
    alpha = (255 - red) / 255
    if blue - red < 12 or blue - green < 8 or alpha < 0.05:
        pixels.append((0, 0, 0, 0))
    else:
        channels = [
            max(0, min(255, round((c - 255 * (1 - alpha)) / alpha)))
            for c in (red, green, blue)
        ]
        pixels.append((*channels, round(alpha * 255)))
clean.putdata(pixels)
# Suppress isolated JPEG fringe noise without flattening the blue shading.
mask = clean.getchannel("A")
clean.putalpha(ImageChops.darker(mask, mask.filter(ImageFilter.MedianFilter(3))))
bounds = clean.getchannel("A").getbbox()
if not bounds:
    raise ValueError("No blue artwork found; inspect the supplied image before converting.")
clean = clean.crop(bounds)
output = ROOT / "public"
brand = output / "brand"
brand.mkdir(exist_ok=True)


def canvas(size, *, opaque=False, padding=0.06):
    result = Image.new(
        "RGBA", (size, size), (255, 255, 255, 255) if opaque else (0, 0, 0, 0)
    )
    mark = clean.copy()
    inset = max(1, round(size * padding))
    mark.thumbnail((size - 2 * inset, size - 2 * inset), Image.Resampling.LANCZOS)
    result.alpha_composite(
        mark, ((size - mark.width) // 2, (size - mark.height) // 2)
    )
    return result


canvas(512).save(brand / "logo.png", optimize=True)
for size in (96, 192):
    mark = canvas(size)
    mark.save(brand / f"logo-{size}.png", optimize=True)
    mark.save(brand / f"logo-{size}.webp", lossless=True, method=6)
canvas(32, padding=0.03).save(output / "favicon-32.png", optimize=True)
canvas(48, padding=0.03).save(output / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
canvas(180, opaque=True, padding=0.12).save(output / "apple-touch-icon.png", optimize=True)
for size in (192, 512):
    canvas(size, opaque=True, padding=0.12).save(output / f"icon-{size}.png", optimize=True)
for path in sorted([*brand.iterdir(), *output.glob("*icon*.png"), output / "favicon.ico"]):
    print(f"{path.relative_to(ROOT)}: {path.stat().st_size:,} bytes")
