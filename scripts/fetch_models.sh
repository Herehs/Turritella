#!/usr/bin/env bash
# Устанавливает модели в assets перед сборкой APK на компьютере разработчика.
set -Eeuo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
assets="$project_dir/src/app/src/main/assets/models"
libs="$project_dir/src/app/libs"
cache="$project_dir/.model-downloads"
checksums="$project_dir/config/models.sha256"
base="https://github.com/k2-fsa/sherpa-onnx/releases/download"

if [[ "${1:-}" == "--help" ]]; then
    echo "Использование: bash scripts/fetch_models.sh [--check]"
    echo "Без параметров — скачать недостающие или повреждённые модели."
    echo "--check — только проверить уже установленные файлы, без сети."
    exit 0
fi
if [[ $# -gt 1 || ( $# -eq 1 && "$1" != "--check" ) ]]; then
    echo "Неизвестный параметр. Используйте --help." >&2
    exit 2
fi
for command in curl tar bzip2 sha256sum awk flock; do
    command -v "$command" >/dev/null || {
        echo "Не найдена команда $command. Установите её и повторите запуск." >&2
        exit 1
    }
done
[[ -f "$checksums" ]] || { echo "Отсутствует $checksums" >&2; exit 1; }
mkdir -p "$assets" "$libs" "$cache"
exec 9>"$cache/install.lock"
flock -n 9 || { echo "Другой процесс уже устанавливает модели." >&2; exit 1; }
trap 'echo "Установка прервана. Повторите запуск для продолжения." >&2' ERR

aar_hash="0a44f3aa516c31eb74cef0ea6bbacf76095434bf6c89be728226c7ecf550232f"
valid() {
    local path="$1" expected="$2" actual
    [[ -f "$path" ]] || return 1
    actual="$(sha256sum "$path")"
    [[ "${actual%% *}" == "$expected" ]]
}
model_hash() {
    awk -v name="$1" '$2 == name { print $1; found=1 } END { if (!found) exit 1 }' "$checksums"
}
verify_all() {
    (cd "$assets" && sha256sum -c "$checksums")
    valid "$libs/sherpa.aar" "$aar_hash" || {
        echo "sherpa.aar отсутствует или повреждён." >&2
        return 1
    }
}
if [[ "${1:-}" == "--check" ]]; then
    verify_all
    echo "Все модели и sherpa.aar прошли проверку."
    exit 0
fi

download() {
    local url="$1" path="$2"
    echo "Загрузка $(basename "$path")"
    # Частичный файл остаётся после обрыва. HTTP Range позволяет продолжить загрузку.
    curl --fail --location --retry 3 --retry-delay 2 --connect-timeout 30 \
        --continue-at - "$url" --output "$path"
}
install_file() {
    local url="$1" target="$2" expected="$3"
    if valid "$target" "$expected"; then
        echo "Уже установлен $(basename "$target")"
        return
    fi
    download "$url" "$target.part"
    if ! valid "$target.part" "$expected"; then
        mv -f "$target.part" "$target.invalid"
        echo "Ошибка SHA-256 для $target. Файл сохранён как .invalid; повторите запуск." >&2
        return 1
    fi
    mv -f "$target.part" "$target"
}
install_archive_member() {
    local tag="$1" archive_name="$2" member="$3" name="$4"
    local expected archive
    expected="$(model_hash "$name")"
    if valid "$assets/$name" "$expected"; then
        echo "Уже установлен $name"
        return
    fi
    archive="$cache/$archive_name.tar.bz2"
    if [[ ! -f "$archive" ]]; then
        download "$base/$tag/$archive_name.tar.bz2" "$archive.part"
        if ! bzip2 -t "$archive.part"; then
            mv -f "$archive.part" "$archive.invalid"
            return 1
        fi
        mv -f "$archive.part" "$archive"
    fi
    tar -xOjf "$archive" "$archive_name/$member" > "$assets/$name.part"
    if ! valid "$assets/$name.part" "$expected"; then
        mv -f "$assets/$name.part" "$assets/$name.invalid"
        mv -f "$archive" "$archive.invalid"
        echo "Ошибка SHA-256 для $name. Повторный запуск заново скачает архив." >&2
        return 1
    fi
    mv -f "$assets/$name.part" "$assets/$name"
}

echo "Установка офлайн-моделей. Требуется около 2 ГБ свободного места с учётом кэша."
install_file "$base/v1.12.30/sherpa-onnx-1.12.30.aar" "$libs/sherpa.aar" "$aar_hash"
giga="sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19"
install_archive_member asr-models "$giga" model.int8.onnx giga.onnx
install_archive_member asr-models "$giga" tokens.txt tokens.txt
install_archive_member speaker-segmentation-models sherpa-onnx-pyannote-segmentation-3-0 model.int8.onnx segment.onnx
install_file "$base/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx" "$assets/voice.onnx" "$(model_hash voice.onnx)"
install_file "$base/asr-models/silero_vad.onnx" "$assets/vad.onnx" "$(model_hash vad.onnx)"
install_file "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task" "$assets/qwen.task" "$(model_hash qwen.task)"

verify_all
cp "$checksums" "$assets/SHA256SUMS.part"
mv -f "$assets/SHA256SUMS.part" "$assets/SHA256SUMS"
echo "Готово. Модели установлены в $assets"
echo "Теперь можно собрать APK: ./gradlew :app:assembleDebug"
