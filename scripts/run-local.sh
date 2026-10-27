#!/usr/bin/env bash
# End-to-end local run: install app from DMG -> start Appium (Mac2) -> run TestNG -> collect artifacts.
# Usage: scripts/run-local.sh [dmg path or URL] [testng suite xml]
#   defaults: the bundled sample DMG and suites/sample-app.xml
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
DMG="${1:-auto}"
SUITE="${2:-suites/sample-app.xml}"
# "auto" picks the DMG that matches the suite (used by hyperexecute.yaml).
if [[ "$DMG" == "auto" ]]; then
  case "$SUITE" in
    *marktext*) DMG="${MARKTEXT_DMG_URL:-https://github.com/marktext/marktext/releases/download/v0.23.0/marktext-mac-arm64-0.23.0.dmg}" ;;
    *)          DMG="$ROOT/dist/DMGSampleApp.dmg" ;;
  esac
fi
PORT="${APPIUM_PORT:-4723}"
RESULTS="$ROOT/build/test-results"
export APPIUM_HOME="$ROOT/.appium"
export APPIUM_URL="http://127.0.0.1:$PORT"

[[ -f "$DMG" || "$DMG" =~ ^https?:// ]] || ./scripts/build-sample-dmg.sh
# MarkTextTest reads its own env file, so both apps can be installed side by side.
case "$SUITE" in
  *marktext*) ENV_FILE="$ROOT/build/installed-marktext.env" ;;
  *)          ENV_FILE="$ROOT/build/installed-app.env" ;;
esac
./scripts/install-dmg.sh "$DMG" "$ROOT/build/installed" "$ENV_FILE"

rm -rf "$RESULTS" && mkdir -p "$RESULTS"
npx appium --address 127.0.0.1 --port "$PORT" --log "$RESULTS/appium.log" --log-level info:debug &
APPIUM_PID=$!
trap 'kill $APPIUM_PID 2>/dev/null || true' EXIT

for _ in $(seq 1 60); do
  curl -sf "$APPIUM_URL/status" >/dev/null && break
  sleep 1
done
curl -sf "$APPIUM_URL/status" >/dev/null || { echo "Appium did not start; see $RESULTS/appium.log" >&2; exit 1; }

set +e
mvn test -Dsuite="$SUITE"
STATUS=$?
set -e

cp -R target/surefire-reports "$RESULTS/" 2>/dev/null || true
echo "Artifacts in $RESULTS (appium.log, surefire-reports/, screenshots/)"
exit $STATUS
