"""Shared app-recorded persistence readers for isolated flows."""
import re
import time
from pathlib import Path


def last_equation(log_text):
    matches = re.findall(r'Equation BigDecimal : (.*)', log_text)
    return matches[-1].strip() if matches else ''


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


def wait_for_result(work, package, expression, timeout=20.0):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for entry in reversed(history_results(work, package)):
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
