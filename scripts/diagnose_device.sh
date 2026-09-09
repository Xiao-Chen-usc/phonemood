#!/usr/bin/env bash
# Collect why a check-in never reached the user, from the phone itself.
# Reads only PhoneMood's own state; writes a copy of its database to the given folder.
set -uo pipefail

PKG=${PACKAGE:-com.phonemood}
SERIAL=${SERIAL:-}
OUT=${1:-phonemood-diagnosis}

if ! command -v adb >/dev/null 2>&1; then
    SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' "$(dirname "$0")/../local.properties" 2>/dev/null)}}
    SDK=${SDK:-$HOME/Library/Android/sdk}
    adb() { "$SDK/platform-tools/adb" ${SERIAL:+-s "$SERIAL"} "$@"; }
else
    adb() { command adb ${SERIAL:+-s "$SERIAL"} "$@"; }
fi

mkdir -p "$OUT"
echo "### installed build"
adb shell dumpsys package "$PKG" | grep -E "versionCode|versionName|firstInstallTime|lastUpdateTime" | head -4

echo "### is the monitor alive"
adb shell ps -A | grep "$PKG" || echo "NO PROCESS - the app is not running at all"
adb shell dumpsys activity services "$PKG" | grep -E "ServiceRecord|isForeground|createTime|app=" | head -8 || echo "NO SERVICE RECORD"

echo "### permissions that gate a check-in"
echo -n "usage access:   "; adb shell cmd appops get "$PKG" GET_USAGE_STATS 2>/dev/null | head -1
echo -n "overlay:        "; adb shell cmd appops get "$PKG" SYSTEM_ALERT_WINDOW 2>/dev/null | head -1
echo -n "notifications:  "; adb shell cmd appops get "$PKG" POST_NOTIFICATION 2>/dev/null | head -1
echo -n "battery:        "; adb shell dumpsys deviceidle whitelist 2>/dev/null | grep "$PKG" || echo "NOT whitelisted (may be killed in the background)"

echo "### notification channel state"
adb shell dumpsys notification --noredact 2>/dev/null | grep -A6 "$PKG" | grep -iE "importance|banned|channel|id=mood" | head -12

echo "### app standby bucket (10=active, 45=rare, 50=restricted)"
adb shell am get-standby-bucket "$PKG" 2>/dev/null

echo "### copying the database"
adb shell am force-stop "$PKG"
adb exec-out run-as "$PKG" tar c databases > "$OUT/databases.tar" 2>/dev/null && tar xf "$OUT/databases.tar" -C "$OUT" && echo "saved to $OUT/databases/" || echo "run-as refused: not a debuggable build"
