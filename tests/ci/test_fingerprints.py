import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))


class FingerprintTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='launcherx-dex-test-')
        cls.path = Path(cls.temp.name)
        cls.host = cls.path / 'host'
        subprocess.run(['javac', '-d', cls.host,
                        ROOT / 'module/src/local/launcherx/patch/DexFingerprint.java',
                        *sorted((ROOT / 'scripts/java').glob('Fingerprint*Tool.java'))], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def fixture(self, name, sources):
        path = self.path / name
        path.mkdir()
        files = []
        for classname, source in sources.items():
            file = path / (classname + '.java')
            file.write_text(source)
            files.append(file)
        classes = path / 'classes'
        subprocess.run(['javac', '-g:none', '--release', '8', '-d', classes, *files], check=True)
        apk = path / 'fixture.zip'
        subprocess.run([SDK / 'build-tools/37.0.0/d8', '--min-api', '29', '--output', apk,
                        *classes.glob('*.class')], check=True, capture_output=True)
        return apk

    def fingerprints(self, apk, names):
        output = subprocess.check_output(['java', '-cp', self.host,
                                          'local.launcherx.patch.FingerprintTool', apk, *names], text=True)
        return {row[0]: row[1:] for line in output.splitlines() if (row := line.split('\t'))}

    def match(self, apk, rows, success=True):
        profile = self.path / 'profile.tsv'
        profile.write_text('\n'.join('\t'.join(row) for row in rows) + '\n')
        result = subprocess.run(['java', '-cp', self.host, 'local.launcherx.patch.FingerprintMatchTool',
                                 profile, apk], text=True, capture_output=True)
        self.assertEqual(result.returncode == 0, success, result.stderr)
        return result.stdout + result.stderr

    def test_renaming_and_unrelated_code_are_accepted_but_changes_are_refused(self):
        source = 'public class NAME { public int value(int x) { return x + 3; } }'
        old = self.fixture('old', {'a': source.replace('NAME', 'a')})
        shape, code, _ = self.fingerprints(old, ['a'])['La;']
        rows = [['target', shape, code, '']]
        new = self.fixture('renamed', {'b': source.replace('NAME', 'b'),
                                      'noise': 'public class noise { public String extra() { return "unrelated"; } }'})
        self.assertIn('target=b', self.match(new, rows))
        changed = self.fixture('changed', {'b': source.replace('NAME', 'b').replace('+ 3', '+ 4')})
        self.assertIn('Heuristic mismatch', self.match(changed, rows, False))
        ambiguous = self.fixture('ambiguous', {n: source.replace('NAME', n) for n in ['b', 'c']})
        self.assertIn('Ambiguous heuristic', self.match(ambiguous, rows, False))

    def test_reference_graph_disambiguates_identical_classes(self):
        old = self.fixture('graph-old', {'a': 'public class a { public c[] target; }', 'c': 'public class c {}'})
        values = self.fingerprints(old, ['a', 'c'])
        shape, code, refs = values['La;']
        edge = refs.split(',').index('Lc;')
        rows = [['root', shape, code, str(edge) + ':leaf'], ['leaf', *values['Lc;'][:2], '']]
        new = self.fixture('graph-new', {'b': 'public class b { public d[] target; }',
                                        'd': 'public class d {}', 'e': 'public class e {}'})
        result = self.match(new, rows)
        self.assertIn('root=b', result)
        self.assertIn('leaf=d', result)

    def test_strings_static_values_and_exception_handlers_affect_fingerprint(self):
        source = ('public class NAME { public static final int NUMBER = 7; '
                  'public String value(int x) { try { return "anchor" + (3 / x); } '
                  'catch (ArithmeticException e) { return "fallback"; } } }')
        old = self.fixture('semantics-old', {'a': source.replace('NAME', 'a')})
        shape, code, _ = self.fingerprints(old, ['a'])['La;']
        for index, changed in enumerate([source.replace('"anchor"', '"changed"'),
                                         source.replace('= 7', '= 8'),
                                         source.replace('ArithmeticException', 'RuntimeException')]):
            new = self.fixture('semantics-' + str(index), {'b': changed.replace('NAME', 'b')})
            self.match(new, [['target', shape, code, '']], False)

    def test_malformed_dex_is_rejected(self):
        apk = self.path / 'malformed.zip'
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('classes.dex', b'dex\n035\0' + b'\0' * 104)
        result = subprocess.run(['java', '-cp', self.host, 'local.launcherx.patch.FingerprintTool', apk, 'a'],
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Unsupported DEX header', result.stderr)

    def test_duplicate_classes_are_rejected(self):
        original = self.fixture('duplicate-source', {'a': 'public class a {}'})
        with zipfile.ZipFile(original) as archive:
            dex = archive.read('classes.dex')
        apk = self.path / 'duplicate.zip'
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('classes.dex', dex)
            archive.writestr('classes2.dex', dex)
        result = subprocess.run(['java', '-cp', self.host, 'local.launcherx.patch.FingerprintTool', apk, 'a'],
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Duplicate DEX class', result.stderr)

    def test_reviewed_versions_and_complete_profile_are_retained(self):
        profiles = json.loads((ROOT / 'compatibility.json').read_text())
        versions = [profiles, *profiles['additional_versions']]
        self.assertEqual({702401, 835645, 837595}, {item['version_code'] for item in versions})
        profile = json.loads((ROOT / 'module/assets/compatibility-837595.json').read_text())
        self.assertEqual(profile['binding_version'], 837595)
        self.assertGreaterEqual(len(profile['classes']), 86)
        self.assertEqual(profile['classes']['ambient_account_provider']['name'],
                         'com.google.android.apps.tv.launcherx.coreservices.account.AccountModeContentProvider')
        for value in profile['classes'].values():
            self.assertEqual(len(value['code']), 64)
            self.assertTrue(set(value['links'].values()) <= profile['classes'].keys())
