#!/usr/bin/env bash
# Phase 0 — boot a production-like stack and wait for health.
# Usage: .claude/skills/polish-site/scripts/preflight.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$ROOT"

if [ ! -f .env ]; then
  echo "• No .env — copying from .env.example"
  cp .env.example .env
  echo "  ⚠ Review .env before shipping (JWT_SECRET, SMTP, Razorpay, S3 are placeholders)."
fi

echo "• docker compose up -d --build"
docker compose up -d --build

echo "• waiting for app on http://localhost:8080 (up to ~120s)"
for i in $(seq 1 60); do
  if curl -fsS -o /dev/null "http://localhost:8080/"; then
    echo "✓ app is up"
    echo "  mailpit (captured email): http://localhost:8025"
    exit 0
  fi
  sleep 2
done

echo "✗ app did not become healthy in time — inspect: docker compose logs --tail=100 app" >&2
exit 1
