"""Builds docs/Uncross-Project-Report.pdf, the designed project report.

Run:  python3 docs/report/build_pdf.py
Needs Playwright (Chromium) and pypdf. The cover and back cover are printed
full-bleed; the body is printed with running headers, then the contents page
gets its page numbers from a first pass and everything is printed again.
"""
import pathlib, re, subprocess, json, tempfile
from pypdf import PdfReader, PdfWriter

R = pathlib.Path(__file__).resolve().parent
IMG = R / 'img'
FONTS = R / 'fonts'
OUT = R.parent / 'Uncross-Project-Report.pdf'


def img(name):
    return f'file://{IMG / name}'


FONT_CSS = f"""
@font-face {{ font-family: 'LB'; font-weight: 400; src: url('file://{FONTS}/libre-baskerville-latin-400-normal.woff2'); }}
@font-face {{ font-family: 'LB'; font-weight: 400; font-style: italic; src: url('file://{FONTS}/libre-baskerville-latin-400-italic.woff2'); }}
@font-face {{ font-family: 'LB'; font-weight: 700; src: url('file://{FONTS}/libre-baskerville-latin-700-normal.woff2'); }}
@font-face {{ font-family: 'Plex'; font-weight: 400; src: url('file://{FONTS}/ibm-plex-sans-latin-400-normal.woff2'); }}
@font-face {{ font-family: 'Plex'; font-weight: 400; font-style: italic; src: url('file://{FONTS}/ibm-plex-sans-latin-400-italic.woff2'); }}
@font-face {{ font-family: 'Plex'; font-weight: 600; src: url('file://{FONTS}/ibm-plex-sans-latin-600-normal.woff2'); }}
@font-face {{ font-family: 'Mono'; font-weight: 400; src: url('file://{FONTS}/ibm-plex-mono-latin-400-normal.woff2'); }}
:root {{ --ink:#1A1614; --ink2:#3D352F; --muted:#6B5A4E; --red:#B3261E; --green:#1F6B3A;
        --paper:#FBDFBA; --pink:#F7DCCB; --pink2:#FCEEE6; --rule:#E7CFC0; --night:#141110; }}
* {{ box-sizing: border-box; }}
html, body {{ margin: 0; padding: 0; }}
body {{ font: 9.6pt/1.6 'Plex', sans-serif; color: var(--ink); -webkit-print-color-adjust: exact; print-color-adjust: exact; }}
"""

# ---------------------------------------------------------------- covers
COVER = FONT_CSS + """
@page { size: A4; margin: 0; }
.page { width: 210mm; height: 297mm; position: relative; overflow: hidden; page-break-after: always; }
.cover { background: var(--paper); }
.cover .art { position: absolute; right: -62mm; bottom: 0; height: 175mm; }
.cover .band { position: absolute; left: 18mm; right: 18mm; top: 16mm; display: flex; justify-content: space-between;
  border-top: 3px double var(--ink); border-bottom: 1px solid var(--ink); padding: 5px 0;
  font: 7.5pt 'Mono'; letter-spacing: .14em; text-transform: uppercase; color: var(--ink2); }
.cover .title { position: absolute; left: 18mm; top: 40mm; width: 150mm; }
.cover .kick { font: 8pt 'Mono'; letter-spacing: .18em; text-transform: uppercase; color: var(--red); margin-bottom: 4mm; }
.cover h1 { font: 700 76pt/0.9 'LB', serif; letter-spacing: -2.5px; margin: 0; }
.cover .rule { width: 52mm; border-top: 3px solid var(--ink); margin: 7mm 0 6mm; }
.cover .sub { font: italic 15pt/1.45 'LB', serif; color: var(--ink2); margin: 0; width: 118mm; }
.cover .meta { position: absolute; left: 18mm; top: 128mm; width: 92mm; font-size: 8.8pt; }
.cover .meta div { display: flex; gap: 6mm; border-bottom: 1px solid rgba(26,22,20,.22); padding: 2.2mm 0; }
.cover .meta div:first-child { border-top: 2px solid var(--ink); }
.cover .meta span:first-child { width: 26mm; flex: none; font: 7pt/1.9 'Mono'; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); }
.cover .edition { position: absolute; left: 18mm; bottom: 16mm; font: 7.5pt 'Mono'; letter-spacing: .14em; text-transform: uppercase; color: var(--ink2); }
.back { background: var(--night); color: #F1E4DA; }
.back .art { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: 60% 50%; opacity: .55; }
.back .veil { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(20,17,16,.15) 0%, rgba(20,17,16,.92) 62%); }
.back .words { position: absolute; left: 18mm; right: 18mm; bottom: 26mm; }
.back .kick { font: 8pt 'Mono'; letter-spacing: .18em; text-transform: uppercase; color: #E8695E; }
.back h2 { font: 700 30pt/1.1 'LB', serif; margin: 4mm 0 5mm; }
.back p { font: italic 12pt/1.55 'LB', serif; color: #D9C9BC; width: 140mm; margin: 0 0 9mm; }
.back .row { display: flex; gap: 12mm; border-top: 1px solid rgba(241,228,218,.3); padding-top: 5mm; }
.back .row b { display: block; font: 700 20pt 'LB', serif; color: #fff; }
.back .row span { font: 7pt 'Mono'; letter-spacing: .12em; text-transform: uppercase; color: #BBA797; }
"""

