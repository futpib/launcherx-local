#!/usr/bin/env python3
"""Verify that No action consumes all six shortcuts without losing customization."""
import hashlib
import json
import time

from all_shortcuts_smoke import KEYS, restore, send
from build_publisher import build
from key_actions_smoke import ACTIVITY, APP, choose, events, receiver
from lab import ARTIFACTS, ROOT, adb, guard, root, shell
from profile_smoke import inspect, zero_accounts
from remote_smoke import CHOOSER, FIXTURE, STORE, foreground
from smoke import HOME, TARGET, fresh_home


def unchanged(code, name):
    before = events()
    send(code, 'short')
    foreground(name, ACTIVITY)
    assert events() == before, 'No action changed media or window-key state'
    logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S')
    assert 'No action for shortcut: ' + str(code) in logs


def main():
    guard()
    zero_accounts()
    apk = ROOT / 'build/apps/launcherx-local.apk'
    backup = '/data/local/tmp/launcherx-no-action-' + str(time.time_ns())
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
        for code, label in KEYS:
            choose(code, 16, 'No action')
            receiver()
            unchanged(code, 'no-action-' + str(code))
            print(label + ': No action keeps the current app and media state', flush=True)
        checks.append('All six source buttons save No action through the native chooser and consume short press without app launch, media events or window keys')

        fresh_home()
        receiver()
        for code, label in KEYS:
            unchanged(code, 'no-action-persisted-' + str(code))
        checks.append('All six No action assignments survive launcher process restart')

        send(313, 'long')
        foreground('no-action-edit', CHOOSER)
        inspect('no-action-edit', ['Restore Star default', 'No action'])
        shell('input keyevent KEYCODE_BACK')
        receiver()
        unchanged(313, 'no-action-edit-cancelled')
        checks.append('Long press still opens customization, displays No action and preserves the assignment when cancelled')

        choose(313, 0, 'Play / Pause')
        receiver()
        before = events()
        send(313, 'short')
        assert events() != before
        checks.append('Replacing No action with Play/Pause resumes real media-key delivery')

        choose(313, 17, 'KEYCODE_F8 (138)', custom='KEYCODE_F8')
        receiver()
        send(313, 'short')
        assert 'KEYCODE_F8' in events() and 'name="keys" value="1"' in events()
        checks.append('Custom key entry remains reachable and F8 reaches the focused activity')

        restore(190, 'YouTube')
        send(190, 'short')
        foreground('no-action-youtube-restored', 'com.google.android.youtube.tv/')
        receiver()
        unchanged(193, 'no-action-netflix-independent')
        checks.append('Individual restore re-enables YouTube while Netflix remains disabled')
        restore(313, 'Star', all_buttons=True)
        send(313, 'short')
        foreground('no-action-star-restored', CHOOSER)
        fresh_home()
        for code, label in KEYS[2:]:
            send(code, 'short')
            foreground('no-action-default-' + str(code), HOME)
        logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S')
        for code, label in KEYS[2:]:
            assert 'Default remote action: ' + label in logs
        for error in ['Could not save shortcut key', 'Could not send shortcut key', 'Patch refused']:
            assert error not in logs, error
        assert 'FATAL EXCEPTION' not in adb('logcat', '-d', '-s', 'AndroidRuntime:E')
        checks.append('Restore all removes No action assignments and selects native and OEM defaults')
        zero_accounts()
    finally:
        (ARTIFACTS / 'no-action-logcat.raw').write_bytes(adb('logcat', '-d', binary=True))
        root('am force-stop local.launcherx.patch')
        root('am force-stop ' + TARGET)
        root('rm -rf ' + STORE)
        if existed:
            root('cp -a ' + backup + '/store ' + STORE)
            root('restorecon -RF ' + STORE)
        root('rm -rf ' + backup + ' ' + FIXTURE)
        adb('uninstall', APP)
        fresh_home()
    report = {
        'result': 'passed',
        'module_version': json.loads((apk.parent / 'receipt.json').read_text())['version_name'],
        'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'checks': checks,
        'accounts': 0, 'physical_tv_tested': False,
        'limitations': ['Source KeyEvents enter the Google receiver; physical buttons and Xiaomi forwarding are not exercised.',
                        'OEM defaults are observed as selected branches; the emulator has no Xiaomi service.'],
        'cleanup': 'Native shortcut store restored; receiver helper and media/key fixture removed.'}
    (ROOT / 'validation-no-action.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
