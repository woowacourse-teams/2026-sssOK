import styled from "@emotion/styled";

import { colors, radius, spacing } from "@/shared/styles/tokens";

export const Dock = styled.div`
  position: fixed;
  bottom: calc(44px + env(safe-area-inset-bottom, 0px));
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  justify-content: flex-start;
  width: 100%;
  max-width: 480px;
  padding: 0 ${spacing[16]};
  z-index: 800;
  pointer-events: none;

  @media (min-width: 768px) {
    max-width: 1180px;
  }
`;

export const FloatingFeedbackButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 0;
  border-radius: ${radius.full};
  background: ${colors.primarySubtle};
  color: ${colors.textAccent};
  opacity: 0.9;
  pointer-events: auto;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    transform 90ms ease;

  svg {
    width: 19px;
    height: 19px;
  }

  &:hover {
    background: ${colors.interactiveHover};
  }

  &:active {
    transform: scale(0.96);
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 4px;
  }
`;