COVER_HTML = f"""<!doctype html><html><head><meta charset='utf-8'><style>{COVER}</style></head><body>
<section class='page cover'>
  <img class='art' src='{img("press.jpg")}' alt=''>
  <div class='band'><span>Object-Oriented Modelling</span><span>Project Report</span><span>November 2026</span></div>
  <div class='title'>
    <div class='kick'>A stock exchange, built from objects</div>
    <h1>Uncross</h1>
    <div class='rule'></div>
    <p class='sub'>A multi-stock exchange simulator with a price-time matching engine, designed to demonstrate object-oriented modelling.</p>
  </div>
  <div class='meta'>
    <div><span>Submitted by</span><span>Ritcha<br>Surbhi<br>Tushar Rana (IIT2025165)</span></div>
    <div><span>Course</span><span>Object-Oriented Modelling</span></div>
    <div><span>Institute</span><span>Indian Institute of Information Technology, Allahabad</span></div>
    <div><span>Source</span><span>github.com/Tushar2007-ux/dalalx</span></div>
  </div>
  <div class='edition'>The Morning Edition · Vol. I</div>
</section>
</body></html>"""

BACK_HTML = f"""<!doctype html><html><head><meta charset='utf-8'><style>{COVER}</style></head><body>
<section class='page back'>
  <img class='art' src='{img("theatre.jpg")}' alt=''>
  <div class='veil'></div>
  <div class='words'>
    <div class='kick'>The floor is open</div>
    <h2>Five stocks. Five threads.<br>One fair rule.</h2>
    <p>Every feature in Uncross is an object that plugs into an interface. The engine announces; everything else listens.</p>
    <div class='row'>
      <div><b>654</b><span>Automated tests</span></div>
      <div><b>169,315</b><span>Orders per second</span></div>
      <div><b>11</b><span>Design patterns</span></div>
    </div>
  </div>
</section>
</body></html>"""

