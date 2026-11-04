import * as React from 'react';

/**
 * Wraps a surface in the Day or Night edition: sets data-edition, paper colour and grain.
 */
export interface EditionProps {
  edition?: 'day' | 'night';
  grain?: boolean;
  motion?: 'reduced';
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

export declare function Edition(props: EditionProps): JSX.Element;
