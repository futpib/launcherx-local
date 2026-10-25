#!/usr/bin/env python3
"""Exercise original Xiaomi gesture bytecode and the optional hook in the guarded AVD."""
import json
import re
import time
import xml.etree.ElementTree as ET

from build import ROOT
from lab import adb, guard, shell, wait_boot

FIXTURE = 'local.launcherx.volumefixture'
SETTING = 'launcherx_test_volume_hook'
LEGACY_SETTING = 'launcherx_disable_double_volume_down_mute'
OUT = ROOT / 'artifacts/volume-gesture-smoke'
CHECKS = []


def nodes(name):
    remote = '/data/local/tmp/launcherx-volume-ui.xml'
    try:
        shell('uiautomator dump ' + remote)
        xml = shell('cat ' + remote)
        (OUT / (name + '.xml')).write_text(xml)
        return list(ET.fromstring(xml).iter('node'))
    finally:
        shell('rm -f ' + remote)


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    assert x2 > x1 and y2 > y1, node.attrib
    shell(f'input tap {(x1+x2)//2} {(y1+y2)//2}')
    time.sleep(.5)


def enable_fixture():
    shell('am force-stop org.lsposed.manager')
    shell('am start -W -n org.lsposed.manager/.ui.activity.MainActivity')
    for attempt in range(5):
        view = nodes('manager-' + str(attempt))
        tabs = [n for n in view if n.get('content-desc', '').startswith('Modules')]
        if tabs:
            tap(tabs[0])
            break
        shell('input keyevent KEYCODE_BACK')
    else:
        raise AssertionError('Cannot find LSPosed Modules tab')
    view = nodes('modules')
    tap(next(n for n in view if n.get('text') == 'LauncherX Xiaomi volume fixture'))
    view = nodes('fixture-scope')
    switch = next(n for n in view if n.get('class') == 'android.widget.Switch')
    if switch.get('checked') != 'true':
        tap(switch)
    view = nodes('fixture-enabled')
    assert any(n.get('text') == 'System Framework' for n in view), [n.attrib for n in view]
    rows = [n for n in view if any(c.get('text') == 'System Framework' for c in n.iter('node'))
            and len([c for c in n.iter('node') if c.get('class') == 'android.widget.CheckBox']) == 1]
    assert rows, 'System Framework scope row missing'
    checkbox = next(n for n in rows[-1].iter('node') if n.get('class') == 'android.widget.CheckBox')
    if checkbox.get('checked') != 'true':
        tap(checkbox)
    view = nodes('fixture-scope-confirmed')
    assert len([n for n in view if n.get('class') == 'android.widget.CheckBox' and n.get('checked') == 'true']) == 1


def state(name):
    dump = shell('dumpsys audio')
    (OUT / (name + '-audio.txt')).write_text(dump)
    block = re.search(r'- STREAM_MUSIC[^\n]*:\n(.*?)(?=\n- STREAM_|\Z)', dump, re.S)[1]
    muted = 'Muted: true' in block
    current = int(re.search(r'2 \(speaker\): (\d+)', block)[1])
    assert 'Devices: speaker' in block, block
    return {'muted': muted, 'volume': current}


def volume(index):
    before = state('before-reset')
    if before['muted']:
        shell('input keyevent 164')
    difference = index - before['volume']
    if difference > 0:
        shell('input keyevent ' + ' '.join(['24'] * difference))
    else:
        for _ in range(-difference):
            shell('input keyevent 25')
            time.sleep(.85)
    time.sleep(.9)
    result = state('after-reset')
    assert result == {'volume': index, 'muted': False}, (result, index)


def press(codes, name, muted, index):
    shell('input keyevent ' + ' '.join(map(str, codes)))
    time.sleep(.8)
    result = state(name)
    assert result == {'volume': index, 'muted': muted}, (name, result, index, muted)
    CHECKS.append(name)
    print(name, result, flush=True)


def boot_fixture(enabled):
    adb('reboot')
    wait_boot()
    log = adb('logcat', '-d', '-s', 'LauncherXVolumeFixture:I', 'LauncherXLocal:I', '*:S')
    (OUT / ('boot-enabled.txt' if enabled else 'boot-stock.txt')).write_text(log)
    assert 'Original Xiaomi gesture bytecode active in emulator system_server' in log, log
    assert 'Production hook enabled=' + str(enabled).lower() in log, log
    assert ('Disabled Xiaomi double volume-down mute' in log) == enabled, log


