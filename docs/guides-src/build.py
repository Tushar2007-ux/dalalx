"""Builds the two self-contained guide pages in docs/ from the sources in this folder.

Run from anywhere:  python3 docs/guides-src/build.py
"""
import base64
import pathlib
import re

HERE = pathlib.Path(__file__).resolve().parent
DOCS = HERE.parent
FONTS = ('<link rel="preconnect" href="https://fonts.googleapis.com">'
         '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>'
         '<link href="https://fonts.googleapis.com/css2?family=Libre+Baskerville:ital,wght@0,400;0,700;1,400'
         '&family=IBM+Plex+Sans:ital,wght@0,400;0,500;0,600;1,400&family=IBM+Plex+Mono:wght@400;500;600&display=swap" rel="stylesheet">')
UP = '<svg viewBox="0 0 24 24" fill="none" stroke-width="2"><path d="M12 19V5M5 12l7-7 7 7"/></svg>'

OOM_CSS = """
.vs { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; max-width: 900px; margin: 18px 0; }
.vs > div > p.lab { font: 600 11px var(--mono); letter-spacing: .12em; text-transform: uppercase; margin: 0 0 6px; }
.vs .bad p.lab { color: var(--accent); } .vs .good p.lab { color: var(--good); }
.vs .code { margin: 0; }
.vs .bad pre { border-top-color: var(--accent); } .vs .good pre { border-top-color: var(--good); }
.uml { font: 14px var(--mono); white-space: nowrap; }
"""


def read(name):
    return (HERE / name).read_text(encoding='utf-8')


def toc(main):
    items = re.findall(r'<section class="part" id="([^"]+)">\s*<div class="part-head"><span class="num"[^>]*>(\d+)</span>(?:<p class="kicker">[^<]*</p>)?<h2>(.*?)</h2>', main)
    lis = '\n'.join(f'    <li><a href="#{i}"><span class="n">{n}</span><span>{re.sub("<[^>]+>", "", t)}</span></a></li>' for i, n, t in items)
    return len(items), lis


def page(title, brand, short, dateline, h1, sub, meta, main, extra_css='', extra_js=''):
    n, lis = toc(main)
    seg = ('<div class="seg" role="group" aria-label="Edition"><button type="button" data-theme-set="light">Day</button>'
           '<button type="button" data-theme-set="dark">Night</button></div>')
    return f'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<meta name="description" content="{sub}">
{FONTS}
<style>
{read('shared.css')}{extra_css}
</style>
</head>
<body>
<a class="btn" href="#content" style="position:absolute;left:-9999px" onfocus="this.style.left='12px';this.style.top='12px';this.style.zIndex=99">Skip to content</a>
<div class="topbar"><div class="wrap">
  <button class="btn menu-btn" type="button" aria-expanded="false" aria-controls="toc">Contents</button>
  <a class="brand" href="#top">{brand}</a>
  <span class="now" id="now">{short}</span>
  {seg}
</div><div class="progress"></div></div>

<div class="wrap" id="top">
<header class="mast">
  <div class="dateline">{dateline}</div>
  <h1>{h1}</h1>
  <p class="sub">{sub}</p>
  <div class="meta">{meta}</div>
</header>

<div class="layout">
<nav class="toc" id="toc" aria-label="Contents">
  <p class="toc-head">Contents <span>{n} parts</span></p>
  <ol>
{lis}
  </ol>
  <p class="keys"><kbd>j</kbd> next part · <kbd>k</kbd> previous</p>
  <div class="mobile-seg">{seg}</div>
</nav>

<main id="content">
{main}
</main>
</div>
</div>
<footer><div class="wrap"><span>Uncross · The Morning Edition</span><span>{short}</span></div></footer>
<div class="scrim"></div>
<button class="to-top" type="button" aria-label="Back to top">{UP}</button>
<script>
{read('shared.js')}
{extra_js}
</script>
</body>
</html>
'''


def inline_images(html):
    def sub(m):
        data = base64.b64encode((HERE / 'img' / (m.group(1) + '.png')).read_bytes()).decode()
        return 'data:image/png;base64,' + data
    return re.sub(r'\{\{(\w+)\}\}', sub, html)


guide = page(
    title="Uncross Presenter's Guide", brand='Uncross', short="Presenter's Guide",
    dateline="<span>Vol. I · Presenter's Guide</span><span>Explained from zero</span><span>The Morning Edition</span>",
    h1='Uncross, <em>explained</em>',
    sub='Everything about the site for someone who has never seen a stock market: what each page shows, what to click, what to say, and how to answer the questions.',
    meta='<span><b>14</b> parts</span><span><b>12</b> pages toured</span><span><b>16</b> practice questions</span><span><b>12 min</b> timed plan</span>',
    main=read('guide-main.html'), extra_css=read('guide.css'), extra_js=read('guide.js'))
(DOCS / 'uncross-presenter-guide.html').write_text(guide, encoding='utf-8')

oom = page(
    title='Uncross OOM Principles', brand='Uncross', short='OOM Companion',
    dateline='<span>Vol. I · Course companion</span><span>Object-Oriented Modelling</span><span>The Morning Edition</span>',
    h1='How Uncross uses <em>OOM</em>',
    sub='Every object-oriented principle, pattern and UML relationship in the project, explained simply and tied to the exact class where it lives, so you can point at the code when asked.',
    meta='<span><b>4</b> pillars</span><span><b>6</b> relationships</span><span><b>5</b> SOLID rules</span><span><b>11</b> patterns</span><span><b>4</b> class diagrams</span>',
    main=read('oom-main.html'), extra_css=OOM_CSS)
(DOCS / 'uncross-oom-principles.html').write_text(inline_images(oom), encoding='utf-8')
print('built both guides')
