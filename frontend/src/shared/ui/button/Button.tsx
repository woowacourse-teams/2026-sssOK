import type { ButtonHTMLAttributes, ReactNode } from "react";

import { StyledButton } from "./Button.styles";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "default" | "primary" | "danger";
  size?: "sm" | "lg";
  children: ReactNode;
}

export const Button = ({
  variant = "primary",
  size = "sm",
  type = "button",
  children,
  ...props
}: ButtonProps) => {
  return (
    <StyledButton variant={variant} size={size} type={type} {...props}>
      {children}
    </StyledButton>
  );
};
