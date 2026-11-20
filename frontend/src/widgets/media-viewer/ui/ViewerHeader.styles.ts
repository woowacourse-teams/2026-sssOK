import styled from "@emotion/styled";
import { ViewerButton } from "./ViewerControls.styles";

import { colors } from "@/shared/styles/tokens";

export const Header = styled.header`
  display: flex;
  flex: none;
  align-items: center;
  gap: 12px;
  padding: calc(14px + env(safe-area-inset-top, 0px)) 16px 14px;
`;

export const SelectionButton = styled(ViewerButton)`
  border-radius: 50%;

  &:disabled {
    cursor: default;
    opacity: 0.4;
  }

  &[aria-pressed="true"] {
    background: ${colors.primary};
  }
`;

export const Counter = styled.span`
  flex: 1;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
`;
