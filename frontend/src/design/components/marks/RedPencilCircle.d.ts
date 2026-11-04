import * as React from 'react';

/**
 * Surveillance red-pencil loop drawn around a figure or word.
 */
export interface RedPencilCircleProps {
  children: React.ReactNode;
  draw?: boolean;
  drawKey?: string | number;
  color?: string;
  label?: string;
  style?: React.CSSProperties;
}

export declare function RedPencilCircle(props: RedPencilCircleProps): React.JSX.Element;
