"""Offline CPython runner for trusted beginner exercises, not a security sandbox."""
import builtins
import contextlib
import io
import json
import sys
import time
import traceback
from beginner_helper import diagnose

class PracticeLimit(Exception):
    pass

class OutputBuffer(io.StringIO):
    def write(self, value):
        if self.tell() + len(value) > 12000:
            raise PracticeLimit('Output is too long. Try a smaller loop.')
        return super().write(value)

def run(code, stdin=''):
    started = time.monotonic()
    output = OutputBuffer()
    snapshots = []
    previous = {}
    count = 0
    values = iter(stdin.splitlines())
    error = None
    def read_input(prompt=''):
        if prompt:
            output.write(str(prompt))
        try:
            return next(values)
        except StopIteration:
            raise EOFError('Add a line in Input for each input() call.')
    def safe_values(frame):
        result = {}
        for key, value in list(frame.f_locals.items()):
            if key.startswith('_'):
                continue
            if type(value) in (str, int, float, bool, list, tuple, dict, set, type(None)):
                try:
                    # Avoid repr of giant containers before clipping.
                    if isinstance(value, (list, tuple)):
                        shown = repr(value[:8]) + (' …' if len(value) > 8 else '')
                    elif isinstance(value, (dict, set)):
                        shown = f'<{type(value).__name__}: {len(value)} items>'
                    else:
                        shown = repr(value)
                    result[key] = shown[:180]
                except Exception:
                    result[key] = '<value>'
            if len(result) >= 12:
                break
        return result
    def trace(frame, event, arg):
        nonlocal count
        count += 1
        if time.monotonic() - started > 1.5 or count > 18000:
            raise PracticeLimit('Paused: exercise exceeded the 1.5 s / 18,000 trace-event limit.')
        if frame.f_code.co_filename == '<practice>':
            if event in ('line', 'return'):
                key = id(frame)
                line = previous.pop(key, None)
                if line is not None and len(snapshots) < 100:
                    snapshots.append({'line': line, 'variables': safe_values(frame), 'output': output.getvalue()})
                if event == 'line' and frame.f_lineno > 0:
                    previous[key] = frame.f_lineno
        return trace
    namespace = {'__name__': '__main__', '__builtins__': dict(vars(builtins), input=read_input)}
    try:
        compiled = compile(code, '<practice>', 'exec')
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            sys.settrace(trace)
            try:
                exec(compiled, namespace, namespace)
            finally:
                sys.settrace(None)
    except BaseException as exc:
        sys.settrace(None)
        line = getattr(exc, 'lineno', None)
        tb = exc.__traceback__
        while tb:
            if tb.tb_frame.f_code.co_filename == '<practice>':
                line = tb.tb_lineno
            tb = tb.tb_next
        hints = {
            'SyntaxError': 'Check quotes, brackets and the colon after if / for.',
            'IndentationError': 'Use four spaces inside if / for blocks.',
            'NameError': 'Give this variable a value before using it.',
            'TypeError': 'Check the data types. Try str() or int() when needed.',
            'ValueError': 'Check that the input value has the expected format.',
            'ZeroDivisionError': 'A number cannot be divided by zero.',
            'EOFError': 'Open Input and add one line per input() call.',
        }
        error = {'type': type(exc).__name__, 'message': str(exc)[:500], 'line': line,
                 'hint': hints.get(type(exc).__name__, 'Change the code and the output will update automatically.')}
    result = {'output': output.getvalue(), 'error': error, 'steps': snapshots,
              'elapsed_ms': round((time.monotonic() - started) * 1000)}
    result['helper'] = diagnose(code, error)
    return json.dumps(result, ensure_ascii=False)
