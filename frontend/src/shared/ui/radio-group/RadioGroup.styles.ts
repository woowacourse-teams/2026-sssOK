import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const RadioGroupLabel = styled.span`
  color: ${colors.textSecondary};

  ${typography.caption3}
`;

export const RadioGroupContainer = styled.div<{ $columnCount: number }>`
  display: grid;
  grid-template-columns: repeat(${({ $columnCount }) => $columnCount}, minmax(0, 1fr));
  gap: ${spacing[12]};
  width: 100%;
`;

export const OptionLabel = styled.label`
  position: relative;
  min-width: 0;
  cursor: pointer;

  > input {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    overflow: hidden;
    border: 0;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }

  &:hover > input:not(:disabled) + span {
    background-color: ${colors.interactiveHover};
  }

  > input:checked + span {
    border-color: ${colors.borderPrimary};
    box-shadow: inset 0 0 0 1px ${colors.borderPrimary};
    color: ${colors.textAccent};
    ${typography.label4};
  }

  > input:focus-visible + span {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: 2px;
  }
`;

export const Option = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 50px;
  padding: 0 ${spacing[12]};
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius[12]};

  background-color: ${colors.backgroundDefault};
  color: ${colors.textSecondary};
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    box-shadow 150ms ease,
    color 150ms ease;

  ${typography.body}
`;
