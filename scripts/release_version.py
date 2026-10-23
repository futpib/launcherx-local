#!/usr/bin/env python3
"""Choose an increasing Android version from all published release tags."""
import argparse
import datetime
import re

from build import ROOT, manifest_version


def next_version(tags, minimum, now=None):
    codes = [int(match[1]) for tag in tags
             if (match := re.fullmatch(r"[^\s+]+\+([0-9]+)", tag.strip()))]
    code = max([minimum, *codes]) + 1
    if not 1 <= code <= 2100000000:
        raise ValueError("Android version code exhausted")
    now = now or datetime.datetime.now(datetime.timezone.utc)
    name = now.astimezone(datetime.timezone.utc).strftime("%y.%m.%d.%H.%M")
    return {"name": name, "code": code, "tag": f"{name}+{code}"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tags", type=argparse.FileType("r"))
    parser.add_argument("--output", required=True, type=argparse.FileType("a"))
    args = parser.parse_args()
    _, minimum = manifest_version(ROOT / "module/AndroidManifest.xml")
    for key, value in next_version(args.tags.read().splitlines(), minimum).items():
        args.output.write(f"{key}={value}\n")


if __name__ == "__main__":
    main()
