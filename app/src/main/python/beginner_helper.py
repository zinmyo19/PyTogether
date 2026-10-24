"""Small, offline diagnostics for beginner Python exercises.

This module never executes learner code and never calls a network service.  Any
suggested edit is syntax-checked before it is returned to the UI.
"""
import ast
import io
import keyword
import re
import tokenize
from typing import Any, Dict, Optional


_HEADER_WORDS = {"if", "elif", "else", "for", "while", "def", "class", "try",
                 "except", "finally", "with", "async", "match", "case"}
_NAME_FIXES = {
    "pritn": "print",
    "prnt": "print",
    "imput": "input",
    "lenght": "len",
    "lenth": "len",
}
_BOOL_FIXES = {"true": "True", "false": "False", "none": "None"}


def _result(title: str, explanation: str, next_step: str, line=None, fix=None) -> Dict[str, Any]:
    return {"title": title, "explanation": explanation, "next_step": next_step,
            "line": line if isinstance(line, int) and line > 0 else None, "fix": fix}


def _checked_fix(code: str, after: str, label: str) -> Optional[dict]:
    if after == code:
        return None
    try:
        compile(after, "<practice>", "exec")
    except (SyntaxError, ValueError, TypeError, MemoryError):
        return None
    return {"label": label, "before": code, "after": after}


def _tokens(source: str):
    try:
        return list(tokenize.generate_tokens(io.StringIO(source).readline))
    except (tokenize.TokenError, IndentationError, SyntaxError):
        # Tokenize can stop on an unfinished string; callers can still use the
        # tokens it produced only when available, but no speculative edit is safe.
        return []


def _replace_name(code: str, line: int, old: str, new: str) -> Optional[str]:
    """Replace one real NAME token, never text inside a string or comment."""
    matches = [tok for tok in _tokens(code)
               if tok.type == tokenize.NAME and tok.string == old and tok.start[0] == line]
    if len(matches) != 1:
        return None
    tok = matches[0]
    lines = code.splitlines(True)
    if not (1 <= tok.start[0] <= len(lines)):
        return None
    row = lines[tok.start[0] - 1]
    start, end = tok.start[1], tok.end[1]
    lines[tok.start[0] - 1] = row[:start] + new + row[end:]
    return "".join(lines)


def _name_fix(code: str, line: int, missing: str) -> Optional[dict]:
    replacement = _BOOL_FIXES.get(missing) or _NAME_FIXES.get(missing)
    # True/False/None are keywords in Python but are intentional literal
    # replacements here; all other replacements come from the fixed allowlist.
    if not replacement:
        return None
    after = _replace_name(code, line, missing, replacement)
    if after is None:
        return None
    label = f"{missing} ကို {replacement} သို့ ပြင်ရန်"
    return _checked_fix(code, after, label)


def _missing_colon_fix(code: str, line: int) -> Optional[dict]:
    """Suggest a colon only for a tokenized control/header line."""
    toks = [t for t in _tokens(code)
            if t.start[0] == line and t.type not in (tokenize.COMMENT, tokenize.NL,
                                                       tokenize.NEWLINE, tokenize.INDENT,
                                                       tokenize.DEDENT, tokenize.ENDMARKER)]
    if not toks or toks[0].type != tokenize.NAME or toks[0].string not in _HEADER_WORDS:
        return None
    if any(t.type == tokenize.OP and t.string == ":" for t in toks):
        return None
    # A header must have a body marker/expression.  `else`, `try`, and
    # `finally` are complete headers by themselves; other headers need tokens.
    if toks[0].string not in {"else", "try", "finally"} and len(toks) < 2:
        return None
    lines = code.splitlines(True)
    if not (1 <= line <= len(lines)):
        return None
    row = lines[line - 1]
    comment = next((t for t in _tokens(code) if t.start[0] == line and t.type == tokenize.COMMENT), None)
    if comment:
        prefix = row[:comment.start[1]]
        whitespace = prefix[len(prefix.rstrip(" \t")):]
        lines[line - 1] = prefix.rstrip(" \t") + ":" + whitespace + row[comment.start[1]:]
    else:
        insert_at = len(row.rstrip("\r\n"))
        lines[line - 1] = row[:insert_at] + ":" + row[insert_at:]
    after = "".join(lines)
    return _checked_fix(code, after, "ဒီ block အဆုံးမှာ colon (:) ထည့်ရန်")


