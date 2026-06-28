# Porting Meerkly to Android: Core Developer Guide — GeckoView Local MVP

This document describes the Android port plan for the existing Electron-based Meerkly desktop application.

Current project assumption:

```text
Native Android project already exists
Template: Empty Activity
Language: Kotlin
UI: Jetpack Compose
Minimum SDK: API 24 / Android 7.0
```

This guide proceeds from that state. It does **not** cover project creation, Expo, React Native, backend APIs, WebSockets, payouts, or remote worker networking.

The MVP is strictly local:

```text
User enters URL in local input field
  → app validates URL
  → GeckoView / GeckoSession loads the page locally
  → app records final URL, title, timing, logs, diagnostics
  → optional local HTML extraction is saved/displayed locally
```

Keep this guide as the single source of truth for the Android local MVP.

---

## Goals

- Provide a local Android UI that allows entering a URL and opening it in a dedicated browser runtime.
- Use **GeckoView** instead of Android WebView for the browser engine.
- Preserve important runtime features from the Electron desktop app:
  - stable per-install machine ID
  - isolated persistent browser profile
  - structured JSONL logging with retention
  - navigation with timeout and clear success/failure reporting
  - final URL and page title logged after successful navigation
  - optional local HTML extraction
  - diagnostics export: logs + machine info + latest navigation status
  - crash/session recovery for the browser runtime
- Keep all page loads initiated by the local input field.
- Avoid remote worker/API/networking architecture until the local browser MVP is stable.

---

## Non-goals for this MVP

Do **not** implement these yet:

- backend API
- WebSocket connection
- worker registration / remote worker identity
- remote job assignment
- heartbeats
- artifact upload
- object storage
- customer API
- payouts/earnings
- policy engine for customer jobs
- Android worker marketplace
- desktop/Android shared remote worker protocol
- background paid request processing

The only network activity in this MVP is the browser loading the URL typed by the local user.

---

## Strategic Platform Decision

### Browser engine

Use:

```text
GeckoView
```

Do not use Android WebView as the final browser engine.

Reasoning:

| Engine | Good | Problem |
|---|---|---|
| Android WebView | Easy, built in, controllable | Fingerprints as embedded WebView; `wv` / Client Hints / runtime inconsistencies |
| Chrome Custom Tabs | Real system browser | User-visible and not DOM-controllable enough |
| GeckoView | Real browser engine, controllable, embeddable, no WebView `wv` identity | Gecko/Firefox-like, not Chrome-like |
| Chromium fork | Strong Chrome-like option | Much heavier engineering/maintenance |

Target identity:

```text
Real Android Gecko/Firefox-like browser environment
```

Not:

```text
Fake Chrome Android
```

The goal is not to look like Chrome. The goal is to use a coherent, real mobile browser runtime.

---

## Recommended Tech Stack

Use the native Android stack already created by the Empty Activity project:

- Language: **Kotlin**
- UI: **Jetpack Compose**
- Browser engine: **GeckoView**
- Concurrency: **Kotlin Coroutines**
- Settings storage: **SharedPreferences for MVP**, DataStore later if desired
- Optional local DB: **Room** only if local history becomes non-trivial
- Logging: **Timber + custom JSONL rolling file logger**, or a custom logger only
- ZIP export: Android `java.util.zip`
- Tests: JUnit, Robolectric, Android instrumentation tests

Minimum SDK:

```text
minSdk = 24
Android 7.0
```

API 24 is acceptable for this MVP. Be conservative with APIs that require newer Android versions. Gate any newer platform APIs with `Build.VERSION.SDK_INT` checks.

---

## High-level Architecture Mapping

### Original Electron mapping

- Electron main process -> Android `Application` + controller/repository layer
- Renderer UI -> `MainActivity` + Jetpack Compose screens
- BrowserWindow -> `GeckoView` + `GeckoSession`
- Hidden BrowserWindow / background render worker -> optional hidden local `GeckoSession` later
- IPC -> Kotlin flows, repositories, ViewModels
- App user data -> Android app-specific storage:
  - `filesDir`
  - `cacheDir`
  - `getExternalFilesDir()` for diagnostics export
  - `SharedPreferences` / DataStore for config
