#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
adb="$sdk/platform-tools/adb"
emulator="$sdk/emulator/emulator"
avd="PhoneMood_API35"
apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
[[ -x "$adb" && -x "$emulator" ]] || { echo '找不到 Android SDK。请参阅 docs/COMPUTER_TESTING.md。'; exit 1; }
[[ -f "$apk" ]] || { echo "找不到安装包：$apk。请先运行 scripts/build.sh。"; exit 1; }
"$emulator" -list-avds | tr -d '\r' | grep -qx "$avd" || { echo "找不到模拟器 $avd。请参阅 docs/COMPUTER_TESTING.md。"; exit 1; }
"$adb" start-server
serial=''
# Never target a physical phone or an unrelated emulator.
while read -r device state rest; do
    if [[ "$device" == emulator-* && "$state" == device ]]; then
        name=$("$adb" -s "$device" emu avd name 2>/dev/null | head -1 | tr -d '\r')
        if [[ "$name" == "$avd" ]]; then serial="$device"; break; fi
    fi
done < <("$adb" devices)
if [[ -z "$serial" ]]; then
    port=5554
    while "$adb" devices | grep -q "emulator-$port" || lsof -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; do
        port=$((port + 2))
        [[ "$port" -le 5682 ]] || { echo '没有可用的模拟器端口。'; exit 1; }
    done
    serial="emulator-$port"
    mkdir -p .tools
    echo '正在启动 Android 模拟器，首次打开可能需要一两分钟…'
    nohup "$emulator" -avd "$avd" -port "$port" -no-audio -no-boot-anim -gpu swiftshader_indirect > .tools/emulator-desktop.log 2>&1 < /dev/null &
fi
ready=false
for ((attempt=0; attempt<120; attempt++)); do
    if [[ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then ready=true; break; fi
    sleep 1
done
[[ "$ready" == true ]] || { echo '启动超时，请查看 .tools/emulator-desktop.log。'; exit 1; }
echo '正在安装 PhoneMood（保留已有记录）…'
"$adb" -s "$serial" install -r "$apk"
"$adb" -s "$serial" shell input keyevent KEYCODE_WAKEUP
"$adb" -s "$serial" shell wm dismiss-keyguard
"$adb" -s "$serial" shell am start -W -n com.phonemood/.ui.MainActivity
echo 'PhoneMood 已打开。请在模拟器内操作；语言跟随模拟器的 Android 系统设置。'
