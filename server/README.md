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

## Subscriptions

The server also sells **Mr. Pool Pro**, a monthly subscription, through Razorpay. It is
inert until the keys below are in the environment: without them the endpoints answer
"not configured" and the game carries on with everything free-to-play.

### What the app can and cannot say

The app never asserts that it has a subscription. It can ask, and it can start one; the
answer comes from `Entitlements`, which is only ever written by a webhook whose HMAC-SHA256
signature Razorpay produced. A forged webhook is refused, and a signed webhook about a
subscription this server never created is refused too — otherwise anyone could name their
own player id in the notes and hand themselves a subscription.

Card and UPI details never touch this server or the app. `POST /billing/subscribe` creates
a subscription at Razorpay and returns Razorpay's own hosted payment page; the phone opens
that in its browser.

### Endpoints

| Method | Path | What it does |
| --- | --- | --- |
| `GET` | `/billing/plan` | Whether subscriptions are configured, and the price to show |
| `GET` | `/billing/status?player=<id>` | Whether that install has paid, and until when |
| `POST` | `/billing/subscribe` | Creates a subscription, returns the payment page URL |
| `POST` | `/billing/cancel` | Stops the renewal at the end of the paid cycle |
| `POST` | `/billing/webhook` | Razorpay's signed callback — the only thing that grants Pro |

### Setting it up

1. Create a Razorpay account and complete its KYC. This needs a business identity and a
   bank account; it is not something this repository can do for you.
2. In the Razorpay dashboard, create a **Plan** — monthly, at whatever price you want —
   and copy its `plan_id`.
3. Create a **Webhook** pointing at `https://your-server/billing/webhook`, subscribed to
   the `subscription.*` events. Copy the webhook secret.
4. Deploy this server with these environment variables:

   ```
   RAZORPAY_KEY_ID=rzp_live_xxxxxxxx
   RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxx
   RAZORPAY_WEBHOOK_SECRET=xxxxxxxxxxxxxxxx
   RAZORPAY_PLAN_ID=plan_xxxxxxxx
   SUBSCRIPTION_PRICE=₹99 / month
   SUBSCRIPTION_STORE=/data/entitlements.jsonl
   ```

5. Build the app pointing at it:
   `./gradlew assembleDebug -PbillingServerUrl=https://your-server`

Start with Razorpay's **test mode** keys. A test-mode subscription goes through the whole
flow without money moving, which is the only sane way to find out whether the webhook is
reaching you.

### Things to know before this takes real money

* **`SUBSCRIPTION_STORE` must be on a disk that survives a redeploy.** Most free hosting
  tiers give you a container with no persistent volume, and there the file is wiped on
  every deploy — which means every subscriber loses what they paid for. Point it at a
  mounted volume, or move `Entitlements` onto a real database.
* **The subscription belongs to an install, not a person.** There are no accounts in this
  game. Clearing the app's data or changing phone loses the subscription, and there is no
  way to restore it. Fixing that means adding sign-in, which nothing here has yet.
* **`/billing/status` is unauthenticated.** It reveals nothing but a boolean and a date,
  and a player id is a random UUID, but anyone holding one can read its status.
* The throttle on `/billing/subscribe` is six per address per hour. That stops a runaway
  retry loop, not a determined attacker.
* Google Play does not allow an app distributed through the Play Store to take payment for
  digital goods this way. This is fine for a sideloaded APK; publishing to Play means
  moving to Play Billing.
