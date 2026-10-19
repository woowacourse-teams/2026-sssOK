import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { NARROW, edgeScrollRow } from "./responsive";

export const FilterRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${spacing[8]};

  ${NARROW} {
    ${edgeScrollRow}
  }
`;

export const Chip = styled.button<{ $selected: boolean }>`
  height: 27px;
  padding-inline: ${spacing[12]};
  border: 1px solid ${({ $selected }) => ($selected ? colors.borderPrimary : colors.borderDefault)};
  border-radius: ${radius.full};

  background-color: ${({ $selected }) =>
    $selected ? colors.primarySubtle : colors.backgroundDefault};
  color: ${({ $selected }) => ($selected ? colors.textAccent : colors.textSecondary)};

  ${typography.caption2}

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 2px;
  }
`;
