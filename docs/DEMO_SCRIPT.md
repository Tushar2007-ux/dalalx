# Demo script (about 8 minutes)

> **Short of time, or nervous?** Run `./demo.sh` (or open http://localhost:5173/#/demo): the **autopilot** plays a 13-step, roughly 3-minute version of this script by itself, with captions at the bottom. Use *Pause* to talk over any step and *Skip* to move on. The manual script below gives you more to say.

**Before the examiners arrive:**
- Start everything with `./run.sh` and open http://localhost:5173. That's the **front page**; "Open the terminal" goes to the Trading Floor.
- Let the bots trade for a minute so the chart and tape fill up.
- Pick an edition: Day edition looks best on a projector, Night edition on a laptop screen.
- Have IntelliJ open on `SymbolEngine.java` and `docs/UML.md`.

---

### 1. The pitch (30 s) — Trading Floor

> "DalalX is a simulated stock exchange: a matching engine in pure Java, five stocks each matched on its own thread, a Spring server, and a newspaper-style React front end. Everything you see moving is real matching, driven by five trading bots."

Point at:
- the Markets column and the stock tabs;
- the live candlestick chart and the trade tape (the monograms are who bought and who sold);
- The Wire headlines.

### 2. Place an order (1 min) — the order book and matching

1. Click a price in the **order book**. It fills the ticket.
2. Choose **Limit**, 50 shares, **Buy** → confirm. Show the toast and the FILLED stamp.
3. Place a limit buy *below* the best ask. It rests: show it under **Your orders** and in the book. Cancel it.

> "Best price first, then first come, first served. Trades happen at the resting order's price."

### 3. X-Ray (1 min)

1. Switch **X-Ray on** in the ticket column: every book level shows the stamp of the trader behind it.
2. Click **See how your last order was matched** and press **Play**.

> "Before matching, the engine copies the book on the stock's own thread, so this is exactly what the order saw: ① ② ③ fills at the resting prices, then the stop, then the rest of the order resting."

### 4. Patterns in the code (1 min) — IntelliJ / `docs/UML.md`

Show the matching class diagram:
- **Strategy:** one matcher per order type.
- **Template Method:** the shared loop.
- **Factory.**
- **Observer:** the event bus with eight listeners.

> "Adding Fill-or-Kill is one new class and one line: Open/Closed."

### 5. Concurrency (1 min) — `docs/uml/03-concurrency.png`

> "Each stock has one worker thread that owns its book. Other threads only drop commands in a BlockingQueue: producer–consumer. Only one thread touches a book, so the matching code needs no locks. That's thread confinement, the idea behind the LMAX exchange. The stress test fires 50,000 orders from 8 threads and checks nothing is lost or crossed."

### 6. Correspondents and a Flash Crash (2 min)

1. Open **Correspondents**: the bot cards with live P&L. Turn one bot's aggressiveness dial; send one on leave.
2. Under **Today's Storylines**, run **Flash Crash** → go back to the **Trading Floor**, RELIANCE tab.
3. Watch RELIANCE slide. When it falls 10% in 60 seconds:
   - STOP PRESS, the **circuit breaker** halts it;
   - the page greys out, a countdown starts, and the ticket is paused.

> "That's the State pattern: OPEN → HALTED. New orders are refused, but cancels still work."

4. After 30 s it enters the **call auction**: the book is allowed to cross, and the indicative price and volume update live.

> "Orders are collected, and at the bell everything trades at the one price that matches the most shares."

5. At the bell: "Trading resumes in RELIANCE at ₹…", with the auction volume in the toast.

### 7. Surveillance, the innovative feature (1.5 min) — Investigations

1. Open **Investigations**. The Inspector has already caught **The Spoofer** (spoofing and layering).
2. Click the case: show the figures and the **evidence timeline**. Big bids placed and pulled within a quarter-second (✕) while it sells (●).

> "Each rule is a Strategy with its own sliding window; a new type of abuse is a new class. Several conditions must hold together, so the market maker, which also cancels all day, isn't flagged."

3. Click **Suspend The Spoofer**. Show the STOP PRESS headline, the rejected orders, and its bids gone from every book.

### 8. Close (30 s)

> "277 core, 32 server and 49 frontend tests, a stress test and a soak test. 169,000 orders a second on one core, and the benchmark found a real bug that made the engine 48× faster once fixed. Questions?"

---

**If something goes wrong:** the page shows a **Market Closed** edition when the server is unreachable and reconnects by itself. Restart with `./run.sh`. If a storyline is still running, stop it on the Correspondents page.
