import styled from "@emotion/styled";

import { spacing } from "@/shared/styles/tokens";

export const BadgeContent = styled.span`
  display: inline-flex;
  align-items: center;
  gap: ${spacing[4]};

  svg {
    width: 12px;
    height: 12px;
  }
`;
