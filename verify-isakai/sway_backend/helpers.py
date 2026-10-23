"""Shared app-recorded persistence readers for isolated flows."""
import fcntl
import json
import os
import re
import socket
import subprocess
import sys
import time
from contextlib import contextmanager
from pathlib import Path

BROKER_SOCKET = Path('/tmp/isakai-acl-broker-%d.sock' % os.getuid())
BROKER_LOCK = Path('/tmp/isakai-acl-broker-%d.lock' % os.getuid())
BROKER_SCRIPT = Path(__file__).resolve().parents[1] / 'root-acl-broker.py'
BROKER_PING_TIMEOUT = 35
_broker_proc = None
_broker_log = None


class BrokerBusy(RuntimeError):
    """A live broker did not answer within its connection timeout."""


def _broker_request(request, timeout=30):
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
        client.settimeout(timeout)
        client.connect(str(BROKER_SOCKET))
        stream = client.makefile('rw')
        stream.write(json.dumps(request) + '\n')
        stream.flush()
        return json.loads(stream.readline())


def _broker_ping(timeout=2):
    """True: alive; False: no listener; None: connected but busy.

    A timeout must never be read as death: the broker is single-threaded and
    a recursive ACL operation can keep a queued connection waiting. Replacing
    a live-but-busy broker would orphan its socket and leave two root
    brokers.
    """
    try:
        reply = _broker_request({'op': 'ping'}, timeout=timeout)
    except (ConnectionRefusedError, FileNotFoundError):
        return False
    except socket.timeout:
        return None
    except (OSError, ValueError):
        # A connection that was accepted but failed or returned junk is not
        # proof that the listener is gone. Refuse rather than unlink it.
        return None
    return reply.get('code') == 0 if isinstance(reply, dict) else None


