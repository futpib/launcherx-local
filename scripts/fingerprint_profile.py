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
    parser.add_argument('--binding-version', type=int, default=837595)
    parser.add_argument('--methods', type=Path, help='Reviewed alias-to-method-name lists; other classes stay whole-class')
    args = parser.parse_args()
    bindings = json.loads(args.bindings.read_text())
    methods = json.loads(args.methods.read_text()) if args.methods else {}
    if not methods.keys() <= bindings.keys() or any(not names or len(names) != len(set(names)) for names in methods.values()):
        raise SystemExit('Method contracts must name known aliases and nonempty distinct methods')
    reverse = {'L' + name.replace('.', '/') + ';': alias for alias, name in bindings.items()}
    if len(reverse) != len(bindings):
        raise SystemExit('Profile aliases must name distinct classes')
    with tempfile.TemporaryDirectory(prefix='launcherx-fingerprints-') as tmp:
        subprocess.run(['javac', '-d', tmp,
                        ROOT / 'module/src/local/launcherx/patch/DexFingerprint.java',
                        ROOT / 'scripts/java/FingerprintTool.java'], check=True)
        output = subprocess.check_output(['java', '-Xmx1g', '-cp', tmp,
                                          'local.launcherx.patch.FingerprintTool', str(args.apk),
                                          *(name + ('#' + ','.join(sorted(methods[alias])) if alias in methods else '')
                                            for alias, name in bindings.items())], text=True)
    nodes = {}
    for line in output.splitlines():
        name, shape, code, refs = line.split('\t')
        nodes[reverse[name]] = {'name': bindings[reverse[name]], 'shape': shape, 'code': code,
                              'links': {str(i): reverse[ref] for i, ref in enumerate(refs.split(','))
                                        if ref in reverse}}
        if reverse[name] in methods:
            nodes[reverse[name]]['methods'] = sorted(methods[reverse[name]])
    lines = ['{', f'  "format": {2 if methods else 1},', f'  "binding_version": {args.binding_version},', '  "classes": {']
    lines.extend('    ' + json.dumps(alias) + ': ' + json.dumps(node, separators=(',', ':'))
                 + (',' if index + 1 < len(nodes) else '')
                 for index, (alias, node) in enumerate(nodes.items()))
    args.output.write_text('\n'.join([*lines, '  }', '}']) + '\n')
    print(f'Generated {len(nodes)} fingerprints in {args.output}')


if __name__ == '__main__':
    main()
