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

The server also sells **Mr. Pool Pro**, a monthly subscription, paid by UPI straight into
the owner's account. There is no payment gateway, which means two things worth being blunt
about:

* **Nothing tells this server that money arrived.** UPI sends no callback to anyone but
  the bank. So a payment becomes a subscription only when the owner opens the approvals
  page, checks their own bank statement, and presses Approve.
* **Nothing renews by itself.** UPI AutoPay needs a payment provider. Every month the
  player pays again and claims again.

That is the trade for taking no commission and needing no KYC beyond a bank account.

### How a payment works

1. The player taps Subscribe. The server mints a short reference — `MRP-K7J2Q` — and
   returns a `upi://pay` link carrying the owner's UPI id, the amount, and that reference
   in the transaction note.
2. The phone opens the link in GPay, PhonePe or Paytm. The money goes to the owner. The
   game never sees a UPI id, a PIN or a card.
3. The player taps **I have paid** and may type their transaction id. This grants nothing:
   it moves the payment into the owner's queue.
4. The owner opens `https://your-server/billing/admin?token=…`, matches the reference
   against a line in their bank statement, and presses Approve. That is the only thing in
   this system that turns a subscription on.

The reference is load bearing: it is the one link between a line in a bank statement and a
player. It uses the room code alphabet, which already leaves out the characters that look
alike, because a reference misread as another is a payment credited to the wrong person.

### Endpoints

| Method | Path | What it does |
| --- | --- | --- |
| `GET` | `/billing/plan` | Whether subscriptions are configured, the price, the UPI id |
| `GET` | `/billing/status?player=<id>` | Whether that install has paid, and where its last payment stands |
| `POST` | `/billing/payment` | Mints a reference and returns the `upi://` link |
| `POST` | `/billing/claim` | The player says they paid — queues it, grants nothing |
| `GET` | `/billing/admin?token=…` | The owner's approvals page |
| `POST` | `/billing/admin/decide` | Approve or reject one payment |
| `POST` | `/billing/admin/revoke` | Take a subscription back |

### Setting it up

Deploy with these environment variables:

```
UPI_ID=yourname@okhdfcbank
UPI_PAYEE_NAME=Mr. Pool
SUBSCRIPTION_PRICE=99
ADMIN_TOKEN=<a long random string>
SUBSCRIPTION_DAYS=30
SUBSCRIPTION_STORE=/data/entitlements.jsonl
```

Then build the app pointing at it:
`./gradlew assembleDebug -PbillingServerUrl=https://your-server`

`ADMIN_TOKEN` is the password for the approvals page. **Leave it blank and subscriptions
switch off entirely** rather than leaving that page open to anyone who finds the URL — a
test enforces that. Make it long and random; it is the only thing between the internet and
a page that hands out subscriptions.

### Things to know before this takes real money

* **`SUBSCRIPTION_STORE` must be on a disk that survives a redeploy.** Most free hosting
  tiers give you a container with no persistent volume, and there the file is wiped on
  every deploy — every subscriber loses what they paid for. Point it at a mounted volume,
  or move `Entitlements` onto a real database.
* **Approving is manual and does not scale.** A handful of players a week is an evening's
  work; a few hundred is a job. At that point a payment provider stops being optional.
* **The subscription belongs to an install, not a person.** There are no accounts in this
  game. Clearing the app's data or changing phone loses the subscription, and there is no
  way to restore it. Fixing that means adding sign-in, which nothing here has yet.
* **The owner is trusting the player's word only as far as their bank statement.** A claim
  carries whatever the player typed. It is shown escaped on the approvals page — the
  transaction id is free text from the internet, and that page is opened by the one person
  who can grant subscriptions — but it proves nothing. Always check the bank.
* **`/billing/status` is unauthenticated.** It reveals nothing but a boolean, a date and a
  reference, and a player id is a random UUID, but anyone holding one can read its status.
* The throttle on `/billing/payment` is eight per address per hour. That stops a runaway
  retry loop, not a determined attacker.
* Google Play does not allow an app distributed through the Play Store to take payment for
  digital goods this way. This is fine for a sideloaded APK; publishing to Play means
  moving to Play Billing.
