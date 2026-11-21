"""semver.py 的单元测试：python scripts/test_semver.py"""

import unittest

from semver import compare, is_prerelease, parse


class SemVerTest(unittest.TestCase):
    def test_stable_beats_same_core_prerelease(self):
        self.assertEqual(compare("2.5.2", "2.5.2-beta1"), 1)
        self.assertEqual(compare("2.5.2-beta1", "2.5.2"), -1)

    def test_beta_sequence(self):
        self.assertEqual(compare("2.5.2-beta2", "2.5.2-beta1"), 1)
        self.assertEqual(compare("2.5.2-beta1", "2.5.2-beta2"), -1)

    def test_rc_after_beta(self):
        self.assertEqual(compare("2.5.2-rc1", "2.5.2-beta9"), 1)
        self.assertEqual(compare("2.5.2-rc1", "2.5.2-rc2"), -1)

    def test_numeric_prerelease_component(self):
        self.assertEqual(compare("2.5.2-beta.10", "2.5.2-beta.2"), 1)
        # 数字标识符小于字母标识符
        self.assertEqual(compare("2.5.2-1", "2.5.2-alpha"), -1)

    def test_shorter_prerelease_is_smaller(self):
        self.assertEqual(compare("2.5.2-beta", "2.5.2-beta.1"), -1)

    def test_core_versions(self):
        self.assertEqual(compare("2.5.3-beta1", "2.5.2"), 1)
        self.assertEqual(compare("2.6.0", "2.5.9"), 1)
        self.assertEqual(compare("2.5.1", "2.5.2"), -1)

    def test_equal_ignores_v_prefix(self):
        self.assertEqual(compare("2.5.2-beta1", "v2.5.2-beta1"), 0)

    def test_is_prerelease(self):
        self.assertTrue(is_prerelease("2.5.2-beta1"))
        self.assertFalse(is_prerelease("2.5.2"))

    def test_parse_rejects_garbage(self):
        for bad in ("", "2.5", "v", "2.5.2.1", "2.x.0"):
            with self.assertRaises(ValueError):
                parse(bad)

    def test_parse_core(self):
        v = parse("v2.5.2-beta1")
        self.assertEqual(v.core, (2, 5, 2))
        self.assertEqual(v.prerelease, ("beta1",))


class BaseBumpTest(unittest.TestCase):
    """规则 7：上一个版本还是预发布时，不许换版本基线（见 docs/RELEASE-SPEC.md 2.5）。"""

    def setUp(self):
        from check_version import check_base_bump

        self.f = check_base_bump

    def test_same_base_beta_allowed(self):
        self.assertEqual(self.f("2.5.5-beta10", ["2.5.5-beta9", "2.5.5-beta8"]), [])

    def test_base_bump_blocked_while_previous_is_prerelease(self):
        # 用户点名的例子：2.5.5-beta10 之后不能是 2.5.6-beta1
        self.assertTrue(self.f("2.5.6-beta1", ["2.5.5-beta10", "2.5.5-beta9"]))

    def test_minor_bump_blocked(self):
        self.assertTrue(self.f("2.6.0-beta1", ["2.5.5-beta9"]))

    def test_base_bump_allowed_after_formal_release(self):
        self.assertEqual(self.f("2.6.0-beta1", ["2.5.5", "2.5.5-beta10"]), [])

    def test_formal_after_beta_line_same_base(self):
        self.assertEqual(self.f("2.5.5", ["2.5.5-beta10"]), [])

    def test_user_request_bypass(self):
        self.assertEqual(self.f("2.6.0-beta1", ["2.5.5-beta10"], allow=True), [])

    def test_rollback_blocked(self):
        self.assertTrue(self.f("2.5.5-beta10", ["2.6.0-beta1", "2.5.5-beta9"]))

    def test_new_version_not_in_changelog_uses_newest(self):
        self.assertEqual(self.f("2.6.0-beta2", ["2.6.0-beta1", "2.5.5"]), [])

    def test_empty_history(self):
        self.assertEqual(self.f("2.6.0-beta1", []), [])


if __name__ == "__main__":
    unittest.main()
