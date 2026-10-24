#!/usr/bin/env bash
# R4: required CI uses --required (or ISAKAI_E2E_REQUIRED=1).
# Missing prerequisites: exit 77 locally, exit 1 when required. Never a PASS.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# This entry point must never be downgraded to an unprivileged check.
args=("$@")
elevation_set=0
while (( $# )); do
  case "$1" in
    --required)
      shift
      continue
      ;;
    --elevate)
      if [[ $# -ge 2 && ( "$2" == sudo || "$2" == pkexec ) ]]; then
        elevation_set=1
        shift 2
        continue
      fi
      ;;
  esac
  echo "usage: $0 [--required] [--elevate {sudo,pkexec}]" >&2
  exit 2
done
if (( ! elevation_set )) && ! sudo -n true >/dev/null 2>&1 && command -v pkexec >/dev/null; then
  args+=(--elevate pkexec)
fi
exec python3 "$ROOT/verify-isakai/window-flow.py" "${args[@]}"
