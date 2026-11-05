# Learning guide: how to study DalalX

This guide is for you, Tushar. You know basic Java, the four OOP principles, UML, C++ and OS synchronisation (pthreads, mutexes, producer–consumer). Read the code in the order below: each stage builds on the one before. Allow about **12–15 hours** in total; the times are rough.

For every class, the Javadoc at the top says what it is, why it exists and which pattern it shows. Read that first, then the code. `docs/NOTES.md` has five viva points per step, and `docs/UML.md` has the diagrams.

> **Tip:** open the project in IntelliJ IDEA (Community is free). Ctrl/Cmd-click a name to jump to it, and right-click a test → *Run* to watch the code work. Every class has tests showing exactly how it behaves.

---

## Stage 1 — The vocabulary (1 h) · `core/.../model`

| Read | Learn |
|---|---|
| `Side`, `OrderType`, `OrderStatus` | Java **enums with behaviour** (`Side.opposite()`, `OrderType.requiresLimitPrice()`). Better than C++ `enum` because they're full classes. |
| `OrderRequest`, `Trade` | **`record`s**: immutable value objects; the compiler writes the constructor, getters, `equals`, `hashCode` and `toString`. The *compact constructor* validates input (e.g. the fat-finger limits). |
| `Order` | An **entity**: mutable (it fills), with **encapsulation**: you can't set `remainingQty` directly, only `fill()`, `cancel()` or `reject()`, which keep the rules (status always matches the quantity). |
| `Money` | Why money is `long` **paise**, never `double` (`0.1 + 0.2 != 0.3` in floating point). |

**Try:** in `OrderTest`, add a test that filling more than the remaining quantity throws.

## Stage 2 — The order book (1.5 h) · `book`

| Read | Learn |
|---|---|
| `PriceLevel` | An `ArrayDeque` as a FIFO queue: **time priority** at one price. |
| `OrderBook` | `TreeMap` (a red-black tree, like C++ `std::map`) keyed by price; bids use `Comparator.reverseOrder()` so the best price is always `firstEntry()`. A `HashMap` from id to order gives O(1) lookup for cancels. |
| `BookSnapshot`, `LevelSnapshot` | Giving out immutable copies instead of the live book (defensive copies). |

**Key idea:** *price-time priority*: best price first, and earliest first at the same price. Know the complexity: add and match are O(log L) for L price levels; cancel is O(1) to find, plus O(n) within its level's queue.

## Stage 3 — Matching (2 h) · `matching`

Read `MatchingStrategy` → `AbstractOrderMatcher` → `LimitOrderMatcher`, `MarketOrderMatcher`, `IocOrderMatcher` → `MatchingStrategies` → `OrderFactory` → `SymbolEngine`.

- **Strategy pattern:** one class per order type behind one interface.
- **Template Method:** the loop is written once, in a `final` method; subclasses fill in the hooks.
- **Open/Closed:** to add Fill-or-Kill you write one new class and add one map entry, without editing any existing matcher.
- Trades happen at the **resting** order's price (the price improvement goes to the incoming order).

**Try:** write `FokOrderMatcher` (fill completely or cancel completely) as an exercise; the tests in `IocOrderMatcherTest` are a template.

## Stage 4 — Events (1 h) · `events`

`EngineEvent` (a `sealed interface`), the seven event records, `EngineListener`, `EventBus`, and how `SymbolEngine.publishOutcome` uses them.

