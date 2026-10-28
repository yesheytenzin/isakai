#!/usr/bin/env python3
"""Project-hygiene lint: fail loudly on planning-metadata violations.

Checks (stdlib only, no judgment applied):
  1. Every progress task file (frontmatter with `id:`) carries
     `status:`/`owner:`/`next:` lines and a known status.
  2. No README roadmap task bullet carries text after its .md link
     (the index must not state statuses).
  3. Every FINDINGS.md item names an owning task (`Triaged <ID>`)
     or the debt ledger.
  4. Every .audit/*.tsv has a `task` column and every row fills it.
  5. progress/STATUS.md matches frontmatter (regenerate, don't hand-edit).

Allowed by the D2 hygiene-lint exception: this checks planning
metadata, not app behavior, so it is not verification scaffolding.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KNOWN_STATUSES = {"pending", "in_progress", "blocked", "done", "deferred"}
TASK_LINK = re.compile(r"\[.*?]\(([A-Za-z]+\d+\.md)\)")

errors = []


def fail(msg):
    errors.append(msg)
    print("FAIL: " + msg)


# 1. Task-file frontmatter (progress/ root plus archive/).
for path in sorted((ROOT / "progress").glob("*.md")) + sorted((ROOT / "progress" / "archive").glob("*.md")):
    text = path.read_text(encoding="utf-8")
    m = re.match(r"---\n(.*?)\n---\n?", text, re.DOTALL)
    if not m or not re.search(r"^id:", m.group(1), re.MULTILINE):
        continue  # not a task file (index, manifest, history, findings)
    front = m.group(1)
    for key in ("status", "owner", "next"):
        if not re.search(r"^%s:\s*\S" % key, front, re.MULTILINE):
            fail("%s: frontmatter missing `%s:` line" % (path.name, key))
    sm = re.search(r'^status:\s*"([^"]+)"', front, re.MULTILINE)
    if sm and sm.group(1) not in KNOWN_STATUSES:
        fail("%s: unknown status %r" % (path.name, sm.group(1)))

# 2. README roadmap bullets must not state text after the link.
readme = (ROOT / "progress" / "README.md").read_text(encoding="utf-8")
for n, line in enumerate(readme.splitlines(), 1):
    s = line.strip()
    if not s.startswith("- ["):
        continue
    m = TASK_LINK.search(s)
    if m and s[m.end():].strip():
        fail("README.md:%d: roadmap bullet has text after link: %s" % (n, s))

# 3. FINDINGS.md items must name an owning task or the debt ledger.
findings = (ROOT / "progress" / "FINDINGS.md").read_text(encoding="utf-8")
for n, line in enumerate(findings.splitlines(), 1):
    if not re.match(r"\s*-\s+\S", line):
        continue
    if not re.search(r"Triaged\s+[A-Z]+\d+|Ledger:\s*`?PONYTAIL-DEBT\.md", line):
        fail("FINDINGS.md:%d: item names no owning task: %s"
             % (n, line.strip()))

# 4. Audit TSVs need a task value per row. A single-task trail file may
# omit the column: the task is implied by the file name (c8.tsv -> C8,
# c7-completion.tsv -> C7). Multi-task trails like fixall.tsv must carry
# an explicit column.
IMPLIED_TASK = {"c7-completion": "C7"}


def implied_task(name):
    stem = name[:-4] if name.endswith(".tsv") else name
    if stem in IMPLIED_TASK:
        return IMPLIED_TASK[stem]
    m = re.match(r"^([a-z]+)(\d+)$", stem)
    return (m.group(1) + m.group(2)).upper() if m else None


for tsv in sorted((ROOT / ".audit").glob("*.tsv")):
    rows = tsv.read_text(encoding="utf-8").splitlines()
    if not rows:
        continue
    header = rows[0].split("\t")
    task_idx = header.index("task") if "task" in header else None
    if task_idx is None:
        if implied_task(tsv.name) is None:
            fail("%s: header has no `task` column" % tsv.name)
        continue
    for n, row in enumerate(rows[1:], 2):
        cells = row.split("\t")
        if len(cells) != len(header):
            fail("%s:%d: %d fields vs %d header columns"
                 % (tsv.name, n, len(cells), len(header)))
            continue
        if task_idx is not None and not cells[task_idx].strip():
            fail("%s:%d: row missing task value" % (tsv.name, n))

# 5. STATUS.md must match frontmatter. The status script is the only
# writer (it reads progress/*.md, progress/archive/*.md and ROADMAP.md
# sections); hand edits or stale commits fail here. Rerun
# tools/progress-status.py and commit both files.
import importlib.util as _ilu
_spec = _ilu.spec_from_file_location(
    "progress_status", str(ROOT / "tools" / "progress-status.py"))
_ps = _ilu.module_from_spec(_spec)
_spec.loader.exec_module(_ps)
_expected = _ps.render(_ps.tasks())
_actual = (ROOT / "progress" / "STATUS.md").read_text(encoding="utf-8")
if _actual != _expected:
    fail("progress/STATUS.md is stale: rerun tools/progress-status.py")

# 6. ROADMAP.md sections carry machine-readable status lines so the
# pending/deferred lane has one source. Each `## <ID>` header must be
# followed by `Status: <status> — <next>` with a known status.
_roadmap = ROOT / "progress" / "ROADMAP.md"
if _roadmap.exists():
    _rlines = _roadmap.read_text(encoding="utf-8").splitlines()
    for _n, _line in enumerate(_rlines, 1):
        _m = re.match(r"^## ([A-Z]+\d+)\b", _line)
        if not _m:
            continue
        _j = _n
        while _j < len(_rlines) and not _rlines[_j].strip():
            _j += 1
        _sm = re.match(r"^Status: (\w+) \u2014 (.*\S)\s*$",
                       _rlines[_j] if _j < len(_rlines) else "")
        if not _sm or _sm.group(1) not in KNOWN_STATUSES:
            fail("ROADMAP.md:%d: header %s lacks a valid `Status:` line" % (_n, _m.group(1)))

print("%d violation(s)" % len(errors))
sys.exit(1 if errors else 0)
