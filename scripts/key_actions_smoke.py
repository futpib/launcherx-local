#!/usr/bin/env python3
"""Exercise saved key actions through native UI, real media sessions and input injection."""
import hashlib
import json
import re
import shlex
import subprocess
import time
from PIL import Image

from all_shortcuts_smoke import KEYS, choose_app, restore, send
from build_publisher import build
from lab import ARTIFACTS, ROOT, adb, guard, root, shell
from profile_smoke import zero_accounts
from remote_smoke import CHOOSER, FIXTURE, STORE, foreground, send as send_native
from smoke import BROWSER, HOME, TARGET, fresh_home, screen

APP = 'local.launcherx.keysfixture'
ACTIVITY = APP + '/.KeyActivity'
EVENTS = '/data/user/0/' + APP + '/shared_prefs/events.xml'


def dialog(name, expected):
    deadline = time.monotonic() + 25
    while True:
        screen(name)
        source = Image.open(ARTIFACTS / (name + '.png'))
        width, height = source.size
        path = ARTIFACTS / (name + '-dialog.png')
        source.crop((int(width * .24), int(height * .05), int(width * .76), int(height * .97))).save(path)
        text = subprocess.check_output(['tesseract', str(path), 'stdout', '--psm', '6'],
                                       text=True, stderr=subprocess.DEVNULL)
        (ARTIFACTS / (name + '.txt')).write_text(text)
        if re.sub(r'\s+', '', expected).casefold() in re.sub(r'\s+', '', text).casefold():
            return text
        assert time.monotonic() < deadline, (name, expected, text)
        time.sleep(.4)


def choose(code, index, label, custom=None, cancel=False):
    fresh_home()
    send(code, 'long')
    foreground('keys-chooser', CHOOSER)
    shell('input keyevent KEYCODE_DPAD_UP KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT '
          'KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER')
    dialog('keys-list', 'Send key for')
    if index:
        shell('input keyevent ' + ' '.join(['KEYCODE_DPAD_DOWN'] * index))
    shell('input keyevent KEYCODE_DPAD_CENTER')
    if custom is not None:
        dialog('keys-custom', 'Android key code')
        shell('input text ' + custom)
        shell('input keyevent KEYCODE_BACK')
        time.sleep(.4)
        shell('input keyevent KEYCODE_DPAD_DOWN KEYCODE_DPAD_LEFT KEYCODE_DPAD_CENTER')
    dialog('keys-confirm', ' to ' + label + '?')
    if cancel:
        shell('input keyevent KEYCODE_BACK')
        time.sleep(.4)
        shell('input keyevent KEYCODE_BACK')
    else:
        shell('input keyevent KEYCODE_DPAD_LEFT KEYCODE_DPAD_CENTER')
    foreground('keys-saved', HOME)


def receiver():
    shell('am start -W -n ' + ACTIVITY)
    foreground('keys-receiver', ACTIVITY)


def events():
    return root('cat ' + EVENTS)


