import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Header = styled.header`
  display: flex;
  align-items: center;
  width: 100%;
  height: 56px;
  padding: 0 ${spacing[12]} 0 ${spacing[8]};
`;

export const Title = styled.h1`
  color: ${colors.textStrong};

  ${typography.heading3}
`;
