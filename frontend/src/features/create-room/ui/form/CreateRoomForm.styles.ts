import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Form = styled.form`
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  padding: ${spacing[16]};
`;

export const SubmitArea = styled.div`
  width: 100%;
  margin-top: auto;
`;

export const SubmitError = styled.p`
  margin-bottom: ${spacing[8]};
  color: ${colors.danger};

  ${typography.label5}
`;
