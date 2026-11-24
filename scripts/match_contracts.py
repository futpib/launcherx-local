#!/usr/bin/env python3
"""Run the production DEX member-contract resolver on privately obtained APKs."""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def rows(profile):
    for alias, item in profile['classes'].items():
        yield ['class', alias, item.get('name', ''), item.get('parent', ''), str(int(item['interface']) if 'interface' in item else -1)]
        for value in item.get('strings', []):
            yield ['string', alias, value]
        for kind in ['field', 'method']:
            for index, member in enumerate(item.get(kind + 's', [])):
                row = [kind, alias, member.get('id', ''), member.get('name', ''), member['type'], str(int(member.get('static', False)))]
                if kind == 'method':
                    row.append(','.join(member['parameters']))
                yield row
                for value in member.get('strings', []):
                    yield ['method-string', alias, str(index), value]
                for position, target in member.get('type_references', {}).items():
                    yield ['type-reference', alias, str(index), position, target]
                for ref in member.get('references', []):
                    yield ['member-reference', alias, str(index), ref['owner'], ref['type'], str(ref['opcode']), ref.get('name', '')]
        for access in item.get('accesses', []):
            yield ['access', alias, access['id'], access['method'], access['owner'], access['type'],
                   str(access['opcode']), access.get('after', ''), str(access.get('first', False)).lower(), access.get('exclude', '')]


def write_rows(profile, path):
    path.write_text('\n'.join('\t'.join(base64.b64encode(cell.encode()).decode() for cell in row)
                              for row in rows(profile)) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apks', nargs='+', type=Path)
    parser.add_argument('--profile', type=Path, default=ROOT / 'module/assets/contracts.json')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    profile = json.loads(args.profile.read_text())
    if profile.get('format') != 1 or profile.get('binding_version') != 841183:
        parser.error('Unknown contract format or binding baseline')
    with tempfile.TemporaryDirectory(prefix='launcherx-contracts-') as directory:
        path = Path(directory)
        write_rows(profile, path / 'profile.tsv')
        subprocess.run(['javac', '-d', path,
                        ROOT / 'module/src/local/launcherx/patch/DexFingerprint.java',
                        ROOT / 'module/src/local/launcherx/patch/DexContracts.java',
                        ROOT / 'scripts/java/ContractMatchTool.java'], check=True)
        result = subprocess.run(['java', '-Xmx768m', '-cp', path, 'local.launcherx.patch.ContractMatchTool',
                                 path / 'profile.tsv', *args.apks], capture_output=True, text=True)
    report = {'matched': result.returncode == 0, 'classes': {}, 'members': {}, 'failure': result.stderr.strip()}
    for line in result.stdout.splitlines():
        kind, name, value = line.split('\t')
        report[kind + 'es' if kind == 'class' else 'members'][name] = value
    output = json.dumps(report, indent=2) + '\n'
    if args.output:
        args.output.write_text(output)
    else:
        print(output, end='')
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
