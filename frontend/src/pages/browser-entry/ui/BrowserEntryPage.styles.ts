import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Page = styled.main`
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  padding: ${spacing[16]};
  padding-bottom: calc(${spacing[16]} + env(safe-area-inset-bottom, 0px));
`;

export const Content = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${spacing[32]};
  text-align: center;
`;

export const Image = styled.img`
  width: min(180px, 52vw);
  height: auto;
  object-fit: contain;
`;

export const Title = styled.h1`
  color: ${colors.textStrong};
  ${typography.heading2}
`;

export const Description = styled.p`
  color: ${colors.textSecondary};
  white-space: pre-line;
  ${typography.body}
`;

export const Actions = styled.div`
  display: flex;
  flex: none;
  flex-direction: column;
  gap: ${spacing[12]};
  width: 100%;
`;
