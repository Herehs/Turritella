#!/usr/bin/env python3
"""Version metadata shared by tagged releases and local checks."""
import re
import sys


def release_version(tag: str) -> tuple[str, int]:
    match = re.fullmatch(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:\.(0|[1-9][0-9]*))?", tag)
    if not match:
        raise ValueError("Ожидается тег v1.0 или v1.0.1 без ведущих нулей")
    major, minor, patch = (int(value or 0) for value in match.groups())
    code = major * 1_000_000 + minor * 1_000 + patch
    if minor > 999 or patch > 999 or not 1 <= code <= 2_100_000_000:
        raise ValueError("Версия выходит за пределы versionCode Android")
    return tag[1:], code


if __name__ == "__main__":
    try:
        name, code = release_version(sys.argv[1])
    except (IndexError, ValueError) as error:
        sys.exit(str(error))
    print(f"version_name={name}")
    print(f"version_code={code}")
