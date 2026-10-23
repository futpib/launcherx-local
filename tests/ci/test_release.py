import datetime
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scripts'))
from build import ANDROID, ROOT, manifest_version, prepare_manifest
from release_version import next_version


class ReleaseVersionTest(unittest.TestCase):
    def test_first_release_is_newer_than_local_install(self):
        value = next_version([], 3, datetime.datetime(2026, 10, 23, 12, 34, tzinfo=datetime.timezone.utc))
        self.assertEqual(value, {'name': '26.10.23.12.34', 'code': 4, 'tag': '26.10.23.12.34+4'})

    def test_all_releases_count_even_if_out_of_order(self):
        value = next_version(['26.10.23.12.34+11', 'unrelated', 'v0.3', '26.10.21.00.00+97'], 4)
        self.assertEqual(value['code'], 98)

    def test_manifest_floor_wins_over_older_releases(self):
        self.assertEqual(next_version(['26.10.21.00.00+5'], 20)['code'], 21)

    def test_version_exhaustion_fails(self):
        with self.assertRaises(ValueError):
            next_version(['26.10.21.00.00+2100000000'], 3)

    def test_version_override_does_not_change_source_or_permissions(self):
        source = ROOT / 'module/AndroidManifest.xml'
        original = source.read_bytes()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'AndroidManifest.xml'
            self.assertEqual(prepare_manifest(source, output, '26.10.23.12.34', 100), ('26.10.23.12.34', 100))
            self.assertEqual(manifest_version(output), ('26.10.23.12.34', 100))
            old_tree, new_tree = ET.fromstring(original), ET.parse(output).getroot()
            for key in ('versionName', 'versionCode'):
                new_tree.set(f'{{{ANDROID}}}{key}', old_tree.get(f'{{{ANDROID}}}{key}'))
            self.assertEqual(ET.tostring(old_tree), ET.tostring(new_tree))
        self.assertEqual(source.read_bytes(), original)

    def test_invalid_codes_fail_before_packaging(self):
        with tempfile.TemporaryDirectory() as directory:
            for code in (0, -1, 2100000001):
                with self.subTest(code=code), self.assertRaises(ValueError):
                    prepare_manifest(ROOT / 'module/AndroidManifest.xml', Path(directory) / 'manifest.xml', code=code)


if __name__ == '__main__':
    unittest.main()
