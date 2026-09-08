# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Android exit node for **Meerkly**: once the user signs in, this device joins the Meerkly network as
an exit node — proxying traffic for the signed-in account's earnings — via the **Meerkly proxy SDK**
(`com.meerkly:sdk`, a Rust core behind uniffi-generated Kotlin bindings dispatched through JNA). The
app's own job is small: sign-in, keep the SDK connected while backgrounded, and show the account's
earnings and this device's status. It runs no browser engine and does not fetch, render, or extract
any page content itself.

> **Current repo state:** the full `com.meerkly.android.*` implementation exists (auth, data,
> logging, diagnostics, proxy, ui, worker, util) and is live, not a plan. 2.0.0 replaced the earlier
> GeckoView-based crawler-and-gateway-worker entirely — there is no browser engine, no gateway
> WebSocket client, and no per-device pairing/registration step left in this app.

## Build, test, run

This is a single-module Gradle project (`:app`). Use the wrapper; do not assume a system Gradle.

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build + install on connected device/emulator
./gradlew test                          # JVM unit tests (app/src/test)
./gradlew testDebugUnitTest             # unit tests, debug variant only
./gradlew connectedAndroidTest          # instrumented tests on device (app/src/androidTest)
./gradlew lint                          # Android lint
./gradlew assemble check                # full build + all checks

# Run a single unit test class / method:
./gradlew test --tests "com.meerkly.android.ExampleUnitTest"
./gradlew test --tests "com.meerkly.android.ExampleUnitTest.addition_isCorrect"

adb devices                             # confirm a device is attached before connected* tasks
adb logcat | grep -i meerkly            # follow app logs on device
```

Gradle **configuration cache is enabled** (`org.gradle.configuration-cache=true` in
`gradle.properties`). Plugins/build logic must be configuration-cache compatible. The Gradle
daemon runs on a **JDK 21 toolchain** (pinned in `gradle/gradle-daemon-jvm.properties`).

## Toolchain & versions

All dependency and plugin versions are centralized in the **version catalog**
`gradle/libs.versions.toml` and referenced as `libs.*` — add/bump dependencies there, not inline
in `app/build.gradle.kts`.

- AGP `9.2.1`, Kotlin `2.2.10`, Gradle `9.4.1`, Compose BOM `2026.02.01`
- `compileSdk = 36` (declared via the new AGP 9 `compileSdk { version = release(36) { ... } }` DSL),
  `targetSdk = 36`, **`minSdk = 26`** (Android 8.0). The SDK's own floor is 24; 26 is kept because
  it's what this app has shipped and run on, not because anything requires it — dropping to 24
  would mean qualifying the foreground service, notification and Keystore paths on two API levels
  the app has never run on. It is a choice now, not a floor a dependency imposes.
- App compiles against **Java 17** (`compileOptions` source/target = `VERSION_17`).
- `com.meerkly:sdk:0.6.0` (the `meerklySdk` version catalog entry, `libs.meerkly.sdk`) is the proxy
  engine — a uniffi-generated Kotlin binding over a Rust core, dispatched through JNA reflection.
  `MainViewModel.machineInfo.sdkVersion` reads this same catalog entry via a `BuildConfig` field
  (`app/build.gradle.kts`), so the Settings/diagnostics display can't drift from it. R8 stays off in
  release builds — see the comment beside `optimization { enable = false }` in `app/build.gradle.kts`.

## Hard constraints (non-negotiable)

- **Scope is exit-node proxying + account/earnings display.** Do **not** build ahead of a new plan:
  durable storage / DB, job queues, artifact upload, object storage, geo-targeted selection,
  cash-out/payouts, a policy engine, or any UI beyond the earnings/device display.
- **Storage stays app-scoped.** Never read or use the user's installed browser's data. Don't expose
  local files to other apps beyond the FileProvider-mediated diagnostics share. Don't request broad
  storage permissions.
- **Logging/diagnostics:** JSONL entries `{ ts, level, event, machine_id, data }`. Do **not** put
  private account or traffic content in logs or diagnostics by default — only what's needed to
  debug connection/earnings state, unless the user opts in.
- **Permissions:** the closed set is `INTERNET`, `ACCESS_NETWORK_STATE`, plus the always-on
  worker's block: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` (WorkerService),
  `RECEIVE_BOOT_COMPLETED` (BootReceiver), `POST_NOTIFICATIONS` (ongoing notification, 33+),
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Doze exemption nudge), `WAKE_LOCK` (currently unused by
  this app's own Kotlin — see the comment in `AndroidManifest.xml` — kept because removing it is a
  separate, deliberate decision). Do not add more without a plan.

