#!/usr/bin/env python3
"""Build emulator-only fixture APKs, never shipped in the module."""
import shutil
import zipfile

from build import ROOT, run
from lab import SDK


def build(kind='publisher'):
    if kind not in ('publisher', 'markets', 'remote', 'keys', 'probe'):
        raise ValueError(kind)
    tools = SDK / 'build-tools/37.0.0'
    android = SDK / 'platforms/android-34/android.jar'
    source = ROOT / 'tests' / kind
    output = ROOT / 'build' / kind
    shutil.rmtree(output, ignore_errors=True)
    for directory in ('classes', 'dex'):
        (output / directory).mkdir(parents=True)
    run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android,
        '-d', output / 'classes', *sorted((source / 'src').rglob('*.java')))
    run(tools / 'd8', '--min-api', '29', '--lib', android, '--output', output / 'dex',
        *sorted((output / 'classes').rglob('*.class')))
    run(tools / 'aapt', 'package', '-f', '-M', source / 'AndroidManifest.xml', '-I', android,
        '-F', output / 'unsigned.apk')
    with zipfile.ZipFile(output / 'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
        apk.write(output / 'dex/classes.dex', 'classes.dex')
    run(tools / 'zipalign', '-f', '4', output / 'unsigned.apk', output / 'aligned.apk')
    apk = output / (kind + '.apk')
    key = ROOT / ('.cache/probe.keystore' if kind == 'probe' else '.cache/local.keystore')
    if not key.exists() and kind == 'probe':
        run('keytool', '-genkeypair', '-keystore', key, '-alias', 'local', '-storepass', 'android',
            '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
            '-dname', 'CN=Unrelated LauncherX Test App')
    run(tools / 'apksigner', 'sign', '--ks', key, '--ks-key-alias', 'local',
        '--ks-pass', 'pass:android', '--out', apk, output / 'aligned.apk')
    return apk


if __name__ == '__main__':
    print(build())
