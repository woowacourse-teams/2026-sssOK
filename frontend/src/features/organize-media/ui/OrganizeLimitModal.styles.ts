import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

/** 받기 실패 모달(`DownloadFailureModal`)과 같은 짜임이다. */
export const Content = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${spacing[12]};
  text-align: center;

  > button {
    margin-top: ${spacing[4]};
  }
`;

export const Actions = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[8]};
  width: 100%;
  margin-top: ${spacing[4]};
`;

export const Mascot = styled.img`
  width: 106px;
  height: auto;
`;

export const Title = styled.h2`
  color: ${colors.textStrong};

  ${typography.heading3}
`;

export const Description = styled.p`
  color: ${colors.textSecondary};

  ${typography.caption1}
`;
