#!/usr/bin/env python3
"""Root-side ACL broker for the confined-app acceptance flows.

The flows need root only to change ACLs (grant the invoking user read
access to another uid's app data, and grant the app uid access to the
compositor socket). `pkexec` authorizes one process at a time, so a
per-directory elevation asks the operator for a password many times per
run.

This broker is started through a single `pkexec` and serves a line-based
JSON protocol over a 0600 unix socket owned by the invoking user. It
offers a small fixed set of ACL operations on paths inside the harness
work tree and the isolated compositor runtime dir, nothing else, and
exits on `shutdown` or after an idle timeout. The flows reuse one broker
across all pinned apps, so the operator authorizes once per session.
"""
import json
import os
import re
import signal
import socket
import subprocess
import sys
import time
from pathlib import Path

SETFACL = '/usr/bin/setfacl'
GETFACL = '/usr/bin/getfacl'
REPO_CACHE = Path(__file__).resolve().parents[1] / '.cache/isakai'
WAYLAND_ROOT = '/tmp/isakai-wayland-'
IDLE_TIMEOUT = 3600
CONNECTION_TIMEOUT = 30
OWNER_UID = int(os.environ.get('PKEXEC_UID') or os.environ.get('SUDO_UID') or -1)


def _allowed(path):
    resolved = Path(path).resolve()
    if resolved == REPO_CACHE or REPO_CACHE in resolved.parents:
        return True
    return str(resolved).startswith(WAYLAND_ROOT)


def _setfacl(args):
    return subprocess.run([SETFACL] + args, capture_output=True, text=True)


def _getfacl(args):
    return subprocess.run([GETFACL] + args, capture_output=True, text=True)


def _grant(path, uid, exclude):
    """Access grant matching helpers.grant_data_read, minus `run/`."""
    path = Path(path)
    failures = []
    result = _setfacl(['-m', 'u:%d:rX' % uid, str(path)])
    if result.returncode:
        failures.append(result.stderr.strip())
    for entry in sorted(path.iterdir()):
        if entry.name in exclude:
            continue
        result = _setfacl(['-R', '-m', 'u:%d:rX' % uid, str(entry)])
        if result.returncode:
            failures.append(result.stderr.strip())
    return failures


def _revoke(path, uid, exclude):
    """Remove only the named-user entry the harness added, then verify it
    is gone outside the excluded subtree. `-b` would strip unrelated ACLs
    and would recurse into `run/`, which uid-helper validates as strict 0600.
    """
    path = Path(path)
    failures = []
    _setfacl(['-x', 'u:%d' % uid, str(path)])
    entries = [entry for entry in sorted(path.iterdir()) if entry.name not in exclude]
    for entry in entries:
        _setfacl(['-R', '-x', 'u:%d' % uid, str(entry)])
    for target in [path] + entries:
        dumped = _getfacl(['-R', '-n', '-p', str(target)])
        if dumped.returncode:
            failures.append(dumped.stderr.strip())
        elif re.search(r'^user:%d:' % uid, dumped.stdout, re.M):
            failures.append('user:%d entry remains under %s' % (uid, target))
    return failures


def _reap(uid):
    """Terminate leftover processes of a confined app uid (an interrupted
    flow can orphan the app and make the next launch fail AlreadyRunning)."""
    if not 200000 <= uid <= 299999:
        return 'uid outside the confined app range: %d' % uid
    targets = []
    for entry in Path('/proc').iterdir():
        if not entry.name.isdigit():
            continue
        try:
            status = (entry / 'status').read_text()
        except (FileNotFoundError, PermissionError):
            continue
        if '\nUid:\t%d\t' % uid in status:
            targets.append(int(entry.name))
    for pid in targets:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    deadline = time.monotonic() + 5
    while targets and time.monotonic() < deadline:
        targets = [pid for pid in targets if Path('/proc/%d' % pid).exists()]
        time.sleep(0.1)
    for pid in targets:
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    return ''


