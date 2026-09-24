# AdMob — what is wired up, and what is still yours to do

The code is done. What is left needs an AdMob account and a Play Console account, which
are yours, not the repository's.

## What the build ships with today

**Google's own test ad units.** They are published by Google for exactly this, they are
safe to build, run and share, and they serve real-looking ads marked "Test Ad".

> **Never test with your real ad units.** Google reads your own taps on your own ads as
> invalid traffic and closes AdMob accounts for it — that is the single most common way a
> new publisher loses their account. The test ids are the default precisely so nobody has
> to remember to switch to them.

The lobby says **TEST ADS** in the corner while the test ids are in use, so a build can
never quietly be the wrong one.

## Putting your own ids in

Create the app and three ad units in [AdMob](https://apps.admob.com), then build with:

```bash
./gradlew assembleRelease \
  -PadmobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY \
  -PadmobBannerUnit=ca-app-pub-XXXXXXXXXXXXXXXX/AAAAAAAAAA \
  -PadmobInterstitialUnit=ca-app-pub-XXXXXXXXXXXXXXXX/BBBBBBBBBB \
  -PadmobRewardedUnit=ca-app-pub-XXXXXXXXXXXXXXXX/CCCCCCCCCC
```

Leave any of them out and that one falls back to Google's test unit. None of these are
secrets — every shipped Android app carries them in the clear — so they are properties for
convenience, not for safety.

In AdMob the three units are:

| Unit | Format |
| --- | --- |
| Banner | Banner |
| Interstitial | Interstitial |
| Rewarded | Rewarded |

## Where the ads appear

| | Where | How often |
| --- | --- | --- |
| **Banner** | A 50dp strip under the menus — lobby, shops, wallet, profile | Always on those screens. **Never** over the table and never over the studio card. |
| **Interstitial** | On the way from a finished match back to the lobby | Every third finished match, and never the first. The table is never interrupted and the result is never covered. |
| **Rewarded** | A button in **Coins & Rewards** | Only when the player presses it. Four a day, 10 coins each. |

The rules are in `AdPolicy`, which holds no Android types and is tested — including a test
that a day of watching ads pays less than a couple of wins against the hard robot, so the
matches still mean something.

## Still yours to do before Play Store

- [ ] **Declare that the app contains ads.** Play Console → *App content* → *Ads* → "Yes".
      Getting this wrong is a listing suspension, not a warning.
- [ ] **Write a privacy policy and host it.** AdMob requires one, and Play Console asks for
      the URL. It has to say that the app shows ads, that Google may collect an advertising
      identifier, and how to contact you.
- [ ] **Fill in the Data safety form.** The ads SDK adds the `AD_ID` permission, so the
      form must declare that the app collects an advertising id for advertising.
- [ ] **Answer the target audience questions.** If the app is ever marked as for children,
      personalised ads must be turned off — AdMob and Play both enforce that.
- [ ] **Consent for players in the EEA, the UK and Switzerland.** Google requires a consent
      message there, built with the User Messaging Platform (UMP) SDK. It is *not* wired up
      yet. If you only publish in India you can leave it, but the moment the listing is
      available in Europe it is required.
- [ ] **Sign the release with a real key.** The key in `keystore/` is the checked-in debug
      key. It must never sign a release.
- [ ] **Link AdMob to your Play Console app**, or AdMob cannot verify the app and fill
      rates stay poor.

## What is deliberately not here

**No UMP consent flow.** It is real work and it only matters for Europe. Everything else is
wired so it can be added without touching the rest.

**No mediation, no other ad networks.** One network, one thing to debug.

**No ads during a match, in any form.** A full screen ad mid-frame, or a strip eating a
thumb's width of the cushion, would make the game worse to play — and a game people stop
playing earns nothing.
