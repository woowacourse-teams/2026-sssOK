import { css, keyframes } from "@emotion/react";
import styled from "@emotion/styled";

import { spacing } from "@/shared/styles/tokens";

const fadeUp = keyframes`
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: none;
  }
`;

/**
 * 처음 들어왔을 때 헤드라인 → 미리보기 → 버튼 순으로 짧게 나타나 읽는 순서를 잡아 준다.
 * 다 합쳐 0.65초 안에 끝나 "3초 안에 이해" 를 늦추지 않는다.
 * 캐릭터 인트로가 있으면 섹션이 `--enter-offset` 만큼 늦춰, 인트로가 걷힐 때 나타나게 한다.
 */
const enter = (delayMs: number) => css`
  animation: ${fadeUp} 400ms cubic-bezier(0.22, 0.61, 0.36, 1)
    calc(var(--enter-offset, 0ms) + ${delayMs}ms) both;

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }
`;

/**
 * 휴대폰에서는 한 화면 폭으로 위에서 아래로 쌓는다.
 * 넓은 화면에서는 왼쪽에 헤드라인과 버튼, 오른쪽에 미리보기를 나란히 둔다.
 */
export const Section = styled.section<{ enterOffsetMs: number }>`
  --enter-offset: ${({ enterOffsetMs }) => enterOffsetMs}ms;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  gap: ${spacing[20]};
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
  padding: ${spacing[24]} ${spacing[16]} ${spacing[16]};

  @media (min-width: 768px) {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 460px);
    grid-template-areas:
      "intro preview"
      "actions preview";
    grid-template-rows: auto auto;
    align-content: center;
    column-gap: 64px;
    row-gap: ${spacing[32]};
    max-width: 1040px;
    padding: ${spacing[32]};
  }
`;

export const IntroArea = styled.div`
  ${enter(0)}
  @media (min-width: 768px) {
    grid-area: intro;
    align-self: end;
  }
`;

/**
 * 미리보기 자리. 휴대폰에서는 화면 높이만큼 늘어나되, 키 큰 화면에서 그림이 다 채우지 못할
 * 만큼 길어지지 않게 막는다. 남는 높이는 섹션이 위아래로 나눠 가진다.
 * 넓은 화면에서는 오른쪽 단을 정해진 높이로 채운다.
 */
export const PreviewArea = styled.div`
  ${enter(120)}
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 280px;
  max-height: 480px;

  /* PC(마우스로 쓰는 기기)는 화면이 길어 칸이 끝까지 늘면 비어 보인다. 휴대폰은 그대로 둔다 */
  @media (hover: hover) and (pointer: fine) {
    max-height: 400px;
  }

  @media (min-width: 768px) {
    grid-area: preview;
    height: 540px;
    max-height: none;
  }
`;

export const ActionGroup = styled.div`
  ${enter(240)}
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${spacing[4]};
  width: 100%;

  @media (min-width: 768px) {
    grid-area: actions;
    align-self: start;
    align-items: flex-start;
    max-width: 360px;
  }
`;
