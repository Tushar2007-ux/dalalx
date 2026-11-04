A live price; inks in with a brief green/red tint when it moves. Coalesces to ≤4 inks/s.

```jsx
<PriceCell value={2481.5} change={12.4} changePct={0.5} showChange size="lg" prefix="₹" />
```

All colours come from the nearest `[data-edition]`; wrap screens in `<Edition>`. Numbers always use `.dx-num` (IBM Plex Mono, tabular). Buy/sell always pair colour with ▲/▼ and a word.
