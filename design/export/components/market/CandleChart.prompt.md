Engraved candlestick chart with hatched volume, time axis, day high/low, previous close, crosshair readout and interval tabs. Fills its container.

```jsx
<div style={{ height: 360 }}><CandleChart candles={candles} prevClose={2460} interval={iv} onInterval={setIv} /></div>
```

All colours come from the nearest `[data-edition]`. Numbers use `.dx-num`. Buy/sell always pair colour with ▲/▼ and a word.