- Logs -> JSONL rolling log files under `filesDir/logs`

### Local MVP architecture

```text
Meerkly Android App
  ├─ MainActivity
  │   └─ Jetpack Compose UI
  │       ├─ URL input
  │       ├─ Open button
  │       ├─ Browser status
  │       ├─ Log panel
  │       ├─ Machine info
  │       └─ Diagnostics export
  │
  ├─ Browser Layer
  │   ├─ GeckoRuntime
  │   ├─ visible GeckoView
  │   └─ primary GeckoSession
  │
  ├─ Optional Local Extraction Layer
  │   ├─ Gecko WebExtension / native messaging bridge
  │   └─ HTML/title/final URL extraction
  │
  └─ App Infrastructure
      ├─ MachineIdManager
      ├─ AppLogger
      ├─ DiagnosticsExporter
      ├─ UrlValidator
      └─ BrowserStateRepository
```

---

## Browser Identity and Fingerprint Strategy

Do not over-spoof.

The local Android browser should be a coherent real Android Gecko browser:

```text
Android device
+ Gecko engine
+ SpiderMonkey JS runtime
+ Firefox/Gecko-like browser behavior
+ stable app profile
+ real touch/mobile viewport
+ real device GPU/canvas/audio characteristics
+ persistent cookies/storage/cache in the app profile
```

Avoid:

```text
Chrome UA
+ Gecko engine
+ missing Chrome Client Hints
+ random viewport
+ random timezone
+ randomized GPU/canvas every load
```

That creates cross-layer inconsistencies.

For the local MVP, do not try to spoof Chrome. Use GeckoView normally, then later decide whether to tune UA/profile behavior.

---

## Profiles and Isolation

Use a dedicated Meerkly browser profile separate from installed Chrome/Firefox and separate from any other app.

For MVP:

```text
Meerkly app profile:
  - cookies
  - cache
  - localStorage
  - indexedDB
  - history-like local records, if implemented
```

This profile is app-scoped. It does not access the user's personal Chrome or Firefox browser data.

Later, if paid worker mode is added, split into:

```text
User browsing profile
Worker profile
```

For this local MVP, one dedicated Meerkly Gecko profile is enough.

---

## App UX

Main UI should replicate the desktop control panel:

- URL input
- `Open` button
- `Stop` button, optional
- `Reload` button, optional
- `Export Diagnostics` button
- Machine info display:
  - machine ID
  - app version
  - device model
  - Android SDK
  - GeckoView version, if available
  - profile path/status
- Navigation status:
  - idle/loading/success/error
  - requested URL
  - final URL
  - title
  - load duration
  - error message, if any
- Log panel with recent entries

Do not add these screens yet:

- Earn Mode
- Wallet
- payouts
- remote job history
- worker status dashboard
- data/battery earning limits

Those belong to a later remote-worker phase.

---

## URL Validation

Port `src/shared/urlValidator.ts` logic to Kotlin.

Rules:

- Trim input and reject empty strings.
- Reject forbidden protocols:
  - `file:`
  - `chrome:`
  - `about:`
  - `content:`
  - `javascript:`
  - `data:`
- Add `https://` if no protocol is present.
- Allow only `http:` and `https:`.

Example:

```kotlin
fun validateAndNormalizeUrl(input: String): Result<String> {
  val trimmed = input.trim()
  if (trimmed.isEmpty()) {
    return Result.failure(IllegalArgumentException("Empty URL"))
  }

  var candidate = trimmed
  if (!candidate.contains("://")) {
    candidate = "https://$candidate"
  }

  val uri = try {
    android.net.Uri.parse(candidate)
  } catch (e: Exception) {
    null
  }

  if (uri == null || uri.scheme == null) {
    return Result.failure(IllegalArgumentException("Invalid URL"))
  }

  val scheme = uri.scheme!!.lowercase()
  if (scheme != "http" && scheme != "https") {
    return Result.failure(IllegalArgumentException("Unsupported protocol: $scheme"))
  }

  return Result.success(uri.toString())
}
```

---

## GeckoView Integration

