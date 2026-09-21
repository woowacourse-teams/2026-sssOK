import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

/** 디자인의 정보 표 글자(13px). 같은 크기의 토큰이 아직 없다. */
const metaText = {
  fontSize: "13px",
  fontWeight: 500,
  lineHeight: "20px",
} as const;

export const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[20]};
`;

export const Top = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${spacing[12]};
`;

export const Title = styled.h2`
  color: ${colors.textStrong};

  ${typography.heading3}
`;

export const CloseButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  color: ${colors.textSecondary};

  svg {
    width: 100%;
    height: 100%;
  }
`;

export const Body = styled.p`
  color: ${colors.textStrong};
  /* 전문은 줄바꿈까지 쓴 그대로 읽어야 한다. 긴 URL 이 모달 밖으로 새지 않게 끊는다. */
  white-space: pre-wrap;
  overflow-wrap: anywhere;

  ${typography.body}
`;

export const MetaList = styled.dl`
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: ${spacing[16]};
  border-radius: ${radius[12]};
  background: ${colors.backgroundSubtle};
`;

export const MetaRow = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${spacing[12]};
`;

export const MetaLabel = styled.dt`
  width: 72px;
  flex-shrink: 0;
  color: ${colors.textSecondary};

  ${metaText}
`;

export const MetaValue = styled.dd`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: ${spacing[4]};
  min-width: 0;
  color: ${colors.textStrong};

  ${metaText}
`;

/** 해석하기 전의 UA 원본. 기기를 정확히 가려야 할 때 읽는다 — 자르지 않는다. */
export const RawUserAgent = styled.span`
  color: ${colors.textSecondary};
  overflow-wrap: anywhere;

  ${typography.caption3}
`;

export const Notice = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: ${spacing[12]};
  color: ${colors.textSecondary};

  ${typography.body}
`;
