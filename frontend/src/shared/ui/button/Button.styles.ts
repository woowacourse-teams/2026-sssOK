import styled from "@emotion/styled";

import { colors, radius, typography } from "@/shared/styles/tokens";
import type { ButtonProps } from "./Button";

export const StyledButton = styled.button<{
  variant: ButtonProps["variant"];
  size: ButtonProps["size"];
}>`
  width: 100%;
  height: ${({ size }) => (size === "sm" ? "55px" : "65px")};
  border: ${({ variant }) => (variant === "default" ? `1.25px solid ${colors.borderDefault}` : 0)};
  border-radius: ${({ size }) => (size === "sm" ? radius[12] : radius[16])};

  background-color: ${({ variant }) =>
    variant === "primary"
      ? colors.primary
      : variant === "danger"
        ? colors.danger
        : colors.backgroundDefault};
  color: ${({ variant }) => (variant === "default" ? colors.textStrong : colors.textInverse)};

  ${({ size }) => (size === "sm" ? typography.label5 : typography.label1)}

  cursor: pointer;

  @media (hover: hover) {
    &:hover:not(:disabled) {
      background-color: ${({ variant }) => {
        if (variant === "primary") return colors.primaryPressed;
        if (variant === "default") return colors.interactiveHover;
        if (variant === "danger") return colors.dangerPressed;
        return undefined;
      }};
    }
  }

  &:active:not(:disabled) {
    background-color: ${({ variant }) => {
      if (variant === "primary") return colors.primaryPressed;
      if (variant === "default") return colors.interactiveHover;
      if (variant === "danger") return colors.dangerPressed;
      return undefined;
    }};
  }

  &:disabled {
    background-color: ${colors.disabled};
    color: ${colors.textInverse};
    cursor: not-allowed;
  }
`;
