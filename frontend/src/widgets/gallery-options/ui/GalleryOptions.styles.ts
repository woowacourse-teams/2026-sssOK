import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const Options = styled.section`
  position: relative;
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 45px;
  padding: 0 ${spacing[16]};

  &::before {
    content: "";
    position: absolute;
    top: 0;
    right: ${spacing[16]};
    left: ${spacing[16]};
    height: 1px;
    background-color: ${colors.borderDefault};
  }
`;

export const OptionButton = styled.button<{ $active?: boolean }>`
  min-width: 32px;
  position: relative;
  height: 45px;
  padding: 0;
  color: ${({ $active }) => ($active ? colors.textAccent : colors.textSecondary)};

  ${({ $active }) => ($active ? typography.caption2 : typography.caption3)}

  &::after {
    content: ${({ $active }) => ($active ? '""' : "none")};
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 2px;
    border-radius: 2px;
    background-color: ${colors.primary};
  }
`;

export const SelectAllButton = styled.button<{ $active: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: ${spacing[8]};
  height: 45px;
  padding: 0;
  color: ${({ $active }) => ($active ? colors.textAccent : colors.textSecondary)};

  ${typography.caption3}

  &:disabled {
    cursor: default;
  }
`;

export const SelectMark = styled.span<{ $active: boolean }>`
  display: grid;
  place-items: center;
  width: 21px;
  height: 21px;
  border: 1px solid ${({ $active }) => ($active ? colors.primary : colors.borderDisabled)};
  border-radius: ${radius.full};
  background-color: ${({ $active }) => ($active ? colors.primary : "transparent")};
  color: ${({ $active }) => ($active ? colors.textInverse : colors.borderDisabled)};

  svg {
    width: 13px;
    height: 13px;
  }
`;
