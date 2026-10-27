#!/usr/bin/env bash
# Compiles sample-app/main.swift into DMGSampleApp.app and packages it as dist/DMGSampleApp.dmg.
# Stand-in for the customer's build stage: any real .dmg can replace the output.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/build/sample-app"
APP="$BUILD/DMGSampleApp.app"
DIST="$ROOT/dist"
BUNDLE_ID="com.testmu.samples.DMGSampleApp"

rm -rf "$BUILD" && mkdir -p "$APP/Contents/MacOS" "$DIST"

swiftc -O "$ROOT/sample-app/main.swift" -o "$APP/Contents/MacOS/DMGSampleApp"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleIdentifier</key><string>${BUNDLE_ID}</string>
  <key>CFBundleName</key><string>DMGSampleApp</string>
  <key>CFBundleExecutable</key><string>DMGSampleApp</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0.0</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>LSMinimumSystemVersion</key><string>12.0</string>
  <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST

# Ad-hoc sign so Gatekeeper/launch services accept the copied bundle locally.
codesign --force --deep --sign - "$APP"

# Standard drag-to-install layout: the .app plus an /Applications symlink.
STAGE="$BUILD/dmg-root"
mkdir -p "$STAGE"
cp -R "$APP" "$STAGE/"
ln -s /Applications "$STAGE/Applications"

rm -f "$DIST/DMGSampleApp.dmg"
hdiutil create -volname "DMGSampleApp" -srcfolder "$STAGE" -ov -format UDZO "$DIST/DMGSampleApp.dmg" >/dev/null
echo "Built $DIST/DMGSampleApp.dmg"
