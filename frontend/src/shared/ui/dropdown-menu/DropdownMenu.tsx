import styled from "@emotion/styled";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import { colors, radius, shadow, spacing, typography } from "@/shared/styles/tokens";

export interface DropdownMenuProps {
  onClose: () => void;
  children: ReactNode;
}

export const DropdownMenu = ({ onClose, children }: DropdownMenuProps) => {
  return (
    <>
      <Overlay data-testid="dropdown-menu-overlay" onClick={onClose} />
      <Card data-testid="dropdown-menu">{children}</Card>
    </>
  );
};

interface DropdownMenuItemProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon?: ReactNode;
  tone?: "default" | "danger";
}

export const DropdownMenuItem = ({
  icon,
  tone = "default",
  type = "button",
  children,
  ...props
}: DropdownMenuItemProps) => {
  return (
    <Item type={type} $tone={tone} {...props}>
      {icon}
      <span>{children}</span>
    </Item>
  );
};

export const DropdownMenuDivider = styled.hr`
  width: 100%;
  margin: ${spacing[8]} 0;
  border: 0;
  border-top: 1px solid ${colors.borderDefault};
`;

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  z-index: 1000;
`;

const Card = styled.div`
  position: absolute;
  top: calc(100% + ${spacing[8]});
  right: 0;
  z-index: 1001;
  display: inline-flex;
  flex-direction: column;
  min-width: 176px;
  padding: ${spacing[8]};
  background: ${colors.backgroundDefault};
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius[16]};
  box-shadow: ${shadow.modal};
`;

const Item = styled.button<{ $tone: "default" | "danger" }>`
  display: flex;
  align-items: center;
  gap: ${spacing[12]};
  width: 100%;
  padding: 11px ${spacing[12]};
  border-radius: 10px;
  color: ${({ $tone }) => ($tone === "danger" ? colors.danger : colors.textStrong)};
  text-align: left;

  ${typography.label5}

  svg {
    flex: none;
    width: 20px;
    height: 20px;
  }

  &:hover {
    background-color: ${({ $tone }) =>
      $tone === "danger"
        ? `color-mix(in srgb, ${colors.danger} 10%, white)`
        : colors.primarySubtle};
    color: ${({ $tone }) => ($tone === "danger" ? colors.danger : colors.primary)};
  }

  &:disabled {
    background: transparent;
    color: ${colors.disabled};
    cursor: not-allowed;
  }
`;
