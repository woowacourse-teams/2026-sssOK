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

export const Controls = styled.div`
  display: flex;
  align-items: center;
  gap: ${spacing[16]};
`;

export const DropdownContainer = styled.div`
  position: relative;
  display: inline-flex;
`;

export const DropdownButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: ${spacing[8]};
  height: 45px;
  padding: 0 ${spacing[4]};
  color: ${colors.textSecondary};

  ${typography.caption2}

  svg {
    width: 16px;
    height: 16px;
  }
`;

export const MenuCheck = styled.span<{ $visible: boolean }>`
  display: grid;
  place-items: center;
  visibility: ${({ $visible }) => ($visible ? "visible" : "hidden")};
  color: ${colors.primary};
`;

export const SelectAllButton = styled.button<{ $active: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: ${spacing[8]};
  height: 45px;
  margin-left: auto;
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
