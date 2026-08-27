import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { Button } from "@/shared/ui/button";

/** 프로토타입 .fab 간격을 유지하면서 스크롤 및 앱 컨테이너 폭에 대응한다. */
export const Dock = styled.div`
  position: fixed;
  bottom: calc(44px + env(safe-area-inset-bottom, 0px));
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  justify-content: flex-end;
  width: 100%;
  max-width: 480px;
  padding: 0 ${spacing[16]};
  z-index: 800;
  pointer-events: none;

  @media (min-width: 768px) {
    max-width: 1180px;
  }
`;

export const FloatingUploadButton = styled(Button)`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  gap: 6px;
  width: auto;
  height: 50px;
  padding: 0 ${spacing[24]};
  border-radius: ${radius.full};
  box-shadow: none;
  white-space: nowrap;
  pointer-events: auto;
  transition:
    background-color 220ms cubic-bezier(0.22, 0.61, 0.36, 1),
    transform 90ms cubic-bezier(0.22, 0.61, 0.36, 1);

  ${typography.label2}

  &:active:not(:disabled) {
    transform: scale(0.97);
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 4px;
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;

export const HiddenFileInput = styled.input`
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  border: 0;
  margin: -1px;
  clip-path: inset(50%);
  overflow: hidden;
  white-space: nowrap;
`;
