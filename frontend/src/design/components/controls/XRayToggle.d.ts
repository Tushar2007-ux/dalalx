import * as React from 'react';

/**
 * X-Ray mode switch styled as a newspaper cross-reference link ("See the explainer →").
 */
export interface XRayToggleProps {
  on?: boolean;
  onChange?: (on: boolean) => void;
  label?: string;
  offText?: string;
  onText?: string;
  style?: React.CSSProperties;
}

export declare function XRayToggle(props: XRayToggleProps): React.JSX.Element;
