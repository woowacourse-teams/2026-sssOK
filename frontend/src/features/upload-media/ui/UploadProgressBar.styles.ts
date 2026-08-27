import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";
import { Badge } from "@/shared/ui/badge";

/**
 * 화면 하단에 띄우는 자리. 모달(1000)보다 낮게 둔다 —
 * 업로드가 끝나고 실패 모달(#74)이 뜨면 그쪽이 위여야 한다.
 */
export const Dock = styled.div`
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  justify-content: center;
  /* 아이폰 홈 인디케이터에 바가 물리지 않게 한다. */
  padding: ${spacing[16]} ${spacing[16]} calc(${spacing[16]} + env(safe-area-inset-bottom));
  z-index: 900;
  /* 바 바깥은 갤러리가 그대로 눌려야 한다. */
  pointer-events: none;

  > * {
    pointer-events: auto;
  }
`;

export const Count = styled.span`
  flex-shrink: 0;
  color: ${colors.textStrong};

  ${typography.label2}
`;

/**
 * 진행률 알약. 색과 모양은 `Badge` 의 `soft` 변형을 그대로 쓴다 —
 * 이슈가 정한 `primarySubtle` 배경 + `textAccent` 글자가 곧 그 변형이고,
 * 채움이 알약 밖으로 새지 않게 하는 `overflow: hidden` 도 거기 딸려 온다 (#73).
 *
 * 덮어쓰는 것들은 배지로 안 되는 부분이다. Badge 는 내용만큼만 차지하고 `position` 이 없어서,
 * 그대로 두면 바를 가로지르지도 채움 띠를 담지도 못한다.
 * 높이와 글자 크기도 배지 기본값(24px·caption2)이 아니라 디자인에 맞춘 값이다.
 */
export const Status = styled(Badge)`
  position: relative;
  flex: 1;
  height: auto;
  gap: ${spacing[4]};
  padding: ${spacing[8]} ${spacing[12]};

  ${typography.caption1}

  svg {
    width: 16px;
    height: 16px;
  }
`;

/**
 * 퍼센트만큼 차오르는 띠. 숫자와 같은 값을 보지만 눈으로 먼저 읽힌다.
 * 너비는 진행률 이벤트마다 바뀌므로 클래스가 아니라 인라인 스타일로 준다.
 */
export const Fill = styled.div`
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  background: ${colors.primary};
  opacity: 0.24;
  transition: width 0.2s ease-out;
`;

/** 채움 위에 얹는다. 같은 칸을 쓰므로 쌓임 순서를 명시한다. */
export const StatusText = styled.span`
  position: relative;
  display: flex;
  align-items: center;
  gap: ${spacing[4]};
`;

export const CancelButton = styled.button`
  flex-shrink: 0;
  color: ${colors.textStrong};

  ${typography.label2}
`;

export const Spinner = styled.span`
  display: flex;

  svg {
    animation: spin 1s linear infinite;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
`;
