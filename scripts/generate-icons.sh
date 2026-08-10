#!/usr/bin/env bash
#
# Rasterizes icon-src/*.svg into the launcher resources under app/src/main/res.
# Mirrors meerkly-desktop/scripts/generate-icons.sh — edit the SVGs, re-run this.
#
# The SVGs are the source of truth. They are rasterized rather than hand-ported
# to VectorDrawables because the mascot leans on objectBoundingBox gradients,
# which VectorDrawable only expresses in user-space coordinates — converting
# them by hand invites silent drift from the desktop icon.
#
# Requires: rsvg-convert (brew install librsvg), magick (brew install imagemagick)

set -euo pipefail

cd "$(dirname "$0")/.."
SRC="icon-src"
RES="app/src/main/res"

for tool in rsvg-convert magick; do
  command -v "$tool" >/dev/null || { echo "error: $tool not found (brew install librsvg imagemagick)" >&2; exit 1; }
done

# density:legacy launcher px (48dp):adaptive layer px (108dp)
BUCKETS=(
  "mdpi:48:108"
  "hdpi:72:162"
  "xhdpi:96:216"
  "xxhdpi:144:324"
  "xxxhdpi:192:432"
)

for bucket in "${BUCKETS[@]}"; do
  IFS=: read -r dpi legacy adaptive <<<"$bucket"
  mip="$RES/mipmap-$dpi"
  drw="$RES/drawable-$dpi"
  mkdir -p "$mip" "$drw"

  # --- Adaptive icon layers (API 26+; the OS masks these itself) ---
  for layer in background foreground monochrome; do
    rsvg-convert -w "$adaptive" -h "$adaptive" "$SRC/ic_launcher_$layer.svg" \
      -o "$drw/ic_launcher_$layer.png"
    magick "$drw/ic_launcher_$layer.png" -define webp:lossless=true \
      "$drw/ic_launcher_$layer.webp"
    rm -f "$drw/ic_launcher_$layer.png"
  done

  # --- Legacy raster launcher icons (pre-26 launchers, Play listing) ---
  # The 1024 desktop icon already carries its own rounded body and margin.
  rsvg-convert -w "$legacy" -h "$legacy" "$SRC/ic_launcher_legacy.svg" -o "$mip/ic_launcher.png"
  magick "$mip/ic_launcher.png" \
    \( -size "${legacy}x${legacy}" xc:none -fill white \
       -draw "circle $((legacy/2)),$((legacy/2)) $((legacy/2)),0" \) \
    -alpha set -compose DstIn -composite \
    "$mip/ic_launcher_round.png"

  magick "$mip/ic_launcher.png" -define webp:lossless=true "$mip/ic_launcher.webp"
  magick "$mip/ic_launcher_round.png" -define webp:lossless=true "$mip/ic_launcher_round.webp"
  rm -f "$mip/ic_launcher.png" "$mip/ic_launcher_round.png"
done

# The adaptive layers now live in drawable-*; drop the checked-in vector stubs
# that AGP's template shipped so there is exactly one definition of each.
rm -f "$RES/drawable/ic_launcher_background.xml" "$RES/drawable/ic_launcher_foreground.xml"

# Play Store listing icon: 512x512 PNG, no alpha (Play rejects transparency).
mkdir -p build/play
rsvg-convert -w 512 -h 512 "$SRC/ic_launcher_legacy.svg" -o build/play/.store-rgba.png
magick build/play/.store-rgba.png -background "#faf4e8" -alpha remove -alpha off \
  build/play/play-store-icon.png
rm -f build/play/.store-rgba.png

echo "icons regenerated:"
echo "  $RES/drawable-*/ic_launcher_{background,foreground,monochrome}.webp  (adaptive layers)"
echo "  $RES/mipmap-*/ic_launcher{,_round}.webp                              (legacy raster)"
echo "  build/play/play-store-icon.png                                       (512x512 store listing)"
