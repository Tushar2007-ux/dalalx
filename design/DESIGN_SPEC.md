# DalalX — "The Morning Edition" · Developer Handoff

This spec covers every screen in `ui_kits/` (landing, trading floor v2 with its halt/auction/resume and closed states, X-Ray, Correspondents, Investigations). The source of truth for values is `tokens/*.css`; the reference implementations are `components/**/*.jsx`. Where this document and the CSS disagree, the CSS wins. Please report the mismatch.

> **Location in this repo:** the exported Claude Design files (`tokens/`, `components/`, `ui_kits/`, `assets/`, `styles.css`) live in `design/export/`. Screens are in `design/screens/`, portraits in `design/assets/`.

**Contents:**
1. Principles
2. Tokens (both editions)
3. Typography
4. Grid and breakpoints
5. Components and states
6. Monogram stamps and portraits
7. Motion
8. Print effects in CSS/SVG
9. Accessibility checklist
10. Screen inventory

---

## 1. Principles

- It is a trading tool first. The newspaper is the skin, and it must never reduce legibility of a figure.
- **Never colour alone.** Every buy or up state carries ▲ plus a word or the letter B. Every sell or down state carries ▼ plus a word or S. Every status carries a glyph plus a label.
- **Flat print.** No colour gradients, no glows, no drop shadows, no blur, no rounded corners. Rubber stamps (3px) and monograms (circle) are the only exceptions.
- **Every number is mono.** IBM Plex Mono with `tnum lnum zero`, so digits never shift width while prices tick.
- **Two editions.** Every token resolves from the nearest `[data-edition]` ancestor. Components never hard-code colour.

---

## 2. Tokens

Load order: `styles.css` imports `typography → colors → colors-night-alt → layout → motion → print`.

### 2.1 Colour — Day and Night

Contrast ratios are measured against `--paper` unless noted. "Sunk" means `--surface-sunk`.

| Token | Day | Night | Use | Day contrast | Night contrast |
|---|---|---|---|---|---|
| `--paper` | `#F7DCCB` | `#141110` | page | — | — |
| `--surface` | `#FBEAE0` | `#1C1816` | slips, inputs, toasts, modal, selected row | — | — |
| `--surface-sunk` | `#F3D3C0` | `#241F1C` | hover rows, input affixes, active tab hover | — | — |
| `--rule` | `#E2C2B0` | `#3A322D` | hairline rules, row separators, grid lines | graphic | graphic |
| `--rule-strong` | `#1A1614` | `#F1E4DA` | panel heads, double rules, borders of controls | 13.7 | 14.8 |
| `--ink` | `#1A1614` | `#F1E4DA` | body text, figures | 13.7 · surface 15.4 · sunk 12.1 | 14.8 |
| `--ink-muted` | `#665449` | `#A8968A` | meta, captions, axis labels | 5.5 · sunk 4.8 | 6.6 |
| `--ink-inverse` | `#F7DCCB` | `#141110` | text on solid ink | 13.7 | 14.8 |
| `--accent` | `#B3261E` | `#F2B8A0` | kickers, modal kicker | 5.0 | 10.8 |
| `--buy` | `#1E6B3F` | `#7FC79A` | buy/bid/up figures, buy fills | 5.0 · surface 5.6 · sunk 4.6 | 9.4 |
| `--sell` | `#B3261E` | `#F08A7E` | sell/ask/down figures | 5.0 · surface 5.6 · sunk 4.6 | 7.1 |
| `--on-buy` / `--on-sell` | `#FBEAE0` | `#141110` | text on solid buy/sell | ≥5.0 | ≥7.1 |
| `--buy-wash` | `rgba(30,107,63,.12)` | `rgba(127,199,154,.14)` | ink-in tint, buy hover | — | — |
| `--sell-wash` | `rgba(179,38,30,.10)` | `rgba(240,138,126,.14)` | ink-in tint, sell hover | — | — |
| `--stamp` | `#B3261E` | `#F08A7E` | rubber stamps, STOP PRESS, halted | 5.0 | 7.1 |
| `--pencil` | `#C2410C` | `#FDBA74` | red-pencil marks (graphics only on Day) | 4.0 (graphic ≥3) | 11.2 |
| `--pencil-text` | `#A8360A` | `#FDBA74` | pencil notes, input errors, flagged text | 5.0 | 11.2 |
| `--scrim` | `rgba(247,220,203,.88)` | `rgba(20,17,16,.86)` | modal backdrop | — | — |
| `--focus` | `#1A1614` | `#F2B8A0` | focus ring | — | — |

