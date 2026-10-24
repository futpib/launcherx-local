#!/usr/bin/env python3
"""Exercise native remote receivers, shortcut UI and storage in the rooted AVD."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import time

from build_publisher import build
from lab import ARTIFACTS, ROOT, adb, guard, root, shell
from profile_smoke import inspect, zero_accounts
from smoke import BROWSER, HOME, TARGET, fresh_home, resumed, screen

CHOOSER = TARGET + '/.magicbutton.ui.MagicAppSelectionActivity'
STORE = '/data/user/0/' + TARGET + '/files/remote_button_configuration'
FIXTURE = '/data/local/tmp/launcherx-remote.apk'


def send(*arguments):
    root('CLASSPATH=' + FIXTURE + ' app_process /system/bin local.launcherx.remote.SendKey '
         + ' '.join(shlex.quote(str(arg)) for arg in arguments))
    time.sleep(1)


def foreground(name, component):
    deadline = time.monotonic() + 20
    while True:
        try:
            state = resumed(component)
            break
        except AssertionError:
            if time.monotonic() >= deadline:
                raise
            time.sleep(.3)
    screen(name)
    (ARTIFACTS / (name + '-activity.txt')).write_text(state)
    return state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-apk', type=Path,
                        help='Same-key, same-version build of the unpatched module')
    args = parser.parse_args()
    guard()
    zero_accounts()
    assert shell('getprop ro.product.first_api_level') == '34'
    assert shell('pm path ' + BROWSER), 'Install the lab TV Bro APK first'
    apk = ROOT / 'build/apps/launcherx-local.apk'
    fixture = build('remote')
    backup = '/data/local/tmp/launcherx-remote-backup-' + str(time.time_ns())
    root('am force-stop ' + TARGET)
    existed = root('test -d ' + STORE + ' && echo yes || echo no') == 'yes'
    root('mkdir ' + backup)
    if existed:
        root('cp -a ' + STORE + ' ' + backup + '/store')
    checks = []
    try:
        root('rm -rf ' + STORE)
        adb('push', fixture, FIXTURE)
        root('chmod 444 ' + FIXTURE)
        if args.baseline_apk:
            print(adb('install', '--no-incremental', '-r', args.baseline_apk), flush=True)
            fresh_home()
            send(313, 'long')
            foreground('remote-baseline-long', HOME)
            send(313, 'short')
            foreground('remote-baseline-short', HOME)
            adb('logcat', '-c')
            send('app', BROWSER)
            foreground('remote-baseline-app', HOME)
            logs = adb('logcat', '-d')
            (ARTIFACTS / 'remote-baseline-app-logcat.txt').write_text(logs)
            assert 'Blocking launch since internet is available' in logs
            checks.append('Unpatched module reproduces both Home redirects and the internet app-launch block')

        print(adb('install', '--no-incremental', '-r', apk), flush=True)
        adb('logcat', '-c')
        fresh_home()
        send(313, 'short')
        foreground('remote-unassigned', CHOOSER)
        inspect('remote-unassigned', ['Create a shortcut', 'Favorite app'])
        shell('input keyevent KEYCODE_BACK')
        foreground('remote-cancelled', HOME)
        checks.append('Unassigned star short press opens the native chooser; cancel returns Home')

        send(313, 'long')
        foreground('remote-long', CHOOSER)
        inspect('remote-long', ['Create a shortcut', 'Favorite app'])
        shell('input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER')
        inspect('remote-app-list', ['Create a shortcut for your favorite app'])
        # Only the focused app gets a text label beneath its banner.
        for index in range(12):
            text = inspect('remote-app-focus-' + str(index), ['Create a shortcut'])
            if re.search(r'^TV Bro\s*$', text, re.MULTILINE):
                break
            shell('input keyevent KEYCODE_DPAD_RIGHT')
        else:
            raise AssertionError('Could not focus TV Bro in native shortcut picker')
        shell('input keyevent KEYCODE_DPAD_CENTER KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER')
        foreground('remote-saved', HOME)
        inspect('remote-saved', ['Your apps'])
        saved = adb('exec-out', '/debug_ramdisk/su -c '
                    + shlex.quote('cat ' + STORE + '/data/*/content'), binary=True)
        assert BROWSER.encode() in saved
        send(313, 'short')
        state = foreground('remote-browser', BROWSER)
        assert 'launchedFromPackage=' + TARGET in state
        checks.append('Native DPAD chooser saves TV Bro; short press launches it with internet available')

        send(313, 'long')
        foreground('remote-edit-from-app', CHOOSER)
        inspect('remote-edit-from-app', ['Create a shortcut', 'Favorite app'])
        shell('input keyevent KEYCODE_BACK')
        fresh_home()
        send(313, 'short')
        foreground('remote-persisted', BROWSER)
        checks.append('Long press edits while another app is foreground; saved action survives process restart')

        fresh_home()
        send(190, 'long')
        foreground('remote-youtube-picker', CHOOSER)
        inspect('remote-youtube-picker', ['Create a shortcut', 'Restore YouTube default'])
        shell('input keyevent KEYCODE_BACK')
        send(190, 'short')
        foreground('remote-youtube', 'com.google.android.youtube.tv/')
        checks.append('YouTube long press opens the unrestricted native chooser; unassigned short press retains YouTube')

        fresh_home()
        send('app', BROWSER)
        foreground('remote-broker', BROWSER)
        checks.append('OEM-style GlobalButtonLaunch receiver launches a local app account-free')
        fresh_home()
        send('app', BROWSER, 'com.google.android.youtube.tv')
        foreground('remote-invalid-package', HOME)
        checks.append('Native launchIntent/package mismatch rejection remains intact')

        send(314, 'short')
        foreground('remote-unregistered-macro', HOME)
        zero_accounts()
        logs = adb('logcat', '-d')
        (ARTIFACTS / 'remote-final-logcat.txt').write_text(logs)
        assert 'process=' + TARGET + ':coreservices' in logs
        assert 'Incognito local remote action: button' in logs
        assert 'Incognito local remote action: app launch' in logs
        assert 'launchIntent\'s package is not' in logs
        assert 'Patch refused' not in logs
        crashes = adb('logcat', '-d', '-s', 'AndroidRuntime:E')
        (ARTIFACTS / 'remote-crashes.txt').write_text(crashes)
        assert 'FATAL EXCEPTION' not in crashes
        checks.append('No crashes or new accounts; unregistered MACRO_2 remains unhandled')
    finally:
        print(adb('install', '--no-incremental', '-r', apk), flush=True)
        root('am force-stop ' + TARGET)
        root('rm -rf ' + STORE)
        if existed:
            root('cp -a ' + backup + '/store ' + STORE)
            root('restorecon -RF ' + STORE)
        root('rm -rf ' + backup + ' ' + FIXTURE)
        fresh_home()
    checks.append('Restored the original shortcut store and removed the receiver fixture')
    report = {'result': 'passed', 'module_version': '0.8',
              'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
              'physical_tv_tested': False, 'accounts': 0, 'checks': checks,
              'method': 'Root sends KeyEvent parcelables through the real stock broadcast receivers; DPAD, screenshots/OCR, ActivityManager and native saved protobuf storage verify UI and launches.',
              'limitations': ['Receiver injection does not test physical keylayout, Bluetooth, standby or wake.',
                              'No real Google account, child profile or profile lock was exercised.',
                              'Google Home, device-input hardware and Freeplay were not unlocked or verified.']}
    (ROOT / 'validation-remote.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
