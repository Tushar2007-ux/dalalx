import { useEffect } from 'react'
import { DemoBar } from './demo/DemoBar'
import { MomentOverlay } from './shell/MomentOverlay'
import { PressIntro } from './shell/PressIntro'
import { HelpCard } from './shell/HelpCard'
import { useShortcuts } from './shell/shortcuts'
import { useMarket } from './store/market'
import { AudiencePage } from './audience/AudiencePage'
import { JoinPage } from './audience/JoinPage'
import { EngineRoom } from './engine/EngineRoom'
import { BigBoard } from './pages/BigBoard'
import { HowItWorks } from './pages/HowItWorks'
import { useAutopilot, useDemo } from './demo/autopilot'
import { Correspondents } from './pages/Correspondents'
import { FrontPage } from './pages/FrontPage'
import { Investigations } from './pages/Investigations'
import { XRayPage } from './pages/XRayPage'
import { useRoute } from './shell/route'
import { Terminal } from './terminal/Terminal'
import { useMarketData } from './terminal/useMarketData'

/**
 * The app: one live data connection shared by every page, a page chosen from
 * the URL hash, and the demo autopilot (started by visiting #/demo).
 */
export default function App() {
  useMarketData()
  useAutopilot()
  const route = useRoute()
  const presenter = useMarket((s) => s.presenter)
  useShortcuts(route !== 'join')

  useEffect(() => {
    if (route === 'demo') useDemo.getState().start()
  }, [route])

  return (
    <>
      {/* Each page turns in like a newspaper page; keyed by route so the animation replays on every visit. */}
      <div key={route} className={`dx-page-turn${presenter && route !== 'join' ? ' dx-presenter' : ''}`}>
        <Page route={route} />
      </div>
      <MomentOverlay />
      <HelpCard />
      <DemoBar />
      <PressIntro />
    </>
  )
}

function Page({ route }: { route: ReturnType<typeof useRoute> }) {
  switch (route) {
    case 'floor': return <Terminal />
    case 'xray': return <XRayPage />
    case 'correspondents': return <Correspondents />
    case 'surveillance': return <Investigations />
    case 'engine': return <EngineRoom />
    case 'audience': return <AudiencePage />
    case 'how': return <HowItWorks />
    case 'wall': return <BigBoard />
    case 'join': return <JoinPage />
    case 'demo': return <Terminal /> // the autopilot moves to its first page at once
    default: return <FrontPage />
  }
}
