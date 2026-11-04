import * as React from 'react';

/**
 * Folio line at the foot of a screen: "Page 1 · Markets · DalalX".
 */
export interface FolioProps {
  page?: number;
  section?: string;
  title?: string;
  right?: React.ReactNode;
  style?: React.CSSProperties;
}

export declare function Folio(props: FolioProps): React.JSX.Element;
