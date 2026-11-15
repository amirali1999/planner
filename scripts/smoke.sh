#!/usr/bin/env bash
# Demo 0 API smoke test against a throwaway backend.
# - builds the current backend jar (never a stale one)
# - starts it on 127.0.0.1:${SMOKE_PORT:-18080} with a temporary SQLite file (never data/planner.db)
# - runs the core workflow, restarts the backend, verifies everything is unchanged, then cleans up
# Requires: JDK 21, Node 22.12+, curl. Usage: scripts/smoke.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${SMOKE_PORT:-18080}"
API="http://127.0.0.1:${PORT}/api/v1"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/planner-smoke.XXXXXX")"
DB="$WORK/planner.db"
PID=""

cleanup() {
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then kill "$PID"; wait "$PID" 2>/dev/null || true; fi
  rm -rf "$WORK"
}
trap cleanup EXIT

if curl -s -o /dev/null "http://127.0.0.1:${PORT}/" 2>/dev/null; then
  echo "Port ${PORT} is already in use; set SMOKE_PORT to a free port." >&2
  exit 1
fi

echo "== building backend jar"
# Offline first: with cached dependencies Maven otherwise can stall for minutes checking remote repositories.
# Falls back to an online build (first run on a fresh machine).
(cd "$ROOT/backend" && { ./mvnw -o -q -B -DskipTests package || ./mvnw -q -B -DskipTests package; })
JAR="$(ls "$ROOT"/backend/target/planner-backend-*.jar | grep -v '\.original$' | head -1)"

start_backend() {
  PLANNER_DB_PATH="$DB" PLANNER_SERVER_PORT="$PORT" java -jar "$JAR" > "$WORK/backend-$1.log" 2>&1 &
  PID=$!
  for _ in $(seq 1 60); do
    if curl -s -o /dev/null "$API/health"; then return 0; fi
    if ! kill -0 "$PID" 2>/dev/null; then echo "backend exited; log:" >&2; cat "$WORK/backend-$1.log" >&2; exit 1; fi
    sleep 1
  done
  echo "backend did not become healthy in 60s" >&2
  exit 1
}

stop_backend() {
  kill "$PID"; wait "$PID" 2>/dev/null || true; PID=""
}

echo "== phase 1: workflow (temporary DB: $DB)"
start_backend 1
node "$ROOT/scripts/smoke-api.mjs" "$API" workflow "$WORK/state.json"
stop_backend

echo "== phase 2: restart and verify"
start_backend 2
node "$ROOT/scripts/smoke-api.mjs" "$API" verify "$WORK/state.json"
stop_backend

echo "SMOKE PASS"
