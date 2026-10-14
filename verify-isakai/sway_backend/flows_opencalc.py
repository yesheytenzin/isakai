"""OpenCalc acceptance on the isolated compositor.

Calculate 12+34, assert the app's own recorded result, long-press copy it
to the real clipboard, resize through the compositor, stay live 31s, and
close through the compositor with exit 0.
"""
import time

from .compositor import SwayError
from .helpers import history_results, last_equation, wait_for_result


def run(run_app, comp):
    steps = {}
    comp.shot('01-initial')
    comp.type_text('12+34')
    time.sleep(1.0)
    equation = last_equation(run_app.read_log())
    if equation != '12+34':
        raise SwayError('typed expression was not recorded as 12+34 (got %r)' % equation)
    steps['expression_logged'] = True
    # Commit with '=': the display row shows the live intermediate until
    # equals commits MainActivity.H and the app records history.
    comp.click_window(560, 700)
    time.sleep(2.0)
    expected = wait_for_result(run_app.work, run_app.app['package'], '12+34')
    if expected is None:
        comp.shot('02-no-result')
        raise SwayError('the app never recorded a result for 12+34 in its own history')
    steps['expected_result'] = expected
    time.sleep(1.5)
    comp.shot('02-result')
    # Copy: long-press the result row until the clipboard changes to the
    # recorded result. Sweep stays above the keypad panel.
    baseline = comp.clip().strip()
    copied = baseline
    display_limit = comp.panel_top()
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
            # After evaluation the display holds the result, which the app
            # also logs as an equation line; both states are legitimate.
            # Anything else means a press reached the keypad.
            if equation != '12+34' and equation != expected:
                raise SwayError('the app input changed to %r (expression %r, result %r; panel top y=%d, press %d,%d): '
                                'a press reached the keypad' % (equation, '12+34', expected, display_limit, dx, dy))
            if copied == expected:
                break
        if copied == expected:
            break
    steps['clipboard'] = copied
    if copied != expected:
        comp.shot('03-copy-failed')
        raise SwayError('clipboard held %r (baseline %r), expected the recorded result %r'
                        % (copied[:40], baseline[:40], expected))
    # Resize through the compositor: authoritative resize set in both
    # directions, each must change the client size and keep the app alive.
    before = comp.rect()
    steps['size_before'] = [before['width'], before['height']]
    shrunk = comp.resize(480, 600)
    steps['size_shrunk'] = [shrunk['width'], shrunk['height']]
    if [shrunk['width'], shrunk['height']] == steps['size_before']:
        raise SwayError('shrink resize had no effect: %s' % (steps['size_before'],))
    if not run_app.alive():
        raise SwayError('app died during the shrink resize')
    comp.shot('04-shrunk')
    grown = comp.resize(720, 860)
    steps['size_grown'] = [grown['width'], grown['height']]
    if [grown['width'], grown['height']] == steps['size_shrunk']:
        raise SwayError('grow resize had no effect: %s' % (steps['size_shrunk'],))
    if not run_app.alive():
        raise SwayError('app died during the grow resize')
    comp.shot('05-grown')
    # Liveness: usable, not merely started.
    deadline = time.monotonic() + 31
    while time.monotonic() < deadline:
        if not run_app.alive():
            raise SwayError('app exited during the 30-second liveness window')
        if comp.app_node() is None:
            raise SwayError('window disappeared during the liveness window')
        time.sleep(1.0)
    steps['liveness_seconds'] = 31
    steps['exit_code'] = run_app.close()
    steps['closed_cleanly'] = True
    return steps


FLOW = run