## Always-on worker service (`worker/`)

`worker/WorkerService.kt` is a **thin `specialUse` foreground service**: `AppGraph` stays the owner
of the `ProxyController`; the service owns only the startForeground posture and the ongoing
notification (text tracks `ProxyController.state`; single action = Stop). Type is `specialUse` on
purpose — `dataSync` is capped at 6h/day on Android 15+ and can't launch from boot — and needs a
Play Console declaration at release. Start paths all funnel through
`WorkerServiceLauncher.startIfEligible` (eligible = `worker_enabled` pref && a known publisher id):
`MainActivity.onStart`, sign-in completing (`AccountCoordinator.onWorkerEligible`), `BootReceiver`
on BOOT_COMPLETED (fires after first unlock — the persisted auth session needs the Keystore), and
START_STICKY restart. **Invariant: an explicit user Stop is sticky** — the notification action and
the dashboard control persist `worker_enabled=false` (`worker/WorkerPrefs.kt`, `meerkly_prefs`) and
every auto-start path checks it; nothing may restart the worker until the user presses Start.
Sign-out is *not* orthogonal here the way 1.x was on desktop: `AccountCoordinator.signOut` stops the
proxy, because in 2.0 the publisher id is both the credential's payload and the earning identity —
continuing to earn for an account the user just signed out of would be wrong. A user force-stop from
system settings blocks restart until the next app open — accepted OS behavior.

### Play release: the foreground-service declaration catch-22

**A build that adds a new foreground service is blocked on upload by default**, and the declaration
form only appears *after* the block. The required order is: upload → let it get flagged → complete
the declaration on the App content page → resubmit. This is the normal path, not a mistake. Just Eat
Takeaway [documented the same
surprise](https://medium.com/justeattakeaway-tech/live-updates-and-progress-notifications-for-android-16-at-jet-b0c87eab17b4)
("results in a bit of a catch-22… this initially disrupted the release cadence").

Two declarations are needed:
- **`specialUse` FGS** — use-case description plus a demo video.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — the riskier of the two. Apps with *bounded*,
  user-initiated work (a food order finishing in under an hour) don't need it; Meerkly's worker runs
  indefinitely, so the justification has to actually carry the argument that Doze suspending the
  socket breaks the app's core function.

### Live Updates (Android 16) — considered and rejected

Don't re-litigate: a Live Update (`ProgressStyle` + `POST_PROMOTED_NOTIFICATIONS` +
`setRequestPromotedOngoing`, the food-delivery-style status-bar chip) is a **notification
presentation** API and grants no background execution whatsoever — apps that use it still run a
foreground service underneath, and Just Eat's is `specialUse`, exactly like ours. It also fails
Google's own [usage criteria](https://developer.android.com/develop/ui/views/notifications/live-update)
for this app: a Live Update must be time-sensitive, "require the user's attention throughout", and
have "a distinct start and end". Meerkly's worker runs indefinitely and is explicitly designed to
need no attention.

## Account sign-in (live)

Mirrors the desktop's OAuth flow; device registration is gone, replaced by a publisher-id fetch:
- **`auth/AuthManager.kt`** — OAuth2 Auth Code + PKCE via **AppAuth** against
  `BuildConfig.ACCOUNT_BASE_URL` (Doorkeeper; static endpoints, no discovery). Client id
  `meerkly-android`, redirect `https://dashboard.meerkly.com/oauth2redirect` — a verified **App
  Link** (`android:autoVerify="true"` in `AndroidManifest.xml`), not a private-use scheme: any app
  can claim a scheme, and this client skips the consent screen (RFC 8252 §8.1). **Debug builds
  inject a cleartext-permitting `ConnectionBuilder`** (AppAuth's default refuses the `http://` dev
  token endpoint) and redirect to a LAN IP instead (`app/src/debug/AndroidManifest.xml`).
