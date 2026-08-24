import type { ButtonHTMLAttributes, ReactNode } from "react";

import { StyledIconButton } from "./IconButton.styles";

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  size?: "sm" | "md";
  variant?: "default" | "danger";
  children: ReactNode;
}

export const IconButton = ({
  size = "md",
  variant = "default",
  type = "button",
  children,
  ...props
}: IconButtonProps) => {
  return (
    <StyledIconButton type={type} $size={size} $variant={variant} {...props}>
      {children}
    </StyledIconButton>
  );
};
