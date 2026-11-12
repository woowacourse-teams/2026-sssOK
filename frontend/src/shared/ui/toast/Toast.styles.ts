import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { FloatingBar } from "@/shared/ui/floating-bar";

export const DEFAULT_TOAST_BOTTOM = "calc(36px + env(safe-area-inset-bottom, 0px))";

export const Dock = styled.div<{ $bottom: string }>`
  position: fixed;
  bottom: ${({ $bottom }) => $bottom};
  left: 50%;
  transform: translateX(-50%);
  z-index: 1100;
  display: flex;
  justify-content: center;
  width: 100%;
  max-width: 480px;
  padding: 0 ${spacing[16]};
  pointer-events: none;

  @media (min-width: 768px) {
    max-width: 1180px;
  }
`;

export const Frame = styled(FloatingBar)`
  gap: 10px;
  height: auto;
  min-height: 68px;
  max-width: 520px;
  padding: 22px;
  border: 1.25px solid #e5ded1;
  box-shadow: 0 12.42px 24.84px rgba(41, 28, 20, 0.14);
  pointer-events: auto;
`;

export const StatusIcon = styled.span<{ $tone: "success" | "error" }>`
  display: flex;
  flex: none;
  width: 22px;
  height: 22px;
  color: ${({ $tone }) => ($tone === "error" ? colors.danger : colors.primary)};

  svg {
    width: 100%;
    height: 100%;
  }
`;

export const Text = styled.span`
  flex: 1;
  min-width: 0;
  color: ${colors.textStrong};
  letter-spacing: -0.01em;
  overflow-wrap: anywhere;
  /* 문구 안의 \n 을 줄바꿈으로 보여 준다. */
  white-space: pre-line;

  ${typography.caption2}
`;

/* 아이콘은 18px 이지만 손가락으로 누를 자리는 32px 로 둔다. 바 높이가 늘지 않게 위아래로 당긴다. */
export const CloseButton = styled.button`
  display: grid;
  place-items: center;
  flex: none;
  width: 32px;
  height: 32px;
  margin: -7px -7px -7px 0;
  color: ${colors.textSecondary};
  border-radius: ${radius.full};

  svg {
    width: 18px;
    height: 18px;
  }

  &:hover {
    color: ${colors.textStrong};
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 4px;
  }
`;
