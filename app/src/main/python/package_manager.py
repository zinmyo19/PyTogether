"""Small, explicit runtime package manager for Chaquopy Android.

Only trusted, universal (pure-Python) wheels are accepted. This module is not a
sandbox: installing a package executes its setup metadata and imported code.
"""
import importlib
import json
import os
import re
import sys
import threading
from email.parser import Parser

PYPI = "https://pypi.org/simple"
_NAME = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9._-]{0,212}[A-Za-z0-9])?$")
_LOCK = threading.RLock()


def _fail(message, **extra):
    result = {"ok": False, "error": message}
    result.update(extra)
    return result


def validate_name(value):
    """Return a normalized package name or raise ValueError.

    Deliberately accepts names only, not version constraints, options, paths,
    URLs, VCS references, or shell syntax.
    """
    if not isinstance(value, str):
        raise ValueError("Package name must be text.")
    name = value.strip()
    if not name or len(name) > 214 or not _NAME.fullmatch(name):
        raise ValueError("Enter a package name only (for example: requests). URLs, paths, and options are not allowed.")
    if any(ch in name for ch in "/\\:@%?$#'\";&|<>`\n\r\t "):
        raise ValueError("URLs, shell characters, spaces, and pip options are not allowed.")
    return re.sub(r"[-_.]+", "-", name).lower()


def activate(path):
    """Add app-private packages to imports, idempotently; return absolute path."""
    path = os.path.abspath(os.fspath(path))
    os.makedirs(path, exist_ok=True)
    if path not in sys.path:
        sys.path.insert(0, path)
    importlib.invalidate_caches()
    return path


def installed(path):
    path = activate(path)
    names = set()
    try:
        entries = os.listdir(path)
    except OSError:
        entries = []
    for entry in entries:
        if entry.endswith(".dist-info"):
            metadata = os.path.join(path, entry, "METADATA")
            name = None
            try:
                with open(metadata, encoding="utf-8") as stream:
                    name = Parser().parsestr(stream.read(65536)).get("Name")
            except OSError:
                pass
            names.add((name or entry[:-10].split("-")[0]).replace("_", "-").lower())
        elif entry.endswith(".egg-info"):
            names.add(entry[:-9].split("-")[0].replace("_", "-"))
    return sorted(names)


def install(name, path):
    """Install one package and return a JSON-compatible result dictionary."""
    try:
        normalized = validate_name(name)
    except ValueError as exc:
        return _fail(str(exc))
    path = activate(path)
    os.makedirs(path, exist_ok=True)
    with _LOCK:
        before = set(installed(path))
        try:
            from pip._internal.cli.main import main as pip_main
        except Exception as exc:
            return _fail("pip is not bundled in this build; add the exact Chaquopy pip dependency before enabling Install.", detail=repr(exc))
        argv = [
            "install", normalized, "--target", path,
            "--isolated", "--index-url", PYPI, "--timeout", "20", "--retries", "1",
            "--only-binary=:all:", "--no-compile", "--disable-pip-version-check",
            "--no-input", "--no-cache-dir", "--platform", "any",
            "--implementation", "py", "--abi", "none",
        ]
        old = os.environ.get("PIP_NO_INPUT")
        os.environ["PIP_NO_INPUT"] = "1"
        try:
            code = pip_main(argv)
        except SystemExit as exc:
            code = exc.code if isinstance(exc.code, int) else 1
        except BaseException as exc:
            return _fail("pip failed without completing the install; nothing is reported as successful.", detail=repr(exc))
        finally:
            if old is None:
                os.environ.pop("PIP_NO_INPUT", None)
            else:
                os.environ["PIP_NO_INPUT"] = old
        if code not in (0, None):
            return _fail("No compatible pure-Python wheel was installed. Native/platform wheels and source distributions are unsupported.", exit_code=code)
        activate(path)
        after = set(installed(path))
        if normalized not in after:
            return _fail("pip returned success but the requested package is not present; installation was not accepted.", installed=sorted(after), new=sorted(after - before))
        return {"ok": True, "name": normalized, "installed": sorted(after), "path": path}


def install_json(name, path):
    """Java-friendly JSON wrapper; never turns a failure into fake success."""
    try:
        return json.dumps(install(name, path), ensure_ascii=False)
    except BaseException as exc:
        return json.dumps(_fail("Unexpected package-manager failure; installation status is unknown.", detail=repr(exc)), ensure_ascii=False)


def installed_json(path):
    try:
        return json.dumps({"ok": True, "installed": installed(path)}, ensure_ascii=False)
    except BaseException as exc:
        return json.dumps(_fail("Could not read installed packages.", detail=repr(exc)), ensure_ascii=False)
