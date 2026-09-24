#!/usr/bin/env python3
"""Draws the launcher icons and the Play Store graphics.

Two reasons this exists rather than a folder of hand-made PNGs:

  * minSdk is 24, and adaptive icons only arrived in API 26. With nothing but
    res/mipmap-anydpi-v26 there is no launcher icon at all on Android 7.0 and
    7.1 -- the resource simply does not resolve for those versions.
  * Play wants a 512x512 icon and a 1024x500 feature graphic that look like the
    app rather than like a placeholder.

Everything below is drawn from the same few numbers, so the legacy PNGs, the
adaptive icon and the store art cannot drift apart. Run it after changing them:

    python3 tools/make_icons.py

Needs Pillow.
"""

import pathlib
from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
STORE = ROOT / "docs/store"

# The app's own palette, from res/values/colors.xml.
FELT = (11, 110, 58)
FELT_DARK = (7, 78, 41)
INK = (11, 20, 22)
GOLD = (244, 197, 66)
BALL = (16, 16, 16)
WHITE = (255, 255, 255)

# Supersampling factor. Pillow has no antialiased drawing, so everything is drawn
# big and scaled down, which is what softens the edges.
SS = 8

FONT_DIRS = [
    "/usr/share/fonts/truetype/dejavu",
    "/usr/share/fonts/truetype/freefont",
]


def font(name, size):
    for d in FONT_DIRS:
        p = pathlib.Path(d) / name
        if p.exists():
            return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()


def draw_ball(d, cx, cy, r):
    """The eight ball: black sphere, white spot, a real numeral 8, one highlight."""
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=BALL)

    # A soft highlight up and to the left, which is what makes it read as a
    # sphere rather than a black disc.
    hr = r * 0.30
    hx, hy = cx - r * 0.38, cy - r * 0.40
    for i in range(12, 0, -1):
        k = i / 12.0
        v = int(16 + 120 * (1 - k) ** 2)
        d.ellipse([hx - hr * k, hy - hr * k, hx + hr * k, hy + hr * k], fill=(v, v, v))

    # The white spot, and the 8 drawn as two rings so it stays a shape rather
    # than a font that may not be installed wherever this runs next.
    sr = r * 0.46
    d.ellipse([cx - sr, cy - sr, cx + sr, cy + sr], fill=WHITE)

    stroke = sr * 0.20
    top_r = sr * 0.40
    bot_r = sr * 0.52
    top_cy = cy - sr * 0.40
    bot_cy = cy + sr * 0.40
    for ccy, rr in ((top_cy, top_r), (bot_cy, bot_r)):
        d.ellipse([cx - rr, ccy - rr, cx + rr, ccy + rr], fill=BALL)
        d.ellipse(
            [cx - rr + stroke, ccy - rr + stroke, cx + rr - stroke, ccy + rr - stroke],
            fill=WHITE,
        )


def launcher_png(size, circular=True):
    """One legacy launcher icon.

    Circular because that is how a launcher on API 26+ masks the adaptive icon,
    so Android 7 ends up with the same silhouette as everything newer.
    """
    n = size * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if circular:
        d.ellipse([0, 0, n - 1, n - 1], fill=FELT)
        # A darker rim, so the icon still has an edge on a green wallpaper.
        d.ellipse([0, 0, n - 1, n - 1], outline=FELT_DARK, width=int(n * 0.035))
    else:
        d.rounded_rectangle([0, 0, n - 1, n - 1], radius=int(n * 0.18), fill=FELT)

    draw_ball(d, n / 2, n / 2, n * 0.30)
    return img.resize((size, size), Image.LANCZOS)


def fit(name, text, max_width, start_size):
    """The largest size of `name` at which `text` still fits in max_width.

    Without this the title runs off the right edge of the feature graphic the
    moment anybody changes the words, and Play crops rather than scales.
    """
    size = start_size
    while size > 8:
        f = font(name, size)
        if f.getlength(text) <= max_width:
            return f
        size -= 1
    return font(name, 8)


def store_icon(size=512):
    """Play's listing icon: a full square, no transparency -- Google masks it."""
    n = size * SS
    img = Image.new("RGB", (n, n), FELT)
    draw_ball(ImageDraw.Draw(img), n / 2, n / 2, n * 0.33)
    return img.resize((size, size), Image.LANCZOS)


def feature_graphic(w=1024, h=500):
    """The banner at the top of the Play listing.

    Play crops this on some layouts, so the ball and the words stay inside the
    middle rather than running to the edges.
    """
    n_w, n_h = w * SS // 4, h * SS // 4  # 4x is plenty here and keeps memory sane
    img = Image.new("RGB", (n_w, n_h), FELT)
    d = ImageDraw.Draw(img)

    # Felt that darkens towards the edges, like light over a table.
    for y in range(n_h):
        k = abs(y - n_h / 2) / (n_h / 2)
        v = tuple(int(c * (1 - 0.30 * k * k)) for c in FELT)
        d.line([(0, y), (n_w, y)], fill=v)

    draw_ball(d, n_w * 0.175, n_h * 0.50, n_h * 0.27)

    left = n_w * 0.33
    room = n_w * 0.94 - left

    title1 = fit("DejaVuSans-Bold.ttf", "Mr. Pool 3D", room, int(n_h * 0.20))
    title2 = fit("DejaVuSans-Bold.ttf", "8 Ball Pool", room, int(n_h * 0.20))
    tagline = "Play a friend online  ·  Beat the bots  ·  Free"
    sub = fit("DejaVuSans.ttf", tagline, room, int(n_h * 0.085))

    d.text((left, n_h * 0.30), "Mr. Pool 3D", font=title1, fill=WHITE, anchor="lm")
    d.text((left, n_h * 0.54), "8 Ball Pool", font=title2, fill=GOLD, anchor="lm")
    d.text((left, n_h * 0.75), tagline, font=sub, fill=(214, 232, 220), anchor="lm")
    return img.resize((w, h), Image.LANCZOS)


def main():
    # Legacy launcher icons. The densities Android looks for below API 26.
    for folder, px in [
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192),
    ]:
        out = RES / folder
        out.mkdir(parents=True, exist_ok=True)
        icon = launcher_png(px)
        icon.save(out / "ic_launcher.png")
        icon.save(out / "ic_launcher_round.png")
        print(f"{folder}/ic_launcher.png  {px}x{px}")

    STORE.mkdir(parents=True, exist_ok=True)
    store_icon().save(STORE / "icon-512.png")
    print("docs/store/icon-512.png  512x512")
    feature_graphic().save(STORE / "feature-graphic-1024x500.png")
    print("docs/store/feature-graphic-1024x500.png  1024x500")


if __name__ == "__main__":
    main()
