import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { FloatingBar } from "@/shared/ui/floating-bar";

export const Dock = styled.div`
  position: fixed;
  bottom: calc(36px + env(safe-area-inset-bottom, 0px));
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

  ${typography.caption2}
`;

export const CloseButton = styled.button`
  flex: none;
  min-height: 24px;
  color: ${colors.textSecondary};
  border-radius: ${radius.full};

  ${typography.caption2}

  &:hover {
    color: ${colors.textStrong};
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 4px;
  }
`;