- Sign-in completes with a `GET /api/v1/me` call that returns the account's email and **publisher
  id** — the SDK's required config, and the only thing standing between a signed-in install and
  earning. `AuthManager.completeSignIn` persists both; `AccountCoordinator` starts the proxy once
  the id is known and heals an install that signed in but never received one (`healPublisherId`).
- **`data/SecureStore.kt`** — AndroidKeyStore AES/GCM encryption over `meerkly_prefs` values
  (session-memory fallback if the Keystore is broken; never plaintext on disk). Holds the OAuth
  session and the publisher id.
- **`auth/AccountCoordinator.kt`** — the wiring: the proxy starts only once a publisher id is known;
  sign-in fetches that id, then starts the worker; sign-out stops the proxy (see "Always-on worker
  service" above). Exposes the merged `StateFlow<AuthStatus>` the UI renders.

## UI (cream & ink)

`ui/RootScreen.kt` switches Loading → `AuthGateScreen` → `MainScaffold` on `AuthStatus`. There is no
persistent browser host to keep composed — the proxy SDK has no surface that needs to stay alive in
the UI tree, so `RootScreen` is a plain state switch. Brand art (mascot/coin) is Compose Canvas in
`ui/BrandArt.kt`, stroke icons in `ui/components/StrokeIcons.kt`; the palette lives in `ui/theme/`
(fixed light scheme, no dynamic color). Display type is **Fraunces** (`ui/theme/Type.kt`, bundled
variable font, `opsz` pinned to 72) — the same face account.meerkly.com uses; never reach for
`FontFamily.Serif`, which is Noto Serif.

**Three tabs** (`MainScaffold` + `ui/nav/`): Home, Devices, Settings. Activity was a fourth until
2.0.0 — it listed pages this device had fetched, and the proxy SDK has no notion of a job to list
(see the KDoc on `ui/nav/Destination.kt`). Navigation is a hand-rolled `Destination` enum + `NavState`
(pure data, `rememberSaveable`), **not** navigation-compose — a NavHost would add a competing
back-stack dispatcher this app doesn't need. Back order: compact detail → non-Home tab → system.
Adaptive width comes from `BoxWithConstraints` + `WindowWidth` (pure, unit-tested; Material's
600/840dp breakpoints) rather than `material3-window-size-class`, whose `calculateWindowSizeClass()`
needs an Activity and can't be tested on the JVM. Compact = bottom bar; ≥600dp = rail; ≥840dp =
two-pane for Devices (`ui/components/TwoPane.kt`) — list beside detail, hand-rolled rather than
`ListDetailPaneScaffold` to avoid a second BOM and a second back-navigation model. Devices is real:
it reads `MainViewModel.earnings` and renders this account's actual devices, not a placeholder.
Content goes through `components/ContentColumn` so nothing stretches on a tablet, and the top bar
shares its 20dp gutter so the wordmark lines up with the cards.

**The earnings poll and the ON_RESUME refresh live in `MainScaffold`, not a screen** — on a screen
they'd stop the moment the user changed tab. `ui/DesignRenderTest.kt` renders screens to PNG on the
JVM (Robolectric, native graphics) for design review without a device; it asserts nothing. Screens
that need to be renderable take **plain state + lambdas**, not `MainViewModel` (which drags in
`AppGraph` and a live `ProxyController` needing a native library).

## Conventions

- Kotlin official code style (`kotlin.code.style=official`), Jetpack Compose for all UI,
  Kotlin Coroutines for async/navigation timeouts.
- `namespace` / `applicationId` = `com.meerkly.android`.
- Settings: `SharedPreferences` for the MVP (DataStore later if desired); `Room` only if local
  history becomes non-trivial.
