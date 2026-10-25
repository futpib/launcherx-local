#!/usr/bin/env python3
"""Measure source-key release to actual delivered input using Android uptime."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import statistics
import time
import xml.etree.ElementTree as ET

from build_publisher import build
from key_actions_smoke import choose, events, receiver
from lab import ARTIFACTS, ROOT, adb, guard, root, shell
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


def no_root_worker():
    assert not root('pidof launcherx-key-injector || true').strip()


def lifecycle():
    no_root_worker()
    logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S')
    assert logs.count('Verified key-action caller installation') == 1
    denied = shell('content call --uri content://local.launcherx.patch.keys --method send --arg 138 2>&1')
    assert 'SecurityException' in denied and 'Only Google TV' in denied
    root('am force-stop local.launcherx.patch')
    cold = measure(1, 'keys')
    logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S')
    assert logs.count('Verified key-action caller installation') == 2
    no_root_worker()
    return {'after_cold_provider': cold,
            'checks': ['Warm calls cache launcher verification and reject another caller UID',
                       'Stopping the companion app rediscovers the scoped input hook and delivers exactly one key',
                       'No root input worker is created']}


def protocol():
    for code, operation, ttl, error in [(0, 'send', 10000, 'Enter an Android key code'),
                                        (138, 'invalid', 10000, 'Unknown key operation'),
                                        (138, 'send', -1, 'Invalid or expired owner-user request')]:
        before = event_values()
        uptime = int(float(shell('cat /proc/uptime').split()[0]) * 1000)
        output = root('am broadcast --user 0 -a local.launcherx.patch.INJECT_KEY -p android '
                      '--ei code ' + str(code) + ' --es operation ' + operation +
                      ' --el local.launcherx.patch.deadline ' + str(uptime + ttl))
        assert 'data="Key request failed: ' + error in output, output
        assert event_values() == before
    no_root_worker()


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
        custom = measure(args.samples, 'keys')
        extra = None
        if args.lifecycle:
            extra = lifecycle()
            protocol()
            extra['checks'].append('Input hook rejects invalid keys, unknown operations and expired requests without input delivery')
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
        no_root_worker()
        root('am force-stop ' + TARGET)
        root('rm -rf ' + STORE)
        if existed:
            root('cp -a ' + backup + '/store ' + STORE)
            root('restorecon -DRF ' + STORE)
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
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
