#!/usr/bin/env python3
"""Probe Calculator: launch, screenshot keypad + toolbar area, dump prefs."""
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
log_path = Path('/tmp/iso-calc-probe.log')
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
    comp.shot('calc-probe-full')
    # top strip (toolbar area) enlarged 2x for inspection
    top = comp.shot('calc-probe-top', (0, 0, r['width'], 120))
    print('shots:', top, flush=True)
    print('LOG tail:', repr(log_path.read_text()[-1500:]), flush=True)
finally:
    comp.close()
    try:
        p.wait(timeout=15)
        print('exit:', p.returncode, flush=True)
    except subprocess.TimeoutExpired:
        p.kill()
    comp.dispose()
