import * as React from 'react';

/**
 * A live price; inks in with a brief green/red tint when it moves. Coalesces to ≤4 inks/s.
 */
export interface PriceCellProps {
  value: number;
  change?: number;
  changePct?: number;
  decimals?: number;
  size?: 'sm' | 'md' | 'lg' | 'xl' | number;
  ink?: 'up' | 'down';
  halted?: boolean;
  showChange?: boolean;
  prefix?: string;
  align?: 'left' | 'right';
  /** Render only the ▲/▼ change figure. */
  changeOnly?: boolean;
  style?: React.CSSProperties;
}

export declare function PriceCell(props: PriceCellProps): React.JSX.Element;
