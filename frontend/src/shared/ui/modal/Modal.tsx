import styled from "@emotion/styled";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { LuX } from "react-icons/lu";
import { colors, radius, shadow, spacing } from "@/shared/styles/tokens";

export interface ModalProps {
  onClose: () => void;
  children: ReactNode;
}

export const Modal = ({ onClose, children }: ModalProps) => {
  return createPortal(
    <Overlay data-testid="modal-overlay" onClick={onClose}>
      <Card onClick={(event) => event.stopPropagation()}>
        <Header>
          <CloseButton type="button" onClick={onClose} aria-label="닫기">
            <LuX />
          </CloseButton>
        </Header>
        <Body>{children}</Body>
      </Card>
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
  padding: ${spacing[20]};
  background: ${colors.overlay};
  z-index: 1000;
`;

const Card = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[16]};
  width: 100%;
  max-width: 330px;
  padding: ${spacing[20]};
  background: ${colors.backgroundDefault};
  border-radius: ${radius[16]};
  box-shadow: ${shadow.modal};
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: ${spacing[12]};
`;

const CloseButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  color: ${colors.textSecondary};

  svg {
    width: 100%;
    height: 100%;
  }
`;

const Body = styled.div`
  width: 100%;
`;
