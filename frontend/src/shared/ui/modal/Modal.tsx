import styled from "@emotion/styled";
import { useEffect, useId, useRef, type ReactNode } from "react";
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
  /**
   * 넘기면 카드 맨 위에 제목과 X 를 한 줄로 두고, 본문이 길어도 이 줄은 스크롤되지 않는다.
   * 긴 내용을 내려 읽다가도 닫을 곳을 찾으러 다시 올라갈 필요가 없다.
   */
  title?: ReactNode;
  container?: Element | null;
  children: ReactNode;
  labelledBy?: string;
  describedBy?: string;
}

export const Modal = ({
  onClose,
  showClose = true,
  size = "sm",
  title,
  container,
  children,
  labelledBy,
  describedBy,
}: ModalProps) => {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const generatedTitleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    if (!labelledBy) {
      const heading = dialog.querySelector("h1, h2, h3");
      if (heading) {
        if (!heading.id) heading.id = generatedTitleId;
        dialog.setAttribute("aria-labelledby", heading.id);
      }
    }
    dialog.showModal();
    dialog.focus({ preventScroll: true });
    document.body.style.overflow = "hidden";

    return () => {
      dialog.close();
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus({ preventScroll: true });
      }
    };
  }, [generatedTitleId, labelledBy]);

  return createPortal(
    <Overlay
      ref={dialogRef}
      tabIndex={-1}
      aria-modal="true"
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      data-testid="modal-overlay"
      onClick={onClose}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          event.preventDefault();
          event.stopPropagation();
          onClose();
        }
        if (event.key !== "Tab") return;
        const dialog = event.currentTarget;
        const controls = Array.from(
          dialog.querySelectorAll<HTMLElement>(
            'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
          ),
        ).filter((element) => !element.closest('[hidden], [inert], [aria-hidden="true"]'));
        const first = controls[0];
        const last = controls[controls.length - 1];
        if (!first) {
          event.preventDefault();
          dialog.focus();
        } else if (
          event.shiftKey &&
          (document.activeElement === first || document.activeElement === dialog)
        ) {
          event.preventDefault();
          last.focus();
        } else if (
          !event.shiftKey &&
          (document.activeElement === last || document.activeElement === dialog)
        ) {
          event.preventDefault();
          first.focus();
        }
      }}
    >
      <Card size={size} onClick={(event) => event.stopPropagation()}>
        {title !== undefined ? (
          <TitleBar size={size}>
            {title}
            {showClose && (
              <CloseButton type="button" onClick={onClose} aria-label="닫기">
                <LuX />
              </CloseButton>
            )}
          </TitleBar>
        ) : (
          showClose && (
            <Header>
              <CloseButton type="button" onClick={onClose} aria-label="닫기">
                <LuX />
              </CloseButton>
            </Header>
          )
        )}
        <Body>{children}</Body>
      </Card>
    </Overlay>,
    container ?? document.body,
  );
};

const Overlay = styled.dialog`
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
  margin: 0;
  border: 0;
  color: inherit;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: ${spacing[20]};
  background: ${colors.overlay};
  z-index: 1000;

  &::backdrop {
    background: transparent;
  }

  &:focus {
    outline: none;
  }
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
  color: ${colors.textPrimary};
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

const TitleBar = styled.div<{ size: NonNullable<ModalProps["size"]> }>`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: ${spacing[12]};
  margin-bottom: ${({ size }) => (size === "lg" ? spacing[20] : spacing[16])};
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
