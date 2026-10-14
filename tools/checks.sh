#!/usr/bin/env bash
# Shared local/CI gates. Required coverage never degrades to a skip or a pass.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mode=${1:-core}
if [[ $# -gt 1 || ! "$mode" =~ ^(core|wayland|privileged)$ ]]; then
    echo "usage: $0 [core|wayland|privileged]" >&2
    exit 2
fi
if [[ ! -f .cache/toolchain/env.sh ]]; then
    echo 'Missing pinned toolchain; run python3 tools/bootstrap.py first.' >&2
    exit 1
fi
source .cache/toolchain/env.sh
logs="${ISAKAI_CHECK_LOG_DIR:-.cache/isakai/checks/$mode}"
mkdir -p "$logs"
{
    date -u +'%Y-%m-%dT%H:%M:%SZ'
    git rev-parse HEAD
    git status --short
    zig version
    java -version
    printf 'profile=%s (Zig output identifies cached checks)\n' "$mode"
} >"$logs/environment.log" 2>&1
check() {
    local name=$1
    shift
    {
        printf 'RUN:'; printf ' %q' "$@"; printf '\n'
        "$@"
    } 2>&1 | tee "$logs/$name.log"
}
check repository python3 tools/check-repository.py
check build zig build
check zig zig build test --summary all
check java zig build test-java
check oracle oracle/diff.sh
check process-exit python3 verify-isakai/check-process-exit.py
check translation-cache python3 verify-isakai/check-translation-cache.py
check runtime python3 verify-isakai/check-runtime.py
check compat-report python3 verify-isakai/check-compat-report.py
check apk-limits python3 verify-isakai/check-apk-limits.py
check packaging python3 verify-isakai/check-packaging.py
if [[ "$mode" != core ]]; then
    check navigation python3 verify-isakai/check-navigation.py
    check harness-failure python3 verify-isakai/check-harness-failure.py
    if [[ "$mode" == privileged ]]; then
        check window tools/e2e-privileged.sh --required
    else
        check window python3 verify-isakai/window-flow.py --unprivileged --required
    fi
fi
printf 'PASS: %s checks; real APK acceptance is a separate gate. Logs: %s\n' "$mode" "$logs"
