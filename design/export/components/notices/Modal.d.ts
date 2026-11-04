import * as React from 'react';

/**
 * Dialog on a paper scrim; for order confirmation and surveillance rulings.
 */
export interface ModalProps {
  open?: boolean;
  kicker?: string;
  title: string;
  children?: React.ReactNode;
  actions?: React.ReactNode;
  onClose?: () => void;
  width?: number;
  inline?: boolean;
  style?: React.CSSProperties;
}

export declare function Modal(props: ModalProps): JSX.Element;
