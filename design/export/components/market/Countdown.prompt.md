Session clock (closing bell, auction end). Digits change instantly; colour escalates near zero.

```jsx
<Countdown endsAt={Date.now() + 134000} label="Closing bell in" />
```

All colours come from the nearest `[data-edition]`; wrap screens in `<Edition>`. Numbers always use `.dx-num` (IBM Plex Mono, tabular). Buy/sell always pair colour with ▲/▼ and a word.