### 1. Add Mozilla Maven repository

In `settings.gradle.kts`, add Mozilla Maven to `dependencyResolutionManagement`:

```kotlin
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://maven.mozilla.org/maven2/") }
  }
}
```

### 2. Java/Kotlin compatibility

In `app/build.gradle.kts`:

```kotlin
android {
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }
}
```

### 3. Add GeckoView dependency

In `app/build.gradle.kts`:

```kotlin
dependencies {
  implementation("org.mozilla.geckoview:geckoview:<PINNED_VERSION>")
}
```

Pin the version explicitly. Avoid floating/nightly versions for production. Upgrade intentionally and test navigation, storage, extraction, and crash recovery after each upgrade.

---

## Recommended Package Structure

Use this structure under your existing package, for example `com.meerkly.android`:

```text
app/src/main/java/com/meerkly/android/
  MainActivity.kt

  ui/
    MainScreen.kt
    BrowserScreen.kt
    DiagnosticsScreen.kt
    SettingsScreen.kt

  browser/
    GeckoBrowserManager.kt
    GeckoSessionController.kt
    GeckoViewHost.kt
    HtmlExtractor.kt

  data/
    SettingsRepository.kt
    MachineIdManager.kt
    RecentNavigationRepository.kt

  logging/
    AppLogger.kt
    JsonlFileLogger.kt
    LogRetention.kt

  model/
    NavigationResult.kt
    BrowserStatus.kt
    ExtractedPage.kt

  diagnostics/
    DiagnosticsExporter.kt

  util/
    UrlValidator.kt
    ZipUtils.kt
```

---

## Minimal Gecko Browser Manager

```kotlin
class GeckoBrowserManager(
  private val context: Context,
  private val logger: AppLogger
) {
  private var runtime: GeckoRuntime? = null
  private var session: GeckoSession? = null

  fun start() {
    runtime = GeckoRuntime.create(context)

    val settings = GeckoSessionSettings.Builder()
      .usePrivateMode(false)
      .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
      .build()

    session = GeckoSession(settings).also { geckoSession ->
      geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
          logger.info("browser.page_start", mapOf("url" to url))
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
          logger.info("browser.page_stop", mapOf("success" to success))
        }
      }

      geckoSession.open(runtime!!)
    }
  }

  fun getSession(): GeckoSession? = session

  fun load(url: String) {
    logger.info("browser.load", mapOf("url" to url))
    session?.loadUri(url)
  }

  fun stop() {
    session?.close()
    session = null
    runtime = null
  }
}
```

---

## Visible GeckoView Browser Screen

The MVP should use a visible `GeckoView` because it is easier to debug and directly matches the local input-field workflow.

Conceptual Compose host:

```kotlin
@Composable
fun GeckoViewHost(
  manager: GeckoBrowserManager,
  modifier: Modifier = Modifier
) {
  AndroidView(
    modifier = modifier,
    factory = { context ->
      GeckoView(context).apply {
        manager.getSession()?.let { setSession(it) }
      }
    },
    update = { geckoView ->
      manager.getSession()?.let { session ->
        if (geckoView.session != session) {
          geckoView.setSession(session)
        }
      }
    }
  )
}
```

Implementation note: check the actual GeckoView API for the version you pin. Method/property names can vary slightly across versions.

---

## Navigation Result Model

```kotlin
data class NavigationResult(
  val success: Boolean,
  val requestedUrl: String,
  val finalUrl: String?,
  val title: String?,
  val error: String?,
  val startedAt: Instant,
  val finishedAt: Instant,
  val loadedMs: Long?,
  val htmlSizeBytes: Long?
)
```

No `jobId` is needed for the local MVP.

---

## Navigation with Timeout

Use coroutines around Gecko navigation callbacks.

Pseudo-implementation:

```kotlin
suspend fun navigateWithTimeout(
  manager: GeckoBrowserManager,
  url: String,
  timeoutMs: Long = 30_000L
): NavigationResult {
  val started = Instant.now()

  return withTimeoutOrNull(timeoutMs) {
    manager.navigate(url)
  } ?: NavigationResult(
    success = false,
    requestedUrl = url,
    finalUrl = null,
    title = null,
    error = "Navigation timeout after $timeoutMs ms",
    startedAt = started,
    finishedAt = Instant.now(),
    loadedMs = null,
    htmlSizeBytes = null
  )
}
```

