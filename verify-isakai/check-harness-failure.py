#!/usr/bin/env python3
"""Fault injection for the harness, not evidence of app compatibility.
Real install/uninstall; deliberately failed launch prints the old success logs.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='isakai-failed-launch-') as tmp:
    wrapper = Path(tmp) / 'isakai'
    wrapper.write_text('''#!/usr/bin/env python3
import os, sys
if 'launch' in sys.argv[1:]:
    print('isakai: activity org.isakai.fixture.FixtureActivity runWindowed')
    print('isakai: run complete')
    sys.exit(42)
os.execv(os.environ['ISAKAI_REAL_BINARY'], [os.environ['ISAKAI_REAL_BINARY'], *sys.argv[1:]])
''')
    wrapper.chmod(0o755)
    env = dict(os.environ, ISAKAI=str(wrapper), ISAKAI_REAL_BINARY=str(ROOT / 'zig-out/bin/isakai'))
    p = subprocess.run(['python3', str(ROOT / 'verify-isakai/window-flow.py'), '--unprivileged', '--required'],
                       env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
    print(p.stdout)
    assert p.returncode == 1, p.returncode
    assert 'launch exited before a window appeared' in p.stdout
    assert 'PASS:' not in p.stdout
    assert 'uninstalled org.isakai.' in p.stdout
    print('PASS: fake lifecycle success logs plus exit 42 cannot pass E2E; cleanup ran')
