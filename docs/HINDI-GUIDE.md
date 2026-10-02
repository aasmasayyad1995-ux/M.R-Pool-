# Mr. Pool 3D — poori guide (Hindi)

**Ye file kyun hai:** taaki aage ka raasta kisi ki yaad par na tike. Jo bhi is project ke
baare mein jaanna zaroori hai, wo sab yahin likha hai. Chat band ho jaye, subscription
khatam ho jaye, mahine beet jaayein — ye file repo mein rahegi aur kaam karti rahegi.

Angrezi mein wahi baat [`PLAY_STORE.md`](PLAY_STORE.md) mein hai.

---

## Sabse pehle: teen baatein jo kabhi mat bhoolna

**1. Upload key kho gayi to app hamesha ke liye marr gaya.**
Google use wapas nahin de sakta — kisi ko nahin deta. App kabhi update nahin hoga. Nayi
app banani padegi, naye naam se, aur purane users chale jayenge. **Do jagah backup rakho.**

**2. Apne ad par kabhi tap mat karna.**
Google ise "invalid traffic" maanta hai aur AdMob account band kar deta hai. Naye
publisher ka account isi se sabse zyada jata hai. Dekhna ho to AdMob mein apna phone
**test device** register karo — phir tap karna surakshit hai.

**3. `keystore/debug.keystore` se asli release kabhi sign mat karna.**
Wo file repo mein jaan-boojh kar hai, aur uska password Android ka default hai — yaani
sabko pata hai. Usse signed app koi bhi nakli bana sakta hai. Code ab khud mana kar deta
hai, par jaan lena zaroori hai ki kyun.

---

## Abhi tak kya ho chuka hai

Ye sab taiyaar hai, dobara karne ki zaroorat nahin:

| Cheez | Kahan |
|---|---|
| App icon 512×512 | `docs/store/icon-512.png` |
| Feature graphic 1024×500 | `docs/store/feature-graphic-1024x500.png` |
| 7 screenshots | `docs/store/screenshots/` — **01 se 07 ke kram mein** |
| Short + full description | `docs/store/listing.md` |
| Privacy policy (live) | https://aasmasayyad1995-ux.github.io/M.R-Pool-/privacy-policy.html |
| Contact email | `infinitycore.studio@gmail.com` |
| Release signing ka intezaam | `app/build.gradle.kts` |
| `.aab` banane ka workflow | Actions → **Play release** |
| AdMob ki asli IDs | `.github/workflows/build.yml` mein |

Code bhi taiyaar hai: **targetSdk 36** (Play ki aaj ki zaroorat), edge-to-edge, Android 7
ke liye icon, aur **173 tests**.

---

## PHASE 1 — Play Console account

Kaam kam, **intezaar zyada**. Isliye sabse pehle.

1. `play.google.com/console` → Google account se sign in
2. Account ka prakaar: **Personal** *(Organisation mat chunna — D-U-N-S number maangta hai)*
3. **$25 one-time** fee, card se
4. **Identity verification** — ID aur address. Google isme **kuch din se kuch hafte** le sakta hai
5. Payment profile bharo — ads ki kamai wahin aayegi

---

## PHASE 2 — App banao

Dashboard → **Create app**

| Khana | Kya |
|---|---|
| App name | `Mr. Pool 3D` |
| Default language | English (US) ya Hindi |
| App or game | **Game** |
| Free or paid | **Free** |

> ⚠️ **Free ek baar chunne ke baad paid nahin kiya ja sakta.** Hamari kamai ads se hai, to Free hi sahi hai.

Do declaration par tick → **Create app**.

---

## PHASE 3 — Forms

> 🔴 **Jhooth mat bolna.** Google jaanch karta hai, aur galat jawab par app baad mein bhi hat sakta hai.

### 3.1 App access
**"All functionality is available without special access"**
*Game mein koi login nahin hai. Ye na batao to reviewer login dhoondhta rahega aur
"app doesn't work" bol dega.*

### 3.2 Ads
**Yes, my app contains ads** ✔

### 3.3 Content rating (IARC)

| Sawaal | Jawab | Kyun |
|---|---|---|
| Violence | Nahin | |
| Sexual content | Nahin | |
| Bad language | Nahin | chat hai hi nahin |
| Controlled substances | Nahin | |
| **Gambling** | **Nahin** | koi betting nahin; coins na khareede ja sakte hain, na paise mein badle |
| Users can interact | **Haan** | online match hai |
| Users share location | Nahin | |
| Users share personal info | Nahin | sirf chuna hua naam dikhta hai |

