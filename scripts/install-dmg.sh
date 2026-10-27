#!/usr/bin/env bash
# Mounts a .dmg, copies the first .app it contains into an isolated install dir, then detaches.
# Usage: scripts/install-dmg.sh <path-or-url-to.dmg> [install-dir] [env-file]
# Writes the installed app path and bundle id to the env file (default build/installed-app.env) for the tests to read.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${1:?usage: install-dmg.sh <dmg path or URL> [install dir]}"
INSTALL_DIR="${2:-$ROOT/build/installed}"
ENV_FILE="${3:-$ROOT/build/installed-app.env}"
mkdir -p "$ROOT/build" "$INSTALL_DIR"
# Absolute, so the env file points at the app from any working directory.
INSTALL_DIR="$(cd "$INSTALL_DIR" && pwd)"

DMG="$SOURCE"
if [[ "$SOURCE" =~ ^https?:// ]]; then
  DMG="$ROOT/build/downloaded.dmg"
  echo "Downloading $SOURCE"
  curl -fL --retry 3 -o "$DMG" "$SOURCE"
fi

MOUNT_POINT="$(mktemp -d /tmp/dmg-mount.XXXXXX)"
cleanup() { hdiutil detach "$MOUNT_POINT" -quiet -force 2>/dev/null || true; rmdir "$MOUNT_POINT" 2>/dev/null || true; }
trap cleanup EXIT

# -nobrowse keeps it out of Finder; yes handles DMGs that show a license agreement.
(yes || true) | hdiutil attach "$DMG" -nobrowse -noautoopen -readonly -mountpoint "$MOUNT_POINT" >/dev/null

APP_SRC="$(find "$MOUNT_POINT" -maxdepth 2 -name '*.app' -type d | head -1)"
[[ -n "$APP_SRC" ]] || { echo "No .app found in $DMG" >&2; exit 1; }

APP_DEST="$INSTALL_DIR/$(basename "$APP_SRC")"
rm -rf "$APP_DEST"
ditto "$APP_SRC" "$APP_DEST"
# Downloaded DMGs carry the quarantine flag, which would block an unattended first launch.
xattr -dr com.apple.quarantine "$APP_DEST" 2>/dev/null || true

BUNDLE_ID="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP_DEST/Contents/Info.plist")"
cat > "$ENV_FILE" <<ENV
APP_PATH=$APP_DEST
BUNDLE_ID=$BUNDLE_ID
ENV
echo "Installed $APP_DEST ($BUNDLE_ID)"
