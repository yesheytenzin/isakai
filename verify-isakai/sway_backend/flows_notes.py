"""Simple Notes acceptance flow on the isolated compositor.

The editor is intentionally driven through the same visible surface as a
user. Unicode is supplied through the compositor clipboard because the
virtual keyboard's key table is ASCII-oriented.
"""
import sqlite3
import subprocess
import time
from pathlib import Path

from .compositor import SwayError
from .vinput import isolated_env

EDITOR = (40, 110)
PLUS = (600, 15)
MARKER = "Isakai 日本語 – note"


def _clipboard(text):
    subprocess.run(["wl-copy", "--type", "text/plain"], input=text.encode("utf-8"),
                   env=isolated_env(), check=True, timeout=10)


def _notes(work, package):
    path = Path(work) / "data" / package / "databases" / "notes.db"
    if not path.exists():
        return []
    connection = sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True)
    try:
        return list(connection.execute("select id, title, value from notes order by id"))
    finally:
        connection.close()


def _saved_marker(work, package):
    return next((row for row in _notes(work, package) if row[2] == MARKER), None)


def _fragment_count(run_app):
    return run_app.read_log().count("Added fragment to active set")


def _wait_for_root(run_app, log_start, dialog):
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        roots = [line for line in run_app.read_log()[log_start:].splitlines()
                 if line.startswith("isakai: new root ")]
        if roots and ("android.app.DialogOverlayGroup" in roots[-1]) == dialog:
            return
        if not run_app.alive():
            break
        time.sleep(0.2)
    raise SwayError("Notes did not switch to the expected %s root" %
                    ("dialog" if dialog else "editor"))


def run(run_app, comp):
    steps = {}
    comp.shot("01-initial")
    time.sleep(2.0)
    base = _fragment_count(run_app)
    rows = _notes(run_app.work, run_app.app["package"])
    suffix = max((row[0] for row in rows), default=0) + 1
    titles = {row[1] for row in rows}
    while "N%d" % suffix in titles:
        suffix += 1
    title = "N%d" % suffix
    steps["title"] = title
    comp.click_window(*PLUS)
    time.sleep(1.0)
    if not run_app.alive():
        raise SwayError("Notes exited when opening the new-note dialog")
    comp.shot("02-create-dialog")
    comp.click_window(300, 345)
    comp.type_text(title)
    comp.shot("02-titled-dialog")
    comp.click_window(282, 461)
    time.sleep(2.0)
    if not run_app.alive():
        raise SwayError("Notes exited when confirming the new-note dialog")
    if _fragment_count(run_app) <= base:
        comp.shot("02-create-failed")
        raise SwayError("confirming the new-note dialog did not open an editor fragment")
    steps["editor_opened"] = True
    comp.shot("02-editor")
    comp.click_window(*EDITOR)
    comp.key("CTRL+A")
    _clipboard(MARKER)
    comp.key("CTRL+V")
    time.sleep(3.0)
    comp.shot("03-unicode")
    # The folder button opens the note picker and saves the current note
    # before leaving the editor.
    picker_log_start = len(run_app.read_log())
    comp.click_window(536, 15)
    _wait_for_root(run_app, picker_log_start, dialog=True)
    time.sleep(1.0)
    comp.shot("04-note-picker")
    if not run_app.alive():
        raise SwayError("Notes exited when opening the note picker")
    deadline = time.monotonic() + 10
    saved = None
    while time.monotonic() < deadline:
        saved = next((row for row in _notes(run_app.work, run_app.app["package"])
                      if row[1] == title and row[2] == MARKER), None)
        if saved is not None:
            break
        time.sleep(0.3)
    if saved is None:
        raise SwayError("the new note did not persist its exact Unicode contents")
    steps["saved_note"] = list(saved)
    editor_log_start = len(run_app.read_log())
    comp.key("Escape")
    _wait_for_root(run_app, editor_log_start, dialog=False)
    time.sleep(1.0)
    if not run_app.alive():
        raise SwayError("back from the note picker exited Notes")
    comp.shot("05-back-to-editor")
    steps["back_from_picker"] = True
    steps["exit_code"] = run_app.close()
    run_app.start()
    time.sleep(5.0)
    comp.shot("06-reopened")
    if saved not in _notes(run_app.work, run_app.app["package"]):
        raise SwayError("the saved note changed across process restart")
    comp.click_window(*EDITOR)
    comp.type_text("X")
    time.sleep(3.0)
    comp.shot("06-reopened-edited")
    comp.click_window(536, 15)
    time.sleep(1.5)
    expected_reopened = (saved[0], saved[1], MARKER + "X")
    deadline = time.monotonic() + 3
    reopened = None
    while time.monotonic() < deadline:
        reopened = next((row for row in _notes(run_app.work, run_app.app["package"])
                         if row[0] == saved[0]), None)
        if reopened == expected_reopened:
            break
        time.sleep(0.2)
    if reopened != expected_reopened:
        comp.shot("06-reopen-failed")
        raise SwayError("the reopened editor saved %r instead of %r" %
                        (reopened, expected_reopened))
    steps["reopened_exact_text"] = True
    deadline = time.monotonic() + 31
    while time.monotonic() < deadline:
        if not run_app.alive() or comp.app_node() is None:
            raise SwayError("Notes did not remain open for 31 seconds")
        time.sleep(1.0)
    steps["liveness_seconds"] = 31
    steps["final_exit_code"] = run_app.close()
    steps["saved"] = True
    return steps
