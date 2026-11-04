Buy / Sell selector; always shows ▲/▼ and the word, never colour alone.

```jsx
<SideToggle value={side} onChange={setSide} />
```

All colours come from the nearest `[data-edition]`; wrap screens in `<Edition>`. Numbers always use `.dx-num` (IBM Plex Mono, tabular). Buy/sell always pair colour with ▲/▼ and a word.
