import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { FloatingBar } from "@/shared/ui/floating-bar";

/*
 * 실제 화면의 하단 바를 그대로 옮긴 스타일.
 * - 선택 바: features/download-media/ui/SelectionDownloadBar.styles.ts
 * - 토스트: shared/ui/toast/Toast.styles.ts
 * 저쪽 값을 바꾸면 이쪽도 함께 바꾼다. 위치(Dock)만 빼고 크기·색·글자는 같다.
 */

export const PREVIEW_BAR_HEIGHT = 66;
const PC_BAR_HEIGHT = 54;

const appear = keyframes`
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
 * 바가 뜨는 자리. 바가 바뀌거나 사라져도 위의 갤러리가 흔들리지 않게 높이를 잡아 둔다.
 * 남는 높이의 2/3 을 차지해 바가 그 한가운데에 놓이고, 나머지 1/3 은 갤러리 위(SceneTopSpace)로 간다.
 * 미리보기에서는 갤러리와 함께 한 칸에 들어가야 해서, 실제(68px)보다 조금 낮춘 66px 로 그린다.
 */
export const BarSlot = styled.div`
  display: flex;
  flex: 2;
  align-items: center;
  width: 100%;
  min-height: ${PREVIEW_BAR_HEIGHT}px;

  /* FloatingBar 의 좁은 화면 규칙(min-height: 68px)보다 앞서도록 한 단계 더 구체적으로 쓴다 */
  && > * {
    height: ${PREVIEW_BAR_HEIGHT}px;
    min-height: ${PREVIEW_BAR_HEIGHT}px;
    padding-block: 11px;
    animation: ${appear} 220ms ease-out both;
  }

  /* PC 는 장면을 크게 확대해 그려서 바가 두꺼워 보인다. 확대되는 만큼 낮춘다 */
  @media (min-width: 768px) {
    && > * {
      height: ${PC_BAR_HEIGHT}px;
      min-height: ${PC_BAR_HEIGHT}px;
      padding-block: 6px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    && > * {
      animation: none;
    }
  }
`;

/**
 * 키 큰 화면에서 남는 높이의 1/3 을 갤러리 위에 둬 갤러리를 조금 내린다.
 * 남는 높이가 없으면 장면의 gap(12px)만큼 겹쳐 들어가 자리를 차지하지 않는다.
 */
export const SceneTopSpace = styled.div`
  flex: 1;
  margin-bottom: -12px;
`;

/* ---------- 선택 바 ---------- */

export const Count = styled.span`
  flex-shrink: 0;
  ${typography.caption2}
  /* 미리보기는 장면째 줄여 그려서 실제(12px)보다 조금 키운다 */
  font-size: 13px;
  line-height: 18px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  color: ${colors.textStrong};
`;

export const SelectionLayout = styled.div`
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  width: 100%;
  gap: ${spacing[16]};
`;

export const SelectionSummary = styled.div`
  display: flex;
  align-items: center;
  gap: ${spacing[8]};
  justify-self: start;
`;

export const SelectionCheck = styled.span`
  display: grid;
  flex: none;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: ${radius.full};
  background: ${colors.primary};
  color: ${colors.textInverse};

  svg {
    width: 12px;
    height: 12px;
    stroke-width: 2.5;
  }
`;

export const ActionGroup = styled.div`
  display: flex;
  align-items: center;
  justify-self: end;
  gap: 0;
`;

/** `$pressed` 는 실제 버튼을 누르고 있는 모습(`:active`)이다 */
export const DownloadButton = styled.span<{ $pressed: boolean }>`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  justify-self: center;
  width: 132px;
  height: 40px;
  padding: 0 ${spacing[16]};
  border-radius: ${radius[12]};
  white-space: nowrap;
  background: ${({ $pressed }) => ($pressed ? colors.primaryPressed : colors.primary)};
  ${typography.label5}
  font-size: 13px;
  color: ${colors.textInverse};
  transform: ${({ $pressed }) => ($pressed ? "scale(0.96)" : "none")};
  transition: transform 90ms ease-out;

  svg {
    flex: none;
    width: 16px;
    height: 16px;
  }
`;

/* ---------- 토스트 ---------- */

export const ToastFrame = styled(FloatingBar)`
  gap: 10px;
  height: auto;
  max-width: 520px;
  padding-inline: 22px;
  border: 1.25px solid #e5ded1;
  box-shadow: 0 12.42px 24.84px rgba(41, 28, 20, 0.14);
`;

export const ToastIcon = styled.span`
  display: flex;
  flex: none;
  width: 22px;
  height: 22px;
  color: ${colors.primary};

  svg {
    width: 100%;
    height: 100%;
  }
`;

export const ToastText = styled.span`
  flex: 1;
  min-width: 0;
  color: ${colors.textStrong};
  letter-spacing: -0.01em;
  overflow-wrap: anywhere;

  ${typography.caption2}
  /* 미리보기는 장면째 줄여 그려서 실제(12px)보다 조금 키운다 */
  font-size: 14px;
  line-height: 20px;
`;

export const ToastClose = styled.span`
  flex: none;
  color: ${colors.textSecondary};

  ${typography.caption2}
  font-size: 13px;
`;
