#!/usr/bin/env bash
# R4: required CI uses --required (or ISAKAI_E2E_REQUIRED=1).
# Missing prerequisites: exit 77 locally, exit 1 when required. Never a PASS.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# This entry point must never be downgraded to an unprivileged check.
for arg in "$@"; do
  if [[ "$arg" != --required ]]; then
    echo "usage: $0 [--required]" >&2
    exit 2
  fi
done
exec python3 "$ROOT/verify-isakai/window-flow.py" "$@"
