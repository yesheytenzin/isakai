#!/usr/bin/env python3
"""Persistent virtual-pointer + virtual-keyboard driver for the isolated sway.

Spawns isakai-vinput with piped stdin/stdout and exposes a line-command API.
Used both interactively and by the sway flow backend.
"""
import os
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
COMPOSITOR = os.path.join(ROOT, '.cache/isakai/compositor')
RUNTIME = '/tmp/isakai-wayland-iy7f4069'
SWAYSOCK = os.path.join(RUNTIME, 'sway-ipc.1000.232640.sock')


def isolated_env():
    env = dict(os.environ)
    env['XDG_RUNTIME_DIR'] = RUNTIME
    env['WAYLAND_DISPLAY'] = 'wayland-1'
    env['SWAYSOCK'] = SWAYSOCK
    env['LD_LIBRARY_PATH'] = os.path.join(COMPOSITOR, 'root/usr/lib') + ':' + env.get('LD_LIBRARY_PATH', '')
    return env


class VInput:
    def __init__(self):
        self.proc = subprocess.Popen(
            [os.path.join(COMPOSITOR, 'isakai-vinput')],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, bufsize=1,
            env=isolated_env(), start_new_session=True)
        # wait for ready line on stderr
        import select
        deadline = time.monotonic() + 10
        ready = False
        while time.monotonic() < deadline:
            r, _, _ = select.select([self.proc.stderr], [], [], 0.5)
            if r:
                line = self.proc.stderr.readline()
                if 'vinput ready' in line:
                    ready = True
                    break
            if self.proc.poll() is not None:
                break
        if not ready:
            err = self.proc.stderr.read() if self.proc.poll() is not None else 'timeout'
            raise RuntimeError('vinput did not start: %s' % err)

    def cmd(self, line, timeout=15):
        import select
        self.proc.stdin.write(line + '\n')
        self.proc.stdin.flush()
        r, _, _ = select.select([self.proc.stdout], [], [], timeout)
        if not r:
            raise RuntimeError('vinput no reply to %r' % line)
        reply = self.proc.stdout.readline().strip()
        if not reply.startswith('ok'):
            raise RuntimeError('vinput %r -> %r' % (line, reply))
        return reply

    def move(self, x, y):
        return self.cmd('move %d %d' % (x, y))

    def click(self, x, y, hold_ms=80):
        return self.cmd('click %d %d %d' % (x, y, hold_ms))

    def down(self, x, y):
        return self.cmd('down %d %d' % (x, y))

    def up(self):
        return self.cmd('up')

    def key(self, name):
        return self.cmd('key %s' % name)

    def type(self, text):
        return self.cmd('type %s' % text)

    def close(self):
        try:
            self.cmd('quit', timeout=5)
        except Exception:
            pass
        try:
            self.proc.wait(timeout=5)
        except Exception:
            self.proc.kill()


if __name__ == '__main__':
    v = VInput()
    print('READY caps check next')
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            print(v.cmd(line))
        except RuntimeError as e:
            print('FAIL:', e)
        if line == 'quit':
            break
    v.close()
