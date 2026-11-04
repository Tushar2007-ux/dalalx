import * as React from 'react';

/**
 * Action button. primary = solid ink, secondary = outlined, danger = red ink, ghost = text.
 */
export interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  loading?: boolean;
  state?: 'hover' | 'active' | 'focus';
  fullWidth?: boolean;
  kbd?: string;
  children?: React.ReactNode;
  onClick?: () => void;
  type?: 'button' | 'submit';
  style?: React.CSSProperties;
}

export declare function Button(props: ButtonProps): React.JSX.Element;