# ---------------------------------------------------------------- body
BODY_CSS = FONT_CSS + """
@page { size: A4; margin: 20mm 18mm 20mm 18mm; }
p { margin: 0 0 2.6mm; orphans: 3; widows: 3; }
strong, b { font-weight: 600; }
em { font-style: italic; }
code { font: 8.3pt 'Mono', monospace; background: var(--pink2); color: var(--ink2); padding: .2mm 1mm; border-radius: 1mm; white-space: nowrap; }
a { color: var(--red); text-decoration: none; }
h3 { font: 700 12.5pt/1.3 'LB', serif; margin: 6mm 0 2mm; page-break-after: avoid; }
h4 { font: 600 8pt 'Mono'; letter-spacing: .14em; text-transform: uppercase; color: var(--red); margin: 5mm 0 2mm; page-break-after: avoid; }
ul, ol { margin: 0 0 3mm; padding-left: 5mm; }
li { margin: 1.2mm 0; }
li::marker { color: var(--red); font-weight: 600; }

/* chapter opener */
.chapter { page-break-before: always; }
.band { height: 40mm; margin: 0 0 7mm; border-radius: 1.5mm; overflow: hidden; position: relative; }
.band img { width: 100%; height: 100%; object-fit: cover; display: block; }
.head { page-break-inside: avoid; display: grid; grid-template-columns: auto 1fr; gap: 0 6mm; align-items: end; border-bottom: 2.5px solid var(--ink); padding-bottom: 4mm; margin-bottom: 5mm; }
.head .num { font: 700 46pt/1 'LB', serif; margin-bottom: -1.5mm; color: var(--red); letter-spacing: -2px; grid-row: span 2; }
.head .kick { font: 7.5pt 'Mono'; letter-spacing: .18em; text-transform: uppercase; color: var(--muted); }
.head h2 { font: 700 22pt/1.1 'LB', serif; margin: 1mm 0 0; letter-spacing: -.3px; }
.lede { font: 11.5pt/1.6 'LB', serif; color: var(--ink2); margin: 0 0 5mm; }
.lede::first-letter { float: left; font: 700 34pt/.8 'LB', serif; color: var(--red); padding: 1.6mm 2mm 0 0; }

/* callout */
.callout { background: var(--pink2); border-radius: 1.5mm; padding: 5mm 6mm 5mm 15mm; margin: 5mm 0; position: relative; page-break-inside: avoid; }
.callout::before { content: '\\201C'; position: absolute; left: 4.5mm; top: 1mm; font: 700 34pt 'LB', serif; color: var(--red); }
.callout .lab { font: 7pt 'Mono'; letter-spacing: .16em; text-transform: uppercase; color: var(--red); margin-bottom: 1mm; }
.callout p { font: italic 11pt/1.55 'LB', serif; margin: 0; color: var(--ink); }

/* stats */
.stats { display: grid; grid-template-columns: repeat(4, 1fr); border-top: 2.5px solid var(--ink); border-bottom: 1px solid var(--ink); margin: 5mm 0; page-break-inside: avoid; }
.stats div { padding: 3.5mm 3mm 3mm 0; border-right: 1px solid var(--rule); }
.stats div + div { padding-left: 3.5mm; }
.stats div:last-child { border-right: 0; }
.stats b { display: block; font: 700 19pt/1.05 'LB', serif; letter-spacing: -.5px; }
.stats b.red { color: var(--red); }
.stats span { font: 6.8pt/1.4 'Mono'; letter-spacing: .1em; text-transform: uppercase; color: var(--muted); display: block; margin-top: 1.2mm; }

/* cards */
.cards { display: grid; grid-template-columns: 1fr 1fr; gap: 4mm; margin: 4mm 0; }
.cards.three { grid-template-columns: 1fr 1fr 1fr; gap: 3mm; }
.cards.four { grid-template-columns: repeat(4, 1fr); gap: 3mm; }
.card { background: var(--pink2); border-top: 2.5px solid var(--ink); padding: 3.5mm 4mm 3mm; page-break-inside: avoid; border-radius: 0 0 1.5mm 1.5mm; }
.card h5 { font: 700 10.5pt/1.3 'LB', serif; margin: 0 0 1.2mm; }
.card p { font-size: 8.9pt; line-height: 1.55; color: var(--ink2); margin: 0; }
.card .tag { font: 6.8pt 'Mono'; letter-spacing: .12em; text-transform: uppercase; color: var(--red); display: block; margin-bottom: 1mm; }

/* tables */
table { width: 100%; border-collapse: collapse; margin: 3mm 0 5mm; font-size: 8.7pt; line-height: 1.45; }
thead { display: table-header-group; }
tr { page-break-inside: avoid; }
th { text-align: left; font: 6.8pt 'Mono'; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); border-bottom: 2px solid var(--ink); padding: 2mm 3mm 1.6mm 0; }
td { border-bottom: 1px solid var(--rule); padding: 2.1mm 3mm 2.1mm 0; vertical-align: top; }
tbody tr:nth-child(even) td { background: #FEF8F4; }
td:first-child { font-weight: 600; padding-left: 1.5mm; }
th:first-child { padding-left: 1.5mm; }
td.id { font: 7.5pt 'Mono'; color: var(--red); white-space: nowrap; }
tr.total td { border-top: 2px solid var(--ink); font-weight: 600; background: transparent !important; }

/* figures */
figure { margin: 5mm 0; page-break-inside: avoid; }
figure .frame { border: 1px solid var(--rule); border-radius: 1.5mm; background: #FBEAE0; padding: 3mm; }
figure img { display: block; width: 100%; height: auto; max-height: 120mm; object-fit: contain; }
figure.arch img { max-height: 84mm; }
figure.shot .frame { padding: 0; overflow: hidden; background: #fff; }
figcaption { font-size: 8pt; color: var(--muted); margin-top: 2mm; }
figcaption b { font: 6.8pt 'Mono'; letter-spacing: .14em; text-transform: uppercase; color: var(--red); margin-right: 2mm; font-weight: 400; }
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 4mm; }
.pair figure { margin: 0; }
.pair figure img { max-height: 95mm; object-fit: cover; object-position: top; }

/* order book */
.book { font: 8.6pt 'Mono'; border: 1.5px solid var(--ink); border-radius: 1.5mm; overflow: hidden; background: var(--pink2); }
.book .hd { font-size: 6.8pt; letter-spacing: .14em; padding: 2mm 3mm; border-bottom: 1px solid var(--ink); color: var(--muted); }
.book .r { display: grid; grid-template-columns: 11mm 17mm 11mm 1fr; padding: 1.5mm 3mm; border-bottom: 1px solid var(--rule); }
.book .r:last-child { border-bottom: 0; }
.book .ask .p { color: var(--red); font-weight: 600; } .book .bid .p { color: var(--green); font-weight: 600; }
.book .n { font: 7.6pt 'Plex'; color: var(--muted); }
.book .sp { text-align: center; font-size: 6.8pt; color: var(--muted); padding: 1mm; border-bottom: 1px solid var(--rule); letter-spacing: .1em; }
.book .new { background: var(--ink); color: #fff; } .book .new .n { color: #E2C2B0; }
.book .hit { background: rgba(179,38,30,.12); } .book .hit .n { color: var(--red); font-weight: 600; }
.book .rest { background: rgba(31,107,58,.13); } .book .rest .n { color: var(--green); font-weight: 600; }
.split { display: grid; grid-template-columns: 1fr 1fr; gap: 6mm; align-items: start; margin: 4mm 0; }
.steps { list-style: none; padding: 0; counter-reset: s; margin: 0; }
.steps li { counter-increment: s; position: relative; padding-left: 9mm; margin: 0 0 3mm; }
.steps li::before { content: counter(s); position: absolute; left: 0; top: .3mm; width: 5.6mm; height: 5.6mm; border-radius: 50%; background: var(--ink); color: #fff; font: 600 7.5pt/5.6mm 'Mono'; text-align: center; }

/* bars */
.bars { margin: 4mm 0 5mm; page-break-inside: avoid; }
.bars .row { display: grid; grid-template-columns: 34mm 1fr 26mm; align-items: center; gap: 3mm; margin: 2mm 0; font-size: 8.5pt; }
.bars .track { height: 6mm; background: var(--pink2); border-radius: 1mm; overflow: hidden; }
.bars .fill { height: 100%; background: var(--ink); border-radius: 1mm; }
.bars .fill.red { background: var(--red); }
.bars .v { font: 600 9pt 'Mono'; text-align: right; }

/* contents + abstract */
.toc { page-break-after: always; }
.toc .head { margin-bottom: 7mm; }
.toc ol { list-style: none; padding: 0; margin: 0; columns: 1; }
.toc li { display: grid; grid-template-columns: 11mm 1fr auto; align-items: baseline; border-bottom: 1px solid var(--rule); padding: 2.4mm 0; margin: 0; }
.toc li .n { font: 700 12pt 'LB', serif; color: var(--red); }
.toc li .t { font: 11pt 'LB', serif; }
.toc li .pg { font: 8pt 'Mono'; color: var(--muted); }
.abstract { margin-top: 9mm; display: grid; grid-template-columns: 38mm 1fr; gap: 6mm; }
.abstract .lab { font: 7.5pt 'Mono'; letter-spacing: .18em; text-transform: uppercase; color: var(--red); border-top: 2.5px solid var(--ink); padding-top: 2mm; }
.abstract p { font: 10.5pt/1.65 'LB', serif; color: var(--ink2); border-top: 1px solid var(--ink); padding-top: 2mm; margin: 0; }
.refs li { margin: 2mm 0; }
"""