def main():
    guard()
    zero_accounts()
    apk = ROOT / 'build/apps/launcherx-local.apk'
    backup = '/data/local/tmp/launcherx-key-actions-' + str(time.time_ns())
    root('am force-stop ' + TARGET)
    existed = root('test -d ' + STORE + ' && echo yes || echo no') == 'yes'
    root('mkdir ' + backup)
    if existed:
        root('cp -a ' + STORE + ' ' + backup + '/store')
    checks = []
    try:
        root('rm -rf ' + STORE)
        print(adb('install', '--no-incremental', '-r', apk), flush=True)
        print(adb('install', '--no-incremental', '-r', build('keys')), flush=True)
        adb('push', build('remote'), FIXTURE)
        root('chmod 444 ' + FIXTURE)
        adb('logcat', '-c')
        denied = shell('content call --uri content://local.launcherx.patch.keys --method prepare --arg 4 2>&1')
        assert 'SecurityException' in denied and 'Only Google TV' in denied, denied
        checks.append('Root broker rejects an unrelated shell caller')

        choose(313, 0, 'Play / Pause')
        receiver()
        assert 'name="playing" value="true"' in events()
        send(313, 'short')
        assert 'name="playing" value="false"' in events()
        assert 'name="media" value="1"' in events()
        send(313, 'short')
        assert 'name="playing" value="true"' in events()
        assert 'name="media" value="2"' in events()
        checks.append('Saved Play/Pause delivers exactly one media press per short press to a real Android MediaSession')
        print(checks[-1], flush=True)
        send_native(313, 'short', 'generated')
        assert 'name="media" value="2"' in events()
        checks.append('An injected fallback key cannot recursively trigger another Send key action')

        choose(313, 1, 'Play', cancel=True)
        receiver()
        send(313, 'short')
        assert 'name="media" value="3"' in events()
        assert 'name="playing" value="false"' in events()
        checks.append('Opening the chooser, cancelling a replacement and restarting the launcher retain the saved key action')

        choose(313, 17, 'KEYCODE_F8 (138)', custom='KEYCODE_F8')
        receiver()
        send(313, 'short')
        assert 'KEYCODE_F8' in events() and 'name="keys" value="1"' in events()
        checks.append('Custom symbolic Android key is saved and root injection reaches the focused app window')
        print(checks[-1], flush=True)

        fresh_home()
        choose_app(313, 'Star')
        send(313, 'short')
        foreground('keys-replaced-by-app', BROWSER)
        checks.append('Choosing a favorite app replaces a saved key action')

        for code, label in KEYS[1:]:
            choose(code, 0, 'Play / Pause')
            receiver()
            before = int(re.search(r'name="media" value="(\d+)"', events()).group(1))
            send(code, 'short')
            assert 'name="media" value="' + str(before + 1) + '"' in events()
            print(label + ': saved Play/Pause reaches the media session', flush=True)
        checks.append('All six supported source buttons can send Play/Pause through the Google receiver')

        choose(313, 9, 'Back')
        receiver()
        send(313, 'short')
        foreground('keys-back', HOME)
        checks.append('Back action uses Android input dispatch to leave the receiver app')

        choose(190, 8, 'TV home')
        receiver()
        send(190, 'short')
        foreground('keys-home', HOME)
        checks.append('YouTube can send the actual TV Home key without opening Google Home setup')

        restore(190, 'YouTube')
        send(190, 'short')
        foreground('keys-youtube-restored', 'com.google.android.youtube.tv/')
        restore(313, 'Star', all_buttons=True)
        send(313, 'short')
        foreground('keys-star-restored', CHOOSER)
        saved = adb('exec-out', '/debug_ramdisk/su -c '
                    + shlex.quote('cat ' + STORE + '/data/*/content'), binary=True)
        assert b'launcherx-key:' not in saved
        checks.append('Individual and all-button restoration remove key actions and retain native defaults')
        logs = adb('logcat', '-d', binary=True).decode('utf-8', errors='replace')
        (ARTIFACTS / 'keys-logcat.txt').write_text(logs)
        for error in ['Could not save shortcut key', 'Could not send shortcut key', 'E LSPosed-Bridge:', 'FATAL EXCEPTION']:
            assert error not in logs, error
        zero_accounts()
    finally:
        (ARTIFACTS / 'keys-final-logcat.raw').write_bytes(adb('logcat', '-d', binary=True))
        root('am force-stop local.launcherx.patch')
        root('am force-stop ' + TARGET)
        root('rm -rf ' + STORE)
        if existed:
            root('cp -a ' + backup + '/store ' + STORE)
            root('restorecon -RF ' + STORE)
        root('rm -rf ' + backup + ' ' + FIXTURE)
        adb('uninstall', APP)
        fresh_home()
    report = {'result': 'passed', 'module_version': json.loads((apk.parent / 'receipt.json').read_text())['version_name'],
              'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'checks': checks,
              'physical_tv_tested': False, 'accounts': 0,
              'limitations': ['Silent AudioTrack and MediaSession fixture is not a third-party player.',
                              'Source KeyEvents enter the Google receiver; physical buttons and Xiaomi forwarding are not exercised.',
                              'Custom key effects depend on the receiving app and Android policy.'],
              'cleanup': 'Native shortcut store restored; test APK and helper removed.'}
    (ROOT / 'validation-key-actions.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
