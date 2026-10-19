"""Simple Calculator acceptance on the isolated compositor.

Keypad 2+2= commits a new row (fresh id) in the app's own Room history
database; the toolbar settings icon opens SettingsActivity and a real tap
flips the persisted decimal-mark preference; the keypad then types a
comma-decimal expression whose new history row (fresh id) proves the
preference reaches evaluation; the app closes and reopens on the same data
dir with the preference and the committed row intact; it stays live 31s
and exits 0 through its own back handling.
"""
import os
import shutil
import sqlite3
import time
from pathlib import Path

from .compositor import SwayError
from .helpers import read_pref_bool

# Keypad geometry at 640x800 (measured from rendered keys): all layers sit
# at the window origin, columns x centers 80/240/400/560, rows y:
# 348 '%^==', 449 '789x', 550 '456-', 650 '123+', 750 '0.C='.
KEY_1 = (80, 650)
KEY_2 = (240, 650)
KEY_5 = (240, 550)
KEY_PLUS = (560, 650)
KEY_EQUALS = (560, 750)
KEY_DECIMAL = (240, 750)
KEY_CLEAR = (400, 750)
# Toolbar settings icon (measured: SettingsActivity starts from this tap).
SETTINGS_ITEM = (592, 16)
# Settings list: drawn rows currently sit ~60px above their interactive hit
# rows. (320,440) is the measured interactive position of "Use comma as a
# decimal mark", and the preference flip below proves the tap reached that
# row.
SETTINGS_DECIMAL_ROW = (320, 440)

PREF_KEY = 'use_comma_as_decimal_mark'
PREFS_FILE = 'Prefs.xml'


def _db_snapshot(work, package):
    """Local copies of the live Room DB (db + wal + shm) for reading."""
    src = Path(work) / 'data' / package / 'databases'
    if not (src / 'calculator.db').exists():
        return None
    tmp = Path('/tmp/isakai-%d-calc-db' % os.getpid())
    if tmp.exists():
        shutil.rmtree(tmp)
    tmp.mkdir(parents=True)
    for suffix in ('calculator.db', 'calculator.db-wal', 'calculator.db-shm'):
        path = src / suffix
        if path.exists():
            shutil.copy2(path, tmp / suffix)
    return tmp / 'calculator.db'


def history_rows(work, package):
    """(id, formula, result) rows from the app's own history table."""
    snapshot = _db_snapshot(work, package)
    if snapshot is None:
        return []
    con = sqlite3.connect(str(snapshot))
    try:
        return list(con.execute('select id, formula, result from history order by id'))
    finally:
        con.close()


def newest_history_row(work, package):
    """The row with the highest id, or None."""
    rows = history_rows(work, package)
    return rows[-1] if rows else None


def wait_for_new_row(work, package, after_id, timeout=20.0):
    """The newest row with id > after_id, or None on timeout.

    Ids stay monotonic even when the app clears its history between
    sessions, so retained rows can never satisfy a fresh-row assertion.
    """
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        rows = history_rows(work, package)
        for row in reversed(rows):
            if row[0] > after_id:
                return row
        time.sleep(0.5)
    return None


def wait_for_pref(work, package, expected, timeout=6.0):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if read_pref_bool(work, package, PREFS_FILE, PREF_KEY) is expected:
            return expected
        time.sleep(0.3)
    return read_pref_bool(work, package, PREFS_FILE, PREF_KEY)


def result_zoom(comp, name):
    """Zoomed crop of the calculator display for evidence."""
    from PIL import Image
    # Room commits before the main-thread result update is presented.
    time.sleep(2.0)
    path = comp.shot(name)
    image = Image.open(path).convert('RGB')
    pixels = image.load()
    minx, miny, maxx, maxy = image.width, image.height, 0, 0
    for y in range(100, 300):
        for x in range(0, image.width):
            r, g, b = pixels[x, y]
            if r < 245 or g < 245 or b < 245:
                minx = min(minx, x)
                miny = min(miny, y)
                maxx = max(maxx, x)
                maxy = max(maxy, y)
    zoom = comp.evidence / (path.stem + '-zoom.png')
    crop = image.crop((max(0, minx - 10), max(0, miny - 10), maxx + 10, maxy + 10))
    crop = crop.resize((crop.width * 3, crop.height * 3), Image.NEAREST)
    crop.save(zoom)
    comp.steps.append({'step': name + '-zoom', 'shot': str(zoom),
                       'bbox': [minx, miny, maxx, maxy]})