Edition-only switches:

| Token | Day | Night |
|---|---|---|
| `--portrait-day` | `block` | `none` |
| `--portrait-night` | `none` | `block` |
| `--portrait-blend` | `multiply` | `screen` |
| `--grain-opacity` | `0.07` | `0.06` |
| `--grain` | SVG noise, warm-dark | SVG noise, warm-light |
| `color-scheme` | `light` | `dark` |

**Night candidates (pending choice).** These live in `tokens/colors-night-alt.css` on `[data-edition="midnight" | "charcoal" | "pink-night"]` and use the same token names. Charcoal Press lifts sell to `#EB6064`, because the brief's `#E5484D` is 4.45:1 on its surface. Once one is chosen, rename its selector to `[data-edition="night"]` and delete the rest.

### 2.2 Spacing and sizing (`tokens/layout.css`)

| Token | Value |
|---|---|
| `--space-1` … `--space-7` | 4 · 8 · 12 · 16 · 24 · 32 · 48 px |
| `--gutter` | 20px (the column rule sits in the middle) |
| `--column-min` | 240px |
| `--row-book` | 24px (X-Ray enlarged book: 36px) |
| `--row-tape` | 22px |
| `--control-h` / `--control-h-sm` | 36px / 28px. Side toggle 40px; lg button 44px. |
| `--radius` | 0 |
| `--radius-stamp` | 3px |
| `--avatar-head-max` | 47px (below 48px, show a monogram or head crop, never a full portrait) |

### 2.3 Rules

| Rule | CSS | Where |
|---|---|---|
| Hairline | `1px solid var(--rule)` | row separators, vertical column rules, grid lines |
| Dotted | `1px dotted var(--rule)` | tape rows |
| Bold | `2px solid var(--rule-strong)` | top of every section label, card tops |
| Thin double | `3px double var(--rule-strong)` | under section labels, folio, footer |
| Double | `4px double var(--rule-strong)` | under the masthead and dateline, modal top |
| Pencil limit | `2px dashed var(--pencil)` | X-Ray limit line |

---

## 3. Typography

**Fonts.** Google Fonts: Fraunces (variable: opsz 9–144, wght 300–900), IBM Plex Sans (400/500/600/700), IBM Plex Mono (400/500/600). In production, self-host WOFF2 subsets (Latin + ₹ U+20B9 + ▲▼●○■◆◐✎✂§¶✓✕①②③).

- `--font-display`: `"Fraunces", "Times New Roman", serif`
- `--font-ui`: `"IBM Plex Sans", system-ui, sans-serif`
- `--font-mono`: `"IBM Plex Mono", ui-monospace, monospace`, always with `font-feature-settings: "tnum","lnum","zero"` (class `.dx-num`)

| Role | Family | Size / line-height | Weight | Other |
|---|---|---|---|---|
| Masthead (landing) | Fraunces | 72 / 72 | 800 | opsz 144, −0.02em |
| Landing lead headline | Fraunces | clamp(48px, 7.4vw, 108px) / 0.95 | 800 | opsz 144, −0.02em, balance |
| Strip wordmark (terminal) | Fraunces | 26 / 26 | 800 | opsz 144 |
| Page title (Investigations, Correspondents) | Fraunces | 40 / 40 | 800 | −0.01em |
| Headline banner | Fraunces | 28 / 31 | 700 | opsz 72, `text-wrap: pretty` |
| Card / story headline | Fraunces | 21–23 / 1.1 | 700 | opsz 48–72 |
| Deck / standfirst | Fraunces italic | 15 / 1.35 (landing standfirst 24 / 1.3) | 400 | muted |
| Pencil notes, empty states | Fraunces italic | 14 / 1.3 | 400 | `--pencil-text` or muted |
| Section label | Plex Sans | 13 / 1 | 700 | uppercase, 0.12em |
| Kicker / field label | Plex Sans | 11 / 1 | 700 | uppercase, 0.14em |
| Body | Plex Sans | 14 / 20 | 400 | |
| Control | Plex Sans | 13 / 1 | 600 | |
| Meta | Plex Sans | 12 / 1.4 | 400 | muted |
| Book, tape figures | Plex Mono | 13 / 24 (tape 12.5 / 22) | 500 (best level 600) | tnum |
| Last price | Plex Mono | 32–40 | 500 | |
| Countdown | Plex Mono | 40 (lg 56, sm 22) / 1 | 500 | −0.02em |
| Axis labels | Plex Mono | 10–10.5 | 400 | muted |

