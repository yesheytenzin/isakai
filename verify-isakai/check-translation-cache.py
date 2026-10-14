#!/usr/bin/env python3
"""Exercise the CLI cache: reuse current output, replace legacy/stale output."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='isakai-translation-cache-') as temp:
    base = Path(temp)
    apk = base / 'fixture.apk'
    env = dict(os.environ, ISAKAI_NO_PRIVILEGE='1', ISAKAI_ALLOW_UNSIGNED='1',
               ISAKAI_FRAMEWORK=str(ROOT / 'zig-out/framework'), ISAKAI_WINDOW='0')
    def run(*command):
        result = subprocess.run(list(map(str, command)), env=env, cwd=ROOT,
                                capture_output=True, text=True, timeout=60)
        assert result.returncode == 0, result.stdout + result.stderr
        return result.stdout + result.stderr
    run(ROOT / 'zig-out/bin/fixture-apk', apk)
    cli = [ROOT / 'zig-out/bin/isakai', '--work', base / 'work']
    run(*cli, 'install', apk)
    run(*cli, 'launch', 'org.isakai.fixture')
    marker = next((base / 'work/code').rglob('.translated'))
    original = marker.read_bytes()
    assert original.startswith(b'translator:') and len(original.splitlines()[0]) == 75
    activity = marker.parent / 'org/isakai/fixture/FixtureActivity.class'
    good_class = activity.read_bytes()
    before = activity.stat().st_mtime_ns
    run(*cli, 'launch', 'org.isakai.fixture')
    assert activity.stat().st_mtime_ns == before, 'valid cache was unnecessarily regenerated'
    # Old installations only stored DEX digests, so they must be regenerated.
    marker.write_bytes(original.split(b'\n', 1)[1])
    activity.write_bytes(b'stale bytecode')
    run(*cli, 'launch', 'org.isakai.fixture')
    assert marker.read_bytes() == original and activity.read_bytes() == good_class
    # A different translator identity must also invalidate the same APK.
    marker.write_bytes(b'translator:' + b'0' * 64 + b'\n' + original.split(b'\n', 1)[1])
    activity.write_bytes(b'stale bytecode')
    run(*cli, 'launch', 'org.isakai.fixture')
    assert marker.read_bytes() == original and activity.read_bytes() == good_class
    run(*cli, 'uninstall', 'org.isakai.fixture')
print('PASS: CLI reuses valid translation and invalidates legacy/different-translator caches')
