#!/usr/bin/env python3
"""Inspect private launcher APK candidates with the module's actual matcher; never approve a fuzzy match."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--profile', type=Path, default=ROOT / 'module/assets/compatibility-841183.json')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    profile = json.loads(args.profile.read_text())
    with tempfile.TemporaryDirectory(prefix='launcherx-match-') as directory:
        path = Path(directory)
        rows = path / 'profile.tsv'
        rows.write_text('\n'.join('\t'.join([alias, item['shape'], item['code'],
                        ','.join(index + ':' + target for index, target in item['links'].items()),
                        ','.join(sorted(item.get('methods', [])))])
                        for alias, item in profile['classes'].items()) + '\n')
        subprocess.run(['javac', '-d', path, ROOT / 'module/src/local/launcherx/patch/DexFingerprint.java',
                        ROOT / 'scripts/java/FingerprintMatchTool.java'], check=True)
        result = subprocess.run(['java', '-Xmx1g', '-cp', path, 'local.launcherx.patch.FingerprintMatchTool',
                                 rows, args.apk, '--diagnose'], capture_output=True, text=True, check=False)
    candidates, bindings = {}, {}
    for line in result.stdout.splitlines():
        if line.startswith('candidates\t'):
            _, alias, exact, shape = line.split('\t')
            exact = [name[1:-1].replace('/', '.') for name in exact.split(',') if name]
            shape = [name[1:-1].replace('/', '.') for name in shape.split(',') if name]
            candidates[alias] = {'reviewed_name': profile['classes'][alias]['name'],
                                 'exact': exact, 'layout_only_count': len(set(shape) - set(exact)),
                                 'layout_only_examples': sorted(set(shape) - set(exact))[:10]}
        elif '=' in line:
            alias, name = line.split('=', 1)
            bindings[alias] = name
    report = {'binding_version': profile['binding_version'], 'matched': result.returncode == 0,
              'bindings': bindings, 'candidates': dict(sorted(candidates.items())),
              'failure': result.stderr.strip(),
              'limits': 'DEX discovery only; signing certificate, resources and runtime behavior are not checked. '
                        'Layout-only candidates require review and must never activate hooks.'}
    text = json.dumps(report, indent=2) + '\n'
    if args.output:
        args.output.write_text(text)
    else:
        print(text, end='')
    raise SystemExit(0 if report['matched'] else 1)


if __name__ == '__main__':
    main()
