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
from sway_backend import flows_opencalc, flows_calculator, flows_notes, helpers  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from pinned_apps import load_apps  # noqa: E402

APP_INDEX = {'opencalc': 0, 'calculator': 1, 'notes': 2}
FLOWS = {
    'opencalc': flows_opencalc.run,
    'calculator': flows_calculator.run,
    'notes': flows_notes.run,
}


class AppRun:
    def __init__(self, app, work, log_path, comp, privileged=False):
        self.app = app
        self.work = Path(work)
        self.log_path = Path(log_path)
        self.comp = comp
        self.privileged = privileged
        self.process = None
        self._started = False

    def _wait_for_bridge(self, uid, log_base, timeout=15):
        """A privileged launch must show the app's bridge client connecting
        as its real uid; the launch log line is the host-observable proof."""
        needle = 'bridge: client uid %d connected' % uid
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if needle in self.read_log()[log_base:]:
                return
            if self.process.poll() is not None:
                break
            time.sleep(0.2)
        raise SwayError('privileged launch has no bridge connection for uid %d: %s'
                        % (uid, self.read_log()[log_base:][-1000:]))

    def start(self, startup_timeout=240):
        env = dict(isolated_env())
        if self.privileged:
            # Real default confinement: no override, no dev uid. The CLI
            # elevates its uid-helper through polkit itself (M6f); uid-helper
            # brokers the compositor socket into the app sandbox, and the
            # bridge endpoint lives under managed work/run/run dirs, so the
            # dev-mode ISAKAI_BRIDGE_DIR/ISAKAI_STAGE_DIR overrides must
            # stay unset.
            for key in ('ISAKAI_NO_PRIVILEGE', 'ISAKAI_CONFINEMENT',
                        'ISAKAI_BRIDGE_DIR', 'ISAKAI_STAGE_DIR'):
                env.pop(key, None)
            env.update({
                'ISAKAI_NO_PRIVILEGE': '0',
                'ISAKAI_FRAMEWORK': str(ROOT / 'zig-out/framework'),
                'ISAKAI_WINDOW': '1',
            })
        else:
            env.update({
                'ISAKAI_NO_PRIVILEGE': '1',
                'ISAKAI_FRAMEWORK': str(ROOT / 'zig-out/framework'),
                'ISAKAI_WINDOW': '1',
                'ISAKAI_BRIDGE_DIR': '/tmp/isakai-%d-isosway' % os.getuid(),
                'ISAKAI_STAGE_DIR': '/tmp/isakai-%d-isosway-stage' % os.getuid(),
            })
        if self.privileged:
            # An interrupted previous run can orphan the confined app, and
            # the registry then reports it live to the next launch.
            helpers.reap_app(self.work, self.app['package'])
        log_base = len(self.read_log()) if self._started else 0
        log_file = self.log_path.open('a' if self._started else 'w')
        self._started = True
        self.process = subprocess.Popen(
            [str(ROOT / 'zig-out/bin/isakai'), '--work', str(self.work),
             'launch', self.app['package']],
            env=env, stdout=log_file, stderr=subprocess.STDOUT,
            start_new_session=True)
        if self.privileged:
            # After (re)launch the app may have created further private
            # dirs; re-apply the read grant so the flows' persistence
            # readers can see app-recorded state.
            helpers.grant_data_read(self.work, self.app['package'])
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
                if self.privileged:
                    self._wait_for_bridge(helpers.app_uid(self.work, self.app['package']),
                                          log_base, timeout=10)
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
        self._log_file.close()
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
        if self.privileged:
            helpers.reap_app(self.work, self.app['package'])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('app', choices=sorted(FLOWS))
    parser.add_argument('--work', type=Path)
    parser.add_argument('--evidence', type=Path, default=ROOT / '.cache/isakai/evidence')
    parser.add_argument('--startup-timeout', type=float, default=240)
    parser.add_argument('--privileged', action='store_true',
                        help='drive the pinned app under its real default per-app '
                             'confinement (install tree must have real uids; '
                             'requires pkexec authorization)')
    parser.add_argument('--shutdown-broker', action='store_true',
                        help='stop the shared root ACL broker after this run')
    args = parser.parse_args()
    apps = load_apps()
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
    run_app = AppRun(app, work, log_path, comp, privileged=args.privileged)
    socket_path = None
    uid = None
    try:
        if args.privileged:
            helpers.grant_data_read(work, app['package'])
            uid = helpers.app_uid(work, app['package'])
            socket_path = (Path(isolated_env()['XDG_RUNTIME_DIR'])
                           / isolated_env()['WAYLAND_DISPLAY']).resolve()
            helpers.grant_compositor_socket(socket_path, uid)
        run_app.start(args.startup_timeout)
        result = FLOWS[args.app](run_app, comp)
        result['work'] = str(work)
        result['confinement'] = ('privileged default per-app uid'
                                 if args.privileged else
                                 'same-UID development mode')
        print(json.dumps({'app': args.app, 'steps': comp.steps, 'result': result}, indent=1))
        print('PASS:', args.app, ('flow (isolated sway, default confinement)'
                                  if args.privileged else 'flow (isolated sway)'))
        return 0
    except SwayError as failure:
        print('FAIL:', failure, file=sys.stderr)
        print(run_app.read_log()[-3000:], file=sys.stderr)
        return 1
    finally:
        # Every cleanup step is attempted independently: a failed app stop or
        # socket revoke must not leave the app-data ACL readable. Any failure
        # fails the process even if the flow itself printed PASS.
        cleanup_errors = []
        try:
            run_app.stop()
        except Exception as failure:
            cleanup_errors.append(('app stop', failure))
        if args.privileged:
            if socket_path is not None and uid is not None:
                try:
                    helpers.revoke_compositor_socket(socket_path, uid)
                except Exception as failure:
                    cleanup_errors.append(('compositor socket ACL revoke', failure))
            try:
                helpers.revoke_data_read(work, app['package'])
            except Exception as failure:
                cleanup_errors.append(('data ACL revoke', failure))
        try:
            comp.dispose()
        except Exception as failure:
            cleanup_errors.append(('compositor dispose', failure))
        if args.shutdown_broker:
            try:
                helpers.shutdown_broker()
            except Exception as failure:
                cleanup_errors.append(('broker shutdown', failure))
        if cleanup_errors:
            for label, failure in cleanup_errors:
                print('CLEANUP FAIL: %s: %s' % (label, failure), file=sys.stderr)
            sys.exit(1)


if __name__ == '__main__':
    sys.exit(main())
