#!/usr/bin/env python3
"""ADB helpers locked to this project's emulator, never the physical TV."""
import argparse
import os
from pathlib import Path
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / "artifacts"
SDK = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
SERIAL = "127.0.0.1:5587"
SERVER_PORT = "5038"


def adb(*args, timeout=30, binary=False):
    result = subprocess.run([str(SDK / "platform-tools/adb"), "-P", SERVER_PORT,
                             "-s", SERIAL, *map(str, args)],
                            capture_output=True, text=not binary,
                            errors=None if binary else 'replace', timeout=timeout)
    if result.returncode:
        raise RuntimeError((args, result.returncode, result.stdout, result.stderr))
    return result.stdout if binary else result.stdout.strip()


def guard():
    if adb("shell", "getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Refusing to operate on a non-emulator")
    name = adb("shell", "getprop", "ro.boot.qemu.avd_name")
    if name != "launcherx_local14":
        raise RuntimeError(f"Unexpected AVD: {name}")


def shell(command):
    guard()
    return adb("shell", command)


def root(command):
    return shell("/debug_ramdisk/su -c " + shlex.quote(command))


def wait_boot(timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if adb("shell", "getprop", "sys.boot_completed", timeout=5) == "1":
                guard()
                print("Emulator boot completed", flush=True)
                return
        except (RuntimeError, subprocess.TimeoutExpired):
            try:
                adb("reconnect", timeout=5)
                adb("connect", SERIAL, timeout=5)
            except (RuntimeError, subprocess.TimeoutExpired):
                pass
        time.sleep(2)
    raise TimeoutError("Emulator boot did not complete")


def snapshot(name):
    guard()
    ARTIFACTS.mkdir(exist_ok=True)
    if not name.replace("-", "").replace("_", "").isalnum():
        raise ValueError("Invalid artifact name")
    remote = "/data/local/tmp/launcherx-local-ui.xml"
    try:
        output = adb("shell", "uiautomator dump " + remote, timeout=20)
        if "UI hierchary dumped" not in output and "UI hierarchy dumped" not in output:
            raise RuntimeError("UI dump failed: " + output)
        xml = adb("shell", "cat " + remote)
        (ARTIFACTS / (name + ".xml")).write_text(xml)
        for node in ET.fromstring(xml).iter("node"):
            text = node.get("text") or node.get("content-desc")
            if text:
                print(text[:250], node.get("bounds"))
    finally:
        (ARTIFACTS / (name + ".png")).write_bytes(adb("exec-out", "screencap -p", binary=True))
        adb("shell", "rm -f " + remote)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["shell", "root", "snapshot", "tap", "wait_boot"])
    parser.add_argument("args", nargs="*")
    args = parser.parse_args()
    if args.action == "wait_boot":
        wait_boot()
    elif args.action == "snapshot":
        snapshot(args.args[0])
    elif args.action == "tap":
        x, y = map(int, args.args[:2])
        shell(f"input tap {x} {y}")
        time.sleep(1)
        if len(args.args) > 2:
            snapshot(args.args[2])
    else:
        print(globals()[args.action](args.args[0]))
