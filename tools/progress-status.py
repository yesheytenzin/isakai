#!/usr/bin/env python3
"""Single-source status view: generate progress/STATUS.md from task frontmatter.

Frontmatter is the only status source. This script derives the Open /
Constrained / Done views so no hand-maintained list can drift from it.
Run after any frontmatter change and commit the result; progress-lint.py
fails if STATUS.md is stale.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROGRESS = ROOT / "progress"
OUT = PROGRESS / "STATUS.md"

LANES = (
    ("Open — in progress or blocked", {"in_progress", "blocked"}),
    ("Constrained — pending or deferred", {"pending", "deferred"}),
    ("Done", {"done"}),
)


def tasks():
    found = []
    for path in sorted(PROGRESS.glob("*.md")):
        text = path.read_text(encoding="utf-8")
        m = re.match(r"---\n(.*?)\n---\n?", text, re.DOTALL)
        if not m or not re.search(r"^id:", m.group(1), re.MULTILINE):
            continue
        front = m.group(1)

        def field(name):
            mm = re.search(r"^%s:\s*\"([^\"]+)\"" % name, front, re.MULTILINE)
            return mm.group(1) if mm else ""

        found.append({
            "id": field("id"),
            "status": field("status"),
            "next": field("next"),
            "file": path.name,
        })
    return found


def render(rows):
    lines = [
        "# Task status (generated — do not hand-edit)",
        "",
        "Derived from task frontmatter by `tools/progress-status.py`.",
        "Edit the owning task file, rerun the script, commit both.",
        "",
    ]
    for title, statuses in LANES:
        lines += ["## %s" % title, ""]
        lane = [t for t in rows if t["status"] in statuses]
        if not lane:
            lines.append("_none_")
        for t in sorted(lane, key=lambda t: t["id"]):
            lines.append("- [%s](%s): %s" % (t["id"], t["file"], t["next"]))
        lines.append("")
    return "\n".join(lines) + "\n"


def main():
    rows = tasks()
    OUT.write_text(render(rows), encoding="utf-8")
    print("wrote %s (%d tasks)" % (OUT.relative_to(ROOT), len(rows)))


if __name__ == "__main__":
    sys.exit(main())
