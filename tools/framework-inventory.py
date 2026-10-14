#!/usr/bin/env python3
"""Regenerate the build source inventory and API catalog; --check never writes."""
import argparse
from pathlib import Path
import re
ROOT = Path(__file__).resolve().parents[1]
check = argparse.ArgumentParser()
check.add_argument('--check', action='store_true')
args = check.parse_args()
sources = sorted(p for p in (ROOT / 'framework').rglob('*.java') if not p.name.endswith('Test.java'))
build = ROOT / 'build.zig'
text = build.read_text()
start = text.index('    fw.addFileArg(')
end = text.index('const fw_install', start)
generated = ''.join('    fw.addFileArg(b.path("' + str(p.relative_to(ROOT)) + '"));\n' for p in sources)
text = text[:start] + generated + '\n    ' + text[end:]
doc = ROOT / 'docs/SUPPORTED_APIS.md'
header = doc.read_text().split('## android.accessibilityservice')[0].split('<!-- GENERATED')[0]
header = header.replace('This is a manually maintained class inventory, initially extracted from framework\njavadocs.', 'This inventory is generated from framework source paths.').replace('A reproducible catalog generator is planned under Q3.', 'Regenerate with `python3 tools/framework-inventory.py`; CI uses `--check`.\nBehavioral limits below are maintained separately from the generated names.')
header = header.replace('Handler delays are ignored, callbacks run on the caller, and callback cancellation is a no-op.', 'Handler uses thread-owned queues, monotonic delays and cancellation; window dispatch polls at up to 100ms intervals.')
catalog = header.rstrip() + '\n\n<!-- GENERATED: source presence is not behavioral support. -->\n'
package = None
for p in sources:
    rel = p.relative_to(ROOT / 'framework')
    pkg = '.'.join(rel.parts[:-1])
    if package != pkg:
        catalog += '\n## ' + pkg + '\n\n'
        package = pkg
    catalog += '- [`' + p.stem + '`](../' + str(p.relative_to(ROOT)) + ')\n'
for path, content in ((build,text),(doc,catalog)):
    if path.read_text() != content:
        if args.check: raise SystemExit('STALE: ' + str(path.relative_to(ROOT)))
        path.write_text(content)
print('PASS: deterministic inventory:', len(sources), 'unique production Java sources')
