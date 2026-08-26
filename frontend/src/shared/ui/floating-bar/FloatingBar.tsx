import { type ReactNode } from "react";
import styled from "@emotion/styled";
import { colors, radius, shadow, spacing } from "@/shared/styles/tokens";

export interface FloatingBarProps {
  children: ReactNode;
}

export const FloatingBar = ({ children }: FloatingBarProps) => {
  return <Bar>{children}</Bar>;
};

const Bar = styled.div`
  display: flex;
  align-items: center;
  gap: ${spacing[12]};
  height: 68px;
  width: 100%;
  max-width: 350px;
  padding: ${spacing[12]} ${spacing[16]};
  background: ${colors.backgroundDefault};
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius.full};
  box-shadow: ${shadow.toast};
`;
