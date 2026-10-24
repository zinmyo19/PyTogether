import json
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app" / "src" / "main" / "python"))
import code_generator  # noqa: E402
import practice_engine  # noqa: E402

SAMPLE_INPUT = {
    "hello_name": "Aye", "add_two": "2\n3", "calculator": "6\n/\n3", "times_table": "7",
    "even_odd": "4", "sum_to_n": "10", "temperature": "100", "list_stats": "3,8,5",
    "guess_game": "3\n9\n7", "count_vowels": "Hello World", "fizzbuzz": "", "greet_function": "",
    "phonebook": "အေး", "palindrome": "level", "countdown": "3",
}


class CodeGeneratorTests(unittest.TestCase):
    def test_every_recipe_has_burmese_steps_and_runs_cleanly(self):
        self.assertEqual(set(SAMPLE_INPUT), {r["id"] for r in code_generator.RECIPES})
        for recipe in code_generator.RECIPES:
            with self.subTest(recipe["id"]):
                self.assertGreaterEqual(len(recipe["steps"]), 3)
                self.assertTrue(recipe["summary"] and recipe["title"] and recipe["keywords"])
                result = json.loads(practice_engine.run(recipe["code"], SAMPLE_INPUT[recipe["id"]]))
                self.assertIsNone(result.get("error"), result)
                self.assertTrue(result["output"].strip())

    def test_keyword_match_burmese_and_english(self):
        add = json.loads(code_generator.offline_generate("နံပါတ်နှစ်ခု ပေါင်းတဲ့ program"))
        self.assertEqual(add["match"]["recipe"], "add_two")
        fizz = json.loads(code_generator.offline_generate("write fizzbuzz please"))
        self.assertEqual(fizz["match"]["recipe"], "fizzbuzz")

    def test_unknown_request_does_not_invent_code(self):
        result = json.loads(code_generator.offline_generate("build me a weather forecast app"))
        self.assertIsNone(result["match"])

    def test_offline_check_reports_syntax_mistake_without_changing_code(self):
        result = json.loads(code_generator.offline_check('print("hi"'))
        self.assertEqual(len(result["mistakes"]), 1)
        self.assertEqual(result["mistakes"][0]["line"], 1)
        self.assertEqual(result["source"], "offline")

    def test_offline_check_proposes_reviewable_fix_that_compiles(self):
        result = json.loads(code_generator.offline_check("for i in range(3)\n    print(i)\n"))
        self.assertTrue(result["mistakes"])
        if result["code"]:
            compile(result["code"], "<fix>", "exec")
            self.assertEqual(result["original"], "for i in range(3)\n    print(i)\n")

    def test_offline_check_good_code_and_input_are_not_false_alarms(self):
        good = json.loads(code_generator.offline_check("print('ok')\n"))
        self.assertEqual(good["mistakes"], [])
        asks = json.loads(code_generator.offline_check("n = int(input())\nprint(n)\n"))
        self.assertEqual(asks["mistakes"], [])
        self.assertIn("input()", asks["notes"])

    def test_empty_code_is_rejected(self):
        self.assertEqual(json.loads(code_generator.offline_check("  \n")), {"error": "empty"})

    def test_analyze_reports_syntax_and_unavailable_modules_without_running(self):
        bad = json.loads(code_generator.analyze("def f(:\n"))
        self.assertFalse(bad["compiles"])
        self.assertEqual(bad["line"], 1)
        ui = json.loads(code_generator.analyze("import tkinter\nraise SystemExit('must not run')\n"))
        self.assertTrue(ui["compiles"])
        self.assertEqual(ui["unavailable"], ["tkinter"])
        missing = json.loads(code_generator.analyze("import definitely_not_installed_pkg_xyz\n"))
        self.assertEqual(missing["missing"], ["definitely_not_installed_pkg_xyz"])


if __name__ == "__main__":
    unittest.main()
