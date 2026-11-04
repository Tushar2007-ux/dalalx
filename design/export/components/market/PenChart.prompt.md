Intraday line drawn in ink; only the newest segment draws, like a pen extending.

```jsx
<PenChart data={prices} prevClose={2450} width={420} height={140} />
```

All colours come from the nearest `[data-edition]`; wrap screens in `<Edition>`. Numbers always use `.dx-num` (IBM Plex Mono, tabular). Buy/sell always pair colour with ▲/▼ and a word.
