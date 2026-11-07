"""DER для произвольных имён говорящих. Перекрытия учитываются, collar = 0."""
import argparse
import json
from collections import Counter
from pathlib import Path
from scipy.optimize import linear_sum_assignment


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))["transcript"]


def score(ref, hyp):
    marks = sorted({r[k] for r in ref + hyp for k in ("start_ms", "end_ms")})
    spans = []
    overlap = Counter()
    for start, end in zip(marks, marks[1:]):
        def active(rows):
            return {r["speaker_id"] for r in rows if r["start_ms"] <= start < r["end_ms"]}
        a, b = active(ref), active(hyp)
        spans.append((end - start, a, b))
        for x in a:
            for y in b:
                overlap[x, y] += end - start
    refs = sorted({r["speaker_id"] for r in ref})
    hyps = sorted({r["speaker_id"] for r in hyp})
    mapping = {}
    if refs and hyps:
        matrix = [[overlap[a, b] for b in hyps] for a in refs]
        ii, jj = linear_sum_assignment(matrix, maximize=True)
        mapping = {hyps[j]: refs[i] for i, j in zip(ii, jj)}
    denominator = sum(ms * len(a) for ms, a, b in spans)
    if not denominator:
        raise ValueError("Эталон не содержит речи")
    errors = sum(ms * (max(len(a), len(b)) - sum(mapping.get(y) in a for y in b)) for ms, a, b in spans)
    return errors / denominator


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("reference")
    p.add_argument("hypothesis")
    args = p.parse_args()
    print(f"DER {score(read(args.reference), read(args.hypothesis)):.2%}")
