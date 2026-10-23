#!/usr/bin/env python3
"""Exercise automatic activation, LSPosed disable, and compatibility refusal in the AVD."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

from lab import ARTIFACTS, ROOT, adb, guard, root, shell, snapshot
from profile_smoke import inspect, zero_accounts
from smoke import HOME, TARGET, resumed

SETUP = 'local.launcherx.patch/.SetupActivity'
BASIC = TARGET + '/.home.VanillaModeHomeActivity'
MANAGER = 'org.lsposed.manager/.ui.activity.MainActivity'


def hierarchy(name):
    snapshot(name)
    return list(ET.parse(ARTIFACTS / (name + '.xml')).iter('node'))


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    shell(f'input tap {(x1 + x2) // 2} {(y1 + y2) // 2}')
    time.sleep(1)


def module_enabled(enabled):
    # Exercise LSPosed's actual switch; never edit its database or module scope.
    root('am force-stop ' + TARGET)
    shell('am force-stop org.lsposed.manager')
    shell('am start -W -n ' + MANAGER)
    nodes = hierarchy('setup-framework-start')
    if not any(node.get('text') == 'Enable module' for node in nodes):
        tab = next(node for node in nodes if node.get('content-desc', '').startswith('Modules'))
        tap(tab)
        nodes = hierarchy('setup-framework-modules')
        tap(next(node for node in nodes if node.get('text') == 'LauncherX Local'))
        nodes = hierarchy('setup-framework-scope')
    assert any(node.get('text') == 'com.google.android.apps.tv.launcherx' for node in nodes)
    switch = next(node for node in nodes if node.get('class') == 'android.widget.Switch')
    if (switch.get('checked') == 'true') != enabled:
        tap(switch)
    nodes = hierarchy('setup-framework-enabled' if enabled else 'setup-framework-disabled')
    switch = next(node for node in nodes if node.get('class') == 'android.widget.Switch')
    assert (switch.get('checked') == 'true') == enabled
    checked = [node for node in nodes if node.get('class') == 'android.widget.CheckBox'
               and node.get('checked') == 'true']
    assert len(checked) == 1, 'Fixture must scope only Google TV'


def start_home(name, basic=False, stock=False):
    root('am force-stop ' + TARGET)
    shell('input keyevent KEYCODE_HOME')
    inspect(name, ['Set up Google TV'] if basic or stock else ['Your apps'])
    if stock:
        state = resumed(TARGET)
        (ARTIFACTS / (name + '-activity.txt')).write_text(state)
        return
    deadline = time.monotonic() + 30
    while True:
        try:
            resumed(BASIC if basic else HOME)
            break
        except AssertionError:
            if time.monotonic() >= deadline:
                (ARTIFACTS / (name + '-failure-logcat.txt')).write_text(adb('logcat', '-d'))
                raise
            time.sleep(.5)
    resolved = shell('cmd package resolve-activity --brief -a android.intent.action.MAIN '
                     '-c android.intent.category.HOME -p ' + TARGET)
    assert (BASIC if basic else HOME) in resolved, resolved


def basic_fixture():
    # Prepare a stock Basic TV starting point; this is test setup, not activation.
    root('am force-stop ' + TARGET)
    root('pm enable ' + BASIC + ' && pm disable ' + HOME)


def open_setup(name, expected):
    root('am force-stop ' + TARGET)
    shell('am force-stop local.launcherx.patch')
    shell('am start -W -n ' + SETUP)
    nodes = hierarchy(name)
    assert any(expected in node.get('text', '') for node in nodes), expected
    buttons = [node.get('text') for node in nodes if node.get('class') == 'android.widget.Button']
    assert buttons == ['Choose app content'], buttons


def negative_apk(directory, kind):
    shutil.copytree(ROOT / 'module', directory / 'module')
    (directory / 'scripts').mkdir()
    shutil.copy2(ROOT / 'scripts/build.py', directory / 'scripts/build.py')
    (directory / 'artifacts').mkdir()
    shutil.copy2(ROOT / 'artifacts/xposed-api-82.jar', directory / 'artifacts/xposed-api-82.jar')
    (directory / '.cache').mkdir()
    shutil.copy2(ROOT / '.cache/local.keystore', directory / '.cache/local.keystore')
    config = json.loads((ROOT / 'compatibility.json').read_text())
    if kind == 'version':
        config['version_code'] += 1
    else:
        config['dex_sha256'][0][1] = '0' * 64
    (directory / 'compatibility.json').write_text(json.dumps(config))
    subprocess.run(['python3', str(directory / 'scripts/build.py')], check=True)
    return directory / 'build/apps/launcherx-local.apk'


def main():
    guard()
    zero_accounts()
    apk = ROOT / 'build/apps/launcherx-local.apk'
    print(adb('install', '--no-incremental', '-r', apk), flush=True)
    checks = []
    try:
        basic_fixture()
        adb('logcat', '-c')
        start_home('setup-auto-home')
        checks.append('Basic TV automatically becomes Incognito Home when the module loads')
        print(checks[-1], flush=True)
        logs = adb('logcat', '-d')
        assert 'Home selected by the loaded module' in logs
        (ARTIFACTS / 'setup-auto-logcat.txt').write_text(logs)
        assert 'FATAL EXCEPTION' not in adb('logcat', '-d', '-s', 'AndroidRuntime:E')

        module_enabled(False)
        adb('logcat', '-c')
        start_home('setup-module-disabled', stock=True)
        logs = adb('logcat', '-d')
        assert 'installed mode=apps' not in logs
        assert 'FATAL EXCEPTION' not in adb('logcat', '-d', '-s', 'AndroidRuntime:E')
        (ARTIFACTS / 'setup-disabled-logcat.txt').write_text(logs)
        checks.append('disabling in LSPosed and restarting restores stock signed-out setup UI with zero accounts')
        print(checks[-1], flush=True)
        module_enabled(True)
        start_home('setup-module-reenabled')
        checks.append('re-enabling in LSPosed and restarting automatically restores Incognito Home')

        open_setup('setup-one-control', 'Compatible launcher.')
        checks.append('settings app has only Choose app content and passive compatibility status')
        for kind, expected in [('version', 'Unsupported launcher version'), ('dex', 'Code mismatch: classes.dex')]:
            with tempfile.TemporaryDirectory(prefix='launcherx-negative-') as directory:
                bad = negative_apk(Path(directory), kind)
                basic_fixture()
                print(adb('install', '--no-incremental', '-r', bad), flush=True)
                adb('logcat', '-c')
                start_home('setup-refuse-' + kind + '-home', basic=True)
                logs = adb('logcat', '-d')
                assert 'Patch refused' in logs and expected in logs
                assert 'Home selected by the loaded module' not in logs
                (ARTIFACTS / ('setup-refuse-' + kind + '-logcat.txt')).write_text(logs)
                open_setup('setup-refuse-' + kind, expected)
                shell('input keyevent KEYCODE_DPAD_UP KEYCODE_DPAD_CENTER')
                nodes = hierarchy('setup-refuse-' + kind + '-channels')
                assert any('Cannot change app content: ' + expected in node.get('text', '') for node in nodes)
                checks.append(kind + ' mismatch refuses hooks, automatic activation and channel changes')
                print(checks[-1], flush=True)
    finally:
        print(adb('install', '--no-incremental', '-r', apk), flush=True)
        module_enabled(True)
        start_home('setup-final-home')
        zero_accounts()
    result = {'result': 'passed', 'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
              'checks': checks, 'physical_tv_tested': False,
              'fixture': 'isolated emulator; actual LSPosed UI; negative module metadata variants'}
    (ARTIFACTS / 'setup-result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2), flush=True)


if __name__ == '__main__':
    main()
