#!/usr/bin/env python3
"""Turns raw phone captures into screenshots Google Play will accept.

Play wants an aspect ratio between 1:2 and 2:1. A capture off a modern phone does not
have one: these came off a 20:9 screen at 2408x1080, which is 2.23:1, and Play rejects
it outright. Something has to give, and the choice of what is the whole point of this
script.

It trims the parts that are not the game -- the black bar down one side, Android's
navigation bar down the other, the status bar along the top -- and then *pads* to 16:9
with the app's own background colour instead of cropping further. Padding, because every
edge of this interface carries something worth seeing: the coin balance, the price
buttons, the power slider. Cropping to fit would have thrown one of them away, and a
screenshot that sells the game badly is worse than one with a band along the top.

Usage, from the repository root:

    python3 tools/prepare_screenshots.py capture1.jpg capture2.jpg ...

Files come out in docs/store/screenshots/ numbered in the order given, which is the
order they should be uploaded. Needs Pillow and numpy.
"""

import pathlib
import sys

import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/store/screenshots"

#: The app's own background, so the bands read as part of the design.
INK = (11, 20, 22)

#: Android's navigation bar, down the right edge in landscape.
NAV_BAR = 115

#: The clock and battery strip along the top.
STATUS_BAR = 60

#: 16:9, which Play recommends for landscape and which clears its rule comfortably.
TARGET = (1920, 1080)

#: Below this, a row or column is the black surround rather than anything drawn.
DARK = 16


def content_box(image: Image.Image) -> tuple[int, int, int, int]:
    """The part of the capture that is actually the app."""
    pixels = np.asarray(image).astype(int)
    height, width, _ = pixels.shape
    columns = pixels.mean(axis=(0, 2))
    rows = pixels.mean(axis=(1, 2))

    left = next(x for x in range(width) if columns[x] > DARK)
    bottom = next(y for y in range(height - 1, -1, -1) if rows[y] > DARK) + 1
    # The navigation bar has bright icons in it, so it cannot be found by darkness --
    # it is a fixed width on a given phone, and it is always hard against the edge.
    return left, STATUS_BAR, width - NAV_BAR, bottom


def prepare(source: pathlib.Path) -> Image.Image:
    image = Image.open(source).convert("RGB")
    content = image.crop(content_box(image))
    width, height = content.size

    # Grow the canvas to 16:9 around the content rather than cutting into it.
    canvas_height = max(height, round(width * TARGET[1] / TARGET[0]))
    canvas_width = max(width, round(canvas_height * TARGET[0] / TARGET[1]))
    canvas = Image.new("RGB", (canvas_width, canvas_height), INK)
    canvas.paste(content, ((canvas_width - width) // 2, (canvas_height - height) // 2))

    return canvas.resize(TARGET, Image.LANCZOS)


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2

    OUT.mkdir(parents=True, exist_ok=True)
    for index, name in enumerate(argv[1:], start=1):
        source = pathlib.Path(name)
        out = OUT / f"{index:02d}-{source.stem}.jpg"
        prepare(source).save(out, "JPEG", quality=92, optimize=True)
        # A path outside the repository is not worth crashing over after the work is done.
        shown = out.relative_to(ROOT) if out.is_relative_to(ROOT) else out
        print(f"{shown}  {out.stat().st_size // 1024} KB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
