import * as React from 'react';

/**
 * Numeric field with ₹ prefix and unit suffix (qty, shares); mono tabular figures.
 */
export interface PriceInputProps {
  label?: string;
  value?: string;
  onChange?: (v: string) => void;
  prefix?: string;
  suffix?: string;
  placeholder?: string;
  hint?: string;
  error?: string;
  state?: 'focus' | 'error' | 'disabled';
  disabled?: boolean;
  readOnly?: boolean;
  id?: string;
  inputMode?: string;
  /** 'right' (default) for figures; 'left' for text fields. */
  align?: 'left' | 'right';
  style?: React.CSSProperties;
}

export declare function PriceInput(props: PriceInputProps): JSX.Element;
