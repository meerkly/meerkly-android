#!/usr/bin/env bash
#
# Cut a release: bump the version, commit, tag, and (optionally) push.
# The Android twin of `npm version patch` in the desktop repos — the point is
# that versionName and the git tag can never drift, because one command sets
# both and the Release workflow refuses to build when they disagree.
#
#   scripts/release.sh 1.1          # bump to 1.1, commit + tag v1.1
#   scripts/release.sh 1.1 --push   # ...and push so the workflow starts
#
# See --help for the one-time signing setup.

set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE="app/build.gradle.kts"

usage() {
  cat <<'EOF'
Usage: scripts/release.sh <versionName> [--push]

Bumps versionName to <versionName> and versionCode by 1 in app/build.gradle.kts,
commits both, and creates the matching tag v<versionName>.

  --push    also run `git push --follow-tags`, which starts the Release workflow

The workflow builds signed per-ABI APKs (~100 MB each) and opens a DRAFT
GitHub release. Install one, check it runs, then press Publish.

You can also start it by hand from the Actions tab ("Release" -> Run workflow)
for a tag that already exists.

ONE-TIME SIGNING SETUP
----------------------
The workflow signs with an upload key you own; it fails rather than emit an
unsigned APK (Android won't install one). Create the key:

  keytool -genkeypair -v -keystore meerkly-release.jks -alias meerkly \
    -keyalg RSA -keysize 4096 -validity 10000

Keep that file and its passwords in 1Password — losing them means an upload-key
reset with Google. Then add four repo secrets:

  gh secret set MEERKLY_KEYSTORE_BASE64 < <(base64 -i meerkly-release.jks)
  gh secret set MEERKLY_KEYSTORE_PASSWORD  # unlocks the keystore file
  gh secret set MEERKLY_KEY_ALIAS          # the entry name: "meerkly" above
  gh secret set MEERKLY_KEY_PASSWORD       # SAME as the keystore password

The last two trip people up. The alias names one key inside the keystore (a
keystore can hold several) — `keytool -list -keystore meerkly-release.jks`
prints it. The key password is nominally separate from the store password, but
keytool writes PKCS12 by default and PKCS12 has no per-entry password: it warns
"Different store and key passwords not supported for PKCS12 KeyStores" and
ignores -keypass. So give both password secrets the same value, or signing
fails with a wrong-password error on a password that looks correct.

For local signed builds instead, put storeFile/storePassword/keyAlias/keyPassword
in keystore.properties (gitignored) — see app/build.gradle.kts.
EOF
}

if [ $# -eq 0 ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  usage
  exit 0
fi

VERSION="$1"
PUSH="${2:-}"

if ! [[ "$VERSION" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
  echo "error: version must look like 1.1 or 1.2.3 (got '$VERSION')" >&2
  exit 1
fi

# A dirty tree would sweep unrelated work into the release commit.
if [ -n "$(git status --porcelain)" ]; then
  echo "error: working tree is dirty — commit or stash first." >&2
  git status --short >&2
  exit 1
fi

if git rev-parse "v$VERSION" >/dev/null 2>&1; then
  echo "error: tag v$VERSION already exists." >&2
  exit 1
fi

current_name=$(grep -oE 'versionName = "[^"]+"' "$GRADLE" | head -1 | sed -E 's/.*"(.*)"/\1/')
current_code=$(grep -oE 'versionCode = [0-9]+' "$GRADLE" | head -1 | grep -oE '[0-9]+')
next_code=$((current_code + 1))

# versionCode must strictly increase or Android refuses the upgrade — bumping
# it is not optional, so it happens here rather than being remembered.
sed -i '' -E "s/versionName = \"$current_name\"/versionName = \"$VERSION\"/" "$GRADLE"
sed -i '' -E "s/versionCode = $current_code/versionCode = $next_code/" "$GRADLE"

echo "versionName $current_name -> $VERSION"
echo "versionCode $current_code -> $next_code"

git add "$GRADLE"
git commit -m "Release v$VERSION"
git tag -a "v$VERSION" -m "Meerkly v$VERSION"

if [ "$PUSH" = "--push" ]; then
  git push --follow-tags
  echo
  echo "Pushed. Watch the build:"
  echo "  gh run watch"
else
  echo
  echo "Committed and tagged locally. To start the release:"
  echo "  git push --follow-tags"
fi
