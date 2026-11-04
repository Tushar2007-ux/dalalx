import * as React from 'react';

/**
 * Byline card for a bot: portrait, name, desk, quote, status, stats. card or compact layout.
 * @startingPoint section="DalalX" subtitle="Byline card for a bot: portrait, name, desk, quote, status, stats. card or compact layout." viewport="700x300"
 */
export interface BotBylineProps {
  bot?: 'munim' | 'momentum' | 'panic' | 'whale' | 'spoofer' | 'inspector';
  stats?: { label: string;
  value: string;
  dir?: 'up' | 'down' }[];
  status?: 'active' | 'idle' | 'flagged' | 'halted' | 'duty';
  layout?: 'card' | 'compact';
  portraitSize?: number;
  selected?: boolean;
  hover?: boolean;
  onClick?: () => void;
  style?: React.CSSProperties;
}

export declare function BotByline(props: BotBylineProps): JSX.Element;
