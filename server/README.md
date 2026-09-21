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
in so you do not have to write them: [`render.yaml`](render.yaml) and
[`fly.toml`](fly.toml). Copy [`.env.example`](.env.example) to see every setting with an
explanation.

Whatever you pick:

- The host gives the process a `PORT`; the server reads it.
- Health check path: `/health`.
- **If you are taking subscriptions, the service needs a disk mounted at `/data`.** Free
  tiers give you a container with no persistent storage, so `entitlements.jsonl` is wiped
  on every deploy and every subscriber loses the month they paid for. Online play alone
  does not need one — it keeps nothing.

Free tiers also idle a service out after a period of no traffic. The first player to
connect after that waits a few seconds while it wakes up.

### Render, step by step

1. Sign up at [render.com](https://render.com) and connect this GitHub repository.
2. **New → Blueprint**, pick the repo. Render reads `render.yaml` and offers a service
   called `mrpool-server`.
3. It will ask for three values. These never enter the repository:
   - `UPI_ID` — your UPI id, where the money lands, e.g. `yourname@okhdfcbank`
   - `SUBSCRIPTION_PRICE` — a plain number, e.g. `99`
   - `ADMIN_TOKEN` — a long random password. Generate one: `openssl rand -hex 24`
4. Apply. First build takes a few minutes; Gradle is compiling Kotlin inside the image.
5. Check it: open `https://your-service.onrender.com/health`. It should say `ok`.

`render.yaml` asks for the `starter` plan because that is the cheapest one that can have a
disk. Drop it to `free` and delete the `disk:` block only while nobody is paying.

### Fly.io, step by step

Cheaper than a paid Render instance, but it is a command line rather than a web form.

```bash
cd server
fly launch --no-deploy --copy-config     # pick a name; it rewrites `app` in fly.toml
fly volumes create mrpool_data --size 1
fly secrets set UPI_ID=yourname@okhdfcbank SUBSCRIPTION_PRICE=99 \
  ADMIN_TOKEN=$(openssl rand -hex 24)
fly deploy
```

`fly secrets set` prints nothing back, so note the admin token down when you generate it —
you cannot read it out of Fly afterwards, only replace it.

### Then point the app at it

```bash
./gradlew assembleDebug \
  -PmatchServerUrl=wss://your-server/ws \
  -PbillingServerUrl=https://your-server
```

Or set both in CI. Until the app is built with `billingServerUrl`, the Pro screen says
subscriptions are unavailable — which is the correct behaviour, not a bug.

### Checking it works before anyone real pays

With the server running, walk the whole flow with `curl`. This is exactly what the app
does, and it takes a minute:

```bash
SERVER=https://your-server
TOKEN=your-admin-token

# 1. Is billing switched on?
curl -s $SERVER/billing/plan
# {"configured":true,"price":"₹99 / month","upiId":"yourname@okhdfcbank"}

# 2. A player asks to subscribe. Note the reference.
curl -s -X POST $SERVER/billing/payment \
  -H 'Content-Type: application/json' -d '{"player":"test-device"}'
# {"reference":"MRP-K7J2Q","payLink":"upi://pay?pa=...&tn=MRP-K7J2Q",...}

# 3. They say they have paid. This must NOT turn the subscription on.
curl -s -X POST $SERVER/billing/claim -H 'Content-Type: application/json' \
  -d '{"player":"test-device","reference":"MRP-K7J2Q","utr":"test"}'
curl -s "$SERVER/billing/status?player=test-device"
# {"active":false,...,"claim":"SUBMITTED",...}   <- still false. Good.

# 4. Approve it from the page, then check again.
curl -s "$SERVER/billing/status?player=test-device"
# {"active":true,...}
```

Then **redeploy the service and run step 4 again**. If `active` goes back to `false`, your
disk is not persistent and you must fix that before taking a single real rupee.

### Approving payments

Open `https://your-server/billing/admin?token=YOUR_ADMIN_TOKEN` on your phone, beside your
banking app. It lists every payment waiting, with its reference and the transaction id the
player typed. Match the reference and the amount against your account, then press Approve.

Bookmark that URL. Anyone who has it can hand out subscriptions, so treat it like a
password — which is what the token in it is.

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

### The settings

Every one of these is explained in [`.env.example`](.env.example), and
[**Deploy it**](#deploy-it) above has the click-by-click for Render and Fly.

| Variable | |
| --- | --- |
| `UPI_ID` | Your UPI id. Where the money lands. |
| `UPI_PAYEE_NAME` | The name a player's UPI app shows them. |
| `SUBSCRIPTION_PRICE` | Rupees per month, a plain number. |
| `SUBSCRIPTION_DAYS` | How many days one payment buys. Default 30. |
| `ADMIN_TOKEN` | The password for the approvals page. |
| `SUBSCRIPTION_STORE` | Where subscribers are remembered. Must be on a mounted disk. |

`ADMIN_TOKEN` is the one to be careful with. **Leave it blank and subscriptions switch off
entirely** rather than leaving that page open to anyone who finds the URL — a test
enforces that. Make it long and random (`openssl rand -hex 24`); it is the only thing
between the internet and a page that hands out subscriptions.

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
