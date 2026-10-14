#!/usr/bin/env python3
"""Probe: type 12+34, press equals, screenshot, read history + clipboard."""
import json
import os
import subprocess
import sys
import time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from sway_backend.compositor import SwayCompositor, isolated_env  # noqa: E402
from sway_backend.helpers import history_results, last_equation  # noqa: E402

ROOT = Path('/home/tenzin/development/isakai')
apps = json.loads((ROOT / 'progress.json').read_text())['apps']
app = apps[0]
pkg = app['package']
work = ROOT / '.cache/isakai/recheck/opencalc10'

env = dict(isolated_env())
env.update({
    'ISAKAI_NO_PRIVILEGE': '1',
    'ISAKAI_FRAMEWORK': str(ROOT / 'zig-out/framework'),
    'ISAKAI_WINDOW': '1',
    'ISAKAI_BRIDGE_DIR': '/tmp/isakai-1000-isosway',
    'ISAKAI_STAGE_DIR': '/tmp/isakai-1000-isosway-stage',
})
comp = SwayCompositor(app, '/tmp')
log_path = Path('/tmp/iso-oc-equals.log')
log_file = log_path.open('w')
p = subprocess.Popen(
    [str(ROOT / 'zig-out/bin/isakai'), '--work', str(work), 'launch', pkg],
    env=env, stdout=log_file, stderr=subprocess.STDOUT, start_new_session=True)
try:
    node = comp.wait_for_window(120)
    comp.focus()
    time.sleep(1.5)
    comp.type_text('12+34')
    time.sleep(2.0)
    print('equation after type:', repr(last_equation(log_path.read_text())), flush=True)
    comp.shot('probe-typed')
    # press the = key (bottom-right of keypad)
    comp.click_window(560, 700)
    print('pressed equals area', flush=True)
    time.sleep(2.0)
    comp.shot('probe-equals')
    print('equation after equals:', repr(last_equation(log_path.read_text())), flush=True)
    entries = history_results(work, pkg)
    print('history last3:', [(e['a'], e['b']) for e in entries[-3:]], flush=True)
    # now long-press result row
    comp.click_window(40, 200, hold_ms=900)
    time.sleep(0.8)
    print('clipboard after longpress:', repr(comp.clip().strip()[:40]), flush=True)
    comp.shot('probe-copied')
finally:
    comp.close()
    try:
        p.wait(timeout=15)
        print('exit:', p.returncode, flush=True)
    except subprocess.TimeoutExpired:
        p.kill()
    comp.dispose()
