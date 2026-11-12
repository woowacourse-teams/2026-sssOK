import styled from "@emotion/styled";

import { colors, radius, spacing } from "@/shared/styles/tokens";

// 정리 시트의 본문과 버튼. 시트 틀과 반응형은 공용 BottomSheet가 담당한다.
export const Submit = styled.button`
  width: 100%;
  height: 56px;
  border-radius: ${radius[16]};
  background: ${colors.primary};
  font-size: 16px;
  font-weight: 700;
  color: ${colors.textInverse};

  &:active:not(:disabled) {
    background: ${colors.primaryPressed};
  }

  &:disabled {
    background: ${colors.disabled};
    cursor: not-allowed;
  }
`;

export const Later = styled.button`
  width: 100%;
  height: 44px;
  margin-top: ${spacing[4]};
  border-radius: ${radius[16]};
  font-size: 15px;
  font-weight: 700;
  color: ${colors.textSecondary};
`;

/**
 * 버리는 쪽 테두리 버튼(비슷한 사진 G-S3b · 660:2731). 테두리·색은 공용 Button 의 default 와 같고,
 * 크기는 위 주황 버튼(`Submit`)과 맞춘다. 두 버튼은 8px 띄운다.
 */
export const Stop = styled.button`
  width: 100%;
  height: 56px;
  margin-top: ${spacing[8]};
  border: 1.25px solid ${colors.borderDefault};
  border-radius: ${radius[16]};
  background: ${colors.backgroundDefault};
  font-size: 16px;
  font-weight: 700;
  color: ${colors.textStrong};

  &:active {
    background: ${colors.interactiveHover};
  }
`;

/** 제목 아래 본문 한 줄(G-3b). 15px, 위 6px·아래 20px 여백. */
export const Body = styled.p`
  width: 100%;
  padding: 6px 0 ${spacing[20]};
  font-size: 15px;
  font-weight: 500;
  line-height: 22px;
  color: ${colors.textPrimary};
`;