def chapter(num, kicker, title, lede, inner, band=None, pos='center'):
    band_html = f"<div class='band'><img src='{img(band)}' style='object-position:{pos}' alt=''></div>" if band else ''
    return f"""<section class='chapter' id='ch{num}'>{band_html}
<div class='head'><span class='num'>{num:02d}</span><span class='kick'>{kicker}</span><h2>{title}</h2></div>
<p class='lede'>{lede}</p>
{inner}
</section>"""


def fig(n, src, cap, cls=''):
    return f"<figure class='{cls}'><div class='frame'><img src='{img(src)}' alt='{cap}'></div><figcaption><b>Figure {n}</b>{cap}</figcaption></figure>"


def table(heads, rows, ids=False, total=False):
    th = ''.join(f'<th>{h}</th>' for h in heads)
    body = ''
    for i, r in enumerate(rows):
        cls = " class='total'" if total and i == len(rows) - 1 else ''
        cells = ''.join((f"<td class='id'>{c}</td>" if ids and j == 0 else f'<td>{c}</td>') for j, c in enumerate(r))
        body += f'<tr{cls}>{cells}</tr>'
    return f'<table><thead><tr>{th}</tr></thead><tbody>{body}</tbody></table>'


C = lambda s: f'<code>{s}</code>'

