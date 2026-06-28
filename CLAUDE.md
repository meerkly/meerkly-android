# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Source of truth:** the full porting guide lives at [docs/android-port-guide.md](docs/android-port-guide.md).
> This file is the condensed, current-state summary; the guide has the complete rationale, package
> layout, and phase-by-phase checklist.

## What this is

Android port of the existing Electron-based **Meerkly** desktop app. The goal of the current
phase is a **strictly local MVP**: a user types a URL, the app validates it, loads it in a real
browser engine (**GeckoView**, not Android `WebView`), and records the result (final URL, title,
timing, logs, diagnostics). No backend, no networking beyond the page load itself.

> **Current repo state:** this is still the default Android Studio "Empty Activity" scaffold.
> `MainActivity.kt` only shows a `Greeting` composable; there is no Meerkly code, no GeckoView,
> no `com.meerkly.android.{browser,data,logging,...}` packages yet. Treat the "Target architecture"
> and "Migration phases" sections below as the build plan, not a description of existing code.

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
  `targetSdk = 36`, **`minSdk = 26`** (Android 8.0). The guide originally targeted API 24, but
  GeckoView 152's AAR declares `minSdkVersion 26`, so 26 is the hard floor — manifest-merger fails
  at 24/25.
- App compiles against **Java 11** (`compileOptions` source/target = `VERSION_11`). Note: the
  porting guide text suggests Java 17 — the actual project is on 11. Pick a level deliberately and
  keep `compileOptions`, any `kotlin { compilerOptions { jvmTarget } }`, and the GeckoView
  requirement consistent if you change it. On AGP 9 / Kotlin 2.2 use the `compilerOptions {}` DSL,
  not the deprecated `kotlinOptions {}` shown in the guide.

**Pin GeckoView explicitly** (currently `152.0.20260621191700`, Lite build) and re-test
navigation/storage/extraction/crash-recovery on every bump — a bump can also raise the required
minSdk. Gate any newer platform APIs behind `Build.VERSION.SDK_INT` checks.

## Adding GeckoView (not yet present)

Two setup steps the scaffold is missing:

1. Add Mozilla Maven to `dependencyResolutionManagement.repositories` in `settings.gradle.kts`:
   `maven { url = uri("https://maven.mozilla.org/maven2/") }` (note: `repositoriesMode` is
   `FAIL_ON_PROJECT_REPOS`, so it must go here, not in a module build file).
2. Add `org.mozilla.geckoview:geckoview:<PINNED_VERSION>` via the version catalog.

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

## Hard constraints (non-negotiable for this MVP)

- **Local only.** Do not build: backend API, WebSocket protocol, worker registration/identity,
  remote job assignment, heartbeats, artifact upload, object storage, payouts/earnings, policy
  engine, or any "Earn Mode" / wallet / worker-dashboard UI. The only network activity is the
  browser loading the user-entered URL. These belong to a future remote-worker phase.
- **URL validation** (port of `src/shared/urlValidator.ts`): trim and reject empty; if no scheme,
  prepend `https://`; allow only `http`/`https`; **reject `file:`, `chrome:`, `about:`, `content:`,
  `javascript:`, `data:`**. Validate/normalize before every load.
- **Storage stays app-scoped.** Never read or use the user's installed Chrome/Firefox data. Don't
  expose local files to web content. Don't request broad storage permissions.
- **Logging/diagnostics:** JSONL entries `{ ts, level, event, machine_id, data }`. Do **not** put
  full page HTML in logs or in diagnostics by default (it can contain private content) — only
  requested URL, final URL, title, duration, success/failure unless the user opts in.
- **Permissions:** add `INTERNET` (and optionally `ACCESS_NETWORK_STATE`) to the manifest — it
  currently has none. No foreground-service permission unless/until a service actually exists.

## Conventions

- Kotlin official code style (`kotlin.code.style=official`), Jetpack Compose for all UI,
  Kotlin Coroutines for async/navigation timeouts.
- `namespace` / `applicationId` = `com.meerkly.android`.
- Settings: `SharedPreferences` for the MVP (DataStore later if desired); `Room` only if local
  history becomes non-trivial.
