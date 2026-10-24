"""Offline helpers for the Code Generator screen.

Nothing here calls a network service.  `offline_generate` picks a ready-made,
reviewed beginner recipe (it does not write new code on its own), `offline_check`
points out beginner mistakes using the existing rule-based helper, and `analyze`
reports whether code compiles and which imports are not available.
Generated code is never executed automatically.
"""
import ast
import json
import sys

from beginner_helper import diagnose

# Each recipe: id, title (Burmese), keywords (lower-case, Burmese + English),
# summary, steps, code.
RECIPES = [
    {
        "id": "hello_name",
        "title": "နာမည်မေးပြီး နှုတ်ဆက်မယ်",
        "keywords": ["နှုတ်ဆက်", "hello", "greet", "နာမည်မေး", "name"],
        "summary": "User ရဲ့ နာမည်ကို မေးပြီး \"မင်္ဂလာပါ\" လို့ ပြန်ပြောတဲ့ program။",
        "steps": [
            "input(\"...\") က စာသားမေးပြီး user ရိုက်တာကို ယူတယ်။ ရလာတာကို name ဆိုတဲ့ variable ထဲ သိမ်းတယ်။",
            "\"မင်္ဂလာပါ \" + name က စာသားနှစ်ခုကို ဆက်ပေးတယ်။",
            "print(...) က screen ပေါ်မှာ ပြတယ်။ Input အကွက်ထဲ နာမည်တစ်ခု ရိုက်ထည့်ပြီး စမ်းကြည့်ပါ။",
        ],
        "code": "# နာမည်မေးပြီး နှုတ်ဆက်တဲ့ program\nname = input(\"နာမည် ဘာလဲ? \")\nprint(\"မင်္ဂလာပါ \" + name)\n",
    },
    {
        "id": "add_two",
        "title": "နံပါတ်နှစ်ခု ပေါင်းမယ်",
        "keywords": ["ပေါင်း", "add", "plus", "sum of two", "နှစ်ခုပေါင်း"],
        "summary": "နံပါတ်နှစ်ခုကို မေးပြီး ပေါင်းလဒ်ကို ပြတဲ့ program။",
        "steps": [
            "input() က စာသားအဖြစ်ပဲ ပြန်ပေးတယ်။ ဂဏန်းအဖြစ် တွက်ချင်လို့ int(...) နဲ့ ပြောင်းတယ်။",
            "a နဲ့ b ထဲမှာ နံပါတ်နှစ်ခု သိမ်းတယ်။",
            "a + b ကို တွက်ပြီး print နဲ့ ပြတယ်။ Input အကွက်မှာ တစ်ကြောင်းကို နံပါတ်တစ်ခုစီ ရိုက်ပါ။",
        ],
        "code": "# နံပါတ်နှစ်ခု ပေါင်းတဲ့ program\na = int(input(\"ပထမ number: \"))\nb = int(input(\"ဒုတိယ number: \"))\nprint(\"ပေါင်းလဒ် =\", a + b)\n",
    },
    {
        "id": "calculator",
        "title": "ဂဏန်းတွက်စက်လေး (+ - * /)",
        "keywords": ["calculator", "တွက်စက်", "ဂဏန်းတွက်", "လေးမျိုး", "calc"],
        "summary": "နံပါတ်နှစ်ခုနဲ့ လုပ်ချင်တဲ့ အမှတ်အသား (+ - * /) ကို မေးပြီး တွက်ပေးတဲ့ program။",
        "steps": [
            "float(...) နဲ့ ဒသမပါတဲ့ ဂဏန်းတွေကိုလည်း လက်ခံမယ်။",
            "if / elif / else နဲ့ ရွေးထားတဲ့ အမှတ်အသားကို တစ်ခုချင်း စစ်တယ်။",
            "စားတဲ့အခါ b သုည ဖြစ်နေရင် error မတက်အောင် အရင်စစ်ထားတယ်။",
        ],
        "code": "# ဂဏန်းတွက်စက်လေး\na = float(input(\"ပထမ number: \"))\nop = input(\"လုပ်ချင်တာ (+ - * /): \")\nb = float(input(\"ဒုတိယ number: \"))\nif op == \"+\":\n    print(a + b)\nelif op == \"-\":\n    print(a - b)\nelif op == \"*\":\n    print(a * b)\nelif op == \"/\":\n    if b == 0:\n        print(\"သုညနဲ့ စား၍ မရပါ\")\n    else:\n        print(a / b)\nelse:\n    print(\"မသိတဲ့ အမှတ်အသား ဖြစ်ပါတယ်\")\n",
    },
    {
        "id": "times_table",
        "title": "မြှောက်ဇယား ထုတ်မယ်",
        "keywords": ["မြှောက်ဇယား", "multiplication", "times table", "ဇယား", "table"],
        "summary": "ရွေးထားတဲ့ နံပါတ်ရဲ့ ၁ ကနေ ၁၀ အထိ မြှောက်ဇယားကို ပြတဲ့ program။",
        "steps": [
            "n ထဲမှာ ဇယားထုတ်ချင်တဲ့ နံပါတ်ကို သိမ်းတယ်။",
            "for i in range(1, 11) က i ကို ၁ ကနေ ၁၀ အထိ တစ်ခုချင်း ပေးတယ်။",
            "ပတ်တိုင်း n * i ကို တွက်ပြီး print လုပ်တယ်။ for အောက်က လိုင်းတွေကို space ၄ ခုဝင်ရေးထားတာ သတိထားပါ။",
        ],
        "code": "# မြှောက်ဇယား\nn = int(input(\"ဘယ်နံပါတ်ရဲ့ ဇယားလဲ? \"))\nfor i in range(1, 11):\n    print(n, \"x\", i, \"=\", n * i)\n",
    },
    {
        "id": "even_odd",
        "title": "စုံ / မ စစ်မယ်",
        "keywords": ["စုံ", "even", "odd", "စုံမ"],
        "summary": "နံပါတ်တစ်ခုက စုံလား မလား ပြောပေးတဲ့ program။",
        "steps": [
            "n % 2 က ၂ နဲ့စားတဲ့အခါ ကျန်တဲ့ အကြွင်းကို ပေးတယ်။",
            "အကြွင်း 0 ဆိုရင် စုံ၊ မဟုတ်ရင် မ။",
            "== က \"ညီလား\" လို့ နှိုင်းယှဉ်တာ၊ = တစ်ခုတည်းက တန်ဖိုးထည့်တာ။ မရောပါနဲ့။",
        ],
        "code": "# စုံ / မ စစ်မယ်\nn = int(input(\"နံပါတ် ရိုက်ပါ: \"))\nif n % 2 == 0:\n    print(n, \"က စုံဂဏန်း\")\nelse:\n    print(n, \"က မဂဏန်း\")\n",
    },
    {
        "id": "sum_to_n",
        "title": "၁ ကနေ n အထိ စုစုပေါင်း",
        "keywords": ["1 မှ", "၁ မှ", "1 to n", "sum to", "စုစုပေါင်း", "total"],
        "summary": "၁ ကနေ n အထိ နံပါတ်တွေ အားလုံးကို ပေါင်းပေးတဲ့ program။",
        "steps": [
            "total = 0 နဲ့ စပြီး စုစုပေါင်းကို သိမ်းမယ့် ထည့်စရာတစ်ခု ပြင်ထားတယ်။",
            "for i in range(1, n + 1) က ၁ ကနေ n အထိ ပတ်တယ်။ (n ပါဖို့ n + 1 လို့ ရေးတာ)",
            "ပတ်တိုင်း total = total + i နဲ့ ထပ်ပေါင်းတယ်။ Data flow မှာ total ပြောင်းသွားတာကို ကြည့်ပါ။",
        ],
        "code": "# ၁ ကနေ n အထိ ပေါင်းမယ်\nn = int(input(\"n ကို ရိုက်ပါ: \"))\ntotal = 0\nfor i in range(1, n + 1):\n    total = total + i\nprint(\"စုစုပေါင်း =\", total)\n",
    },
    {
        "id": "temperature",
        "title": "အပူချိန် Celsius → Fahrenheit",
        "keywords": ["အပူချိန်", "temperature", "celsius", "fahrenheit", "စင်တီဂရိတ်"],
        "summary": "Celsius ကို Fahrenheit ပြောင်းပေးတဲ့ program။",
        "steps": [
            "float(input(...)) နဲ့ ဒသမပါတဲ့ အပူချိန်ကို ယူတယ်။",
            "ပုံသေနည်း F = C × 9 / 5 + 32 ကို code အဖြစ် ရေးထားတယ်။",
            "ရလဒ်ကို print နဲ့ ပြတယ်။ 100 ထည့်ရင် 212 ရပါမယ်။",
        ],
        "code": "# Celsius ကနေ Fahrenheit\nc = float(input(\"Celsius ဘယ်လောက်လဲ? \"))\nf = c * 9 / 5 + 32\nprint(c, \"C =\", f, \"F\")\n",
    },
    {
        "id": "list_stats",
        "title": "နံပါတ်စာရင်း — အများဆုံး၊ အနည်းဆုံး၊ ပျမ်းမျှ",
        "keywords": ["အကြီးဆုံး", "အများဆုံး", "ပျမ်းမျှ", "average", "maximum", "minimum", "max", "min", "list"],
        "summary": "Comma နဲ့ ခြားထားတဲ့ နံပါတ်တွေထဲက အများဆုံး၊ အနည်းဆုံး၊ ပျမ်းမျှကို ပြတဲ့ program။",
        "steps": [
            "text.split(\",\") က စာကြောင်းကို comma နေရာမှာ ဖြတ်ပြီး စာရင်း (list) လုပ်ပေးတယ်။",
            "for ပတ်ပြီး တစ်ခုချင်း float နဲ့ ဂဏန်းပြောင်းကာ numbers list ထဲ append လုပ်တယ်။",
            "max()၊ min()၊ sum() နဲ့ len() ကို သုံးပြီး အဖြေတွေ တွက်တယ်။",
        ],
        "code": "# နံပါတ်စာရင်း စစ်မယ်\ntext = input(\"နံပါတ်တွေကို comma နဲ့ ခြားရေးပါ (ဥပမာ 3,8,5): \")\nnumbers = []\nfor part in text.split(\",\"):\n    numbers.append(float(part))\nprint(\"အများဆုံး:\", max(numbers))\nprint(\"အနည်းဆုံး:\", min(numbers))\nprint(\"ပျမ်းမျှ:\", sum(numbers) / len(numbers))\n",
    },
    {
        "id": "guess_game",
        "title": "နံပါတ်ခန့်မှန်းတဲ့ ဂိမ်း",
        "keywords": ["ခန့်မှန်း", "guess", "game", "ဂိမ်း"],
        "summary": "ဝှက်ထားတဲ့ နံပါတ်ကို မမှန်မချင်း ခန့်မှန်းရတဲ့ ဂိမ်းလေး။",
        "steps": [
            "secret ထဲမှာ ဝှက်ထားတဲ့ နံပါတ်ကို ပုံသေထည့်ထားတယ်။",
            "while True က မှန်တဲ့အထိ ပတ်နေမယ်။ မှန်ရင် break နဲ့ ထွက်တယ်။",
            "နည်းနေရင် \"ပိုကြီးကြည့်ပါ\"၊ များနေရင် \"ပိုသေးကြည့်ပါ\" လို့ အရိပ်အမြွက်ပေးတယ်။ Input အကွက်မှာ တစ်ကြောင်းကို ခန့်မှန်းချက်တစ်ခုစီ ရိုက်ပါ။",
        ],
        "code": "# နံပါတ်ခန့်မှန်းတဲ့ ဂိမ်း\nsecret = 7\nwhile True:\n    guess = int(input(\"ခန့်မှန်းပါ (1-10): \"))\n    if guess == secret:\n        print(\"မှန်ပါတယ်!\")\n        break\n    elif guess < secret:\n        print(\"ပိုကြီးကြည့်ပါ\")\n    else:\n        print(\"ပိုသေးကြည့်ပါ\")\n",
    },
    {
        "id": "count_vowels",
        "title": "စာထဲက သရ (a e i o u) ရေတွက်မယ်",
        "keywords": ["vowel", "သရ", "စာလုံးရေ", "count letters", "ရေတွက်", "count"],
        "summary": "English စာကြောင်းထဲက a e i o u စာလုံးတွေ ဘယ်နှလုံးရှိလဲ ရေတွက်တဲ့ program။",
        "steps": [
            "text.lower() က စာလုံးအကြီးအသေး မရွေးအောင် အသေးပြောင်းတယ်။",
            "for ch in text က စာလုံးတစ်လုံးချင်း ပတ်တယ်။",
            "ch in \"aeiou\" က သရဖြစ်လား စစ်တယ်။ ဖြစ်ရင် count ကို ၁ တိုးတယ်။",
        ],
        "code": "# သရ ရေတွက်မယ်\ntext = input(\"English စာကြောင်း ရိုက်ပါ: \").lower()\ncount = 0\nfor ch in text:\n    if ch in \"aeiou\":\n        count = count + 1\nprint(\"သရ စုစုပေါင်း =\", count)\n",
    },
    {
        "id": "fizzbuzz",
        "title": "FizzBuzz",
        "keywords": ["fizzbuzz", "fizz", "buzz"],
        "summary": "၁ ကနေ ၁၅ အထိ — ၃ နဲ့စား လည်းရရင် Fizz၊ ၅ နဲ့စား လည်းရရင် Buzz၊ နှစ်ခုလုံးဆို FizzBuzz ပြတဲ့ ကျင့်ခန်း။",
        "steps": [
            "for i in range(1, 16) က ၁ ကနေ ၁၅ အထိ ပတ်တယ်။",
            "၃ ရော ၅ ရော နှစ်ခုလုံးနဲ့ စားလို့ပြတ်တာ (i % 15 == 0) ကို အရင်ဆုံး စစ်ရတယ်။ နောက်မှ စစ်ရင် ၁၅ က Fizz ပဲ ထွက်သွားမယ်။",
            "i % 3 == 0 ဆိုတာ \"၃ နဲ့ ပြတ်အောင် စားလို့ရလား\" လို့ မေးတာ။",
        ],
        "code": "# FizzBuzz\nfor i in range(1, 16):\n    if i % 15 == 0:\n        print(\"FizzBuzz\")\n    elif i % 3 == 0:\n        print(\"Fizz\")\n    elif i % 5 == 0:\n        print(\"Buzz\")\n    else:\n        print(i)\n",
    },
    {
        "id": "greet_function",
        "title": "function တစ်ခု သတ်မှတ်ပြီး ခေါ်မယ်",
        "keywords": ["function", "def ", "def", "လုပ်ဆောင်ချက်သတ်မှတ်"],
        "summary": "def နဲ့ ကိုယ်ပိုင် function ရေးပြီး အကြိမ်ကြိမ် ပြန်သုံးတာကို ပြတဲ့ ဥပမာ။",
        "steps": [
            "def greet(name): က greet ဆိုတဲ့ function ကို သတ်မှတ်တယ်။ name က ထည့်ပေးရမယ့် တန်ဖိုး။",
            "function အောက်က လိုင်းတွေကို space ၄ ခုဝင်ရေးရတယ်။ ဒါက function ရဲ့ အစိတ်အပိုင်းလို့ ပြောတာ။",
            "greet(\"...\") လို့ ခေါ်တိုင်း အထဲက code အလုပ်လုပ်တယ်။ နာမည်အမျိုးမျိုးနဲ့ ခေါ်ကြည့်ပါ။",
        ],
        "code": "# function ရေးနည်း\ndef greet(name):\n    print(\"မင်္ဂလာပါ \" + name + \"!\")\n\ngreet(\"မောင်မောင်\")\ngreet(\"မမ\")\n",
    },
    {
        "id": "phonebook",
        "title": "ဖုန်းစာအုပ် (dictionary)",
        "keywords": ["dictionary", "dict", "ဖုန်းစာအုပ်", "phonebook", "phone book"],
        "summary": "နာမည်နဲ့ ဖုန်းနံပါတ်ကို dictionary နဲ့ သိမ်းပြီး ရှာပေးတဲ့ program။",
        "steps": [
            "{ } ထဲမှာ \"နာမည်\": \"နံပါတ်\" ဆိုပြီး တွဲ (key: value) ထည့်ထားတာက dictionary။",
            "name in phones က ဒီနာမည် စာအုပ်ထဲ ရှိလားလို့ စစ်တယ်။",
            "ရှိရင် phones[name] နဲ့ နံပါတ်ကို ထုတ်ပြတယ်။ မရှိရင် မတွေ့ဘူးလို့ ပြောတယ်။",
        ],
        "code": "# ဖုန်းစာအုပ်\nphones = {\"အေး\": \"0911111111\", \"မြင့်\": \"0922222222\"}\nname = input(\"ဘယ်သူ့ နံပါတ်ရှာမလဲ? \")\nif name in phones:\n    print(name, \"ရဲ့ နံပါတ်:\", phones[name])\nelse:\n    print(\"မတွေ့ပါ\")\n",
    },
    {
        "id": "palindrome",
        "title": "စာကို ပြောင်းပြန်လှန်မယ် / palindrome စစ်မယ်",
        "keywords": ["ပြောင်းပြန်", "reverse", "palindrome", "ပြန်ဖတ်"],
        "summary": "စာကို ပြောင်းပြန်ပြပြီး ရှေ့ကဖတ်၊ နောက်ကဖတ် တူလား (palindrome) စစ်တဲ့ program။",
        "steps": [
            "text[::-1] က စာကို အနောက်ကနေ ပြောင်းပြန် ယူတာ။",
            "text == reversed_text နဲ့ မူရင်းနဲ့ ပြောင်းပြန်ကို နှိုင်းယှဉ်တယ်။",
            "တူရင် palindrome ဥပမာ \"level\" သို့မဟုတ် \"madam\" ကို စမ်းကြည့်ပါ။",
        ],
        "code": "# စာကို ပြောင်းပြန်လှန်ပြီး စစ်မယ်\ntext = input(\"စာတစ်ခု ရိုက်ပါ: \")\nreversed_text = text[::-1]\nprint(\"ပြောင်းပြန်:\", reversed_text)\nif text == reversed_text:\n    print(\"Palindrome ဖြစ်ပါတယ်\")\nelse:\n    print(\"Palindrome မဟုတ်ပါ\")\n",
    },
    {
        "id": "countdown",
        "title": "နောက်ပြန်ရေ (countdown)",
        "keywords": ["countdown", "နောက်ပြန်ရေ", "while", "ပြန်ရေ"],
        "summary": "နံပါတ်တစ်ခုကနေ ၁ အထိ နောက်ပြန်ရေပြတဲ့ program။",
        "steps": [
            "n ထဲမှာ စတင်မယ့် နံပါတ်ကို ထည့်တယ်။",
            "while n > 0 က n သုညထက်ကြီးနေသရွေ့ ပတ်နေတယ်။",
            "ပတ်တိုင်း n ကိုပြပြီး n = n - 1 နဲ့ တစ်လျှော့တယ်။ ဒါမရှိရင် အဆုံးမသတ်ဘဲ ပတ်နေမှာ (ပထမဆုံး သတိထားရမယ့်အချက်)။",
        ],
        "code": "# နောက်ပြန်ရေ\nn = int(input(\"ဘယ်နံပါတ်ကနေ စမလဲ? \"))\nwhile n > 0:\n    print(n)\n    n = n - 1\nprint(\"ပြီးပါပြီ!\")\n",
    },
]


