import { css } from "@emotion/react";
import styled from "@emotion/styled";

import { colors, typography } from "@/shared/styles/tokens";

const THUMB_SIZE = 28;
const TRACK_HEIGHT = 8;

const alongThumb = (percent: number) =>
  `calc(${percent}% + ${THUMB_SIZE / 2 - (THUMB_SIZE * percent) / 100}px)`;

export const Label = styled.label`
  color: ${colors.textSecondary};

  ${typography.caption3}

  @media (min-width: 768px) {
    ${typography.caption1}
  }
`;

export const ValueRow = styled.div`
  position: relative;
  height: 24px;
`;

export const Value = styled.span<{ $percent: number }>`
  position: absolute;
  bottom: 0;
  left: ${({ $percent }) => alongThumb($percent)};
  color: ${colors.textAccent};
  font-variant-numeric: tabular-nums;
  transform: translateX(-50%);
  white-space: nowrap;

  ${typography.label3}
`;

export const Track = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const thumb = css`
  width: ${THUMB_SIZE}px;
  height: ${THUMB_SIZE}px;
  border: 1px solid rgba(0, 0, 0, 0.16);
  border-radius: 50%;

  background-color: ${colors.backgroundDefault};
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.16);
  cursor: grab;
  transition: box-shadow 150ms ease;
`;

// 또렷한 테두리 대신 번지는 그림자로 동그라미 둘레가 은은하게 퍼져 나가게 한다.
const thumbHighlighted = css`
  box-shadow:
    0 0 16px 6px rgba(246, 109, 59, 0.5),
    0 2px 6px rgba(0, 0, 0, 0.16);
`;

export const StyledSlider = styled.input<{ $progress: number }>`
  display: block;
  width: 100%;
  height: ${THUMB_SIZE}px;
  margin: 0;
  background: transparent;
  cursor: pointer;
  appearance: none;
  touch-action: pan-y;
  -webkit-tap-highlight-color: transparent;

  &::-webkit-slider-runnable-track {
    height: ${TRACK_HEIGHT}px;
    border-radius: ${TRACK_HEIGHT / 2}px;
    background: linear-gradient(
      to right,
      ${colors.primary} ${({ $progress }) => $progress}%,
      ${colors.borderDefault} ${({ $progress }) => $progress}%
    );
  }

  &::-webkit-slider-thumb {
    ${thumb}
    margin-top: ${(TRACK_HEIGHT - THUMB_SIZE) / 2}px;
    appearance: none;
  }

  &::-moz-range-track {
    height: ${TRACK_HEIGHT}px;
    border-radius: ${TRACK_HEIGHT / 2}px;
    background-color: ${colors.borderDefault};
  }

  &::-moz-range-progress {
    height: ${TRACK_HEIGHT}px;
    border-radius: ${TRACK_HEIGHT / 2}px;
    background-color: ${colors.primary};
  }

  &::-moz-range-thumb {
    ${thumb}
  }

  &:focus-visible {
    outline: none;
  }

  &:active::-webkit-slider-thumb,
  &:focus-visible::-webkit-slider-thumb {
    ${thumbHighlighted}
    cursor: grabbing;
  }

  &:active::-moz-range-thumb,
  &:focus-visible::-moz-range-thumb {
    ${thumbHighlighted}
    cursor: grabbing;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }
`;

export const Marks = styled.div`
  position: relative;
  height: 20px;
`;

export const Mark = styled.span<{ $percent: number; $disabled: boolean }>`
  position: absolute;
  top: 0;
  left: ${({ $percent }) => alongThumb($percent)};
  color: ${colors.textSecondary};
  cursor: ${({ $disabled }) => ($disabled ? "not-allowed" : "pointer")};
  font-variant-numeric: tabular-nums;
  transform: translateX(-50%);
  user-select: none;
  white-space: nowrap;

  ${typography.caption1}
  font-weight: 500;
`;
