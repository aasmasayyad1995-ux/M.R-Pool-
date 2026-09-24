# Publishing Mr. Pool 3D on Google Play

Everything the repository can do for itself is done. What is left needs a Play account,
a signing key and a card, and none of those can live in a git repository.

Work through it in order. Steps 1 to 4 are one-off setup; step 5 is what you repeat for
every release.

---

## 0. What is already done

| | |
|---|---|
| Signed bundle | `.github/workflows/release.yml` builds the `.aab` Play wants |
| Release signing | `app/build.gradle.kts`, fed from secrets, never from the debug key |
| Target API 36 | Required of new apps since 31 Aug 2026 |
| Edge to edge | Handled in `MainActivity`, so API 35+ does not put buttons under the navigation bar |
| Launcher icon | PNGs for Android 7, adaptive icon for Android 8+ |
| Store graphics | `docs/store/icon-512.png`, `docs/store/feature-graphic-1024x500.png` |
| Privacy policy | `docs/privacy-policy.html` — written and filled in; needs GitHub Pages turning on |

---

## 1. The Play Console account

1. Go to <https://play.google.com/console> and sign up.
2. Pay the **one-off $25** registration fee.
3. Complete **identity verification** — a government ID and an address. Google can take
   a few days over this, so start it before you need it.

Choose a **personal** account unless you have a registered company; an organisation
account also needs a D-U-N-S number, which takes longer.

---

## 2. The upload key

This key is how Google knows an update really came from you. **If you lose it, the app can
never be updated again** — not by you, not by Google. Keep a copy somewhere that is not
just your phone.

On a computer with a JDK installed:

```bash
keytool -genkeypair -v \
  -keystore upload.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias mrpool-upload
```

It asks for two passwords (use the same one for both, it is simpler) and some name and
address fields — those are not shown to anybody, so they only need to be truthful enough.

> **Never** use `keystore/debug.keystore` from this repository. It is published here with
> the Android default password, so anything signed with it can be forged by anybody who
> reads the repo. That is exactly why it is safe to check in and useless for a release.

Then turn it into text so it can be stored as a secret:

```bash
base64 -w0 upload.jks > upload.jks.base64     # Linux
base64 -i upload.jks | tr -d '\n' > upload.jks.base64   # macOS
```

---

## 3. Four GitHub secrets

In the repository: **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the whole contents of `upload.jks.base64` |
| `RELEASE_STORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `mrpool-upload` |
| `RELEASE_KEY_PASSWORD` | the key password |

These really are secrets, unlike the AdMob ids — anyone holding them can sign an update
that Play and every phone will accept as genuinely yours.

---

## 4. Fill in the privacy policy and publish it

Play requires a working privacy policy URL. This repo has the policy written; it needs
your contact address and somewhere to live.

The contact address is already filled in. It is shown publicly on the listing, so if it
ever needs to change, it is the one `mailto:` line at the bottom of the file.

1. In the repository: **Settings → Pages → Source: Deploy from a branch**, branch `main`,
   folder `/docs`, then Save.
2. A minute later the policy is live at
   `https://aasmasayyad1995-ux.github.io/M.R-Pool-/privacy-policy.html`.
   Open it and check before you paste it into Play.

Free, and it updates itself whenever the file changes.

---

## 5. Build the bundle

GitHub → **Actions** → **Play release** → **Run workflow**.

- **versionName** — what players see: `1.0`.
- **versionCode** — leave blank and it uses the run number. It only has to be **higher
  than every previous upload**; Play refuses a repeat.

When it finishes, download the artifact at the bottom of the run. It contains
`app-release.aab` (this is what you upload) and `app-release.apk` (sideload this to check
the real release build on a phone first).

The workflow fails on purpose if the bundle came out unsigned, rather than letting you
find out after a rejected upload.

---

## 6. Create the app in Play Console

**Create app**, then fill in:

- App name: `Mr. Pool 3D`
- Type: **Game**, category **Casual** or **Sports**
- Free
- Confirm it follows the developer programme policies

### Store listing

| Field | What to use |
|---|---|
| App icon | `docs/store/icon-512.png` |
| Feature graphic | `docs/store/feature-graphic-1024x500.png` |
| Phone screenshots | **At least 2.** Take them on a phone: the lobby and a match in progress. Landscape, since the game is landscape. |
| Short description | max 80 characters, e.g. *Play 8 ball pool in 3D against friends online or against the bots.* |
| Full description | up to 4000 characters |
| Contact email | the same one as in the privacy policy |
| Privacy policy | the GitHub Pages URL from step 4 |

### The forms Play will not let you skip

- **Data safety** — declare that the app collects an **advertising ID** for ads, and that
  a **player name** is sent during online play. Everything else stays on the device.
  `docs/privacy-policy.html` has the full list; answer from that table and the two will
  agree, which matters — Google checks.
- **Content rating** — an IARC questionnaire. Answer honestly.
- **Ads** — **yes, this app contains ads**.
- **Gambling** — **no**. True, and worth being sure of: there is no payment code in the
  app at all, coins cannot be bought, and they cannot be turned back into money.
- **Target audience** — 13+ is the straightforward answer. Choosing under-13 pulls in
  Families policy requirements and restricts which ads may be served.
- **App access** — no login required; say so, or a reviewer will look for one.

---

## 7. Closed testing before production

A new personal developer account normally cannot publish straight to production. Google
currently asks for a **closed test with at least 12 testers who stay opted in for 14
days**. The exact numbers change, so read what your own Console says.

Plan for it: it is two weeks of calendar time, not two weeks of work. Start the closed
test as soon as the first bundle builds.

---

## 8. After it is live: finish AdMob

AdMob shows **Mr. Pool 3D** as `Requires review` until the app is linked to a real store
listing. Once the app is published:

**AdMob → Apps → Mr. Pool 3D → App settings → link to the Play Store listing.**

Fill in payment details in AdMob at the same time. Until both are done, expect low ad fill
or none — that is not a bug in the app.

> **Never tap your own ads**, at any point, on any device. Google reads it as invalid
> traffic and it is the commonest way a new publisher loses their AdMob account. To test
> the ads safely, register your phone as an AdMob test device first.

---

## The match server, and why a reviewer would have seen a broken game

The game requires an internet connection by design, and online play points at a **free**
Render instance that **sleeps after about 15 minutes of no traffic** and takes up to a
minute to wake. A reviewer's connection is, by definition, the first one after a quiet
spell.

Until recently that was fatal. While the instance wakes it does not answer slowly — it
*refuses*, instantly, with an error page of its own, and the app gave up after one try and
showed a red failure. Tapping Play Online as the first person in an hour reliably looked
like a game that does not work.

The app now keeps asking for about 75 seconds, with a widening gap between attempts, and
says on screen that the first connect after a quiet spell can take a minute. See
`net/ServerWake.kt`. A Cancel button sits next to it throughout.

That is enough for a review, and it is the right behaviour whatever the server runs on.
It does not make the wait pleasant for a real player, though, so if the game gets any
traffic worth having, move the server to a paid instance that does not sleep —
`server/README.md` has the options.
