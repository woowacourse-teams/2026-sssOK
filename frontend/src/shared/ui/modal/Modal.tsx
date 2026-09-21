import styled from "@emotion/styled";
import { useEffect, type ReactNode } from "react";
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
  /**
   * 카드 너비. 기본(`sm`)은 모바일 확인 모달 크기다.
   * `lg` 는 관리자 화면처럼 긴 본문과 표를 한 번에 읽어야 하는 자리에 쓴다.
   */
  size?: "sm" | "lg";
  children: ReactNode;
}

export const Modal = ({ onClose, showClose = true, size = "sm", children }: ModalProps) => {
  // 바깥 클릭과 같은 길이다. 닫으면 안 되는 순간은 부르는 쪽이 onClose 를 비워서 막는다.
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  return createPortal(
    <Overlay data-testid="modal-overlay" onClick={onClose}>
      <Card
        role="dialog"
        aria-modal="true"
        size={size}
        onClick={(event) => event.stopPropagation()}
      >
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

const Card = styled.div<{ size: NonNullable<ModalProps["size"]> }>`
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: ${({ size }) => (size === "lg" ? "560px" : "330px")};
  max-height: 100%;
  /* 카드는 화면 높이에서 멈추고, 넘치는 내용은 Body 가 스크롤한다. X 는 제자리에 남는다. */
  overflow: hidden;
  padding: ${({ size }) => (size === "lg" ? "28px" : spacing[20])};
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
  /* flex 자식은 기본 최소 높이가 내용 높이라, 0 으로 풀어야 카드 안에서 줄어들며 스크롤된다. */
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  /* 끝까지 내린 뒤의 스크롤이 뒤 화면으로 넘어가지 않게 막는다. 닫았을 때 보던 자리가 그대로여야 한다. */
  overscroll-behavior: contain;
`;
