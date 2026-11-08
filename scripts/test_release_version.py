import unittest
from release_version import release_version


class ReleaseVersionTest(unittest.TestCase):
    def test_versions_and_order(self):
        self.assertEqual(("1.0", 1_000_000), release_version("v1.0"))
        self.assertEqual(("1.2.3", 1_002_003), release_version("v1.2.3"))
        versions = ["v0.0.1", "v0.1", "v1.0", "v1.0.1", "v1.1", "v2.0"]
        codes = [release_version(v)[1] for v in versions]
        self.assertEqual(codes, sorted(set(codes)))

    def test_invalid_tags(self):
        for tag in ["1.0", "v1", "v01.0", "v1.0-rc1", "v1.1000", "v1.0.1000", "v0.0", "v2101.0", "v1.0\n", "v1.0;echo x"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release_version(tag)


if __name__ == "__main__":
    unittest.main()
