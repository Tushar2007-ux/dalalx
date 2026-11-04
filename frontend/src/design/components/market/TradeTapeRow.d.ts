import * as React from 'react';

/**
 * One printed trade on the tape; new rows typeset in left to right.
 */
export interface TradeTapeRowProps {
  time: string;
  symbol: string;
  side?: 'buy' | 'sell';
  price: number;
  qty: number;
  buyer?: string;
  seller?: string;
  fresh?: boolean;
  mine?: boolean;
  flagged?: boolean;
  /** Bot key or 'you'; with sellerBot renders two stamp monograms (buyer ← seller) with names on hover. */
  buyerBot?: string;
  sellerBot?: string;
  style?: React.CSSProperties;
}

export declare function TradeTapeRow(props: TradeTapeRowProps): React.JSX.Element;
