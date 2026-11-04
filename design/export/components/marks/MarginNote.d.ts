import * as React from 'react';

/**
 * Pencil note in the margin, from the Surveillance Inspector or the user.
 */
export interface MarginNoteProps {
  children: React.ReactNode;
  author?: string;
  time?: string;
  side?: 'right' | 'left' | 'below';
  tone?: 'pencil' | 'ink';
  style?: React.CSSProperties;
}

export declare function MarginNote(props: MarginNoteProps): JSX.Element;
