import { Link } from "react-router-dom";
import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const Page = styled.main`
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  padding: ${spacing[16]};
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

export const ActionLink = styled(Link)`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 65px;
  border: 1.25px solid ${colors.borderDefault};
  border-radius: ${radius[16]};
  background-color: ${colors.backgroundDefault};
  color: ${colors.textStrong};
  ${typography.label1}

  @media (hover: hover) {
    &:hover {
      background-color: ${colors.interactiveHover};
    }
  }

  &:active {
    background-color: ${colors.interactiveHover};
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 3px;
  }
`;

export const PrimaryActionLink = styled(ActionLink)`
  border: 0;
  background-color: ${colors.primary};
  color: ${colors.textInverse};

  @media (hover: hover) {
    &:hover {
      background-color: ${colors.primaryPressed};
    }
  }

  &:active {
    background-color: ${colors.primaryPressed};
  }
`;
