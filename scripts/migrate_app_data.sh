#!/usr/bin/env bash
# Carry PhoneMood's local records from the old package to the new one.
#
# The applicationId changed from com.phonemood to com.phonemood.app. Android treats that
# as a different app, so the new build installs beside the old one with an empty database
# instead of updating it. Both builds are debuggable, so run-as can reach both data
# directories. The Room schema is version 3 on either side, so the files are copied as
# they are; nothing is migrated or rewritten.
#
# Usage:  scripts/migrate_app_data.sh [backup.tar]
set -euo pipefail

OLD=${OLD_PACKAGE:-com.phonemood}
NEW=${NEW_PACKAGE:-com.phonemood.app}
BACKUP=${1:-phonemood-$OLD-$(date +%Y%m%d-%H%M%S).tar}

# adb is not on PATH on this machine; fall back to the SDK location.
if ! command -v adb >/dev/null 2>&1; then
    SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' "$(dirname "$0")/../local.properties" 2>/dev/null)}}
    SDK=${SDK:-$HOME/Library/Android/sdk}
    [ -x "$SDK/platform-tools/adb" ] || { echo "adb not found; set ANDROID_HOME" >&2; exit 1; }
    adb() { "$SDK/platform-tools/adb" "$@"; }
fi

adb get-state >/dev/null

for pkg in "$OLD" "$NEW"; do
    adb shell pm path "$pkg" >/dev/null 2>&1 || { echo "Not installed: $pkg" >&2; exit 1; }
    adb shell run-as "$pkg" true >/dev/null 2>&1 || { echo "run-as refused, not a debuggable build: $pkg" >&2; exit 1; }
done

# Stop both so Room flushes its write-ahead log and nothing writes during the copy.
adb shell am force-stop "$OLD"
adb shell am force-stop "$NEW"

# databases/ holds the Room file plus its -wal and -shm; files/datastore holds the settings.
SOURCES=()
for path in databases files/datastore; do
    if adb shell run-as "$OLD" test -e "$path" >/dev/null 2>&1; then SOURCES+=("$path"); fi
done
[ ${#SOURCES[@]} -gt 0 ] || { echo "Nothing to copy: $OLD has no records" >&2; exit 1; }

adb exec-out run-as "$OLD" tar c "${SOURCES[@]}" > "$BACKUP"
echo "Saved $(wc -c < "$BACKUP" | tr -d ' ') bytes from $OLD to $BACKUP"

# Only now, with the backup on this machine, clear what the new install created for itself.
for path in "${SOURCES[@]}"; do
    adb shell run-as "$NEW" rm -rf "$path" >/dev/null 2>&1 || true
done

adb push "$BACKUP" /data/local/tmp/phonemood-migration.tar >/dev/null
adb shell run-as "$NEW" sh -c "cd /data/data/$NEW && tar xf /data/local/tmp/phonemood-migration.tar"
adb shell rm -f /data/local/tmp/phonemood-migration.tar

echo "Records are now in $NEW."
echo "Open it, confirm today's screen and your mood history look right, and only then uninstall $OLD."
