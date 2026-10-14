"""Simple Notes acceptance flow on the isolated compositor.

The editor is intentionally driven through the same visible surface as a
user. Unicode is supplied through the compositor clipboard because the
virtual keyboard's key table is ASCII-oriented.
"""
import subprocess
import time
from pathlib import Path

from .compositor import SwayError
from .vinput import isolated_env

EDITOR = (320, 155)
MARKER = "Isakai 日本語 – note"


def _clipboard(text):
    subprocess.run(["wl-copy", "--type", "text/plain"], input=text.encode("utf-8"),
                   env=isolated_env(), check=True, timeout=10)


def _saved_marker(work, package):
    root = Path(work) / "data" / package
    for path in root.rglob("*"):
        if path.is_file():
            try:
                if MARKER.encode("utf-8") in path.read_bytes():
                    return path
            except OSError:
                continue
    return None


def run(run_app, comp):
    steps = {}
    comp.shot("01-initial")
    comp.click_window(*EDITOR)
    comp.key("CTRL+A")
    _clipboard(MARKER)
    comp.key("CTRL+V")
    time.sleep(1.0)
    comp.shot("02-unicode")
    if _saved_marker(run_app.work, run_app.app["package"]) is not None:
        steps["marker_visible_in_store"] = True

    # Back from the editor is the user-visible save boundary for Simple
    # Notes. The activity must return to the list rather than exit the app.
    comp.key("Escape")
    time.sleep(1.5)
    comp.shot("03-list")
    steps["returned_from_editor"] = True
    if run_app.alive() is False:
        raise SwayError("Notes exited while navigating back from the editor")

    if _saved_marker(run_app.work, run_app.app["package"]) is None:
        raise SwayError("Unicode note was not persisted after navigating back")

    steps["saved"] = True
    return steps
