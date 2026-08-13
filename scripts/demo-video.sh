#!/usr/bin/env bash
#
# Record the Play Console declaration video for the specialUse foreground
# service (FOREGROUND_SERVICE_SPECIAL_USE) from a connected device.
#
#   scripts/demo-video.sh
#
# The form wants to see the task that needs the FGS, the ongoing notification,
# the work continuing while the app is backgrounded, and user control. The
# recorded sequence:
#
#   1. Cold start on Home — dashboard in the Stopped state.
#   2. Tap Start — worker card flips, ongoing notification posts.
#   3. Expand the shade — notification with live connection state + Stop.
#   4. Home button — app backgrounded, service keeps running.
#   5. Expand the shade again — notification persists without the app.
#   6. Tap the notification's Stop action — service stops, notification gone.
#   7. Reopen the app — dashboard honestly shows Stopped (sticky Stop).
#
# All taps resolve UI text via `uiautomator dump`, never fixed coordinates
# (same reasoning as screenshots.sh: coordinates move with every screen size).
#
# Recording on a daily-driver phone, the shade is the problem: the video has to
# show Meerkly's notification and nothing else. Two separate fixes, because they
# solve different halves:
#   - Do Not Disturb stops anything NEW from interrupting the take (a heads-up
#     banner landing mid-recording is the worst case).
#   - DND does not remove what is already in the shade, so the run audits the
#     active notifications and waits for you to swipe the strays away.
# SysUI demo mode gives the clean, fixed status bar the screenshots already use.
# All three are restored on exit, including on failure.
#
# Preconditions (the script checks what it can):
#   - device attached, unlocked, app installed, signed in + paired
#   - gateway reachable, so Start actually reaches "Connected"
#
# Output: store-assets/demo/fgs-special-use.mp4 (Play wants a YouTube or
# Drive link — upload it there and paste the link into the form).

set -euo pipefail
cd "$(dirname "$0")/.."

# --- adb (not on PATH on a stock macOS setup) --------------------------------
ADB=""
for candidate in \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "$HOME/Library/Android/sdk/platform-tools/adb" \
    "$(command -v adb 2>/dev/null || true)"; do
  [ -n "$candidate" ] && [ -x "$candidate" ] && ADB="$candidate" && break
done
if [ -z "$ADB" ]; then
  sdk=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -1 || true)
  [ -n "$sdk" ] && [ -x "$sdk/platform-tools/adb" ] && ADB="$sdk/platform-tools/adb"
fi
[ -n "$ADB" ] || {
  echo "error: adb not found. Set ANDROID_HOME, or install platform-tools." >&2
  exit 1
}
"$ADB" get-state >/dev/null 2>&1 || { echo "error: no device attached (adb devices)." >&2; exit 1; }

PKG=com.meerkly.android
OUT_DIR=store-assets/demo
OUT="$OUT_DIR/fgs-special-use.mp4"
REMOTE=/sdcard/meerkly-fgs-demo.mp4

"$ADB" shell pm path "$PKG" >/dev/null 2>&1 || {
  echo "error: $PKG is not installed on the device." >&2
  exit 1
}

# Remember Do Not Disturb as found: this script turns it on, but the phone may
# well have had it on already, and the trap must not "restore" it to off.
ZEN_BEFORE=$("$ADB" shell settings get global zen_mode 2>/dev/null | tr -d '\r\n')
case "$ZEN_BEFORE" in '' | *[!0-9]*) ZEN_BEFORE=0 ;; esac

# --- restore, always ---------------------------------------------------------
# Installed BEFORE anything on the device is touched. The keyguard check below
# is an early exit, and an earlier version armed this trap after it — so a
# locked phone exited having already set `stayon true`, and stayed pinned awake.
restore() {
  echo "→ restoring device"
  "$ADB" shell svc power stayon false >/dev/null 2>&1 || true
  "$ADB" shell cmd statusbar collapse >/dev/null 2>&1 || true
  "$ADB" shell pkill -INT screenrecord >/dev/null 2>&1 || true
  "$ADB" shell rm -f "$REMOTE" >/dev/null 2>&1 || true
  if [ "$ZEN_BEFORE" = "0" ]; then
    "$ADB" shell cmd notification set_dnd off >/dev/null 2>&1 \
      || "$ADB" shell settings put global zen_mode 0 >/dev/null 2>&1 || true
  fi
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  "$ADB" shell settings put global sysui_demo_allowed 0 >/dev/null 2>&1 || true
}
trap restore EXIT INT TERM

