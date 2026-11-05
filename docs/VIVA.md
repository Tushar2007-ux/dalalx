# Viva preparation: questions and answers

Short answers you can say out loud, with the class to point at. Grouped by topic; ⭐ marks the ones most likely to be asked.

## A. The domain

**⭐ 1. What does a matching engine do?**
It keeps an order book of buy orders (bids) and sell orders (asks) for each stock. When a new order arrives it trades it against the best opposite orders, and whatever doesn't trade either rests in the book (limit order) or is cancelled (market or IOC). `SymbolEngine.submit`.

**⭐ 2. What is price-time priority?**
The best price trades first (highest bid, lowest ask); among orders at the same price, the earliest trades first. `OrderBook` keeps a `TreeMap` of price levels, and each `PriceLevel` keeps an `ArrayDeque` in arrival order.

**⭐ 3. LIMIT vs MARKET vs IOC?**
For BUY 100 at ₹103 against asks 50 @ ₹102 and 80 @ ₹105: LIMIT buys 50 @ ₹102 and rests 50 as a bid at ₹103; IOC buys 50 @ ₹102 and cancels the rest; MARKET (no limit) buys 50 @ ₹102 and 50 @ ₹105. A market order with nothing to trade against is *rejected*.

**4. At what price does a trade happen?**
At the resting order's price. The incoming order gets any price improvement. The X-Ray shows this: a limit buy at ₹2,487.50 pays ₹2,486.75, ₹2,487.00 and ₹2,487.25.

**⭐ 5. Why are prices `long` and not `double`?**
Binary floating point can't represent 0.1 exactly, so sums drift (`0.1 + 0.2 = 0.30000000000000004`). Money is whole paise in a `long`; it is only divided by 100 for display. Percentages use integer basis points.

## B. OOP and design patterns

**⭐ 6. Which design patterns did you use, and where?**
- **Strategy:** order matchers, bots, surveillance rules, storylines.
- **Template Method:** `AbstractOrderMatcher.match`.
- **Factory:** `OrderFactory`, `StandardBots`, `BotStyle`.
- **Observer:** `EventBus`/`EngineListener`.
- **State:** `MarketState` and `TradingSession`.
- **Adapter:** `ExchangeGateway`, and the whole server module.
- **Memento:** `ScenarioStage.restore`.
- **Null Object:** `XRayRecorder.NONE`, `OrderGate.OPEN`.
- **Command:** the `Command` record sent to workers.

**⭐ 7. Show me the Open/Closed principle.**
Adding Fill-or-Kill means one new `FokOrderMatcher` class and one entry in `MatchingStrategies`. No existing matcher or engine code changes. The same goes for a new bot, surveillance rule, storyline or market phase (the auction was added without editing `OpenState` or `HaltedState`).

**8. Why an abstract class *and* an interface for matchers?**
The interface (`MatchingStrategy`) is what the engine depends on. The abstract class (`AbstractOrderMatcher`) shares the loop between the three implementations. A future matcher with a completely different algorithm can implement the interface directly.

**⭐ 9. State pattern versus a boolean `halted` flag?**
With flags, every place that cares (accepting orders, matching, the clock, the UI) needs `if (halted) … else if (auction) …`, and each new phase means editing all of them. With State, each phase's behaviour lives in one class, the engine delegates to the current state, and a transition is swapping one object.

**10. Where is encapsulation?**
`Order` exposes no setters; only `fill`, `cancel` and `reject` change it, and they keep the status consistent with the quantity. The book's maps are private, and callers get immutable `BookSnapshot`s.

**11. Where is polymorphism?**
`strategies.forType(type).match(order, book)` calls a different `match` depending on the object. The same goes for `bot.act(ctx)` and `rule.inspect(event, log)`.

**12. Why `record`s?**
They're immutable value objects with `equals`/`hashCode` for free. Events, trades, snapshots and DTOs are records: they cross threads, so being immutable makes them safe to share without locks.

**13. What is Dependency Inversion here?**
High-level code depends on abstractions: bots depend on `MarketView` and `OrderGateway`, not on `Exchange`; the engine depends on `OrderGate`, not on surveillance. Tests pass in fakes (`FakeMarket`, `EngineGateway`).

## C. Concurrency ⭐⭐

**⭐ 14. How is the exchange multi-threaded?**
There is one worker thread per stock. Each worker owns its stock's book and takes commands from a `BlockingQueue`. Web threads, the bot thread and the clock thread are producers; the worker is the single consumer. Five stocks match in parallel.

**⭐ 15. The order book has no locks. Why is that safe?**
Thread confinement: only the stock's worker thread ever reads or writes its book. The queue hand-off gives a happens-before guarantee: everything a producer did before handing over a command is visible to the worker after it takes it. Nothing is shared, so nothing needs locking.

**⭐ 16. Why not just make the book `synchronized`?**
Every thread would compete for the same lock on a busy book: blocking, context switches, and fairness decided by the scheduler, not by arrival order. Confinement keeps the matching code simple, lock-free and cache-friendly. This is the LMAX architecture.

**17. How does a web request get its result back?**
`exchange.submit()` returns a `CompletableFuture` at once. The controller waits on it (5 s timeout). The worker completes it after matching, which wakes the web thread.

**18. What if the queue fills up?**
Back-pressure: a producer waits up to one second (`offer` with a timeout), then the order is refused with "too busy" (HTTP 503) instead of blocking for ever. `BackPressureTest`.

