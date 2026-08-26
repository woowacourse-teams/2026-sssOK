import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import type { BadgeProps } from "./Badge";

export const StyledBadge = styled.span<{
  size: BadgeProps["size"];
  variant: BadgeProps["variant"];
}>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  height: ${({ size }) => (size === "sm" ? "22px" : "24px")};
  padding-block: ${({ size }) => (size === "sm" ? 0 : spacing[4])};
  padding-inline: ${({ size }) => (size === "sm" ? spacing[8] : spacing[12])};
  border-radius: ${radius.full};

  background-color: ${({ variant }) =>
    variant === "primary"
      ? colors.primary
      : variant === "soft"
        ? colors.primarySubtle
        : colors.backgroundDefault};
  color: ${({ variant }) =>
    variant === "primary"
      ? colors.textInverse
      : variant === "soft"
        ? colors.textAccent
        : colors.textPrimary};

  ${({ size }) => (size === "sm" ? typography.caption4 : typography.caption2)}

  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;
