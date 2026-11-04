#!/usr/bin/env bash
# Starts the DalalX backend (Spring Boot, :8080) and frontend (Vite, :5173) together.
# Press Ctrl+C to stop both.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

BACKEND_PID=""
FRONTEND_PID=""

# Called on Ctrl+C or when either process exits: stop whatever is still running.
cleanup() {
  trap - INT TERM EXIT
  echo
  echo "Stopping DalalX..."
  for pid in "$FRONTEND_PID" "$BACKEND_PID"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      # Kill the whole process group so child processes (java, node) also stop.
      kill -- -"$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    fi
  done
  wait 2>/dev/null || true
}
trap cleanup INT TERM EXIT

# Job control gives each background job its own process group (needed for kill -- -PID).
set -m

if [[ ! -d frontend/node_modules ]]; then
  echo "Installing frontend dependencies..."
  (cd frontend && npm install)
fi

echo "Building core and starting backend on http://localhost:8080 ..."
# 'install' puts core into the local repo so the server module can resolve it,
# then spring-boot:run starts the server.
mvn -q -DskipTests install && (cd server && exec mvn -q spring-boot:run) &
BACKEND_PID=$!

echo "Starting frontend on http://localhost:5173 ..."
(cd frontend && exec npm run dev) &
FRONTEND_PID=$!

# Keep running until either process exits (or Ctrl+C is pressed); cleanup runs via trap.
# A polling loop is used instead of 'wait -n' because macOS ships bash 3.2.
while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
  sleep 1
done
