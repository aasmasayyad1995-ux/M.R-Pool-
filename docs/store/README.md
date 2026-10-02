# Store art

Everything Play asks for as a picture. Upload them in this order; the first two are
what shows in search results, so they are the table rather than a menu.

| File | What it is |
|---|---|
| `icon-512.png` | The listing icon. 512×512, square, no transparency — Google rounds the corners itself. |
| `feature-graphic-1024x500.png` | The banner across the top of the listing. |
| `screenshots/01-match-aiming.jpg` | A match in play, with the aim line out. |
| `screenshots/02-match-cue-and-angles.jpg` | The cue down and the angle preview showing where both balls go. |
| `screenshots/03-lobby.jpg` | The lobby, with the player's record. |
| `screenshots/04-play-with-robot.jpg` | The three robots and what each pays. |
| `screenshots/05-play-online.jpg` | Quick match and private rooms. |
| `screenshots/06-table-selection.jpg` | Twenty tables, one unlocked. |
| `screenshots/07-cue-selection.jpg` | Twelve cues and their stats. |

The icon and the banner are drawn by `tools/make_icons.py`. The screenshots are real
phone captures, prepared with `tools/prepare_screenshots.py`.

## What Play will and will not take

Screenshots must be between 1:2 and 2:1, at least 320px on the short side, at most
3840px on the long one, JPEG or 24-bit PNG with no alpha, under 8MB. **A raw capture
from a modern phone fails that first rule**: these came off a 20:9 screen at 2408×1080,
which is 2.23:1, and Play rejects it.

So the script trims what is not the game — the black bar down one side, Android's
navigation bar down the other, the status bar along the top — and then pads with the
app's own background colour rather than cropping further. Padding, because every edge
of this interface is carrying something worth seeing: the coin balance, the price
buttons, the power slider. Cutting to fit would have thrown one of them away.

To redo them after a UI change: take the captures, then

```bash
python3 tools/prepare_screenshots.py shot1.jpg shot2.jpg …
```
