#!/usr/bin/env python3
"""Drive a pinned real app through its acceptance flow under the real compositor.

Unlike observe-app.py (startup smoke), this harness asserts *outcomes*:
typed text reaches the app, the computed result is copied to the real
clipboard, the window survives a resize, and the process exits cleanly with
no leftover window or child.

Every input action re-verifies that the target window is the compositor's
active window: a blind keystroke lands in whatever window happens to be
focused (which on a live desktop is somebody else's terminal).

Usage: flow-drive.py {opencalc|calculator|notes} [--work DIR] [--evidence DIR]
"""
import argparse
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_INDEX = {'opencalc': 0, 'calculator': 1, 'notes': 2}
YDOTOOL_SOCKET = '/tmp/isakai-flow-ydotool.sock'


class FlowError(AssertionError):
    pass


def run(argv, timeout=30, check=True, **kw):
    result = subprocess.run([str(a) for a in argv], text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout, **kw)
    if check and result.returncode:
        raise FlowError('command failed (%d): %s\n%s' % (result.returncode, ' '.join(map(str, argv)), result.stdout))
    return result


class Compositor:
    """hyprctl + grim + ydotool/wtype with liveness and focus guards."""

    def __init__(self, app, evidence, ydotool_socket=YDOTOOL_SOCKET):
        self.app = app
        self.package = app['package']
        self.evidence = evidence
        self.ydotool = ydotool_socket
        self.steps = []
        self.device_origin = None
        self.scale_x = 1.0
        self.scale_y = 1.0

    # --- compositor state (always read fresh; cached values go stale) ---
    def clients(self):
        return json.loads(run(['hyprctl', '-j', 'clients']).stdout)

    def app_client(self):
        for client in self.clients():
            if client.get('class') == self.package and client.get('mapped'):
                return client
        return None

    def active_window(self):
        return json.loads(run(['hyprctl', '-j', 'activewindow']).stdout)

    def visible_ws(self):
        monitors = json.loads(run(['hyprctl', '-j', 'monitors']).stdout)
        return monitors[0]['activeWorkspace']['id']

    def switch_to(self, ws, tries=6):
        for _ in range(tries):
            if self.visible_ws() == ws:
                return
            run(['hyprctl', 'dispatch', 'hl.dsp.focus({workspace=%d})' % ws])
            time.sleep(0.7)
        raise FlowError('workspace %s is not visible' % ws)

    def wait_for_quiet(self, stable_for=2.5, timeout=90):
        """Block until the pointer and focused window stop changing.

        A live desktop shares the compositor with this harness: a run that
        starts while the session is active gets its focus and pointer shifted
        mid-action. Waiting for a quiet window makes the flakiness explicit
        (and reports it) instead of mistaking it for app behaviour.
        """
        deadline = time.monotonic() + timeout
        last = None
        since = time.monotonic()
        while time.monotonic() < deadline:
            state = (tuple(self.cursor()), self.active_window().get('address'))
            if state != last:
                last = state
                since = time.monotonic()
            elif time.monotonic() - since >= stable_for:
                return True
            time.sleep(0.25)
        return False

    def focus(self, address, tries=20):
        for _ in range(tries):
            active = self.active_window()
            if active.get('address') == address:
                return
            # The window can sit on another workspace: make that workspace
            # visible first, then focus the address on it.
            client = self.app_client()
            if client is None:
                raise FlowError('app window disappeared while focusing')
            if client['workspace']['id'] != self.visible_ws():
                self.switch_to(client['workspace']['id'])
            run(['hyprctl', 'dispatch', 'hl.dsp.focus({window="address:%s"})' % address])
            time.sleep(0.9)
        active = self.active_window()
        raise FlowError('expected %s active, got %s' % (address, active.get('address')))

    def ensure_focus(self):
        client = self.app_client()
        if client is None:
            raise FlowError('app window is gone')
        if self.active_window().get('address') != client['address']:
            # A live desktop is shared with the person using it: wait for a
            # quiet moment before pulling focus away from their window.
            self.wait_for_quiet(stable_for=1.5, timeout=30)
            self.focus(client['address'])
        return client

    # --- input (verified: the harness never types into an unverified window) ---
    def _ydotool(self, *args):
        env = dict(os.environ, YDOTOOL_SOCKET=self.ydotool)
        run(['ydotool', *args], env=env)

    def cursor(self):
        pos = run(['hyprctl', 'cursorpos']).stdout.strip()
        return [int(v.strip()) for v in pos.split(',')]

    def calibrate_pointer(self):
        """Measure the virtual pointer's absolute mapping once.

        The uinput device's axis range is not the screen size, so absolute
        requests are scaled (2x with the current daemon). Hyprland applies
        pointer acceleration to *relative* motion, so absolute plus a measured
        scale is the only reliable path.
        """
        self.device_origin = None
        self._ydotool('mousemove', '--absolute', '-x', '0', '-y', '0')
        time.sleep(0.25)
        origin = self.cursor()
        samples = []
        for req in ((480, 270), (960, 540)):
            self._ydotool('mousemove', '--absolute', '-x', str(req[0]), '-y', str(req[1]))
            time.sleep(0.25)
            got = self.cursor()
            samples.append((req, got))
        self.device_origin = origin
        # Least-squares scale through the origin.
        num_x = sum(r[0] * (g[0] - origin[0]) for r, g in samples)
        den_x = sum(r[0] * r[0] for r, _ in samples)
        num_y = sum(r[1] * (g[1] - origin[1]) for r, g in samples)
        den_y = sum(r[1] * r[1] for r, _ in samples)
        if den_x == 0 or den_y == 0 or num_x <= 0 or num_y <= 0:
            raise FlowError('pointer calibration failed: origin=%s samples=%s' % (origin, samples))
        self.scale_x = num_x / den_x
        self.scale_y = num_y / den_y

    def move(self, x, y, tolerance=4, attempts=8):
        """Absolute placement with a self-tuning scale estimate.

        The uinput device maps requested absolute units onto the screen with a
        scale near 2 but the effective ratio drifts (device axis range,
        compositor handling, pointer acceleration for relative motion). Every
        observation updates the estimate, so the loop converges instead of
        trusting a fixed factor.
        """
        if self.device_origin is None:
            self.calibrate_pointer()
        last_req = None
        last_actual = None
        for _ in range(attempts):
            current = self.cursor()
            if last_req is not None and last_actual is not None:
                req_dx = last_req[0] - (last_req[0] - 0)
                d_req_x = last_req[0] - self.device_origin[0]
                d_req_y = last_req[1] - self.device_origin[1]
                d_act_x = last_actual[0] - self.device_origin[0]
                d_act_y = last_actual[1] - self.device_origin[1]
                if d_req_x != 0:
                    self.scale_x = 0.5 * self.scale_x + 0.5 * (d_act_x / d_req_x)
                if d_req_y != 0:
                    self.scale_y = 0.5 * self.scale_y + 0.5 * (d_act_y / d_req_y)
                if self.scale_x <= 0.1 or self.scale_y <= 0.1:
                    self.calibrate_pointer()
            dx = x - current[0]
            dy = y - current[1]
            if abs(dx) <= tolerance and abs(dy) <= tolerance:
                return True
            req_x = round((current[0] - self.device_origin[0]) / self.scale_x + dx / self.scale_x)
            req_y = round((current[1] - self.device_origin[1]) / self.scale_y + dy / self.scale_y)
            self._ydotool('mousemove', '--absolute', '-x', str(req_x), '-y', str(req_y))
            time.sleep(0.2)
            last_req = (req_x, req_y)
            last_actual = self.cursor()
        return False

    def click_window(self, dx, dy, hold_ms=0, attempts=5, tolerance=8):
        """Press at a window-relative point.

        The workspace re-tiles whenever another window appears and a live
        session's own pointer can move the cursor between our command and the
        press, so each attempt re-reads the rect, re-aims, then checks the
        pointer stayed put for the whole hold (a moving pointer cancels the
        app's long-press regardless of our intent).
        """
        for _ in range(attempts):
            client = self.ensure_focus()
            x = client['at'][0] + dx
            y = client['at'][1] + dy
            if not self.move(x, y, tolerance=tolerance):
                # Live-session pointer noise: press anyway when the cursor is
                # near the target (the caller asserts the *effect*), retry only
                # when it is nowhere close.
                near = self.cursor()
                if abs(near[0] - x) > 25 or abs(near[1] - y) > 25:
                    time.sleep(0.3)
                    continue
                self.steps.append({'step': 'press_near_miss', 'target': [x, y], 'at': near})
            before = self.cursor()
            if hold_ms:
                self._ydotool('click', '0x40')
                time.sleep(hold_ms / 1000.0)
                after = self.cursor()
                self._ydotool('click', '0x80')
                if abs(after[0] - before[0]) > 8 or abs(after[1] - before[1]) > 8:
                    time.sleep(0.3)
                    continue  # the pointer was hijacked mid-hold: retry
            else:
                self._ydotool('click', '0xC0')
            time.sleep(0.3)
            return client
        raise FlowError('could not place the pointer at window offset %d,%d' % (dx, dy))

    def press_window(self, dx, dy, hold_ms, release=True, attempts=4):
        """Press-and-hold at a window-relative point without releasing."""
        for _ in range(attempts):
            client = self.ensure_focus()
            if self.move(client['at'][0] + dx, client['at'][1] + dy):
                self._ydotool('click', '0x40')
                time.sleep(hold_ms / 1000.0)
                if release:
                    self._ydotool('click', '0x80')
                time.sleep(0.3)
                return client
            time.sleep(0.3)
        raise FlowError('could not place the pointer at window offset %d,%d' % (dx, dy))

    def click(self, x, y, hold_ms=0):
        if not self.move(x, y):
            raise FlowError('could not reach %s,%s (at %s)' % (x, y, self.cursor()))
        if hold_ms:
            self._ydotool('click', '0x40')
            time.sleep(hold_ms / 1000.0)
            self._ydotool('click', '0x80')
        else:
            self._ydotool('click', '0xC0')
        time.sleep(0.3)

    def type_text(self, text):
        self.ensure_focus()
        run(['wtype', text])
        time.sleep(0.5)

    def key(self, name):
        self.ensure_focus()
        run(['wtype', '-k', name])
        time.sleep(0.4)

    def chord(self, mods, key):
        self.ensure_focus()
        argv = []
        for mod in mods:
            argv += ['-M', mod]
        argv += ['-k', key]
        for mod in reversed(mods):
            argv += ['-m', mod]
        run(['wtype', *argv])
        time.sleep(0.4)

    # --- capture ---
    def shot(self, name, window_relative=None):
        client = self.ensure_focus()
        x, y = client['at']
        w, h = client['size']
        path = self.evidence / (self.app['name'].split()[0].lower() + '-' + name + '.png')
        if window_relative is not None:
            rx, ry, rw, rh = window_relative
            region = '%d,%d %dx%d' % (x + rx, y + ry, rw, rh)
        else:
            region = '%d,%d %dx%d' % (x, y, w, h)
        run(['grim', '-g', region, str(path)])
        self.steps.append({'step': name, 'shot': str(path), 'region': region})
        return path

    def panel_top(self, threshold=0.25):
        """First window row whose pixels are mostly non-white (the keypad).

        Geometry-independent way to keep blind presses inside the display
        area: the panel is a filled control surface, the display is white
        with sparse glyphs. Falls back to the window height when the frame
        cannot be classified.
        """
        client = self.ensure_focus()
        x, y = client['at']
        w, h = client['size']
        path = '/tmp/isakai-flow-frame.png'
        run(['grim', '-g', '%d,%d %dx%d' % (x, y, w, h), path])
        try:
            from PIL import Image
            image = Image.open(path).convert('RGB')
            pixels = image.load()
            columns = range(0, image.width, 4)
            streak = 0
            for row in range(image.height):
                dark = 0
                for col in columns:
                    r, g, b = pixels[col, row]
                    if r < 245 or g < 245 or b < 245:
                        dark += 1
                fraction = dark / max(1, len(list(columns)))
                if fraction > threshold:
                    streak += 1
                    if streak >= 3:
                        return max(0, row - 2)
                else:
                    streak = 0
            return image.height
        except Exception:
            return h

    def clip(self):
        return run(['wl-paste', '--no-newline'], check=False).stdout