def recipe_list():
    """Titles for the picker: [{"id":..., "title":...}]."""
    return json.dumps([{"id": r["id"], "title": r["title"]} for r in RECIPES], ensure_ascii=False)


def _recipe_result(recipe):
    return {
        "summary": recipe["summary"],
        "steps": list(recipe["steps"]),
        "mistakes": [],
        "code": recipe["code"],
        "original": None,
        "libraries": [],
        "notes": "ဒီ code က app ထဲမှာ ကြိုပါပြီးသား နမူနာ ဖြစ်ပါတယ်။ AI နဲ့ ကိုယ်လိုချင်သလို ရေးခိုင်းချင်ရင် Settings မှာ Online ဖွင့်ပါ။",
        "source": "offline",
        "recipe": recipe["id"],
    }


def offline_recipe(recipe_id):
    for recipe in RECIPES:
        if recipe["id"] == recipe_id:
            return json.dumps(_recipe_result(recipe), ensure_ascii=False)
    return json.dumps({"error": "recipe not found"})


def offline_generate(request):
    """Pick the best matching recipe by keywords, or return suggestions."""
    text = (request or "").lower()
    scored = []
    for recipe in RECIPES:
        score = sum(1 for word in recipe["keywords"] if word.lower().strip() and word.lower().strip() in text)
        if score:
            scored.append((score, recipe))
    scored.sort(key=lambda item: -item[0])
    if scored:
        return json.dumps({"match": _recipe_result(scored[0][1]),
                           "others": [r["id"] for _, r in scored[1:4]]}, ensure_ascii=False)
    return json.dumps({"match": None, "others": []})


