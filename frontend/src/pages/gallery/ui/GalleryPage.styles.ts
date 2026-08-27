import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Page = styled.main`
  flex-direction: column;
  min-height: 0;
  background-color: ${colors.backgroundDefault};
  overflow: hidden;
`;

export const PageState = styled.main`
  align-items: center;
  justify-content: center;
  padding: ${spacing[24]};
  color: ${colors.textSecondary};

  ${typography.body}
`;
