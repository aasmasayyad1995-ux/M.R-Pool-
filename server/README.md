# Mr. Pool match server

Pairs two players and forwards messages between them. That is all it does.

It never simulates a game of pool and never reads a move: a move's contents arrive as an
opaque blob and leave untouched. Both devices replay the same shots through the same
deterministic physics, so the rules live in the app — and this server never needs
redeploying when they change.

## Run it

```bash
./gradlew run     # listens on $PORT, or 8080
./gradlew test    # 16 tests
```

Check it is alive at `http://localhost:8080/health`.

## Point the app at it

```bash
cd ..
./gradlew assembleDebug -PmatchServerUrl=ws://10.0.2.2:8080/ws     # Android emulator
./gradlew assembleDebug -PmatchServerUrl=wss://your-server/ws      # deployed
```

Use `wss://` in production. `ws://` is plaintext, and Android blocks it by default anyway
outside of localhost.

## Deploy it

There is a `Dockerfile`, so anything that takes one will do — Render, Railway and Fly all
have a free tier that fits this comfortably. Two things to set:

- The host must give the process a `PORT`; the server reads it.
- Health check path: `/health`.

Free tiers usually idle a service out after a period of no traffic. The first player to
connect after that will wait a few seconds while it wakes up.

## The protocol

Client to server:

| Message | Meaning |
| --- | --- |
| `{"op":"hello","name":"Asad"}` | Sets the name the opponent will see |
| `{"op":"create"}` | Opens a private room |
| `{"op":"join","code":"ABCDE"}` | Joins one |
| `{"op":"queue"}` | Takes a seat in the quick match queue |
| `{"op":"leave"}` | Leaves the room or the queue |
| `{"op":"relay","body":{…}}` | Sends `body` to the other player, untouched |

Server to client:

| Message | Meaning |
| --- | --- |
| `{"op":"created","code":"ABCDE"}` | Your room is open |
| `{"op":"searching"}` | You are in the queue |
| `{"op":"start","code","seat","host","seed","opponent"}` | Play. Both sides get the same `seed` |
| `{"op":"peer","body":{…}}` | The other player sent this |
| `{"op":"gone"}` | They left, or their connection dropped |
| `{"op":"error","reason":"…"}` | That did not work |

## What it does not do yet

No accounts, no rate limiting and no persistence — rooms live in memory and vanish when the
process restarts, which is fine for matches that last minutes. Anyone who knows the URL can
connect. Before this carries anything that matters, it needs at least a rate limit per
connection and a cap on rooms per address.
