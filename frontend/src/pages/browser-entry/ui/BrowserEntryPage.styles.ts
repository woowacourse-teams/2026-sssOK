import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

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
  gap: ${spacing[16]};
  width: 100%;
  padding-top: ${spacing[24]};

  > button {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: ${spacing[12]};
    margin-top: ${spacing[24]};

    svg {
      width: 24px;
      height: 24px;
    }
  }
`;

export const InstallLabel = styled.p`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${spacing[8]};
  color: ${colors.textSecondary};
  ${typography.caption1}
`;

export const StoreLinks = styled.div`
  display: flex;
  gap: ${spacing[12]};
`;

export const StoreLink = styled.a`
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: ${spacing[8]};
  min-height: 48px;
  border: 1.25px solid ${colors.borderDefault};
  border-radius: ${radius[16]};
  color: ${colors.textStrong};
  ${typography.label3}

  svg {
    flex-shrink: 0;
    width: 22px;
    height: 22px;
  }

  @media (hover: hover) {
    &:hover {
      background-color: ${colors.interactiveHover};
    }
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 3px;
  }
`;