CHAPTERS = [
 chapter(1, 'Chapter one', 'Introduction',
  'The project builds a working stock exchange from scratch and uses it to show object-oriented modelling in practice: every feature is a class that plugs into an interface, and the engine rarely changes when a feature is added.',
  f"""
<h4>The problem</h4>
<p>An exchange receives buy and sell orders from many people at once and must decide, fairly and quickly, which buyer trades with which seller and at what price. It must also stop runaway prices, catch manipulation, and never lose an order.</p>
<h4>Objectives</h4>
<div class='cards three'>
 <div class='card'><span class='tag'>Objective 1</span><h5>A fair engine</h5><p>Price-time priority, with money held exactly in paise.</p></div>
 <div class='card'><span class='tag'>Objective 2</span><h5>Clear OOP</h5><p>Named patterns, SOLID principles, code a student can defend.</p></div>
 <div class='card'><span class='tag'>Objective 3</span><h5>Safe parallelism</h5><p>Stocks in parallel, with no locks inside the order book.</p></div>
 <div class='card'><span class='tag'>Objective 4</span><h5>A real market's systems</h5><p>Breaker, auction, surveillance, risk checks and recovery.</p></div>
 <div class='card'><span class='tag'>Objective 5</span><h5>A visible engine</h5><p>A website where you can watch an order being matched.</p></div>
 <div class='card'><span class='tag'>Scope</span><h5>Five stocks, pretend money</h5><p>RELIANCE, TCS, INFY, HDFCBANK, TATAMOTORS (up to 12); simulated prices and news.</p></div>
</div>
<p>It is a teaching system, not connected to any real market.</p>
<div class='callout'><div class='lab'>The idea in one sentence</div><p>An exchange is a matchmaker for buyers and sellers. Uncross is one built from scratch in Java, with a website that lets you watch it think.</p></div>
""", 'counter.jpg', '50% 40%'),

 chapter(2, 'Chapter two', 'How an exchange matches orders',
  'An exchange keeps one order book per stock: buyers&rsquo; bids sorted from the highest price down, sellers&rsquo; asks from the lowest price up. While the best bid is below the best ask, nothing trades; the gap between them is the spread.',
  f"""
<div class='split'>
 <div>
  <p>When a new order arrives, the engine applies three rules, in this order:</p>
  <ol class='steps'>
   <li><b>Best price first.</b> A bid at ₹102.50 is filled before a bid at ₹102.40.</li>
   <li><b>Then earliest first.</b> At the same price, the order that arrived first is filled first.</li>
   <li><b>The resting order sets the price.</b> A trade happens at the price of the order that was already waiting.</li>
  </ol>
  <p>This is <b>price-time priority</b>, the rule used by NSE, BSE and NYSE. An order that is only partly filled leaves its remainder resting in the book: a <b>partial fill</b>.</p>
 </div>
 <div>
  <div class='book'>
   <div class='hd'>ORDER BOOK · RELIANCE</div>
   <div class='r new'><span>BUY</span><span class='p'>102.50</span><span>500</span><span class='n'>a new order arrives</span></div>
   <div class='r ask'><span>ASK</span><span class='p'>102.60</span><span>300</span><span class='n'>too dear: untouched</span></div>
   <div class='r ask hit'><span>ASK</span><span class='p'>102.50</span><span>200</span><span class='n'>trades 200 at 102.50</span></div>
   <div class='sp'>— THE SPREAD —</div>
   <div class='r bid rest'><span>BID</span><span class='p'>102.50</span><span>300</span><span class='n'>the rest waits: new best bid</span></div>
   <div class='r bid'><span>BID</span><span class='p'>102.40</span><span>400</span><span class='n'>resting</span></div>
  </div>
  <figcaption style='margin-top:2mm'><b>Figure 1</b>One buy order for 500 meets the book: 200 trade, 300 rest.</figcaption>
 </div>
</div>
<h4>The five order types</h4>
{table(['Order type', 'Behaviour'], [
 ['Limit', 'Trades at its price or better; the rest waits in the book'],
 ['Market', 'Trades immediately at the best available prices; never waits'],
 ['IOC (Immediate or Cancel)', 'Trades what it can now at its price; the rest is cancelled'],
 ['Stop-loss', 'Hidden until the last price reaches its trigger, then trades as a market order'],
 ['Iceberg', 'Shows one slice at a time; each new slice joins the back of the queue']])}
<div class='callout'><div class='lab'>Money is never a double</div><p>₹102.50 is stored as the whole number 10250 paise. Decimals cannot hold 0.1 exactly, and on an exchange tiny errors add up to real money.</p></div>
"""),

 chapter(3, 'Chapter three', 'Requirements',
  'Eight functional requirements say what the exchange does; six non-functional ones say how it must be built. Each is met in the code and covered by tests.',
  f"""
<h4>Functional</h4>
{table(['ID', 'Requirement'], [
 ['F1', 'Accept limit, market, IOC, stop-loss and iceberg orders; cancel and amend resting orders'],
 ['F2', 'Match by price-time priority and publish every trade and book change'],
 ['F3', 'Halt a stock that moves 10% within 60 seconds, then reopen it through a call auction'],
 ['F4', 'Run trading bots with different behaviours, including user-built rule bots and a timed tournament'],
 ['F5', 'Detect spoofing, layering, wash trades, pumping and front-running, and open cases with evidence'],
 ['F6', 'Hold accounts (name and PIN) with ₹10,00,000 starting cash; refuse orders beyond free cash or owned shares'],
 ['F7', 'Journal every command and rebuild the market after a restart; replay a past session (Rewind)'],
 ['F8', 'Show the market live in a browser, and let an audience trade from phones']], ids=True)}
<h4>Non-functional</h4>
{table(['ID', 'Requirement'], [
 ['N1', f'Money is a {C("long")} in paise; {C("double")} is never used for prices'],
 ['N2', 'The core engine has no framework dependency (no Spring)'],
 ['N3', 'Each order book is touched by exactly one thread; no locks inside the book'],
 ['N4', 'New order types, rules and bots are added without editing existing classes (Open/Closed)'],
 ['N5', 'Every class carries a Javadoc paragraph naming its purpose and pattern'],
 ['N6', 'Every feature is covered by JUnit 5 tests before it is accepted']], ids=True)}
"""),

 chapter(4, 'Chapter four', 'System architecture',
  'The system is a Maven multi-module project with three modules, and every dependency points inward, towards the engine. The engine knows nothing about the web.',
  f"""
{fig(2, 'architecture.png', 'Module structure: three Maven modules, dependencies pointing inward.', 'arch')}
<div class='cards four'>
 <div class='card'><span class='tag'>core</span><h5>Pure Java 21</h5><p>All business logic in 16 packages. No Spring import anywhere, so it builds and tests in seconds without a server.</p></div>
 <div class='card'><span class='tag'>server</span><h5>Spring Boot 3, adapters only</h5><p>REST controllers take commands in; WebSocket (STOMP) pushes events out. No business logic.</p></div>
 <div class='card'><span class='tag'>frontend</span><h5>React and TypeScript</h5><p>The newspaper pages. It only draws what the engine has already decided.</p></div>
 <div class='card'><span class='tag'>Why it matters</span><h5>Dependency Inversion</h5><p>The web layer could be replaced by a desktop UI without changing a line of the engine.</p></div>
</div>
""", 'layers.jpg', '50% 55%'),

 chapter(5, 'Chapter five', 'The object model',
  'The class diagrams below are generated from the real classes: every name on them exists in the code and can be opened if asked.',
  f"""
<h3>5.1 Matching</h3>
<p>{C('SymbolEngine')} owns one {C('OrderBook')} and a {C('MatchingStrategies')} registry (composition). The registry holds one {C('MatchingStrategy')} per order type. {C('AbstractOrderMatcher')} implements the shared matching loop once (Template Method), and its five subclasses supply only what differs. The book keeps bids and asks as sorted maps of {C('PriceLevel')}s, each a first-in, first-out queue of {C('Order')}s: price ordering comes from the map, time ordering from the queue.</p>
{fig(3, 'matching.png', 'Class diagram: matching. Filled diamonds are composition; hollow triangles are inheritance.')}
<h3>5.2 Events and market state</h3>
<p>The {C('EventBus')} publishes every {C('EngineEvent')} to any number of {C('EngineListener')}s (Observer); surveillance, the risk desk, headlines and the day&rsquo;s tape are all listeners. {C('TradingSession')} holds one {C('MarketState')} (State): Open, Halted or Auction, each deciding what an order may do.</p>
{fig(4, 'observer.png', 'Class diagram: Observer. The engine announces; every feature is just another listener.')}
{fig(5, 'state.png', 'Class diagram: State. The circuit breaker moves the session between three state objects.')}
<h3>5.3 Bots</h3>
<p>Every bot implements {C('TradingBot')}; most extend {C('AbstractBot')}, which holds the shared code for placing and withdrawing orders. A bot sees the market only through a {C('BotContext')} holding a {C('MarketView')} and an {C('OrderGateway')}: two narrow interfaces.</p>
{fig(6, 'bots.png', 'Class diagram: bots. Hiring a new bot is one new subclass and nothing else.')}
"""),

 chapter(6, 'Chapter six', 'OOP principles and design patterns',
  'Each principle and pattern below exists in the code and is named in the Javadoc of the class that uses it. None was added to be named in a report; each is there because removing it would mean editing existing code to add the next feature.',
  f"""
<h4>The four pillars</h4>
<div class='cards'>
 <div class='card'><span class='tag'>Encapsulation</span><h5>Objects enforce their own rules</h5><p>{C('Order.fill')} refuses to overfill; there is no way to grow an order in place; an {C('Account')} cannot spend a rupee twice.</p></div>
 <div class='card'><span class='tag'>Abstraction</span><h5>What, not how</h5><p>Bots see a {C('MarketView')} and an {C('OrderGateway')}, never the {C('Exchange')} behind them.</p></div>
 <div class='card'><span class='tag'>Inheritance</span><h5>Shared code, written once</h5><p>{C('AbstractOrderMatcher')} and {C('AbstractBot')}: five matchers and eight bots supply only what differs.</p></div>
 <div class='card'><span class='tag'>Polymorphism</span><h5>One call, five behaviours</h5><p>{C('strategies.forType(type).match(order, book)')} runs the right matcher with no if-chain.</p></div>
</div>
<h4>SOLID and the design patterns</h4>
{table(['Principle or pattern', 'Where it lives', 'What it achieves'], [
 ['Single Responsibility', f"{C('StopBook')} and {C('StopLossOrderMatcher')}", 'One class decides when a stop fires, another how it trades'],
 ['Open/Closed', C('MatchingStrategies.standard'), 'Stop-loss and iceberg added as two classes and two lines; no matcher edited'],
 ['Liskov Substitution', 'any matcher, bot or market state', 'Any child works wherever its parent type is expected'],
 ['Interface Segregation', f"{C('MarketView')} + {C('OrderGateway')}", 'Bots get two small interfaces instead of the whole exchange'],
 ['Dependency Inversion', f"no Spring in {C('core')}; injected {C('Clock')}", 'The web is a detail plugged in from outside; time can be replayed'],
 ['Strategy', 'matchers, bots, order gates', 'Swap behaviour at run time'],
 ['Template Method', C('AbstractOrderMatcher.match'), f"A {C('final')} algorithm with abstract hooks"],
 ['Factory', C('OrderFactory'), 'Every order is created valid, with id, sequence and timestamp'],
 ['Observer', f"{C('EventBus')}, {C('EngineListener')}", 'The engine announces; surveillance, risk, charts and headlines listen'],
 ['State', f"{C('MarketState')}", 'Open, Halted and Auction as objects, so no code asks &ldquo;are we halted?&rdquo;'],
 ['Command', f"{C('Command')}, the journal", 'Requests become objects that are queued, logged and replayed'],
 ['Memento', 'X-Ray snapshots', 'The book&rsquo;s state at each matching step, kept for replay'],
 ['Singleton · Null Object · Composite', f"{C('OpenState.INSTANCE')}, {C('OrderGate.OPEN')}, {C('OrderGate.allOf')}", 'One stateless instance; a do-nothing gate instead of null; many gates acting as one'],
 ['Adapter · Facade', f"{C('ExchangeGateway')}, controllers; {C('Exchange')}", 'Translate one interface to another; one front door to the subsystem']])}
<div class='callout'><div class='lab'>Open/Closed, proven</div><p>When stop-loss and iceberg orders were added, not one existing matcher was edited: two new classes and two lines in a registry.</p></div>
<p>Value objects such as {C('Trade')}, {C('OrderRequest')} and every event are immutable Java {C('record')}s, safe to share between threads. {C('EngineEvent')} is a {C('sealed')} interface, so the compiler knows every kind of event.</p>
"""),

 chapter(7, 'Chapter seven', 'Concurrency design',
  'Each stock has exactly one worker thread, and that thread is the only code that ever touches its order book. The book is deliberately not thread-safe, because it never needs to be: this is thread confinement.',
  f"""
<div class='cards four'>
 <div class='card'><span class='tag'>Producer–consumer</span><h5>One queue per stock</h5><p>Web requests, bots and phones put {C('Command')} objects on the worker&rsquo;s {C('BlockingQueue')}; the worker takes them one at a time.</p></div>
 <div class='card'><span class='tag'>Results</span><h5>A promise back</h5><p>The caller holds a {C('CompletableFuture')} that the worker completes once the order is matched.</p></div>
 <div class='card'><span class='tag'>Back-pressure</span><h5>Slow down, never fall over</h5><p>When a queue is full, a sender waits up to one second instead of overloading memory.</p></div>
 <div class='card'><span class='tag'>No shared books</span><h5>No locks between stocks</h5><p>An order is always for one stock; what crosses threads is an immutable record.</p></div>
</div>
{fig(7, 'seq.png', 'Sequence diagram: one order from the browser to the trade tape. The web thread never touches a book.')}
<div class='callout'><div class='lab'>The bank-counter picture</div><p>Five counters, one clerk each. Only the clerk writes in the counter&rsquo;s ledger; customers drop their slips in a tray, and the clerk handles them in order.</p></div>
""", 'hall.jpg', '50% 50%'),

 chapter(8, 'Chapter eight', 'Features',
  'Five order types share one matching loop, and every system around them (the breaker, the bots, surveillance, accounts and the journal) is a separate object that plugs into the engine.',
  f"""
<h3>Circuit breaker and call auction</h3>
<p>A move of 10% within 60 seconds halts the stock for 30 seconds. A 30-second call auction then collects orders and reopens at the one price that trades the most shares, so arriving first stops mattering at the reopen.</p>
{fig(8, 'st.png', 'State diagram: the circuit breaker.')}
<div class='cards'>
 <div class='card'><span class='tag'>Order types · amend</span><h5>Keep your place, or lose it</h5><p>Reducing quantity at the same price keeps queue position; a new price or a larger quantity is a cancel and re-entry at the back.</p></div>
 <div class='card'><span class='tag'>Bots</span><h5>A market that never sleeps</h5><p>Munim (market maker), Momentum, Panic Seller, Whale, Contrarian, Noise and a Spoofer; user-built rule bots and timed tournaments; simulated news every 60–150 s.</p></div>
 <div class='card'><span class='tag'>Surveillance</span><h5>Five rules, one stream</h5><p>Spoofing, layering, wash trades, pumping and front-running each open a case with evidence; a suspension cancels resting orders.</p></div>
 <div class='card'><span class='tag'>Accounts · risk desk</span><h5>No money, no trade</h5><p>₹10,00,000 to start; PINs stored as salted SHA-256; buys beyond free cash and sells beyond owned shares are refused.</p></div>
 <div class='card'><span class='tag'>Journal · Rewind</span><h5>Nothing is lost on a restart</h5><p>One writer thread logs every command; start-up replays 45,039 commands in 2.1 s. Rewind replays a session at up to 600×.</p></div>
 <div class='card'><span class='tag'>X-Ray</span><h5>The engine in slow motion</h5><p>One order&rsquo;s matching replayed step by step with a plain-English narrator, from snapshots of the book.</p></div>
</div>
<div class='pair' style='margin-top:5mm'>
 {fig(9, 'investigations.jpg', 'The Investigations page: cases with their evidence.', 'shot')}
 {fig(10, 'rewind.jpg', 'Rewind: a saved session played back at 60×.', 'shot')}
</div>
""", 'lever.jpg', '50% 45%'),

 chapter(9, 'Chapter nine', 'User interface',
  'The frontend is styled as a financial newspaper, The Morning Edition, with a light Day edition and a dark Night edition. It only displays what the engine has already decided.',
  f"""
<div class='pair'>
 {fig(11, 'frontpage.jpg', 'Page 1: the Front Page.', 'shot')}
 {fig(12, 'floor.jpg', 'Page 2: the Trading Floor.', 'shot')}
</div>
{table(['Page', 'What it shows'], [
 ['Front Page', 'Headlines written from engine events, prices with sparklines, market status'],
 ['Trading Floor', 'Order book, price chart, order ticket, trade tape, portfolio, halt countdown and auction curves'],
 ['X-Ray', 'One order matched step by step, with a narrator'],
 ['Correspondents', 'The bots: strategies, profit, hiring, the rule-bot builder and tournaments'],
 ['Investigations', 'Surveillance cases with evidence and rulings'],
 ['Engine Room', 'Worker threads, queue depth and the Rush Hour stress test (40,000 commands)'],
 ['Audience', 'QR code to join from a phone, leaderboard and podium'],
 ['How It Works', 'The architecture and patterns explained'],
 ['Account · Rewind · Evening Edition', 'Personal account; replay of a past session; printable end-of-day summary']])}
"""),

 chapter(10, 'Chapter ten', 'Testing and results',
  f'Every step was built tests-first with JUnit 5 and AssertJ, and verified with the full build, the frontend build and lint before it was accepted.',
  f"""
<div class='stats'>
 <div><b>363</b><span>Core engine tests</span></div>
 <div><b>52</b><span>Server tests</span></div>
 <div><b>239</b><span>Frontend tests</span></div>
 <div><b class='red'>654</b><span>Tests in total</span></div>
</div>
<h3>Measured, then fixed</h3>
<p>A benchmark with fixed random seeds and a warm-up round measured the engine. The first run managed 3,485 orders per second. Profiling showed the bottleneck was X-Ray, not matching: to name the main trader at each price, it walked every order in that level&rsquo;s queue. Keeping a running total of shares per trader made it 48× faster, with every test still passing.</p>
<div class='bars'>
 <div class='row'><span>First run</span><div class='track'><div class='fill' style='width:2.1%'></div></div><span class='v'>3,485 /s</span></div>
 <div class='row'><span>After one fix</span><div class='track'><div class='fill red' style='width:100%'></div></div><span class='v'>169,315 /s</span></div>
 <div class='row'><span>End to end</span><div class='track'><div class='fill' style='width:42.7%'></div></div><span class='v'>72,380 /s</span></div>
</div>
{table(['Measurement', 'Result'], [
 ['Engine alone, one thread', '169,315 orders/s · median 4.6 µs · 99th percentile 27 µs per order'],
 ['End to end, through the queues', '72,380 orders/s · median 43 µs · 99th percentile 182 µs'],
 ['Recovery after restart', '45,039 commands in 2.1 s']])}
<div class='callout'><div class='lab'>The lesson</div><p>Measure before optimising. The slow part was the feature that explains the engine, not the engine.</p></div>
""", 'stopwatch.jpg', '50% 50%'),

 chapter(11, 'Chapter eleven', 'Limitations and future work',
  'The design is honest about what it does not do yet. Each limitation below has a clear next step.',
  f"""
{table(['Area', 'Limitation', 'Next step'], [
 ['Durability', 'The journal is written by a background thread, so a crash can lose the last few milliseconds', 'Wait for the disk before confirming an order, as real exchanges do'],
 ['Authentication', 'Accounts use a name and PIN', 'Real authentication and sessions outside the classroom'],
 ['Scale', 'The exchange runs on one machine', 'Partition stocks across machines'],
 ['Order types', 'No good-till-date or other time-in-force options', 'Add them as new matching strategies'],
 ['Surveillance', 'Rules can flag innocent patterns (front-running once flagged the Momentum bot)', 'Keep a person in the loop: a rule only opens a case']])}
"""),

 chapter(12, 'Chapter twelve', 'Conclusion',
  'Uncross shows that a fair, fast matching engine can be built from small, well-named objects.',
  f"""
<p>The strongest evidence of the design is how the project grew: fourteen features were added after the first version, each mostly as new classes plugged into existing interfaces (strategies, listeners, gates and bots), while the engine&rsquo;s core changed little. Thread confinement made concurrency simple enough to explain in one sentence, and the test suite and measurements show the design works.</p>
<div class='stats'>
 <div><b>5</b><span>Stocks, one thread each</span></div>
 <div><b>11</b><span>Design patterns</span></div>
 <div><b>654</b><span>Automated tests</span></div>
 <div><b class='red'>48×</b><span>Faster after one fix</span></div>
</div>
<h3>References</h3>
<ol class='refs'>
 <li>Source code: <a href='https://github.com/Tushar2007-ux/dalalx'>github.com/Tushar2007-ux/dalalx</a></li>
 <li>E. Gamma, R. Helm, R. Johnson, J. Vlissides, <em>Design Patterns: Elements of Reusable Object-Oriented Software</em>, Addison-Wesley, 1994.</li>
 <li>R. C. Martin, <em>Clean Architecture</em>, Prentice Hall, 2017 (SOLID principles).</li>
 <li>B. Goetz et al., <em>Java Concurrency in Practice</em>, Addison-Wesley, 2006 (thread confinement, producer–consumer).</li>
</ol>
"""),
]

