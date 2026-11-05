import { useEffect } from 'react'
import { DemoBar } from './demo/DemoBar'
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

  useEffect(() => {
    if (route === 'demo') useDemo.getState().start()
  }, [route])

  return (
    <>
      <Page route={route} />
      <DemoBar />
    </>
  )
}

function Page({ route }: { route: ReturnType<typeof useRoute> }) {
  switch (route) {
    case 'floor': return <Terminal />
    case 'xray': return <XRayPage />
    case 'correspondents': return <Correspondents />
    case 'surveillance': return <Investigations />
    case 'demo': return <Terminal /> // the autopilot moves to its first page at once
    default: return <FrontPage />
  }
}