def last_equation(log_text):
    """The app's own echo of its input ("Equation BigDecimal : 12+34")."""
    matches = re.findall(r'Equation BigDecimal : (.*)', log_text)
    return matches[-1].strip() if matches else ''


def history_results(work, package):
    """Read the app's own recorded (expression, result) pairs.

    OpenCalc stores AUTO_SAVE history entries in its shared preferences, so
    the harness can assert the *value* the app computed instead of trusting a
    screenshot or the clipboard alone.
    """
    prefs = Path(work) / 'data' / package / 'shared_prefs' / (package + '_preferences.xml')
    if not prefs.exists():
        return []
    text = prefs.read_text(errors='replace')
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


def wait_for_result(work, package, expression, timeout=20.0):
    """Last recorded result for `expression`, or None on timeout."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for entry in reversed(history_results(work, package)):
            if entry['a'] == expression:
                return entry['b']
        time.sleep(0.5)
    return None


class AppRun:
    """Owns the launched app process for one flow."""

    def __init__(self, app, work, log_path, compositor):
        self.app = app
        self.work = work
        self.log_path = log_path
        self.compositor = compositor
        self.process = None
        self.log = None

    def start(self, startup_timeout=240):
        env = dict(os.environ,
                   ISAKAI_NO_PRIVILEGE='1',
                   ISAKAI_FRAMEWORK=str(ROOT / 'zig-out/framework'),
                   ISAKAI_WINDOW='1',
                   ISAKAI_BRIDGE_DIR='/tmp/isakai-%d-flowdrive' % os.getuid(),
                   ISAKAI_STAGE_DIR='/tmp/isakai-%d-flowdrive-stage' % os.getuid())
        self.log = self.log_path.open('w')
        self.process = subprocess.Popen(
            [str(ROOT / 'zig-out/bin/isakai'), '--work', str(self.work), 'launch', self.app['package']],
            env=env, stdout=self.log, stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.monotonic() + startup_timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise FlowError('launch exited early (%s): %s' % (self.process.returncode, self.read_log()[-2000:]))
            client = self.compositor.app_client()
            if client:
                self.compositor.switch_to(client['workspace']['id'])
                self.compositor.focus(client['address'])
                return client
            time.sleep(0.2)
        raise FlowError('no mapped window within %ss' % startup_timeout)

    def alive(self):
        return self.process is not None and self.process.poll() is None

    def read_log(self):
        return self.log_path.read_text(errors='replace') if self.log_path.exists() else ''

    def close(self):
        """Compositor close request, then assert a clean, reaped exit."""
        client = self.compositor.app_client()
        if client is not None:
            run(['hyprctl', 'dispatch', 'hl.dsp.window.close({window="address:%s"})' % client['address']])
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                break
            time.sleep(0.2)
        else:
            raise FlowError('app did not exit after the compositor close request')
        code = self.process.returncode
        if code != 0:
            raise FlowError('expected exit 0, got %s: %s' % (code, self.read_log()[-2000:]))
        time.sleep(1.5)
        if self.compositor.app_client() is not None:
            raise FlowError('window survived the close request')
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
        if self.log:
            self.log.close()


def start_ydotoold():
    """Input injection needs the daemon; start a private instance when absent."""
    if Path(YDOTOOL_SOCKET).exists():
        probe = subprocess.run(['ydotool', 'mousemove', '--absolute', '-x', '0', '-y', '0'],
                               env=dict(os.environ, YDOTOOL_SOCKET=YDOTOOL_SOCKET),
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if probe.returncode == 0:
            return
    Path(YDOTOOL_SOCKET).unlink(missing_ok=True)
    subprocess.Popen(['ydotoold', '-p', YDOTOOL_SOCKET, '-o', '%d:%d' % (os.getuid(), os.getgid())],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    for _ in range(40):
        if Path(YDOTOOL_SOCKET).exists():
            return
        time.sleep(0.1)
    raise FlowError('ydotoold did not start')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('app', choices=sorted(APP_INDEX) + ['opencalc-copy-probe'])
    parser.add_argument('--work', type=Path)
    parser.add_argument('--evidence', type=Path, default=ROOT / '.cache/isakai/evidence')
    parser.add_argument('--startup-timeout', type=float, default=240)
    args = parser.parse_args()
    apps = json.loads((ROOT / 'progress.json').read_text())['apps']
    key = args.app.split('-')[0]
    app = apps[APP_INDEX[key]]
    args.evidence.mkdir(parents=True, exist_ok=True)
    work = args.work or ROOT / '.cache/isakai/baseline' / app['package']
    start_ydotoold()
    compositor = Compositor(app, args.evidence)
    log_path = args.evidence / (app['package'] + '.' + args.app + '.log')
    flow = FLOWS[args.app]
    run_app = AppRun(app, work, log_path, compositor)
    try:
        client = run_app.start(args.startup_timeout)
        compositor.quiet = compositor.wait_for_quiet()
        result = flow(run_app, compositor, client)
        result['quiet_before_flow'] = compositor.quiet
        print(json.dumps({'app': args.app, 'work': str(work), 'steps': compositor.steps,
                          'result': result}, indent=1))
        print('PASS:', args.app, 'flow')
        return 0
    except FlowError as failure:
        print('FAIL:', failure, file=sys.stderr)
        print(run_app.read_log()[-3000:], file=sys.stderr)
        return 1
    finally:
        run_app.stop()


def opencalc_flow(run_app, comp, client):
    """Calculate, verify the result, copy it, resize, close."""
    steps = {}
    comp.shot('01-initial')
    comp.type_text('12+34')
    time.sleep(1.0)
    if last_equation(run_app.read_log()) != '12+34':
        raise FlowError('typed expression was not recorded as 12+34 (got %r): another process owns '
                        'the keyboard focus' % last_equation(run_app.read_log()))
    if '12+34' not in run_app.read_log():
        raise FlowError('typed expression never reached the evaluator')
    steps['expression_logged'] = True
    expected = wait_for_result(run_app.work, run_app.app['package'], '12+34')
    if expected is None:
        comp.shot('02-no-result')
        raise FlowError('the app never recorded a result for 12+34 in its own history')
    steps['expected_result'] = expected
    time.sleep(1.5)  # let the display repaint the settled value
    display = comp.shot('02-result', (0, 0, 949, 270))
    # Copy: OpenCalc copies the *result* display on long-click. The display
    # rows sit at the top of the window; their exact offsets depend on the
    # current tile size, so candidates are swept until one copies.
    baseline = comp.clip().strip()
    copied = baseline
    # OpenCalc's copy target is the *result* row: a wrap_content TextView under
    # the input row. Its offset moves with the tile size, so sweep — but only
    # inside the display area: the keypad panel owns everything below, and a
    # blind press there edits the expression (observed: presses on digit
    # buttons changed 12+34 to a different sum before the copy).
    display_limit = comp.panel_top()
    # Fine sweep: the result row is a wrap_content TextView whose band moves
    # with the tile height, so step 15px and always press near x=0 (inside its
    # text) before trying a second column.
    candidates = [dy for dy in range(30, max(45, display_limit), 15)]
    for dy in candidates:
        for dx in (10, 40):
            comp.click_window(dx, dy, hold_ms=900)
            time.sleep(0.5)
            copied = comp.clip().strip()
            if copied != baseline:
                steps['copy_hit'] = '%d,%d' % (dx, dy)
                steps['copy_value'] = copied[:40]
            equation = last_equation(run_app.read_log())
            if equation != '12+34':
                raise FlowError('the app input changed to %r (panel top y=%d, press %d,%d): a press '
                                'reached the keypad or another process typed into the focused window; '
                                'rerun the flow when the desktop session is idle'
                                % (equation, display_limit, dx, dy))
            if copied == expected:
                break
        if copied == expected:
            break
    steps['clipboard'] = copied
    if copied != expected:
        comp.shot('03-copy-failed')
        raise FlowError('clipboard held %r (baseline %r), expected the recorded result %r '
                        '(the app computed the right value but the long-press copy did not deliver it)'
                        % (copied[:40], baseline[:40], expected))
    # Resize: the compositor owns the geometry, so drive it through a
    # float/tile toggle (each transition delivers a configure event and the
    # app must redraw) and, when the corner drag takes effect, a user-style
    # drag as well.
    steps['size_tiled'] = list(comp.ensure_focus()['size'])
    run(['hyprctl', 'dispatch', 'hl.dsp.window.float({window="address:%s", action="enable"})' % client['address']],
        check=False)
    time.sleep(0.8)
    floated = comp.ensure_focus()
    steps['floated'] = bool(floated.get('floating'))
    steps['size_floated'] = list(floated['size'])
    if not steps['floated']:
        raise FlowError('window did not float; the compositor could not resize it')
    comp.shot('04-floated')
    if not run_app.alive():
        raise FlowError('app died while the window was floated')

    run(['hyprctl', 'dispatch', 'hl.dsp.window.float({window="address:%s", action="disable"})' % client['address']],
        check=False)
    time.sleep(0.8)
    tiled_again = comp.ensure_focus()
    steps['size_tiled_again'] = list(tiled_again['size'])
    steps['tile_resize_changed_size'] = steps['size_tiled'] != steps['size_tiled_again'] or steps['size_floated'] != steps['size_tiled_again']
    if not steps['tile_resize_changed_size']:
        raise FlowError('float/tile transitions did not resize the window: %s -> %s -> %s'
                        % (steps['size_tiled'], steps['size_floated'], steps['size_tiled_again']))
    comp.shot('05-retiled')
    if not run_app.alive():
        raise FlowError('app died during the resize round trip')

    # User-style corner drag (best effort: Hyprland only honours it for
    # floating windows and only when the grab lands on the border).
    run(['hyprctl', 'dispatch', 'hl.dsp.window.float({window="address:%s", action="enable"})' % client['address']],
        check=False)
    time.sleep(0.6)
    before_drag = comp.ensure_focus()
    try:
        drag_resize(comp, -160, -90)
        after_drag = comp.ensure_focus()
        steps['drag_resize_delta'] = [after_drag['size'][0] - before_drag['size'][0],
                                      after_drag['size'][1] - before_drag['size'][1]]
    except FlowError as drag_failure:
        steps['drag_resize'] = 'skipped: %s' % drag_failure
    comp.shot('06-after-drag')
    if not run_app.alive():
        raise FlowError('app died during the drag resize')

    # Liveness: the R1 criterion is remaining usable, not merely starting.
    deadline = time.monotonic() + 31
    while time.monotonic() < deadline:
        if not run_app.alive():
            raise FlowError('app exited during the 30-second liveness window')
        if comp.app_client() is None:
            raise FlowError('window disappeared during the liveness window')
        time.sleep(1.0)
    steps['liveness_seconds'] = 31
    steps['exit_code'] = run_app.close()
    steps['closed_cleanly'] = True
    return steps


def drag_resize(comp, dx, dy, hold_ms=250):
    """Drag the window's bottom-right corner by (dx, dy)."""
    client = comp.ensure_focus()
    x = client['at'][0] + client['size'][0] - 3
    y = client['at'][1] + client['size'][1] - 3
    comp.move(x, y, tolerance=6)
    comp._ydotool('click', '0x40')
    time.sleep(hold_ms / 1000.0)
    comp.move(x + dx, y + dy, tolerance=8)
    comp._ydotool('click', '0x80')
    time.sleep(0.6)


def opencalc_copy_probe(run_app, comp, client):
    """Diagnostic: find the long-press target that copies the result."""
    comp.type_text('2+2')
    time.sleep(1.5)
    comp.shot('probe-typed', (0, 0, 949, 300))
    findings = {}
    for dx, dy in [(150, 37), (474, 37), (40, 37), (150, 62), (474, 62)]:
        comp.click_window(dx, dy, hold_ms=900)
        time.sleep(0.8)
        findings['%d,%d' % (dx, dy)] = comp.clip().strip()[:24]
        if findings['%d,%d' % (dx, dy)] not in ('', 'spec/graphql/error_handling_spec.rb'):
            break
    comp.shot('probe-after', (0, 0, 949, 300))
    return findings


FLOWS = {'opencalc': opencalc_flow, 'opencalc-copy-probe': opencalc_copy_probe}


if __name__ == '__main__':
    sys.exit(main())