def _safe_type(exc_type: str, message: str) -> tuple[str, str, str]:
    if exc_type == "NameError":
        return ("အမည်မတွေ့ပါ", "ဒီအမည်ကို အသုံးမပြုမီ တန်ဖိုးပေးထားခြင်း သို့မဟုတ် အမည်စာလုံးပေါင်းကို စစ်ပါ။",
                "ပြထားတဲ့လိုင်းရှိ အမည်နဲ့ အပေါ်မှာ တန်ဖိုးပေးထားတဲ့ အမည် တူသလား စစ်ပါ။")
    if exc_type in {"IndentationError", "TabError"}:
        return ("စာကြောင်းအဝင်ကို စစ်ပါ", "Python က block အတွင်းက code ကို စာကြောင်းအစ spaces နဲ့ ခွဲခြားပါတယ်။",
                "block အတွင်းက လိုင်းတွေကို spaces ၄ ခုပိုဝင်ရေးပါ။ Tab နဲ့ spaces ရောမသုံးပါနဲ့။")
    if exc_type == "SyntaxError" and ("unterminated string" in message or "EOL while scanning string" in message):
        return ("စာသားအဆုံး quote ကို စစ်ပါ", "စာသားကို quote နဲ့ စဖွင့်ထားရင် အမျိုးအစားတူ quote နဲ့ ပြန်ပိတ်ရပါတယ်။",
                "ဥပမာ print(\"မင်္ဂလာပါ\") လို စာသားရဲ့ အစနဲ့အဆုံး quote ကို တွဲစစ်ပါ။")
    if exc_type in {"SyntaxError", "IndentationError", "TabError"}:
        return ("ရေးသားပုံကို စစ်ပါ", "Python ရဲ့ syntax သို့မဟုတ် indentation မမှန်ပါ။ block တစ်ခုချင်းစီရဲ့ အကွာအဝေးနဲ့ colon ကို စစ်ပါ။",
                "ပြထားတဲ့လိုင်းမှာ quote၊ bracket၊ colon နဲ့ spaces ကို တစ်ခုပြီးတစ်ခု စစ်ပါ။")
    if exc_type == "TypeError":
        return ("တန်ဖိုးအမျိုးအစား မကိုက်ပါ", "လုပ်ဆောင်ချက်တစ်ခုက မျှော်လင့်ထားတဲ့ data type မဟုတ်တဲ့ တန်ဖိုးကို ရရှိထားပါတယ်။",
                "လိုင်းမှာ သုံးထားတဲ့ တန်ဖိုးအမျိုးအစားတွေကို စစ်ပြီး လိုအပ်ရင် str() သို့မဟုတ် int() ကို စဉ်းစားပါ။")
    if exc_type == "ValueError":
        return ("တန်ဖိုးပုံစံ မကိုက်ပါ", "အမျိုးအစားတော့ မှန်ပေမယ့် လက်ခံနိုင်တဲ့ ပုံစံမဟုတ်တဲ့ တန်ဖိုးကို ရရှိထားပါတယ်။",
                "ပြထားတဲ့လိုင်းမှာ တန်ဖိုးပုံစံနဲ့ ပြောင်းလဲမှုကို စစ်ပါ။")
    if exc_type == "ZeroDivisionError":
        return ("သုညနဲ့ စားလို့မရပါ", "ကိန်းတစ်ခုကို သုညနဲ့ စားလို့ မရပါဘူး။",
                "စားတဲ့နေရာက divisor ကို သုညမဖြစ်အောင် စစ်ပါ။")
    if exc_type == "EOFError":
        return ("Input မလုံလောက်ပါ", "input() ခေါ်ဆိုမှုအတွက် Input ထဲမှာ လိုအပ်တဲ့လိုင်း မရှိသေးပါဘူး။",
                "Input နေရာမှာ input() ခေါ်ဆိုမှုတိုင်းအတွက် လိုင်းတစ်ကြောင်း ထည့်ပါ။")
    if exc_type == "PracticeLimit":
        return ("လေ့ကျင့်ခန်း ရပ်ထားပါတယ်", "output သို့မဟုတ် run time က သတ်မှတ်ထားတဲ့ ကန့်သတ်ချက်ကို ကျော်သွားလို့ ရပ်ထားပါတယ်။",
                "loop ကို ပိုတိုအောင်လုပ်ပြီး output ပမာဏကို လျှော့ကာ ပြန် run လုပ်ပါ။")
    if exc_type in {"IndexError", "KeyError", "AttributeError"}:
        return ("တန်ဖိုးကို ရှာမတွေ့ပါ", "စာရင်းအညွှန်း၊ dictionary key သို့မဟုတ် object attribute ကို သုံးတဲ့နေရာမှာ မကိုက်ညီမှုရှိပါတယ်။",
                "ပြထားတဲ့လိုင်းမှာ ရှိနိုင်တဲ့ index၊ key သို့မဟုတ် attribute ကို စစ်ပါ။")
    return ("ပြဿနာတစ်ခု တွေ့ပါတယ်", "Python က ဒီလိုင်းမှာ error တစ်ခုတွေ့ထားပါတယ်။ အောက်က အမှားအမျိုးအစားကို အခြေခံပြီး code ကို စစ်ပါ။",
            "ပြထားတဲ့လိုင်းကို တစ်ဆင့်ချင်း စစ်ပြီး ပြန် run လုပ်ပါ။")


