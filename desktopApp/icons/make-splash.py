"""Draws the animated splash the launcher shows while the app starts (desktopApp/splash/).

Usage: python3 -I make-splash.py   (needs Pillow; run from anywhere)

The Java launcher draws it before the JVM starts and animates it on its own thread, so it moves
while the app loads. It goes away by itself when the first window appears. GIF is the only format
the launcher animates: 256 colours and no partial transparency, so the background is opaque and
the edges are smoothed against it.

The launcher picks a bigger file on a HiDPI screen by its name: name@2x.gif on macOS,
name.scale-<dpi>.gif on Windows (120 = 125%, 144 = 150%, 168 = 175%, 192 = 200%).
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ICON = os.path.join(HERE, "SlowDowner.png")
FONT = os.path.join(HERE, "../../shared-ui/src/commonMain/composeResources/font/space_grotesk_bold.ttf")
OUT = os.path.join(HERE, "../splash")

# The app's dark theme (shared-ui/.../ui/theme/Color.kt): background, outlineVariant, primary, onBackground.
BACKGROUND = (0x0F, 0x11, 0x14)
TRACK = (0x2C, 0x32, 0x3B)
ARC = (0xF2, 0xA5, 0x41)
TEXT = (0xEC, 0xE8, 0xE2)

WIDTH, HEIGHT = 320, 200
FRAMES = 24
FRAME_MS = 40  # one turn in about a second
SUPERSAMPLE = 4

# (file name, scale)
VARIANTS = [
    ("splash.gif", 1.0),
    ("splash@2x.gif", 2.0),
    ("splash.scale-120.gif", 1.25),
    ("splash.scale-144.gif", 1.5),
    ("splash.scale-168.gif", 1.75),
    ("splash.scale-192.gif", 2.0),
]


def static_layer(scale):
    """Everything but the spinner, at scale * SUPERSAMPLE."""
    k = scale * SUPERSAMPLE
    image = Image.new("RGB", (round(WIDTH * k), round(HEIGHT * k)), BACKGROUND)
    draw = ImageDraw.Draw(image)
    # A hairline border: the splash stays visible on a dark desktop.
    draw.rectangle([0, 0, image.width - 1, image.height - 1], outline=TRACK, width=max(1, round(k)))
    icon_size = round(88 * k)
    icon = Image.open(ICON).convert("RGBA").resize((icon_size, icon_size), Image.LANCZOS)
    image.paste(icon, ((image.width - icon_size) // 2, round(28 * k)), icon)
    font = ImageFont.truetype(FONT, round(24 * k))
    draw.text((image.width / 2, round(134 * k)), "Downstep", font=font, fill=TEXT, anchor="mm")
    return image


def frame(base, scale, index):
    k = scale * SUPERSAMPLE
    image = base.copy()
    draw = ImageDraw.Draw(image)
    radius = 10 * k
    centre = (image.width / 2, 170 * k)
    box = [centre[0] - radius, centre[1] - radius, centre[0] + radius, centre[1] + radius]
    stroke = max(1, round(2.5 * k))
    draw.ellipse(box, outline=TRACK, width=stroke)
    start = 360 * index / FRAMES - 90
    draw.arc(box, start, start + 100, fill=ARC, width=stroke)
    # Round caps, as a Material progress indicator.
    for angle in (start, start + 100):
        a = math.radians(angle)
        r = radius - stroke / 2
        x, y = centre[0] + r * math.cos(a), centre[1] + r * math.sin(a)
        draw.ellipse([x - stroke / 2, y - stroke / 2, x + stroke / 2, y + stroke / 2], fill=ARC)
    return image.resize((round(WIDTH * scale), round(HEIGHT * scale)), Image.LANCZOS)


def write(name, scale):
    base = static_layer(scale)
    frames = [frame(base, scale, i) for i in range(FRAMES)]
    # One palette for every frame, no dithering: the static parts must not shimmer between frames.
    sheet = Image.new("RGB", (frames[0].width, frames[0].height * 2))
    sheet.paste(frames[0], (0, 0))
    sheet.paste(frames[FRAMES // 4], (0, frames[0].height))
    palette = sheet.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    indexed = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    indexed[0].save(
        os.path.join(OUT, name), save_all=True, append_images=indexed[1:],
        duration=FRAME_MS, loop=0, optimize=False, disposal=1,
    )


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, scale in VARIANTS:
        write(name, scale)
        print(name, os.path.getsize(os.path.join(OUT, name)), "bytes")


if __name__ == "__main__":
    main()