Minimum text size is 10px, used only for axis labels and monogram letters. Interactive text is never below 12px.

---

## 4. Grid and breakpoints

### 4.1 The newspaper column

A **column** is a `RuledPanel`. Its head is a 2px ink rule, then a 13px caps label and optional meta on the right, then a 3px double rule. Columns are separated by a 1px `--rule` vertical line with 12px of padding each side, so the gutter is 24px with the rule in the middle.

**Wrap rule.** Never draw a left rule by array index. When columns wrap, the first item on a new row gets a stray rule. Use one of these instead:
- a fixed column count per breakpoint, with the rule on `:not(:first-child)` of each row; or
- top rules on every item (used on the landing and storylines).

### 4.2 Terminal (trading floor v2)

The page has a 24px side margin. Vertical order: strip masthead → headline banner → (halt or auction band) → stock tabs → columns → folio.

| Breakpoint | Layout |
|---|---|
| **≥ 1440px** (design width) | One row of four columns: **Markets** 0.8fr (min 230) · **Chart + Tape** 1.65fr (min 440) · **Order Book** 1fr (min 300) · **Ticket + Surveillance** 1fr (min 300). |
| **900–1439px** | Two rows. Row 1: Chart + Tape (span 2) and Order Book. Row 2: Ticket + Surveillance, then Markets. The vertical rule appears only between columns in the same row. |
| **< 900px** | Single column in task order: Headline → Ticket → Order Book → Chart → Tape → Markets/The Wire → Surveillance. Stock tabs scroll horizontally (`overflow-x: auto`, no wrap). The book drops to 5 levels per side. |

Chart height is `flex: 1; min-height: 340px`. The chart fills its column down to the tape.

### 4.3 Other screens

| Screen | ≥ 1440 | 900–1439 | < 900 |
|---|---|---|---|
| Landing | Lead: 3 columns (standfirst+CTAs · portrait · market box); In this edition: 4; Steps: 3 | 2 + 1; 2 × 2; 3 | Everything 1 column; lead headline 48px |
| X-Ray | Book 2.2fr (min 720) · Narration 1fr (min 340) | Book full width, narration below | Book explainer column collapses below each row; narration first |
| Correspondents | Cards `repeat(auto-fill, minmax(380px,1fr))` gives 3; storylines 4 | 2; 2 | 1; 1 |
| Investigations | Feed 1fr (min 320) · Case 2.4fr (min 640) · Rules 1fr (min 290) | Case full width; Feed + Rules below | Feed → Case → Rules |

Max content width: the landing is capped at 1320px and centred. Terminal screens are full-bleed with 24px margins.

---

## 5. Components and states

All components are in `components/`, with `.d.ts` for props and `.prompt.md` for usage. "Hover / Focus / Disabled" follow the global rules unless a component lists its own:
- **Hover:** background `--surface-sunk`, transition 120ms.
- **Pressed:** `translateY(1px)`.
- **Focus:** 2px `--focus` outline, offset 2px.
- **Disabled:** 1px dashed `--ink-muted` border, muted text, `not-allowed` cursor.

