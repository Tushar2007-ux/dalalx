import * as React from 'react';

/**
 * One price level of the book with a hatched depth bar in its own track.
 * @startingPoint section="DalalX" subtitle="One price level of the book with a hatched depth bar in its own track." viewport="700x300"
 */
export interface OrderBookRowProps {
  side?: 'bid' | 'ask';
  price: number;
  qty: number;
  orders?: number;
  depth?: number;
  best?: boolean;
  mine?: boolean;
  flagged?: boolean;
  hover?: boolean;
  onClick?: (price: number) => void;
  /** Bot key shown as a head when xray is on. */
  bot?: string;
  xray?: boolean;
  /** 'track' (default): hatched bar in its own column, bids from left, asks from right; never overlaps figures. 'row': behind the row. */
  depthStyle?: 'row' | 'track';
  style?: React.CSSProperties;
}

export declare function OrderBookRow(props: OrderBookRowProps): JSX.Element;
