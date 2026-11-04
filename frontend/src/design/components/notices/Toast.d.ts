import * as React from 'react';

/**
 * Order confirmation slip: filled, partial, rejected, cancelled, info.
 */
export interface ToastProps {
  kind?: 'filled' | 'partial' | 'rejected' | 'cancelled' | 'info';
  title: string;
  detail?: string;
  time?: string;
  action?: { label: string;
  onClick: () => void };
  onClose?: () => void;
  animate?: boolean;
  style?: React.CSSProperties;
}

export declare function Toast(props: ToastProps): React.JSX.Element;