| Component | Variants | States |
|---|---|---|
| **Edition** | `day`, `night` (+ candidates) | grain on/off; `data-motion="reduced"` |
| **Masthead** | full (landing) · `compact` · `strip` (terminal) | status: live ● · pre-open ○ · auction ◐ · halted ■ · closed ■ |
| **HeadlineBanner** | tone neutral/up ▲/down ▼ | live (typesets in) · breaking (solid stamp kicker) · stale (muted, pencil timestamp) |
| **SectionLabel** | rule bold/hair/double/none; under double/hair/none | with count, with meta |
| **RuledPanel** | columnRule none/left/right | ready · loading (hatched bars, "Setting type…") · empty (italic) · stale (pencil meta) · error ("Wire down") |
| **StockTabs** | — | default · hover · active (solid ink) · focus · halted (struck symbol, ■ Halted, not selectable) |
| **Button** | primary (solid ink) · secondary (outlined) · danger (solid sell) · ghost (text) · sizes sm 28 / md 36 / lg 44 | default · hover (primary/danger: inset 2px paper ring = double rule; secondary: sunk) · pressed · focus · disabled · loading ("Setting…") · optional kbd hint |
| **OrderTypeToggle** | Limit / Market / IOC (custom options allowed, e.g. "Limit only" in auction, speeds in X-Ray) | selected (solid ink) · hover · focus · disabled; hint line in italic |
| **SideToggle** | ▲ Buy / ▼ Sell | selected (solid buy/sell) · hover (wash) · focus · disabled |
| **PriceInput** | prefix ₹, suffix qty / per share; `align` right (figures) or left (text) | default · focus (inset 1px ink ring) · error (pencil border + ✎ italic message) · disabled · read-only |
| **XRayToggle** | — | off ("See the explainer →") · on (solid label, "Close the explainer ←") · hover underline |
| **PriceCell** | sizes sm/md/lg/xl/number; changeOnly | idle · ink-up · ink-down · halted (struck + ■ HALTED) |
| **OrderBookRow** | side bid/ask; depthStyle track (default) / row | default · best (▲/▼ glyph, 600 weight) · hover · mine (YOU, surface) · flagged (pencil circle on qty) · xray (monogram replaces order count) |
| **TradeTapeRow** | with monograms (buyerBot/sellerBot) or text | fresh (typesets) · mine (surface) · flagged (✎) · tooltip on hover/focus with ▲ Buyer / ▼ Seller names |
| **CandleChart** | intervals 1m · 5m · 15m · 1D | idle · hover crosshair (price tag right, time tag bottom, OHLCV readout) |
| **PenChart** | — | newest segment draws |
| **BreadthBar** | — | advancing / unchanged-halted / declining |
| **Countdown** | sm/md/lg, `note` override | normal · warn ≤60s (pencil) · final ≤10s (stamp, `aria-live=assertive`) · done ("The bell has rung.") |
| **TheWire** | — | newest bold, newest typesets |
| **Folio** | — | — |
| **Portrait** | crop auto/full/head; `plate` (light inset for Night) | Day ink multiply · Night light ink screen |
| **Monogram** | bot codes + YOU | — |
| **BotByline** | card · compact | active ● · idle ○ · flagged ◆ (UNDER INVESTIGATION stamp) · halted ■ (SUSPENDED) · duty ◉ · hover/selected |
| **RubberStamp** | stop-press · under-investigation · filled · custom text; sm/md/lg; `backed` (paper behind) | static · slam (animate, re-slams only when `slamKey` changes) · lift (wrapper `.dx-anim-lift`) |
| **RedPencilCircle** | — | drawn · drawing (`drawKey` replays) |
| **MarginNote** | side right/left/below; tone pencil/ink | — |
| **AlertNotice** | severity info § / notice ¶ / warning ! / critical ‼; `label` override | fresh (slip in) · dismissible |
| **Toast** | filled ✓ · partial ◐ · rejected ✕ · cancelled — · info § | slip in; optional action; close |
| **Modal** | — | open (scrim fade) · Esc and scrim close |

