"""Builds Uncross-Project-Report.pdf from report.md (python3 docs/report/build_pdf.py)."""
import markdown, pathlib, re, subprocess
R = pathlib.Path(__file__).resolve().parent
body = markdown.markdown((R / 'report.md').read_text(), extensions=['tables'])
heads = re.findall(r'<h2>(.*?)</h2>', body)
toc = ''.join(f'<li>{h}</li>' for h in heads)
FONTS = R / 'fonts'
css = f"""
@font-face {{ font-family: 'LB'; font-weight: 400; src: url('file://{FONTS}/libre-baskerville-latin-400-normal.woff2'); }}
@font-face {{ font-family: 'LB'; font-weight: 400; font-style: italic; src: url('file://{FONTS}/libre-baskerville-latin-400-italic.woff2'); }}
@font-face {{ font-family: 'LB'; font-weight: 700; src: url('file://{FONTS}/libre-baskerville-latin-700-normal.woff2'); }}
@font-face {{ font-family: 'Plex'; font-weight: 400; src: url('file://{FONTS}/ibm-plex-sans-latin-400-normal.woff2'); }}
@font-face {{ font-family: 'Plex'; font-weight: 400; font-style: italic; src: url('file://{FONTS}/ibm-plex-sans-latin-400-italic.woff2'); }}
@font-face {{ font-family: 'Plex'; font-weight: 600; src: url('file://{FONTS}/ibm-plex-sans-latin-600-normal.woff2'); }}
@font-face {{ font-family: 'PlexMono'; font-weight: 400; src: url('file://{FONTS}/ibm-plex-mono-latin-400-normal.woff2'); }}
@page {{ size: A4; margin: 22mm 20mm 22mm 20mm; }}
body {{ font: 10.5pt/1.55 'Plex', 'Noto Sans', sans-serif; color: #1A1614; }}
.cover {{ height: 245mm; display: flex; flex-direction: column; justify-content: space-between; page-break-after: always; }}
.cover .top {{ border-top: 3px double #1A1614; border-bottom: 1px solid #1A1614; padding: 6px 0; font: 8.5pt 'PlexMono'; letter-spacing: .12em; text-transform: uppercase; color: #665449; display: flex; justify-content: space-between; }}
.cover h1 {{ font: 700 54pt/1 'LB', serif; margin: 0 0 10px; letter-spacing: -1px; }}
.cover .sub {{ font: italic 15pt/1.45 'LB', serif; color: #4A433D; max-width: 140mm; margin: 0; }}
.cover .kick {{ font: 600 9pt 'PlexMono'; letter-spacing: .14em; text-transform: uppercase; color: #B3261E; margin-bottom: 14px; }}
.cover table.meta {{ width: auto; border-top: 2px solid #1A1614; }}
.cover table.meta td {{ border: none; border-bottom: 1px solid #E2C2B0; padding: 7px 28px 7px 0; font-size: 10.5pt; }}
.cover table.meta td:first-child {{ font: 600 8.5pt 'PlexMono'; letter-spacing: .1em; text-transform: uppercase; color: #665449; }}
.toc {{ page-break-after: always; }}
.toc h2 {{ margin-top: 0; }}
.toc ol {{ list-style: none; padding: 0; }}
.toc li {{ border-bottom: 1px solid #E2C2B0; padding: 7px 0; font: 11pt 'LB', serif; }}
h2 {{ font: 700 17pt/1.2 'LB', serif; margin: 26px 0 10px; padding-top: 10px; border-top: 2px solid #1A1614; page-break-after: avoid; }}
h3 {{ font: 700 12.5pt 'LB', serif; margin: 18px 0 6px; page-break-after: avoid; }}
p {{ margin: 0 0 9px; orphans: 3; widows: 3; }}
li {{ margin: 3px 0; }}
code {{ font: 9pt 'PlexMono', monospace; background: #F7E6DB; padding: 0 3px; border-radius: 2px; }}
table {{ width: 100%; border-collapse: collapse; margin: 10px 0 14px; font-size: 9.5pt; page-break-inside: auto; }}
tr {{ page-break-inside: avoid; }}
th {{ text-align: left; font: 600 8pt 'PlexMono'; letter-spacing: .08em; text-transform: uppercase; color: #665449; border-bottom: 2px solid #1A1614; padding: 6px 10px 5px 0; }}
td {{ border-bottom: 1px solid #E2C2B0; padding: 6px 10px 6px 0; vertical-align: top; }}
td:first-child {{ font-weight: 600; }}
img {{ display: block; max-width: 100%; max-height: 150mm; margin: 10px auto 4px; border: 1px solid #E2C2B0; page-break-inside: avoid; }}
p:has(> img) {{ margin: 0; page-break-after: avoid; }}
p > em:only-child {{ display: block; text-align: center; font-size: 9pt; color: #665449; margin-bottom: 8px; }}
a {{ color: #B3261E; }}
strong {{ font-weight: 600; }}
"""
html = f"""<!doctype html><html><head><meta charset='utf-8'><title>Uncross — Project Report</title><style>{css}</style></head><body>
<section class='cover'>
 <div class='top'><span>Object-Oriented Modelling</span><span>Project Report</span><span>November 2026</span></div>
 <div>
  <div class='kick'>Stock exchange order matching system</div>
  <h1>Uncross</h1>
  <p class='sub'>A multi-stock exchange simulator with a price-time matching engine, built to demonstrate object-oriented modelling.</p>
 </div>
 <table class='meta'>
  <tr><td>Submitted by</td><td>Tushar Rana</td></tr>
  <tr><td>Enrolment no.</td><td>IIT2025165</td></tr>
  <tr><td>Course</td><td>Object-Oriented Modelling</td></tr>
  <tr><td>Institute</td><td>Indian Institute of Information Technology, Allahabad</td></tr>
  <tr><td>Source code</td><td>github.com/Tushar2007-ux/dalalx</td></tr>
 </table>
</section>
<section class='toc'><h2>Contents</h2><ol>{toc}</ol>
<h3>Abstract</h3>
<p>Uncross is a multi-stock exchange simulator with a real price-time matching engine, written in Java 21 for the Object-Oriented Modelling course at IIIT Allahabad. It matches orders for five stocks on five threads, and shows every step of the market through a React website styled as a financial newspaper. The design uses named patterns (Strategy, Template Method, Factory, Observer, State, Command) and the SOLID principles so that new order types, rules and bots are added as new classes rather than edits to the engine. It is covered by 654 automated tests and processes 169,315 orders per second on one thread.</p>
</section>
{body}
</body></html>"""
(R / 'report.html').write_text(html)
js = f"""const {{chromium}}=require('playwright');(async()=>{{const b=await chromium.launch();const p=await b.newPage();await p.goto('file://{R}/report.html');await p.evaluate(()=>document.fonts.ready);await p.waitForTimeout(500);
await p.pdf({{path:'{R.parent}/Uncross-Project-Report.pdf',format:'A4',printBackground:true,displayHeaderFooter:true,headerTemplate:'<span></span>',footerTemplate:'<div style="width:100%;font-size:8px;font-family:monospace;color:#665449;padding:0 20mm;display:flex;justify-content:space-between"><span>Uncross — Project Report</span><span class=pageNumber></span></div>',margin:{{top:'20mm',bottom:'22mm',left:'20mm',right:'20mm'}}}});await b.close()}})()"""
subprocess.run(['node', '-e', js], check=True)
print('ok')
