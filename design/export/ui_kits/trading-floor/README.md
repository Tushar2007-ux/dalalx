# Trading Floor UI kit

The DalalX terminal: masthead with edition switch, live headline banner, stock section tabs, and four ruled columns (Markets + closing-bell countdown; RELNC price, pen chart and trade tape; order book; order ticket + surveillance).

Interactions: the market ticks every 900ms; switch Day/Night from the masthead (persisted in localStorage); place an order from the ticket → confirm modal → FILLED stamp + toast. About 13s in, the Spoofer starts layering bids: STOP PRESS headline, flagged book rows, a critical notice with a red-pencil circle, and an Inspector margin note.

Composed entirely from `components/`; no primitives are re-implemented here. Built from the brief only; there is no production app to copy.