TITLES = re.findall(r"<h2>(.*?)</h2>", ''.join(CHAPTERS))


def body_html(pages):
    items = ''.join(f"<li><span class='n'>{i + 1:02d}</span><span class='t'>{t}</span><span class='pg'>{pages[i] if pages else ''}</span></li>" for i, t in enumerate(TITLES))
    toc = f"""<section class='toc'>
<div class='head'><span class='num'>00</span><span class='kick'>Project report</span><h2>Contents</h2></div>
<ol>{items}</ol>
<div class='abstract'><div class='lab'>Abstract</div>
<p>Uncross is a multi-stock exchange simulator with a real price-time matching engine, written in Java 21 for the Object-Oriented Modelling course at IIIT Allahabad. It matches orders for five stocks on five threads and shows every step of the market through a React website styled as a financial newspaper. The design uses named patterns (Strategy, Template Method, Factory, Observer, State, Command) and the SOLID principles, so new order types, rules and bots are added as new classes rather than edits to the engine. It is covered by 654 automated tests and processes 169,315 orders per second on one thread.</p></div>
</section>"""
    chapters = ''.join(CHAPTERS)
    return f"<!doctype html><html><head><meta charset='utf-8'><title>Uncross — Project Report</title><style>{BODY_CSS}</style></head><body>{toc}{chapters}</body></html>"


