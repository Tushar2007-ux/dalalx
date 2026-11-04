# DalalX

A multi-stock exchange order matching simulator, built for an Object-Oriented
Modelling course. UI theme: *The Morning Edition*.

| Module      | What it is                                              |
|-------------|---------------------------------------------------------|
| `core/`     | Pure Java 21 matching engine and exchange (no Spring)   |
| `server/`   | Spring Boot 3 adapter exposing core over REST/WebSocket |
| `frontend/` | React + Vite + TypeScript UI                            |
| `design/`   | UI designs (read-only)                                  |
| `docs/`     | Notes, UML and guides                                   |

## Requirements
Java 21+, Maven 3.9+, Node 20+.

## Run
```bash
./run.sh            # backend on :8080, frontend on :5173 — Ctrl+C stops both
```

## Test
```bash
mvn -q verify                       # Java tests
(cd frontend && npm test && npm run build)   # frontend tests + type check
```

See `CLAUDE.md` for project rules and `docs/NOTES.md` for a build log.
