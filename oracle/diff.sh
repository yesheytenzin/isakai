#!/bin/bash
# Differential oracle: smali corpus -> DEX -> our translator vs dex2jar ->
# execute both on the host JVM and diff outputs.
#
# Prereqs (one-time): dex-tools in /tmp/oracle (see docs/TESTING.md)
#   D2J=/tmp/oracle/dex2jar/dex-tools-v2.4
# Usage: oracle/diff.sh [case-name...]  (default: all cases in oracle/corpus/)
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

cases=("$@")
if [ ${#cases[@]} -eq 0 ]; then
    for f in "$here"/corpus/*.smali; do cases+=("$(basename "$f" .smali)"); done
fi

fail=0
for case in "${cases[@]}"; do
    smali="$here/corpus/$case.smali"
    # Driver: <Case>Driver.java (capitalized case + Driver).
    driver="$here/corpus/$(tr '[:lower:]' '[:upper:]' <<< "${case:0:1}")${case:1}Driver.java"
    [ -f "$smali" ] || { echo "SKIP $case: no smali"; continue; }
    [ -f "$driver" ] || { echo "SKIP $case: no driver"; continue; }
    mkdir -p "$work/$case/ours" "$work/$case/theirs"
    "$D2J/d2j-smali.sh" -o "$work/$case/classes.dex" "$smali" >/dev/null 2>&1
    if ! "$root/zig-out/bin/translate-test" "$work/$case/classes.dex" "$work/$case/ours" >/dev/null 2>&1; then
        echo "FAIL $case: our translator rejected"
        fail=1
        continue
    fi
    if ! "$D2J/d2j-dex2jar.sh" -o "$work/$case/theirs/out.jar" "$work/$case/classes.dex" >/dev/null 2>&1; then
        echo "FAIL $case: dex2jar rejected"
        fail=1
        continue
    fi
    (cd "$work/$case/theirs" && unzip -o -q out.jar)
    driver_class=$(basename "$driver" .java)
    javac -d "$work/$case/ours" "$driver" 2>/dev/null || { echo "FAIL $case: driver compile (ours)"; fail=1; continue; }
    javac -d "$work/$case/theirs" "$driver" 2>/dev/null || { echo "FAIL $case: driver compile (theirs)"; fail=1; continue; }
    ours_out=$(java -cp "$work/$case/ours" "$driver_class" 2>&1) || { echo "FAIL $case: ours threw:"; echo "$ours_out" | head -5; fail=1; continue; }
    theirs_out=$(java -cp "$work/$case/theirs" "$driver_class" 2>&1) || { echo "FAIL $case: theirs threw"; fail=1; continue; }
    if [ "$ours_out" = "$theirs_out" ]; then
        echo "PASS $case"
    else
        echo "FAIL $case: output differs"
        diff <(echo "$ours_out") <(echo "$theirs_out") | head -10
        fail=1
    fi
done
# Line-number oracle (baksmali .line vs javap -l -c vs --dump-lines).
if ! "$here/lines.sh"; then
    fail=1
fi
exit $fail