def restore_setting(name, value):
    shell('settings delete global ' + name if value == 'null'
          else 'settings put global ' + name + ' ' + value)


def main():
    guard()
    OUT.mkdir(parents=True, exist_ok=True)
    assert not shell('pm list packages ' + FIXTURE), 'Remove any prior fixture first'
    original = state('original')
    previous = shell('settings get global ' + SETTING)
    legacy = shell('settings get global ' + LEGACY_SETTING)
    for value in (previous, legacy):
        assert value in ('null', '0', '1'), value
    try:
        print(adb('install', '--no-incremental', '-r', ROOT / 'build/apps/launcherx-local.apk'), flush=True)
        shell('settings put global ' + SETTING + ' 0')
        print(adb('install', '--no-incremental', ROOT / 'build/volume-fixture/volume-fixture.apk'), flush=True)
        enable_fixture()
        boot_fixture(False)
        volume(10)
        press([25, 25], 'stock-double-mutes', True, 8)
        volume(10)
        shell('settings put global ' + SETTING + ' 1')
        shell('settings put global ' + LEGACY_SETTING + ' 0')
        boot_fixture(True)
        shell('am start -W -n local.launcherx.patch/.SetupActivity')
        time.sleep(2)
        view = nodes('simplified-setup')
        assert not any(n.get('class') == 'android.widget.Switch' for n in view)
        assert [n.get('text') for n in view if n.get('class') == 'android.widget.Button'] == ['Choose app content']
        CHECKS.append('setup has one app-content button and no volume toggle')
        volume(10)
        press([25, 25], 'hook-active-without-preference', False, 8)
        CHECKS.append('obsolete volume preference cannot disable the hook')
        volume(10)
        press([25], 'single-down', False, 9)
        press([24], 'single-up', False, 10)
        volume(10)
        press([25, 25, 25], 'fixed-triple', False, 7)
        volume(10)
        press([164], 'explicit-mute', True, 10)
        press([164], 'explicit-unmute', False, 10)
        shell('input keyevent --longpress 25')
        time.sleep(.8)
        hold = state('hold')
        assert not hold['muted'] and 0 < hold['volume'] < 10, hold
        CHECKS.append('held volume-down still lowers volume')
        boot_fixture(True)
        volume(10)
        press([25, 25], 'reboot-preserves-fix', False, 8)
        (OUT / 'hook-log.txt').write_text(adb('logcat', '-d', '-s', 'LauncherXLocal:I', '*:S'))
    except Exception:
        (OUT / 'failure-log.txt').write_text(adb('logcat', '-d', '-s', 'LauncherXVolumeFixture:V',
                                               'LauncherXLocal:V', 'MitvAudioService:V', 'AndroidRuntime:E', '*:S'))
        raise
    finally:
        if shell('pm list packages ' + FIXTURE):
            print(adb('uninstall', FIXTURE), flush=True)
        restore_setting(SETTING, previous)
        restore_setting(LEGACY_SETTING, legacy)
        time.sleep(1)
        adb('reboot')
        wait_boot()
        volume(original['volume'])
        if original['muted']:
            shell('input keyevent 164')
        assert state('restored') == original
        assert not shell('pm list packages ' + FIXTURE)
        assert shell('settings get global ' + SETTING) == previous
        assert shell('settings get global ' + LEGACY_SETTING) == legacy
        shell('input keyevent KEYCODE_HOME')
    release = json.loads((ROOT / 'build/apps/receipt.json').read_text())
    report = {'result': 'passed', 'module_version': release['version_name'], 'apk_sha256': release['sha256'],
              'checks': CHECKS, 'physical_tv_patch_tested': False,
              'fixture': json.loads((ROOT / 'build/volume-fixture/receipt.json').read_text()),
              'limitations': ['Emulator runs original Xiaomi gesture bytecode with an AOSP audio adapter.',
                              'A fixture-only setting selects the stock baseline or the production hook at boot.',
                              'Production firmware gate is not bypassed in the release APK.',
                              'This emulator run does not test the physical TV or remote.'],
              'cleanup': 'Fixture uninstalled; emulator rebooted; original volume, mute and settings restored.'}
    (ROOT / 'validation-volume-gesture.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
