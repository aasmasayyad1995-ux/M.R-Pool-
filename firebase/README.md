# Firebase setup for online play

Online play needs a Firebase project. Everything else in the game — the robot, the two
player game on one device, the shops — works without it, and the app says so politely if
this step is skipped.

## 1. Create the project

1. Go to https://console.firebase.google.com and create a project.
2. Add an **Android app** to it with the package name `com.mrpool.eightball`.
3. Download the `google-services.json` it gives you and put it in `app/`.

## 2. Create the database

1. In the console, open **Build → Realtime Database** and create one.
2. Pick the region closest to your players — it decides the latency of every shot.

## 3. Publish the rules

Copy `database.rules.json` from this folder into the **Rules** tab and publish.

## A warning about these rules

The rules here are open: anyone who has your database URL can read and write rooms. That is
deliberate for a first version — the app has no sign-in, so there is no identity to check a
rule against — and it is fine while the database holds nothing but throwaway pool matches.

It is **not** fine if you ever store anything about your players in it. Before that, add
Firebase **Anonymous Authentication** and tighten the rules to:

```json
".read": "auth != null",
".write": "auth != null"
```

and then narrow them further so a player can only write their own moves.

## What it costs

A whole match is a few hundred bytes: each shot is four numbers, and both devices replay it
through the same simulation rather than streaming positions. The Spark (free) plan covers a
great many matches. Old rooms are not cleaned up automatically — if the database ever grows,
add a scheduled function that deletes rooms older than a day.
