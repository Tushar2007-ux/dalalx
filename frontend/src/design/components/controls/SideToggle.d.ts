import * as React from 'react';

/**
 * Buy / Sell selector; always shows ▲/▼ and the word, never colour alone.
 */
export interface SideToggleProps {
  value?: 'buy' | 'sell';
  onChange?: (v: 'buy' | 'sell') => void;
  disabled?: boolean;
  hoverValue?: 'buy' | 'sell';
  focusValue?: 'buy' | 'sell';
  style?: React.CSSProperties;
}

export declare function SideToggle(props: SideToggleProps): React.JSX.Element;
