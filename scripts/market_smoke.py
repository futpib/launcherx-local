#!/usr/bin/env python3
"""Exercise the native Add apps tile and Android store chooser in the guarded AVD."""
import hashlib
import csv
import io
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image, ImageOps

from build_publisher import build
from lab import ARTIFACTS, ROOT, adb, guard, root, shell
from smoke import HOME, TARGET, fresh_home, resumed, screen

PACKAGE = 'local.launcherx.markets'
PLAY = 'com.android.vending'
STORES = [('org.fdroid.fdroid', 'market-fdroid.apk'), ('com.aurora.store', 'market-aurora.apk')]
QUERY = ('cmd package query-activities --brief --components -a android.intent.action.MAIN '
         '-c android.intent.category.APP_MARKET')


def inspect(name, expected=(), absent=()):
    screen(name)
    source = ARTIFACTS / (name + '.png')
    if name == 'market-empty':
        with Image.open(source) as image:
            width, height = image.size
            crop = image.convert('RGB').crop((int(width * .24), int(height * .35),
                                             int(width * .76), int(height * .65)))
            source = ARTIFACTS / (name + '-ocr.png')
            ImageOps.invert(crop).save(source)
    text = subprocess.check_output(['tesseract', str(source),
                                    'stdout', '--psm', '6'], text=True, stderr=subprocess.DEVNULL)
    (ARTIFACTS / (name + '.txt')).write_text(text)
    for value in expected:
        assert value in text, (value, text)
    for value in absent:
        assert value not in text, (value, text)
    return text


def open_markets(name, count):
    shell('input keyevent KEYCODE_HOME')
    time.sleep(1)
    # Normalize remote focus, then move to the last tile: native Add apps.
    shell('input keyevent ' + ' '.join(['KEYCODE_DPAD_UP'] * 6 + ['KEYCODE_DPAD_DOWN']))
    shell('input keyevent ' + ' '.join(['KEYCODE_DPAD_RIGHT'] * 24))
    shell('input keyevent KEYCODE_DPAD_CENTER')
    time.sleep(2)
    logs = adb('logcat', '-d', '-s', 'LauncherXLocal:I')
    counts = re.findall(r'opening APP_MARKET chooser: (\d+) activities', logs)
    assert counts and int(counts[-1]) == count, logs
    state = resumed('com.android.intentresolver' if count > 1 else HOME if count == 0 else PLAY)
    (ARTIFACTS / (name + '-activity.txt')).write_text(state)


def select(index, package, name):
    if index:
        shell('input keyevent ' + ' '.join(['KEYCODE_DPAD_RIGHT'] * index))
    shell('input keyevent KEYCODE_DPAD_CENTER')
    time.sleep(2)
    state = resumed(package)
    (ARTIFACTS / (name + '-activity.txt')).write_text(state)
    # Android's chooser forwards the original caller identity to the selected app.
    assert 'launchedFromPackage=' + TARGET in state, state[:4000]
    screen(name)


def receipt(component, name):
    inspect(name, [PACKAGE + '.' + component])
    logs = adb('logcat', '-d', '-s', 'MarketFixture:I')
    (ARTIFACTS / (name + '-receipt.txt')).write_text(logs)
    last = logs.splitlines()[-1]
    for value in (PACKAGE + '.' + component, 'android.intent.action.MAIN',
                  'android.intent.category.APP_MARKET'):
        assert value in last, last


def store_index(name, label):
    # The system can rank choices using history. Read the actual visible order,
    # without attaching accessibility inspection to the paused launcher.
    source = ARTIFACTS / (name + '.png')
    tsv = subprocess.check_output(['tesseract', str(source), 'stdout', '--psm', '6', 'tsv'],
                                  text=True, stderr=subprocess.DEVNULL)
    with Image.open(source) as image:
        width, height = image.size
    labels = ('Google', 'Aurora', 'F-Droid')
    words = [word for word in csv.DictReader(io.StringIO(tsv), delimiter='\t')
             if word['text'] in labels and width * .28 < int(word['left']) < width * .72
             and int(word['top']) < height * .25]
    order = [word['text'] for word in sorted(words, key=lambda word: int(word['left']))]
    assert sorted(order) == sorted(labels), order
    return order.index(label)


