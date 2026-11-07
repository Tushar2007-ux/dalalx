/**
 * Viva mode's margin notes: for each part of the screen, the Java class
 * behind it, the pattern or idea it shows, and two lines an examiner would
 * want to hear. Plain data, so it's easy to check and change.
 */
export interface VivaNote {
  /** The class (or classes) to open in IntelliJ. */
  cls: string
  /** The pattern or principle, in a few words. */
  idea: string
  /** Two short sentences to say out loud. */
  say: string
}

export const VIVA_NOTES = {
  markets: {
    cls: 'LiveMarketView · StatusBoard',
    idea: 'Observer',
    say: 'Both listen on the EventBus and keep the latest price and phase per stock. Nobody asks the engine; the engine tells them.',
  },
  wire: {
    cls: 'HeadlineWriter',
    idea: 'Observer of three subjects',
    say: 'It listens to the engine, the storylines and the surveillance desk, and writes a headline when something newsworthy happens. Rules stop it repeating itself.',
  },
  chart: {
    cls: 'CandleAggregator · TradeHistory',
    idea: 'Observer + bounded stores',
    say: 'Every TradeExecuted event updates the open candle for each interval. Stores are bounded, so memory never grows for ever.',
  },
  tape: {
    cls: 'TradeExecuted (record) · MarketDataBridge',
    idea: 'Immutable events, batched every 100 ms',
    say: 'Trades are immutable records, safe to share between threads. The bridge batches them and pushes over STOMP so the browser isn’t flooded.',
  },
  book: {
    cls: 'OrderBook · PriceLevel',
    idea: 'Price-time priority',
    say: 'A TreeMap of price levels per side (best first) and an ArrayDeque per level (first come, first served). A HashMap finds an order by id for O(1) cancels.',
  },
  ticket: {
    cls: 'OrderFactory · MatchingStrategies',
    idea: 'Factory + Strategy + Template Method',
    say: 'The factory gives each order its id and timestamp. Each order type has its own matcher; they share one loop in AbstractOrderMatcher.match().',
  },
  orders: {
    cls: 'Order (entity)',
    idea: 'Encapsulation',
    say: 'Only fill(), cancel() and reject() can change an order, and they keep its status consistent with its remaining quantity.',
  },
  surveillance: {
    cls: 'SurveillanceDesk · SurveillanceRule',
    idea: 'Strategy + Open/Closed',
    say: 'Each kind of abuse is its own rule class with a sliding window. A new rule is a new class; the desk doesn’t change.',
  },
  bots: {
    cls: 'TradingBot · BotRunner',
    idea: 'Strategy + thread confinement',
    say: 'Every bot is a strategy behind one interface. One "bots" thread runs them all, so their own fields need no locks.',
  },
  halt: {
    cls: 'TradingSession · HaltedState',
    idea: 'State pattern',
    say: 'Each phase is a class; the session holds the current one and swaps it on a transition. No if (halted) checks scattered around.',
  },
  auction: {
    cls: 'CallAuction · AuctionState',
    idea: 'Maximum-volume price',
    say: 'For each price, demand is shares bid at or above it and supply is shares offered at or below. The price with the largest min(demand, supply) wins.',
  },
  engine: {
    cls: 'SymbolWorker · Exchange',
    idea: 'Producer–consumer, thread confinement',
    say: 'One worker thread per stock owns its book and takes commands from a BlockingQueue. Only it touches the book, so the book needs no locks.',
  },
  xray: {
    cls: 'XRayDesk · Narrator',
    idea: 'Null Object + pure function',
    say: 'The worker copies the book just before matching, on its own thread, so the replay is exactly what the order saw. Narrator turns the facts into English.',
  },
  leaderboard: {
    cls: 'BotLedger · Leaderboard',
    idea: 'Observer + mark-to-market',
    say: 'The ledger listens to every trade and keeps cash and shares per trader. P&L is cash plus shares valued at the last price.',
  },
  audience: {
    cls: 'GuestDesk',
    idea: 'Token bucket rate limit',
    say: 'Each phone gets a secret token and a small bucket of order tokens that refills three times a second, so nobody can flood the market.',
  },
} satisfies Record<string, VivaNote>

export type VivaKey = keyof typeof VIVA_NOTES
