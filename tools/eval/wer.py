"""python wer.py reference.json hypothesis.json"""
import argparse
import json
import re
from pathlib import Path
from jiwer import wer


def text(path):
    rows = json.loads(Path(path).read_text(encoding="utf-8"))["transcript"]
    words = " ".join(row["text"] for row in rows).casefold().replace("ё", "е")
    return " ".join(re.findall(r"[^\W_]+", words))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="WER без пунктуации, с сохранением чисел")
    parser.add_argument("reference")
    parser.add_argument("hypothesis")
    args = parser.parse_args()
    print(f"WER {wer(text(args.reference), text(args.hypothesis)):.2%}")
