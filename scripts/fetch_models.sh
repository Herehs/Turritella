#!/usr/bin/env bash
# Выполняется на машине разработчика. На телефоне сеть не нужна.
set -eu -o pipefail
cd "$(dirname "$0")/.."
assets=src/app/src/main/assets/models
libs=src/app/libs
mkdir -p "$assets" "$libs"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
base=https://github.com/k2-fsa/sherpa-onnx/releases/download
fetch() {
  if [[ ! -s "$2" ]]; then
    curl -fL --retry 3 "$1" -o "$2.part"
    mv "$2.part" "$2"
  fi
}
fetch "$base/v1.12.30/sherpa-onnx-1.12.30.aar" "$libs/sherpa.aar"
echo "0a44f3aa516c31eb74cef0ea6bbacf76095434bf6c89be728226c7ecf550232f  $libs/sherpa.aar" | sha256sum -c -
giga=sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19
seg=sherpa-onnx-pyannote-segmentation-3-0
archive_model() {
  fetch "$base/$1/$2.tar.bz2" "$tmp/$2.tar.bz2"
  tar -xjf "$tmp/$2.tar.bz2" -C "$tmp" "$2/$3"
  mv "$tmp/$2/$3" "$assets/$4"
}
if [[ ! -s "$assets/giga.onnx" || ! -s "$assets/tokens.txt" ]]; then
  archive_model asr-models "$giga" model.int8.onnx giga.onnx
  tar -xOf "$tmp/$giga.tar.bz2" "$giga/tokens.txt" > "$assets/tokens.txt"
fi
[[ -s "$assets/segment.onnx" ]] || archive_model speaker-segmentation-models "$seg" model.int8.onnx segment.onnx
fetch "$base/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx" "$assets/voice.onnx"
fetch "$base/asr-models/silero_vad.onnx" "$assets/vad.onnx"
fetch 'https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task' "$assets/qwen.task"
(cd "$assets" && sha256sum -c ../../../../../../config/models.sha256)
cp config/models.sha256 "$assets/SHA256SUMS"
