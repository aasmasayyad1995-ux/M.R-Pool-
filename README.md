# 🎱 Mr. Pool — 8 Ball Pool, Android 3D Edition

A complete 8 ball pool game for Android: a real 3D table rendered with OpenGL ES 3.0, a
physics engine with follow, draw and english, an opponent with three genuinely different
skill levels, and a coin economy with 12 cue sticks and 20 tables to unlock.

> Are you ready to become Mr. Pool? 🏆

## Opening

The app opens on the Infinity Core studio card: the infinity mark draws itself as a single
continuous stroke — a real lemniscate of Bernoulli, not a glyph, which is what lets one
animated stroke travel the whole figure of eight — and the name fades up underneath it. A
tap anywhere skips to the lobby.

## The lobby

| Option | What it does |
| --- | --- |
| **Play with Robot** | Pick Beginner, Medium or Hard. Free to play, and a win pays 25, 50 or 100 coins. |
| **Play with Friend** | Two players on one device. No entry fee. |
| **Choose Cue Stick** | 12 cues. The house cue is free, the rest start at **200 coins**. |
| **Table Selection** | 20 tables. The local club is free, the rest start at **1,000 coins**. |
| **Coins & Rewards** | Balance, daily bonus, and exactly what every match pays. |
| **How to Play Pool** | Rules, controls, spin, position play and safety play. |

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

Coins are in-game currency only. Nothing in this app costs real money and there is no
payment code anywhere in it.

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
* A foul gives the opponent ball in hand anywhere on the table.
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

Deploy it anywhere that takes a Dockerfile — Render, Railway and Fly all have a free tier
that fits. Then build the app pointing at it:

```bash
./gradlew assembleDebug -PmatchServerUrl=wss://your-server.example.com/ws
```

Leave that property out and online play is simply switched off; the rest of the game is
unaffected and the online screen says what is missing.

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

Controls: drag with one finger to aim, drag with two to orbit, pinch to zoom, drag the power
bar for speed, and open the spin pad to move the tip off centre.

## Installing it on a phone

Every push to `main` publishes the APK to the
[latest release](../../releases/latest/download/app-debug.apk). Open that link on an Android
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

Requirements: Android Studio / AGP 8.5, JDK 17, `compileSdk` 34, `minSdk` 24, and a device
or emulator with OpenGL ES 3.0.

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
