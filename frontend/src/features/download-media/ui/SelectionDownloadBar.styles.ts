import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

/**
 * 바가 뜨는 자리. `FloatingBar` 는 알약 모양만 그리고 위치는 모른다 —
 * 업로드 진행 바도 같은 모양을 쓰기 때문에, 어디에 뜰지는 쓰는 쪽이 정한다.
 */
export const Dock = styled.div`
  position: fixed;
  /* 아이폰 홈 인디케이터에 가리지 않게 안전 영역만큼 더 띄운다. */
  bottom: calc(36px + env(safe-area-inset-bottom, 0px));
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  justify-content: center;
  width: 100%;
  max-width: 480px;
  padding: 0 ${spacing[16]};
  z-index: 900;
  pointer-events: none;

  > * {
    pointer-events: auto;
  }

  @media (min-width: 768px) {
    max-width: 1180px;
  }
`;

export const Count = styled.span`
  flex-shrink: 0;
  ${typography.caption2}
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

export const Status = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${spacing[8]};
  flex: 1;
  min-width: 0;
  height: 40px;
  padding: 0 14px;
  border-radius: ${radius.full};
  background: ${colors.primarySubtle};
  ${typography.caption2}
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  color: ${colors.textAccent};

  svg {
    flex: none;
    width: 14px;
    height: 14px;
  }

  .spin {
    animation: spin 0.7s linear infinite;
  }

  @media (prefers-reduced-motion: reduce) {
    .spin {
      animation: none;
    }
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
`;

export const DownloadButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  justify-self: center;
  width: 132px;
  height: 40px;
  padding: 0 ${spacing[16]};
  border-radius: ${radius[12]};
  /* 좁은 폭에서 "zip 다운로드" 가 두 줄로 갈라지면 바 높이가 통째로 흔들린다. */
  white-space: nowrap;
  background: ${colors.primary};
  ${typography.label5}
  font-size: 12px;
  color: ${colors.textInverse};

  &:hover,
  &:active {
    background: ${colors.primaryPressed};
  }

  svg {
    flex: none;
    width: 18px;
    height: 18px;
  }

  @media (max-width: 360px) {
    gap: ${spacing[4]};
    width: 124px;
    padding-inline: ${spacing[8]};
    font-size: 12px;
  }
`;

export const PlainButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${spacing[4]};
  flex-shrink: 0;
  min-width: 24px;
  min-height: 40px;
  border-radius: ${radius[12]};
  white-space: nowrap;
  color: ${colors.textStrong};
  font-size: 12px;
  font-weight: 600;
  line-height: 16px;

  &:hover {
    background: ${colors.interactiveHover};
  }

  svg {
    width: 18px;
    height: 18px;
  }

  @media (max-width: 360px) {
    font-size: 11px;
  }
`;
