import * as React from 'react';

/**
 * A newspaper column: section label + content, separated from neighbours by thin vertical rules.
 */
export interface RuledPanelProps {
  label?: string;
  meta?: React.ReactNode;
  count?: number;
  rule?: 'bold' | 'hair' | 'double' | 'none';
  columnRule?: 'none' | 'left' | 'right';
  state?: 'ready' | 'loading' | 'empty' | 'stale' | 'error';
  emptyText?: string;
  footer?: React.ReactNode;
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

export declare function RuledPanel(props: RuledPanelProps): JSX.Element;