def _handle(request):
    op = request.get('op')
    if op == 'ping':
        return 0, ''
    if op == 'shutdown':
        return 0, ''
    if op == 'reap':
        error = _reap(int(request.get('uid', 0)))
        return (1, error) if error else (0, '')
    path = request.get('path', '')
    if not path or not _allowed(path):
        return 3, 'path outside the harness work tree: %r' % path
    uid = int(request.get('uid', 0))
    if uid != OWNER_UID and not 200000 <= uid <= 299999:
        return 3, 'uid not allowed: %d' % uid
    if op == 'grant':
        failures = _grant(path, uid, request.get('exclude', []))
        return (1, '; '.join(failures)) if failures else (0, '')
    if op == 'revoke':
        failures = _revoke(path, uid, request.get('exclude', []))
        return (1, '; '.join(failures)) if failures else (0, '')
    if op == 'socket-grant':
        result = _setfacl(['-m', 'u:%d:rw' % uid, path])
        return result.returncode, result.stderr.strip()
    if op == 'socket-revoke':
        result = _setfacl(['-x', 'u:%d' % uid, path])
        if result.returncode and 'No matching entry' not in result.stderr:
            return result.returncode, result.stderr.strip()
        return 0, ''
    return 2, 'unknown op: %r' % op


def serve(sock):
    while True:
        try:
            conn, _ = sock.accept()
        except socket.timeout:
            return
        try:
            with conn, conn.makefile('rw') as stream:
                conn.settimeout(CONNECTION_TIMEOUT)
                line = stream.readline()
                if not line:
                    continue
                request = None
                shutdown = False
                try:
                    request = json.loads(line)
                    if not isinstance(request, dict):
                        raise ValueError('request must be a JSON object')
                    code, error = _handle(request)
                    shutdown = request.get('op') == 'shutdown' and code == 0
                except Exception as failure:  # report, keep serving
                    code, error = 4, '%s: %s' % (type(failure).__name__, failure)
                stream.write(json.dumps({'code': code, 'err': error}) + '\n')
                stream.flush()
                if shutdown:
                    return
        except OSError:
            # A client that stalls past the timeout or disconnects must not
            # take the single-threaded broker down.
            continue


def _socket_state(path):
    """True: a live broker answers; False: no listener; None: unresponsive.

    An unresponsive socket is never treated as stale: the single-threaded
    broker may simply be inside a recursive ACL operation, and unlinking
    its socket would orphan the live root process.
    """
    probe = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    probe.settimeout(2)
    try:
        probe.connect(path)
        probe.sendall(b'{"op": "ping"}\n')
        reply = probe.makefile('r').readline()
    except (ConnectionRefusedError, FileNotFoundError):
        return False
    except (OSError, socket.timeout):
        return None
    finally:
        probe.close()
    try:
        return json.loads(reply).get('code') == 0
    except ValueError:
        return None


def main():
    path = sys.argv[1]
    if os.path.lexists(path):
        state = _socket_state(path)
        if state is True:
            print('root ACL broker already running at %s; refusing duplicate' % path,
                  file=sys.stderr)
            return 0
        if state is None:
            print('root ACL broker socket %s is unresponsive; refusing to steal it' % path,
                  file=sys.stderr)
            return 1
        try:
            os.unlink(path)
        except FileNotFoundError:
            pass
        except OSError as failure:
            print('cannot remove stale broker socket %s: %s' % (path, failure),
                  file=sys.stderr)
            return 1
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    sock.bind(path)
    owner = os.environ.get('PKEXEC_UID') or os.environ.get('SUDO_UID')
    if owner:
        os.chown(path, int(owner), -1)
    os.chmod(path, 0o600)
    bound = os.stat(path)
    sock.listen(8)
    sock.settimeout(IDLE_TIMEOUT)
    try:
        serve(sock)
    finally:
        sock.close()
        try:
            current = os.stat(path)
            if (current.st_dev, current.st_ino) == (bound.st_dev, bound.st_ino):
                os.unlink(path)
        except FileNotFoundError:
            pass


if __name__ == '__main__':
    sys.exit(main())
