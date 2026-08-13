# Background operation becomes opt-in, scraping does not

**Date:** 2026-08-13
**Status:** design approved, pending implementation plan

## Problem

`worker_enabled` is a master switch. Pressing Stop — from the dashboard or the
ongoing notification — calls `gatewayClient.stop()` and tears down the
foreground service, so the app serves no jobs at all, even while the user is
looking at it. That conflates two different things: *whether this device
participates* and *whether it participates while nobody is watching*.

The intended model is narrower. A paired device should always try to accept
jobs. Background mode decides only whether it keeps doing so once the app is no
longer on screen.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | Two states, not three | The control is background on/off. With background off, closing the app *is* the off-switch — the same way quitting stops the desktop worker. A separate master switch would be a second control for one concept. |
| 2 | The gate stays *paired*, not *signed-in* | Sign-out already keeps the device registered and the worker running (`AccountCoordinator.signOut()` never touches the gateway). Changing that would diverge from the desktop worker and the cross-app invariant. Consequence, accepted: a signed-out paired device can scrape while showing the sign-in gate. |
| 3 | One pure policy function | The "should we be running?" decision currently lives in five places and is about to gain a second input. Centralising it keeps the combinations testable on the JVM. |
| 4 | `ProcessLifecycleOwner` for foreground detection | Driving it from `MainActivity.onStart/onStop` would disconnect and reconnect on every rotation. The gateway keys workers by machineId, so a flapping worker pollutes `GET /v1/devices` and the FIFO fairness ordering. |
| 5 | Never cut a job: drain in-flight work, 45s safety net | Dropping mid-crawl wastes a job the gateway already dispatched, forfeits the credit, and makes the requester wait out a timeout. 45s is the budget the code already assumes per job (`FETCH_WAKELOCK_MS`) and sits above the 30s navigation timeout, so it is a backstop rather than a routine cutoff. Best-effort — see "Latency and the freeze caveat". |

## Architecture

### The policy

A pure function in a new `worker/WorkerPolicy.kt`, replacing
`WorkerServiceLauncher.eligible()` and following the same pure-function shape:

```kotlin
data class WorkerState(val gateway: Boolean, val service: Boolean)

fun desired(
    paired: Boolean,
    gatewayUrl: String,
    backgroundEnabled: Boolean,
    foreground: Boolean,
): WorkerState {
    val usable = paired && gatewayUrl.isNotBlank()
    return WorkerState(
        gateway = usable && (foreground || backgroundEnabled),
        service = usable && backgroundEnabled,
    )
}
```

`gateway` is "hold the WebSocket and accept jobs"; `service` is "run the
`specialUse` foreground service and its notification". The foreground service
exists **only** to survive backgrounding, so it is never needed for the
foreground-only case.

### Who applies it

`AppGraph` registers a `ProcessLifecycleOwner` observer during construction (main
process only, alongside the existing `isMainProcess()` guard) and re-applies the
policy on every input change: foreground transitions, pairing completion, and the
user toggling background mode. Applying means reconciling actual state to desired
— start or stop the gateway, start or stop the service — so every caller performs
the same idempotent reconcile instead of issuing its own start/stop commands.

This removes decision-making from:

- `MainActivity.onStart` — the lifecycle observer already covers foregrounding.
- `MainViewModel.setWorkerEnabled` — persists the pref, then reconciles.
- `AccountCoordinator` — drops `isWorkerEnabled`/`onWorkerEligible` in favour of
  a single "inputs changed" callback after pairing.
- `WorkerService.onStartCommand` — keeps `gatewayClient.start()` only as the
  STICKY-restart defence it already is.
- `BootReceiver` — unchanged; it asks the policy, which still answers false when
  background mode is off.

### Draining

`GatewayClient` gains a drain path used when the policy flips `gateway` to
false while a fetch is in flight:

1. Mark the client draining; accept no new work.
2. A `fetch` frame arriving while draining is answered immediately with a
   failure `result`, so the gateway re-dispatches rather than waiting out a
   timeout.
3. Wait for the in-flight job to finish, capped at 45s, then close the socket.
4. With no job in flight, close immediately.

## Latency and the freeze caveat

The network is only as fast as its slowest worker, so nothing here may make a
requester wait. Concretely:

- **A `fetch` arriving mid-drain is answered immediately with a failure
  result**, never ignored. The gateway re-dispatches on the next tick instead of
  waiting out its own timeout — silence is the expensive case, not failure.
