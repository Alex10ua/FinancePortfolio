# FinancePortfolio — Android

A native Android client (Kotlin and Jetpack Compose) for the FinancePortfolio backend. It
shows your data and changes nothing: every request is a `GET`, and the app has no create or
edit actions.

The app has 8 screens. Each one is a port of a mobile mockup in
`portfolio-design/project/src/`:

| Screen | Mockup |
|---|---|
| Sign in, fingerprint lock | `responsive.jsx` → `MobileLogin` |
| All Portfolios | `responsive.jsx` → `MobilePortfolioList` |
| Dashboard | `responsive.jsx` → `MobileDashboard` (the drawer is the desktop sidebar) |
| Holdings | `pages-holdings.jsx` → `MobileHoldings` |
| Transactions | `responsive.jsx` → `MobileTransactions` |
| Dividends | `responsive-dividends.jsx` → `MobileDividends` |
| Dividend Calendar | `responsive-dividends.jsx` → `MobileDividendCalendar` |
| Self-Funding | `pages-selffunding.jsx` → `MobileSelfFunding` |

Other web pages have no mobile mockup yet, so the drawer leaves them out. Under the repo's
design-first rule, each of those pages needs a mockup before it is built.

## First build

The project has never been compiled. It was written on a machine without the Android SDK,
so expect a few compile errors on the first build.

1. Install **Android Studio**. It includes the JDK, the Android SDK and an emulator.
2. In Android Studio, choose **File → Open** and select `portfolio-mobile-android/`.
   Android Studio syncs the project using the Gradle version in
   `gradle/wrapper/gradle-wrapper.properties` (8.11.1).
3. The repo has no `gradlew` script or wrapper jar. To create them, open the Gradle tool
   window and run **Tasks → build setup → wrapper**. After that, these commands work:
   ```bash
   ./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
   ./gradlew test                 # JVM tests of the money/dividend/self-funding maths
   ```
4. If Android Studio offers to upgrade AGP, Kotlin or the Compose BOM, accept. The pinned
   versions (AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01) are simply the last set known
   to work together.

## Reaching the backend

The repo does not set up a route from the phone to the backend. Release builds accept
HTTPS only.

**From anywhere: Tailscale on the PC.**
1. Install Tailscale on the PC and on the phone, and sign both in to the same tailnet.
2. In the admin console's **DNS** page, turn on **MagicDNS** and **HTTPS certificates**.
3. On the PC, run `tailscale serve --bg http://localhost:8080`. This serves the backend at
   `https://<pc-name>.<tailnet>.ts.net` with a real certificate.
4. In the app's **Server** field, enter `<pc-name>.<tailnet>.ts.net`. Without a scheme,
   `https://` is assumed.

Note that this puts the whole PC on the tailnet. Flask (port 5000) and MongoDB (port
27017) listen on every network interface, so your other tailnet devices can reach them
too. Don't turn on Funnel: it would publish the backend to the internet.

**Debug builds only: no Tailscale needed.** These two options use plain HTTP. They are
allowed only by `src/debug/res/xml/network_security_config.xml`, which release builds
don't include.
- **Emulator:** enter `http://10.0.2.2:8080` as the Server. That address is the emulator's
  name for the PC.
- **Phone connected through adb** (USB or Wi-Fi debugging): run
  `adb reverse tcp:8080 tcp:8080`, then enter `http://localhost:8080`. Run it again after
  each reconnect.

Either way, the backend needs no changes. CORS only applies to browsers; OkHttp sends no
`Origin` header.

## Security model

- **Signing in.** Signing in uses the same Spring form login as the web app (`POST /login`).
  The session cookie stays in memory only (`SessionCookieJar`) and never outlives the app
  process.
- **"Unlock with fingerprint next time".** With this on, the password is encrypted with an
  AES-GCM key in the Android Keystore (`CredentialStore`):
  - the key requires a strong biometric for **every** use;
  - enrolling a new fingerprint destroys the key, so the saved sign-in is lost and you sign
    in with your password once.
- **Each launch.** Each launch starts at the lock screen. The fingerprint unlocks the
  password, and the password signs in. Nothing that grants access is stored outside the
  keystore.
- **Expired sessions.** When Spring's 30-minute idle session expires, the app signs in again
  in the background with the password it holds in memory. If that fails, the app returns to
  the lock screen.
- **Log out.** Logging out removes the saved sign-in, the session, the offline cache and
  every screen's in-memory data. Android backups are disabled.

## Offline

Every successful `GET` is saved as raw JSON in app-private storage (`ResponseCache`). When
the server is out of reach (Tailscale off, PC asleep, or a 5xx from the proxy), each screen
shows the last saved copy under a "showing data from 14:32" banner. Pull down to retry. If a
fingerprint unlock happens while offline, the app opens with saved data and signs in once
the server answers again.

## Layout

```
app/src/main/java/com/dev/alex/portfolio/
├── AppViewModel.kt, AppRoot.kt   lock/sign-in state, back stack, theme
├── data/
│   ├── api/                      OkHttp client, DTOs, session, cookie jar, errors
│   ├── auth/                     keystore credential store, BiometricPrompt gate
│   ├── cache/                    raw-JSON offline cache
│   └── PortfolioRepository.kt    network first, cache second
├── domain/                       pure Kotlin ports of the web client's maths
└── ui/                           Compose: theme (tokens.jsx), icons (icons.jsx),
                                  components, shell (top bar + drawer), one package per screen
```

The `domain/` package ports the web client's logic, so the two must stay in step:

| Web file | Android file |
|---|---|
| `lib/currency.ts` | `Money.kt` |
| `lib/holdingCurrency.ts` | `Holdings.kt` |
| `selfFundingMath.ts` | `SelfFundingMath.kt` |
| the Dividends and Dividend Calendar pages | `DividendMath.kt`, `CalendarMath.kt` |
| the dashboard's `rangeStartMonth` | `PortfolioMath.kt` |

Ticker logos come from the web client's `portfolio-app-frontend/public/images/` folder,
which the build packages into the app as-is. A logo added there for the web appears in
the app on its next build. A ticker without a file shows its letter instead.

The API sends every amount in its own currency. The app converts amounts with the rates
from `/fx-rates`, exactly as the web client does.

## Not in this version

- **Writing anything.** The mockups' "+" buttons (new transaction, new portfolio) are left
  out rather than added as buttons that do nothing.
- **The notification bell.** The backend has no alerts.
- **A ticker detail view.** On the web, tapping a holding opens `HoldingDetailDialog`.
- **Release signing.** Release builds are signed with the debug key, which is fine for
  installing the app yourself (sideloading).
