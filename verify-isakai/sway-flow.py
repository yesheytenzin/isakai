#!/usr/bin/env python3
"""Run a pinned real-app acceptance flow on the isolated sway compositor.

Usage: sway-flow.py {opencalc|calculator|notes} [--work PATH] [--evidence PATH]
Requires the isolated sway at /tmp/isakai-wayland-iy7f4069 (see
.verify-isakai/SKILL.md "Isolated compositor"). The vinput daemon starts
before the app so the seat advertises pointer+keyboard capabilities.
"""
import argparse
import json
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sway_backend.compositor import SwayCompositor, SwayError, isolated_env  # noqa: E402
from sway_backend import flows_opencalc, flows_calculator, flows_notes  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
APP_INDEX = {'opencalc': 0, 'calculator': 1, 'notes': 2}
FLOWS = {
    'opencalc': flows_opencalc.run,
    'calculator': flows_calculator.run,
    'notes': flows_notes.run,
}


class AppRun:
    def __init__(self, app, work, log_path, comp):
        self.app = app
        self.work = Path(work)
        self.log_path = Path(log_path)
        self.comp = comp
        self.process = None

    def start(self, startup_timeout=240):
        env = dict(isolated_env())
        env.update({
            'ISAKAI_NO_PRIVILEGE': '1',
            'ISAKAI_FRAMEWORK': str(ROOT / 'zig-out/framework'),
            'ISAKAI_WINDOW': '1',
            'ISAKAI_BRIDGE_DIR': '/tmp/isakai-%d-isosway' % os.getuid(),
            'ISAKAI_STAGE_DIR': '/tmp/isakai-%d-isosway-stage' % os.getuid(),
        })
        log_file = self.log_path.open('w')
        self.process = subprocess.Popen(
            [str(ROOT / 'zig-out/bin/isakai'), '--work', str(self.work),
             'launch', self.app['package']],
            env=env, stdout=log_file, stderr=subprocess.STDOUT,
            start_new_session=True)
        self._log_file = log_file
        deadline = time.monotonic() + startup_timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise SwayError('launch exited early (%s): %s'
                                % (self.process.returncode, self.read_log()[-2000:]))
            node = self.comp.app_node()
            if node is not None:
                self.comp.focus()
                time.sleep(1.0)
                return node
            time.sleep(0.3)
        raise SwayError('no mapped window within %ss' % startup_timeout)

    def alive(self):
        return self.process is not None and self.process.poll() is None

    def read_log(self):
        return self.log_path.read_text(errors='replace') if self.log_path.exists() else ''

    def close(self):
        self.comp.close()
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                break
            time.sleep(0.2)
        else:
            raise SwayError('app did not exit after the compositor close request')
        code = self.process.returncode
        if code != 0:
            raise SwayError('expected exit 0, got %s: %s' % (code, self.read_log()[-2000:]))
        time.sleep(1.0)
        if self.comp.app_node() is not None:
            raise SwayError('window survived the close request')
        return code

    def stop(self):
        if self.process and self.process.poll() is None:
            try:
                os.killpg(self.process.pid, signal.SIGTERM)
                self.process.wait(timeout=8)
            except (subprocess.TimeoutExpired, ProcessLookupError):
                try:
                    os.killpg(self.process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
        try:
            self._log_file.close()
        except AttributeError:
            pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('app', choices=sorted(FLOWS))
    parser.add_argument('--work', type=Path)
    parser.add_argument('--evidence', type=Path, default=ROOT / '.cache/isakai/evidence')
    parser.add_argument('--startup-timeout', type=float, default=240)
    args = parser.parse_args()
    apps = json.loads((ROOT / 'progress.json').read_text())['apps']
    app = apps[APP_INDEX[args.app]]
    args.evidence.mkdir(parents=True, exist_ok=True)
    work = args.work or ROOT / '.cache/isakai/baseline' / app['package']
    if not Path(work).exists():
        # fall back to the pinned recheck tree for this package
        for cand in sorted((ROOT / '.cache/isakai/recheck').glob('*')):
            reg = cand / 'registry.json'
            if reg.exists() and app['package'] in reg.read_text():
                work = cand
                break
    comp = SwayCompositor(app, args.evidence)
    log_path = args.evidence / (app['package'] + '.sway-' + args.app + '.log')
    run_app = AppRun(app, work, log_path, comp)
    try:
        run_app.start(args.startup_timeout)
        result = FLOWS[args.app](run_app, comp)
        result['work'] = str(work)
        print(json.dumps({'app': args.app, 'steps': comp.steps, 'result': result}, indent=1))
        print('PASS:', args.app, 'flow (isolated sway)')
        return 0
    except SwayError as failure:
        print('FAIL:', failure, file=sys.stderr)
        print(run_app.read_log()[-3000:], file=sys.stderr)
        return 1
    finally:
        run_app.stop()
        comp.dispose()


if __name__ == '__main__':
    sys.exit(main())
