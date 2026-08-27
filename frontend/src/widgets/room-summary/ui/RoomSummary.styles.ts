import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Header = styled.header`
  flex: none;
  width: 100%;
  padding: ${spacing[12]} ${spacing[16]} 0;
`;

export const RemainingTimeText = styled.time<{ $urgent: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: ${spacing[4]};
  color: ${({ $urgent }) => ($urgent ? colors.danger : colors.textAccent)};

  ${typography.caption2}

  svg {
    width: 14px;
    height: 14px;
  }
`;

export const RoomTitle = styled.h1`
  flex: 1;
  min-width: 0;
  color: ${colors.textStrong};
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  ${typography.heading2}
`;
