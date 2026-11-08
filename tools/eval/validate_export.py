"""Проверка JSON-контракта жюри, без зависимостей: python validate_export.py file.json."""
import argparse
import json
import re
from pathlib import Path


def validate(document, duration_ms=None):
    if not isinstance(document, dict) or set(document) != {"transcript"}:
        raise ValueError("В корне должен быть только ключ transcript")
    rows = document["transcript"]
    if not isinstance(rows, list) or not rows:
        raise ValueError("transcript должен быть непустым массивом реплик")
    if duration_ms is not None and (type(duration_ms) is not int or duration_ms <= 0):
        raise ValueError("Длительность должна быть положительным целым числом миллисекунд")
    speakers = set()
    previous_start = -1
    for index, row in enumerate(rows, 1):
        prefix = f"Реплика {index}: "
        if not isinstance(row, dict) or set(row) != {"speaker_id", "start_ms", "end_ms", "text"}:
            raise ValueError(prefix + "неверный набор полей")
        speaker = row["speaker_id"]
        if not isinstance(speaker, str) or not re.fullmatch(r"speaker_[1-9][0-9]*", speaker):
            raise ValueError(prefix + "неверный speaker_id")
        if speaker not in speakers:
            if speaker != f"speaker_{len(speakers)+1}":
                raise ValueError(prefix + "голоса должны нумероваться по первому появлению")
            speakers.add(speaker)
        start, end = row["start_ms"], row["end_ms"]
        if type(start) is not int or type(end) is not int or not 0 <= start < end:
            raise ValueError(prefix + "нужны целые метки 0 ≤ start_ms < end_ms")
        if start < previous_start:
            raise ValueError(prefix + "нарушен хронологический порядок")
        if duration_ms is not None and end > duration_ms:
            raise ValueError(prefix + "реплика выходит за длительность аудио")
        if not isinstance(row["text"], str) or not row["text"].strip():
            raise ValueError(prefix + "пустой текст")
        previous_start = start
    return len(rows), len(speakers)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path)
    parser.add_argument("--duration-ms", type=int)
    args = parser.parse_args()
    try:
        rows, speakers = validate(json.loads(args.file.read_text(encoding="utf-8")), args.duration_ms)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Ошибка: {error}\n")
    print(f"OK: реплик {rows}, участников {speakers}")
