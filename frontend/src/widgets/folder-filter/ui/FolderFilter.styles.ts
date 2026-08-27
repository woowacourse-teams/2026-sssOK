import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const FolderList = styled.div`
  display: flex;
  flex: none;
  align-items: center;
  gap: ${spacing[16]};
  width: 100%;
  padding: ${spacing[12]} ${spacing[16]};
  color: ${colors.textPrimary};
  overflow-x: auto;
  scrollbar-width: none;

  ${typography.caption1}

  &::-webkit-scrollbar {
    display: none;
  }

  svg {
    width: 18px;
    height: 18px;
  }
`;

export const FolderButton = styled.button<{ $active: boolean }>`
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: ${spacing[8]};
  padding: ${spacing[4]};
  color: ${({ $active }) => ($active ? colors.textStrong : colors.textSecondary)};
  white-space: nowrap;

  ${({ $active }) => ($active ? typography.label5 : typography.caption1)}
`;

export const Count = styled.span<{ $active: boolean }>`
  display: grid;
  place-items: center;
  min-width: 22px;
  height: 22px;
  padding: 0 ${spacing[4]};
  border-radius: ${radius.full};
  background-color: ${({ $active }) => ($active ? colors.primarySubtle : "transparent")};
  color: ${({ $active }) => ($active ? colors.textAccent : colors.textSecondary)};

  ${typography.caption2}
`;
