import { useMarket } from '../store/market'
import { swing, WEATHER_COPY, weatherFor, type Weather } from './signals'

/**
 * The market outlook, set like a newspaper's weather box: an engraved icon,
 * one word, and a line of advice. The forecast is measured, not written — it
 * comes from how far prices have swung on the recent tape, and any halted
 * stock makes it a storm.
 */
export function WeatherBox() {
  const trail = useMarket((s) => s.trail)
  const status = useMarket((s) => s.status)
  const pct = swing(Object.values(trail))
  const anyHalted = Object.values(status).some((st) => st.phase !== 'OPEN')
  const weather = weatherFor(pct, anyHalted)
  const copy = WEATHER_COPY[weather]
  return (
    <aside className={`dx-weather is-${weather}`} aria-label={`Market outlook: ${copy.title}`}>
      <WeatherIcon weather={weather} />
      <div>
        <span className="dx-weather-kicker">Market outlook</span>
        <strong className="dx-weather-title">{copy.title}</strong>
        <p>{copy.line}</p>
        <span className="dx-weather-reading dx-num">Recent swing {pct.toFixed(2)}%</span>
      </div>
    </aside>
  )
}

/** Four small engravings: sun, sun-and-cloud, rain cloud, thunderhead. */
function WeatherIcon({ weather }: { weather: Weather }) {
  const cloud = 'M14 40 a9 9 0 0 1 2 -17.8 a13 13 0 0 1 25 -2.4 a10.5 10.5 0 0 1 3 20.2 z'
  return (
    <svg className="dx-weather-icon" width="64" height="64" viewBox="0 0 56 56" aria-hidden="true" fill="none"
      stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {(weather === 'fair' || weather === 'breezy') && (
        <g className="dx-weather-sun">
          <circle cx={weather === 'fair' ? 28 : 19} cy={weather === 'fair' ? 28 : 19} r={weather === 'fair' ? 9 : 7} />
          {Array.from({ length: 8 }, (_, i) => {
            const a = (i * Math.PI) / 4
            const cx = weather === 'fair' ? 28 : 19, cy = cx
            const r1 = weather === 'fair' ? 13.5 : 10.5, r2 = weather === 'fair' ? 18.5 : 14
            return <line key={i} x1={cx + Math.cos(a) * r1} y1={cy + Math.sin(a) * r1} x2={cx + Math.cos(a) * r2} y2={cy + Math.sin(a) * r2} />
          })}
        </g>
      )}
      {weather !== 'fair' && <path d={cloud} fill="var(--surface)" />}
      {weather !== 'fair' && <path d="M19 33 h20 M17 37 h14" strokeWidth="0.9" opacity="0.6" />}
      {weather === 'unsettled' && <g className="dx-weather-rain"><line x1="19" y1="45" x2="17" y2="51" /><line x1="28" y1="45" x2="26" y2="51" /><line x1="37" y1="45" x2="35" y2="51" /></g>}
      {weather === 'stormy' && <path className="dx-weather-bolt" d="M30 41 l-6 8 h5 l-3 7 l9 -10 h-5 l3 -5 z" fill="var(--accent)" stroke="var(--accent)" strokeWidth="1" />}
    </svg>
  )
}
