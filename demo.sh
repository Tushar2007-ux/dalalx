#!/usr/bin/env bash
# Starts DalalX (like ./run.sh) and opens the browser on the demo autopilot.
# Press Ctrl+C to stop everything.
set -euo pipefail
cd "$(dirname "$0")"

./run.sh &
RUN=$!
trap 'kill "$RUN" 2>/dev/null; wait "$RUN" 2>/dev/null' INT TERM

echo "Waiting for the exchange and the presses to warm up..."
until curl -s -o /dev/null localhost:8080/api/health && curl -s -o /dev/null localhost:5173; do
  if ! kill -0 "$RUN" 2>/dev/null; then echo "DalalX did not start; see the messages above."; exit 1; fi
  sleep 1
done
sleep 5 # let the bots fill the books first

URL="http://localhost:5173/#/demo"
echo "Opening $URL"
open "$URL" 2>/dev/null || xdg-open "$URL" 2>/dev/null || echo "Open $URL in your browser."
wait "$RUN"
