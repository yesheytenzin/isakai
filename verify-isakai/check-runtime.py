#!/usr/bin/env python3
"""Real JVM + production policy, without privileged UID/mount assertions.

Also exercises the desktop-bridge endpoint: a deliberately *deep* work
directory (the layout that used to overflow ``sun_path`` and silently
disable desktop integration) plus a live bridge daemon, so the probe's
socket round-trip proves both the short endpoint and its Landlock grant.
"""
import argparse
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--java', default=shutil.which('java'))
options = parser.parse_args()
java = Path(options.java).resolve()
compiler = java.parent / 'javac'
with tempfile.TemporaryDirectory(prefix='isakai-runtime-') as tmp:
    # Deep nesting on purpose: <work>/run/<package>/bridge.sock would exceed
    # the 107-byte limit, so the endpoint must not live under the work dir.
    work = Path(tmp) / ('nested-' + 'x' * 40) / ('deeper-' + 'y' * 40) / 'work'
    package = 'org.isakai.probe'
    data = work / 'data' / package
    code = work / 'code' / package
    stage = work / 'stage' / package
    for path in (data / 'cache/tmp', code, stage):
        path.mkdir(parents=True)
    legacy = work / 'run' / package
    legacy.mkdir(parents=True)
    assert len(str(legacy / 'bridge.sock')) > 107, 'test setup must exceed sun_path'
    # Endpoint shape shared with src/bridge/path.zig; any short /tmp/isakai-*
    # directory is valid, so the harness never duplicates the digest formula.
    endpoint = Path('/tmp') / f'isakai-{os.getuid()}-runtimeregress'
    shutil.rmtree(endpoint, ignore_errors=True)
    endpoint.mkdir(mode=0o711)
    (code / 'canary').write_text('readonly')
    (work / 'outside').write_text('outside')
    (work / 'other').write_text('other')
    framework = ROOT / 'zig-out/framework'
    subprocess.run([str(compiler), '--release', '17', '-cp', str(framework), '-d', str(code),
                    str(ROOT / 'verify-isakai/RuntimeProbe.java')], check=True)
    daemon = subprocess.Popen(
        [str(ROOT / 'zig-out/bin/isakai-dbus-bridge'), '--bridge-dir', str(endpoint),
         '--stage-dir', str(stage), '--uid', str(os.getuid())],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    try:
        socket = endpoint / 'bridge.sock'
        for _ in range(50):
            if socket.exists():
                break
            assert daemon.poll() is None, 'bridge exited: ' + daemon.stderr.read()
            import time
            time.sleep(0.1)
        assert socket.exists(), f'bridge socket {socket} never appeared'
        assert len(str(socket)) <= 107, 'endpoint must fit sun_path'
        env = dict(os.environ, ISAKAI_BRIDGE=str(endpoint))
        args = [str(ROOT / 'zig-out/bin/runtime-probe'), str(work), package, str(framework),
                str(java), '--enable-native-access=ALL-UNNAMED',
                f'-Djava.io.tmpdir={data}/cache/tmp', f'-Djava.library.path={framework}/lib', f'-Dprobe.uid={os.getuid()}',
                f'-Dprobe.stage={stage}', f'-Dprobe.code={code}/canary',
                f'-Dprobe.outside={work}/outside', f'-Dprobe.other={work}/other',
                '-cp', f'{code}:{framework}', 'org.isakai.runtime.IsakaiMain', str(data),
                'org.isakai.verify.RuntimeProbe', '-', package]
        print('RUN:', ' '.join(args), flush=True)
        result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                timeout=30, env=env)
        print(result.stdout)
        assert result.returncode == 0, result.returncode
        assert 'PROBE PASS' in result.stdout
        assert 'PROBE BRIDGE PASS' in result.stdout, 'bridge round-trip did not run or failed'
        assert (data / 'allowed.txt').read_text() == 'persistent-data'
        assert (code / 'canary').read_text() == 'readonly'
        assert (work / 'outside').read_text() == 'outside'
    finally:
        daemon.send_signal(signal.SIGTERM)
        try:
            daemon.wait(timeout=5)
        except subprocess.TimeoutExpired:
            daemon.kill()
        shutil.rmtree(endpoint, ignore_errors=True)
    print('PASS: JVM survived production confinement (deep work dir, live bridge round-trip);'
          ' this does not test privileged UID/mount setup.')
