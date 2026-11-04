import * as React from 'react';

/**
 * Segmented Limit / Market / IOC selector with a one-line explanation.
 */
export interface OrderTypeToggleProps {
  value?: string;
  onChange?: (v: string) => void;
  options?: { value: string;
  label: string;
  hint?: string }[];
  disabled?: boolean;
  showHint?: boolean;
  hoverValue?: string;
  focusValue?: string;
  style?: React.CSSProperties;
}

export declare function OrderTypeToggle(props: OrderTypeToggleProps): JSX.Element;
