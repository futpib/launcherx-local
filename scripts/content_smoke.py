#!/usr/bin/env python3
"""Test real TvContract rows and native DPAD links in the guarded emulator."""
import hashlib
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

from build_publisher import build
from lab import ARTIFACTS, ROOT, adb, guard, root, shell, snapshot, wait_boot
from smoke import TARGET, HOME, fresh_home, resumed, screen
from PIL import Image, ImageOps

PACKAGE = 'local.launcherx.fixture'


def publish(operation):
    output = shell('am broadcast -n ' + PACKAGE + '/.Publisher --es operation ' + operation)
    assert 'data="fixture:' + operation + ':ok"' in output, output


def query(table, column):
    return root('content query --uri content://android.media.tv/' + table
                + ' --projection _id:package_name:browsable:' + column)


def mark(table, label, value, column='title'):
    rows = query(table, column)
    matches = [line for line in rows.splitlines() if 'package_name=' + PACKAGE in line
               and column + '=' + label in line]
    assert len(matches) == 1, rows
    row_id = re.search(r'_id=(\d+)', matches[0]).group(1)
    root('content update --uri content://android.media.tv/' + table + '/' + row_id
         + ' --bind browsable:i:' + str(value))


def inspect(name, expected=(), absent=(), pixels=False):
    deadline = time.monotonic() + 35
    while True:
        if pixels or 'chooser' in name or '-link' in name:
            # Attaching UIAutomator to another activity can crash the paused stock Home.
            # Read the real screen pixels for these steps instead.
            screen(name)
            source = ARTIFACTS / (name + '.png')
            crop_bounds = {
                'content-chooser': (.24, .30, .76, .70),
                'content-chooser-checked': (.24, .30, .76, .70),
                'content-deleted-watch': (.045, .44, .73, .52),
                'content-deleted-preview': (.045, .83, .73, .92),
            }
            if name in crop_bounds:
                with Image.open(source) as image:
                    width, height = image.size
                    left, top, right, bottom = crop_bounds[name]
                    crop = image.convert('RGB').crop((int(width * left), int(height * top),
                                                     int(width * right), int(height * bottom)))
                    source = ARTIFACTS / (name + '-ocr.png')
                    ImageOps.invert(crop).save(source)
            xml = subprocess.check_output(['tesseract', str(source), 'stdout', '--psm', '6'],
                                          stderr=subprocess.DEVNULL, text=True)
            (ARTIFACTS / (name + '.txt')).write_text(xml)
        else:
            try:
                snapshot(name)
            except RuntimeError:
                # Boot completion can precede the first inspectable window.
                if time.monotonic() >= deadline:
                    raise
                time.sleep(1)
                continue
            xml = (ARTIFACTS / (name + '.xml')).read_text()
        if all(value in xml for value in expected) and not any(value in xml for value in absent):
            return xml
        if time.monotonic() >= deadline:
            raise AssertionError((name, expected, absent, xml))
        time.sleep(1)


def select(row, name, item=0):
    fresh_home()
    time.sleep(2)
    # No UIAutomator between these remote events: it resets Google's focus state.
    for _ in range(row):
        shell('input keyevent KEYCODE_DPAD_DOWN')
        time.sleep(.25)
    for _ in range(item):
        shell('input keyevent KEYCODE_DPAD_RIGHT')
        time.sleep(.25)
    screen(name + '-focus')
    shell('input keyevent KEYCODE_DPAD_CENTER')
    time.sleep(1)
    state = resumed(PACKAGE)
    assert 'launchedFromPackage=' + TARGET in state
    (ARTIFACTS / (name + '-activity.txt')).write_text(state)


def check_logs(name):
    logs = adb('logcat', '-d')
    (ARTIFACTS / (name + '-logcat.txt')).write_text(logs)
    hook_logs = '\n'.join(line for line in logs.splitlines() if 'LauncherXLocal' in line)
    for error in ['unavailable', 'Skipping', 'failed', 'Patch refused']:
        assert error not in hook_logs, hook_logs
    assert 'FATAL EXCEPTION' not in adb('logcat', '-d', '-s', 'AndroidRuntime:E')


