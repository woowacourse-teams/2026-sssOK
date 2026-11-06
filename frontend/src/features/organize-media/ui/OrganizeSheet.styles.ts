import styled from "@emotion/styled";

import { colors, radius, shadow, spacing } from "@/shared/styles/tokens";

/*
 * 사진 정리 시트들이 함께 쓰는 틀. 피그마 "AI-1 · 정리 방식 선택 시트"(558:5390)와
 * "G-3b · 나누기 멈추기 확인"(560:177) 값을 옮겼다.
 * 공용 BottomSheet 는 손잡이(50×5)·제목(18px)이 이 시안과 달라 쓰지 않는다.
 * 딤 색은 공용 토큰 `colors.overlay` 와 같다 (#1A1612, 58%).
 */

/**
 * 폰에서는 아래에서 올라오는 시트, PC(768px~)에서는 가운데 카드다.
 * 넓은 화면에서 시트를 끝까지 펼치면 두 카드가 가로로 길게 늘어나 그림이 작아 보인다.
 * PC 카드 크기는 공용 Modal 의 md(420px·모서리 24px·여백 28px)와 같다.
 */
const DESKTOP = "@media (min-width: 768px)";

export const Overlay = styled.div`
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: ${colors.overlay};
  z-index: 1000;

  ${DESKTOP} {
    align-items: center;
    padding: ${spacing[20]};
  }
`;

export const Dialog = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-height: 70vh;
  padding: 10px ${spacing[20]} calc(${spacing[20]} + env(safe-area-inset-bottom, 0px));
  overflow-y: auto;
  background: ${colors.backgroundDefault};
  border-radius: ${radius[24]} ${radius[24]} 0 0;

  ${DESKTOP} {
    max-width: 420px;
    max-height: 100%;
    padding: 28px;
    border-radius: ${radius[24]};
    box-shadow: ${shadow.modal};
  }
`;

/** 끌어내리는 손잡이. PC 카드에는 끌 일이 없어 숨긴다. */
export const Handle = styled.div`
  flex-shrink: 0;
  width: 36px;
  height: 4px;
  margin-bottom: ${spacing[16]};
  border-radius: ${radius.full};
  background: ${colors.borderDefault};

  ${DESKTOP} {
    display: none;
  }
`;

export const Title = styled.h2`
  width: 100%;
  font-size: 20px;
  font-weight: 700;
  line-height: 28px;
  color: ${colors.textStrong};
`;

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
