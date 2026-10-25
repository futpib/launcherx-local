#!/usr/bin/env python3
"""Test independent native bindings and restoration for the six remote buttons."""
import hashlib
import json
import re
import shlex
import subprocess
import time
from PIL import Image

from build_publisher import build
from lab import ARTIFACTS, ROOT, adb, guard, root, shell
from profile_smoke import inspect, zero_accounts
from remote_smoke import CHOOSER, FIXTURE, STORE, foreground, send as send_native
from smoke import BROWSER, HOME, TARGET, fresh_home, screen

KEYS = [(313, 'Star'), (190, 'YouTube'), (193, 'Netflix'),
        (194, 'Prime Video'), (351, 'Live TV'), (352, 'Xiaomi TV+')]


def send(code, duration):
    send_native(code, duration, *(['bridge'] if code in [193, 194, 351, 352] else []))


def choose_app(code, label, app_label='TV Bro'):
    send(code, 'long')
    foreground('buttons-' + str(code) + '-chooser', CHOOSER)
    text = inspect('buttons-' + str(code) + '-chooser', ['Create a shortcut',
                                                      'Restore ' + label + ' default'])
    if 'Favorite app' in text:
        shell('input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER')
    for index in range(25):
        text = inspect('buttons-' + str(code) + '-app-' + str(index), ['Create a shortcut'])
        if re.search(r'^' + re.escape(app_label) + r'\s*$', text, re.MULTILINE):
            break
        shell('input keyevent KEYCODE_DPAD_RIGHT')
    else:
        raise AssertionError('Could not focus ' + app_label)
    shell('input keyevent KEYCODE_DPAD_CENTER KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER')
    foreground('buttons-' + str(code) + '-saved', HOME)


def restore(code, label, all_buttons=False, cancel=False):
    fresh_home()
    send(code, 'long')
    foreground('buttons-restore-chooser', CHOOSER)
    shell('input keyevent KEYCODE_DPAD_UP KEYCODE_DPAD_LEFT')
    if all_buttons:
        shell('input keyevent KEYCODE_DPAD_RIGHT')
    shell('input keyevent KEYCODE_DPAD_CENTER')
    # Full-screen thresholding can omit the gray dialog over the dimmed chooser.
    # Keep the original screenshot and read its visible central dialog separately.
    expected = 'Restore all button defaults?' if all_buttons else 'Restore ' + label + ' default?'
    deadline = time.monotonic() + 25
    while True:
        screen('buttons-restore-confirm')
        source = Image.open(ARTIFACTS / 'buttons-restore-confirm.png')
        width, height = source.size
        cropped = ARTIFACTS / 'buttons-restore-confirm-dialog.png'
        source.crop((int(width * .24), int(height * .35),
                     int(width * .76), int(height * .65))).save(cropped)
        text = subprocess.check_output(['tesseract', str(cropped), 'stdout', '--psm', '6'],
                                       text=True, stderr=subprocess.DEVNULL)
        (ARTIFACTS / 'buttons-restore-confirm-dialog.txt').write_text(text)
        if expected.casefold() in text.casefold():
            break
        assert time.monotonic() < deadline, (expected, text)
        time.sleep(.5)
    if cancel:
        shell('input keyevent KEYCODE_BACK')
        time.sleep(.5)
        shell('input keyevent KEYCODE_BACK')
    else:
        shell('input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER')
    foreground('buttons-restore-finished', HOME)


