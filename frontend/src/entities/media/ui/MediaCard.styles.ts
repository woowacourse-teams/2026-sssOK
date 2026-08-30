import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { MediaImage } from "./MediaImage";

export const Card = styled.div<{ $selected: boolean }>`
  position: relative;
  display: block;
  width: 100%;
  overflow: hidden;
  aspect-ratio: 8 / 9;
  border-radius: ${radius[12]};
  background-color: ${colors.borderDefault};

  &::after {
    content: "";
    position: absolute;
    inset: 0;
    border: 2.5px solid ${({ $selected }) => ($selected ? colors.primary : "transparent")};
    border-radius: ${radius[12]};
    pointer-events: none;
  }
`;

export const CardButton = styled.button`
  display: block;
  width: 100%;
  height: 100%;

  &:focus-visible {
    outline: 3px solid ${colors.primary};
    outline-offset: -3px;
  }
`;

export const Thumbnail = styled(MediaImage)`
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  pointer-events: none;
`;

export const SelectionMark = styled.button<{ $selected: boolean }>`
  position: absolute;
  top: ${spacing[8]};
  right: ${spacing[8]};
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: ${radius.full};
  background-color: ${({ $selected }) =>
    $selected ? colors.primary : "rgba(255, 255, 255, 0.88)"};
  color: ${({ $selected }) => ($selected ? colors.textInverse : colors.textSecondary)};

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 3px;
  }

  svg {
    width: 15px;
    height: 15px;
  }
`;

export const UploaderBadge = styled.span<{ $mine: boolean }>`
  position: absolute;
  bottom: ${spacing[8]};
  left: ${spacing[8]};
  max-width: calc(100% - ${spacing[16]});
  height: 22px;
  padding: 3px 9px;
  overflow: hidden;
  border-radius: ${radius.full};
  background-color: ${({ $mine }) => ($mine ? colors.primary : "rgba(255, 255, 255, 0.94)")};
  color: ${({ $mine }) => ($mine ? colors.textInverse : colors.textPrimary)};
  text-overflow: ellipsis;
  white-space: nowrap;

  ${typography.caption4}
`;

export const PlayMark = styled.span`
  position: absolute;
  inset: 0;
  display: grid;
  width: 42px;
  height: 42px;
  margin: auto;
  place-items: center;
  border-radius: ${radius.full};
  background-color: ${colors.overlay};
  color: ${colors.textInverse};
  pointer-events: none;

  svg {
    width: 20px;
    height: 20px;
  }
`;

export const Duration = styled.span`
  position: absolute;
  right: ${spacing[8]};
  bottom: ${spacing[8]};
  padding: 2px 6px;
  border-radius: 6px;
  background-color: ${colors.overlay};
  color: ${colors.textInverse};
  font-variant-numeric: tabular-nums;

  ${typography.caption4}
`;