def offline_check(code):
    """Point out beginner mistakes without AI.  Uses the same rules as Ask Helper."""
    if not (code or "").strip():
        return json.dumps({"error": "empty"})
    error = None
    try:
        compile(code, "<generator>", "exec")
    except SyntaxError as exc:
        error = {"type": type(exc).__name__, "message": str(exc.msg)[:300], "line": exc.lineno}
    except (ValueError, MemoryError) as exc:
        error = {"type": type(exc).__name__, "message": str(exc)[:300], "line": None}
    note = ""
    if error is None:
        import practice_engine  # same limited, traced runner the editor uses
        outcome = json.loads(practice_engine.run(code, ""))
        run_error = outcome.get("error")
        if run_error and run_error.get("type") == "EOFError":
            note = "Code ထဲမှာ input() ရှိလို့ စစ်တဲ့အချိန် ရပ်သွားပါတယ်။ Editor ရဲ့ Input အကွက်မှာ တန်ဖိုးထည့်ပြီး Run လုပ်ကြည့်ပါ။"
        elif run_error:
            error = run_error
    helper = diagnose(code, error)
    mistakes = []
    fixed = None
    if error is not None:
        mistakes.append({"line": helper.get("line") or error.get("line"),
                         "problem": helper.get("title") or "အမှားတွေ့ပါတယ်",
                         "why": (helper.get("explanation") or "") + "\n" + (helper.get("next_step") or "")})
        if helper.get("fix"):
            fixed = helper["fix"]["after"]
    summary = ("အမှား " + str(len(mistakes)) + " ခု တွေ့ပါတယ်။" if mistakes
               else "Syntax အမှား မတွေ့ပါ။ ဒါပေမဲ့ logic မှန်တယ်လို့ အာမမခံပါ။")
    steps = []
    if mistakes:
        steps.append("အောက်က အမှားကို ဖတ်ပါ။")
        steps.append("ပြင်နည်း ပါရင် \"Review\" နှိပ်ပြီး မူလ code နဲ့ နှိုင်းယှဉ်ကြည့်ပါ။ လက်ခံမှသာ code ပြောင်းပါမယ်။")
    return json.dumps({"summary": summary, "steps": steps, "mistakes": mistakes, "code": fixed,
                       "original": code if fixed else None, "libraries": [], "notes": note,
                       "source": "offline"}, ensure_ascii=False)


_UNAVAILABLE_ON_ANDROID = {"tkinter", "turtle", "curses", "readline", "idlelib"}


def analyze(code, site_dir=None):
    """Compile check plus import check.  Never executes the code."""
    if site_dir:
        try:
            import package_manager
            package_manager.activate(site_dir)
        except Exception:
            pass
    try:
        tree = ast.parse(code)
    except (SyntaxError, ValueError) as exc:
        return json.dumps({"compiles": False, "line": getattr(exc, "lineno", None),
                           "message": str(getattr(exc, "msg", exc))[:200], "missing": [], "unavailable": []})
    modules = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            modules.update(alias.name.split(".")[0] for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module and node.level == 0:
            modules.add(node.module.split(".")[0])
    import importlib.util
    missing, unavailable = [], []
    stdlib = getattr(sys, "stdlib_module_names", set())
    for name in sorted(modules):
        if name in _UNAVAILABLE_ON_ANDROID:
            unavailable.append(name)
            continue
        if name in stdlib:
            continue
        try:
            if importlib.util.find_spec(name) is None:
                missing.append(name)
        except (ImportError, ValueError):
            missing.append(name)
    return json.dumps({"compiles": True, "line": None, "message": "", "missing": missing, "unavailable": unavailable})
