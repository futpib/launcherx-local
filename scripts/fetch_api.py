#!/usr/bin/env python3
"""Download the compile-only Xposed dependency, accepting only its pinned digest."""
import hashlib
from pathlib import Path
import urllib.request

from build import API_SHA256, ROOT

URL = "https://maven.aliyun.com/repository/public/de/robv/android/xposed/api/82/api-82.jar"


def fetch(destination=ROOT / "artifacts/xposed-api-82.jar"):
    destination = Path(destination)
    if destination.is_file() and hashlib.sha256(destination.read_bytes()).hexdigest() == API_SHA256:
        return destination
    with urllib.request.urlopen(URL, timeout=60) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != API_SHA256:
        raise SystemExit("Downloaded Xposed API does not match its pinned SHA-256")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(".tmp")
    temporary.write_bytes(data)
    temporary.replace(destination)
    return destination


if __name__ == "__main__":
    print(fetch())
