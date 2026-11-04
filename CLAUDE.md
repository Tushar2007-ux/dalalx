# DalalX — Project Rules

DalalX is a stock exchange order matching system built for a university
Object-Oriented Modelling (OOM) course. The author will study and be examined
on this code: **clarity and good OOP design matter more than cleverness.**

## Goal
Multi-stock exchange simulator:
- Pure-Java matching engine
- Spring Boot server (REST/WebSocket)
- React frontend
- Trading bots
- Circuit breaker
- X-Ray mode
- Market surveillance
- Call auction
- Live newspaper headlines

UI theme: **"The Morning Edition"** — a financial newspaper, with Day and Night editions.

## Layout (Maven multi-module)
- `core/` — pure Java 21. **NO Spring or any framework dependency.** Contains engine,
  exchange, bots, surveillance, breaker, auction, headlines.
- `server/` — Spring Boot 3, depends on `core`, and **only adapts** it to REST/WebSocket.
  No business logic here.
- `frontend/` — React + Vite + TypeScript.
- `design/` — UI designs. **Read-only, never modify.**
  - `design/DESIGN_SPEC.md` — handoff spec
  - `design/screens/` — 16 screen PNGs + README
  - `design/assets/` — original portraits
  - `design/export/` — Claude Design export:
    - `tokens/*.css` — **source of truth for all values** (colours, type, layout, motion)
    - `components/**/*.jsx` — reference components, with `.d.ts` props
    - `ui_kits/` — working prototypes
    - `assets/portraits/` — processed portraits to use in the app
- `docs/` — notes, UML, guides.

## Packages in core
`com.dalalx.core.model`, `.book`, `.matching`, `.events`, `.exchange`, `.bots`,
`.scenarios`, `.breaker`, `.xray`, `.surveillance`, `.auction`, `.headlines`.

## Domain rules
- Symbols (simulated): `RELIANCE`, `TCS`, `INFY`, `HDFCBANK`, `TATAMOTORS`.
- Prices are `long` in **paise** (₹102.50 = `10250`). **Never use `double` for money.**
- Quantities are `long`.

## Design patterns (name them in Javadoc)
- **Strategy** — order-type matching, bot behaviour
- **Factory** — order creation
- **Observer** — engine events
- **State** — market status / circuit breaker
- **Open/Closed** — new rules and order types are added without editing existing code

## Concurrency model
- Each symbol has **exactly one worker thread** that owns its order book.
- All access to a book goes through that worker's `BlockingQueue`.
- The order book itself is **NOT thread-safe by design** (thread confinement).
  Explain this in comments wherever it matters.

## Code style
- Every class gets a Javadoc paragraph: what it is, why it exists, and which
  pattern/principle it shows.
- Comment non-obvious logic for a student reader.
- Keep methods short.
- Prefer immutable `record`s for value objects.
- No Lombok.

## Workflow for every step
1. Write JUnit 5 tests (AssertJ for assertions).
2. Run `mvn -q verify` (and frontend checks — `npm run build` / `npm run lint` in
   `frontend/` — when relevant).
3. Fix until green.
4. Append a short summary of what was built to `docs/NOTES.md`.
