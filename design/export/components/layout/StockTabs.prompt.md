Newspaper-style section tabs for switching the active stock.

```jsx
<StockTabs value="RELNC" onChange={setSym} tabs={[{symbol:'RELNC',change:1.2},{symbol:'TATAM',change:-0.8}]} />
```

All colours come from the nearest `[data-edition]`; wrap screens in `<Edition>`. Numbers always use `.dx-num` (IBM Plex Mono, tabular). Buy/sell always pair colour with ▲/▼ and a word.