HEADER = "<div style=\"width:100%;font-family:monospace;font-size:7px;letter-spacing:1.4px;color:#6B5A4E;padding:0 18mm;display:flex;justify-content:space-between;text-transform:uppercase\"><span>Uncross · Project Report</span><span>The Morning Edition</span></div>"
FOOTER = "<div style=\"width:100%;font-family:monospace;font-size:7px;letter-spacing:1.4px;color:#6B5A4E;padding:0 18mm;display:flex;justify-content:space-between\"><span>IIIT ALLAHABAD · OBJECT-ORIENTED MODELLING</span><span style=\"color:#B3261E\" class=\"pageNumber\"></span></div>"


def render(html, path, margins, header=True):
    tmp = pathlib.Path(tempfile.mkdtemp()) / 'page.html'
    tmp.write_text(html)
    opts = {'path': str(path), 'format': 'A4', 'printBackground': True, 'margin': margins}
    if header:
        opts.update(displayHeaderFooter=True, headerTemplate=HEADER, footerTemplate=FOOTER)
    js = f"""const {{chromium}}=require('playwright');(async()=>{{const b=await chromium.launch();const p=await b.newPage();
await p.goto('file://{tmp}');await p.evaluate(()=>document.fonts.ready);await p.waitForFunction(()=>[...document.images].every(i=>i.complete));
await p.waitForTimeout(300);await p.pdf({json.dumps(opts)});await b.close()}})()"""
    subprocess.run(['node', '-e', js], check=True)