### 3.4 Target audience
**13 and over**
*Under-13 chunne par Families policy lag jaati hai — ads par bhaari pabandi.*

### 3.5 Data safety

**Does your app collect or share user data?** → **Haan**

| Data | Collected | Shared | Purpose |
|---|---|---|---|
| Device or other IDs → **Advertising ID** | Haan | Haan (Google/AdMob) | Advertising or marketing |
| Personal info → **Name** | Haan | Haan | App functionality |

Ye **NAHIN** batana, kyunki sach mein nahin hai:
- ❌ **Photos** — profile picture phone se bahar jaati hi nahin
- ❌ Email, phone number, contacts, location, files, financial info

> Naam wale par agar **"Data is processed ephemerally"** ka option mile to **haan** karna —
> server naam sirf opponent tak pahuncha kar bhool jata hai, kahin likhta nahin.

> 💡 Saare jawab privacy policy ke **"At a glance"** table mein hain. Dono ka mel khana
> zaroori hai — Google milata hai.

### 3.6 Baaki
Government app: **Nahin** · Financial features: **Koi nahin** · Health: **Nahin** · News: **Nahin**

### 3.7 Privacy policy URL
```
https://aasmasayyad1995-ux.github.io/M.R-Pool-/privacy-policy.html
```

---

## PHASE 4 — Store listing

Sab `docs/store/` se copy-paste:

| Khana | Kahan se |
|---|---|
| Short description | `listing.md` |
| Full description | `listing.md` |
| App icon | `store/icon-512.png` |
| Feature graphic | `store/feature-graphic-1024x500.png` |
| Screenshots | `store/screenshots/` — **kram mat badalna** |
| Category | **Games → Sports** |
| Contact email | `infinitycore.studio@gmail.com` |

> Screenshots ka kram isliye zaroori hai ki Play ke search mein **sirf pehle do** dikhte
> hain — aur wahan jaan-boojh kar table rakha hai, menu nahin.

---

## PHASE 5 — Upload key aur bundle

**Yahan computer chahiye** (JDK ke saath).

**1. Key banao**
```bash
keytool -genkeypair -v -keystore upload.jks \
  -keyalg RSA -keysize 4096 -validity 10000 -alias mrpool-upload
```
Do password maangega (dono same rakh lo). Naam/sheher wale khane kisi ko dikhte nahin.

**2. Text mein badlo**
```bash
base64 -w0 upload.jks > upload.jks.base64                 # Linux
base64 -i upload.jks | tr -d '\n' > upload.jks.base64     # Mac
```

**3. GitHub secrets daalo** — repo → Settings → Secrets and variables → Actions

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `upload.jks.base64` ka poora text |
| `RELEASE_STORE_PASSWORD` | keystore ka password |
| `RELEASE_KEY_ALIAS` | `mrpool-upload` |
| `RELEASE_KEY_PASSWORD` | key ka password |

**4. Bundle banao** — GitHub → Actions → **Play release** → Run workflow
versionName `1.0`, versionCode khali chhod do.

Artifact mein do file milengi:
- `app-release.aab` ← **yahi Play par jaati hai**
- `app-release.apk` ← phone par install karke pehle khud check karne ke liye

**5. Play App Signing** — upload ke waqt Google puchhega. **Zaroor on karna.** Isi se key
kho jane par recovery ka raasta khulta hai.

---

## PHASE 6 — Testing

> Naya personal account **seedha production par publish nahin kar sakta.**

1. **Internal testing** — apne aap ko tester daal kar install karke dekho. Turant.
2. **Closed testing** — Google abhi **12 testers, 14 din lagatar** maangta hai.
   *Ye niyam badalta rehta hai — **Console jo dikhaye wahi sach maano.***
3. 14 din baad **"Apply for production access"** ka form.

**12 log abhi se jutana shuru kar do.** Unka Google account ka email chahiye.

---

## PHASE 7 — Submit

Production → Create new release → `.aab` upload → **Send for review**.
Google ko **kuch din se do hafte**.

---

## PHASE 8 — Live hone ke baad

**AdMob → Apps → Mr. Pool 3D → App settings → link to Play Store listing**

Tabhi `Requires review` hatega aur ads aana shuru honge. Us se pehle ad na dikhna
**normal hai, kharabi nahin** — Google ka niyam hai ki app kisi store par publish ho,
tabhi wo review karta hai.

---

## Match server — submit se pehle padh lena

Game bina internet chalta hi nahin, aur online wala server **free Render** par hai jo
**15 minute khali rehne par so jata hai** aur jagne mein ~1 minute leta hai.

