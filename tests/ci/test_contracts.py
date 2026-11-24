import copy
import json
from pathlib import Path
import subprocess
import sys
import unittest

import test_fingerprints as fingerprints
ROOT = fingerprints.ROOT

sys.path.insert(0, str(ROOT / 'scripts'))
from match_contracts import write_rows


class ContractTest(unittest.TestCase):
    fixture = fingerprints.FingerprintTest.fixture

    @classmethod
    def setUpClass(cls):
        fingerprints.FingerprintTest.setUpClass.__func__(cls)
        subprocess.run(['javac', '-cp', cls.host, '-d', cls.host,
                        ROOT / 'scripts/java/ContractMatchTool.java'], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def match(self, apk, classes, success=True):
        profile = self.path / 'contracts.tsv'
        write_rows({'classes': classes}, profile)
        result = subprocess.run(['java', '-cp', self.host, 'local.launcherx.patch.ContractMatchTool', profile, apk],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, result.stderr)
        return result.stdout + result.stderr

    def test_renamed_method_and_class_with_unrelated_code_and_fields(self):
        rule = {'root': {'strings': ['home.role'], 'fields': [{'name': 'state', 'type': 'I'}],
                         'methods': [{'id': 'render', 'type': 'V', 'parameters': ['Ljava/lang/String;']} ]}}
        for name, source in [
            ('before', 'public class a { int state; public void old(String x) { System.out.println("home.role" + x); } }'),
            ('after', 'public class b { int state; long unrelated; public void moved(String x) { state++; System.out.println("home.role" + x); } public void noise() throws Throwable { java.lang.invoke.MethodHandle h = null; h.invokeExact(); } }')]:
            apk = self.fixture('contract-' + name, {('a' if name == 'before' else 'b'): source})
            text = self.match(apk, rule)
            self.assertIn('member\troot.render\t' + ('old' if name == 'before' else 'moved'), text)
        for index, source in enumerate([
            'public class b { long state; public void moved(String x) { System.out.println("home.role"); } }',
            'public class b { int state; public void moved(int x) { System.out.println("home.role"); } }',
            'public class b { int state; public void moved(String x) { System.out.println("different.role"); } }',
            'public class b { int state; public void moved(String x) { System.out.println("home.role"); } public void decoy(String x) {} }']):
            self.match(self.fixture('contract-invalid-' + str(index), {'b': source}), rule, False)

    def test_reference_graph_refuses_missing_and_ambiguous_targets(self):
        classes = {'root': {'strings': ['root.anchor'], 'fields': [{'name': 'target', 'type': '@leaf'}]},
                   'leaf': {'fields': [{'name': 'value', 'type': 'I'}]}}
        apk = self.fixture('contract-graph', {
            'root': 'public class root { leaf target; public String label() { return "root.anchor"; } }',
            'leaf': 'public class leaf { int value; }', 'decoy': 'public class decoy { int value; }'})
        self.assertIn('class\tleaf\tleaf', self.match(apk, classes))
        missing = copy.deepcopy(classes)
        missing['root']['strings'] = ['absent.anchor']
        failure = self.match(apk, missing, False)
        self.assertIn('Contract mismatch: missing targets [root]', failure)
        self.assertNotIn('leaf=[', failure)
        wrong = copy.deepcopy(classes)
        wrong['root']['fields'][0]['type'] = '@missing'
        self.assertIn('Unknown contract alias', self.match(apk, wrong, False))
        disconnected = copy.deepcopy(classes)
        disconnected['root']['fields'] = []
        self.assertIn('Contract mismatch', self.match(apk, disconnected, False))

    def test_field_roles_follow_named_code_paths_and_refuse_ambiguity(self):
        classes = {
            'root': {'strings': ['root.anchor'], 'methods': [{'name': 'state', 'id': 'state', 'type': 'Ljava/util/Map;', 'parameters': []}]},
            'callback': {'fields': [{'name': 'owner', 'type': '@root'}], 'methods': [
                {'name': 'dispatch', 'id': 'dispatch', 'type': 'V', 'parameters': []}],
                'accesses': [{'id': 'buttons', 'method': 'dispatch', 'owner': '@root', 'type': 'Ljava/util/Map;',
                              'opcode': 84, 'exclude': 'root.state'}]}}
        for suffix, first, second, extra in [('old', 'a', 'b', ''), ('new', 'moved', 'renamed', 'long noise;')]:
            apk = self.fixture('access-' + suffix, {
                'root': 'public class root { java.util.Map ' + first + ', ' + second + '; ' + extra +
                        ' public java.util.Map state() { System.out.println("root.anchor"); return ' + first + '; } }',
                'callback': 'public class callback { root owner; public void dispatch() { System.out.println(owner.' + first + '); System.out.println(owner.' + second + '); } }'})
            self.assertIn('member\tcallback.buttons\t' + second, self.match(apk, classes))
        broken = copy.deepcopy(classes)
        del broken['callback']['accesses'][0]['exclude']
        self.assertIn('Ambiguous or missing field access', self.match(apk, broken, False))

    def test_anchored_constructor_field_survives_parameter_and_field_renaming(self):
        rule = {'root': {'methods': [{'id': 'init', 'name': '<init>', 'type': 'V', 'parameters': ['...'],
                                     'strings': ['pseudonymous']}], 'accesses': [
            {'id': 'flag', 'method': 'init', 'owner': '@root', 'type': 'Z', 'opcode': 92,
             'after': 'pseudonymous', 'first': True}]}}
        for suffix, field, extra in [('old', 'oldFlag', ''), ('new', 'movedFlag', ', int unused')]:
            apk = self.fixture('flag-' + suffix, {'a': 'public class a { boolean ' + field +
                '; public a(String account' + extra + ') { ' + field + ' = "pseudonymous".equals(account); } }'})
            self.assertIn('member\troot.flag\t' + field, self.match(apk, rule))

    def test_real_contract_is_not_version_or_obfuscated_class_whitelist(self):
        profile = json.loads((ROOT / 'module/assets/contracts.json').read_text())
        self.assertEqual(profile['format'], 1)
        self.assertGreaterEqual(len(profile['classes']), 80)
        for alias, rule in profile['classes'].items():
            self.assertNotIn('version_code', rule)
            if 'name' in rule:
                self.assertIn('/', rule['name'], alias)
        self.assertEqual(profile['classes']['isn']['accesses'][0]['after'], 'pseudonymous')


if __name__ == '__main__':
    unittest.main()