def chapter_pages(pdf):
    """Body page (1-based, as the footer numbers it) on which each chapter title first appears."""
    out, reader = [], PdfReader(str(pdf))
    texts = [p.extract_text() or '' for p in reader.pages]
    for i, t in enumerate(TITLES):
        plain = re.sub('<[^>]+>', '', t).replace('&rsquo;', '’')
        key = f'{i + 1:02d}'
        page = next((n for n, tx in enumerate(texts) if n > 0 and plain.split()[0] in tx and key in tx), None)
        out.append(page + 1 if page is not None else '')
    return out


tmpdir = pathlib.Path(tempfile.mkdtemp())
M = {'top': '20mm', 'bottom': '20mm', 'left': '18mm', 'right': '18mm'}
render(COVER_HTML, tmpdir / 'cover.pdf', {'top': '0', 'bottom': '0', 'left': '0', 'right': '0'}, header=False)
render(BACK_HTML, tmpdir / 'back.pdf', {'top': '0', 'bottom': '0', 'left': '0', 'right': '0'}, header=False)
render(body_html(None), tmpdir / 'body1.pdf', M)
pages = chapter_pages(tmpdir / 'body1.pdf')
render(body_html(pages), tmpdir / 'body.pdf', M)

w = PdfWriter()
for part in ['cover.pdf', 'body.pdf', 'back.pdf']:
    for pg in PdfReader(str(tmpdir / part)).pages:
        w.add_page(pg)
w.add_metadata({'/Title': 'Uncross — Project Report', '/Author': 'Ritcha, Surbhi, Tushar Rana'})
with open(OUT, 'wb') as f:
    w.write(f)
print('wrote', OUT, 'chapter pages', pages)