Actual implementation should expose a suspend function from `GeckoBrowserManager` or `GeckoSessionController` that completes on:

- page stop with success
- explicit navigation error
- crash/session failure
- timeout

---

## HTML Extraction

Preferred approach:

```text
Built-in Gecko WebExtension
  → content script reads document.documentElement.outerHTML
  → native messaging sends result to Kotlin
  → Kotlin stores/displays/logs result locally
```

Do not rely on Android WebView-style `evaluateJavascript`. GeckoView supports WebExtensions and native messaging, which is the cleaner bridge for page content extraction.

Result shape:

```kotlin
data class ExtractedPage(
  val finalUrl: String,
  val title: String?,
  val html: String?,
  val htmlSizeBytes: Long,
  val timing: PageTiming
)
```

Content script target behavior:

```javascript
(() => {
  return {
    url: location.href,
    title: document.title,
    html: document.documentElement.outerHTML
  }
})()
```

For early MVP, it is acceptable to defer full HTML extraction and only log:

- requested URL
- final URL
- title
- duration
- success/failure

Then add the WebExtension bridge after basic navigation is stable.

If extracted HTML is stored locally, use:

```text
context.cacheDir/pages
```

Do not include full page HTML in diagnostics by default unless the user explicitly chooses it. HTML can contain private content.

---

## Machine ID

Generate a stable UUID and persist it with `SharedPreferences` for the MVP.

```kotlin
object MachineIdManager {
  private const val PREFS = "meerkly_prefs"
  private const val KEY_MACHINE_ID = "machine_id"

  fun getMachineId(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var id = prefs.getString(KEY_MACHINE_ID, null)
    if (id.isNullOrBlank()) {
      id = UUID.randomUUID().toString()
      prefs.edit().putString(KEY_MACHINE_ID, id).apply()
    }
    return id
  }
}
```

Do not use hardware identifiers. Use an app-scoped generated ID.

---

## Logging & Rotation

Use JSONL logs.

Each log entry:

```json
{
  "ts": "2026-06-28T14:22:00.000Z",
  "level": "info",
  "event": "browser.navigation_completed",
  "machine_id": "machine_abc",
  "data": {
    "requested_url": "https://example.com",
    "final_url": "https://example.com",
    "title": "Example Domain",
    "html_size_bytes": 384211,
    "duration_ms": 3510
  }
}
```

Recommended locations:

```text
context.filesDir/logs
context.cacheDir/diagnostics
context.getExternalFilesDir("diagnostics")
```

Retention:

- keep current log + last N days
- compress older logs
- cap total log directory size
- expose last N log entries in UI

---

## Diagnostics Export

Export ZIP should include:

```text
diagnostics.json
machine.json
app.json
browser_status.json
logs/*.jsonl
recent_navigations.json
```

`diagnostics.json` should include:

- machine ID
- app version
- Android SDK
- device model
- locale/timezone
- GeckoView version, if available
- profile path/status
- most recent navigation result
- log retention metadata

Use scoped storage:

- write to `cacheDir` or `getExternalFilesDir("diagnostics")`
- share via `Intent.ACTION_SEND`
- avoid broad storage permissions

---

## Crash and Session Recovery

### Gecko session failure

On navigation/session crash/failure:

1. log crash/error details
2. mark current navigation failed
3. close current `GeckoSession`
4. optionally recreate `GeckoRuntime` if needed
5. restore browser manager to idle state
6. let the user retry from the input field

### App restart

On app restart:

1. reload machine ID/settings
2. initialize logging
3. recreate Gecko runtime/session
4. do not assume unfinished navigation succeeded
5. show last known navigation status in diagnostics/logs if available

---

## Permissions

Required:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Potentially useful:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Avoid broad storage permissions.

Do not add foreground-service permissions for the local MVP unless you actually add a service later.

---

## Security and Trust Requirements

