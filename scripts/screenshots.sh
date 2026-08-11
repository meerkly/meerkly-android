#!/usr/bin/env bash
#
# Capture Play Store screenshots from a connected device.
#
#   scripts/screenshots.sh phone|tablet7|tablet10|all
#
# Play wants 2–8 shots per form factor, and tablet sets are separate from
# phone. Rather than a blank emulator, this resizes the *connected* device with
# `wm size`/`wm density` — the only way to get real credits, a real activity
# feed and a real connection state into tablet shots. `adb exec-out screencap`
# grabs the logical framebuffer, so the PNG is a correct 2560x1600 even though
# the physical panel renders it squashed.
#
# Everything it changes is restored on exit, including on failure — a device
# left at 1600x2560 is a genuinely unpleasant surprise.
#
# Output: store-assets/screenshots/<slot>/NN-<name>.png

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
  # local.properties is gitignored but usually has the SDK path.
  sdk=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -1 || true)
  [ -n "$sdk" ] && [ -x "$sdk/platform-tools/adb" ] && ADB="$sdk/platform-tools/adb"
fi
[ -n "$ADB" ] || {
  echo "error: adb not found. Set ANDROID_HOME, or install platform-tools." >&2
  exit 1
}
"$ADB" get-state >/dev/null 2>&1 || { echo "error: no device attached (adb devices)." >&2; exit 1; }

PKG=com.meerkly.android
OUT_ROOT=store-assets/screenshots

# --- restore, always ---------------------------------------------------------
restore() {
  echo "→ restoring device"
  "$ADB" shell wm size reset >/dev/null 2>&1 || true
  "$ADB" shell wm density reset >/dev/null 2>&1 || true
  "$ADB" shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
  "$ADB" shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  "$ADB" shell settings put global sysui_demo_allowed 0 >/dev/null 2>&1 || true
}
trap restore EXIT INT TERM

# --- a clean status bar (the trick Google's own tooling uses) ----------------
demo_mode_on() {
  "$ADB" shell settings put global sysui_demo_allowed 1 >/dev/null
  local d="am broadcast -a com.android.systemui.demo"
  "$ADB" shell "$d -e command clock -e hhmm 0900" >/dev/null
  "$ADB" shell "$d -e command battery -e level 100 -e plugged false" >/dev/null
  "$ADB" shell "$d -e command network -e wifi show -e level 4" >/dev/null
  "$ADB" shell "$d -e command network -e mobile show -e level 4 -e datatype none" >/dev/null
  "$ADB" shell "$d -e command notifications -e visible false" >/dev/null
}

shoot() {              # shoot <slot> <nn> <name> <destination-key|->
  local slot="$1" nn="$2" name="$3" screen="$4"
  local dir="$OUT_ROOT/$slot"
  mkdir -p "$dir"
  if [ "$screen" != "-" ]; then
    # Debug-only intent extra, so we never tap fixed coordinates that would
    # move at every size. See MainActivity.
    "$ADB" shell am start -n "$PKG/.MainActivity" -e meerkly.screen "$screen" >/dev/null
  fi
  sleep 2.5
  "$ADB" exec-out screencap -p > "$dir/$nn-$name.png"
  printf "  %s/%s-%s.png\n" "$slot" "$nn" "$name"
}

capture_slot() {       # capture_slot <slot> <w> <h> <density> <rotate:0|1>
  local slot="$1" w="$2" h="$3" density="$4" rotate="$5"
  echo "→ $slot (${w}x${h} @ ${density}dpi, rotate=$rotate)"
  "$ADB" shell wm size "${w}x${h}" >/dev/null
  "$ADB" shell wm density "$density" >/dev/null
  "$ADB" shell settings put system accelerometer_rotation 0 >/dev/null
  "$ADB" shell settings put system user_rotation "$rotate" >/dev/null
  "$ADB" shell am force-stop "$PKG" >/dev/null
  sleep 1
  demo_mode_on

  shoot "$slot" 01 home     home
  shoot "$slot" 02 activity activity
  shoot "$slot" 03 devices  devices
  shoot "$slot" 04 settings settings
}

case "${1:-all}" in
  phone)    slots="phone" ;;
  tablet7)  slots="tablet7" ;;
  tablet10) slots="tablet10" ;;
  all)      slots="phone tablet7 tablet10" ;;
  *) echo "usage: $0 phone|tablet7|tablet10|all" >&2; exit 1 ;;
esac

for slot in $slots; do
  case "$slot" in
    # 1080x1920 @420 = 411x731dp. Compact, and a native 9:16 that sidesteps
    # Play's phone aspect-ratio rule (the Pixel's own 1080x2400 is 9:20).
    phone)    capture_slot phone    1080 1920 420 0 ;;
    # Landscape on both tablet slots: that's where the two-pane layout lives,
    # and it's what the large-screen quality bar wants to see.
    tablet7)  capture_slot tablet7  1200 1920 320 1 ;;
    tablet10) capture_slot tablet10 1600 2560 320 1 ;;
  esac
done

echo
echo "→ validating"
fail=0
for f in $(find "$OUT_ROOT" -name '*.png' 2>/dev/null); do
  read -r w h < <(magick identify -format "%w %h" "$f" 2>/dev/null || echo "0 0")
  bytes=$(wc -c <"$f")
  problem=""
  [ "$w" -lt 320 ] || [ "$h" -lt 320 ] && problem="too small"
  [ "$w" -gt 3840 ] || [ "$h" -gt 3840 ] && problem="too large"
  [ "$bytes" -gt 8388608 ] && problem="over 8MB"
  printf "  %5s x %-5s %6sKB  %s%s\n" "$w" "$h" "$((bytes / 1024))" "$f" \
    "${problem:+  <-- $problem}"
  [ -n "$problem" ] && fail=1
done
[ "$fail" -eq 0 ] || { echo "error: some shots fail Play's limits" >&2; exit 1; }

echo
echo "Check each shot before uploading: no personal email, no full machine id."
