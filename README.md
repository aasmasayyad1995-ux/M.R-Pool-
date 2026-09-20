# 🎱 Mr. Pool — 8 Ball Pool, Android 3D Edition

A complete 8 ball pool game for Android: a real 3D table rendered with OpenGL ES 3.0, a
physics engine with follow, draw and english, an opponent with three genuinely different
skill levels, and a coin economy with 12 cue sticks and 20 tables to unlock.

> Are you ready to become Mr. Pool? 🏆

## The lobby

| Option | What it does |
| --- | --- |
| **Play with Robot** | Pick Beginner, Medium or Hard, pay the table's entry fee and play for coins. |
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
| **Beginner** | 0.27 | 21.9% | — | 1 / 12 | Picks a ball by eye, aims roughly, hits too hard, never plans the next shot. |
| **Medium** | 0.47 | 8.9% | 8 / 12 | — | Picks the right ball, rehearses a handful of options, controls speed, plays safe when stuck. |
| **Hard** | 0.67 | 3.7% | 12 / 12 | 10 / 12 | Champion level: rehearses every candidate shot in the physics engine, values where the cue ball finishes, hooks you with a safety when nothing is on, and banks off cushions to escape a snooker. |

Difficulty is not a random number bolted onto a perfect aimer. Each level differs in aim
error, speed control, how many candidate shots it rehearses, whether it weighs position,
whether it plays safeties and whether it uses spin — see `ai/RobotDifficulty.kt`.

## Money

Coins are in-game currency only. Nothing in this app costs real money and there is no
payment code anywhere in it.

* Every match against the robot charges the entry fee of the table you are on (50 coins on
  the free table, up to 12,000 on the championship table).
* The prize is the table's base prize multiplied by the difficulty: **x1.5** beginner,
  **x2.5** medium, **x4** hard.
* Daily bonus of 250 coins, an automatic top up if you go broke, and playing a friend is free.

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
├── data/                    12 cues, 20 tables, profile and persistence
├── render/                  OpenGL ES 3.0 renderer, meshes, procedural textures
└── ui/                      Compose lobby, shops, wallet, rules and the in game HUD
app/src/test/                physics, rules and audio unit tests
```
