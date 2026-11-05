# DalalX benchmark

Run it yourself (numbers depend heavily on the machine):

```bash
./bench.sh                       # 1,000,000 engine orders, 400,000 exchange orders, 4 producers
./bench.sh 500000 200000 4       # smaller run
```

## What is measured

| Test | What it shows |
|---|---|
| **Engine alone** | One `SymbolEngine` on one thread: no queue, no other threads. The raw cost of creating an order, matching it against the book, publishing its events and (20% of the time) cancelling an earlier order. |
| **Whole exchange** | 4 producer threads send orders for all five stocks through `Exchange.submit()`. Each producer waits for its answer before sending the next, and each order is timed from `submit()` until its future completes: the full round trip through the worker's `BlockingQueue`, the matching, and back. |

The order flow is random but reproducible (fixed seeds): 80% limit, 15% IOC, 5% market, prices within ±₹5 of ₹1,000 in 10-paise steps, sizes 1–200. A warm-up round runs first so the JIT compiler has optimised the hot code before anything is timed.

## Results (development container: 2 CPUs, Java 21)

| Test | Orders | Throughput (orders/s) | p50 | p99 | p99.9 |
|---|---:|---:|---:|---:|---:|
| Engine alone (1 thread, no queue) | 500,000 | 169,315 | 4.6 µs | 27.4 µs | 77.6 µs |
| Whole exchange (4 producers → 5 workers) | 200,000 | 72,380 | 43.3 µs | 181.6 µs | 1,422 µs |

A laptop with more cores will usually do better on the exchange test, because the five workers really run in parallel there.

## What the numbers say

- **Matching is microseconds.** Half of all orders are matched, announced and done in under 5 µs. The worst 1-in-1,000 take under 0.1 ms; those are usually orders that sweep many levels or happen to run during a garbage collection.
- **The queue costs about ten times the match.** Handing an order to another thread and waking the producer when it's done (a `BlockingQueue` put/take plus completing a `CompletableFuture`) costs more than matching it. That's the price of thread confinement, and it buys lock-free matching code that is simple to reason about. LMAX's Disruptor removes most of this cost with a ring buffer and busy-spinning threads.
- **It's fast enough for the show by a factor of a thousand.** All the bots together send a few hundred orders a second. The 100 ms WebSocket batching, not the engine, decides how often the screen updates.

## A bug the benchmark found

The first run managed only **3,485 orders/s**. The cause was the X-Ray feature: every book update named the "main trader" at each of the 10 levels by walking *every order in that level's queue*. Under the benchmark's flow a level holds thousands of orders, so each order cost tens of thousands of steps. The fix was to keep a running total of shares per trader in each `PriceLevel`, updated on add, fill and remove, so finding the main trader is a scan over a handful of traders. The engine got **48× faster** (3,485 → 169,315 orders/s) with no change in behaviour, and every existing test still passes. The lesson: measure; the slow part is rarely where you expect it.
