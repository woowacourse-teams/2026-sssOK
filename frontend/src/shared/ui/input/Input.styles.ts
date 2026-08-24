import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const Label = styled.label`
  color: ${colors.textSecondary};

  ${typography.caption3}
`;

export const StyledInput = styled.input<{ $hasError: boolean }>`
  width: 100%;
  height: 55px;
  padding: 0 ${spacing[16]};
  border: ${({ $hasError }) =>
    $hasError ? `2px solid ${colors.borderDanger}` : `1.25px solid ${colors.borderDefault}`};
  border-radius: ${radius[12]};
  outline: none;

  background-color: ${colors.backgroundDefault};
  color: ${colors.textStrong};
  transition: border-color 150ms ease;

  ${typography.label3}

  &::placeholder {
    color: ${colors.textSecondary};

    ${typography.body}
  }

  &:focus {
    border-width: 2px;
    border-color: ${({ $hasError }) => ($hasError ? colors.borderDanger : colors.borderPrimary)};
  }

  &:disabled {
    border-color: ${colors.borderDisabled};
    color: ${colors.textSecondary};
    cursor: not-allowed;
  }
`;

export const ErrorMessage = styled.span`
  min-width: 0;
  overflow: hidden;
  color: ${colors.danger};
  text-overflow: ellipsis;
  white-space: nowrap;

  ${typography.caption3}
`;

export const Count = styled.span`
  flex-shrink: 0;
  margin-left: auto;
  color: ${colors.textSecondary};

  ${typography.caption2}
`;
