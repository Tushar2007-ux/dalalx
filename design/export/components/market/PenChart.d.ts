import * as React from 'react';

/**
 * Intraday line drawn in ink; only the newest segment draws, like a pen extending.
 */
export interface PenChartProps {
  data: number[];
  prevClose?: number;
  /** Omit to fill the container (ResizeObserver). */
  width?: number;
  height?: number;
  animate?: boolean;
  style?: React.CSSProperties;
}

export declare function PenChart(props: PenChartProps): JSX.Element;
