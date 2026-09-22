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

There is a `Dockerfile`, so anything that takes one will do. Two config files are checked
in so you do not have to write them: [`render.yaml`](../render.yaml) and
[`fly.toml`](fly.toml).

Whatever you pick:

- The host gives the process a `PORT`; the server reads it.
- Health check path: `/health`.
- **It needs nothing but the container.** The server keeps no state, so a free tier is
  genuinely fine for it and no disk is needed.

Free tiers also idle a service out after a period of no traffic. The first player to
connect after that waits a few seconds while it wakes up.

### Render, step by step

The free tier suits this. The server keeps no state between matches, so there is nothing
a recycled container can lose.

1. Sign up at [render.com](https://render.com) and connect this GitHub repository.
2. **New → Blueprint**, pick the repo. Leave **Blueprint Path** empty — `render.yaml`
   sits at the repo root, which is where Render looks by default.
3. Name it `mrpool-server`, branch `main`, and apply. First build takes a few minutes;
   Gradle is compiling Kotlin inside the image. No card needed.
4. Check it: open `https://your-service.onrender.com/health`. It should say `ok`.

A free instance idles out after a while with no traffic, so the first player to connect
after a quiet spell waits a few seconds while it wakes up.

### Fly.io, step by step

A command line rather than a web form.

```bash
cd server
fly launch --no-deploy --copy-config     # pick a name; it rewrites `app` in fly.toml
fly deploy
```

### Then point the app at it

```bash
./gradlew assembleDebug -PmatchServerUrl=wss://your-server/ws
```

Or set it in CI. Without it, online play is switched off and the online screen says so.

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
| `{"op":"report","lines":["…"]}` | Reports the other player. Written to the log; nothing comes back |

Server to client:

| Message | Meaning |
| --- | --- |
| `{"op":"created","code":"ABCDE"}` | Your room is open |
| `{"op":"searching"}` | You are in the queue |
| `{"op":"start","code","seat","host","seed","opponent"}` | Play. Both sides get the same `seed` |
| `{"op":"peer","body":{…}}` | The other player sent this |
| `{"op":"gone"}` | They left, or their connection dropped |
| `{"op":"error","reason":"…"}` | That did not work |

Chat is not in that table on purpose: it goes through `relay` like everything else, because
the server does not read what it forwards. The filter, the flood limit and the mute are all
in the app, on both sides of the wire.

## Reports

`report` writes one line to the server's log — the room, who reported, who they reported and
up to six of the lines complained of — and returns nothing. One report per player per room,
so a changed app cannot fill the log by holding the button down.

**That is the whole of it, and the app says so to the player.** There are no accounts here,
so there is nobody to ban: a player is a name typed into a box and a socket that closes when
they leave. A report leaves a record somebody can read. The mute in the app is what actually
stops the messages, and it needs no server at all.

## What it does not do yet

No accounts, no rate limiting and no persistence — rooms live in memory and vanish when the
process restarts, which is fine for matches that last minutes. Anyone who knows the URL can
connect. Before this carries anything that matters, it needs at least a rate limit per
connection and a cap on rooms per address.

Note what that means for chat: the flood limit lives in the app, at both ends, so a changed
client can still push messages at the relay as fast as it likes. The receiving app throws
them away, which protects the player, but not the server. A per-connection limit here is the
missing piece.
