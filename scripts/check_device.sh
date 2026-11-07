#!/usr/bin/env bash
# Запуск на подключённом arm64-устройстве с Android 10+.
set -eu -o pipefail
cd "$(dirname "$0")/.."
[[ $# == 1 && -f "$1" ]] || { echo 'Передайте путь к записи диалога в WAV.' >&2; exit 2; }
adb install -r artifacts/neuro-steno-debug.apk
adb install -r artifacts/neuro-steno-test.apk
dir=/sdcard/Android/data/ru.steno/files
adb shell am start -W -n ru.steno/.MainActivity >/dev/null
adb shell mkdir -p "$dir"
adb push "$1" "$dir/evaluation.wav"
log=$(mktemp)
trap 'rm -f "$log"' EXIT
adb shell am instrument -w -e class ru.steno.OfflinePipelineTest -e audio "$dir/evaluation.wav" ru.steno.test/androidx.test.runner.AndroidJUnitRunner | tee "$log"
grep -q 'OK (1 test)' "$log"
mkdir -p artifacts/device-results
adb pull "$dir/debug/." artifacts/device-results/
