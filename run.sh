#!/usr/bin/env bash
# Starts the DalalX backend (Spring Boot, :8080) and frontend (Vite, :5173) together.
# Press Ctrl+C to stop both.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

BACKEND_PID=""
FRONTEND_PID=""
WATCH_PID=""

# Called on Ctrl+C or when either process exits: stop whatever is still running.
cleanup() {
  trap - INT TERM EXIT
  echo
  echo "Stopping DalalX..."
  for pid in "$WATCH_PID" "$FRONTEND_PID" "$BACKEND_PID"; do
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

# Install (or update) the frontend's packages when package-lock.json is newer than what's installed.
if [[ ! -d frontend/node_modules || frontend/package-lock.json -nt frontend/node_modules/.package-lock.json ]]; then
  echo "Installing frontend dependencies..."
  (cd frontend && npm install)
fi

echo "Building core and starting backend on http://localhost:8080 ..."
# 'install' puts core into the local repo so the server module can resolve it,
# then spring-boot:run starts the server. Only the build's key lines are shown.
( mvn -DskipTests -ntp install 2>&1 | grep --line-buffered -E "Building |BUILD|ERROR" ;
  [[ ${PIPESTATUS[0]} -eq 0 ]] && cd server && exec mvn -q spring-boot:run ) &
BACKEND_PID=$!

# Say clearly when the exchange is up, or that it is still building.
( waited=0
  until curl -s -o /dev/null localhost:8080/api/health; do
    sleep 5; waited=$((waited + 5))
    if (( waited % 30 == 0 )); then echo "... still starting the exchange (${waited}s). The first build can take a couple of minutes."; fi
  done
  echo "✓ Exchange is up on http://localhost:8080. Open http://localhost:5173" ) &
WATCH_PID=$!

echo "Starting frontend on http://localhost:5173 ..."
(cd frontend && exec npm run dev) &
FRONTEND_PID=$!

# Keep running until either process exits (or Ctrl+C is pressed); cleanup runs via trap.
# A polling loop is used instead of 'wait -n' because macOS ships bash 3.2.
while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
  sleep 1
done
