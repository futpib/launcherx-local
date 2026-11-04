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
    # Activating the module can bring Home forward during its one-time handoff.
    # Reopen the manager and re-read the switch before deciding whether to toggle.
    shell('am start -W -n ' + MANAGER)
    root('am force-stop ' + TARGET)
    for attempt in range(5):
        shell('am start -W -n ' + MANAGER)
        time.sleep(.5)
        try:
            resumed(MANAGER)
            nodes = hierarchy('setup-framework-start-' + str(attempt))
        except (AssertionError, RuntimeError):
            continue
        if not any(node.get('text') == 'Enable module' for node in nodes):
            tab = next((node for node in nodes if node.get('content-desc', '').startswith('Modules')), None)
            if tab is None:
                continue
            tap(tab)
            nodes = hierarchy('setup-framework-modules')
            module = next((node for node in nodes if node.get('text') == 'LauncherX Local'), None)
            if module is None:
                continue
            tap(module)
            nodes = hierarchy('setup-framework-scope')
        if not any(node.get('text') == 'com.google.android.apps.tv.launcherx' for node in nodes):
            continue
        switch = next((node for node in nodes if node.get('class') == 'android.widget.Switch'), None)
        if switch is None:
            continue
        if (switch.get('checked') == 'true') != enabled:
            tap(switch)
        shell('am start -W -n ' + MANAGER)
        nodes = hierarchy('setup-framework-enabled' if enabled else 'setup-framework-disabled')
        switch = next((node for node in nodes if node.get('class') == 'android.widget.Switch'), None)
        if switch is None or (switch.get('checked') == 'true') != enabled:
            continue
        checked = [node for node in nodes if node.get('class') == 'android.widget.CheckBox'
                   and node.get('checked') == 'true']
        assert len(checked) in (1, 2), 'Fixture scopes Google TV and optionally System Framework'
        return
    raise AssertionError('LSPosed manager did not retain the requested module state')


def start_home(name, basic=False, stock=False):
    root('am force-stop ' + TARGET)
    time.sleep(1)  # Let Android detach the old notification listener before restarting it.
    shell('input keyevent KEYCODE_HOME')
    if stock or basic:
        # Recent stock launchers may show the device-lock onboarding first.
        text = inspect(name, [], any_of=['Set up Google TV', 'Set up Device Lock', 'Sign in with Google'])
        if 'Set up Device Lock' in text:
            nodes = hierarchy(name + '-device-lock')
            tap(next(node for node in nodes if node.get('text') == 'Not now'))
            inspect(name, [], any_of=['Set up Google TV', 'Sign in with Google'])
    else:
        inspect(name, ['Your apps'])
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
    shell('am force-stop local.launcherx.patch')
    shell('am start -W -n ' + SETUP)
    deadline = time.monotonic() + 30
    while True:
        nodes = hierarchy(name)
        if any(expected in node.get('text', '') for node in nodes):
            break
        assert any('Checking launcher' in node.get('text', '') for node in nodes), expected
        assert time.monotonic() < deadline, expected
        time.sleep(.5)
    buttons = [node.get('text') for node in nodes if node.get('class') == 'android.widget.Button']
    assert buttons == ['Choose app content'], buttons
    assert not any(node.get('class') == 'android.widget.Switch' for node in nodes)


def negative_apk(directory, kind):
    shutil.copytree(ROOT / 'module', directory / 'module')
    (directory / 'scripts').mkdir()
    shutil.copy2(ROOT / 'scripts/build.py', directory / 'scripts/build.py')
    (directory / 'artifacts').mkdir()
    shutil.copy2(ROOT / 'artifacts/xposed-api-82.jar', directory / 'artifacts/xposed-api-82.jar')
    (directory / '.cache').mkdir()
    shutil.copy2(ROOT / '.cache/local.keystore', directory / '.cache/local.keystore')
    config = json.loads((ROOT / 'compatibility.json').read_text())
    installed = int(re.search(r'versionCode=(\d+)', shell('dumpsys package ' + TARGET))[1])
    profiles = [config, *config.get('additional_versions', [])]
    if kind == 'dex':
        # Pin the actual runtime version, so a hash failure must not fall through.
        config['version_code'] = installed
        config['dex_sha256'][0][1] = '0' * 64
        config['additional_versions'] = [item for item in profiles[1:] if item['version_code'] != installed]
    else:
        # Remove the newest exact profile to exercise structural matching on the real APK.
        config['additional_versions'] = [item for item in profiles[1:] if item['version_code'] < 837595]
        profile_path = directory / 'module/assets/compatibility-837595.json'
        profile = json.loads(profile_path.read_text())
        if kind == 'heuristic-mismatch':
            profile['classes']['isa']['code'] = '0' * 64
        profile_path.write_text(json.dumps(profile))
    (directory / 'compatibility.json').write_text(json.dumps(config))
    subprocess.run(['python3', str(directory / 'scripts/build.py')], check=True)
    return directory / 'build/apps/launcherx-local.apk'


def main():
    guard()
    zero_accounts()
    version = int(re.search(r'versionCode=(\d+)', shell('dumpsys package ' + TARGET))[1])
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
        cases = [('dex', 'Code mismatch: classes.dex')]
        if version > 837595:
            with tempfile.TemporaryDirectory(prefix='launcherx-heuristic-') as directory:
                matched = negative_apk(Path(directory), 'heuristic')
                basic_fixture()
                print(adb('install', '--no-incremental', '-r', matched), flush=True)
                adb('logcat', '-c')
                start_home('setup-heuristic-home')
                logs = adb('logcat', '-d')
                assert 'compatibility=heuristic' in logs and 'Patch refused' not in logs
                (ARTIFACTS / 'setup-heuristic-logcat.txt').write_text(logs)
                open_setup('setup-heuristic-status', 'Launcher matched automatically')
                checks.append('real launcher activates through the heuristic path with its exact profile withheld')
            cases.insert(0, ('heuristic-mismatch', 'Heuristic mismatch'))
        for kind, expected in cases:
            with tempfile.TemporaryDirectory(prefix='launcherx-negative-') as directory:
                bad = negative_apk(Path(directory), kind)
                basic_fixture()
                print(adb('install', '--no-incremental', '-r', bad), flush=True)
                adb('logcat', '-c')
                start_home('setup-refuse-' + kind + '-home', basic=True)
                logs = adb('logcat', '-d')
                assert 'Patch refused' in logs and expected in logs
                assert 'installed mode=apps' not in logs
                assert 'Home selected by the loaded module' not in logs
                (ARTIFACTS / ('setup-refuse-' + kind + '-logcat.txt')).write_text(logs)
                open_setup('setup-refuse-' + kind, expected)
                shell('input keyevent KEYCODE_DPAD_UP KEYCODE_DPAD_CENTER')
                deadline = time.monotonic() + 30
                while True:
                    nodes = hierarchy('setup-refuse-' + kind + '-channels')
                    if any('Cannot change app content: ' + expected in node.get('text', '') for node in nodes):
                        break
                    assert any('Reading app content choices' in node.get('text', '') for node in nodes)
                    assert time.monotonic() < deadline, expected
                    time.sleep(.5)
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