# The video is of whatever is on screen; wake, keep awake, refuse a keyguard.
# A secure lockscreen cannot be dismissed over adb — that one is on the human.
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null
"$ADB" shell svc power stayon true >/dev/null
sleep 1
if "$ADB" shell dumpsys window 2>/dev/null | grep -q "isKeyguardShowing=true"; then
  echo "error: device is locked. Unlock it, then re-run." >&2
  exit 1
fi

# The whole point of the video is the notification — make sure it can post.
"$ADB" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

# --- a shade with only Meerkly in it -----------------------------------------
"$ADB" shell cmd notification set_dnd priority >/dev/null 2>&1 \
  || "$ADB" shell settings put global zen_mode 1 >/dev/null 2>&1 || true

# Packages holding a notification right now. `android` is the system's own —
# "USB debugging connected" is unavoidable while adb is attached, and harmless.
shade_pkgs() {
  "$ADB" shell dumpsys notification 2>/dev/null \
    | grep 'NotificationRecord(' \
    | grep -oE 'pkg=[a-zA-Z0-9._]+' | cut -d= -f2 | sort -u || true
}

audit_shade() {
  local strays attempt=0
  while :; do
    strays=$(shade_pkgs | grep -vx "$PKG" | grep -vx "android" | grep -vx "com.android.systemui" || true)
    [ -z "$strays" ] && { echo "  shade is clean"; return 0; }
    echo "  still in the shade:"
    # shellcheck disable=SC2086
    printf '    %s\n' $strays
    if [ ! -t 0 ] || [ "$attempt" -ge 5 ]; then
      echo "  (continuing — these will be visible in the video)"
      return 0
    fi
    attempt=$((attempt + 1))
    printf "  Swipe them away, then press Enter (Ctrl-C to abort): "
    read -r _ || true
  done
}

echo "→ quieting the phone"
audit_shade

# A clean, fixed status bar — the same trick screenshots.sh uses. Deliberately
# omits the `notifications` command: hiding notification icons would hide the
# very thing this video exists to show. `zen hide` drops the DND moon this
# script just caused.
"$ADB" shell settings put global sysui_demo_allowed 1 >/dev/null
DEMO="am broadcast -a com.android.systemui.demo"
"$ADB" shell "$DEMO -e command clock -e hhmm 0900" >/dev/null
"$ADB" shell "$DEMO -e command battery -e level 100 -e plugged false" >/dev/null
"$ADB" shell "$DEMO -e command network -e wifi show -e level 4" >/dev/null
"$ADB" shell "$DEMO -e command network -e mobile show -e level 4 -e datatype none" >/dev/null
"$ADB" shell "$DEMO -e command status -e zen hide" >/dev/null

