#!/usr/bin/env python3
"""Exercise native Incognito selection and Google sign-in cancellation in the AVD."""
import hashlib
import json
import re
import subprocess
import time

from lab import ARTIFACTS, ROOT, adb, guard, root, shell, wait_boot
from smoke import HOME, TARGET, fresh_home, resumed, screen

CHOOSER = TARGET + '/.profile.chooser.ProfileChooserActivity'
PREFS = '/data/user/0/' + TARGET + '/shared_prefs/launcherx-local-profiles.xml'


def inspect(name, expected, any_of=()):
    deadline = time.monotonic() + 25
    while True:
        # Use screen pixels throughout: UIAutomator can crash the paused stock Home.
        screen(name)
        text = subprocess.check_output(['tesseract', str(ARTIFACTS / (name + '.png')),
                                        'stdout', '--psm', '6'], text=True,
                                       stderr=subprocess.DEVNULL)
        (ARTIFACTS / (name + '.txt')).write_text(text)
        normalized = re.sub(r'\s+', '', text).casefold()
        if (all(re.sub(r'\s+', '', value).casefold() in normalized for value in expected)
                and (not any_of or any(re.sub(r'\s+', '', value).casefold() in normalized
                                       for value in any_of))):
            return text
        if time.monotonic() >= deadline:
            raise AssertionError((name, expected, any_of, text))
        time.sleep(.5)


def zero_accounts():
    state = shell('dumpsys account')
    counts = re.findall(r'^\s*Accounts: (\d+)', state, re.MULTILINE)
    assert counts and all(value == '0' for value in counts), 'Account-free fixture required'


def chooser(name):
    fresh_home()
    inspect(name + '-home-ready', ['Your apps'])
    shell('input keyevent ' + ' '.join(['KEYCODE_DPAD_UP'] * 6))
    shell('input keyevent KEYCODE_DPAD_LEFT KEYCODE_DPAD_CENTER')
    inspect(name, ['Choose an account', 'Incognito', 'Add account'])
    resumed(CHOOSER)
    print(name + ': native chooser ready', flush=True)


def check_logs(name):
    # Native log messages can contain malformed UTF-8. Keep the original bytes
    # as evidence while decoding the text for Java/module failure checks.
    raw = adb('logcat', '-d', binary=True)
    (ARTIFACTS / (name + '.raw')).write_bytes(raw)
    logs = raw.decode('utf-8', errors='replace')
    (ARTIFACTS / (name + '.txt')).write_text(logs)
    assert 'active launcher profile: pseudonymous' in logs
    for error in ['Cannot add Incognito', 'Cannot update the native profile chooser', 'Patch refused']:
        assert error not in logs, error
    crashes = adb('logcat', '-d', '-s', 'AndroidRuntime:E')
    (ARTIFACTS / (name + '-crashes.txt')).write_text(crashes)
    assert 'FATAL EXCEPTION' not in crashes, crashes


def main():
    guard()
    zero_accounts()
    apk = ROOT / 'build/apps/launcherx-local.apk'
    print(adb('install', '--no-incremental', '-r', apk), flush=True)
    root('am force-stop ' + TARGET)
    # Reset only the new module preference to exercise its first-run default.
    previous = root('if [ -f ' + PREFS + ' ]; then cat ' + PREFS + '; fi')
    (ARTIFACTS / 'profile-preferences-before.xml').write_text(previous)
    root('rm -f ' + PREFS)
    adb('logcat', '-c')
    checks = []
    try:
        chooser('profile-default-chooser')
        assert 'name="incognito" value="true"' in root('cat ' + PREFS)
        checks.append('first-run Incognito default and native chooser entry')
        shell('input keyevent KEYCODE_DPAD_CENTER')
        time.sleep(2)
        resumed(HOME)
        inspect('profile-selected-incognito', ['Your apps'])
        checks.append('native DPAD Incognito selection returns to Home')

        chooser('profile-before-signin')
        shell('input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER')
        # Google's form layout and keyboard vary; the outlined field label can
        # defeat OCR. Require an email-form cue and the real Play Services activity.
        inspect('profile-signin', ['Sign in', 'Google Account'],
                any_of=['Email or phone', 'Forgot email?'])
        state = resumed('com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity')
        (ARTIFACTS / 'profile-signin-activity.txt').write_text(state)
        zero_accounts()
        checks.append('native Add account launches real Google Play Services sign-in')
        for _ in range(4):
            shell('input keyevent KEYCODE_BACK')
            time.sleep(.5)
            state = shell('dumpsys activity activities')
            top = re.search(r'topResumedActivity=.*', state)
            if top and CHOOSER in top.group():
                break
        inspect('profile-cancelled-signin', ['Choose an account', 'Incognito', 'Add account'])
        shell('input keyevent KEYCODE_BACK')
        time.sleep(1)
        resumed(HOME)
        assert 'name="incognito" value="true"' in root('cat ' + PREFS)
        zero_accounts()
        checks.append('cancelled sign-in leaves Incognito selected and creates no account')

        chooser('profile-after-cancel')
        shell('input keyevent KEYCODE_DPAD_CENTER')
        time.sleep(1)
        fresh_home()
        inspect('profile-restarted', ['Your apps'])
        assert 'name="incognito" value="true"' in root('cat ' + PREFS)
        checks.append('Incognito selection survives launcher restart')
        check_logs('profile-before-reboot-logcat')
        adb('reboot')
        time.sleep(10)
        wait_boot()
        fresh_home()
        chooser('profile-after-reboot')
        shell('input keyevent KEYCODE_DPAD_CENTER')
        time.sleep(1)
        resumed(HOME)
        zero_accounts()
        assert 'name="incognito" value="true"' in root('cat ' + PREFS)
        checks.append('Incognito selection and chooser survive emulator reboot')
        check_logs('profile-final-logcat')
        checks.append('no launcher fatal exception in uninterrupted remote sequences')
        result = {'result': 'passed', 'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                  'checks': checks, 'accounts': 0, 'physical_tv_tested': False,
                  'authenticated_google_roundtrip_tested': False,
                  'fixture': 'Google TV API 34 x86 with ARM translation; original TV APK'}
        (ARTIFACTS / 'profile-result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result, indent=2), flush=True)
    finally:
        # Leave the fixture at Home even when a sign-in assertion fails.
        shell('input keyevent KEYCODE_HOME')
        zero_accounts()


if __name__ == '__main__':
    main()
