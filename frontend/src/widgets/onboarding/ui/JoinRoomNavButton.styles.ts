import styled from "@emotion/styled";

import { colors, typography } from "@/shared/styles/tokens";

export const TextLink = styled.button`
  min-height: 44px;
  padding: 0 12px;
  color: ${colors.textSecondary};
  ${typography.caption1};

  strong {
    color: ${colors.textStrong};
    font-weight: 700;
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 2px;
    border-radius: 8px;
  }
`;
