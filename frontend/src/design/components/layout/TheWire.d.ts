import * as React from 'react';

/**
 * "The Wire" news-in-brief list: last headlines with timestamps, newest first.
 */
export interface TheWireProps {
  items: { id?: string | number;
  time: string;
  headline: string;
  tone?: 'up' | 'down' | 'alert';
  fresh?: boolean }[];
  limit?: number;
  style?: React.CSSProperties;
}

export declare function TheWire(props: TheWireProps): React.JSX.Element;