def main():
    guard()
    ARTIFACTS.mkdir(exist_ok=True)
    apk = ROOT / 'build/apps/launcherx-local.apk'
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    fixture = build()
    print(adb('install', '--no-incremental', '-r', apk), flush=True)
    print(adb('install', '--no-incremental', '-r', fixture), flush=True)
    firewall = []
    checks = []
    try:
        publish('clear')
        adb('logcat', '-c')
        fresh_home()
        inspect('content-empty', ['Your apps', 'Local TV'], ['Continue watching', 'Fixture recommendations'])
        checks.append('empty providers keep apps-only Home')
        # A UIAutomator inspection can leave a stale accessibility focus in stock Android 14.
        # Restart after inspection, then perform each live mutation/navigation uninterrupted.
        fresh_home()

        # Provider records belong to a real installed app. The regular setup chooser approves
        # the test channel; root marks hidden fixture cards without constructing launcher rows.
        publish('seed')
        shell('am force-stop local.launcherx.patch')
        shell('am start -W -n local.launcherx.patch/.SetupActivity')
        time.sleep(2)
        shell('input keyevent ' + ' '.join(['KEYCODE_DPAD_UP'] * 5 + ['KEYCODE_DPAD_DOWN'] * 4))
        screen('content-chooser-focus')
        shell('input keyevent KEYCODE_DPAD_CENTER')
        inspect('content-chooser', ['App content on Home', 'Fixture recommendations'])
        shell('input keyevent KEYCODE_DPAD_CENTER')
        inspect('content-chooser-checked')
        shell('input keyevent KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER')
        inspect('content-chooser-saved', ['App content choices saved'])
        assert re.search(r'browsable=1, display_name=Fixture recommendations', query('channel', 'display_name'))
        checks.append('remote-controlled setup chooser enables only the selected preview channel')
        shell('input keyevent KEYCODE_HOME')
        mark('watch_next_program', 'Hidden watch card', 0)
        mark('preview_program', 'Hidden preview card', 0)
        bad = ['Hidden watch card', 'Invalid watch card', 'Hidden preview card',
               'Invalid preview card', 'Duplicate preview card', 'Hidden channel card']
        xml = inspect('content-populated', ['Continue watching', 'Local movie', 'Local episode', 'Local clip',
                      'Fixture recommendations', 'Local recommendation', 'Second recommendation'], bad)
        nodes = list(ET.fromstring(xml).iter('node'))
        assert sum(n.get('content-desc', '').startswith('Local movie,') for n in nodes) == 1
        checks.append('both native rows, artwork, episode metadata, hidden/invalid/duplicate filtering')

        select(2, 'content-watch')
        inspect('content-watch-link', ['fixture://play/watch-0', 'seek_ms=180000'])
        select(3, 'content-preview')
        inspect('content-preview-link', ['fixture://play/preview-0', 'seek_ms=0'])
        checks.append('DPAD opens both provider deep links with extras intact')
        select(2, 'content-clip', item=2)
        inspect('content-clip-link', ['fixture://play/watch-clip', 'seek_ms=60000'])
        checks.append('non-film Watch Next clip renders and opens through native resume card')

        fresh_home()
        time.sleep(2)
        pid = shell('pidof ' + TARGET)
        publish('update')
        time.sleep(2)
        assert shell('pidof ' + TARGET) == pid
        inspect('content-updated', ['Updated local movie', 'Updated recommendation'])
        assert shell('pidof ' + TARGET) == pid
        checks.append('provider updates refresh visible Home without a process restart')
        select(2, 'content-updated-watch')
        inspect('content-updated-watch-link', ['fixture://play/watch-0', 'seek_ms=420000'])
        checks.append('updated resume position and playback extras')

        # Refresh while Home is paused, then return through the real Home key.
        pid = shell('pidof ' + TARGET)
        publish('delete')
        shell('input keyevent KEYCODE_HOME')
        time.sleep(2)
        # UIAutomator can crash stock ViewRootImpl when the accessible card it last
        # focused has disappeared. Verify the remaining cards with pixels and DPAD.
        shell('input keyevent ' + ' '.join(['KEYCODE_DPAD_UP'] * 6 + ['KEYCODE_DPAD_DOWN'] * 2))
        inspect('content-deleted-watch', ['Local episode'], ['Updated local movie'], pixels=True)
        shell('input keyevent KEYCODE_DPAD_DOWN')
        inspect('content-deleted-preview', ['Second recommendation'],
                ['Updated recommendation', 'Duplicate preview card'], pixels=True)
        assert shell('pidof ' + TARGET) == pid
        checks.append('deletions made while away are reflected on Home resume')

        fresh_home()
        mark('channel', 'Fixture recommendations', 0, 'display_name')
        inspect('content-hidden-channel', ['Continue watching'], ['Fixture recommendations', 'Second recommendation'])
        fresh_home()
        mark('channel', 'Fixture recommendations', 1, 'display_name')
        inspect('content-restored-channel', ['Fixture recommendations', 'Second recommendation'])
        checks.append('channel visibility changes remove and restore rows live')

        # Block the launcher's network only, inside this emulator; local artwork remains real.
        uid = re.search(r'uid:(\d+)', shell('cmd package list packages -U ' + TARGET)).group(1)
        rule = '-m owner --uid-owner ' + uid + ' -m comment --comment launcherx-local-smoke -j REJECT'
        for command in ('iptables', 'ip6tables'):
            output = root(command + ' -I OUTPUT 1 ' + rule + ' && echo RULE_INSTALLED')
            assert 'RULE_INSTALLED' in output, output
            firewall.append((command, rule))
        fresh_home()
        inspect('content-offline', ['Local episode', 'Second recommendation'])
        select(2, 'content-offline-watch')
        inspect('content-offline-link', ['fixture://play/watch-1'])
        checks.append('rows and playback links work with launcher IPv4/IPv6 egress blocked')
        for command, rule in firewall[:]:
            root(command + ' -D OUTPUT ' + rule)
            firewall.remove((command, rule))

        shell('input keyevent KEYCODE_HOME')
        check_logs('content-before-reboot')
        adb('reboot')
        time.sleep(10)
        wait_boot()
        time.sleep(3)
        resumed(HOME)
        inspect('content-rebooted', ['Local episode', 'Second recommendation'])
        checks.append('populated rows survive emulator reboot')

        fresh_home()
        publish('clear')
        inspect('content-cleared', ['Your apps'], ['Continue watching', 'Fixture recommendations'])
        checks.append('deleting the last entries removes empty rows live')
        assert 'Accounts: 0' in shell('dumpsys account')
        check_logs('content')
        result = {'result': 'passed', 'module_version': ET.parse(ROOT / 'module/AndroidManifest.xml').getroot().get(
                      '{http://schemas.android.com/apk/res/android}versionName'),
                  'apk_sha256': digest,
                  'publisher_sha256': hashlib.sha256(fixture.read_bytes()).hexdigest(),
                  'accounts': 0, 'checks': checks, 'physical_tv_tested': False,
                  'fixture': 'isolated Google TV API 34 emulator; original launcher APK; real TvProvider test publisher; playback receiver verifies links, not video playback'}
        (ARTIFACTS / 'content-result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result, indent=2), flush=True)
    finally:
        (ARTIFACTS / 'content-final-logcat.txt').write_text(adb('logcat', '-d'))
        for command, rule in firewall:
            root(command + ' -D OUTPUT ' + rule)
        publish('clear')
        print(adb('uninstall', PACKAGE), flush=True)
        shell('input keyevent KEYCODE_HOME')
        time.sleep(2)
        resumed(HOME)
        for table, column in [('watch_next_program', 'title'), ('channel', 'display_name'), ('preview_program', 'title')]:
            assert PACKAGE not in query(table, column)
        assert PACKAGE not in shell('pm list packages ' + PACKAGE)


if __name__ == '__main__':
    main()
