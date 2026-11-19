import styled from "@emotion/styled";
import { Link } from "react-router-dom";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { Button } from "@/shared/ui/button";

export const Page = styled.main`
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  max-width: 480px;
  min-height: 100dvh;
  margin-inline: auto;
  padding: ${spacing[16]};
`;

export const Content = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${spacing[32]};
  padding-block: ${spacing[16]};
  text-align: center;
`;

export const Image = styled.img`
  width: min(180px, 52vw);
  height: auto;
  object-fit: contain;
`;

export const Title = styled.h1`
  color: ${colors.textStrong};
  white-space: pre-line;
  ${typography.heading2}
`;

export const Description = styled.p`
  margin-top: ${spacing[8]};
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

export const ErrorStateButton = styled(Button)`
  height: 65px;
  border-radius: ${radius[16]};
  ${typography.label1}

  @media (min-width: 768px) {
    height: 65px;
  }
`;

export const ErrorStateLink = styled(Link, {
  shouldForwardProp: (prop) => prop !== "$primary",
})<{ $primary?: boolean }>`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 65px;
  padding: ${spacing[12]};
  border: ${({ $primary }) => ($primary ? "0" : `1.25px solid ${colors.borderDefault}`)};
  border-radius: ${radius[16]};
  background-color: ${({ $primary }) => ($primary ? colors.primary : colors.backgroundDefault)};
  color: ${({ $primary }) => ($primary ? colors.textInverse : colors.textStrong)};
  text-align: center;
  ${typography.label1}

  @media (hover: hover) {
    &:hover {
      background-color: ${({ $primary }) => ($primary ? colors.primaryPressed : colors.interactiveHover)};
    }
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 3px;
  }
`;
