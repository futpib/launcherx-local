#!/usr/bin/env python3
"""Exercise setup, restoration, and incompatible-build refusal in the isolated AVD."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

from lab import ARTIFACTS, ROOT, adb, guard, shell, snapshot
from smoke import check_home, resumed, HOME

SETUP = "local.launcherx.patch/.SetupActivity"


def open_setup(name):
    shell("am force-stop local.launcherx.patch")
    shell("am start -W -n " + SETUP)
    time.sleep(1)
    snapshot(name)
    return (ARTIFACTS / (name + ".xml")).read_text()


def choose(index, label, name):
    # Use only remote keys to select a setup action, including after touch-mode entry.
    shell("input keyevent " + " ".join(["KEYCODE_DPAD_UP"] * 5))
    for _ in range(index):
        shell("input keyevent KEYCODE_DPAD_DOWN")
    snapshot(name)
    nodes = ET.parse(ARTIFACTS / (name + ".xml")).iter("node")
    focused = [n.get("text") for n in nodes if n.get("focused") == "true"]
    assert focused == [label], focused
    shell("input keyevent KEYCODE_DPAD_CENTER")
    time.sleep(3)


def main():
    guard()
    apk = ROOT / "build/apps/launcherx-local.apk"
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    open_setup("setup-roundtrip-start")
    choose(1, "Restore Basic TV", "setup-restore-focus")
    resumed("com.google.android.apps.tv.launcherx/.home.VanillaModeHomeActivity")
    snapshot("setup-restored-basic")
    assert "Set up Google TV" in (ARTIFACTS / "setup-restored-basic.xml").read_text()
    results = []
    try:
        for kind, expected in [("version", "Unsupported launcher version"), ("dex", "Code mismatch: classes.dex")]:
            with tempfile.TemporaryDirectory(prefix="launcherx-negative-") as directory:
                fixture = Path(directory)
                shutil.copytree(ROOT / "module", fixture / "module")
                (fixture / "scripts").mkdir()
                shutil.copy2(ROOT / "scripts/build.py", fixture / "scripts/build.py")
                (fixture / "artifacts").mkdir()
                shutil.copy2(ROOT / "artifacts/xposed-api-82.jar", fixture / "artifacts/xposed-api-82.jar")
                (fixture / ".cache").mkdir()
                shutil.copy2(ROOT / ".cache/local.keystore", fixture / ".cache/local.keystore")
                config = json.loads((ROOT / "compatibility.json").read_text())
                if kind == "version":
                    config["version_code"] += 1
                else:
                    config["dex_sha256"][0][1] = "0" * 64
                (fixture / "compatibility.json").write_text(json.dumps(config))
                subprocess.run(["python3", str(fixture / "scripts/build.py")], check=True)
                guard()
                print(adb("install", "--no-incremental", "-r", fixture / "build/apps/launcherx-local.apk"))
                xml = open_setup("setup-refuse-" + kind)
                assert expected in xml, xml
                choose(0, "Enable local Home", "setup-refuse-" + kind + "-focus")
                snapshot("setup-refuse-" + kind + "-result")
                assert expected in (ARTIFACTS / ("setup-refuse-" + kind + "-result.xml")).read_text()
                state = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN "
                              "-c android.intent.category.HOME -p com.google.android.apps.tv.launcherx")
                assert "VanillaModeHomeActivity" in state, state
                results.append(kind + " mismatch refused before switching Home")
    finally:
        guard()
        print(adb("install", "--no-incremental", "-r", apk))
    open_setup("setup-compatible-again")
    choose(0, "Enable local Home", "setup-enable-focus")
    resumed(HOME)
    check_home("setup-roundtrip-home")
    result = {"result": "passed", "apk_sha256": digest,
              "checks": ["DPAD setup controls", "Restore Basic TV", *results, "Enable local Home"],
              "fixture": "isolated emulator; negative variants alter module compatibility metadata only"}
    (ARTIFACTS / "setup-result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