def main():
    guard()
    apk = ROOT / 'build/apps/launcherx-local.apk'
    fixture = build('markets')
    # This test removes only its own fixture and the two initially absent store apps.
    for package, filename in STORES:
        assert package not in shell('pm list packages ' + package), 'Use a clean emulator fixture'
        assert (ARTIFACTS / filename).is_file(), 'Download the store APKs described in docs/lab.md'
    if PACKAGE in shell('pm list packages ' + PACKAGE):
        print(adb('uninstall', PACKAGE), flush=True)
    initial_handlers = shell(QUERY)
    assert initial_handlers.startswith(PLAY + '/') and '\n' not in initial_handlers, initial_handlers
    play_state = re.search(r'User 0:.*?enabled=(\d+)', shell('dumpsys package ' + PLAY)).group(1)
    assert play_state in ('0', '1'), play_state
    restore_play = 'default-state' if play_state == '0' else 'enable'
    installed = []
    checks = []
    store_versions = {}
    print(adb('install', '--no-incremental', '-r', apk), flush=True)
    adb('logcat', '-c')
    fresh_home()
    pid = shell('pidof ' + TARGET)
    try:
        open_markets('market-single', 1)
        inspect('market-single', ['Google Play', 'Sign in'])
        shell('input keyevent KEYCODE_BACK')
        time.sleep(1)
        resumed(HOME)
        checks.append('one store opens directly; Back returns to Home')

        print(adb('install', '--no-incremental', '-r', fixture), flush=True)
        installed.append(PACKAGE)
        open_markets('market-many', 5)
        inspect('market-many', ['Alpha', 'Beta', 'Delta', 'Gamma'],
                ['Disabled', 'Private', 'Protected', 'Ordinary'])
        select(1, PACKAGE, 'market-alpha')
        receipt('Alpha', 'market-alpha')
        checks.append('five choices include a handler without DEFAULT; disabled, private, permission-gated and non-market activities excluded')

        open_markets('market-many-again', 5)
        select(4, PACKAGE, 'market-gamma')
        receipt('Gamma', 'market-gamma')
        checks.append('DPAD can select the fifth choice; actual receiver confirms component, action and category')

        root('pm disable --user 0 ' + PACKAGE + '/.Alpha')
        open_markets('market-disabled', 4)
        inspect('market-disabled', ['Beta', 'Delta', 'Gamma'], ['Alpha'])
        shell('input keyevent KEYCODE_BACK')
        time.sleep(1)
        resumed(HOME)
        assert shell('pidof ' + TARGET) == pid
        checks.append('install and component disabling refresh choices without restarting Home; Back cancels chooser')

        print(adb('uninstall', PACKAGE), flush=True)
        installed.remove(PACKAGE)
        root('pm disable-user --user 0 ' + PLAY)
        open_markets('market-empty', 0)
        inspect('market-empty', ['No app stores are available'])
        shell('input keyevent KEYCODE_DPAD_CENTER')
        time.sleep(.5)
        resumed(HOME)
        assert shell('pidof ' + TARGET) == pid
        checks.append('uninstall and app disabling refresh live; zero stores shows a dismissible explanation')
        root('pm ' + restore_play + ' --user 0 ' + PLAY)

        for package, filename in STORES:
            print(adb('install', '--no-incremental', ARTIFACTS / filename), flush=True)
            installed.append(package)
            dump = shell('dumpsys package ' + package)
            (ARTIFACTS / ('market-' + package + '-package.txt')).write_text(dump)
            store_versions[package] = {
                'version_name': re.search(r'versionName=(\S+)', dump).group(1),
                'apk_sha256': hashlib.sha256((ARTIFACTS / filename).read_bytes()).hexdigest(),
            }
        handlers = shell(QUERY)
        (ARTIFACTS / 'market-real-handlers.txt').write_text(handlers)
        assert all(package in handlers for package, _ in STORES), handlers
        open_markets('market-real-chooser', 3)
        inspect('market-real-chooser', ['Aurora', 'F-Droid', 'Google'])
        select(store_index('market-real-chooser', 'Aurora'), 'com.aurora.store', 'market-real-aurora')
        inspect('market-real-aurora', ['Aurora Store', 'Source code'])
        open_markets('market-real-chooser-again', 3)
        inspect('market-real-chooser-again', ['Aurora', 'F-Droid', 'Google'])
        select(store_index('market-real-chooser-again', 'F-Droid'), 'org.fdroid.fdroid', 'market-real-fdroid')
        inspect('market-real-fdroid', ['F-Droid'])
        assert shell('pidof ' + TARGET) == pid
        checks.append('real F-Droid and Aurora appear automatically and both launch through DPAD selection')
        assert 'Accounts: 0' in shell('dumpsys account')
        logs = adb('logcat', '-d')
        (ARTIFACTS / 'market-logcat.txt').write_text(logs)
        for error in ('FATAL EXCEPTION', 'Patch refused', 'Cannot open app stores'):
            assert error not in logs, error
        checks.append('zero Google accounts and no fatal exceptions or picker errors')
    finally:
        root('pm ' + restore_play + ' --user 0 ' + PLAY)
        for package in reversed(installed):
            print(adb('uninstall', package), flush=True)
        shell('input keyevent KEYCODE_HOME')
        time.sleep(2)
        resumed(HOME)
        assert shell(QUERY) == initial_handlers
        for package in [PACKAGE, *(p for p, _ in STORES)]:
            assert package not in shell('pm list packages ' + package)
        assert re.search(r'User 0:.*?enabled=(\d+)', shell('dumpsys package ' + PLAY)).group(1) == play_state
        (ARTIFACTS / 'market-final-logcat.txt').write_text(adb('logcat', '-d'))
    result = {
        'result': 'passed',
        'module_version': ET.parse(ROOT / 'module/AndroidManifest.xml').getroot().get(
            '{http://schemas.android.com/apk/res/android}versionName'),
        'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'fixture_sha256': hashlib.sha256(fixture.read_bytes()).hexdigest(),
        'checks': checks, 'stores': store_versions, 'accounts': 0, 'physical_tv_tested': False,
        'fixture': 'isolated Google TV API 34 emulator; original launcher; actual system chooser and DPAD',
        'cleanup': 'fixture and real store apps removed; original Play Store state and handler list restored',
        'limitations': 'Store entry screens tested; store onboarding, downloads and installation inside stores not exercised.',
    }
    (ARTIFACTS / 'market-result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2), flush=True)


if __name__ == '__main__':
    main()
