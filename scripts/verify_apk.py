#!/usr/bin/env python3
"""Check the real signed APK against its receipt and publication constraints."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

from build import ROOT, compatibility_versions


def verify(directory):
    apk = directory / "launcherx-local.apk"
    receipt = json.loads((directory / "receipt.json").read_text())
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    assert receipt["mode"] == "apps" and receipt["emulator_network"] is False, receipt
    assert receipt["sha256"] == digest
    versions = compatibility_versions(json.loads((ROOT / "compatibility.json").read_text()))
    assert receipt["launcher_profiles"] == [
        {key: version[key] for key in ("version_code", "version_name", "dex_sha256")}
        for version in versions]
    assert (directory / "SHA256SUMS").read_text() == f"{digest}  {apk.name}\n"
    sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
    tools = sdk / "build-tools/37.0.0"
    subprocess.run([tools / "apksigner", "verify", "--verbose", apk], check=True)
    badging = subprocess.check_output([tools / "aapt", "dump", "badging", apk], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    assert package and package[1] == "local.launcherx.patch", badging
    assert int(package[2]) == receipt["version_code"] and package[3] == receipt["version_name"]
    with zipfile.ZipFile(apk) as archive:
        assert archive.read("assets/xposed_init").decode().strip() == "local.launcherx.patch.LauncherPatch"
        assert not any(name.endswith((".apk", ".jar", ".keystore", ".jks")) for name in archive.namelist())
        assert not any(name.startswith("lib/") for name in archive.namelist()), "Expected a universal Java-only module"
        dex = archive.read("classes.dex")
        # DEX class_defs identify definitions, unlike strings/type references to the runtime API.
        string_count, string_offset = struct.unpack_from("<II", dex, 56)
        type_count, type_offset = struct.unpack_from("<II", dex, 64)
        class_count, class_offset = struct.unpack_from("<II", dex, 96)
        strings = []
        for index in range(string_count):
            position = struct.unpack_from("<I", dex, string_offset + 4 * index)[0]
            while dex[position] & 128:
                position += 1
            position += 1
            strings.append(dex[position:dex.index(b"\0", position)])
        types = [strings[struct.unpack_from("<I", dex, type_offset + 4 * index)[0]] for index in range(type_count)]
        classes = [types[struct.unpack_from("<I", dex, class_offset + 32 * index)[0]] for index in range(class_count)]
        assert classes and all(name.startswith(b"Llocal/launcherx/patch/")
                               or name == b"Lcom/android/tools/r8/annotations/LambdaMethod;"
                               for name in classes), classes
    print(f"Verified {apk.name}: {receipt['version_name']} ({receipt['version_code']}), {digest}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", nargs="?", type=Path, default=ROOT / "build/apps")
    verify(parser.parse_args().directory)