Screen-level state patterns:
- **Session closed:** masthead ■ Market closed. Countdown done. Tape rows muted, with "■ Frozen at 15:30:00" in the label meta. Ticket fully disabled with "Market closed · opens 09:15".
- **Circuit halt:**
  - The terminal wrapper gets `filter: grayscale(1) sepia(.3) contrast(.92)`.
  - The halt band (critical notice, "Next edition in" countdown, "What happens next" list) sits above the filtered area and stays in full colour.
  - A STOP PRESS stamp at scale 2.2 sits over the chart and book (not filtered).
  - Ticket disabled with "Order entry paused".
- **Call auction:**
  - Filter is `grayscale(.8) sepia(.2)` and the stamp is at 55% opacity.
  - Auction band: indicative price (ink-in), indicative volume, and buy/sell interest bar.
  - Ticket is limit-only.
- **Reopen:** the stamp lifts, the filter transitions to `none` over 900ms, the headline reads "Trading resumes", and a toast reports the reopen price.

---

## 6. Monogram stamps and portraits

### 6.1 Monograms (`components/bots/Monogram.jsx`)

Use monograms wherever bot identity is shown below 40px: the tape, X-Ray book rows, alert feed and new hires.

| Code | Bot | Colour |
|---|---|---|
| MU | The Munim (Market Maker) | ink |
| MC | Momentum Chaser | ink |
| PS | Panic Seller | ink |
| WH | The Whale | ink |
| SP | The Spoofer | ink |
| SI | Surveillance Inspector | ink |
| YOU | the user | `--stamp` |

Construction:

```css
.monogram {
  width: 20px; height: 20px; border-radius: 50%; box-sizing: border-box;
  display: inline-grid; place-items: center;
  border: 1.5px solid currentColor;          /* outer ring */
  outline: .75px solid currentColor; outline-offset: 1px;  /* second ring = rubber-stamp double edge */
  transform: rotate(-6deg);                  /* hand-stamped tilt */
  font: 700 8px/1 var(--font-ui); letter-spacing: .02em;  /* size × 0.4; YOU uses × 0.32 */
  background: var(--paper); mix-blend-mode: var(--portrait-blend);
}
```

Sizes used:
- 20px on the tape and in the book.
- 22–24px in the alert feed and X-Ray.
- 28px in the case file header.
- 56px in the hire dialog.

Always give it `role="img"` with `aria-label` set to the full name. The tape pairs two monograms, buyer ← seller. The wrapper is focusable and shows a tooltip on hover or focus with "▲ Buyer {name}" and "▼ Seller {name}".

### 6.2 Portraits

- **Assets:** `assets/portraits/<bot>-day.png` and `-night.png` (560px, full bust), plus `<bot>-head-day.png` and `-head-night.png` (160px tight head crop). Bots: munim, momentum, panic, whale, spoofer (+ spoofer-alt), inspector.
- **Ink-only.** The off-white paper was keyed to transparent. Day files are `#1A1614` ink; Night files are `#F1E4DA` ink, which gives the inverted print. Render both and let `.dx-portrait-day` / `.dx-portrait-night` toggle via tokens, with `mix-blend-mode: var(--portrait-blend)`.
- **Light plate.** `plate` shows the Day file on `#F8EADF` with multiply, for Night contexts that want the original look.
- **Minimum size.** Portraits only appear at 40px or larger (byline compact 40, Correspondents 120, Inspector header 76, landing 150–360). Below that, use a monogram.

Pipeline to regenerate the assets:
1. Crop a 4% inset.
2. Compute luminance per pixel: `alpha = clamp((paperL − 12 − L) / (paperL − 37), 0, 1) ^ 0.85`, with paperL about 239.
3. Fill RGB with the ink colour.
4. Export PNG.

At about 40–120 KB each, they are the only raster images in the system.

---

## 7. Motion

All motion uses print metaphors. It is quick and smooth, never bouncy, and no easing overshoots. Tokens are in `tokens/motion.css`.

### 7.1 Easings

| Token | Curve | Use |
|---|---|---|
| `--ease-out` | `cubic-bezier(.2,.7,.2,1)` | default: arrives and settles |
| `--ease-in-out` | `cubic-bezier(.45,0,.25,1)` | drawing: pen, pencil, stamp lift |
| `--ease-slam` | `cubic-bezier(.55,0,.9,.4)` | stamp: accelerates into the paper, no rebound |
| `steps(14, end)` | — | typesetting reveal |

