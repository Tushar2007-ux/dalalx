Dialog on a paper scrim; for order confirmation and surveillance rulings.

```jsx
<Modal kicker="Confirm order" title="Sell 500 RELNC at market?" actions={<Button variant="danger">Sell</Button>} onClose={close}>…</Modal>
```

All colours come from the nearest `[data-edition]`; wrap screens in `<Edition>`. Numbers always use `.dx-num` (IBM Plex Mono, tabular). Buy/sell always pair colour with ▲/▼ and a word.
