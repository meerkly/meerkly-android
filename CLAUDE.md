# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Android port of the existing Electron-based **Meerkly** desktop app: a user types a URL, the app
validates it, loads it in a real browser engine (**GeckoView**, not Android `WebView`), and records
the result (final URL, title, timing, logs, diagnostics). It **also runs as a gateway worker** —
holding a WebSocket to `api-gateway` and serving `fetch` jobs by navigating + extracting page HTML.

> **Current repo state:** the full `com.meerkly.android.*` implementation exists (browser, data,
> logging, diagnostics, gateway, ui, util) — the worker phase is live, not a plan. Treat the "Target
> architecture (build plan)" section below as historical context; trust the code and the "Gateway
> worker" section over it where they conflict.

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
  `targetSdk = 36`, **`minSdk = 26`** (Android 8.0). GeckoView 152's AAR declares `minSdkVersion 26`,
  so 26 is the hard floor — manifest-merger fails at 24/25.
- App compiles against **Java 17** (`compileOptions` source/target = `VERSION_17`) — GeckoView 152
  requires it. Keep `compileOptions`, any `kotlin { compilerOptions { jvmTarget } }`, and the GeckoView
  requirement consistent if you change it. On AGP 9 / Kotlin 2.2 use the `compilerOptions {}` DSL, not
  the deprecated `kotlinOptions {}` DSL.

**Pin GeckoView explicitly** (currently `152.0.20260621191700`, Lite build) and re-test
navigation/storage/extraction/crash-recovery on every bump — a bump can also raise the required
minSdk. Gate any newer platform APIs behind `Build.VERSION.SDK_INT` checks.

## GeckoView wiring (already in place)

GeckoView is set up — keep it consistent if you touch the build:

1. Mozilla Maven is in `dependencyResolutionManagement.repositories` in `settings.gradle.kts`:
   `maven { url = uri("https://maven.mozilla.org/maven2/") }` (note: `repositoriesMode` is
   `FAIL_ON_PROJECT_REPOS`, so it must go here, not in a module build file).
2. `org.mozilla.geckoview:geckoview:<PINNED_VERSION>` is declared via the version catalog.

GeckoView is the chosen engine on purpose. **Do not use Android `WebView` as the production
engine** (it fingerprints as an embedded `wv` runtime). The target identity is a coherent real
Android Gecko/Firefox-like browser — do **not** spoof Chrome UA / Client Hints.

## Target architecture (build plan)

Electron → Android mapping the port follows:

- Electron main process → `Application` + controller/repository layer
- Renderer UI → `MainActivity` + Jetpack Compose screens
- `BrowserWindow` → `GeckoView` + `GeckoSession` (one `GeckoRuntime`, one visible primary session)
- IPC → Kotlin coroutines, `Flow`s, repositories, ViewModels
- App user data → app-scoped Android storage: `filesDir` (logs), `cacheDir` (extracted pages,
  diagnostics staging), `getExternalFilesDir("diagnostics")` (export), `SharedPreferences` (config)

Planned package layout under `com.meerkly.android`: `ui/` (Compose screens), `browser/`
(`GeckoBrowserManager`, session controller, `GeckoViewHost`, `HtmlExtractor`), `data/`
(`SettingsRepository`, `MachineIdManager`, `RecentNavigationRepository`), `logging/`
(`AppLogger`, `JsonlFileLogger`, `LogRetention`), `model/`, `diagnostics/`, `util/`
(`UrlValidator`, `ZipUtils`).

Key behaviors to preserve from the desktop app: stable per-install machine ID (random UUID in
`SharedPreferences` — **never hardware identifiers**), isolated persistent Gecko profile, JSONL
rolling logs with retention, navigation with timeout + clear success/failure, final URL + title
capture, optional local HTML extraction (via a Gecko **WebExtension + native messaging** bridge,
*not* `evaluateJavascript`), diagnostics ZIP export (logs + machine/app/browser-status JSON,
shared via `ACTION_SEND`), and crash/session recovery (close failed session, optionally recreate
runtime, return to idle, let the user retry).

## Hard constraints (non-negotiable)

- **Scope is local crawl + gateway worker.** The worker phase is live (WebSocket register +
  `fetch`/`result` over `gateway/GatewayClient.kt`). Still **do not build** ahead of a new plan:
  durable storage / DB, job queue, auth, artifact upload, object storage, geo-targeted selection,
  payouts/earnings, policy engine, or any "Earn Mode" / wallet / worker-dashboard UI.
- **URL validation** (port of `src/shared/urlValidator.ts`): trim and reject empty; if no scheme,
  prepend `https://`; allow only `http`/`https`; **reject `file:`, `chrome:`, `about:`, `content:`,
  `javascript:`, `data:`**. Validate/normalize before every load.
- **Storage stays app-scoped.** Never read or use the user's installed Chrome/Firefox data. Don't
  expose local files to web content. Don't request broad storage permissions.
- **Logging/diagnostics:** JSONL entries `{ ts, level, event, machine_id, data }`. Do **not** put
  full page HTML in logs or in diagnostics by default (it can contain private content) — only
  requested URL, final URL, title, duration, success/failure unless the user opts in.
- **Permissions:** `INTERNET` is required (worker WebSocket + page loads). No foreground-service
  permission unless/until a service actually exists.

## Gateway worker (live)

`gateway/GatewayClient.kt` (OkHttp) holds a persistent WebSocket to `api-gateway`, registers with
`capabilities:["fetch"]` (reconnect with backoff), and serves `fetch` jobs by driving
`browser/GeckoBrowserManager.navigateAndExtract(url, waitFor, settleMs, rules, detectMs)`, replying
with a `result` (HTML + `waitTimedOut` + `matchedRule`). The wire schema is owned by
`api-gateway/internal/model/device.go` — change the Go model, this client, and the desktop client
together. Keep wait semantics **identical to the desktop worker** (see the root `CLAUDE.md` invariant).

HTML extraction + the wait happen in a bundled GeckoView **WebExtension** under
`app/src/main/assets/extensions/extractor/` (`content.js` reads the per-job spec via native messaging,
applies `wait_for`/`wait_rules`/`settle_ms`/`detect_ms`, and pushes snapshots). This path has several
**hard-won GeckoView constraints** (string-only native replies, `run_at: document_idle`, an early
fire-and-forget snapshot, `setActive(true)`) — they're documented in detail in
`api-gateway/CLAUDE.md` and commented in `GeckoBrowserManager.kt`. Re-test extraction on every
GeckoView bump.

## Conventions

- Kotlin official code style (`kotlin.code.style=official`), Jetpack Compose for all UI,
  Kotlin Coroutines for async/navigation timeouts.
- `namespace` / `applicationId` = `com.meerkly.android`.
- Settings: `SharedPreferences` for the MVP (DataStore later if desired); `Room` only if local
  history becomes non-trivial.
