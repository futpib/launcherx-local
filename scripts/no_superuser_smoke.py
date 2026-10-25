#!/usr/bin/env python3
"""Verify real launcher features with the companion's Magisk root policy set to deny."""
import argparse
import hashlib
import json
import re
import shlex
import subprocess
import sys

from build_publisher import build
from lab import ARTIFACTS, ROOT, adb, guard, root, shell, wait_boot
from smoke import fresh_home


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tests', nargs='*', choices=['content_smoke', 'key_actions_smoke',
                        'key_latency_smoke', 'no_action_smoke', 'setup_smoke'],
                        default=['content_smoke', 'key_actions_smoke', 'key_latency_smoke'])
    args = parser.parse_args()
    guard()
    apk = ROOT / 'build/apps/launcherx-local.apk'
    print(adb('install', '--no-incremental', '-r', apk), flush=True)
    uid = int(re.search(r'uid:(\d+)', shell('cmd package list packages -U local.launcherx.patch')).group(1))
    columns = 'uid,policy,until,logging,notification'
    query = 'SELECT ' + columns + ' FROM policies WHERE uid=' + str(uid) + ';'

    def sql(command):
        return root('magisk --sqlite ' + shlex.quote(command))

    previous = sql(query)
    (ARTIFACTS / 'no-superuser-policy-backup.json').write_text(json.dumps(
        {'uid': uid, 'row': previous}, indent=2) + '\n')
    checks = []
    denied = ''
    try:
        sql('REPLACE INTO policies (' + columns + ') VALUES (' + str(uid) + ',1,0,1,1);')
        denied = sql(query)
        assert '|policy=1|' in denied, denied
        adb('reboot')
        wait_boot()
        boot_log = adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S')
        (ARTIFACTS / 'no-superuser-boot-logcat.txt').write_text(boot_log)
        assert 'Registered key input in android' in boot_log, boot_log
        checks.append('Reboot loads the tested APK into the actual system_server input host')
        fresh_home()
        print(adb('install', '--no-incremental', '-r', build('probe')), flush=True)
        for keys in (False, True):
            response = shell('am broadcast -n local.launcherx.probe/.Probe --ez keys ' + str(keys).lower())
            assert 'permission=-1;reply=null' in response, response
        checks.append('A different-signed installed app requests CONTROL but is denied both channel and key receivers')
        response = shell('content call --uri content://local.launcherx.patch.keys --method send --arg 138 2>&1')
        assert 'SecurityException' in response and 'Only Google TV' in response, response
        checks.append('The public key provider rejects the unrelated shell UID')
        for test in args.tests:
            command = [sys.executable, str(ROOT / 'scripts' / (test + '.py'))]
            if test == 'key_latency_smoke':
                command += ['--lifecycle']
            log = ARTIFACTS / ('no-superuser-' + test + '.log')
            print('Running ' + test + '; log: ' + str(log), flush=True)
            with log.open('w') as output:
                subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, check=True, cwd=ROOT)
            assert sql(query) == denied, 'Companion root policy changed during ' + test
            checks.append(test + ' passed with companion root explicitly denied')
        assert not root('pidof launcherx-key-injector || true').strip()
        checks.append('No root key-injector process exists')
    finally:
        if previous:
            row = dict(field.split('=', 1) for field in previous.split('|'))
            values = ','.join(str(int(row[column])) for column in columns.split(','))
            sql('REPLACE INTO policies (' + columns + ') VALUES (' + values + ');')
        else:
            sql('DELETE FROM policies WHERE uid=' + str(uid) + ';')
        assert sql(query) == previous, 'Magisk policy restoration failed'
        if shell('pm list packages local.launcherx.probe'):
            print(adb('uninstall', 'local.launcherx.probe'), flush=True)
        assert not shell('pm list packages local.launcherx.probe')
    report = {'result': 'passed', 'module_version': json.loads((apk.parent / 'receipt.json').read_text())['version_name'],
              'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'companion_policy_during_tests': denied,
              'checks': checks, 'physical_tv_tested': False,
              'input_host': 'Actual Android system_server through LSPosed System Framework scope',
              'limitations': ['Xiaomi service registration is source-reviewed, not exercised by this emulator.',
                              'Test setup and event observation use the separately authorized ADB shell root grant.'],
              'cleanup': 'Previous companion Magisk policy restored; unrelated app removed; regression suites restore their fixtures.'}
    (ROOT / 'validation-no-superuser.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