# --- tap by visible text, never by coordinate --------------------------------
# tap_text <text> [package]  → taps the center of the first visible node whose
# text is exactly <text>, optionally only within <package> (pass
# com.android.systemui to hit a notification action rather than the app's own
# button behind the shade). Retries because `uiautomator dump` fails during
# window animations. Returns 1 if the text never appears.
tap_text() {
  local text="$1" pkg="${2:-}"
  local attempt xml center
  for attempt in 1 2 3 4 5; do
    xml=$("$ADB" exec-out uiautomator dump /dev/tty 2>/dev/null | sed 's/UI hierchary.*$//') || xml=""
    center=$(printf '%s' "$xml" | python3 -c '
import re, sys
text, pkg = sys.argv[1], sys.argv[2]
xml = sys.stdin.read()
for node in re.finditer(r"<node [^>]*/?>", xml):
    n = node.group(0)
    def attr(name):
        m = re.search(name + "=\"([^\"]*)\"", n)
        return m.group(1) if m else ""
    if attr("text") != text: continue
    if pkg and attr("package") != pkg: continue
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attr("bounds"))
    if not m: continue
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 <= x1 or y2 <= y1: continue  # zero-size = not actually visible
    print((x1 + x2) // 2, (y1 + y2) // 2)
    break
' "$text" "$pkg" 2>/dev/null) || center=""
    if [ -n "$center" ]; then
      # shellcheck disable=SC2086
      "$ADB" shell input tap $center >/dev/null
      return 0
    fi
    sleep 1
  done
  return 1
}

# --- get the app into the starting state: Home tab, worker stopped -----------
# Force-stop first: `am start` on a running task brings it forward and DROPS
# the intent, so the screen extra only works on a cold start (see MainActivity;
# the extra is debug-only and harmless on release, where Home is the default).
# meerkly.demo seeds placeholder crawls (DemoData): the activity ring is
# in-memory and starts empty on every cold start, so the feed the video uses to
# show what the service actually DOES would otherwise be the empty state. Both
# extras are debug-only and ignored by release builds.
echo "→ preparing: cold start on Home"
"$ADB" shell am force-stop "$PKG" >/dev/null
"$ADB" shell am start -n "$PKG/.MainActivity" -e meerkly.screen home -e meerkly.demo 1 >/dev/null
sleep 3

# If the worker is running from a previous session, stop it off-camera so the
# recording opens on the Stopped state and shows the full Start→Stop cycle.
if tap_text "Stop" "$PKG"; then
  echo "  worker was running — stopped it before recording"
  sleep 2
fi

if ! "$ADB" exec-out uiautomator dump /dev/tty 2>/dev/null | grep -q 'text="Start"'; then
  echo "error: no Start button on screen. Is the device signed in and paired?" >&2
  echo "       (The video must show the real dashboard — sign in first, then re-run.)" >&2
  exit 1
fi

# --- record ------------------------------------------------------------------
# screenrecord caps at 3 min; the sequence takes ~75s. 8Mbps keeps the file
# well under upload limits at phone resolution.
echo "→ recording"
"$ADB" shell screenrecord --bit-rate 8000000 --time-limit 170 "$REMOTE" &
RECORD_PID=$!
sleep 2

scene() { echo "  $1"; sleep "$2"; }

scene "dashboard, stopped state" 4

echo "  tap Start"
tap_text "Start" "$PKG" || { echo "error: Start button vanished mid-run." >&2; exit 1; }
scene "worker starting → connected (notification posts)" 8

echo "  expand shade: ongoing notification"
"$ADB" shell cmd statusbar expand-notifications >/dev/null
scene "notification with connection state + Stop action" 5
"$ADB" shell cmd statusbar collapse >/dev/null
sleep 2

# The declaration form asks what the service is FOR, not just that it exists.
# The activity feed is that answer on screen: crawls the worker served.
echo "  show what the running service does: Activity tab"
tap_text "Activity" "$PKG" || true
scene "activity feed — pages fetched by the worker" 6
tap_text "Home" "$PKG" || true
sleep 2

echo "  background the app (worker keeps running)"
"$ADB" shell input keyevent KEYCODE_HOME >/dev/null
scene "launcher — app backgrounded" 3

echo "  expand shade: notification persists without the app"
"$ADB" shell cmd statusbar expand-notifications >/dev/null
scene "same ongoing notification, app not in foreground" 5

echo "  tap Stop in the notification"
if tap_text "Stop" "com.android.systemui"; then
  scene "service stops, notification clears" 4
else
  # Some OEM shades wrap actions oddly; fall back to stopping in-app so the
  # video still ends on user control rather than hanging.
  echo "  (couldn't hit the shade action — stopping in-app instead)"
  "$ADB" shell cmd statusbar collapse >/dev/null
  "$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
  sleep 3
  tap_text "Stop" "$PKG" || true
  scene "stopped in-app" 3
fi
"$ADB" shell cmd statusbar collapse >/dev/null 2>&1 || true
sleep 1

echo "  reopen app: sticky Stopped state"
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
scene "dashboard shows Stopped — nothing auto-restarts" 5

# --- finish: stop recording, pull, validate ----------------------------------
echo "→ finalizing"
"$ADB" shell pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$RECORD_PID" 2>/dev/null || true
sleep 2  # let the muxer close the file before pulling

mkdir -p "$OUT_DIR"
"$ADB" pull "$REMOTE" "$OUT" >/dev/null
"$ADB" shell rm -f "$REMOTE" >/dev/null 2>&1 || true

bytes=$(wc -c <"$OUT")
echo
echo "→ $OUT ($((bytes / 1024 / 1024))MB)"
[ "$bytes" -gt 100000 ] || { echo "error: file suspiciously small — recording failed?" >&2; exit 1; }
echo
echo "Review before uploading:"
echo "  - top bar shows the signed-in email; crop/blur or use a test account if that matters"
echo "  - the notification should read Connected while the shade is open"
echo "  - upload to YouTube (unlisted) or Drive and paste the link into the Play form"
