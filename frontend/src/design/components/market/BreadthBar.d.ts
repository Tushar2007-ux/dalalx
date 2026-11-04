import * as React from 'react';

/**
 * Market breadth: advancing vs declining stocks as a hatched split bar with ▲/▼ counts.
 */
export interface BreadthBarProps {
  advancing?: number;
  declining?: number;
  unchanged?: number;
  halted?: number;
  style?: React.CSSProperties;
}

export declare function BreadthBar(props: BreadthBarProps): React.JSX.Element;