Even in local-only mode:

- validate and normalize URLs before loading
- reject `file:`, `content:`, `javascript:`, `data:`, `about:`, and `chrome:` URLs
- do not expose local files to web content
- keep app browser data app-scoped
- do not read or use installed Chrome/Firefox user data
- allow diagnostics export for debugging
- log errors clearly without leaking sensitive page content by default
- do not include full HTML in logs by default

---

## Developer Workflow

Use:

```text
Android Studio
Kotlin
Jetpack Compose
Gradle
ADB
Real Android phone over USB
```

Commands:

```bash
./gradlew assembleDebug
./gradlew installDebug
adb logcat | grep Meerkly
```

USB testing:

1. enable Developer Options
2. enable USB debugging
3. connect phone
4. accept RSA prompt
5. verify:

```bash
adb devices
```

---

## Testing Plan

### Unit tests

- URL validator
- MachineIdManager
- navigation result mapping
- log rotation
- diagnostics export metadata
- HTML size calculation

### Integration tests

- visible Gecko browser opens URL
- navigation timeout works
- final URL/title captured
- HTML extraction returns expected fields, once implemented
- logging writes JSONL
- diagnostics ZIP exports

### Device tests

- app opens on a real Android 7+ phone
- URL input loads `https://example.com`
- page start/page stop logs appear
- title/final URL appear in UI
- rotation/background/foreground does not corrupt state
- session can be recreated after failure
- diagnostics ZIP shares successfully

---

## Migration Checklist

### Phase 1 — Android shell already exists

Starting point is already done:

```text
Empty Activity
Kotlin
Jetpack Compose
minSdk API 24
```

Next tasks:

1. Add package structure.
2. Add machine ID.
3. Add JSONL logging.
4. Add diagnostics export.
5. Add URL validator.
6. Create basic UI: URL input, Open button, status area, logs.

### Phase 2 — GeckoView visible browser

7. Add Mozilla Maven repository.
8. Add GeckoView dependency.
9. Create `GeckoBrowserManager`.
10. Create visible `GeckoViewHost` composable.
11. Load `https://example.com` from the input field.
12. Log page start/page stop.
13. Capture final URL/title.
14. Add navigation timeout.

### Phase 3 — Local extraction

15. Add simple extraction proof.
16. Add Gecko WebExtension/native messaging bridge.
17. Extract `document.documentElement.outerHTML`.
18. Capture title/final URL/timing/html size.
19. Store or display result locally.

### Phase 4 — Hardening

20. Add crash/session recovery.
21. Add profile persistence checks.
22. Add diagnostics completeness.
23. Add tests around navigation and URL validation.
24. Polish UI parity with desktop app.

### Future phase — Remote worker mode, not now

Only after the local MVP is stable, revisit:

- foreground worker service
- WebSocket control protocol
- remote job assignment
- artifact upload
- policy engine
- payouts/earnings
- Android + desktop shared worker protocol

---

## Final Notes

- Proceed from the existing Empty Activity Android project.
- Use GeckoView as the Android browser engine.
- Do not use WebView as the final production browser engine.
- Keep the MVP local: input field → load URL → log/result/diagnostics.
- Do not build API/networking/worker marketplace pieces yet.
- Prioritize feature parity and browser reliability over exact UI fidelity.
- Add remote worker mode only after visible local Gecko navigation and extraction are solid.

---

> **Implementation note (added during the Android port):** The live build pins
> `org.mozilla.geckoview:geckoview:152.0.20260621191700` (Lite build, no Glean telemetry), which
> **requires Java 17** source/target compatibility and **`minSdk 26` (Android 8.0)** — its AAR
> declares `minSdkVersion 26`, so the `minSdk 24` floor above is not achievable with this engine
> version and the project was bumped to 26. The 4-arg
> `NavigationDelegate.onLocationChange(session, url, perms, hasUserGesture)` is the current
> signature, page loads use `session.load(GeckoSession.Loader().uri(url))`, and GeckoView keeps a
> single app-scoped default profile per `GeckoRuntime` (no profile-dir API). See `CLAUDE.md` for
> the condensed, current build constraints.
