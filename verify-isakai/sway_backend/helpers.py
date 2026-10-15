"""Shared app-recorded persistence readers for isolated flows."""
import re
import time
from pathlib import Path


def last_equation(log_text, since=0):
    matches = re.findall(r'Equation BigDecimal : (.*)', log_text[since:])
    return matches[-1].strip() if matches else ''


def prefs_mtime(work, package):
    prefs = Path(work) / 'data' / package / 'shared_prefs' / (package + '_preferences.xml')
    try:
        return prefs.stat().st_mtime_ns
    except OSError:
        return 0


def history_results(work, package):
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
    if not prefs.exists():
        return None
    text = prefs.read_text(errors='replace')
    match = re.search(r'<boolean name="%s" value="(true|false)"' % re.escape(key), text)
    if not match:
        return None
    return match.group(1) == 'true'
