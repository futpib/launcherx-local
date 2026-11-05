#!/usr/bin/env python3
"""Reproduce stock cleanup during a compatibility gap and recover real native bindings."""
import hashlib
import json
from pathlib import Path
import shlex
import tempfile
import time

from all_shortcuts_smoke import choose_app, restore, send
from build_publisher import build
from key_actions_smoke import ACTIVITY, APP, choose, events, receiver
from lab import ARTIFACTS, ROOT, adb, guard, root, shell
from remote_smoke import FIXTURE, STORE, foreground
from setup_smoke import negative_apk, start_home
from shortcut_test_state import SHADOW, isolated_backup
from smoke import BROWSER, TARGET, fresh_home


def fields(data):
    position = 0

    def varint():
        nonlocal position
        value, shift = 0, 0
        while True:
            byte = data[position]
            position += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value
            shift += 7
            assert shift < 70

    result = {}
    while position < len(data):
        tag = varint()
        if tag & 7 == 0:
            value = varint()
        else:
            assert tag & 7 == 2
            size = varint()
            value = data[position:position + size]
            position += size
        result.setdefault(tag >> 3, []).append(value)
    return result


def assignments(data):
    result = {}
    for entry in fields(data).get(1, []):
        item = fields(entry)
        code = fields(item[1][0])[1][0]
        result[code] = (item.get(2, [0])[0], item.get(6, [b''])[0])
    return result


def read(path=STORE + '/data/*/content'):
    return adb('exec-out', '/debug_ramdisk/su -c ' + shlex.quote('cat ' + path), binary=True)


def checkpoint(expected):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if assignments(read(SHADOW + '/bindings.pb')) == expected:
            return
        time.sleep(.2)
    raise AssertionError('Checkpoint did not capture the saved assignments')


def gap(refused, apk, name):
    before = read(SHADOW + '/bindings.pb')
    print(adb('install', '--no-incremental', '-r', refused), flush=True)
    adb('logcat', '-c')
    start_home(name + '-stock', stock=True)
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        stripped = read()
        if 193 not in assignments(stripped):
            break
        time.sleep(.5)
    assert 193 not in assignments(stripped), 'Stock Home did not actually prune OEM entries'
    assert 'Patch refused' in adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S')
    assert read(SHADOW + '/bindings.pb') == before, 'Refused module changed the checkpoint'
    (ARTIFACTS / (name + '-stripped.pb')).write_bytes(stripped)
    (ARTIFACTS / (name + '-refusal.log')).write_text(adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S'))
    print(adb('install', '--no-incremental', '-r', apk), flush=True)
    adb('logcat', '-c')
    fresh_home()
    return assignments(read())


def main():
    guard()
    apk = ROOT / 'build/apps/launcherx-local.apk'
    backup = '/data/local/tmp/launcherx-persistence-' + str(time.time_ns())
    root('am force-stop ' + TARGET)
    root('mkdir ' + backup)
    root('cp -a ' + STORE + ' ' + backup + '/store')
    checks = []
    try:
        root('rm -rf ' + STORE)
        print(adb('install', '--no-incremental', '-r', apk), flush=True)
        print(adb('install', '--no-incremental', '-r', build('keys')), flush=True)
        adb('push', build('remote'), FIXTURE)
        root('chmod 444 ' + FIXTURE)
        choose(313, 0, 'Play / Pause')
        choose_app(190, 'YouTube')
        choose(193, 17, 'KEYCODE_SPACE (62)', custom='SPACE')
        choose(194, 17, 'KEYCODE_F (34)', custom='f')
        choose(351, 16, 'No action')
        choose_app(352, 'Xiaomi TV+')
        original = assignments(read())
        (ARTIFACTS / 'persistence-original.pb').write_bytes(read())
        checkpoint(original)
        checks.append('Native chooser saved apps, media/custom keys and No action; checkpoint matches all assignments')
        print(checks[-1], flush=True)
        with tempfile.TemporaryDirectory(prefix='launcherx-refusal-') as directory:
            refused = negative_apk(Path(directory), 'dex')
            restored = gap(refused, apk, 'persistence-first')
            assert restored == original, (restored, original)
            checkpoint(original)
            fresh_home()
            assert assignments(read()) == original
            checks.append('Real unhooked Home pruned OEM/YouTube bindings; restoring module recovered every action and survived restart')
            print(checks[-1], flush=True)
            for code in [190, 352]:
                send(code, 'short')
                foreground('persistence-app-' + str(code), BROWSER)
            receiver()
            for code, expected in [(193, 'KEYCODE_SPACE'), (194, 'KEYCODE_F')]:
                send(code, 'short')
                assert expected in events()
            before = events()
            send(351, 'short')
            foreground('persistence-no-action', ACTIVITY)
            assert events() == before
            send(313, 'short')
            assert events() != before
            checks.append('Recovered apps launch; custom keys and Play/Pause reach the fixture; No action stays inert')
            print(checks[-1], flush=True)
            restore(193, 'Netflix')
            reset = assignments(read())
            assert reset[193][1] == b''
            checkpoint(reset)
            assert gap(refused, apk, 'persistence-reset') == reset
            checks.append('An individually restored default is not resurrected after another compatibility gap')
            print(checks[-1], flush=True)
            restore(313, 'Star', all_buttons=True)
            defaults = assignments(read())
            assert all(not defaults[code][1] for code in [313, 190, 193, 194, 351, 352])
            checkpoint(defaults)
            assert gap(refused, apk, 'persistence-reset-all') == defaults
            checks.append('Restore all clears the checkpoint actions and remains reset across stock cleanup')
        logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I', 'AndroidRuntime:E', '*:S')
        assert 'Could not preserve shortcut' not in logs and 'FATAL EXCEPTION' not in logs
        (ARTIFACTS / 'shortcut-persistence-logcat.txt').write_text(logs)
    finally:
        (ARTIFACTS / 'shortcut-persistence-last.log').write_text(adb('logcat', '-d', '-s', 'LauncherXLocal:I', 'AndroidRuntime:E', '*:S'))
        print(adb('install', '--no-incremental', '-r', apk), flush=True)
        root('am force-stop ' + TARGET)
        root('rm -rf ' + STORE)
        root('cp -a ' + backup + '/store ' + STORE)
        root('restorecon -DRF ' + STORE)
        root('rm -rf ' + backup + ' ' + FIXTURE)
        adb('uninstall', APP)
    report = {'result': 'passed', 'module_version': '0.21',
              'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'checks': checks,
              'limits': ['Real Google TV x86 emulator; receiver-injected events, not a physical remote',
                         'Compatibility refusal was forced on the installed launcher; no Play Store update was downloaded'],
              'cleanup': 'Original native store and independent checkpoint restored; test fixtures removed'}
    (ROOT / 'validation-shortcut-persistence.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    with isolated_backup():
        main()
