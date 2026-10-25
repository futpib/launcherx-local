#!/usr/bin/env python3
"""Build a private, emulator-only Xiaomi gesture fixture from reviewed firmware."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

from build import API_SHA256, ROOT, run
from lab import SDK

SERVICES_SHA256 = 'd09f1610b9521b695a74f1703054e0adfd142ff2f5d94fd756ab29a31b4d57ca'


def build(services, apktool):
    assert hashlib.sha256(services.read_bytes()).hexdigest() == SERVICES_SHA256, 'Unreviewed firmware'
    api = ROOT / 'artifacts/xposed-api-82.jar'
    assert hashlib.sha256(api.read_bytes()).hexdigest() == API_SHA256
    output = ROOT / 'build/volume-fixture'
    shutil.rmtree(output, ignore_errors=True)
    for directory in ('classes', 'dex', 'original', 'adapted'):
        (output / directory).mkdir(parents=True)
    run('java', '-cp', apktool, 'com.android.tools.smali.baksmali.Main', 'disassemble',
        '--classes', 'Lcom/android/server/audio/MitvAudioService;,Lcom/android/server/audio/MitvAudioService$1;',
        '-o', output / 'original', str(services) + '/classes.dex')
    originals = sorted((output / 'original').rglob('*.smali'))
    assert len(originals) == 2
    # Only type descriptors change. Gesture instructions, constants and timer code stay original.
    for original in originals:
        text = original.read_text().replace('Lcom/android/server/audio/AudioService;',
                                           'Llocal/launcherx/fixture/AudioServiceBridge;')
        text = text.replace('Lcom/android/server/audio/MitvAudioService', 'Llocal/launcherx/fixture/MitvAudioService')
        (output / 'adapted' / original.name).write_text(text)
    run('java', '-cp', apktool, 'com.android.tools.smali.smali.Main', 'assemble',
        '-a', '34', '-o', output / 'xiaomi.dex', output / 'adapted')
    tools = SDK / 'build-tools/37.0.0'
    android = SDK / 'platforms/android-34/android.jar'
    source = ROOT / 'tests/volume'
    production = [ROOT / 'module/src/local/launcherx/patch' / (name + '.java')
                  for name in ('SystemAudio', 'HookRequest')]
    run('javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', android,
        '-classpath', f'{android}:{api}', '-d', output / 'classes', *production,
        *sorted((source / 'src').rglob('*.java')))
    run(tools / 'd8', '--min-api', '34', '--lib', android, '--classpath', api,
        '--output', output / 'dex', output / 'xiaomi.dex', *sorted((output / 'classes').rglob('*.class')))
    run(tools / 'aapt', 'package', '-f', '-M', source / 'AndroidManifest.xml', '-S', source / 'res',
        '-A', source / 'assets', '-I', android, '-F', output / 'unsigned.apk')
    with zipfile.ZipFile(output / 'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
        apk.write(output / 'dex/classes.dex', 'classes.dex')
    run(tools / 'zipalign', '-f', '4', output / 'unsigned.apk', output / 'aligned.apk')
    apk = output / 'volume-fixture.apk'
    run(tools / 'apksigner', 'sign', '--ks', ROOT / '.cache/local.keystore', '--ks-key-alias', 'local',
        '--ks-pass', 'pass:android', '--out', apk, output / 'aligned.apk')
    run(tools / 'apksigner', 'verify', apk)
    (output / 'receipt.json').write_text(json.dumps({
        'services_sha256': SERVICES_SHA256,
        'production_sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in production},
        'original_smali': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in originals},
        'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'adaptations': ['Relocated Xiaomi class descriptors', 'AudioServiceBridge delegates to actual emulator AudioService',
                        'Xiaomi analytics is a no-op', 'Fixture enters production registration after its own emulator guard'],
    }, indent=2) + '\n')
    return apk


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--services', required=True, type=Path)
    parser.add_argument('--apktool', type=Path, default=Path('/usr/share/android-apktool/apktool.jar'))
    args = parser.parse_args()
    print(build(args.services, args.apktool))