### 7.2 Durations and behaviour

| Motion | Class / property | Duration | Easing | Detail |
|---|---|---|---|---|
| Price ink-in | `.dx-anim-ink-up` / `-down` | 520ms | ease-out | 0–30%: colour = buy/sell, wash background, `text-shadow: 0 0 1.4px currentColor` (ink spread). 30–100%: back to ink, wash fades. |
| Tape row typesets | `.dx-anim-typeset` | 180ms | steps(14) | `clip-path: inset(0 100% 0 0)` → `inset(0)`, left to right. 40ms stagger between rows. |
| Headline typesets | `.dx-anim-typeset` on a key change | 180ms | steps(14) | Only when the text changes. |
| Chart pen | `.dx-anim-pen` | 360ms | ease-in-out | Only the newest segment: `stroke-dasharray = len; stroke-dashoffset: len → 0`. |
| Depth hatch | `.dx-depth` (width) | 240ms | ease-out | Width only. Never animate height or position. |
| Stamp slam | `.dx-anim-stamp` | 220ms | ease-slam | `scale(1.45) → 1` at a fixed `--stamp-angle`, opacity 0 → 1 by 70%. |
| Stamp lift (reopen) | `.dx-anim-lift` | 520ms | ease-in-out | opacity 1 → 0, `translateY(-24px) scale(1.12)`. Forwards. |
| Red-pencil circle | `.dx-anim-draw` | 480ms | ease-in-out | `pathLength="100"`, `stroke-dashoffset: 100 → 0`. |
| Toast / notice slip | `.dx-anim-slip` | 180ms | ease-out | translateY(−6px) and fade in. |
| Modal | `.dx-anim-fade` | 200ms | ease-out | Scrim and dialog fade. |
| Hover / press | transition | 120ms | ease-out | Background colour only, plus press 1px. |
| Halt filter in / out | `filter` transition | 900ms | ease-out | Greyscale/sepia on the terminal wrapper. |
| X-Ray order slip | transform | 420ms | ease-out | Slides 56px into place at step 1. |

### 7.3 What must NOT animate during high-frequency updates

1. **Coalescing.** A price cell inks at most once every 250ms (`INK_COALESCE_MS`). Faster ticks update the digits silently.
2. **No tweening digits.** No count-ups and no number tweens. The number swaps instantly; only colour and spread animate.
3. **No layout motion.** Book rows never slide, reorder or animate height. Columns never resize. Tabular figures keep widths fixed.
4. **Only the newest tape row typesets.** If more than about 8 trades arrive per second, insert the batch without animation.
5. **Chart.** Never redraw the whole line or candles with animation. Axis rescales, new candles and interval switches are instant. Only the newest pen segment draws.
6. **Event-only marks.** Stamps, pencil circles, toasts and notices fire on discrete events (fill, flag, halt), never on a tick. `slamKey` and `drawKey` must stay stable across re-renders.
7. **Countdown digits** change instantly every second.
8. **Depth bars** may transition width, but must skip the transition when a level is inserted or removed, to avoid a sweep across the book.

### 7.4 Reduced motion

Two triggers: the `prefers-reduced-motion: reduce` media query, or `data-motion="reduced"` on any ancestor (an in-app setting). Both:
- remove every `dx-anim-*` animation and the `.dx-depth` transition;
- force `stroke-dashoffset: 0` on pen and pencil paths (fully drawn);
- force `.dx-anim-lift` to `opacity: 0` (stamp simply disappears);
- remove the halt filter transition (instant sepia on and off).

Final states are identical. Only the journey is removed:
- Prices change with no tint.
- Rows appear whole.
- Stamps appear already at their angle.
- Pencil loops appear already drawn.

---

## 8. Print effects in CSS/SVG (no heavy images)

### 8.1 Paper grain

The grain is a tiny inline SVG fractal-noise tile used as a background, on a `::before` layer so it never affects text contrast.

