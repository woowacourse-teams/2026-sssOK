import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Page = styled.main`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  gap: ${spacing[12]};
  padding: ${spacing[24]};
`;

export const Title = styled.h1`
  color: ${colors.textStrong};

  ${typography.heading1}
`;

export const Empty = styled.p`
  color: ${colors.textSecondary};

  ${typography.body}
`;
