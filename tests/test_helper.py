import json
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app" / "src" / "main" / "python"))

import beginner_helper  # noqa: E402
import practice_engine  # noqa: E402


class BeginnerHelperTests(unittest.TestCase):
    def assert_contract(self, result):
        self.assertEqual(set(result), {"title", "explanation", "next_step", "line", "fix"})
        for key in ("title", "explanation", "next_step"):
            self.assertTrue(result[key])
        self.assertTrue(result["line"] is None or isinstance(result["line"], int))
        if result["fix"] is not None:
            self.assertEqual(set(result["fix"]), {"label", "before", "after"})
            self.assertIsInstance(result["fix"]["before"], str)
            self.assertIsInstance(result["fix"]["after"], str)
            compile(result["fix"]["after"], "<test>", "exec")

    def test_success_is_honest_and_has_no_fix(self):
        result = beginner_helper.diagnose("print('မင်္ဂလာပါ')", None)
        self.assert_contract(result)
        self.assertIsNone(result["line"])
        self.assertIsNone(result["fix"])
        self.assertIn("logic", result["explanation"])

    def test_missing_colon_fix_and_full_source(self):
        code = "if True\n    print('မင်္ဂလာပါ')\n"
        result = beginner_helper.diagnose(code, {"type": "SyntaxError", "message": "expected ':'", "line": 1})
        self.assert_contract(result)
        self.assertEqual(result["fix"]["before"], code)
        self.assertEqual(result["fix"]["after"], "if True:\n    print('မင်္ဂလာပါ')\n")

    def test_colon_fix_preserves_comment_and_multiline_source(self):
        code = "# အစ\nif True  # လုပ်မယ်\n    print(\"ok\")\n"
        result = beginner_helper.diagnose(code, {"type": "SyntaxError", "message": "expected ':'", "line": 2})
        self.assertIsNotNone(result["fix"])
        self.assertEqual(result["fix"]["after"], "# အစ\nif True:  # လုပ်မယ်\n    print(\"ok\")\n")

    def test_colon_fix_rejects_string_and_comment_mentions(self):
        cases = [
            ('print("if x")\n', 1),
            ("# if x\nprint('x')\n", 1),
            ("value = 'for item'\n", 1),
        ]
        for code, line in cases:
            with self.subTest(code=code):
                result = beginner_helper.diagnose(code, {"type": "SyntaxError", "message": "bad", "line": line})
                self.assertIsNone(result["fix"])

    def test_python_literal_fixes_are_token_and_ast_aware(self):
        for old, new in (("true", "True"), ("false", "False"), ("none", "None")):
            with self.subTest(old=old):
                code = f"value = {old}\nprint(value)\n"
                result = beginner_helper.diagnose(code, {"type": "NameError", "message": f"name '{old}' is not defined", "line": 1})
                self.assertIsNotNone(result["fix"])
                self.assertIn(f"value = {new}", result["fix"]["after"])

    def test_literal_fix_does_not_touch_quotes_or_comments(self):
        for code in ('print("true")\n', "# true\nprint('x')\n", "text = 'false'\n"):
            with self.subTest(code=code):
                result = beginner_helper.diagnose(code, {"type": "NameError", "message": "name 'true' is not defined", "line": 1})
                self.assertIsNone(result["fix"])

    def test_known_typo_only_and_unknown_name_no_speculation(self):
        typo = beginner_helper.diagnose("pritn('ဟုတ်')\n", {"type": "NameError", "message": "name 'pritn' is not defined", "line": 1})
        self.assertEqual(typo["fix"]["after"], "print('ဟုတ်')\n")
        unknown = beginner_helper.diagnose("print(missing_value)\n", {"type": "NameError", "message": "name 'missing_value' is not defined", "line": 1})
        self.assertIsNone(unknown["fix"])
        numeric = beginner_helper.diagnose("print(pritn + 1)\n", {"type": "NameError", "message": "name 'pritn' is not defined", "line": 1})
        self.assertIsNone(numeric["fix"])

    def test_error_families_have_guidance_without_inventing_values(self):
        for error_type in ("IndentationError", "TabError", "TypeError", "ValueError", "ZeroDivisionError", "EOFError", "IndexError", "KeyError", "AttributeError", "PracticeLimit", "MysteryError"):
            with self.subTest(error_type=error_type):
                result = beginner_helper.diagnose("x = 1\n", {"type": error_type, "message": "details", "line": 1})
                self.assert_contract(result)
                self.assertIsNone(result["fix"])
                self.assertIsInstance(result["line"], int)

    def test_integration_adds_helper_without_breaking_existing_result(self):
        result = json.loads(practice_engine.run("print(true)\n"))
        self.assertIn("output", result)
        self.assertIn("error", result)
        self.assertIn("steps", result)
        self.assertIn("elapsed_ms", result)
        self.assertIn("helper", result)
        self.assertEqual(result["helper"]["fix"]["after"], "print(True)\n")


if __name__ == "__main__":
    unittest.main()
