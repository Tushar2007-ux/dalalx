# DalalX Design System — "The Morning Edition"

DalalX is a stock exchange simulator web app. Users trade against six bots on a simulated exchange with paper money. The interface reads like a financial newspaper that is printing itself live: salmon newsprint, ruled columns, a serif masthead, figures set in mono. It is a precise trading tool first; the newspaper is the skin.

**Sources:** a written brief from the product owner (direction, palettes, fonts, component list, motion intent) and six stippled ink portraits of the bots (seven files; the Spoofer has two poses), originals in `uploads/`. No codebase, Figma or logo was provided. The wordmark is set in plain type (Fraunces 800); there is no logo file.

## Index
- `styles.css` — entry point, `@import`s only
- `tokens/` — `typography.css` (Google Fonts + type scale), `colors.css` (Day `:root`/`[data-edition="day"]`, Night `[data-edition="night"]`), `layout.css`, `motion.css` (durations, easings, keyframes, reduced motion), `print.css` (paper grain, hatching, portrait switching)
- `assets/portraits/` — ink-only transparent PNGs: `<bot>-day.png`, `<bot>-night.png` (560px), `<bot>-head-day.png`, `<bot>-head-night.png` (160px head crops). Bots: `munim`, `momentum`, `panic`, `whale`, `spoofer`, `spoofer-alt`, `inspector`
- `components/` — React primitives (`.jsx` + `.d.ts` + `.prompt.md`) and one specimen card per folder
  - `masthead/` Edition, Masthead, HeadlineBanner
  - `layout/` SectionLabel, RuledPanel, StockTabs, TheWire, Folio
  - `controls/` Button, OrderTypeToggle, SideToggle, PriceInput, XRayToggle
  - `market/` PriceCell (+ `formatINR`), OrderBookRow, TradeTapeRow, CandleChart, PenChart, BreadthBar, Countdown
  - `bots/` Portrait (+ `BOTS` roster), BotByline
  - `marks/` RubberStamp, RedPencilCircle, MarginNote
  - `notices/` AlertNotice, Toast, Modal
- `guidelines/` — foundation cards (colours, type, rules, spacing, textures, glyphs, portraits, motion)
- `ui_kits/trading-floor-v2/` — the current terminal (strip masthead, candlesticks, 10-level book); `ui_kits/trading-floor/` is v1
- `tokens/colors-night-alt.css` — candidate Night palettes (`midnight`, `charcoal`, `pink-night`); `Night Palette Comparison.dc.html` compares them
- `ds-loader.js` — dev loader the cards and UI kit use to compile the `.jsx` in the browser
- `SKILL.md` — agent skill entry

**Intentional additions** (not in the brief): `Edition` (sets the edition scope and paper; every screen needs one), `PenChart` (the brief specifies its motion), `MarginNote` split from the red-pencil circle, `formatINR` and `BOTS` helpers.

## Content fundamentals
- Voice of a markets desk: factual, third person, present tense. Headlines state what happened with a number: "RELNC slides 4% as Panic Seller dumps 12,000 shares".
- Bots are written about as correspondents and subjects of stories, by name: "The Munim", "The Whale", "The Spoofer"; role names for the others ("Momentum Chaser", "Panic Seller", "Surveillance Inspector").
- The user is addressed as "you" only in their own orders and tape rows ("YOU", "Bought 120 RELNC at ₹2,481.50").
- Casing: headlines in sentence case. Section labels and kickers in caps with tracking (MARKETS, ORDER BOOK, TRADE TAPE, SURVEILLANCE, STOP PRESS). Buttons in sentence case with the side glyph: "▲ Buy 120 RELNC".
- Money: ₹ with Indian digit grouping (₹12,40,750.00), always two decimals for prices. Times as 24-hour `14:32:07`, tape to the millisecond.
- Empty and error states are written as print copy, in italics: "Nothing has printed in this column yet.", "Setting type…", "The bell has rung."
- No emoji. No exclamation marks. Surveillance notes are short and suspicious: "Nine of ten bids cancelled inside two seconds. Layering?"

