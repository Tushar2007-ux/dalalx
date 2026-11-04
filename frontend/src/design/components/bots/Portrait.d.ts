import * as React from 'react';

/**
 * Stippled ink portrait of a trading bot, printed into the paper. <48px uses the head crop.
 */
export interface PortraitProps {
  bot?: 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector';
  size?: number;
  crop?: 'auto' | 'full' | 'head';
  plate?: boolean;
  frame?: boolean;
  alt?: string;
  file?: string;
  style?: React.CSSProperties;
}

export declare function Portrait(props: PortraitProps): React.JSX.Element;