```css
.dx-paper { position: relative; isolation: isolate; background: var(--paper); }
.dx-paper::before {
  content: ""; position: absolute; inset: 0; z-index: -1; pointer-events: none;
  background-image: var(--grain); opacity: var(--grain-opacity);   /* 0.07 day · 0.06 night */
}
/* --grain (Day). Night swaps the feColorMatrix to light values (.95 .9 .85). */
--grain: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='180' height='180'>\
<filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2' stitchTiles='stitch'/>\
<feColorMatrix values='0 0 0 0 .1  0 0 0 0 .08  0 0 0 0 .07  0 0 0 .9 0'/></filter>\
<rect width='100%' height='100%' filter='url(%23n)'/></svg>");
```

- **Cost:** about 400 bytes, with no network request. `stitchTiles` makes the 180px tile seamless.
- **Performance:** if repaint cost shows up in profiling on low-end devices, rasterise the tile once to a 180px PNG at build time (about 6 KB).

### 8.2 Halftone / hatched depth bars

The hatching is a repeating linear gradient used as a line pattern, not as a colour fade. Colour comes from `currentColor`, so one class serves bids, asks, volume and loading bars.

```css
.dx-hatch       { background-image: repeating-linear-gradient(-45deg, currentColor 0 1px, transparent 1px 4px); }
.dx-hatch-dense { background-image: repeating-linear-gradient(-45deg, currentColor 0 1.5px, transparent 1.5px 3px); }
.depth-track { height: 14px; position: relative; border-left: 1px solid var(--rule); }   /* bids */
.depth-bar   { position: absolute; inset: 0 auto 0 0; width: calc(var(--depth) * 100%);
               color: var(--buy); border-right: 1.5px solid var(--buy); }               /* solid edge = readable length */
/* Asks mirror it: border-right on the track, bar anchored right, border-left edge, color: var(--sell). */
```

Rules:
- **Own column.** The depth bar lives in its own 96px column after the figures. It never sits behind a number.
- **Scale.** `--depth` is qty divided by the largest visible qty (0–1).
- **Volume bars in SVG charts** use a `<pattern>`: `<pattern id="v" width="3" height="3" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="1" height="3" fill="var(--ink)"/></pattern>`. Fill with `url(#v)` and add a 0.75px ink stroke.
- **Down candles** use the same pattern in sell ink. Up candles are hollow (paper fill, buy stroke), so direction reads by shape as well as colour.
- **Pattern ids.** Give `<pattern>` ids a per-instance suffix to avoid collisions when several charts mount.

### 8.3 Rubber stamps

A stamp is built from borders and type: no images, no SVG.

```css
.stamp {
  display: inline-grid; justify-items: center; gap: 2px;
  padding: 5px 12px;                          /* sm 3/7 · lg 8/18 */
  border: 3px solid var(--stamp);             /* sm 2 · lg 4 */
  outline: 1px solid var(--stamp); outline-offset: 2px;   /* inner + outer ring */
  border-radius: var(--radius-stamp);         /* 3px */
  color: var(--stamp);
  font: 700 15px/1 var(--font-ui); letter-spacing: .14em; text-transform: uppercase;
  transform: rotate(var(--stamp-angle, -6deg));            /* −4° to −9°; FILLED may tilt +5° */
  mix-blend-mode: var(--portrait-blend); opacity: .94;      /* ink pressed into paper */
}
.stamp--backed { background: var(--paper); mix-blend-mode: normal; opacity: 1; }  /* over portraits or dense type */
.stamp__sub { font: 500 .55em var(--font-mono); letter-spacing: .06em; }          /* "Ref. SV-0412", fill details */
```

- **Kinds:** STOP PRESS and UNDER INVESTIGATION (`--stamp`), FILLED ✓ (`--buy`), SUSPENDED, NOW RUNNING, RESTED and custom text.
- **Accessibility:** `role="img"` with an aria-label of the text plus sub-line.
- **Large overlays:** the halt STOP PRESS is the lg stamp at `transform: scale(2.2)` on an absolutely positioned wrapper, placed inside the terminal container (not the page) so it never covers the halt band.
- **Optional texture:** for a worn edge, add `mask-image: var(--grain)` with `mask-size: 180px` at about 85% coverage. This was not applied by default, for legibility.