**19. How do you shut down cleanly?**
A poison-pill command: the worker finishes everything queued before it, then exits. New commands are refused after `shutdown()`. Spring stops the bots before the exchange (bean dependency order).

**20. How did you test the concurrency?**
`ExchangeStressTest`: 8 producer threads send 50,000 random orders and cancels. It then checks that no order was lost or duplicated, that filled + remaining = original for every order, that trades add up, and that no book is crossed. Other tests prove two stocks really run in parallel (a latch that would deadlock on one thread). `SoakTest` runs everything at once.

**21. The bots: are they thread-safe?**
All bots run on one "bots" thread, so their own state is confined to it. The only cross-thread input is pause/tune from web requests: an immutable `BotTuning` in a `volatile` field, and statuses in a `ConcurrentHashMap`.

**22. Which concurrent collections did you use, and why?**
- `LinkedBlockingQueue`: the worker hand-off.
- `CopyOnWriteArrayList`: listeners (many reads, rare writes).
- `ConcurrentHashMap`: statuses, quotes, suspensions.
- `ConcurrentLinkedQueue`: bridge buffers and requests to the bot thread.
- `AtomicLong`: ids.
- Plain collections guarded by `synchronized`: small, rarely contended stores (ledger, headlines, surveillance desk).

## D. Features

**⭐ 23. How does the circuit breaker work?**
Every trade is compared with the highest and lowest price of the last 60 seconds. A move of 10% or more halts the stock for 30 s: new orders are rejected with the reason, but cancels still work. Then a 30-second call auction reopens it. The 60-second high and low come from two monotonic deques in O(1) amortised time.

**⭐ 24. What is a call auction and how is the price chosen?**
Orders are collected without matching. For each price p, demand D(p) is the shares bid at p or above and supply S(p) is the shares offered at p or below. The price with the largest min(D, S) wins; ties go to the smallest |D − S|, then to the price nearest the last trade. Then everything that crosses trades at that one price. Afterwards the book can't be crossed: a leftover crossing pair would mean a higher-volume price existed.

**⭐ 25. Your innovative feature?**
Real-time market surveillance. An Inspector watches every order and trade, detects spoofing, layering, wash trades and pumps with sliding-window rules, opens a case with an evidence timeline, and can suspend the trader. Suspension rejects their orders on every stock and cancels everything they have resting. A Spoofer bot demonstrates it live; it's caught within seconds.

**26. How do you avoid flagging honest traders?**
Each rule needs several conditions together. Spoofing needs *large* orders (≥ 5,000), mostly cancelled, across at least 3 orders. Layering needs 3+ prices within 5 ticks, pulled in under 1 s, *and all in the book at the same moment*. The market maker re-quotes all day but with small orders, wide steps, and one level at a time, so it isn't flagged. A test proves it.

**27. What is X-Ray?**
It replays how your order was matched. Just before matching, the worker copies the opposite side of the book with every order's owner (immutable records). After matching, the facts go to a pure `Narrator` that writes the step-by-step explanation. Because it all happens on the one worker thread, no other order can slip in between the copy and the match.

**28. How do the headlines work?**
`HeadlineWriter` listens to the engine, the storyline director and the surveillance desk (Observer). It reports new 1%-level moves, big pull-backs, block trades, storylines, halts, auctions and investigations, with rules that stop the same news repeating.

## E. Server and frontend

**29. What does Spring do, and what doesn't it do?**
It runs the web server, converts JSON, routes URLs, wires objects (beans) and manages the lifecycle. It does no exchange logic: controllers convert a request into a core call and convert the answer back. Core has no Spring imports.

**30. REST vs WebSocket?**
REST is request/response, for actions and loading a page. The WebSocket (STOMP topics) lets the server push live data the moment it changes; polling would waste requests and still lag.

**31. Why throttle the WebSocket?**
Bots trade hundreds of times a second. The bridge sends the latest book per stock every 100 ms (older snapshots are simply replaced) and all trades as a batch. The browser then applies updates once per animation frame.

**32. How are errors reported?**
In one place, `ApiExceptionHandler`: unknown stock → 404, invalid input → 400, too busy or shutting down → 503. The body is always `{"error": "..."}`.

## F. Testing and quality

**⭐ 33. How did you test it?**
JUnit 5 + AssertJ, written with the code: about 280 core tests (model, book, every matcher, events, concurrency stress, bots, storylines, breaker, auction, X-Ray, surveillance, soak), 32 server tests (MockMvc against the real exchange, plus a real WebSocket client), and 49 frontend tests (vitest).

**34. How fast is it?**
On a 2-CPU container: 169,000 orders/s for one engine (median 4.6 µs per order), and 72,000 orders/s through the whole exchange (median 43 µs round trip). The benchmark also found a real bug, an O(queue) scan in X-Ray; fixing it made the engine 48× faster. See `docs/BENCHMARK.md`.

**35. Can memory grow for ever?**
No. Every store is bounded: recent orders, routes, trades, candles, the activity log, cases, the wire and X-Rays.

**36. What would you add next?**
- Fill-or-Kill and stop orders (new strategies).
- Persistence and event sourcing (replay the event log to rebuild books).
- Self-trade prevention.
- A ring-buffer queue (LMAX Disruptor) for lower latency.
- User accounts with positions and margin.

**37. What did you find hardest?**
A good honest answer: keeping every piece of mutable state on exactly one thread while web, bot and clock threads all need to interact with it. The solution was always the same: send a command to the owning thread, or share only immutable values.
