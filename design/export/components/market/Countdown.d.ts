import * as React from 'react';

/**
 * Session clock (closing bell, auction end). Digits change instantly; colour escalates near zero.
 */
export interface CountdownProps {
  seconds?: number;
  endsAt?: number;
  label?: string;
  warnAt?: number;
  finalAt?: number;
  size?: 'sm' | 'md' | 'lg';
  /** Overrides the phase note under the digits. */
  note?: string;
  style?: React.CSSProperties;
}

export declare function Countdown(props: CountdownProps): JSX.Element;
