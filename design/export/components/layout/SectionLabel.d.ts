import * as React from 'react';

/**
 * Small-caps section label over a rule (MARKETS, ORDER BOOK, TRADE TAPE, SURVEILLANCE).
 */
export interface SectionLabelProps {
  children: React.ReactNode;
  meta?: React.ReactNode;
  count?: number;
  rule?: 'bold' | 'hair' | 'double' | 'none';
  /** Rule beneath the label. Default 'double'. */
  under?: 'double' | 'hair' | 'none';
  as?: string;
  id?: string;
  style?: React.CSSProperties;
}

export declare function SectionLabel(props: SectionLabelProps): JSX.Element;