Reviewer ki connection **hamesha** "lambe gap ke baad pehli" hoti hai. Pehle isse app
toota hua dikhta tha. **Ab code ~75 second tak koshish karta hai** aur screen par likhta
hai ki minute lag sakta hai — review ke liye itna kaafi hai.

Lekin asli players ke liye wo intezaar achha nahin. Game chalne lage to server **paid
plan** par le jaana. Vikalp `server/README.md` mein hain.

---

## Repo mein kaun si file kya hai

```
app/src/main/java/com/mrpool/eightball/
├── game/      physics, 8 ball ke rules, table ki geometry
├── ai/        teen robot
├── net/       online play, matchmaking
├── data/      coins, profile, cues, tables
├── render/    OpenGL se table banana
├── audio/     aawaazein — recording nahin, code se banti hain
├── ui/        saari screens
└── ads/       AdMob — kab dikhana hai (AdPolicy) aur kaise (AdsManager)

server/        match server (Ktor) — sirf do players ko jodta hai
docs/          ye guide, privacy policy, store ka saaman
tools/         icon aur screenshot banane ki script
.github/workflows/
├── build.yml      har push par APK banata hai
└── release.yml    Play ke liye signed .aab (manual)
```

---

## Agar kuch toot jaye

**APK kahan milti hai**
`github.com/aasmasayyad1995-ux/M.R-Pool-/releases` → `latest` → `app-debug.apk`
Har baar `main` badalne par apne aap nayi ban jaati hai.

**Phone par kaunsi build chal rahi hai**
App mein **How to Play → sabse neeche** scroll karo. Wahan `v1.0 · build abc1234` likha
hai. Wahi number GitHub par commit ka hai.

**Build fail ho gaya**
GitHub → **Actions** → laal wale run par tap → neeche error dikhega.

**Tests kaise chalte hain**
Har push par apne aap. 173 tests hain. Laal hone ka matlab kuch toota hai — **merge mat
karna** jab tak hara na ho.

**TEST ADS likha dikhe lobby mein**
Matlab build asli AdMob IDs ke bina bani hai. `build.yml` mein `-PadmobAppId=...` wali
line check karo.

---

## AdMob ki IDs

Ye **secret nahin hain** — har shipped Android app inhe khule mein le kar chalta hai.
Isliye ye workflow file mein hain, GitHub secrets mein nahin.

| | |
|---|---|
| App ID | `ca-app-pub-5938763112022930~7070476193` |
| Banner | `ca-app-pub-5938763112022930/1196167131` |
| Interstitial | `ca-app-pub-5938763112022930/5559841563` |
| Rewarded | `ca-app-pub-5938763112022930/5727362529` |

**Upload key secret hai. Ye nahin.** Farak itna hai: upload key se koi aapke naam par
nakli update bhej sakta hai; in IDs se kuch nahin kar sakta.

---

## Game ke andar paise ka hisaab

| | |
|---|---|
| Shuru mein | 1500 coins |
| Beginner bot jeetne par | 25 |
| Medium | 50 |
| Hard | 100 |
| Roz ka bonus | 50 (ek ad dekhne ke baad) |
| Ek rewarded ad | 10 — **ginti ki koi seema nahin** |
| Sabse mehnga cue | 5,000 |
| Sabse mehnga table | 50,000 |

> ⚠️ **`REWARD_COINS = 10` ko badhane se pehle sochna.** Ad ki ginti par ab koi rok nahin
> hai, isliye poora santulan isi ek number par tika hai. 20 karte hi ek ad ek aasan jeet
> ke barabar ho jayega aur khelna bemaani ho jayega. (Ek test isi par pehra deta hai.)
>
> 10 par hisaab aisa baithta hai: sabse mehnga table 50,000 ka hai, yaani **5,000 ads** —
> lagbhag 40 ghante dekhna. Ya 500 baar hard bot ko haraana. Table hi behtar sauda hai,
> aur poore game ka matlab yahi hai.

---

## Jo kabhi nahin badalna chahiye

- **Profile picture phone se bahar nahin jaati.** Online opponent ko sirf naam dikhta hai.
  Ye privacy policy mein likha hai — badlo to policy bhi badalni padegi.
- **App mein paise ka koi lena-dena nahin.** Na in-app purchase, na billing ka code.
  Coins khareede nahin ja sakte. Isi se wo "gambling nahin hai" wala jawab sach rehta hai.
- **Game ko internet chahiye.** Ye jaan-boojh kar hai, kharabi nahin.
