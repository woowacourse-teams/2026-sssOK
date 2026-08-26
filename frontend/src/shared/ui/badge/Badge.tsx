import type { HTMLAttributes, ReactNode } from "react";

import { StyledBadge } from "./Badge.styles";

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  size?: "sm" | "md";
  variant?: "primary" | "soft" | "neutral";
  children: ReactNode;
}

export const Badge = ({ size = "sm", variant = "neutral", children, ...props }: BadgeProps) => {
  return (
    <StyledBadge size={size} variant={variant} {...props}>
      {children}
    </StyledBadge>
  );
};
