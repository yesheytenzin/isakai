#!/usr/bin/env python3
"""Ordered input test: daemon first (caps=3), then launch app, then type+click."""
import json
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sway_backend'))
from vinput import VInput, isolated_env, COMPOSITOR  # noqa: E402

ROOT = '/home/tenzin/development/isakai'
SM = os.path.join(COMPOSITOR, 'root/usr/bin/swaymsg')


def swaymsg(*args):
    out = subprocess.run([SM, *args], capture_output=True, text=True, env=isolated_env())
    return json.loads(out.stdout)


def find_app(app_id):
    tree = swaymsg('-t', 'get_tree')
    found = []

    def walk(n):
        if n.get('app_id') == app_id:
            found.append(n)
        for c in n.get('nodes', []) + n.get('floating_nodes', []):
            walk(c)

    walk(tree)
    return found[0] if found else None


def main():
    v = VInput()
    print('daemon ready', flush=True)
    seats = swaymsg('-t', 'get_seats')[0]
    print('caps=', seats['capabilities'], [d['name'] for d in seats['devices']], flush=True)
    env = dict(isolated_env())
    env.update({
        'ISAKAI_NO_PRIVILEGE': '1',
        'ISAKAI_FRAMEWORK': ROOT + '/zig-out/framework',
        'ISAKAI_WINDOW': '1',
        'ISAKAI_BRIDGE_DIR': '/tmp/isakai-1000-isosway',
        'ISAKAI_STAGE_DIR': '/tmp/isakai-1000-isosway-stage',
    })
    log = open('/tmp/iso-oc-ordered.log', 'w')
    p = subprocess.Popen(
        [ROOT + '/zig-out/bin/isakai', '--work', ROOT + '/.cache/isakai/recheck/opencalc10',
         'launch', 'com.darkempire78.opencalculator'],
        env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        node = None
        for _ in range(120):
            if p.poll() is not None:
                raise RuntimeError('launch exited %s' % p.returncode)
            node = find_app('com.darkempire78.opencalculator')
            if node:
                break
            time.sleep(0.5)
        if not node:
            raise RuntimeError('no window')
        print('mapped', node['rect'], flush=True)
        time.sleep(2)
        # click in display area to focus, then type
        x, y = node['rect']['x'], node['rect']['y']
        v.click(x + 300, y + 100)
        print('clicked display', flush=True)
        time.sleep(1)
        v.type('12+34')
        print('typed', flush=True)
        time.sleep(2)
        log.flush()
        text = open('/tmp/iso-oc-ordered.log').read()
        print('LOG tail:', repr(text[-800:]), flush=True)
        # screenshot
        subprocess.run(['grim', '-g', '%d,%d %dx%d' % (x, y, node['rect']['width'], node['rect']['height']),
                        '/tmp/iso-oc-ordered.png'], env=env, check=True)
        print('shot ok', flush=True)
    finally:
        v.close()
        if p.poll() is None:
            p.terminate()
            try:
                p.wait(timeout=8)
            except subprocess.TimeoutExpired:
                p.kill()


if __name__ == '__main__':
    main()
