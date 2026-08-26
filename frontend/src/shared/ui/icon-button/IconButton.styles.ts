import styled from "@emotion/styled";

import { colors, radius } from "@/shared/styles/tokens";

export const StyledIconButton = styled.button<{
  $size: "sm" | "md";
  $variant: "default" | "danger";
}>`
  display: inline-flex;
  align-items: center;
  justify-content: center;

  width: 40px;
  height: 40px;

  padding: 0;
  border: 0;
  border-radius: ${radius[12]};

  background-color: ${colors.backgroundDefault};
  color: ${({ $variant }) => ($variant === "danger" ? colors.danger : colors.textStrong)};
  transition:
    background-color 150ms ease,
    color 150ms ease;
  cursor: pointer;

  > svg {
    width: ${({ $size }) => ($size === "sm" ? "22px" : "24px")};
    height: ${({ $size }) => ($size === "sm" ? "22px" : "24px")};
  }

  &:hover {
    background-color: ${({ $variant }) =>
      $variant === "danger"
        ? `color-mix(in srgb, ${colors.danger} 10%, white)`
        : colors.interactiveHover};
  }
`;
