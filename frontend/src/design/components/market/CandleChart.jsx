import React from 'react';

const fmt = (n) => Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const fmtV = (v) => (v >= 1e5 ? (v / 1e5).toFixed(1) + 'L' : v >= 1e3 ? (v / 1e3).toFixed(1) + 'k' : String(v));
const INTERVALS = ['1m', '5m', '15m', '1D'];

/* Engraved candlesticks: up = hollow, outlined in buy ink; down = hatched, sell ink. Shape carries direction as well as colour.
   Volume underneath in hatched ink. Fills its container (ResizeObserver) unless width/height are given. */
export function CandleChart({ candles = [], prevClose, interval = '1m', intervals = INTERVALS, onInterval = () => {}, width: fw, height: fh, minHeight = 260, style }) {
  const ref = React.useRef(null);
  const plotRef = React.useRef(null);
  const [box, setBox] = React.useState({ w: 520, h: 320 });
  const [hover, setHover] = React.useState(null);
  const [tabH, setTabH] = React.useState(null);
  React.useLayoutEffect(() => {
    const el = plotRef.current;
    if (!el) return;
    const measure = () => {
      const r = el.getBoundingClientRect();
      const w = Math.max(240, Math.floor(r.width)), h = Math.max(120, Math.floor(r.height));
      setBox((b) => (b.w === w && b.h === h ? b : { w, h }));
    };
    measure();
    const raf = requestAnimationFrame(measure);
    const t = setTimeout(measure, 400);
    window.addEventListener('load', measure);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(measure);
    const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measure) : null;
    if (ro) ro.observe(el);
    return () => { cancelAnimationFrame(raf); clearTimeout(t); window.removeEventListener('load', measure); if (ro) ro.disconnect(); };
  }, [minHeight]);
  // Re-measure after every render (each data tick), so a stale size self-corrects; equality guard prevents loops.
  React.useLayoutEffect(() => {
    const el = plotRef.current; if (!el) return;
    const w = Math.max(240, Math.floor(el.clientWidth)), h = Math.max(120, Math.floor(el.clientHeight));
    if (w !== box.w || h !== box.h) setBox({ w, h });
  });
  const liveH = plotRef.current ? Math.max(120, Math.floor(plotRef.current.clientHeight)) : box.h;
  const W = fw || box.w, H = fh ? fh - 30 : Math.min(box.h, liveH);
  const uid = React.useMemo(() => 'dxc' + Math.random().toString(36).slice(2, 7), []);
  const pad = { l: 0, r: 66, t: 10, b: 20 };
  const volH = Math.round(H * 0.18);
  const priceH = H - pad.t - pad.b - volH - 8;
  const plotW = W - pad.l - pad.r;
  const n = candles.length;
  const content = n === 0 ? null : (() => {
    const hi = Math.max(...candles.map((c) => c.h)), lo = Math.min(...candles.map((c) => c.l));
    const top = Math.max(hi, prevClose ?? hi), bot = Math.min(lo, prevClose ?? lo);
    const span = (top - bot) || 1, padv = span * 0.06;
    const Y = (v) => pad.t + (1 - (v - (bot - padv)) / (span + 2 * padv)) * priceH;
    const step = plotW / n;
    const bw = Math.max(2, Math.min(9, step * 0.56));
    const X = (i) => pad.l + step * i + step / 2;
    const vmax = Math.max(...candles.map((c) => c.v)) || 1;
    const vBase = pad.t + priceH + 8 + volH;
    const hiI = candles.findIndex((c) => c.h === hi), loI = candles.findIndex((c) => c.l === lo);
    const ticks = 4, tickVals = Array.from({ length: ticks + 1 }, (_, i) => bot - padv + ((span + 2 * padv) * i) / ticks);
    const every = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(plotW / 70))));
    const hv = hover != null ? candles[hover] : null;
    return { Y, X, bw, vmax, vBase, hi, lo, hiI, loI, tickVals, every, hv, step };
  })();
  const onMove = (e) => {
    if (!content) return;
    const r = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - r.left, y = e.clientY - r.top;
    if (x > plotW) return setHover(null);
    setHover({ i: Math.max(0, Math.min(n - 1, Math.floor(x / content.step))), y });
  };
  const hv = hover && candles[hover.i];
  const last = candles[n - 1];
  const yToPrice = (y) => { if (!content) return 0; const { Y } = content; const a = Y(1), b = Y(0); return (y - b) / (a - b); };
  return (
    <div ref={ref} style={{ position: 'relative', width: fw || '100%', height: fh || '100%', minHeight, display: 'flex', flexDirection: 'column', ...style }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, height: 26, marginBottom: 4, minWidth: 0 }}>
        <div role="tablist" aria-label="Interval" style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          {intervals.map((iv, i) => (
            <React.Fragment key={iv}>
              {i > 0 && <span aria-hidden="true" style={{ color: 'var(--ink-muted)', fontSize: 12 }}>·</span>}
              <button role="tab" aria-selected={iv === interval} onClick={() => onInterval(iv)} onMouseEnter={() => setTabH(iv)} onMouseLeave={() => setTabH(null)} className="dx-focus"
                style={{ appearance: 'none', border: 'none', borderRadius: 0, cursor: 'pointer', padding: '2px 7px', fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: iv === interval ? 600 : 500,
                  background: iv === interval ? 'var(--ink)' : tabH === iv ? 'var(--surface-sunk)' : 'transparent', color: iv === interval ? 'var(--ink-inverse)' : 'var(--ink)' }}>{iv}</button>
            </React.Fragment>
          ))}
        </div>
        <span className="dx-num" style={{ fontSize: 11.5, color: 'var(--ink-muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', minWidth: 0 }}>
          {(hv || last) ? <>
            <span>{(hv || last).t}</span>{'  '}O {fmt((hv || last).o)}  H {fmt((hv || last).h)}  L {fmt((hv || last).l)}  C <span style={{ color: (hv || last).c >= (hv || last).o ? 'var(--buy)' : 'var(--sell)', fontWeight: 600 }}>{(hv || last).c >= (hv || last).o ? '▲' : '▼'} {fmt((hv || last).c)}</span>  V {fmtV((hv || last).v)}
          </> : null}
        </span>
      </div>
      <div ref={plotRef} style={{ flex: 1, minHeight: 0, position: 'relative', height: fh ? fh - 30 : undefined }}>
        {content && (
          <svg width={W} height={H} viewBox={'0 0 ' + W + ' ' + H} role="img" aria-label={'Candlestick chart, ' + interval + ' interval, last ' + fmt(last.c)}
            onMouseMove={onMove} onMouseLeave={() => setHover(null)} style={{ position: 'absolute', left: 0, top: 0, display: 'block', overflow: 'visible', cursor: 'crosshair' }}>
            <defs>
              <pattern id={uid + 'd'} width="3" height="3" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="1.2" height="3" fill="var(--sell)" /></pattern>
              <pattern id={uid + 'v'} width="3" height="3" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="1" height="3" fill="var(--ink)" /></pattern>
            </defs>
            {content.tickVals.map((v, i) => (
              <g key={i}>
                <line x1={0} x2={plotW} y1={content.Y(v)} y2={content.Y(v)} stroke="var(--rule)" strokeWidth="1" />
                <text x={plotW + 8} y={content.Y(v) + 3.5} fontFamily="var(--font-mono)" fontSize="10.5" fill="var(--ink-muted)" style={{ fontFeatureSettings: 'var(--figures)' }}>{fmt(v)}</text>
              </g>
            ))}
            {/* Faint vertical grid at the time labels, and a soft wash under the closing prices. */}
            {candles.map((c, i) => (i % content.every === 0 ? <line key={'g' + i} x1={content.X(i)} x2={content.X(i)} y1={pad.t} y2={pad.t + priceH} stroke="var(--rule)" strokeWidth="1" strokeDasharray="1 4" opacity="0.8" /> : null))}
            <linearGradient id={uid + 'a'} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={last.c >= (prevClose ?? candles[0].o) ? 'var(--buy)' : 'var(--sell)'} stopOpacity="0.16" />
              <stop offset="100%" stopColor={last.c >= (prevClose ?? candles[0].o) ? 'var(--buy)' : 'var(--sell)'} stopOpacity="0" />
            </linearGradient>
            {n > 1 && <path d={'M' + candles.map((c, i) => content.X(i).toFixed(1) + ' ' + content.Y(c.c).toFixed(1)).join(' L') + ' L' + content.X(n - 1).toFixed(1) + ' ' + (pad.t + priceH) + ' L' + content.X(0).toFixed(1) + ' ' + (pad.t + priceH) + ' Z'} fill={'url(#' + uid + 'a)'} stroke="none" />}
            {n > 1 && <path d={'M' + candles.map((c, i) => content.X(i).toFixed(1) + ' ' + content.Y(c.c).toFixed(1)).join(' L')} fill="none" stroke={last.c >= (prevClose ?? candles[0].o) ? 'var(--buy)' : 'var(--sell)'} strokeWidth="1" opacity="0.35" />}
            <line x1={plotW} x2={plotW} y1={pad.t} y2={content.vBase} stroke="var(--rule-strong)" strokeWidth="1" />
            <line x1={0} x2={plotW} y1={content.vBase} y2={content.vBase} stroke="var(--rule-strong)" strokeWidth="1" />
            <line x1={0} x2={plotW} y1={pad.t + priceH + 4} y2={pad.t + priceH + 4} stroke="var(--rule)" strokeWidth="1" />
            {prevClose != null && <>
              <line x1={0} x2={plotW} y1={content.Y(prevClose)} y2={content.Y(prevClose)} stroke="var(--ink)" strokeWidth="1" strokeDasharray="1 3" />
              <text x={4} y={content.Y(prevClose) - 4} fontFamily="var(--font-mono)" fontSize="10" fill="var(--ink-muted)">Prev. close {fmt(prevClose)}</text>
            </>}
            {candles.map((c, i) => {
              const up = c.c >= c.o, x = content.X(i), col = up ? 'var(--buy)' : 'var(--sell)';
              const yo = content.Y(c.o), yc = content.Y(c.c), y1 = Math.min(yo, yc), bh = Math.max(1, Math.abs(yc - yo));
              const vh = (c.v / content.vmax) * (volH - 2);
              return (
                <g key={i}>
                  <line x1={x} x2={x} y1={content.Y(c.h)} y2={content.Y(c.l)} stroke={col} strokeWidth="1" />
                  <rect x={x - content.bw / 2} y={y1} width={content.bw} height={bh} fill={up ? 'var(--paper)' : 'url(#' + uid + 'd)'} stroke={col} strokeWidth="1" />
                  <rect x={x - content.bw / 2} y={content.vBase - vh} width={content.bw} height={vh} fill={'url(#' + uid + 'v)'} stroke="var(--ink)" strokeWidth="0.75" />
                  {(i === n - 1 || (i % content.every === 0 && content.X(n - 1) - x > 44)) && (
                    <text x={x} y={content.vBase + 13} textAnchor={i === n - 1 ? 'end' : 'middle'} dx={i === n - 1 ? content.bw / 2 + 2 : 0} fontFamily="var(--font-mono)" fontSize="10" fontWeight={i === n - 1 ? 600 : 400} fill={i === n - 1 ? 'var(--ink)' : 'var(--ink-muted)'}>{c.t}</text>
                  )}
                </g>
              );
            })}
            <g>
              <text x={content.X(content.hiI)} y={content.Y(content.hi) - 6} textAnchor={content.hiI > n * 0.8 ? 'end' : 'middle'} fontFamily="var(--font-mono)" fontSize="10.5" fontWeight="600" fill="var(--ink)">H {fmt(content.hi)} ▾</text>
              <text x={content.X(content.loI)} y={content.Y(content.lo) + 14} textAnchor={content.loI > n * 0.8 ? 'end' : 'middle'} fontFamily="var(--font-mono)" fontSize="10.5" fontWeight="600" fill="var(--ink)">L {fmt(content.lo)} ▴</text>
            </g>
            <g>
              <line x1={0} x2={plotW} y1={content.Y(last.c)} y2={content.Y(last.c)} stroke={last.c >= (prevClose ?? last.o) ? 'var(--buy)' : 'var(--sell)'} strokeWidth="1" strokeDasharray="3 2" />
              <rect x={plotW + 1} y={content.Y(last.c) - 8} width={pad.r - 2} height={16} fill={last.c >= (prevClose ?? last.o) ? 'var(--buy)' : 'var(--sell)'} />
              <text x={plotW + 6} y={content.Y(last.c) + 4} fontFamily="var(--font-mono)" fontSize="11" fontWeight="600" fill={last.c >= (prevClose ?? last.o) ? 'var(--on-buy)' : 'var(--on-sell)'}>{fmt(last.c)}</text>
            </g>
            {hover && hv && (
              <g pointerEvents="none">
                <line x1={content.X(hover.i)} x2={content.X(hover.i)} y1={pad.t} y2={content.vBase} stroke="var(--ink)" strokeWidth="1" strokeDasharray="2 2" />
                {hover.y < pad.t + priceH && <>
                  <line x1={0} x2={plotW} y1={hover.y} y2={hover.y} stroke="var(--ink)" strokeWidth="1" strokeDasharray="2 2" />
                  <rect x={plotW + 1} y={hover.y - 8} width={pad.r - 2} height={16} fill="var(--ink)" />
                  <text x={plotW + 6} y={hover.y + 4} fontFamily="var(--font-mono)" fontSize="11" fill="var(--ink-inverse)">{fmt(yToPrice(hover.y))}</text>
                </>}
                <rect x={content.X(hover.i) - 26} y={content.vBase + 2} width={52} height={15} fill="var(--ink)" />
                <text x={content.X(hover.i)} y={content.vBase + 13} textAnchor="middle" fontFamily="var(--font-mono)" fontSize="10" fill="var(--ink-inverse)">{hv.t}</text>
              </g>
            )}
          </svg>
        )}
      </div>
    </div>
  );
}
