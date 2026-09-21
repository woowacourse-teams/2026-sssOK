import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const FilterRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${spacing[8]};
  padding: ${spacing[16]} ${spacing[16]} ${spacing[8]};
`;

export const Chip = styled.button<{ $selected: boolean }>`
  height: 24px;
  padding-inline: ${spacing[12]};
  border: 1px solid ${({ $selected }) => ($selected ? colors.borderPrimary : colors.borderDefault)};
  border-radius: ${radius.full};

  background-color: ${({ $selected }) =>
    $selected ? colors.primarySubtle : colors.backgroundDefault};
  color: ${({ $selected }) => ($selected ? colors.textAccent : colors.textSecondary)};

  ${typography.caption4}

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 2px;
  }
`;
