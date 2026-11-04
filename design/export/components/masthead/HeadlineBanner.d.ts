import * as React from 'react';

/**
 * Live headline strip under the masthead; new headlines typeset in.
 */
export interface HeadlineBannerProps {
  kicker?: string;
  headline: string;
  deck?: string;
  time?: string;
  tone?: 'neutral' | 'up' | 'down';
  state?: 'live' | 'breaking' | 'stale';
  style?: React.CSSProperties;
}

export declare function HeadlineBanner(props: HeadlineBannerProps): JSX.Element;
