#!/bin/bash
# Line-number oracle: our LineNumberTable must match the DEX debug info.
#
# Three views of the same ground truth (oracle/corpus/lines.smali `.line`
# directives) must agree:
#   1. baksmali `.line` directives in emission order (DEX ground truth),
#   2. our translate-test --dump-lines triples (method, jvm_pc, line),
#   3. javap -l -c LineNumberTable sections (our emitted class file).
#
# Checks: (2) == (3) exactly (same data, two views), and the per-method
# ordered unique line sequences of (1) == (2).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
D2J=${D2J:-$root/.cache/toolchain/dex_tools/dex-tools-v2.4}
if [ ! -x "$D2J/d2j-smali.sh" ]; then
    echo "ERROR: missing dex-tools; run python3 tools/bootstrap.py dex_tools, then source .cache/toolchain/env.sh" >&2
    exit 1
fi
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

"$D2J/d2j-smali.sh" -o "$work/lines.dex" "$here/corpus/lines.smali" >/dev/null 2>&1
mkdir -p "$work/ours"
"$root/zig-out/bin/translate-test" "$work/lines.dex" "$work/ours" >/dev/null 2>&1
"$root/zig-out/bin/translate-test" --dump-lines "$work/lines.dex" "$work/ours" 2>/dev/null | sort -k2,2 -k3,3n > "$work/dump.txt"
"$D2J/d2j-baksmali.sh" -o "$work/bak" "$work/lines.dex" >/dev/null 2>&1

fail=0

# View 3: javap -l -c LineNumberTable -> "method pc line" triples.
javap -l -c -p -cp "$work/ours" org.isakai.oracle.Lines > "$work/javap.txt" 2>&1
python3 - "$work/javap.txt" > "$work/javap.lines" <<'EOF'
import re, sys
cur = None
triples = []
for ln in open(sys.argv[1]):
    m = re.match(r'^\s*public static \S+ (\w+)\(', ln)
    if m:
        cur = m.group(1)
    m = re.match(r'^\s*line (\d+): (\d+)\s*$', ln)
    if m and cur:
        triples.append((cur, m.group(2), m.group(1)))
for method, pc, line in sorted(triples):
    print(method, pc, line)
EOF
# View 2 normalized: strip the class prefix, sort.
awk '{print $2, $3, $4}' "$work/dump.txt" | sort > "$work/dump.norm"
if cmp -s "$work/dump.norm" "$work/javap.lines"; then
    echo "PASS lines-attr (dump-lines == javap -l -c)"
else
    echo "FAIL lines-attr: --dump-lines differs from javap -l -c"
    diff "$work/dump.norm" "$work/javap.lines" | head -10
    fail=1
fi

# View 1: baksmali ordered unique lines per method.
python3 - "$work/bak/org/isakai/oracle/Lines.smali" > "$work/bak.lines" <<'EOF'
import re, sys
cur = None
seqs = {}
last = {}
for ln in open(sys.argv[1]):
    m = re.match(r'^\.method .* (\w+)\(', ln)
    if m:
        cur = m.group(1)
        seqs.setdefault(cur, [])
        last[cur] = None
        continue
    m = re.match(r'^\s*\.line (\d+)\s*$', ln)
    if m and cur and m.group(1) != last[cur]:
        seqs[cur].append(m.group(1))
        last[cur] = m.group(1)
for method in sorted(seqs):
    if seqs[method]:
        print(method, " ".join(seqs[method]))
EOF
# View 2 collapsed to ordered unique lines per method (dump.txt is pc-sorted per method).
awk '{print $2, $4}' "$work/dump.txt" | uniq > "$work/dump.collapsed"
python3 - "$work/dump.collapsed" > "$work/dump.seq" <<'EOF'
import sys
seqs = {}
for ln in open(sys.argv[1]):
    method, line = ln.split()
    seqs.setdefault(method, []).append(line)
for method in sorted(seqs):
    print(method, " ".join(seqs[method]))
EOF
if cmp -s "$work/dump.seq" "$work/bak.lines"; then
    echo "PASS lines-debug (dump-lines == baksmali .line)"
else
    echo "FAIL lines-debug: --dump-lines differs from baksmali"
    diff "$work/dump.seq" "$work/bak.lines" | head -10
    fail=1
fi
exit $fail