def run(run_app, comp):
    steps = {}
    package = run_app.app['package']
    comp.shot('01-initial')
    # Fresh-row gate: ids recorded now can never satisfy the assertions
    # below, no matter what history the work dir retains.
    prior = newest_history_row(run_app.work, package)
    prior_id = prior[0] if prior else 0
    steps['baseline_row'] = prior
    for point in (KEY_2, KEY_PLUS, KEY_2, KEY_EQUALS):
        comp.click_window(*point)
        time.sleep(0.4)
    row = wait_for_new_row(run_app.work, package, prior_id)
    if row is None:
        comp.shot('02-no-result')
        raise SwayError('no new history row after 2+2= (baseline id %d)' % prior_id)
    row2_id = row[0]
    steps['history_row'] = [row[1], row[2]]
    if row[1] != '2+2' or row[2] != '4':
        comp.shot('02-no-result')
        raise SwayError('the app recorded %r -> %r, expected 2+2 -> 4'
                        % (row[1], row[2]))
    result_zoom(comp, '02-result')
    # Settings: the toolbar icon starts SettingsActivity; the row tap must
    # flip the preference the app itself persists.
    pref_before = read_pref_bool(run_app.work, package, PREFS_FILE, PREF_KEY)
    comp.click_window(*SETTINGS_ITEM)
    time.sleep(2.5)
    if 'SettingsActivity' not in run_app.read_log():
        raise SwayError('settings tap did not start SettingsActivity')
    steps['settings_opened'] = True
    comp.shot('03-settings')
    comp.click_window(*SETTINGS_DECIMAL_ROW)
    pref_after = wait_for_pref(run_app.work, package, not pref_before)
    steps['decimal_pref'] = [pref_before, pref_after]
    if pref_after is None or pref_after == pref_before:
        comp.shot('03-toggle-failed')
        raise SwayError('decimal-mark preference did not flip (before %r, '
                        'after %r)' % (pref_before, pref_after))
    if not run_app.alive():
        raise SwayError('app died toggling the decimal-mark preference')
    comp.shot('04-toggled')
    # Back to the calculator; the same keypad key must now type the decimal
    # mark the preference selects and the app must evaluate it.
    comp.key('Escape')
    time.sleep(1.5)
    if 'MainActivity' not in run_app.read_log():
        raise SwayError('back from settings did not return to MainActivity')
    steps['back_to_main'] = True
    prior_id = row2_id
    comp.click_window(*KEY_CLEAR)
    time.sleep(0.4)
    for point in (KEY_1, KEY_DECIMAL, KEY_5, KEY_PLUS, KEY_2, KEY_DECIMAL,
                  KEY_5, KEY_EQUALS):
        comp.click_window(*point)
        time.sleep(0.4)
    row = wait_for_new_row(run_app.work, package, prior_id)
    if row is None:
        comp.shot('05-no-decimal-result')
        raise SwayError('no new history row for a decimal keypad expression '
                        '(baseline id %d)' % prior_id)
    decimal_id = row[0]
    expected_formula = '1,5+2,5' if pref_after else '1.5+2.5'
    steps['decimal_row'] = [row[1], row[2]]
    if row[1] != expected_formula:
        comp.shot('05-no-decimal-result')
        raise SwayError('the app recorded %r, expected %r for the decimal '
                        'key with use_comma_as_decimal_mark=%r'
                        % (row[1], expected_formula, pref_after))
    try:
        value = float(row[2].replace(',', '.'))
    except ValueError:
        value = None
    if value is None or abs(value - 4.0) > 1e-9:
        comp.shot('05-no-decimal-result')
        raise SwayError('the app recorded result %r for %s, expected 4'
                        % (row[2], expected_formula))
    result_zoom(comp, '05-decimal-result')
    # Closed cleanly, reopened on the same data dir: the preference and the
    # committed decimal row must both survive.
    steps['exit_code'] = run_app.close()
    run_app.start()
    steps['reopened'] = True
    comp.shot('06-reopened')
    reopened_pref = read_pref_bool(run_app.work, package, PREFS_FILE, PREF_KEY)
    if reopened_pref is not pref_after:
        raise SwayError('decimal-mark preference did not survive the restart '
                        '(before restart %r, after %r)'
                        % (pref_after, reopened_pref))
    steps['decimal_row_persisted'] = any(
        r[0] == decimal_id and r[1] == expected_formula for r in history_rows(run_app.work, package))
    if not steps['decimal_row_persisted']:
        raise SwayError('row id %d (%s) did not survive the restart'
                        % (decimal_id, expected_formula))
    # Liveness: usable, not merely started.
    deadline = time.monotonic() + 31
    while time.monotonic() < deadline:
        if not run_app.alive():
            raise SwayError('app exited during the 31-second liveness window')
        if comp.app_node() is None:
            raise SwayError('window disappeared during the liveness window')
        time.sleep(1.0)
    steps['liveness_seconds'] = 31
    # Back on the main screen closes the app through its own back handling.
    comp.key('Escape')
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline and run_app.alive():
        time.sleep(0.2)
    if run_app.alive():
        raise SwayError('back on the main screen did not exit the app')
    code = run_app.process.returncode
    steps['back_exit_code'] = code
    if code != 0:
        raise SwayError('back exit was %s, expected 0: %s'
                        % (code, run_app.read_log()[-2000:]))
    time.sleep(1.0)
    if comp.app_node() is not None:
        raise SwayError('window survived the back exit')
    return steps


FLOW = run
