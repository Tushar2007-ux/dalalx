Byline card for a bot: portrait, name, desk, quote, status, stats. card or compact layout.

```jsx
<BotByline bot="munim" stats={[{label:'P&L',value:'₹4,210',dir:'up'}]} />
```

All colours come from the nearest `[data-edition]`; wrap screens in `<Edition>`. Numbers always use `.dx-num` (IBM Plex Mono, tabular). Buy/sell always pair colour with ▲/▼ and a word.
