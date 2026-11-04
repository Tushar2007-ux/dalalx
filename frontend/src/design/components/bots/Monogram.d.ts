import * as React from 'react';

/**
 * Ink-stamp monogram badge (MU, WH, SP, MC, PS, SI, YOU) for bot identity below 40px; full name in aria-label.
 */
export interface MonogramProps {
  /** A bot key, 'you', or any two letters for a hired correspondent. */
  bot?: 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector' | 'you' | (string & {});
  size?: number;
  angle?: number;
  title?: string;
  style?: React.CSSProperties;
}

export declare function Monogram(props: MonogramProps): React.JSX.Element;
