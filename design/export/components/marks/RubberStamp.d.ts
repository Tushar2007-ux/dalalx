import * as React from 'react';

/**
 * Rubber stamp: STOP PRESS, UNDER INVESTIGATION, FILLED. Slams in once, at an angle.
 */
export interface RubberStampProps {
  kind?: 'stop-press' | 'under-investigation' | 'filled';
  text?: string;
  sub?: string;
  size?: 'sm' | 'md' | 'lg';
  angle?: number;
  color?: string;
  animate?: boolean;
  slamKey?: string | number;
  /** Paper backing so the stamp stays legible over a portrait or dense type. */
  backed?: boolean;
  style?: React.CSSProperties;
}

export declare function RubberStamp(props: RubberStampProps): JSX.Element;
