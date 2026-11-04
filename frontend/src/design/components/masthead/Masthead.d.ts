import * as React from 'react';

/**
 * The DalalX nameplate with dateline and market status; one per screen, at the top.
 * @startingPoint section="DalalX" subtitle="The DalalX nameplate with dateline and market status; one per screen, at the top." viewport="700x300"
 */
export interface MastheadProps {
  title?: string;
  place?: string;
  date?: string;
  session?: number;
  status?: 'live' | 'pre-open' | 'halted' | 'auction' | 'closed';
  editionName?: string;
  leftMeta?: string;
  rightMeta?: string;
  compact?: boolean;
  /** One slim strip: small wordmark, dateline, status, edition, right slot. Use on the terminal; full masthead on the landing page. */
  strip?: boolean;
  right?: React.ReactNode;
  style?: React.CSSProperties;
}

export declare function Masthead(props: MastheadProps): React.JSX.Element;