## Visual foundations
- **Editions.** Day (light) and Night (dark) are full token sets on `[data-edition]`. Day: paper `#F7DCCB` (financial-daily pink), surface `#FBEAE0`, hover `#F3D3C0`, rules `#E2C2B0`, ink `#1A1614`, muted `#665449` (darkened from `#6E5F55`, which fell to 4.7:1 on the pinker paper), buy `#1E6B3F`, sell/stamp `#B3261E`, red pencil `#C2410C`. Night: paper `#141110`, surface `#1C1816`, rules `#3A322D`, ink `#F1E4DA`, muted `#A8968A`, accent `#F2B8A0`, buy `#7FC79A`, sell/stamp `#F08A7E`, pencil `#FDBA74`.
- **Contrast.** All text passes WCAG AA on paper and surface. Day pencil `#C2410C` is 4.0:1 on the pink paper, so it is used for marks (circles, wavy underlines, borders) and `--pencil-text: #A8360A` (5.0:1) is used for small pencil text. Buy, sell and pencil text stay ≥4.6:1 on the hover surface. Solid buttons put `--ink-inverse` / `--on-buy` / `--on-sell` on fills, all ≥5:1.
- **Never colour alone.** Buy/up is always ▲ plus a word or the letter B; sell/down is ▼ plus a word or S. Statuses pair a glyph with a label (● Trading, ■ Halted, ◆ Flagged).
- **Type.** Fraunces for the masthead (800, opsz 144), headlines (700, 28px), bylines and italic decks. IBM Plex Sans for every control and label. IBM Plex Mono with `tnum lnum zero` for every number, so digits never shift width while prices tick. Fonts load from Google Fonts.
- **Masthead.** The terminal uses `<Masthead strip>`: one slim line with a 26px wordmark, dateline, status, edition and toggle. The full nameplate is for the landing page only.
- **Layout.** Newspaper grid: columns separated by 1px `--rule` vertical lines centred in a 20px gutter. Each panel starts with a 2px ink rule, a 13px caps section label and a thin double rule beneath. Screens end with a folio line ("Page 1 · Markets · DalalX"). The dateline sits between a 1px rule and a 4px double rule. Book rows are 24px, tape rows 22px.
- **Surfaces.** Flat paper. Slips (toasts, notices, inputs) use `--surface` with a 1px ink or rule border. Hover is `--surface-sunk`. Corners are square everywhere; only rubber stamps get a 3px radius.
- **Charts.** Engraved candlesticks: up candles hollow in buy ink, down candles hatched in sell ink (shape and colour both carry direction), 1px wicks, hatched ink volume, day high/low labels, dotted previous close, crosshair with price and time readout, interval tabs 1m · 5m · 15m · 1D.
- **Order book.** Hatched depth behind every row, proportional to quantity: bids in green ink from the left, asks in red ink from the right, so the book has a shape. Hatch at 32% so figures stay legible; flagged levels get a red-pencil circle.
- **Texture.** SVG fractal-noise grain at 6–7% opacity on `.dx-paper`. Depth bars and loading bars use a 45° hatch (`.dx-hatch`, 1px line every 4px) with a solid edge. No colour gradients, no glows, no drop shadows, no blur. The only shadow-like thing is an inset 2px paper ring on solid buttons on hover, which reads as a double rule.
- **Portraits.** The originals are converted to ink-only PNGs: the off-white paper becomes transparent, so the stipple prints straight onto any paper. Day uses dark ink with `mix-blend-mode: multiply`. Night swaps to light-ink files (the inverted print) with `screen`. `plate` puts the day print on a light inset for night screens that need the original look. Below 48px, `Portrait` switches to the tight head crop with a hairline frame. The generator's sparkle watermark is lighter than the paper, so it dropped out with the background.
- **Stamps and pencil.** Stamps are two-ring borders in caps Plex Sans 700, rotated −4° to −8°, multiplied into the page. `backed` gives them paper behind when they sit over a portrait. Red-pencil loops are a single hand-drawn path that overshoots its start.
- **States.** Hover: sunk surface, underline for ghost and text buttons. Pressed: 1px down. Focus: 2px outline in `--focus`, offset 2px. Disabled: dashed border, muted ink. Loading: "Setting…".

## Motion
Print metaphors, quick and smooth, never bouncy. No easing overshoots.

| Motion | Duration | Easing | Notes |
|---|---|---|---|
| Price ink-in | 520ms | `cubic-bezier(.2,.7,.2,1)` | 0–160ms ink spread (1.4px text bleed) + green/red tint and wash; tint fades back to ink by 520ms |
| Tape row typesets | 180ms | `steps(14)` clip-path reveal, left to right | stagger 40ms per row |
| Chart pen | 360ms | `cubic-bezier(.45,0,.25,1)` | only the newest segment draws; the rest is static |
| Depth hatch | 240ms | ease-out | width only |
| Stamp slam | 220ms | `cubic-bezier(.55,0,.9,.4)` | scale 1.45→1 at a fixed angle, opacity 0→1, no rebound |
| Red-pencil circle | 480ms | `cubic-bezier(.45,0,.25,1)` | stroke-dashoffset 100→0 |
| Hover / press | 120ms | ease-out | colour only |
| Toast slip-in | 180ms | ease-out | 6px drop + fade |
| Modal | 200ms | ease-out | scrim fade |

**Must not animate during high-frequency updates**
- A price cell inks at most once per 250ms; faster ticks update the digits silently (`PriceCell` coalesces).
- Digits never count or tween. The number changes instantly; only the tint and spread animate.
- No layout animation: book rows never slide or reorder, column widths never change, and tabular figures keep widths fixed.
- Only the newest tape row typesets. When trades arrive faster than about 8 per second, insert the batch without animation.
- The chart never redraws the whole line. Axis rescales are instant.
- Stamps, pencil circles and toasts fire on discrete events (fills, flags), never on ticks.
- The countdown changes digits instantly every second.

**Reduced motion** (`prefers-reduced-motion: reduce`, or `data-motion="reduced"` on any ancestor): every `dx-anim-*` animation and the depth transition are removed. Final states are identical: prices change instantly with no tint, rows appear whole, the pen line and pencil loop show fully drawn, stamps appear at their angle without the slam.

## Iconography
No icon font or SVG icon set. DalalX uses Unicode typographic glyphs, set in the surrounding font: ▲ ▼ (buy/up, sell/down), ● ○ (open/trading, idle/pre-open), ■ (halted), ◆ (flagged), ✎ (pencil note, input error), ✂ (clipped notice), § ¶ (market and exchange notices), ✓ ✕ ◐ (filled, rejected, partly filled), · (unchanged). Glyphs are `aria-hidden`; a text label always carries the meaning. No emoji. If a real icon set is needed later, a thin-stroke set such as Lucide at 1.5px would sit closest; none is used today.

## Using the components
Wrap every screen in `<Edition edition="day|night">`. Components read only CSS variables, so the same component renders in both editions. Portrait paths resolve from `window.DALALX_ASSET_BASE` (set by `ds-loader.js`), defaulting to `assets/portraits/`.
