#!/usr/bin/env python3
"""Check the real Android freezer and Cast receiver in the guarded Android 14 AVD."""
import hashlib
import json
import re
import shlex
import time

from build_publisher import build
from lab import ROOT, adb, guard, root, shell, wait_boot

CAST = 'com.google.android.apps.mediashell'
PROBE = 'local.launcherx.probe'
OUT = ROOT / 'artifacts/cast-freezer'


def process(package):
    dump = shell('dumpsys activity processes')
    for block in re.split(r'(?=^  \*(?:APP|PERS)\*)', dump, flags=re.M):
        if re.search(r'ProcessRecord\{[^\n]*:' + re.escape(package) + r'/', block[:300]):
            return block.split('\n  *', 1)[0]
    raise AssertionError('Process missing: ' + package)


def wait_for(description, check, timeout=50):
    deadline = time.monotonic() + timeout
    while True:
        try:
            result = check()
            if result:
                return result
        except (AssertionError, RuntimeError):
            pass
        if time.monotonic() >= deadline:
            raise AssertionError('Timed out: ' + description)
        time.sleep(2)


def protected():
    state = process(CAST)
    assert 'isFreezeExempt=true' in state, state
    assert 'isFrozen=false' in state and 'isPendingFreeze=false' in state, state
    return state


def main():
    guard()
    OUT.mkdir(parents=True, exist_ok=True)
    assert shell('getprop ro.build.version.sdk') == '34'
    assert not shell('pm list packages ' + PROBE), 'Existing probe must not be overwritten'
    freezer = shell('settings get global cached_apps_freezer')
    constants = shell('settings get global activity_manager_constants')
    voice = shell('settings get secure voice_interaction_service')
    apk = ROOT / 'build/apps/launcherx-local.apk'
    checks = []
    try:
        print(adb('install', '--no-incremental', '-r', apk), flush=True)
        adb('reboot')
        wait_boot()
        logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S')
        (OUT / 'boot.log').write_text(logs)
        assert 'Installed Cast freezer exemption' in logs, logs
        state = wait_for('Cast exemption after boot', protected)
        (OUT / 'cast-boot.txt').write_text(state)
        checks.append('Real system_server sets the system Cast process freezer exemption at boot')

        shell('settings put global cached_apps_freezer enabled')
        assert 'use_freezer=true' in shell('dumpsys activity settings')
        # Shorten only this AVD's service inactivity window, then restore it below.
        values = [] if constants == 'null' else [v for v in constants.split(',')
                                               if not v.startswith('service_max_inactivity=')]
        values.append('service_max_inactivity=1000')
        shell('settings put global activity_manager_constants ' + shlex.quote(','.join(values)))
        # Assistant's live binding otherwise keeps Cast foreground-bound in this AVD.
        shell("settings put secure voice_interaction_service ''")
        print(adb('install', '--no-incremental', build('probe')), flush=True)
        shell('am broadcast -n ' + PROBE + '/.Probe')
        shell('input keyevent KEYCODE_HOME')
        time.sleep(3)
        shell('am broadcast -n ' + PROBE + '/.Probe')

        def cached_cast():
            state = protected()
            return state if 'cached=true' in state else None

        state = wait_for('Cast reaches cached state', cached_cast)
        (OUT / 'cast-cached.txt').write_text(state)
        checks.append('Cast remains exempt and unfrozen while cached')

        def frozen_control():
            state = process(PROBE)
            assert 'isFreezeExempt=false' in state, state
            return state if 'isFrozen=true' in state else None

        state = wait_for('unrelated app freezes normally', frozen_control, timeout=75)
        (OUT / 'control-frozen.txt').write_text(state)
        (OUT / 'cast-with-frozen-control.txt').write_text(protected())
        checks.append('Unrelated cached app freezes automatically while Cast stays unfrozen')

        old_pid = int(re.search(r'\bpid=(\d+)', protected())[1])
        assert root(f'cat /proc/{old_pid}/cmdline').split('\x00')[0] == CAST
        root('kill -TERM ' + str(old_pid))

        def restarted():
            state = protected()
            return state if int(re.search(r'\bpid=(\d+)', state)[1]) != old_pid else None

        state = wait_for('restarted Cast receives exemption', restarted)
        (OUT / 'cast-restarted.txt').write_text(state)
        checks.append('Android restarts Cast with a fresh exempt process record')
        logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I', 'AndroidRuntime:E', '*:S')
        (OUT / 'final.log').write_text(logs)
        assert 'Cast freezer hook refused' not in logs and 'Cast freezer exemption failed' not in logs
        assert 'FATAL EXCEPTION IN SYSTEM PROCESS' not in logs
    finally:
        if shell('pm list packages ' + PROBE):
            print(adb('uninstall', PROBE), flush=True)
        for key, value in [('cached_apps_freezer', freezer), ('activity_manager_constants', constants)]:
            shell('settings delete global ' + key if value == 'null'
                  else 'settings put global ' + key + ' ' + shlex.quote(value))
            assert shell('settings get global ' + key) == value
        shell('settings delete secure voice_interaction_service' if voice == 'null'
              else 'settings put secure voice_interaction_service ' + shlex.quote(voice))
        assert shell('settings get secure voice_interaction_service') == voice
        shell('input keyevent KEYCODE_HOME')
    report = {'result': 'passed',
              'module_version': json.loads((apk.parent / 'receipt.json').read_text())['version_name'],
              'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'checks': checks,
              'physical_tv_tested': False,
              'limitations': ['Framework integration tested on the actual Android 14 emulator.',
                              'Assistant binding temporarily disabled and service inactivity shortened to exercise cached Cast.',
                              'No phone-to-TV playback or standby network policy changes tested.'],
              'cleanup': 'Unrelated probe removed; original freezer, inactivity and voice settings restored.'}
    (ROOT / 'validation-cast-freezer.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
