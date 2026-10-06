"""Builds the TV app's launcher icons, TV banner and in-app images from the web assets.

Run from the repo root after changing web/static/tubetamer-icon-512.png or
web/static/bg-doodle.png:

    python android-tv/tools/make_icons.py

Needs Pillow. The banner text uses Segoe UI Semibold on Windows, else DejaVu Sans.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web" / "static"
RES = ROOT / "android-tv" / "app" / "src" / "main" / "res"

ICON = Image.open(WEB / "tubetamer-icon-512.png").convert("RGBA")
NAVY = (24, 36, 66, 255)       # --bg-card #182442, the icon's own background
BASE = (15, 24, 40, 255)       # --bg-base #0F1828
CREAM = (229, 221, 208, 255)   # --text-primary #E5DDD0
GOLD = (201, 168, 76, 255)     # --accent #C9A84C

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def scaled(img: Image.Image, size: int) -> Image.Image:
    return img.resize((size, size), Image.LANCZOS)


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print(path.relative_to(ROOT), img.size)


def font(size: int) -> ImageFont.FreeTypeFont:
    for name in ("seguisb.ttf", "segoeuib.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


# Legacy launcher icon (pre-Android 8): the full rounded icon, 48 dp.
for d, k in DENSITIES.items():
    save(scaled(ICON, round(48 * k)), RES / f"mipmap-{d}" / "ic_launcher.png")

# Adaptive icon foreground: 108 dp canvas, artwork inside the 66 dp safe zone,
# drawn on the same navy as the background so the mask never shows an edge.
for d, k in DENSITIES.items():
    canvas = round(108 * k)
    art = round(72 * k)
    fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    off = (canvas - art) // 2
    fg.alpha_composite(scaled(ICON, art), (off, off))
    save(fg, RES / f"mipmap-{d}" / "ic_launcher_foreground.png")

# Android TV launcher banner: 320x180 px in xhdpi (Android TV spec), drawn at
# 2x and also kept as xxxhdpi for 4K launchers.
W, H = 640, 360
banner = Image.new("RGBA", (W, H), BASE)
draw = ImageDraw.Draw(banner)
for y in range(H):  # same diagonal mood as the web header gradient
    t = y / H
    c = tuple(round(NAVY[i] * (1 - t) + BASE[i] * t) for i in range(3)) + (255,)
    draw.line([(0, y), (W, y)], fill=c)
# Logo + name centered as one group; the font shrinks until it fits.
LOGO = 200
GAP = 28
size = 80
while True:
    f = font(size)
    l, t, r, b = draw.textbbox((0, 0), "TubeTamer", font=f)
    text_w = r - l
    if LOGO + GAP + text_w <= W - 2 * 40 or size <= 30:
        break
    size -= 2
x0 = (W - (LOGO + GAP + text_w)) // 2
banner.alpha_composite(scaled(ICON, LOGO), (x0, (H - LOGO) // 2))
tx = x0 + LOGO + GAP
draw.text((tx - l, H // 2 - 10), "TubeTamer", font=f, fill=CREAM, anchor="lm")
draw.line([(tx, H // 2 + size // 2 + 4), (tx + text_w, H // 2 + size // 2 + 4)], fill=GOLD, width=5)
banner = banner.convert("RGB")
save(banner, RES / "drawable-xxxhdpi" / "banner.png")
save(banner.resize((320, 180), Image.LANCZOS), RES / "drawable-xhdpi" / "banner.png")

# In-app images (no density scaling: drawn at an explicit dp size).
save(scaled(ICON, 256), RES / "drawable-nodpi" / "tubetamer_logo.png")
save(Image.open(WEB / "bg-doodle.png").convert("RGBA"), RES / "drawable-nodpi" / "bg_doodle.png")
