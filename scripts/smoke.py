#!/usr/bin/env python3
"""Exercise the real launcher and TV Bro with DPAD input in the isolated AVD."""
import argparse
import hashlib
import json
import re
import time
import xml.etree.ElementTree as ET

from lab import ARTIFACTS, ROOT, adb, guard, root, shell, snapshot, wait_boot

TARGET = "com.google.android.apps.tv.launcherx"
HOME = TARGET + "/.home.HomeActivity"
BROWSER = "com.phlox.tvwebbrowser"


def screen(name):
    (ARTIFACTS / (name + ".png")).write_bytes(adb("exec-out", "screencap -p", binary=True))


def resumed(package):
    state = shell("dumpsys activity activities")
    top = re.search(r"topResumedActivity=.*", state)
    if not top or package not in top.group():
        raise AssertionError("Unexpected foreground: " + (top.group() if top else state[:1000]))
    return state


def fresh_home():
    root("am force-stop " + TARGET)
    root("am start -W -n " + HOME)
    time.sleep(3)
    resumed(HOME)


def check_home(name, timeout=30):
    deadline = time.monotonic() + timeout
    while True:
        try:
            snapshot(name)
            xml = (ARTIFACTS / (name + ".xml")).read_text()
            if "Your apps" in xml:
                break
        except RuntimeError:
            if time.monotonic() >= deadline:
                raise
        if time.monotonic() >= deadline:
            raise AssertionError("Home did not finish rendering")
        time.sleep(2)
    for expected in ["Your apps", "TV Bro", "Local TV"]:
        assert expected in xml, expected
    for unexpected in ["Loading error", "Unable to load Home", "Set up Google TV", "UNHANDLED ACCOUNT"]:
        assert unexpected not in xml, unexpected
    accounts = shell("dumpsys account")
    assert "Accounts: 0" in accounts
    (ARTIFACTS / (name + "-accounts.txt")).write_text(accounts)
    nodes = list(ET.fromstring(xml).iter("node"))
    apps = [n.get("content-desc") for n in nodes
            if n.get("class") == "android.widget.FrameLayout"
            and n.get("clickable") == "true" and n.get("content-desc")]
    assert "TV Bro" in apps, apps
    return apps


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reboot", action="store_true")
    args = parser.parse_args()
    guard()
    ARTIFACTS.mkdir(exist_ok=True)
    apk = ROOT / "build/apps/launcherx-local.apk"
    print(adb("install", "--no-incremental", "-r", apk), flush=True)
    time.sleep(2)
    root("pm enable " + HOME)
    root("am force-stop " + BROWSER)
    adb("logcat", "-c")
    fresh_home()
    apps = check_home("smoke-home")

    # UIAutomator attaches accessibility services and resets this launcher's focus.
    # Restart after inspection; keep the complete remote sequence free of UI dumps.
    fresh_home()
    shell("input keyevent KEYCODE_DPAD_DOWN")
    time.sleep(.3)
    screen("smoke-native-focus")
    for _ in range(apps.index("TV Bro")):
        shell("input keyevent KEYCODE_DPAD_RIGHT")
        time.sleep(.15)
    screen("smoke-browser-focus")
    shell("input keyevent KEYCODE_DPAD_CENTER")
    time.sleep(2)
    state = resumed(BROWSER)
    assert "launchedFromPackage=" + TARGET in state
    (ARTIFACTS / "smoke-browser-activity.txt").write_text(state)
    screen("smoke-browser")
    shell("input keyevent KEYCODE_HOME")
    time.sleep(2)
    resumed(HOME)
    check_home("smoke-return-home")
    fresh_home()
    check_home("smoke-restarted")

    if args.reboot:
        adb("reboot")
        time.sleep(10)
        wait_boot()
        time.sleep(3)
        resumed(HOME)
        check_home("smoke-rebooted")

    logs = adb("logcat", "-d")
    (ARTIFACTS / "smoke-logcat.txt").write_text(logs)
    hook_logs = "\n".join(line for line in logs.splitlines() if "LauncherXLocal" in line)
    assert "installed mode=apps version=702401" in hook_logs
    assert "rendering official local apps channel" in hook_logs
    assert "Patch refused" not in hook_logs
    assert "EMULATOR FIXTURE" not in hook_logs
    assert "FATAL EXCEPTION" not in adb("logcat", "-d", "-s", "AndroidRuntime:E")
    receipt = {"result": "passed", "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
               "accounts": 0, "apps": apps, "reboot": args.reboot,
               "checks": ["clean official Home", "native DPAD app launch", "Home return",
                          "process restart", "no launcher fatal exception"],
               "fixture": "Google TV API 34 x86 with ARM translation; original TV APK"}
    (ARTIFACTS / "smoke-result.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2), flush=True)


if __name__ == "__main__":
    main()
