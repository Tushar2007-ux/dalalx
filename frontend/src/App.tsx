import { Correspondents } from './pages/Correspondents'
import { useRoute } from './shell/route'
import { Terminal } from './terminal/Terminal'
import { useMarketData } from './terminal/useMarketData'

/**
 * The app: one live data connection shared by every page, and a page chosen
 * from the URL hash. Step 16 adds the landing page, X-Ray and surveillance.
 */
export default function App() {
  useMarketData()
  const route = useRoute()
  return route === 'correspondents' ? <Correspondents /> : <Terminal />
}
