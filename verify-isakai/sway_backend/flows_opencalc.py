"""OpenCalc acceptance on the isolated compositor.

Calculate 12+34, assert the app's own recorded result, long-press copy it
to the real clipboard, resize through the compositor, stay live 31s, and
close through the compositor with exit 0.
"""
import subprocess
import time

from .compositor import SwayError
from .helpers import history_results, last_equation, prefs_mtime, wait_for_result
from .vinput import isolated_env


def _clipboard_sentinel(text):
    subprocess.run(["wl-copy", "--type", "text/plain"], input=text.encode("utf-8"),
                   env=isolated_env(), check=True, timeout=10)


def run(run_app, comp):
    steps = {}
    comp.shot('01-initial')
    # Fresh-result gate (N1): snapshot history length, prefs mtime, log
    # offset and clipboard BEFORE typing so retained history from previous
    # sessions can never satisfy the assertions below.
    package = run_app.app['package']
    baseline_count = len(history_results(run_app.work, package))
    baseline_mtime = prefs_mtime(run_app.work, package)
    log_base = len(run_app.read_log())
    _clipboard_sentinel('ISAKAI-BASELINE')
    time.sleep(0.3)
    steps['baseline_history_count'] = baseline_count
    # The app restores its last expression/result; clear it so a retained
    # Syntax line cannot shift the keypad or pre-satisfy anything.
    comp.click_window(80, 310)
    time.sleep(0.5)
    comp.type_text('12+34')
    time.sleep(1.0)
    equation = last_equation(run_app.read_log(), since=log_base)
    if equation != '12+34':
        raise SwayError('typed expression was not recorded as 12+34 (got %r)' % equation)
    steps['expression_logged'] = True
    # Commit with '=' at the bottom-right key. The result line can change
    # height (a retained Syntax result pushes the pad down), so target the
    # key glyph at the bottom row rather than a row-relative estimate.
    # Single '=' taps intermittently never register (preview stays, no row),
    # so tap until the app's own history shows the new row; only the absent
    # row triggers a re-tap, so a landed tap is never doubled.
    expected = comp.attempt_until(
        lambda: comp.click_window(560, 750),
        lambda: wait_for_result(run_app.work, package, '12+34',
                                baseline_count=baseline_count, timeout=8.0),
        tries=3, label='= commit for 12+34')
    # Post-commit settle before the copy step below: the app animates the
    # history panel after a commit and taps during that window can vanish.
    time.sleep(2.0)
    if expected is None:
        comp.shot('02-no-result')
        raise SwayError('the app never recorded a NEW result for 12+34 in its own history '
                        '(baseline %d entries; retained rows do not count)' % baseline_count)
    if expected == 'Syntax':
        comp.shot('02-no-result')
        raise SwayError('the evaluator recorded Syntax for 12+34 instead of a result: '
                        'app-side evaluation defect, not a copy defect')
    if prefs_mtime(run_app.work, package) == baseline_mtime and baseline_mtime != 0:
        raise SwayError('history match without prefs store update; refusing stale satisfaction')
    steps['expected_result'] = expected
    time.sleep(1.5)
    comp.shot('02-result')
    # Copy via keyboard first: focus the input field, select all, copy.
    # This drives the app's own EditText/ClipboardManager/clipboard bridge
    # with no layout dependence. The clipboard holds the sentinel, so any
    # read of `expected` below proves a fresh transition, not stale state.
    comp.click_window(40, 60)
    comp.key('CTRL+A')
    comp.key('CTRL+C')
    time.sleep(0.5)
    copied = comp.clip().strip()
    if copied == expected:
        steps['copy_method'] = 'keyboard'
        steps['copy_value'] = copied[:40]
    else:
        steps['copy_method'] = 'touch-sweep'
    baseline = copied
    if copied != expected:
        # Copy: long-press the result row until the clipboard changes to the
        # recorded result. Sweep stays above the keypad panel.
        baseline = comp.clip().strip()
        copied = baseline
    display_limit = comp.panel_top()
    candidates = [dy for dy in range(30, max(45, display_limit), 15)]
    if copied != expected:
        for dy in candidates:
            for dx in (10, 40):
                comp.click_window(dx, dy, hold_ms=900)
                time.sleep(0.5)
                copied = comp.clip().strip()
                if copied != baseline:
                    steps['copy_hit'] = '%d,%d' % (dx, dy)
                    steps['copy_value'] = copied[:40]
                equation = last_equation(run_app.read_log(), since=log_base)
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
