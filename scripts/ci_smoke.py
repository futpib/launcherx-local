#!/usr/bin/env python3
"""Exercise setup refusal on a stock emulator without private launcher code or root."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

from build import ROOT, run

TARGET = 'com.google.android.apps.tv.launcherx'
MODULE = 'local.launcherx.patch'
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
ARTIFACTS = ROOT / 'artifacts/ci-smoke'


class Device:
    def __init__(self, serial, port):
        self.command = [str(SDK / 'platform-tools/adb'), '-P', str(port), '-s', serial]

    def raw(self, *args, binary=False):
        result = subprocess.run([*self.command, *map(str, args)], capture_output=True,
                                text=not binary, timeout=40, check=True)
        return result.stdout if binary else result.stdout.strip()

    def adb(self, *args, binary=False):
        if self.raw('shell', 'getprop ro.kernel.qemu') != '1':
            raise RuntimeError('Refusing a non-emulator device')
        if self.raw('shell', 'getprop ro.boot.qemu.avd_name') != 'launcherx_ci':
            raise RuntimeError('Refusing any AVD except the disposable launcherx_ci fixture')
        return self.raw(*args, binary=binary)

    def shell(self, command):
        return self.adb('shell', command)

    def home(self):
        return self.shell('cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')

    def inspect(self, name, expected):
        deadline = time.monotonic() + 35
        while True:
            self.shell('uiautomator dump /data/local/tmp/launcherx-ci.xml')
            xml = self.shell('cat /data/local/tmp/launcherx-ci.xml')
            (ARTIFACTS / (name + '.xml')).write_text(xml)
            (ARTIFACTS / (name + '.png')).write_bytes(self.adb('exec-out', 'screencap -p', binary=True))
            if expected in xml:
                return xml
            if time.monotonic() >= deadline:
                raise AssertionError((name, expected, xml))
            time.sleep(1)

    def choose(self, index, label, name, expected):
        self.shell('input keyevent ' + ' '.join(['KEYCODE_DPAD_UP'] * 8 + ['KEYCODE_DPAD_DOWN'] * index))
        xml = self.inspect(name + '-focus', label)
        focused = [node.get('text') for node in ET.fromstring(xml).iter('node') if node.get('focused') == 'true']
        assert focused == [label], focused
        self.shell('input keyevent KEYCODE_DPAD_CENTER')
        self.inspect(name, expected)


def fixture(directory, code):
    # This deliberately incompatible, code-free package only exercises compatibility rejection.
    # It does not implement or simulate any launcher behavior.
    manifest = directory / 'AndroidManifest.xml'
    manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                        f'package="{TARGET}" android:versionCode="{code}" android:versionName="ci-negative">'
                        '<uses-sdk android:minSdkVersion="29" android:targetSdkVersion="34" />'
                        '<application android:hasCode="false" android:label="Incompatible CI fixture" />'
                        '</manifest>')
    tools = SDK / 'build-tools/37.0.0'
    run(tools / 'aapt', 'package', '-f', '-M', manifest, '-I', SDK / 'platforms/android-34/android.jar',
        '-F', directory / 'unsigned.apk')
    run(tools / 'zipalign', '-f', '4', directory / 'unsigned.apk', directory / 'aligned.apk')
    output = directory / 'negative.apk'
    run(tools / 'apksigner', 'sign', '--ks', ROOT / '.cache/local.keystore', '--ks-key-alias', 'local',
        '--ks-pass', 'pass:android', '--out', output, directory / 'aligned.apk')
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--server-port', type=int, default=5037)
    args = parser.parse_args()
    device = Device(args.serial, args.server_port)
    # Never replace a genuine launcher or an existing module installation.
    for package in (TARGET, MODULE):
        assert not device.shell('pm list packages ' + package), 'CI requires a clean disposable emulator'
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    apk = ROOT / 'build/apps/launcherx-local.apk'
    home = device.home()
    installed = []
    try:
        device.adb('install', '--no-incremental', apk)
        installed.append(MODULE)
        device.adb('logcat', '-c')
        expected_code = json.loads((ROOT / 'compatibility.json').read_text())['version_code']
        cases = [('missing', None, TARGET), ('version', 1, 'Unsupported launcher version 1'),
                 ('dex', expected_code, 'Unexpected executable code entries')]
        for name, code, expected in cases:
            if code is not None:
                with tempfile.TemporaryDirectory(prefix='launcherx-ci-negative-') as directory:
                    device.adb('install', '--no-incremental', '-r', fixture(Path(directory), code))
                if TARGET not in installed:
                    installed.append(TARGET)
            device.shell('am force-stop ' + MODULE)
            device.shell('am start -W -n ' + MODULE + '/.SetupActivity')
            device.inspect(name + '-initial', 'Cannot enable local Home: ' + expected)
            device.choose(0, 'Enable local Home', name + '-enable', 'No switch completed: ' + expected)
            device.choose(4, 'Choose app content', name + '-channels', 'Cannot change app content: ' + expected)
            assert device.home() == home, 'Setup changed the default Home on an incompatible installation'
            print(name + ': compatibility, Enable and channel chooser refused; Home unchanged', flush=True)
        logs = device.adb('logcat', '-d', '-s', 'AndroidRuntime:E')
        assert 'FATAL EXCEPTION' not in logs, logs
        result = {'result': 'passed', 'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                  'checks': ['missing launcher refusal', 'wrong version refusal', 'wrong executable refusal',
                             'DPAD Enable and channel chooser refuse without switching Home', 'no fatal exception'],
                  'coverage': 'stock Android setup safety only; no Xposed, root, real launcher or Home integration'}
        (ARTIFACTS / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result, indent=2), flush=True)
    finally:
        (ARTIFACTS / 'logcat.txt').write_text(device.adb('logcat', '-d'))
        for package in reversed(installed):
            device.adb('uninstall', package)
        device.shell('rm -f /data/local/tmp/launcherx-ci.xml')
        device.shell('input keyevent KEYCODE_HOME')
        assert device.home() == home


if __name__ == '__main__':
    main()