def diagnose(code: str, error: Optional[dict]) -> Dict[str, Any]:
    """Return a stable beginner-facing diagnosis without executing *code*."""
    code = code if isinstance(code, str) else ""
    if not error:
        return _result("အမှားမတွေ့ပါ", "ဒီ run မှာ runtime error မရှိပါဘူး။ ဒါပေမယ့် program ရဲ့ logic မှန်ကန်တယ်လို့တော့ မဆိုလိုပါဘူး။",
                       "output နဲ့ ကိုယ်မျှော်မှန်းထားတဲ့ ရလဒ်ကို နှိုင်းယှဉ်စစ်ပါ။")
    if not isinstance(error, dict):
        error = {}
    exc_type = str(error.get("type") or "")
    line = error.get("line") if isinstance(error.get("line"), int) and error.get("line") > 0 else None
    message = str(error.get("message") or "")
    title, explanation, next_step = _safe_type(exc_type, message)
    fix = None
    if exc_type == "SyntaxError" and line:
        fix = _missing_colon_fix(code, line)
        if fix:
            title = "colon (:) လိုနေပါတယ်"
            explanation = "block တစ်ခု စမယ့်လိုင်းအဆုံးမှာ : ထည့်ပြီး နောက်လိုင်းက code ကို အတွင်းဝင်ရေးရပါတယ်။"
            next_step = "ပြင်နည်းကို အရင်ကြည့်ပါ။ ဒီအဆင့်မှာ : ထည့်တာတစ်ခုကိုပဲ လေ့လာမယ်။"
    elif exc_type == "NameError" and line:
        match = re.search(r"name ['\"]([^'\"]+)['\"] is not defined", message)
        if match:
            missing = match.group(1)
            # AST location confirms this is a code name, not a quoted/commented string.
            try:
                tree = ast.parse(code)
                in_line = [n for n in ast.walk(tree) if isinstance(n, ast.Name)
                           and n.id == missing and n.lineno == line and isinstance(n.ctx, ast.Load)]
                if missing in _NAME_FIXES:
                    in_line = [n for n in ast.walk(tree) if isinstance(n, ast.Call)
                               and isinstance(n.func, ast.Name) and n.func.id == missing
                               and n.func.lineno == line]
            except (SyntaxError, ValueError, TypeError):
                in_line = []
            if in_line:
                fix = _name_fix(code, line, missing)
                if fix:
                    replacement = _BOOL_FIXES.get(missing) or _NAME_FIXES.get(missing)
                    title = "အမည်စာလုံးပေါင်းကို ပြင်ကြည့်မယ်"
                    explanation = (f"Python မှာ စာလုံးအကြီးအသေး ကွာပါတယ်။ {missing} အစား {replacement} ကို သုံးရပါတယ်။"
                                   if missing in _BOOL_FIXES else
                                   f"{missing} ဆိုတဲ့ အမည်ကို မတွေ့ပါဘူး။ သုံးချင်တာ {replacement} ဖြစ်နိုင်ပါတယ်။")
                    next_step = "ကိုယ်ရည်ရွယ်တဲ့ အမည် ဟုတ်သလား ပြင်နည်းထဲမှာ ကြည့်ပြီးမှ လက်ခံပါ။"
    return _result(title, explanation, next_step, line, fix)
