#!/usr/bin/env python3
"""Build the separate emulator-only TvContract test publisher, never shipped in the module."""
import shutil
import zipfile

from build import ROOT, run
from lab import SDK


def build():
    tools = SDK / 'build-tools/37.0.0'
    android = SDK / 'platforms/android-34/android.jar'
    source = ROOT / 'tests/publisher'
    output = ROOT / 'build/publisher'
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
    apk = output / 'publisher.apk'
    run(tools / 'apksigner', 'sign', '--ks', ROOT / '.cache/local.keystore', '--ks-key-alias', 'local',
        '--ks-pass', 'pass:android', '--out', apk, output / 'aligned.apk')
    return apk


if __name__ == '__main__':
    print(build())
