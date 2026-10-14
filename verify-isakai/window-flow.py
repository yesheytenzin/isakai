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

ROOT = Path(__file__).resolve().parents[1]


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
    opts = parser.parse_args()
    required = ['hyprctl', 'grim', 'wtype', 'javac', 'java']
    if not opts.unprivileged:
        required.extend(['sudo', 'getfacl', 'setfacl'])
    missing = [c for c in required if not shutil.which(c)]
    if not os.getenv('WAYLAND_DISPLAY') or not os.getenv('XDG_RUNTIME_DIR'):
        missing.append('Wayland environment')
    if not opts.unprivileged and 'sudo' not in missing:
        if subprocess.run(['sudo', '-n', 'true'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            missing.append('noninteractive sudo authorization')
    if missing:
        print(('FAIL' if opts.required else 'SKIP') + ': ' + ', '.join(missing))
        return 1 if opts.required else 77
    java, compiler = find_jdk()
    # Do not rebuild or conceal a missing/broken binary inside the test.
    binary = Path(os.environ.get('ISAKAI', ROOT / 'zig-out/bin/isakai')).resolve()
    fixture = ROOT / 'zig-out/bin/fixture-apk'
    check(binary.is_file() and fixture.is_file(), 'build isakai and fixture-apk first')
    artifacts = Path(tempfile.mkdtemp(prefix='isakai-e2e-'))
    artifacts.chmod(0o755)
    print('ARTIFACTS:', artifacts, flush=True)
    work = artifacts / 'work'
    work.mkdir(mode=0o755)
    framework = artifacts / 'framework'
    shutil.copytree(ROOT / 'zig-out/framework', framework)
    package = 'org.isakai.e' + artifacts.name.rsplit('-', 1)[-1].replace('_', 'a')
    peer = package + '.peer'
    env = dict(os.environ)
    for key in ('ISAKAI_CONFINEMENT', 'ISAKAI_BRIDGE_DIR', 'ISAKAI_STAGE_DIR', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'LD_PRELOAD'):
        env.pop(key, None)
    env.update(ISAKAI_NO_PRIVILEGE='1' if opts.unprivileged else '0', ISAKAI_NONINTERACTIVE='1',
               ISAKAI_ALLOW_UNSIGNED='1', ISAKAI_FRAMEWORK=str(framework), ISAKAI_WINDOW='1')
    installed = []
    process = None
    bridge_daemon = None
    endpoint = None
    socket_acl = None
    socket_path = None
    try:
        for name in ([package] if opts.unprivileged else [package, peer]):
            apk = artifacts / (name + '.apk')
            run([fixture, apk, name, '1'])
            # Track attempted installs as well: a failed install must not leave accounts.
            installed.append(name)
            run([binary, '--work', work, 'install', apk], env=env)
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
            run(['sudo', '-n', 'setfacl', '-m', f'u:{uid}:rw', socket_path])
            # Real app-side probe via the same helper and IsakaiMain; no production hooks.
            probe_classes = code / 'probe-classes'
            run([compiler, '--release', '17', '-cp', framework, '-d', probe_classes, ROOT / 'verify-isakai/RuntimeProbe.java'])
            canary = code / 'readonly-canary'
            canary.write_text('readonly')
            canary.chmod(0o666)  # prove Landlock denial independently of DAC
            outside = artifacts / 'outside-canary'
            outside.write_text('outside')
            outside.chmod(0o666)
            other = Path(registry[peer]['data_dir']) / 'secret'
            run(['sudo', '-n', 'touch', other])
            # Headless probe uses the production bridge endpoint shape
            # (short /tmp dir; the helper validates and grants it) and a
            # live daemon, so the probe's socket round-trip under Landlock
            # is exercised with the real app uid.
            endpoint = Path('/tmp') / f'isakai-{os.getuid()}-fixtureprobe'
            endpoint.mkdir(mode=0o711, exist_ok=True)
            endpoint.chmod(0o711)
            bridge_daemon = subprocess.Popen([str(binary.parent / 'isakai-dbus-bridge'), '--bridge-dir', str(endpoint),
                                              '--stage-dir', str(work / 'stage' / package), '--uid', str(uid)],
                                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            for _ in range(50):
                if (endpoint / 'bridge.sock').exists():
                    break
                time.sleep(.1)
            check((endpoint / 'bridge.sock').exists(), 'bridge socket did not appear for the probe')
            command = ['sudo', '-n', binary, 'uid-helper', 'run', package, str(uid), str(data), str(work),
                       '--bridge-dir', str(endpoint), '--framework', str(framework), '--', str(java), '--enable-native-access=ALL-UNNAMED',
                       f'-Djava.io.tmpdir={data}/cache/tmp', f'-Dprobe.uid={uid}',
                       f'-Dprobe.stage={work}/stage/{package}', f'-Dprobe.code={canary}',
                       f'-Dprobe.outside={outside}', f'-Dprobe.other={other}',
                       '-Dprobe.parentNamespace=' + os.readlink('/proc/self/ns/mnt'),
                       '-cp', f'{probe_classes}:{framework}', 'org.isakai.runtime.IsakaiMain', str(data),
                       'org.isakai.verify.RuntimeProbe', '-', package]
            result = run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)
            (artifacts / 'isolation.log').write_text(result.stdout)
            print(result.stdout)
            check('PROBE PASS' in result.stdout, 'app-side isolation probe must pass')
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
                run(dispatch('focus', client['address']))
                screenshot(client, artifacts / f'opened-{iteration}.png')
                # R1: assert liveness continuously, not merely once at the end.
                deadline = time.monotonic() + (31 if iteration == 0 else 1)
                while time.monotonic() < deadline:
                    check(process.poll() is None, 'window runtime exited early: ' + log_path.read_text())
                    check(clients(package), 'surface disappeared early')
                    time.sleep(.2)
                # Fixture gives its EditText focus. Target only our window.
                run(dispatch('focus', client['address']))
                run(['wtype', ' RUNTIME'])
                time.sleep(.3)
                client = clients(package)[0]
                screenshot(client, artifacts / f'typed-{iteration}.png')
                check((artifacts / f'opened-{iteration}.png').read_bytes() != (artifacts / f'typed-{iteration}.png').read_bytes(), 'input did not change rendered pixels')
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
            run([binary, '--work', work, 'uninstall', name], env=env)
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
        print('PASS: ' + ('unprivileged window lifetime/input/close/back' if opts.unprivileged else 'privileged install/isolation/window/input/close/back/uninstall'))
        return 0
    finally:
        if bridge_daemon is not None and bridge_daemon.poll() is None:
            bridge_daemon.terminate()
            try:
                bridge_daemon.wait(timeout=5)
            except subprocess.TimeoutExpired:
                bridge_daemon.kill()
        if endpoint is not None:
            shutil.rmtree(endpoint, ignore_errors=True)
        if socket_acl is not None:
            run(['sudo', '-n', 'setfacl', '-P', '--restore=-'], input=socket_acl)
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
