import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { NARROW, edgeScrollRow } from "./responsive";

export const TabRow = styled.div`
  display: flex;
  gap: 28px;
  margin: ${spacing[16]} ${spacing[16]} 0;
  border-bottom: 1px solid ${colors.borderDefault};

  ${NARROW} {
    ${edgeScrollRow}

    gap: ${spacing[20]};
  }
`;

export const Tab = styled.button<{ $active: boolean }>`
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: ${spacing[8]};
  padding: 0 ${spacing[4]} ${spacing[12]};
  border-bottom: 2px solid ${({ $active }) => ($active ? colors.primary : "transparent")};
  color: ${({ $active }) => ($active ? colors.textAccent : colors.textSecondary)};

  ${typography.label3}

  font-weight: ${({ $active }) => ($active ? 700 : 600)};

  ${NARROW} {
    font-size: 15px;
  }

  &:focus-visible {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: -2px;
  }
`;

export const Count = styled.span<{ $active: boolean }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding-inline: ${spacing[8]};
  border-radius: ${radius.full};
  background: ${({ $active }) => ($active ? colors.primarySubtle : colors.backgroundSubtle)};
  color: ${({ $active }) => ($active ? colors.textAccent : colors.textSecondary)};
  font-variant-numeric: tabular-nums;

  ${typography.caption2}
`;
