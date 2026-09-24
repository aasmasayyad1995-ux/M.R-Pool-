# 🎱 Mr. Pool — 8 Ball Pool, Android 3D Edition

A complete 8 ball pool game for Android: a real 3D table rendered with OpenGL ES 3.0, a
physics engine with follow, draw and english, an opponent with three genuinely different
skill levels, and a coin economy with 12 cue sticks and 20 tables to unlock.

> Are you ready to become Mr. Pool? 🏆

**Mr. Pool needs a connection to play.** Turn the data off and the game covers itself with
a "No internet" screen and a retry — the robot and the same-device friend match included,
even though the pool in them is played entirely on the phone. That is a deliberate choice
and it is written up under [Needing a connection](#needing-a-connection).

## Opening

The app opens on the Infinity Core studio card: the infinity mark draws itself as a single
continuous stroke — a real lemniscate of Bernoulli, not a glyph, which is what lets one
animated stroke travel the whole figure of eight — and the name fades up underneath it. A
tap anywhere skips to the lobby.

## The lobby

| Option | What it does |
| --- | --- |
| **Profile** | Your picture, your name and your record. The picture is picked from the phone and never leaves it. |
| **Play with Robot** | Pick Beginner, Medium or Hard. Free to play, and a win pays 25, 50 or 100 coins. |
| **Play with Friend** | Two players on one device. No entry fee. |
| **Choose Cue Stick** | 12 cues. The house cue is free, the rest start at **200 coins**. |
| **Table Selection** | 20 tables. The local club is free, the rest start at **1,000 coins**. |
| **Coins & Rewards** | Balance, daily bonus, and exactly what every match pays. |
| **How to Play Pool** | Rules, controls, spin, position play and safety play. |

## The profile

A picture and a name, both kept on the phone.

The picture comes through Android's system photo picker, which hands the app a read grant
on the one photo chosen and asks for no permission at all — the app never gets to see the
rest of the gallery. It is then copied in rather than remembered by URI, because a grant
can lapse and the owner can delete the photo, either of which would leave a hole where the
avatar was. What is stored is a 256 pixel square: centre cropped, turned the right way up
from its EXIF orientation, a few kilobytes.

A phone photo can be forty megapixels, so it is decoded at a sample size that lands just
above the avatar size rather than in full, and the work happens off the thread that draws.

**The picture never leaves the phone.** An online opponent sees the name and nothing else.
Showing arbitrary pictures to strangers needs moderation behind it, and that is a different
thing to build.

## The robot

Three opponents that really do play differently — the numbers below come from a 108 game
round robin the robots played against each other (`Harness` style run of the shipped code):

| Level | Pots per shot | Fouls | vs Beginner | vs Medium | How it thinks |
| --- | --- | --- | --- | --- | --- |
| **Beginner** | 0.27 | 21.9% | — | 1 / 12 | Picks a ball by eye, aims roughly, hits too hard, never plans the next shot. Pays **25** to beat. |
| **Medium** | 0.47 | 8.9% | 8 / 12 | — | Picks the right ball, rehearses a handful of options, controls speed, plays safe when stuck. Pays **50**. |
| **Hard** | 0.67 | 3.7% | 12 / 12 | 10 / 12 | Champion level: rehearses every candidate shot in the physics engine, values where the cue ball finishes, hooks you with a safety when nothing is on, and banks off cushions to escape a snooker. Pays **100**. |

Difficulty is not a random number bolted onto a perfect aimer. Each level differs in aim
error, speed control, how many candidate shots it rehearses, whether it weighs position,
whether it plays safeties and whether it uses spin — see `ai/RobotDifficulty.kt`.

## Money

Coins are in-game currency only. They are **won, never bought**: nothing in this app costs
real money, there is no payment code anywhere in it, and there is no way to turn coins back
into money.

* **Every match is free to play**, against the robot and against a friend alike.
* What a win pays depends only on the robot you beat: **25** for the beginner, **50** for
  the medium, **100** for the hard one.
* The table is never a cost of admission — it changes how the cloth runs and how the game
  looks, nothing else.
* Daily bonus of 50 coins, worth one win against the medium robot.

## Rules implemented

Standard 8 ball, including the parts most pool games skip:

* The table stays open after the break; groups are claimed by the first ball legally potted
  on the next shot.
* Fouls: scratch, no contact, wrong group hit first, nothing reaching a cushion after
  contact, and an illegal break (fewer than four balls to a cushion with nothing potted).
* A foul gives the opponent ball in hand anywhere on the table — there is no head string
  restriction after a scratch, which is the bar rule most people play by.
* The 8 potted on the break is re-spotted, not a loss.
* Potting the 8 early, or scratching on it, loses the game. Potting it on the same stroke
  as your last group ball also loses.

## Physics

`game/PoolPhysics.kt` is a proper billiard simulation, not a bouncing circle toy:

* Linear velocity plus a full 3D angular velocity, so follow, draw and english all behave.
* Sliding friction spins a struck ball up to natural roll, then rolling resistance takes
  over. The sliding step is clipped at the moment the ball starts to roll, which is what
  stops slow balls creeping forever.
* Cut induced throw between colliding balls, and english that grabs the cushion on a bounce.
* Cushions cut away at the pocket mouths, with jaws that rattle a ball that does not drop.
* A fixed internal timestep independent of the display's frame rate — so the shot the robot
  rehearsed is exactly the shot you watch.

## Online play

Two devices play the same game without streaming a single ball position.

Pool is turn based, so a whole shot is four numbers — angle, power, side spin, top spin —
and both devices replay it through the identical deterministic simulation. That works
because the physics already runs on a fixed internal timestep that does not depend on either
device's frame rate; it was built that way so the robot's rehearsals would match what the
player sees, and online play falls out of the same property. A whole match costs a few
hundred bytes.

Floating point on two different CPUs is not guaranteed to agree to the last bit, and a
billiard break amplifies a tiny difference quickly, so after every move each device
fingerprints its table (`StateChecksum`, quantised to a tenth of a millimetre — a
four-hundredth of a ball). If the two fingerprints disagree, the host's table is the
authority: it publishes a snapshot and the guest adopts it, rules state and all.

`OnlineMatch` holds no Android or database types, so two of them can be wired to each other
in a unit test and made to play a whole game — which is exactly what the tests do.

Getting in: **Quick Match** takes whoever is waiting in the queue, or **Create a Room** gives
you a five character code to read out. The alphabet leaves out the characters that sound
alike over a phone: no O or 0, no I, L or 1, no S or 5.

### The server

`server/` is a small Ktor WebSocket service that does two things: it pairs players, and it
forwards messages between the two in a room. It never simulates anything and it never reads
a move — a move's contents ride through as an opaque blob — so the rules of pool live in one
place, the app, and the server never needs redeploying when they change.

```bash
cd server
./gradlew run                 # listens on $PORT, or 8080
./gradlew test                # 16 tests, including two clients over real WebSockets
```

Deploy it anywhere that takes a Dockerfile. `render.yaml` and `server/fly.toml` are
checked in, so on either host it is a form to fill rather than a file to write — see
[`server/README.md`](server/README.md) for the click-by-click. It keeps no state between
matches, so a free tier genuinely suits it: no card, no disk. Then build the app pointing
at it:

```bash
./gradlew assembleDebug -PmatchServerUrl=wss://your-server.example.com/ws
```

Leave that property out and online play is simply switched off; the rest of the game is
unaffected and the online screen says what is missing.

## Needing a connection

The whole game is behind a connection check, not just online play.

**What counts as connected** is a network Android has *validated* — one it has checked
really carries traffic — rather than one the phone has merely joined. The difference is the
cafe wifi that connects happily and carries nothing: counting that as online would let a
player in and then break everything they touched.

**Reaching the match server is deliberately not part of it.** The server sleeps when nobody
is using it and takes up to a minute to wake, so tying the whole game to it would lock the
player out of their own game every morning. The check is "does this phone have the
internet", not "is Mr. Pool up".

**A moment with no network is not a lost connection.** Android reports one at every handover
between wifi and mobile data, and a game that ended a match on each of those would be worse
to use than one with no check at all. The connection has to stay gone for
`ConnectionGate.GRACE_MILLIS` — three seconds — before the game believes it. That rule lives
in `ConnectionState`, which holds no Android types and is tested: the blink, the loss that
sticks, a phone that reports the same loss over and over, and a second loss after a
recovery.

**The watcher asks; it does not only listen.** The first version of this trusted Android's
network callbacks alone and did not work at all — turning mobile data off mid-match changed
nothing on screen. Three separate faults, any one of them enough:

* `onLost` read `activeNetwork` to see what was left, and at the instant a network is lost
  that still returns the network going away, still marked validated. The loss reported a
  connection.
* `onCapabilitiesChanged` believed whichever network it was told about. The dying network's
  own capabilities arrive with the loss and still say validated, so that put the connection
  back after the loss had removed it.
* `onAvailable` reported a connection for any network at all, before Android had checked
  whether it carried anything.

So the callbacks are now only a nudge — *something moved, look again* — and the answer
always comes from asking Android afresh. A one second tick asks anyway, so a callback that
never arrives, or arrives with stale news, cannot leave the game believing it is online. The
player therefore waits the grace period plus at most one tick. Being a second late is a
small cost; being wrong until the app is restarted is not.

**What the player sees:**

| Where they are | What happens |
| --- | --- |
| Anywhere but a match | The game is replaced by a "No internet" screen with a **Try again** button. There is nothing behind it to go back to, so there is no way past it. |
| In a match | A message over the table with **one** button, OK, which takes them to the lobby. A match that has lost its connection cannot be picked up where it stopped, so a second button would have to promise something that does not work. |

Being honest about the cost: the robot and the same-device friend match are played entirely
on the phone and would work perfectly well on a train with no signal. They are gated anyway,
because the game is meant to be an online game. If that is ever reconsidered, the one place
to change it is `ConnectionGate.noticeFor`.

## Ads

AdMob, in three places: a strip under the menus, a full screen ad every third match on the
way back to the lobby, and rewarded ones in **Coins & Rewards** — one the player presses for
coins, and one that pays for the daily bonus.

If no ad turns up, the daily bonus is paid anyway: a player who pressed the button and was
shown nothing has done what was asked, and taking their bonus away because an advert did not
fill would be punishing them for our failure. Backing out of an ad that *did* play is a
choice, and pays nothing.

**No ads during a match, in any form.** A full screen ad mid-frame, or a banner eating a
thumb's width of the cushion, would make the game worse to play — and a game people stop
playing earns nothing.

The rules live in `AdPolicy`, away from Android and away from the ad SDK, so how often a
player is interrupted is decided by tests rather than by feel. One of those tests is worth
naming: a full day of watching rewarded ads (4 × 10 coins) must pay less than a couple of
wins against the hard robot, because the moment tapping through ads beats playing, the
matches stop mattering. The first draft of those numbers failed that test and was changed.

**Published builds carry this app's real ad units**, passed in by CI; the defaults in
`app/build.gradle.kts` are Google's test ones, so a build made without them still runs and
says **TEST ADS** in the lobby rather than quietly earning nothing.

**Do not tap the ads in a real build.** Google reads taps on your own ads as invalid traffic
and closes AdMob accounts for it — it is the commonest way a new publisher loses theirs. The
ids, and the Play Store checklist that comes with shipping ads at all, are in
[`docs/ADMOB.md`](docs/ADMOB.md).

## Sound

Every sound is synthesised at runtime too, so the APK still ships without a single asset.
`audio/SoundSynth.kt` builds 16 bit PCM from scratch: a ball click is a pair of high
partials decaying over about 30ms on top of a one millisecond noise transient, a cushion is
the same idea an octave and a half down with the ring damped out, and the pocket is three
rattles into a falling rumble.

The physics reports every impact through `CollisionListener` as the shot plays out, so the
volume and pitch of each click come from the real closing speed — a gentle kiss is quiet and
a full blooded break is not. There is no canned break sample: the break is loud because the
simulation really does drive a dozen collisions in a quarter of a second.

The robot rehearses shots on a copy of the table, and that copy never carries the listener,
so its thinking is silent. Sound can be muted from the lobby or from the table, and the
choice is remembered.

## Rendering

`render/` draws everything procedurally: no image assets ship with the app. Ball numbers,
stripes, cloth weave and wood grain are painted at runtime with `Canvas` and uploaded as GL
textures, so any table colour in the shop can be rendered. Spheres, boxes, discs and the
tapered cue are generated as meshes, lit with a Blinn-Phong shader.

Controls: put a finger on the cue ball and **draw it back** to load the shot, then let go to
play it. The finger is the butt of the cue, so the same drag sets both halves of a shot:
how far back you pull is the power, and swinging round while pulled back swings the line of
the shot with it. Letting go without pulling costs you nothing. Drag anywhere else on the
table to swing the aim, two fingers to orbit, pinch to zoom. The meter down the side fills
as you pull, and the spin pad moves the tip off centre.

## Installing it on a phone

Every push to `main` publishes the APK to the
[latest release](../../releases/latest/download/app-debug.apk). That build has online play
switched on, pointed at the deployed match server. Open that link on an Android
phone, tap the downloaded file, and allow installation from unknown sources. No GitHub login
and no unzipping.

Needs Android 7.0 or newer and a device with OpenGL ES 3.0, which is anything made in roughly
the last decade.

The lobby shows the build it came from in its bottom corner. If a fix appears not to have
worked, check that number first: it is the difference between a fix that failed and a phone
still running the build before it.

Debug builds are signed with the key checked in at `keystore/debug.keystore`, so one build
installs over another. It is a debug key with the standard Android password, published here
on purpose — it must never be used to sign a real release.

Online play is switched off in those builds — they are compiled without a match server URL —
so the online screen explains what is missing and everything else plays normally.

## Building

Open the project in Android Studio (Koala or newer) and run, or from the command line with
the Android SDK installed:

```bash
./gradlew assembleDebug        # build the APK
./gradlew test                 # run the physics and rules unit tests
```

Requirements: Android Studio / AGP 8.13, JDK 17, `compileSdk` 36, `minSdk` 24, and a device
or emulator with OpenGL ES 3.0.

## Publishing on Google Play

The repository builds the signed `.aab` Play wants, targets the API level Play currently
requires, and carries the icon and store graphics. What it cannot carry is a Play account,
a signing key or a card.

[**docs/PLAY_STORE.md**](docs/PLAY_STORE.md) is the whole path from here to a listing, in
order. The short version:

```bash
# once: make an upload key and put it in four GitHub secrets
keytool -genkeypair -v -keystore upload.jks \
  -keyalg RSA -keysize 4096 -validity 10000 -alias mrpool-upload
```

then **Actions → Play release → Run workflow**, and upload the `.aab` it produces.

The upload key is never in this repository and is not recoverable: lose it and the app can
never be updated again. `keystore/debug.keystore` is not a substitute — it is published
here with the Android default password, which is what makes it safe to commit and useless
for a release. A release build with no key configured fails and says what to set, rather
than producing an unsigned bundle Play only rejects after the upload.

One thing worth knowing before the first upload: AdMob keeps the app at `Requires review`
until it is linked to a published listing, so low ad fill at first is expected rather than
a bug. The doc covers it, along with the match server's cold start — the free instance it
runs on sleeps, and the app now waits it out rather than reporting a failure it would have
recovered from a few seconds later.

Store art lives in `docs/store/`, and is drawn by `tools/make_icons.py` along with the
launcher icons, so the icon on a phone and the icon on the listing cannot drift apart.

## Layout

```
app/src/main/java/com/mrpool/eightball/
├── MainActivity.kt          navigation, purchases, stakes and payouts
├── game/                    vectors, balls, table geometry, physics, 8 ball rules, controller
├── ai/                      aim solver, shot candidates, the three robots
├── audio/                   procedural sound synthesis and playback
├── net/                     lockstep online play, matchmaking, the WebSocket client
server/                      the Ktor match server: pairing and relay, nothing else
├── data/                    12 cues, 20 tables, profile and persistence
├── render/                  OpenGL ES 3.0 renderer, meshes, procedural textures
└── ui/                      Compose lobby, shops, wallet, rules and the in game HUD
app/src/test/                physics, rules, audio, netcode and protocol unit tests
```