- **Observer pattern**, and why `CopyOnWriteArrayList` suits it (reads are constant, writes are rare).
- **`sealed` + pattern-matching `switch`:** see `MarketDataBridge.onEvent` in the server; the compiler forces every event type to be handled. (Adding `AuctionUpdate` in Step 13 broke the build until the bridge handled it. That's the point.)

## Stage 5 — Concurrency (2 h) · `exchange` ⭐ examiners love this

Read `Command`, `SymbolWorker`, `Exchange`, then `ExchangeStressTest`.

Map it to what you know from OS:

| OS course | DalalX |
|---|---|
| producer–consumer with a bounded buffer | `LinkedBlockingQueue` (capacity 100,000), with `offer` and a timeout, and `take()` |
| a thread that owns a resource | each `SymbolWorker` thread owns one book (**thread confinement**): no mutex is needed on the book |
| `pthread_join` / termination | the poison-pill command in `shutdown()` |
| a condition variable to wait for a result | `CompletableFuture.get()` |
| atomic counter | `AtomicLong` for ids |

Understand **why** there are no locks in the matching code: only one thread ever touches a book, and the queue hand-off gives a *happens-before* guarantee (everything a producer did before `put` is visible to the worker after `take`). This is the LMAX architecture.

**Try:** run `ExchangeStressTest` and read what it checks.

## Stage 6 — The Spring server (1 h) · `server/`

`ExchangeConfig` (creating beans: who builds what, and in what order), `OrderController`, `MarketDataController`, `ApiExceptionHandler`, `MarketDataBridge` (throttling to 10 updates/s), `WebSocketConfig`.

**Key idea:** the server only **adapts** core to HTTP; it contains no exchange logic (*Ports & Adapters*). Core has zero Spring imports. Check: `grep -r springframework core/` finds nothing.

## Stage 7 — Bots and storylines (1.5 h) · `bots`, `scenarios`

`TradingBot` → `AbstractBot` → the five bots → `BotRunner` → `BotContext`, `MarketView`, `OrderGateway`, `ExchangeGateway` → `BotTuning` → `ScenarioDirector`, `ScenarioStage`, `Storylines`.

- Strategy again; **Dependency Inversion** (bots depend on interfaces, so tests use fakes); the **Adapter** pattern (`ExchangeGateway`).
- One thread runs all the bots, so their own state is thread-confined. Web threads only flip `volatile`/concurrent values (`BotTuning` is immutable and held in a `volatile` field).
- `ScenarioStage` remembers and restores the bots' settings (a **Memento**).

## Stage 8 — Circuit breaker and auction (1.5 h) · `breaker`, `auction`

`MarketState` → `OpenState`, `HaltedState`, `AuctionState` → `TradingSession` → `CircuitBreaker`, `PriceWindow` → `CallAuction`.

- **State pattern** versus a boolean flag (see the viva answers).
- `PriceWindow`: the **sliding-window maximum** with monotonic deques, O(1) amortised. A classic DSA problem (LeetCode 239) used for real.
- `CallAuction`: the max-volume price and why nothing is left crossed afterwards.

## Stage 9 — X-Ray, surveillance and the newsroom (1.5 h) · `xray`, `surveillance`, `headlines`

- `XRayDesk`, `Narrator`: a pure function that turns facts into English, so it's easy to test.
- `SurveillanceRule` and the four rules, `SurveillanceDesk`, `Suspensions`, `OrderGate`.
- `HeadlineWriter`: one Observer listening to three subjects.

## Stage 10 — The frontend (1 h, optional depth) · `frontend/src`

`lib/` (API, STOMP stream with `requestAnimationFrame` batching, money formatting), `store/market.ts` (zustand), `terminal/`, `pages/`. The examiners care mostly about Java, but know that **the frontend never does exchange logic**: it only displays what the server says.

---

## Checklist before the viva

- [ ] I can draw the class diagram of matching and of the State machine from memory.
- [ ] I can explain price-time priority with a 3-order example.
- [ ] I can explain LIMIT vs MARKET vs IOC with the same book (NOTES, Step 3).
- [ ] I can explain why the book has no locks (thread confinement + queue hand-off).
- [ ] I can name each pattern and point to its class: Strategy, Template Method, Factory, Observer, State, Adapter, Memento, Null Object (`XRayRecorder.NONE`), Open/Closed, Dependency Inversion.
- [ ] I can run the demo (`docs/DEMO_SCRIPT.md`) without notes.
- [ ] I've read `docs/VIVA.md` twice.
