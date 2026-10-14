#!/usr/bin/env python3
"""Probe Calculator toolbar: click each menu item, observe which opens Settings."""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from sway_backend.compositor import SwayCompositor, isolated_env  # noqa: E402

ROOT = Path('/home/tenzin/development/isakai')
apps = json.loads((ROOT / 'progress.json').read_text())['apps']
app = apps[1]
pkg = app['package']
work = ROOT / '.cache/isakai/recheck/calculator2'

env = dict(isolated_env())
env.update({
    'ISAKAI_NO_PRIVILEGE': '1',
    'ISAKAI_FRAMEWORK': str(ROOT / 'zig-out/framework'),
    'ISAKAI_WINDOW': '1',
    'ISAKAI_BRIDGE_DIR': '/tmp/isakai-1000-isosway',
    'ISAKAI_STAGE_DIR': '/tmp/isakai-1000-isosway-stage',
})
comp = SwayCompositor(app, '/tmp')
log_path = Path('/tmp/iso-calc-menu.log')
log_file = log_path.open('w')
p = subprocess.Popen(
    [str(ROOT / 'zig-out/bin/isakai'), '--work', str(work), 'launch', pkg],
    env=env, stdout=log_file, stderr=subprocess.STDOUT, start_new_session=True)
try:
    node = comp.wait_for_window(180)
    comp.focus()
    time.sleep(2.5)
    r = node['rect']
    print('rect:', r, flush=True)
    # The rendered toolbar icons are centered at x=528, 560, 592 and 624.
    base_log_len = len(log_path.read_text())
    for i, x in enumerate((592, 528, 560, 624)):
        y = 12
        comp.click_window(x, y)
        time.sleep(2.0)
        new = log_path.read_text()[base_log_len:]
        base_log_len += len(new)
        print('--- click item %d at (%d,%d) ---' % (i, x, y), flush=True)
        for line in new.strip().splitlines()[-12:]:
            print('   ', line[:160], flush=True)
        if comp.app_node() is not None:
            comp.shot('calc-menu-%d' % i)
        else:
            print('>>> app window disappeared', flush=True)
            break
        # if a new activity opened, go back
        if 'SettingsActivity' in new or 'UnitConverter' in new or 'AboutActivity' in new:
            print('>>> activity change detected, pressing back', flush=True)
            comp.key('Escape')
            time.sleep(2.0)
            new2 = log_path.read_text()[base_log_len:]
            base_log_len += len(new2)
            for line in new2.strip().splitlines()[-8:]:
                print('    ', line[:160], flush=True)
finally:
    comp.close()
    try:
        p.wait(timeout=15)
        print('exit:', p.returncode, flush=True)
    except subprocess.TimeoutExpired:
        p.kill()
    comp.dispose()
