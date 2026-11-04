import * as React from 'react';

/**
 * Ink-stamp monogram badge (MU, WH, SP, MC, PS, SI, YOU) for bot identity below 40px; full name in aria-label.
 */
export interface MonogramProps {
  bot?: 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector' | 'you';
  size?: number;
  angle?: number;
  title?: string;
  style?: React.CSSProperties;
}

export declare function Monogram(props: MonogramProps): JSX.Element;
