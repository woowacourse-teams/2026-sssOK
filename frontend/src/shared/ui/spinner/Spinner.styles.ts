import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";

import { colors } from "@/shared/styles/tokens";
import type { SpinnerProps } from "./Spinner";

const spin = keyframes`
  to {
    transform: rotate(360deg);
  }
`;

const SIZE = { md: 28, lg: 36 } as const;

export const Status = styled.div`
  display: inline-flex;
  align-items: center;
  justify-content: center;
`;

export const Ring = styled.span<{
  size: NonNullable<SpinnerProps["size"]>;
  tone: NonNullable<SpinnerProps["tone"]>;
}>`
  display: block;
  width: ${({ size }) => SIZE[size]}px;
  height: ${({ size }) => SIZE[size]}px;
  border: 3px solid
    ${({ tone }) => (tone === "inverse" ? "rgba(255, 255, 255, 0.2)" : colors.primarySubtle)};
  border-top-color: ${({ tone }) => (tone === "inverse" ? colors.textInverse : colors.primary)};
  border-radius: 50%;
  animation: ${spin} 0.8s linear infinite;

  @media (prefers-reduced-motion: reduce) {
    animation-duration: 2s;
  }
`;

export const HiddenLabel = styled.span`
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  border: 0;
  clip: rect(0 0 0 0);
  white-space: nowrap;
`;

export const FullPage = styled.main`
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 100%;
`;