@contextmanager
def _broker_start_lock():
    """Serialize the check-then-start sequence across concurrent flows."""
    fd = os.open(str(BROKER_LOCK), os.O_CREAT | os.O_RDWR, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        yield
    finally:
        fcntl.flock(fd, fcntl.LOCK_UN)
        os.close(fd)


def ensure_broker():
    """One pkexec authorization per session; every ACL change after it is a
    socket round-trip. Without this, a run asks for a password once per
    directory in grant_data_read."""
    global _broker_proc, _broker_log
    state = _broker_ping(BROKER_PING_TIMEOUT)
    if state is True:
        return
    if state is None:
        raise BrokerBusy('root ACL broker is busy; refusing to replace a live '
                         'broker; see /tmp/isakai-acl-broker.log')
    with _broker_start_lock():
        state = _broker_ping(BROKER_PING_TIMEOUT)
        if state is True:
            return
        if state is None:
            raise BrokerBusy('root ACL broker is busy; refusing to replace a '
                             'live broker; see /tmp/isakai-acl-broker.log')
        try:
            BROKER_SOCKET.unlink()
        except FileNotFoundError:
            pass
        except PermissionError:
            # A stale broker socket from an earlier root-owned attempt: the
            # broker unlinks it as root when it starts.
            pass
        _broker_log = open('/tmp/isakai-acl-broker.log', 'a')
        _broker_proc = subprocess.Popen(
            ['/usr/bin/pkexec', sys.executable, str(BROKER_SCRIPT), str(BROKER_SOCKET)],
            stdout=_broker_log, stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            state = _broker_ping(2)
            if state is True:
                return
            if _broker_proc.poll() is not None:
                raise RuntimeError('root ACL broker failed to start (%s); see /tmp/isakai-acl-broker.log'
                                   % _broker_proc.returncode)
            time.sleep(0.2)
        try:
            _broker_proc.kill()
        except PermissionError:
            pass
        raise RuntimeError('root ACL broker did not answer within 90s; see /tmp/isakai-acl-broker.log')



def _broker_call(request):
    ensure_broker()
    reply = _broker_request(request)
    if reply.get('code') != 0:
        raise RuntimeError('root ACL broker %s failed: %s'
                           % (request.get('op'), reply.get('err')))
    return reply


def shutdown_broker():
    global _broker_proc, _broker_log
    try:
        _broker_request({'op': 'shutdown'}, timeout=BROKER_PING_TIMEOUT)
    except (OSError, ValueError):
        pass
    if _broker_proc is not None:
        try:
            _broker_proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pass
        _broker_proc = None
    if _broker_log is not None:
        _broker_log.close()
        _broker_log = None


def grant_data_read(work, package, exclude=('run',)):
    """Grant the invoking user read access to the app's private data dir.

    A default-confined app runs as its real per-app uid with a 0700 data
    dir, so the flow's own persistence readers cannot observe the
    app-recorded state the acceptance depends on. This whitelists the
    invoking uid in the directory ACL root-side (the same pattern the
    privileged window-flow uses for the compositor socket). The named-user
    entry never masks the owner class, so the app's own access is
    unchanged. revoke_data_read strips the added entries.

    `run/` is excluded including its bind-mount landing file:
    uid-helper validates that target as a strict 0600 root file before
    every brokered launch, and an added named-user entry widens the group
    class enough to fail that check. The harness never reads run/.
    """
    data = Path(work) / 'data' / package
    data = data.resolve()
    if not data.is_dir():
        raise RuntimeError('%s does not exist; the privileged work tree must be installed first' % data)
    _broker_call({'op': 'grant', 'path': str(data), 'uid': os.getuid(),
                  'exclude': list(exclude)})


def revoke_data_read(work, package, exclude=('run',)):
    """Remove only the named-user entries added by grant_data_read and fail
    loudly if any survive (the app data must not stay readable to us)."""
    data = Path(work) / 'data' / package
    if not data.is_dir():
        return
    _broker_call({'op': 'revoke', 'path': str(data.resolve()),
                  'uid': os.getuid(), 'exclude': list(exclude)})


def app_uid(work, package):
    """Real per-app uid from the work tree's registry record."""
    registry = json.loads((Path(work) / 'registry.json').read_text())
    return registry[package]['uid']


def reap_app(work, package):
    """Terminate leftovers of this app's confined uid after an interrupted
    run; an orphaned app otherwise makes the next launch fail AlreadyRunning."""
    _broker_call({'op': 'reap', 'uid': app_uid(work, package)})


def grant_compositor_socket(socket_path, uid):
    """Grant the confined app uid rw on the user-owned compositor socket.

    Connect is a write, and the socket is owned by the invoking user, so a
    default-confined app uid (say 200000) without this grant always gets
    EACCES in Window.open. Mirrors the ACL grant the privileged
    window-flow performs, through the shared broker.
    """
    _broker_call({'op': 'socket-grant', 'path': str(socket_path), 'uid': int(uid)})


def revoke_compositor_socket(socket_path, uid):
    """Remove the named-user entry added by grant_compositor_socket."""
    _broker_call({'op': 'socket-revoke', 'path': str(socket_path), 'uid': int(uid)})


def last_equation(log_text, since=0):
    matches = re.findall(r'Equation BigDecimal : (.*)', log_text[since:])
    return matches[-1].strip() if matches else ''


def ensure_read(path):
    """Regrant the owning app's data dir for observation of files created
    after the last grant (a mid-app write lives outside every earlier
    recursive grant). Derives (work, package) from a .../data/<pkg>/... path."""
    parts = Path(path).resolve().parts
    if 'data' not in parts:
        return
    i = len(parts) - parts[::-1].index('data') - 1
    if len(parts) <= i + 1:
        return
    work = os.fspath(Path(*parts[:i]))
    grant_data_read(work, parts[i + 1])


def read_text(path, errors='replace'):
    """Path.read_text with one bounded elevate-and-retry under PermissionError."""
    try:
        return Path(path).read_text(errors=errors)
    except PermissionError:
        ensure_read(path)
        return Path(path).read_text(errors=errors)


def prefs_mtime(work, package):
    prefs = Path(work) / 'data' / package / 'shared_prefs' / (package + '_preferences.xml')
    try:
        return prefs.stat().st_mtime_ns
    except PermissionError:
        ensure_read(prefs)
        return prefs.stat().st_mtime_ns
    except OSError:
        return 0


def history_results(work, package):
    prefs = Path(work) / 'data' / package / 'shared_prefs' / (package + '_preferences.xml')
    try:
        text = read_text(prefs)
    except FileNotFoundError:
        return []
    match = re.search(r'name="[^"]*HISTORY_ELEMENTS">(.*?)</string>', text, re.S)
    if not match:
        return []
    raw = match.group(1)
    raw = raw.replace('&quot;', '"').replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>')
    entries = []
    for item in re.finditer(r'\{[^{}]*\}', raw):
        fields = dict(re.findall(r'"([abcd])":"([^"]*)"', item.group(0)))
        if 'a' in fields and 'b' in fields:
            entries.append(fields)
    return entries


def wait_for_result(work, package, expression, timeout=20.0, baseline_count=0):
    """Newly committed result for `expression`, or None on timeout.

    Only entries appended after `baseline_count` (snapshot before typing)
    are eligible, so retained history from previous sessions can never
    satisfy the assertion. Returns the recorded value verbatim, including
    'Syntax' when the evaluator failed: callers must reject that as an
    evaluator defect, not a copy defect.
    """
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        entries = history_results(work, package)
        for entry in entries[baseline_count:]:
            if entry['a'] == expression:
                return entry['b']
        time.sleep(0.5)
    return None


def read_pref_bool(work, package, prefs_name, key):
    """Single boolean from a shared_prefs XML, or None when absent."""
    prefs = Path(work) / 'data' / package / 'shared_prefs' / prefs_name
    try:
        text = read_text(prefs)
    except FileNotFoundError:
        return None
    match = re.search(r'<boolean name="%s" value="(true|false)"' % re.escape(key), text)
    if not match:
        return None
    return match.group(1) == 'true'
