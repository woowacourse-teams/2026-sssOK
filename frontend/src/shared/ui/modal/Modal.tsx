import styled from "@emotion/styled";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";
import { LuX } from "react-icons/lu";
import { colors, radius, shadow, spacing } from "@/shared/styles/tokens";

export interface ModalProps {
  onClose: () => void;
  /**
   * 오른쪽 위 X 를 그릴지. 기본은 그린다.
   *
   * 카드 안에 `닫기`·`확인` 같은 나갈 길이 이미 있으면 끄는 쪽이 낫다 —
   * 같은 일을 하는 버튼이 둘이면 어느 쪽을 눌러야 하는지 잠깐 생각하게 된다.
   * 바깥을 눌러 닫는 길은 이 값과 상관없이 그대로 열려 있다.
   */
  showClose?: boolean;
  children: ReactNode;
}

export const Modal = ({ onClose, showClose = true, children }: ModalProps) => {
  return createPortal(
    <Overlay data-testid="modal-overlay" onClick={onClose}>
      <Card onClick={(event) => event.stopPropagation()}>
        {showClose && (
          <Header>
            <CloseButton type="button" onClick={onClose} aria-label="닫기">
              <LuX />
            </CloseButton>
          </Header>
        )}
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
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 330px;
  padding: ${spacing[20]};
  background: ${colors.backgroundDefault};
  border-radius: ${radius[24]};
  box-shadow: ${shadow.modal};
`;

const Header = styled.div`
  position: absolute;
  top: ${spacing[16]};
  right: ${spacing[16]};
  display: flex;
  align-items: center;
  justify-content: flex-end;
  z-index: 1;
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
