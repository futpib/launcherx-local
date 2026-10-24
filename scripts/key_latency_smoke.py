#!/usr/bin/env python3
"""Measure source-key release to actual delivered input using Android uptime."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import statistics
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

from build_publisher import build
from key_actions_smoke import choose, events, receiver
from lab import ARTIFACTS, ROOT, SDK, SERIAL, SERVER_PORT, adb, guard, root, shell
from profile_smoke import zero_accounts
from remote_smoke import FIXTURE, STORE
from smoke import TARGET, fresh_home


def event_values():
    return {item.get('name'): int(item.get('value')) for item in ET.fromstring(events())
            if item.tag in ('int', 'long')}


def measure(count, field):
    samples = []
    for index in range(count):
        before = event_values()[field]
        output = root('CLASSPATH=' + FIXTURE + ' app_process /system/bin local.launcherx.remote.SendKey 313 short')
        source = int(re.search(r'source_up_uptime_ms=(\d+)', output).group(1))
        deadline = time.monotonic() + 8
        while True:
            values = event_values()
            if values[field] > before:
                break
            assert time.monotonic() < deadline, (field, values, output)
            time.sleep(.1)
        assert values[field] == before + 1, (before, values)
        latency = values['received'] - source
        assert latency >= 0
        samples.append(latency)
        print(field + ' release-to-delivery: ' + str(latency) + ' ms', flush=True)
    return {'samples_ms': samples, 'median_ms': statistics.median(samples),
            'min_ms': min(samples), 'max_ms': max(samples)}


def worker_pid():
    value = root('pidof launcherx-key-injector || true').strip()
    if not value:
        return None
    assert len(value.split()) == 1, value
    return int(value)


def wait_worker_exit(timeout=8):
    deadline = time.monotonic() + timeout
    while worker_pid() is not None:
        assert time.monotonic() < deadline, 'Input worker did not exit'
        time.sleep(.5)


def lifecycle():
    original = worker_pid()
    assert original
    logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S')
    assert logs.count('Verified key-action caller installation') == 1
    denied = shell('content call --uri content://local.launcherx.patch.keys --method send --arg 138 2>&1')
    assert 'SecurityException' in denied and 'Only Google TV' in denied
    root('kill -TERM ' + str(original))
    wait_worker_exit()
    killed = measure(1, 'keys')
    assert worker_pid() not in (None, original)
    root('am force-stop local.launcherx.patch')
    wait_worker_exit()
    cold = measure(1, 'keys')
    logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S')
    assert logs.count('Verified key-action caller installation') == 2
    print('Killed-worker and cold-provider recovery passed; waiting for idle expiry', flush=True)
    wait_worker_exit(67)
    processes = shell('ps -A -o PID,USER,ARGS')
    relays = [row.split(None, 2) for row in processes.splitlines()
              if 'su -c CLASSPATH=' in row and 'KeyInjector --stream' in row]
    assert all(user.startswith('u0_a') for pid, user, command in relays), relays
    idle = measure(1, 'keys')
    assert worker_pid()
    pids = {row.split()[0] for row in shell('ps -A -o PID').splitlines()[1:]}
    assert all(pid not in pids for pid, user, command in relays), relays
    return {'after_killed_worker': killed, 'after_cold_provider': cold, 'after_idle_expiry': idle,
            'unprivileged_idle_relay_retired_on_wake': bool(relays),
            'checks': ['Warm calls reuse one full launcher verification and still reject another caller UID',
                       'A worker killed between presses restarts and delivers exactly one key',
                       'Stopping the module closes its root worker and the next press re-verifies the launcher',
                       'The root worker exits after 60 seconds idle; the next press retires any frozen unprivileged relay and starts a new worker']}


def protocol():
    path = shell('pm path local.launcherx.patch').split('package:')[1].strip()
    command = 'CLASSPATH=' + shlex.quote(path) + ' app_process /system/bin --nice-name=launcherx-key-protocol-test local.launcherx.patch.KeyInjector --stream'
    for request, expected in [('', ['READY']), ('0\n', ['READY', 'ERROR']), ('not-a-key\n', ['READY', 'ERROR'])]:
        before = event_values()
        result = subprocess.run([str(SDK / 'platform-tools/adb'), '-P', SERVER_PORT, '-s', SERIAL,
                                 'shell', '-T', '/debug_ramdisk/su -c ' + shlex.quote(command)],
                                input=request, text=True, capture_output=True, timeout=15)
        assert result.returncode == 0 and result.stdout.splitlines() == expected, result
        assert event_values() == before
    assert not root('pidof launcherx-key-protocol-test || true').strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=ROOT / 'build/apps/launcherx-local.apk')
    parser.add_argument('--output', type=Path, default=ROOT / 'validation-key-latency.json')
    parser.add_argument('--samples', type=int, default=8)
    parser.add_argument('--lifecycle', action='store_true')
    parser.add_argument('--baseline', type=Path)
    args = parser.parse_args()
    guard()
    zero_accounts()
    app = 'local.launcherx.keysfixture'
    backup = '/data/local/tmp/launcherx-latency-' + str(time.time_ns())
    root('am force-stop ' + TARGET)
    existed = root('test -d ' + STORE + ' && echo yes || echo no') == 'yes'
    root('mkdir ' + backup)
    if existed:
        root('cp -a ' + STORE + ' ' + backup + '/store')
    try:
        root('rm -rf ' + STORE)
        print(adb('install', '--no-incremental', '-r', args.apk), flush=True)
        print(adb('install', '--no-incremental', '-r', build('keys')), flush=True)
        adb('push', build('remote'), FIXTURE)
        root('chmod 444 ' + FIXTURE)
        adb('logcat', '-c')
        choose(313, 17, 'KEYCODE_F8 (138)', custom='KEYCODE_F8')
        receiver()
        initial_worker = worker_pid() if args.lifecycle else None
        custom = measure(args.samples, 'keys')
        extra = None
        if args.lifecycle:
            assert initial_worker == worker_pid()
            extra = lifecycle()
            protocol()
            extra['checks'].append('Private worker exits on EOF and rejects invalid keys without input delivery')
        choose(313, 0, 'Play / Pause')
        receiver()
        media = measure(args.samples, 'media')
        zero_accounts()
        logs = adb('logcat', '-d', binary=True)
        (ARTIFACTS / (args.output.stem + '-logcat.raw')).write_bytes(logs)
        for error in [b'Could not send shortcut key', b'Could not save shortcut key', b'FATAL EXCEPTION']:
            assert error not in logs, error
    finally:
        (ARTIFACTS / (args.output.stem + '-final-logcat.raw')).write_bytes(adb('logcat', '-d', binary=True))
        root('am force-stop local.launcherx.patch')
        wait_worker_exit()
        root('am force-stop ' + TARGET)
        root('rm -rf ' + STORE)
        if existed:
            root('cp -a ' + backup + '/store ' + STORE)
            root('restorecon -RF ' + STORE)
        root('rm -rf ' + backup + ' ' + FIXTURE)
        adb('uninstall', app)
        fresh_home()
    report = {'result': 'passed', 'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              'measurement': 'Source KeyEvent ACTION_UP eventTime to fixture callback uptime, excluding host ADB and sender startup.',
              'custom_f8': custom, 'play_pause': media, 'physical_tv_tested': False,
              'limitations': ['Google receiver injection in the x86 Android 14 emulator; not physical remote timing.',
                              'Media consumer is a silent AudioTrack and MediaSession fixture.'],
              'cleanup': 'Native store restored; fixture APK and receiver helper removed.'}
    if extra:
        report['lifecycle'] = extra
    if args.baseline:
        baseline = json.loads(args.baseline.read_text())
        report['baseline'] = baseline
        report['warm_custom_speedup'] = baseline['custom_f8']['median_ms'] / custom['median_ms']
        assert report['warm_custom_speedup'] > 2, report
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
