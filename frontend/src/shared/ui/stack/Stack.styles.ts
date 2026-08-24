import styled from "@emotion/styled";

import { spacing } from "@/shared/styles/tokens";
import type { LayoutProps } from "../layout.types";

export const StyledStack = styled.div<LayoutProps>`
  display: flex;
  flex-direction: column;

  gap: ${({ gap }) => (gap ? spacing[gap] : 0)};
  align-items: ${({ align = "stretch" }) => align};
  justify-content: ${({ justify = "flex-start" }) => justify};
`;
