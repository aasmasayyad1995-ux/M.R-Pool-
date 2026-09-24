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

## The ids this repository builds with

The real ones, wired into `.github/workflows/build.yml`, so every published build carries
them:

| | |
| --- | --- |
| App ID | `ca-app-pub-5938763112022930~7070476193` |
| Banner | `ca-app-pub-5938763112022930/1196167131` |
| Interstitial | `ca-app-pub-5938763112022930/5559841563` |
| Rewarded | `ca-app-pub-5938763112022930/5727362529` |

They are not secrets — every shipped Android app carries them in the clear — so they are in
the workflow rather than in repository secrets, where they would be a nuisance for no safety
at all.

Because `admobAppId` is now set, the lobby no longer prints **TEST ADS**. A build either has
real ids and says nothing, or has test ids and says so.

> **The ads in this build are real.** Do not tap them, and do not let anybody else tap them
> to "see if it works". Google reads taps on your own ads as invalid traffic and closes
> AdMob accounts for it. Look at them; leave them alone.
>
> If you want to be able to tap freely while testing, the fix is to register the phone as a
> test device — then the real ad units serve test ads to that phone only. It needs the
> device's AdMob id, which the app prints to logcat the first time it asks for an ad.

## Putting different ids in

To build with different ids — a second app, or back to the test units — pass them in:

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
| **Rewarded** | The daily bonus, in **Coins & Rewards** | The 50 coin daily bonus is paid for by watching an ad. Once a day, and it does not count against the four above. |

The rules are in `AdPolicy`, which holds no Android types and is tested — including a test
that a day of watching ads pays less than a couple of wins against the hard robot, so the
matches still mean something.

**If no ad turns up, the daily bonus is paid anyway.** A player who pressed the button and
was shown nothing has done everything asked of them, and taking their bonus away because an
advert did not fill would be punishing them for our failure — and it is the complaint that
arrives first, because fill is worst in exactly the places phones are worst. Backing out of
an ad that *did* play is a choice: it pays nothing, and the button is still there.

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
