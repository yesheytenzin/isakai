#!/usr/bin/env python3
"""Drive the shipped fixture through Hyprland; default mode requires privilege.
No fake success: missing prerequisites exit 77 (or 1 with --required).
Artifacts are retained. --unprivileged is explicitly reduced R1 coverage.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import threading
import uuid

ROOT = Path(__file__).resolve().parents[1]


def reserve_global_uids(work):
    """Uids are global, but nextUid only scans one work tree's registry.

    The C7 pinned apps already own 200000+. Seed this isolated gate
    registry with those uids so the fixture install picks a free one
    instead of failing UidTaken in the root helper. The reservations are
    inert records in a throwaway work tree and are never uninstalled.
    """
    pinned = ROOT / '.cache/isakai/priv/registry.json'
    if not pinned.is_file():
        return
    try:
        packages = json.loads(pinned.read_text())
    except (OSError, json.JSONDecodeError):
        return
    reserved = {}
    for name, rec in packages.items():
        uid = rec.get('uid')
        if not isinstance(uid, int):
            continue
        key = 'reserved.uid.%d' % uid
        reserved[key] = {
            'name': key,
            'uid': uid,
            'data_dir': str(work / 'reserved' / key),
            'apk': str(work / 'reserved' / key),
            'installed_at': 0,
            'state': {'status': 'installed'},
        }
    if reserved:
        (work / 'registry.json').write_text(json.dumps(reserved))
        print('RESERVE: uids held globally:', sorted(r['uid'] for r in reserved.values()), flush=True)


def check(ok, message):
    if not ok:
        raise AssertionError(message)


def run(argv, **kw):
    print('RUN:', ' '.join(map(str, argv)), flush=True)
    capture = 'stdout' not in kw
    if capture:
        kw.update(stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    result = subprocess.run(list(map(str, argv)), check=True, text=True, timeout=kw.pop('timeout', 30), **kw)
    if capture and result.stdout:
        print(result.stdout, end='', flush=True)
    return result


def wait_for(predicate, timeout, message):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(.1)
    raise AssertionError(message)


def proc_status(pid):
    try:
        return (Path('/proc') / str(pid) / 'status').read_text()
    except (FileNotFoundError, PermissionError):
        return ''


def app_java_pids(uid):
    """PIDs of the confined app JVM (IsakaiMain on its command line)."""
    found = []
    for proc in Path('/proc').glob('[0-9]*'):
        try:
            if proc.stat().st_uid != uid:
                continue
            cmdline = (proc / 'cmdline').read_bytes().replace(b'\0', b' ').decode('utf-8', errors='replace')
        except (FileNotFoundError, PermissionError):
            continue
        if 'org.isakai.runtime.IsakaiMain' in cmdline:
            found.append(int(proc.name))
    return found


def check_sandbox(uid, elevate=None):
    """Host-observable confinement proof for the running app JVM.

    Uses only existing kernel diagnostics (/proc); no probe class or
    fixture file. Fails loudly when isolation is not in effect. `elevate`
    supplies the privileged runner for /proc reads that another uid can
    never see (mount namespace), without granting the rest of the check.
    """
    def ns_path(pid):
        try:
            return os.readlink('/proc/%d/ns/mnt' % pid)
        except OSError as error:
            if elevate is None:
                raise AssertionError('could not read mount namespace of app JVM %d (needs %r)' % (pid, error.errno))
            result = subprocess.run(elevate + ['readlink', '/proc/%d/ns/mnt' % pid],
                                    capture_output=True, text=True, timeout=10)
            check(result.returncode == 0, 'elevated ns/mnt read failed for app JVM %d: %s'
                  % (pid, result.stderr.strip()[:120]))
            return result.stdout.strip()
    pids = app_java_pids(uid)
    check(pids, 'no app JVM runs as the confined uid %d' % uid)
    own_ns = os.readlink('/proc/self/ns/mnt')
    report = []
    for pid in pids:
        status = proc_status(pid)
        check(status, 'could not read /proc/%d/status' % pid)
        fields = {}
        for line in status.splitlines():
            if ':' in line:
                key, value = line.split(':', 1)
                fields[key.strip()] = value.strip()
        check(fields.get('Uid') == '%d\t%d\t%d\t%d' % (uid, uid, uid, uid), 'app JVM uid is not %d: %s' % (uid, fields.get('Uid')))
        check(fields.get('Gid') == '%d\t%d\t%d\t%d' % (uid, uid, uid, uid), 'app JVM gid is not %d: %s' % (uid, fields.get('Gid')))
        # setgroups(0) must leave the group list empty; some kernels
        # render it as a blank field, so only reject non-empty lists.
        groups = (fields.get('Groups') or '').split()
        check(not groups, 'app JVM has supplementary groups: %r' % fields.get('Groups'))
        check(fields.get('NoNewPrivs') == '1', 'NO_NEW_PRIVS is not set on the app JVM')
        check(fields.get('Seccomp') == '2', 'seccomp filter is not enforced on the app JVM: %s' % fields.get('Seccomp'))
        pid_ns = ns_path(pid)
        check(pid_ns != own_ns, 'app JVM shares the harness mount namespace')
        report.append('pid=%d uid=%s nnp=%s seccomp=%s mnt=%s' % (pid, fields.get('Uid'), fields.get('NoNewPrivs'), fields.get('Seccomp'), pid_ns))
    return report


def clients(package):
    items = json.loads(subprocess.check_output(['hyprctl', '-j', 'clients'], text=True, timeout=5))
    return [c for c in items if c.get('class') == package and c.get('mapped')]


def dispatch(action, address):
    check(re.fullmatch(r'0x[0-9a-fA-F]+', address), 'invalid window address')
    version = json.loads(subprocess.check_output(['hyprctl', '-j', 'version'], text=True))['version']
    minor = int(version.split('.')[1])
    if minor >= 55:
        method = 'hl.dsp.focus' if action == 'focus' else 'hl.dsp.window.close'
        return ['hyprctl', 'dispatch', method + '({window="address:' + address + '"})']
    return ['hyprctl', 'dispatch', 'focuswindow' if action == 'focus' else 'closewindow', 'address:' + address]


def screenshot(client, path):
    x, y = client['at']
    w, h = client['size']
    run(['grim', '-g', f'{x},{y} {w}x{h}', path])


def settled_client(package, address, timeout=10):
    """Wait until the mapped window's position and size stop changing.

    The fixture maps a taller transient surface and then settles; the gate
    captures one opened screenshot per iteration at this stable geometry.
    """
    deadline = time.monotonic() + timeout
    last = None
    stable_since = 0.0
    while time.monotonic() < deadline:
        matches = [c for c in clients(package) if c.get('address') == address]
        if matches:
            here = (tuple(matches[0]['at']), tuple(matches[0]['size']))
            if here == last:
                if time.monotonic() - stable_since >= 0.5:
                    return matches[0]
            else:
                last = here
                stable_since = time.monotonic()
        time.sleep(.1)
    raise AssertionError('window geometry did not settle: ' + package)


def find_jdk():
    candidates = list(Path('/usr/lib/jvm').glob('*/bin/javac'))
    if shutil.which('javac'):
        candidates.append(Path(shutil.which('javac')).resolve())
    choices = []
    for compiler in candidates:
        version = subprocess.run([str(compiler), '-version'], capture_output=True, text=True, timeout=5)
        match = re.search(r'javac (\d+)', version.stdout + version.stderr)
        if version.returncode == 0 and match and int(match[1]) >= 17:
            choices.append((int(match[1]), compiler.resolve()))
    check(choices, 'a JDK 17+ compiler is required')
    compiler = max(choices)[1]
    return compiler.with_name('java'), compiler


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--required', action='store_true', default=os.getenv('ISAKAI_E2E_REQUIRED') == '1')
    parser.add_argument('--unprivileged', action='store_true')
    parser.add_argument('--elevate', choices=('sudo', 'pkexec'), default='sudo',
                        help='privileged elevation mechanism; sudo -n stays the default')
    parser.add_argument('--artifacts', type=Path,
                        help='durable evidence directory (must not exist)')
    opts = parser.parse_args()
    # pkexec mode drops ISAKAI_NONINTERACTIVE so isakai itself elevates its
    # uid-helper through the same polkit pathway instead of sudo -n.
    noninteractive_launch = opts.elevate == 'sudo'
    required = ['hyprctl', 'grim', 'wtype', 'javac', 'java']
    if not opts.unprivileged:
        required.extend(['getfacl', 'setfacl', opts.elevate])
    missing = [c for c in required if not shutil.which(c)]
    if not os.getenv('WAYLAND_DISPLAY') or not os.getenv('XDG_RUNTIME_DIR'):
        missing.append('Wayland environment')
    if not opts.unprivileged:
        try:
            print('AUTHORIZE: approve the %s prompt for the privileged gate' % opts.elevate,
                  flush=True)
            probe = subprocess.run(
                ([opts.elevate, '-n', 'true'] if opts.elevate == 'sudo'
                 else [opts.elevate, shutil.which('true') or '/usr/bin/true']),
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=600)
            authority_ok = probe.returncode == 0
        except subprocess.TimeoutExpired:
            authority_ok = False
        if not authority_ok:
            missing.append('%s authorization' % opts.elevate)
    if missing:
        print(('FAIL' if opts.required else 'SKIP') + ': ' + ', '.join(missing))
        return 1 if opts.required else 77
    find_jdk()  # prerequisite check only; no probe compilation remains
    # Do not rebuild or conceal a missing/broken binary inside the test.
    binary = Path(os.environ.get('ISAKAI', ROOT / 'zig-out/bin/isakai')).resolve()
    fixture = ROOT / 'zig-out/bin/fixture-apk'
    check(binary.is_file() and fixture.is_file(), 'build isakai and fixture-apk first')
    artifacts = opts.artifacts.resolve() if opts.artifacts else Path(tempfile.mkdtemp(prefix='isakai-e2e-'))
    artifacts.mkdir(parents=True, exist_ok=not opts.artifacts)
    artifacts.chmod(0o755)
    print('ARTIFACTS:', artifacts, flush=True)
    work = artifacts / 'work'
    work.mkdir(mode=0o755)
    reserve_global_uids(work)
    framework = artifacts / 'framework'
    shutil.copytree(ROOT / 'zig-out/framework', framework)
    package = 'org.isakai.e' + uuid.uuid4().hex[:12]
    peer = package + '.peer'
    env = dict(os.environ)
    for key in ('ISAKAI_CONFINEMENT', 'ISAKAI_BRIDGE_DIR', 'ISAKAI_STAGE_DIR', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'LD_PRELOAD'):
        env.pop(key, None)
    env.update(ISAKAI_NO_PRIVILEGE='1' if opts.unprivileged else '0',
               ISAKAI_NONINTERACTIVE='1' if noninteractive_launch else '0',
               ISAKAI_ALLOW_UNSIGNED='1', ISAKAI_FRAMEWORK=str(framework), ISAKAI_WINDOW='1')
    elevate_prefix = [opts.elevate, '-n'] if opts.elevate == 'sudo' else [opts.elevate]

    def elevate(argv, **kw):
        return run(elevate_prefix + list(map(str, argv)), **kw)
    installed = []
    process = None
    socket_acl = None
    socket_path = None
    try:
        for name in ([package] if opts.unprivileged else [package, peer]):
            apk = artifacts / (name + '.apk')
            run([fixture, apk, name, '1'])
            # Track attempted installs as well: a failed install must not leave accounts.
            installed.append(name)
            run([binary, '--work', work, 'install', apk], env=env, timeout=300)
        registry = json.loads((work / 'registry.json').read_text())
        record = registry[package]
        code = Path(record['apk'])
        data = Path(record['data_dir'])
        uid = record['uid']
        if not opts.unprivileged:
            socket_path = Path(env['WAYLAND_DISPLAY'])
            if not socket_path.is_absolute():
                socket_path = Path(env['XDG_RUNTIME_DIR']) / socket_path
            socket_acl = subprocess.check_output(['getfacl', '-p', str(socket_path)], text=True)
            # Grant only the temporary app UID access, then restore the ACL.
            elevate(['setfacl', '-m', f'u:{uid}:rw', socket_path])
            # Filesystem tripwires (mode 666: DAC would allow the app uid,
            # so only sandbox denial keeps them intact). No in-sandbox
            # writer exists without the deleted probe class, so these
            # assert accidental damage only; in-sandbox write-denial and
            # cross-app read-denial stay explicitly blocked (see N1).
            canary = code / 'readonly-canary'
            canary.write_text('readonly')
            canary.chmod(0o666)
            outside = artifacts / 'outside-canary'
            outside.write_text('outside')
            outside.chmod(0o666)
            other = Path(registry[peer]['data_dir']) / 'secret'
            elevate(['touch', other])
            check(canary.read_text() == 'readonly' and outside.read_text() == 'outside', 'canaries changed')
        for iteration, close_mode in enumerate(('compositor', 'back')):
            log_path = artifacts / f'launch-{iteration}.log'
            with log_path.open('w') as log:
                process = subprocess.Popen([str(binary), '--work', str(work), 'launch', package], env=env,
                                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, start_new_session=True)
                def record_output():
                    with log_path.open("a") as captured:
                        for line in process.stdout:
                            captured.write(line)
                            captured.flush()
                reader = threading.Thread(target=record_output)
                reader.start()
                def window_ready():
                    check(process.poll() is None, 'launch exited before a window appeared: ' + log_path.read_text())
                    matches = clients(package)
                    return matches[0] if matches else None
                client = wait_for(window_ready, 15, 'no real mapped surface')
                client = settled_client(package, client['address'])
                run(dispatch('focus', client['address']))
                if not opts.unprivileged:
                    # Confinement proof on the live windowed app: fail the
                    # gate when the sandbox is not in effect.
                    sandbox_report = check_sandbox(uid, elevate_prefix)
                    print('\n'.join(sandbox_report), flush=True)
                    # The desktop bridge must have accepted the confined
                    # client; the socket round-trip is exercised by the real
                    # windowed launch, not a headless probe. Assert it, do not
                    # merely note it.
                    bridge_needle = 'bridge: client uid %d connected' % uid
                    wait_for(lambda: bridge_needle in log_path.read_text(), 10,
                             'desktop bridge did not accept confined uid %d' % uid)
                    with (artifacts / 'isolation.log').open('a') as iso:
                        iso.write('iteration %d (%s close):\n' % (iteration, close_mode))
                        iso.write('\n'.join(sandbox_report) + '\n')
                        iso.write('bridge-connected-at-open: True\n')
                # R1: assert liveness continuously, not merely once at the end.
                deadline = time.monotonic() + (31 if iteration == 0 else 1)
                while time.monotonic() < deadline:
                    check(process.poll() is None, 'window runtime exited early: ' + log_path.read_text())
                    check(clients(package), 'surface disappeared early')
                    time.sleep(.2)
                # One settled-geometry capture proves the mapped window. The
                # host keyboard text path does not reach the fixture EditText
                # (unlike the isolated compositor's vinput path used by the
                # pinned app flows), so this gate does NOT assert typed text:
                # the old byte-diff "input" pass compared two differently
                # sized captures and was not supported. See N1 for the
                # limitation and the pinned flows for real text input.
                client = settled_client(package, client['address'])
                geometry = (tuple(client['at']), tuple(client['size']))
                run(dispatch('focus', client['address']))
                time.sleep(.3)
                screenshot(client, artifacts / f'opened-{iteration}.png')
                if not opts.unprivileged:
                    with (artifacts / 'isolation.log').open('a') as iso:
                        iso.write('text-input: not asserted (host keyboard path); '
                                  'see N1 limitation\n')
                        iso.write('window-geometry: %s\n' % (geometry,))
                client = settled_client(package, client['address'])
                check((tuple(client['at']), tuple(client['size'])) == geometry,
                      'window moved/resized during the settled capture')
                if close_mode == 'compositor':
                    run(dispatch('close', client['address']))
                else:
                    run(['wtype', '-k', 'Escape'])
                check(process.wait(timeout=10) == 0, 'launch returned failure: ' + log_path.read_text())
                reader.join(timeout=5)
                check(not reader.is_alive(), 'launch log stream did not close')
                process = None
            wait_for(lambda: not clients(package), 5, 'surface leaked after close')
            log_text = log_path.read_text()
            check(log_text.count(' destroyed') == 1, 'activity cleanup must run once')
            record = json.loads((work / 'registry.json').read_text())[package]
            check(record['state']['status'] == 'exited' and record['state']['code'] == 0, 'registry must record clean exit')
        if not opts.unprivileged:
            app_uids = {registry[name]['uid'] for name in installed}
            live = []
            for proc in Path('/proc').glob('[0-9]*'):
                try:
                    if proc.stat().st_uid in app_uids:
                        live.append(proc.name)
                except FileNotFoundError:
                    pass
            check(not live, 'app processes survived close: ' + ','.join(live))
        for name in reversed(installed.copy()):
            run([binary, '--work', work, 'uninstall', name], env=env, timeout=120)
            installed.remove(name)
            check(name not in json.loads((work / 'registry.json').read_text()), 'registry entry survived uninstall')
            check(not (work / 'data' / name).exists(), 'app data survived uninstall')
            if not opts.unprivileged:
                user = 'isakai-' + hashlib.sha256(name.encode()).hexdigest()[:24]
                try:
                    pwd.getpwnam(user)
                except KeyError:
                    pass
                else:
                    raise AssertionError('app user survived uninstall')
        print('PASS: ' + ('unprivileged window lifetime/close/back' if opts.unprivileged else 'privileged install/isolation/window/lifetime/close/back/uninstall'))
        return 0
    finally:
        if socket_acl is not None:
            elevate(['setfacl', '-P', '--restore=-'], input=socket_acl)
        if process and process.poll() is None:
            # Close the exact test window, then reap; never kill unrelated windows.
            for client in clients(package):
                subprocess.run(dispatch('close', client['address']), timeout=5)
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=5)
        cleanup_failed = []
        for name in reversed(installed):
            result = subprocess.run([str(binary), '--work', str(work), 'uninstall', name], env=env, timeout=20, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            print(result.stdout, end='', flush=True)
            if result.returncode:
                cleanup_failed.append(name)
        if cleanup_failed:
            raise AssertionError('cleanup failed; retained work at ' + str(work) + ': ' + ','.join(cleanup_failed))
        print('Evidence retained at', artifacts, flush=True)


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (AssertionError, OSError, subprocess.SubprocessError) as error:
        if isinstance(error, subprocess.CalledProcessError) and error.stdout:
            print(error.stdout, file=sys.stderr)
        print('FAIL:', error, file=sys.stderr)
        sys.exit(1)
