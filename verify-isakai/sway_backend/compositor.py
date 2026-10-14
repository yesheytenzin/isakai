"""Sway backend for isolated real-app acceptance.

Replaces the hyprctl/ydotool/wtype Compositor in flow-drive.py with:
- swaymsg over the private $SWAYSOCK for window state/focus/close/resize
- isakai-vinput (persistent virtual pointer+keyboard) for input
- grim + wl-paste pointed at the private XDG_RUNTIME_DIR for capture/clipboard

Fixed geometry contract (sway.conf): app windows float at (100,100) 640x800
on a 1000x1000 headless output. Coordinates are absolute compositor pixels.
"""
import json
import os
import subprocess
import time
from pathlib import Path

from .vinput import VInput, isolated_env, COMPOSITOR, RUNTIME  # noqa: F401

SM = os.path.join(COMPOSITOR, 'root/usr/bin/swaymsg')


class SwayError(AssertionError):
    pass


def swaymsg(*args, timeout=10):
    proc = subprocess.run([SM, *args], capture_output=True, text=True,
                          env=isolated_env(), timeout=timeout)
    if proc.returncode != 0:
        raise SwayError('swaymsg %s failed: %s' % (' '.join(args), proc.stderr.strip()[:300]))
    return json.loads(proc.stdout) if proc.stdout.strip().startswith(('[', '{')) else proc.stdout


