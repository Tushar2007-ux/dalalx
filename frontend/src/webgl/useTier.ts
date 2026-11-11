import { useEffect, useState } from 'react'
import { useMarket } from '../store/market'
import { type Tier, readMachine, tierFor } from './quality'

/**
 * The tier this page should draw at: what the machine can afford, unless the
 * reader has switched 3D off in the Tools menu, which always wins.
 *
 * The machine is read once — it does not change mid-session — but the switch
 * is live, so turning 3D off takes the scenes down immediately.
 */
export function useTier(): Tier {
  const wanted = useMarket((s) => s.three)
  const [machineTier, setMachineTier] = useState<Tier>('off')

  // Read after mount, never during render: the server-side pass has no
  // `navigator`, and a tier read during render would differ between the two.
  useEffect(() => { setMachineTier(tierFor(readMachine())) }, [])

  return wanted ? machineTier : 'off'
}
