import styled from "@emotion/styled";

import { colors, radius, spacing } from "@/shared/styles/tokens";
import { Badge } from "@/shared/ui/badge";

/*
 * 피그마 "AI 사람별로 나누기" 의 하단 바 값을 옮겼다.
 * G-3 나누는 중(558:5531) · G-4 완료(558:5625) · G-4b 실패(558:5719) · G-4c 사람 없음(558:5816)
 * 바 틀은 업로드 진행 바와 같은 공용 FloatingBar 다(358×68, 가는 테두리, 그림자).
 */

/** 업로드 진행 바(`UploadProgressBar`)와 같은 자리다. 바닥에서 36px 위. */
export const Dock = styled.div`
  position: fixed;
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

const bold12 = `
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
`;

export const Count = styled.span`
  flex: 1;
  color: ${colors.textStrong};
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  ${bold12}
`;

/** 가운데 진행 알약. 연한 주황 바탕(primarySubtle)에 주황 글자다. */
export const Status = styled(Badge)`
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  min-width: 138px;
  height: 40px;
  gap: ${spacing[8]};
  padding: 0 14px;
  font-variant-numeric: tabular-nums;
  ${bold12}

  img {
    flex-shrink: 0;
  }
`;

export const Fill = styled.div`
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  background: ${colors.primary};
  opacity: 0.18;
  transition: width 200ms linear;

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;

export const StatusText = styled.span`
  position: relative;
  display: flex;
  align-items: center;
  gap: ${spacing[8]};
`;

export const Spinner = styled.img`
  display: block;
  animation: organize-spin 0.7s linear infinite;

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }

  @keyframes organize-spin {
    to {
      transform: rotate(360deg);
    }
  }
`;

export const CancelButton = styled.button`
  flex: 1;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  min-height: 40px;
  color: ${colors.textStrong};
  ${bold12}

  &:hover {
    color: ${colors.primaryPressed};
  }
`;

/**
 * 상태 아이콘 22px. 피그마는 원(20px)을 오른쪽 아래로 2px 붙이고 그 위에 표시를 얹는다.
 * 각 조각은 피그마에서 받은 SVG 를 그 자리 그대로 둔다.
 */
export const StateIcon = styled.span`
  position: relative;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  overflow: hidden;

  img {
    position: absolute;
    display: block;
    max-width: none;
  }
`;

/** 완료 문구는 14px, 실패·사람 없음 문구는 12px 이다(시안 그대로). */
export const Message = styled.p<{ $size: 12 | 14 }>`
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: ${colors.textStrong};
  font-size: ${({ $size }) => $size}px;
  font-weight: 700;
  line-height: ${({ $size }) => ($size === 14 ? 20 : 16)}px;
  letter-spacing: ${({ $size }) => -$size / 100}px;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const ActionButton = styled.button`
  flex-shrink: 0;
  height: 40px;
  padding: 0 ${spacing[16]};
  border-radius: ${radius.full};
  background: ${colors.primary};
  color: ${colors.textInverse};
  font-size: 14px;
  font-weight: 700;
  line-height: 16px;
  white-space: nowrap;

  &:active {
    background: ${colors.primaryPressed};
  }
`;

export const CloseButton = styled.button`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  min-height: 40px;

  img {
    display: block;
  }
`;
