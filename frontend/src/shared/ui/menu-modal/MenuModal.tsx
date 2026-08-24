import styled from "@emotion/styled";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { colors, spacing } from "@/shared/styles/tokens";

export interface MenuModalProps {
  onClose: () => void;
  children: ReactNode;
}

export const MenuModal = ({ onClose, children }: MenuModalProps) => {
  return createPortal(
    <Overlay data-testid="menu-modal-overlay" onClick={onClose}>
      <Card onClick={(event) => event.stopPropagation()}>{children}</Card>
    </Overlay>,
    document.body,
  );
};

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
`;

const Card = styled.div`
  display: inline-flex;
  flex-direction: column;
  min-width: 176px;
  padding: ${spacing[8]};
  background: ${colors.backgroundDefault};
  border: 1px solid ${colors.borderDefault};
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(43, 29, 20, 0.16);
`;
