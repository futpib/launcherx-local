#!/usr/bin/env python3
"""Exercise Incognito Ambient's native entry and dream service in the guarded AVD."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

from lab import ARTIFACTS, ROOT, SDK, adb, guard, root, shell, snapshot

URI = ('content://com.google.android.apps.tv.launcherx.coreservices.account.'
       'AccountModeContentProvider/queryAccountMode')
QUERY = 'content query --uri ' + URI + ' --projection accountMode:accountName 2>&1'


def cursor_probe():
    remote = '/data/local/tmp/launcherx-ambient-probe.dex'
    with tempfile.TemporaryDirectory(prefix='launcherx-ambient-') as directory:
        build = Path(directory)
        android = SDK / 'platforms/android-34/android.jar'
        subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                        '-classpath', str(android), '-d', directory,
                        str(ROOT / 'module/src/local/launcherx/patch/AmbientModeCursor.java'),
                        str(ROOT / 'tests/ambient/ModeCursorProbe.java')], check=True)
        subprocess.run([str(SDK / 'build-tools/37.0.0/d8'), '--min-api', '29',
                        '--lib', str(android), '--output', directory,
                        *map(str, build.rglob('*.class'))], check=True)
        try:
            adb('push', build / 'classes.dex', remote)
            output = shell('CLASSPATH=' + remote +
                           ' app_process /system/bin local.launcherx.patch.ModeCursorProbe')
            assert output.startswith('PASS:'), output
            (ARTIFACTS / 'ambient-cursor-probe.txt').write_text(output + '\n')
        finally:
            shell('rm -f ' + remote)


def main():
    guard()
    ARTIFACTS.mkdir(exist_ok=True)
    cursor_probe()
    before = root(QUERY)
    assert 'accountMode=2, accountName=,' in before, before
    denied = shell(QUERY)
    assert 'UnsupportedOperationException' in denied and 'Row: 0' not in denied, denied
    checks = ['Android cursor probe preserves identity, locks, extras and other account modes',
              'ordinary root caller still sees mode 2; unauthorized shell query remains rejected']
    try:
        shell('input keyevent KEYCODE_HOME')
        shell('am force-stop com.google.android.apps.tv.dreamx')
        shell('am start -W -n com.android.tv.settings/com.google.android.tv.settings.AmbientActivity')
        time.sleep(3)
        snapshot('ambient-menu')
        nodes = list(ET.parse(ARTIFACTS / 'ambient-menu.xml').iter('node'))
        labels = [node.get('text', '') for node in nodes]
        assert 'Start now' in labels and 'Settings' in labels, labels
        assert not any('set up Google TV' in label for label in labels), labels
        checks.append('native Ambient menu opens with Start now and Settings, without setup error')
        button = next(node for node in nodes if node.get('text') == 'Start now')
        left, top, right, bottom = map(int, re.findall(r'\d+', button.get('bounds')))
        shell(f'input tap {(left + right) // 2} {(top + bottom) // 2}')
        time.sleep(3)
        dreams = shell('dumpsys dreams')
        (ARTIFACTS / 'ambient-dreams.txt').write_text(dreams)
        assert 'com.google.android.apps.tv.dreamx.service.Backdrop' in dreams and 'mBound=true' in dreams
        (ARTIFACTS / 'ambient-dream.png').write_bytes(adb('exec-out', 'screencap -p', binary=True))
        checks.append('native Start now binds the real Backdrop dream service')
    finally:
        shell('input keyevent KEYCODE_HOME')
    time.sleep(2)
    assert 'mCurrentDream=null' in shell('dumpsys dreams')
    shell('input keyevent KEYCODE_HOME')
    time.sleep(2)
    snapshot('ambient-home')
    assert 'Incognito' in (ARTIFACTS / 'ambient-home.xml').read_text()
    assert root(QUERY) == before
    checks.append('Home exits the screensaver and returns to Incognito with unchanged account state')
    result = {'apk_sha256': hashlib.sha256((ROOT / 'build/apps/launcherx-local.apk').read_bytes()).hexdigest(),
              'result': 'passed', 'checks': checks, 'physical_tv_tested': False,
              'limit': 'Full settings editor needs valid Cast device credentials; tested separately on TV.'}
    (ARTIFACTS / 'ambient-result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2), flush=True)


if __name__ == '__main__':
    main()