- **The socket closes cleanly and promptly** once drained, so the gateway drops
  the worker from the idle pool at once rather than discovering it via a
  heartbeat or a dispatch that lands on a dead connection.
- **With nothing in flight, teardown is immediate** — the drain path costs
  nothing in the common case.
- **Foreground reconnect is not debounced.** `ProcessLifecycleOwner`'s delay
  applies to the stop direction; coming back, the reconnect is issued as soon as
  the process is foreground, so availability resumes at handshake speed.
- **The stop-direction debounce is the point, not a cost.** It absorbs rotations
  and brief task-switcher peeks, which would otherwise produce a
  disconnect/reconnect per flip — each one a handshake plus a device-token
  verification against the account service, and a churned entry in the gateway's
  FIFO ordering.

**The caveat.** Without a foreground service, the OS may freeze or reclaim the
process during the drain — recent Android freezes cached processes, and a
partial wake lock does not exempt an app from that. So "never disconnect
mid-job" is best-effort in foreground-only mode and cannot be promised.
Background mode is precisely the guarantee: it is the only configuration in
which a crawl is certain to survive the app leaving the screen. Worth stating
plainly in the UI copy rather than implying that the two modes differ only in
convenience.

A frequent open/close cycle also means repeated register-and-verify round trips.
That is the existing per-connect cost rather than a new one — the gateway
already re-verifies live connections every `DEVICE_RESYNC_SEC` — but the
frequency rises, and it is the reason the stop-direction debounce exists.

## Naming and migration

`worker_enabled` → `background_enabled`. The old name will read as the master
switch it used to be, and the pref is the thing whose meaning changed.

Migration seeds the new key from the old one on first read, then removes the
old key: a user who pressed Stop wanted less background activity, which maps
onto the new meaning without surprising them. Absent both keys, the default
stays `true`.

## Copy

The off state is no longer "stopped". Strings needing new text:

- `worker_card_off_title` / `worker_card_off_note`
- `dash_sub_stopped`
- `activity_empty_stopped_note` — currently claims Meerkly "isn't fetching
  anything", which becomes false while the app is open
- the Settings worker row label

The notification action stays "Stop": it does still stop the service. The
notification itself is unaffected, existing only in background mode.

The off-state copy should carry the real difference rather than framing
background mode as mere convenience: with it off, a crawl running when the user
leaves the app may not finish. Something closer to "Meerkly only works while
this screen is open" than "Meerkly is stopped".

## Invariant change

The root and Android `CLAUDE.md` both state that an explicit user Stop is
sticky and that nothing may restart the worker until Start. That becomes:

> An explicit user Stop is sticky **for background operation**: it survives
> restarts and reboots, and no auto-start path may raise the foreground service
> until the user turns background mode back on. Foreground scraping is not
> covered — a paired device resumes accepting jobs whenever the app is open.

## Error handling

- Draining past the 45s cap closes the socket regardless; the gateway observes a
  disconnect and re-dispatches, which is the existing behaviour for a worker
  that drops mid-job. Reaching the cap should be logged — it means a job
  outlived the navigation timeout, which is worth knowing about on its own.
- An unpaired or gateway-less build yields `WorkerState(false, false)` from the
  policy, unchanged from today's `eligible()`.
- Terminal auth failure (`device_auth_failed`) still pauses reconnects inside
  `GatewayClient`; the policy does not override it.
- Foreground reconnect after a network outage keeps the existing backoff — the
  policy asks for a connection, it does not dictate retry timing.

## Testing

- `desired()` truth table: every combination of the four inputs, asserted on the
  JVM with no device.
- Reconcile is idempotent: applying the same inputs twice issues no second
  start/stop.
- Lifecycle transitions: foreground→background with background mode off stops
  the gateway; with it on, does not.
- Drain: a job in flight when the policy flips completes and sends its result;
  a `fetch` arriving mid-drain gets a failure result. Exercisable on the JVM
  because `GatewayClient` already takes `fetchPage` as an injected function
  (`FetchRecordingTest` is the pattern).
- Migration: an install with `worker_enabled=false` reads `backgroundEnabled`
  as false and no longer has the old key.

## Out of scope

- Any change to desktop or headless workers; this is Android-only.
- Sign-out semantics (decision 2 keeps them as they are).
- A three-state control or a separate master switch.
- Play Console resubmission. The Stop button still deactivates the foreground
  service, so the existing declaration video remains accurate; only the
  dashboard's post-Stop wording changes.
