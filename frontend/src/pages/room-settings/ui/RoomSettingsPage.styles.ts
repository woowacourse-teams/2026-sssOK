import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Page = styled.main`
  display: flex;
  flex-direction: column;
  min-height: 100%;
  background-color: ${colors.backgroundDefault};
`;

export const Header = styled.header`
  position: relative;
  display: flex;
  align-items: center;
  min-height: 64px;
  padding: 0 ${spacing[16]};
`;

export const Title = styled.h1`
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  color: ${colors.textStrong};

  ${typography.heading3}
`;

export const PageState = styled.main`
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: ${spacing[24]};
  color: ${colors.textSecondary};

  ${typography.body}
`;
