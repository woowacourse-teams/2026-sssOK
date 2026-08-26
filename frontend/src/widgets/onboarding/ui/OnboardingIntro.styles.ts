import styled from "@emotion/styled";

import { colors, typography } from "@/shared/styles/tokens";
import { Stack } from "@/shared/ui/stack";

export const IntroStack = styled(Stack)`
  width: 100%;
  text-align: center;
`;

export const ImageSlot = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 150px;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
`;

export const Title = styled.h1`
  color: ${colors.textStrong};
  ${typography.heading1};
`;

export const Highlight = styled.span`
  color: ${colors.textAccent};
`;

export const Description = styled.p`
  color: ${colors.textSecondary};
  ${typography.body};
`;
