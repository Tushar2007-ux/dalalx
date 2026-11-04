import * as React from 'react';

/**
 * Newspaper-style section tabs for switching the active stock.
 */
export interface StockTabsProps {
  tabs: { symbol: string;
  name?: string;
  change: number;
  halted?: boolean }[];
  value: string;
  onChange?: (symbol: string) => void;
  hoverSymbol?: string;
  focusSymbol?: string;
  style?: React.CSSProperties;
}

export declare function StockTabs(props: StockTabsProps): React.JSX.Element;
