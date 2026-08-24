import styled from "@emotion/styled";
import { colors, spacing } from "@/shared/styles/tokens";

export const Divider = styled.hr`
  width: 100%;
  height: 1px;
  margin: ${spacing[8]} 0;
  border: 0;
  background: ${colors.borderDefault};
`;
