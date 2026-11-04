#!/usr/bin/env python3
"""Generate conservative class fingerprints from a privately reviewed launcher APK."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('bindings', type=Path, help='Reviewed alias-to-class JSON, including indirect dependencies')
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    bindings = json.loads(args.bindings.read_text())
    reverse = {'L' + name.replace('.', '/') + ';': alias for alias, name in bindings.items()}
    if len(reverse) != len(bindings):
        raise SystemExit('Profile aliases must name distinct classes')
    with tempfile.TemporaryDirectory(prefix='launcherx-fingerprints-') as tmp:
        subprocess.run(['javac', '-d', tmp,
                        ROOT / 'module/src/local/launcherx/patch/DexFingerprint.java',
                        ROOT / 'scripts/java/FingerprintTool.java'], check=True)
        output = subprocess.check_output(['java', '-Xmx1g', '-cp', tmp,
                                          'local.launcherx.patch.FingerprintTool', str(args.apk),
                                          *bindings.values()], text=True)
    nodes = {}
    for line in output.splitlines():
        name, shape, code, refs = line.split('\t')
        nodes[reverse[name]] = {'name': bindings[reverse[name]], 'shape': shape, 'code': code,
                              'links': {str(i): reverse[ref] for i, ref in enumerate(refs.split(','))
                                        if ref in reverse}}
    lines = ['{', '  "format": 1,', '  "binding_version": 837595,', '  "classes": {']
    lines.extend('    ' + json.dumps(alias) + ': ' + json.dumps(node, separators=(',', ':'))
                 + (',' if index + 1 < len(nodes) else '')
                 for index, (alias, node) in enumerate(nodes.items()))
    args.output.write_text('\n'.join([*lines, '  }', '}']) + '\n')
    print(f'Generated {len(nodes)} fingerprints in {args.output}')


if __name__ == '__main__':
    main()
