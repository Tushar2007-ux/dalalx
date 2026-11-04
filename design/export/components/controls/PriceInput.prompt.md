Numeric field with ₹ prefix and unit suffix (qty, shares); mono tabular figures.

```jsx
<PriceInput label="Limit price" prefix="₹" suffix="per share" value={px} onChange={setPx} />
```

All colours come from the nearest `[data-edition]`; wrap screens in `<Edition>`. Numbers always use `.dx-num` (IBM Plex Mono, tabular). Buy/sell always pair colour with ▲/▼ and a word.
