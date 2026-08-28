import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

/**
 * 바가 뜨는 자리. `FloatingBar` 는 알약 모양만 그리고 위치는 모른다 —
 * 업로드 진행 바도 같은 모양을 쓰기 때문에, 어디에 뜰지는 쓰는 쪽이 정한다.
 */
export const Dock = styled.div`
  position: fixed;
  /* 아이폰 홈 인디케이터에 가리지 않게 안전 영역만큼 더 띄운다. */
  bottom: calc(${spacing[16]} + env(safe-area-inset-bottom, 0px));
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  justify-content: center;
  width: 100%;
  padding: 0 ${spacing[16]};
  z-index: 900;
`;

export const Count = styled.span`
  flex-shrink: 0;
  font-size: ${typography.label2.fontSize};
  font-weight: ${typography.label2.fontWeight};
  line-height: ${typography.label2.lineHeight};
  color: ${colors.textStrong};
`;

export const Status = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${spacing[4]};
  flex: 1;
  padding: ${spacing[8]} ${spacing[12]};
  border-radius: ${radius.full};
  background: ${colors.primarySubtle};
  font-size: ${typography.caption1.fontSize};
  font-weight: ${typography.caption1.fontWeight};
  line-height: ${typography.caption1.lineHeight};
  color: ${colors.textAccent};

  svg {
    width: 16px;
    height: 16px;
  }

  .spin {
    animation: spin 1s linear infinite;
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
  gap: ${spacing[8]};
  flex: 1;
  padding: ${spacing[12]} ${spacing[16]};
  border-radius: ${radius.full};
  /* 좁은 폭에서 "zip 다운로드" 가 두 줄로 갈라지면 바 높이가 통째로 흔들린다. */
  white-space: nowrap;
  background: ${colors.primary};
  font-size: ${typography.label2.fontSize};
  font-weight: ${typography.label2.fontWeight};
  line-height: ${typography.label2.lineHeight};
  color: ${colors.textInverse};

  &:active {
    background: ${colors.primaryPressed};
  }

  svg {
    width: 18px;
    height: 18px;
  }
`;

export const PlainButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${spacing[4]};
  flex-shrink: 0;
  white-space: nowrap;
  color: ${colors.textStrong};
  font-size: ${typography.label2.fontSize};
  font-weight: ${typography.label2.fontWeight};
  line-height: ${typography.label2.lineHeight};

  svg {
    width: 18px;
    height: 18px;
  }
`;