### 8.4 Red-pencil circles

A single hand-drawn cubic path overshoots its own start, stretched over the target with `preserveAspectRatio="none"`. `vector-effect: non-scaling-stroke` keeps the line weight constant at any size.

```html
<span class="pencil-host" style="position:relative; display:inline-block; padding:3px 14px">  <!-- ≥14px side padding keeps the loop off the digits -->
  9,800
  <svg aria-hidden="true" viewBox="0 0 200 80" preserveAspectRatio="none"
       style="position:absolute; left:-6px; top:-7px; width:calc(100% + 12px); height:calc(100% + 14px); overflow:visible; pointer-events:none">
    <path d="M 22 46 C 16 20, 70 6, 116 8 C 164 10, 196 26, 190 46 C 184 68, 128 76, 86 73
             C 42 70, 6 60, 12 40 C 16 27, 44 15, 74 12"
          pathLength="100" stroke-dasharray="100" stroke-dashoffset="100"
          fill="none" stroke="var(--pencil)" stroke-width="2" stroke-linecap="round"
          vector-effect="non-scaling-stroke" class="dx-anim-draw"/>
  </svg>
</span>
```

- **Drawing:** `pathLength="100"` normalises the dash, so one keyframe (`stroke-dashoffset → 0`) draws any size of loop. Re-key the path to redraw.
- **Inside charts:** compute an ellipse-ish loop from the bounding box of the flagged marks (see `ui_kits/surveillance` `Timeline`). Run dotted leaders (`stroke-dasharray: 2 2`) to notes placed in a dedicated band above the plot, never on the data.
- **Accessibility:** the circle is decorative. Put the meaning in an accompanying label ("Flagged by surveillance") and in text (✎ glyph + margin note).

### 8.5 Clipped notices

- **Torn edge:** `clip-path: polygon(0 3px, 7% 1px, 18% 3px, 31% 0, 46% 2px, 58% 0, 72% 3px, 86% 1px, 100% 2px, 100% 100%, 0 100%)`.
- **Border:** a 1px dashed border (2px solid stamp for critical).
- **Scissors:** a ✂ glyph on a paper-coloured chip at the top-left.

---

## 9. Accessibility checklist

- All text pairs in §2.1 pass WCAG AA at their used size. Day `--pencil` (4.0:1) is for graphics only; small text uses `--pencil-text`.
- Direction is never colour alone: ▲/▼ glyphs plus words, hollow vs hatched candles, B/S on the tape.
- Toggles use `role="radiogroup"`/`radio` with arrow-key navigation. Rule switches and X-Ray use `role="switch"`. Stock tabs use `role="tablist"`.
- Live regions:
  - The headline banner is `role="status"` with `aria-live="polite"`.
  - The final countdown is `aria-live="assertive"`.
  - Critical and warning notices are `role="alert"`.
- Tape monogram pairs are focusable and show their tooltip on focus as well as hover.
- The modal traps context with `aria-modal`, and Esc closes it.
- The focus ring is always visible (`:focus-visible`, 2px `--focus`, offset 2px).

---

## 10. Screen inventory

| Screen | File | URL params |
|---|---|---|
| Landing (front page) | `ui_kits/landing/index.html` | `edition` |
| Trading floor v2 | `ui_kits/trading-floor-v2/index.html` | `edition`, `session=closed`, `phase=halt\|auction\|resume` |
| X-Ray explainer | `ui_kits/xray/index.html` | `edition`, `step=0–6` |
| Our Correspondents | `ui_kits/correspondents/index.html` | `edition`, `dialog=hire` |
| Investigations | `ui_kits/surveillance/index.html` | `edition`, `state=suspended` |
| Trading floor v1 (superseded) | `ui_kits/trading-floor/index.html` | `edition` |
| Side-by-side review | `Night Palette Comparison.dc.html` | — |

**Prototype-only code.** `ds-loader.js`, which compiles JSX in the browser, is for these prototypes only. In production, import the `.jsx` components through your bundler and import `styles.css` once at the app root.
