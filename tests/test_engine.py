import json
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app" / "src" / "main" / "python"))

import practice_engine  # noqa: E402


class PracticeEngineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        lessons_path = ROOT / "app" / "src" / "main" / "assets" / "lessons.json"
        cls.lessons = json.loads(lessons_path.read_text(encoding="utf-8"))

    def run_json(self, code, stdin=""):
        result = json.loads(practice_engine.run(code, stdin))
        self.assertIn("output", result)
        self.assertIn("error", result)
        self.assertIn("steps", result)
        self.assertIn("elapsed_ms", result)
        return result

    def test_all_lesson_outputs(self):
        """Every lesson executes on host CPython and produces its documented output."""
        self.assertEqual(len(self.lessons), 6)
        for lesson in self.lessons:
            with self.subTest(lesson=lesson["id"], title=lesson["title"]):
                supplied_input = "မေမေ" if lesson["id"] == 4 else ""
                result = self.run_json(lesson["code"], supplied_input)
                self.assertIsNone(result["error"])
                expected = lesson["expectedOutput"]
                # Lesson 4's expectedOutput contains a descriptive input prefix.
                if lesson["id"] == 4:
                    expected = expected.split("\n", 1)[1]
                # print() supplies a terminal newline; expectedOutput omits it.
                self.assertEqual(result["output"].rstrip("\n"), expected)

    def test_stdin_and_eof(self):
        result = self.run_json("first = input()\nsecond = input()\nprint(first + second)", "A\nB")
        self.assertIsNone(result["error"])
        self.assertEqual(result["output"], "AB\n")
        result = self.run_json("print(input())", "")
        self.assertEqual(result["error"]["type"], "EOFError")
        self.assertIn("Input", result["error"]["hint"])

    def test_syntax_name_and_zero_division_errors(self):
        cases = [
            ("if True print('x')", "SyntaxError", "Check quotes"),
            ("print(missing_name)", "NameError", "Give this variable"),
            ("print(1 / 0)", "ZeroDivisionError", "divided by zero"),
        ]
        for code, error_type, hint_fragment in cases:
            with self.subTest(error_type=error_type):
                result = self.run_json(code)
                self.assertIsNotNone(result["error"])
                self.assertEqual(result["error"]["type"], error_type)
                self.assertIsInstance(result["error"]["line"], int)
                self.assertIn(hint_fragment, result["error"]["hint"])

    def test_timeout_infinite_loop(self):
        result = self.run_json("while True:\n    pass")
        self.assertIsNotNone(result["error"])
        self.assertEqual(result["error"]["type"], "PracticeLimit")
        self.assertIn("1.5 s", result["error"]["message"])
        self.assertLess(result["elapsed_ms"], 5000)

    def test_output_cap(self):
        result = self.run_json("print('x' * 13000)")
        self.assertIsNotNone(result["error"])
        self.assertEqual(result["error"]["type"], "PracticeLimit")
        self.assertIn("Output is too long", result["error"]["message"])
        self.assertLessEqual(len(result["output"]), 12000)

    def test_variables_after_each_line(self):
        result = self.run_json("a = 2\nb = a + 3\nprint(b)")
        self.assertIsNone(result["error"])
        self.assertEqual(result["output"], "5\n")
        self.assertGreaterEqual(len(result["steps"]), 2)
        by_line = {step["line"]: step["variables"] for step in result["steps"]}
        self.assertEqual(by_line[1]["a"], "2")
        self.assertEqual(by_line[2]["a"], "2")
        self.assertEqual(by_line[2]["b"], "5")
        self.assertEqual(by_line[3]["b"], "5")
        # A snapshot records output accumulated through that completed line.
        self.assertEqual(result["steps"][-1]["output"], "5\n")

    def test_empty_code(self):
        result = self.run_json("")
        self.assertIsNone(result["error"])
        self.assertEqual(result["output"], "")
        self.assertIsInstance(result["steps"], list)

    def test_baseexception_system_exit_is_reported(self):
        result = self.run_json("raise SystemExit('stop')")
        self.assertIsNotNone(result["error"])
        self.assertEqual(result["error"]["type"], "SystemExit")
        self.assertEqual(result["error"]["message"], "stop")
        self.assertEqual(result["error"]["line"], 1)


if __name__ == "__main__":
    unittest.main()