def main():
    guard()
    zero_accounts()
    apk = ROOT / 'build/apps/launcherx-local.apk'
    backup = '/data/local/tmp/launcherx-buttons-backup-' + str(time.time_ns())
    root('am force-stop ' + TARGET)
    existed = root('test -d ' + STORE + ' && echo yes || echo no') == 'yes'
    root('mkdir ' + backup)
    if existed:
        root('cp -a ' + STORE + ' ' + backup + '/store')
    checks = []
    try:
        root('rm -rf ' + STORE)
        adb('push', build('remote'), FIXTURE)
        root('chmod 444 ' + FIXTURE)
        print(adb('install', '--no-incremental', '-r', apk), flush=True)
        adb('logcat', '-c')
        for code, label in KEYS:
            fresh_home()
            choose_app(code, label)
            send(code, 'short')
            foreground('buttons-' + str(code) + '-launch', BROWSER)
            checks.append(label + ': native long-press chooser saves and short press launches TV Bro')
            print(checks[-1], flush=True)

        fresh_home()
        for code, label in KEYS:
            send(code, 'short')
            foreground('buttons-' + str(code) + '-persisted', BROWSER)
        checks.append('All six native assignments survive launcher process restart')

        restore(190, 'YouTube', cancel=True)
        send(190, 'short')
        foreground('buttons-restore-cancelled-assignment', BROWSER)
        checks.append('Cancelling restoration keeps the custom assignment')

        restore(190, 'YouTube')
        send(190, 'short')
        foreground('buttons-youtube-restored', 'com.google.android.youtube.tv/')
        for code, label in KEYS:
            if code == 190:
                continue
            send(code, 'short')
            foreground('buttons-independent-' + str(code), BROWSER)
        checks.append('Restoring YouTube returns its native action and preserves the other five bindings')

        fresh_home()
        send(313, 'cancel')
        foreground('buttons-cancelled-key', HOME)
        checks.append('A cancelled key-up does not launch a saved app')

        restore(313, 'Star', all_buttons=True)
        fresh_home()
        send(313, 'short')
        foreground('buttons-star-restored', CHOOSER)
        shell('input keyevent KEYCODE_BACK')
        send(190, 'short')
        foreground('buttons-youtube-restored-all', 'com.google.android.youtube.tv/')
        fresh_home()
        for code, label in KEYS[2:]:
            send(code, 'short')
            foreground('buttons-oem-restored-' + str(code), HOME)
        logs = adb('logcat', '-d', binary=True).decode('utf-8', errors='replace')
        (ARTIFACTS / 'buttons-logcat.txt').write_text(logs)
        for code, label in KEYS[2:]:
            assert 'Default remote action: ' + label in logs
            assert 'Accepted OEM remote event: ' + str(code) in logs
        assert 'Patch refused' not in logs
        assert 'E LSPosed-Bridge:' not in logs
        assert 'Could not restore remote defaults' not in logs
        assert 'FATAL EXCEPTION' not in adb('logcat', '-d', '-s', 'AndroidRuntime:E')
        saved = adb('exec-out', '/debug_ramdisk/su -c '
                    + shlex.quote('cat ' + STORE + '/data/*/content'), binary=True)
        assert BROWSER.encode() not in saved
        checks.append('Restore all clears custom bindings, restores native star/YouTube and selects OEM fallback paths')
        zero_accounts()
    finally:
        (ARTIFACTS / 'buttons-final-logcat.raw').write_bytes(adb('logcat', '-d', binary=True))
        root('am force-stop ' + TARGET)
        root('rm -rf ' + STORE)
        if existed:
            root('cp -a ' + backup + '/store ' + STORE)
            root('restorecon -DRF ' + STORE)
        root('rm -rf ' + backup + ' ' + FIXTURE)
        fresh_home()
    report = {
        'result': 'passed', 'module_version': json.loads((apk.parent / 'receipt.json').read_text())['version_name'],
        'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'checks': checks, 'physical_tv_tested': False, 'accounts': 0,
        'limitations': ['KeyEvent receiver injection does not exercise the physical remote or Xiaomi bridge.',
                        'OEM default branches were selected; actual Xiaomi app/fallback launches require the TV.',
                        'Authenticated and managed-profile paths were not exercised.'],
        'cleanup': 'Previous native shortcut store restored; test helper removed.'}
    (ROOT / 'validation-all-shortcuts.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
