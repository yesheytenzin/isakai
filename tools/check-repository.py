#!/usr/bin/env python3
"""Validate tracker dependencies, local links and generated source inventory."""
from pathlib import Path
import json
import re
import subprocess
ROOT = Path(__file__).resolve().parents[1]
tracker = json.loads((ROOT/'progress.json').read_text())
tasks = {t['id']: t for t in tracker['tasks']}
assert len(tasks) == len(tracker['tasks'])
assert tracker['next_task'] in tasks
def visit(key, seen):
    assert key not in seen, 'dependency cycle: ' + key
    for dep in tasks[key]['depends_on']: visit(dep, seen | {key})
for key, task in tasks.items():
    assert task['status'] in tracker['status_definitions']
    visit(key,set())
    for name in task['code']: assert (ROOT/name).exists(), name
for path in [ROOT/'README.md',*(ROOT/'docs').glob('*.md'),ROOT/'verify-isakai/SKILL.md']:
    for link in re.findall(r'\]\(([^)]+)\)',path.read_text()):
        if '://' in link or link.startswith('#'):continue
        target=link.split('#')[0]
        assert (path.parent/target).exists(), (path,target)
subprocess.run(['python3',str(ROOT/'tools/framework-inventory.py'),'--check'],check=True)
print('PASS: tracker graph, paths, documentation links and inventory')
