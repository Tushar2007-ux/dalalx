import * as React from 'react';

/**
 * Clipped-notice alert with four severities: info, notice, warning, critical.
 */
export interface AlertNoticeProps {
  severity?: 'info' | 'notice' | 'warning' | 'critical';
  /** Overrides the severity's default label (e.g. "Circuit breaker" instead of "Surveillance action"). */
  label?: string;
  title?: string;
  children?: React.ReactNode;
  time?: string;
  reference?: string;
  onDismiss?: () => void;
  fresh?: boolean;
  style?: React.CSSProperties;
}

export declare function AlertNotice(props: AlertNoticeProps): JSX.Element;
