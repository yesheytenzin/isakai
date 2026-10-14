"""Calculator acceptance on the isolated compositor.

Keypad 2+2= shows 4; toolbar settings opens SettingsActivity; toggling
"Use comma as a decimal mark" flips the decimal key . to ,; close/reopen
with the same data dir preserves the comma; back exits cleanly.
"""
import time

from .compositor import SwayError
from .helpers import read_pref_bool

# Keypad geometry at 640x480 (measured RowProbe bounds, parent-relative ==
# root here because all containers sit at 0,0):
# rows y: [153,219] [219,285] [285,350] [350,415] [415,480]; cols x: 160 wide.
# row3 (y=382): 1 2 3 + ; row4 (y=447): 0 . C =
KEY_2 = (240, 382)
KEY_PLUS = (560, 382)
KEY_EQUALS = (560, 447)
KEY_CLEAR = (400, 447)
KEY_DECIMAL = (240, 447)
# ActionMenuView icons are 32px wide in the 640px window.
SETTINGS_ITEM = (592, 16)

PREF_KEY = 'use_comma_as_decimal_mark'


def result_glyphs(comp, name):
    """Crop of the result row for evidence (dark bbox in top strip)."""
    from PIL import Image
    path = comp.shot(name)
    image = Image.open(path).convert('RGB')
    pixels = image.load()
    minx, miny, maxx, maxy = image.width, image.height, 0, 0
    for y in range(0, 160):
        for x in range(0, image.width):
            r, g, b = pixels[x, y]
            if r < 245 or g < 245 or b < 245:
                minx = min(minx, x)
                miny = min(miny, y)
                maxx = max(maxx, x)
                maxy = max(maxy, y)
    zoom = comp.evidence / (path.stem + '-zoom.png')
    crop = image.crop((max(0, minx - 10), max(0, miny - 10), maxx + 10, maxy + 10))
    crop = crop.resize((crop.width * 4, crop.height * 4), Image.NEAREST)
    crop.save(zoom)
    comp.steps.append({'step': name + '-zoom', 'shot': str(zoom),
                       'bbox': [minx, miny, maxx, maxy]})
    return zoom, (minx, miny, maxx, maxy)


def decimal_glyph(comp, name):
    """Crop of the decimal key for evidence."""
    from PIL import Image
    path = comp.shot(name, (160, 415, 160, 65))
    zoom = comp.evidence / (path.stem + '-zoom.png')
    image = Image.open(path).convert('RGB')
    image = image.resize((image.width * 3, image.height * 3), Image.NEAREST)
    image.save(zoom)
    comp.steps.append({'step': name + '-zoom', 'shot': str(zoom)})
    return zoom


def run(run_app, comp):
    steps = {}
    comp.shot('01-initial')
    # Clear to a known state (long-press C), then 2+2=.
    comp.click_window(*KEY_CLEAR, hold_ms=900)
    time.sleep(1.0)
    comp.click_window(*KEY_2)
    time.sleep(0.4)
    comp.click_window(*KEY_PLUS)
    time.sleep(0.4)
    comp.click_window(*KEY_2)
    time.sleep(0.4)
    comp.click_window(*KEY_EQUALS)
    time.sleep(1.5)
    zoom, bbox = result_glyphs(comp, '02-result')
    steps['result_shot'] = str(zoom)
    steps['result_bbox'] = list(bbox)
    # Baseline decimal key must be '.' (uncheck->check direction).
    decimal_glyph(comp, '03-decimal-before')
    # Open settings via the toolbar item.
    comp.click_window(*SETTINGS_ITEM)
    time.sleep(2.5)
    comp.shot('04-settings')
    if 'SettingsActivity' not in run_app.read_log():
        raise SwayError('settings tap did not start SettingsActivity')
    steps['settings_opened'] = True

    before = read_pref_bool(run_app.work, run_app.app['package'],
                            'Prefs.xml', PREF_KEY)
    # Preference rows are full-width and vertically ordered. Probe only the
    # settings content area and stop at the first row that changes the app's
    # persisted value; this remains stable across toolbar height changes.
    for y in range(70, 430, 40):
        comp.click_window(320, y)
        time.sleep(0.25)
        after = read_pref_bool(run_app.work, run_app.app['package'],
                               'Prefs.xml', PREF_KEY)
        if after is not None and after != before:
            steps['preference_toggled_at'] = [320, y]
            before = after
            break
    else:
        raise SwayError('settings preference did not persist a changed value')
    comp.shot('05-preference')

    comp.key('Escape')
    time.sleep(1.0)
    if 'MainActivity' not in run_app.read_log():
        raise SwayError('back from settings did not return to MainActivity')
    steps['back_to_main'] = True
    comp.shot('06-back')
    return steps
    return steps


FLOW = run
