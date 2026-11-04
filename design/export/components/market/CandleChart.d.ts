import * as React from 'react';

/**
 * Engraved candlestick chart with hatched volume, time axis, day high/low, previous close, crosshair readout and interval tabs. Fills its container.
 */
export interface CandleChartProps {
  candles: { t: string;
  o: number;
  h: number;
  l: number;
  c: number;
  v: number }[];
  prevClose?: number;
  interval?: string;
  intervals?: string[];
  onInterval?: (iv: string) => void;
  width?: number;
  height?: number;
  minHeight?: number;
  style?: React.CSSProperties;
}

export declare function CandleChart(props: CandleChartProps): JSX.Element;
