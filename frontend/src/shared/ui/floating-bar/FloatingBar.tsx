import { type ReactNode } from "react";
import styled from "@emotion/styled";
import { colors, radius, spacing } from "@/shared/styles/tokens";

export interface FloatingBarProps {
  className?: string;
  children: ReactNode;
}

export const FloatingBar = ({ className, children }: FloatingBarProps) => {
  return <Bar className={className}>{children}</Bar>;
};

const Bar = styled.div`
  position: relative;
  display: flex;
  align-items: center;
  gap: ${spacing[8]};
  min-height: 68px;
  width: 100%;
  max-width: 520px;
  padding: 13px 22px;
  background: ${colors.backgroundDefault};
  border: 1.25px solid #e5ded1;
  border-radius: ${radius.full};
  box-shadow: 0 12.42px 24.84px rgba(41, 28, 20, 0.14);

  @media (max-width: 400px) {
    gap: 6px;
    padding-inline: ${spacing[12]};
  }
`;
