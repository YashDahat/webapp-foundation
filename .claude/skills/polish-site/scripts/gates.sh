#!/usr/bin/env bash
# Phase 1 — static gates. Runs every gate, reports which failed (does not stop at first failure
# so you see the full picture), then exits non-zero if any failed.
# Usage: .claude/skills/polish-site/scripts/gates.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$ROOT"
FAIL=0
run() { echo; echo "──▶ $1"; shift; "$@" || { echo "✗ FAILED: $*"; FAIL=1; }; }

# Frontend
if [ -d frontend ]; then
  ( cd frontend
    [ -d node_modules ] || npm ci
  )
  run "frontend typecheck" bash -c "cd frontend && npm run typecheck"
  run "frontend lint"      bash -c "cd frontend && npm run lint"
  run "frontend build"     bash -c "cd frontend && npm run build"
fi

# Backend
if [ -f backend/pom.xml ]; then
  run "backend verify" bash -c "cd backend && mvn -q verify"
fi

echo
if [ "$FAIL" -eq 0 ]; then echo "✓ all static gates green"; else echo "✗ one or more gates failed — fix generated code and re-run"; fi
exit "$FAIL"