class SwayCompositor:
    """Window state + input + capture against the isolated sway."""

    def __init__(self, app, evidence):
        self.app = app
        self.package = app['package']
        self.evidence = Path(evidence)
        self.steps = []
        self.vinput = VInput()

    # --- window state ---
    def tree(self):
        return swaymsg('-t', 'get_tree')

    def app_node(self):
        found = []

        def walk(n):
            if n.get('app_id') == self.package:
                found.append(n)
            for c in n.get('nodes', []) + n.get('floating_nodes', []):
                walk(c)

        walk(self.tree())
        mapped = [n for n in found if n.get('visible', True)]
        return mapped[0] if mapped else (found[0] if found else None)

    def wait_for_window(self, timeout=120):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            node = self.app_node()
            if node is not None:
                return node
            time.sleep(0.3)
        raise SwayError('no window for %s within %ss' % (self.package, timeout))

    def rect(self):
        node = self.app_node()
        if node is None:
            raise SwayError('app window is gone')
        return node['rect']

    def focused_app_id(self):
        tree = self.tree()
        focus = []

        def walk(n):
            if n.get('focused'):
                focus.append(n)
            for c in n.get('nodes', []) + n.get('floating_nodes', []):
                walk(c)

        walk(tree)
        for n in focus:
            if n.get('app_id'):
                return n['app_id']
        return None

    def focus(self):
        """Focus the app window by app_id criterion."""
        swaymsg('[app_id="%s"]' % self.package, 'focus')
        time.sleep(0.3)
        if self.focused_app_id() != self.package:
            raise SwayError('could not focus %s (focused: %s)' % (self.package, self.focused_app_id()))

    def ensure_focus(self):
        node = self.app_node()
        if node is None:
            raise SwayError('app window is gone')
        if self.focused_app_id() != self.package:
            self.focus()
            node = self.app_node()
        return node

    # --- input (absolute compositor pixels) ---
    def click(self, x, y, hold_ms=80):
        self.ensure_focus()
        self.vinput.click(int(x), int(y), int(hold_ms))
        time.sleep(0.2)

    def click_window(self, dx, dy, hold_ms=80):
        node = self.ensure_focus()
        r = node['rect']
        self.vinput.click(int(r['x'] + dx), int(r['y'] + dy), int(hold_ms))
        time.sleep(0.2)
        return node

    def down_window(self, dx, dy):
        node = self.ensure_focus()
        r = node['rect']
        self.vinput.down(int(r['x'] + dx), int(r['y'] + dy))
        return node

    def drag_window(self, dx1, dy1, dx2, dy2, steps=10, hold_ms=60):
        """Drag inside the window from one window-relative point to another.

        Used for panel gestures (e.g. expanding the history sheet), which
        plain presses cannot trigger. Coordinates are window-relative.
        """
        import time as _time
        node = self.ensure_focus()
        r = node['rect']
        x1, y1 = int(r['x'] + dx1), int(r['y'] + dy1)
        x2, y2 = int(r['x'] + dx2), int(r['y'] + dy2)
        self.vinput.down(x1, y1)
        for i in range(1, steps + 1):
            x = x1 + (x2 - x1) * i // steps
            y = y1 + (y2 - y1) * i // steps
            self.vinput.move(x, y)
            _time.sleep(hold_ms / 1000.0)
        self.vinput.up()
        _time.sleep(0.5)
        return node

    def up(self):
        self.vinput.up()

    def type_text(self, text):
        self.ensure_focus()
        # vinput type handles ASCII calculator charset; chunk long strings
        for i in range(0, len(text), 32):
            self.vinput.type(text[i:i + 32])
            time.sleep(0.1)
        time.sleep(0.3)

    def key(self, name):
        self.ensure_focus()
        self.vinput.key(name)
        time.sleep(0.2)

    # --- capture ---
    def shot(self, name, window_relative=None):
        node = self.ensure_focus()
        r = node['rect']
        stem = self.app['name'].split()[0].lower()
        path = self.evidence / (stem + '-' + name + '.png')
        if window_relative is not None:
            rx, ry, rw, rh = window_relative
            region = '%d,%d %dx%d' % (r['x'] + rx, r['y'] + ry, rw, rh)
        else:
            region = '%d,%d %dx%d' % (r['x'], r['y'], r['w'] if 'w' in r else r['width'],
                                     r['h'] if 'h' in r else r['height'])
        # rect keys are x,y,width,height in sway tree
        region = '%d,%d %dx%d' % (r['x'] + (window_relative[0] if window_relative else 0),
                                 r['y'] + (window_relative[1] if window_relative else 0),
                                 window_relative[2] if window_relative else r['width'],
                                 window_relative[3] if window_relative else r['height'])
        subprocess.run(['grim', '-g', region, str(path)], env=isolated_env(),
                       check=True, timeout=15)
        self.steps.append({'step': name, 'shot': str(path), 'region': region})
        return path

    def panel_top(self, threshold=0.25):
        """First window row mostly non-white (keypad top)."""
        node = self.ensure_focus()
        r = node['rect']
        path = '/tmp/isakai-sway-frame.png'
        region = '%d,%d %dx%d' % (r['x'], r['y'], r['width'], r['height'])
        subprocess.run(['grim', '-g', region, path], env=isolated_env(),
                       check=True, timeout=15)
        try:
            from PIL import Image
            image = Image.open(path).convert('RGB')
            pixels = image.load()
            width = image.width
            cols = list(range(0, width, 4))
            streak = 0
            for row in range(image.height):
                dark = 0
                for col in cols:
                    rr, g, b = pixels[col, row]
                    if rr < 245 or g < 245 or b < 245:
                        dark += 1
                if dark / max(1, len(cols)) > threshold:
                    streak += 1
                    if streak >= 3:
                        return max(0, row - 2)
                else:
                    streak = 0
            return image.height
        except Exception:
            return r['height']

    def clip(self):
        proc = subprocess.run(['wl-paste', '--no-newline'], env=isolated_env(),
                              capture_output=True, text=True, timeout=10)
        return proc.stdout

    # --- window management ---
    def close(self):
        swaymsg('[app_id="%s"]' % self.package, 'kill')
        time.sleep(0.5)

    def resize(self, width, height):
        """Authoritative compositor resize of the floating window."""
        # floating windows only; our for_window rule floats all com.* apps
        self.ensure_focus()
        swaymsg('resize', 'set', str(int(width)), str(int(height)))
        time.sleep(0.8)
        return self.rect()

    def toggle_float(self):
        swaymsg('[app_id="%s"]' % self.package, 'floating', 'toggle')
        time.sleep(0.8)

    def is_floating(self):
        node = self.app_node()
        return node is not None and node.get('type') == 'floating_con'

    def dispose(self):
        try:
            self.vinput.close()
        except Exception:
            pass
