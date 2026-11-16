import styled from "@emotion/styled";
import { useEffect, useLayoutEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { LuX } from "react-icons/lu";
import { colors, radius, shadow, spacing, typography } from "@/shared/styles/tokens";

export interface BottomSheetProps {
  title: string;
  /** 제목 아래 회색 한 줄. 있으면 제목과 한 덩어리로 묶인다. */
  description?: ReactNode;
  /** 제목 덩어리 오른쪽에 붙는 작은 그림. */
  aside?: ReactNode;
  onClose?: () => void;
  children: ReactNode | ((close: () => void) => ReactNode);
  variant?: "form" | "action";
}

export const BottomSheet = ({
  title,
  description,
  aside,
  onClose,
  children,
  variant = "form",
}: BottomSheetProps) => {
  const titleId = useId();
  const [isClosing, setIsClosing] = useState(false);
  const requestClose = (): void => {
    if (!onClose || isClosing) return;
    setIsClosing(true);
  };
  const overlayRef = useRef<HTMLDialogElement>(null);
  const sheetRef = useRef<HTMLDivElement>(null);
  const drag = useRef<{ pointerId: number; startY: number; delta: number } | null>(null);

  useLayoutEffect(() => {
    if (!isClosing) return;
    const sheet = sheetRef.current;
    if (sheet) {
      sheet.style.setProperty("--sheet-exit-offset", `${drag.current?.delta ?? 0}px`);
      sheet.style.removeProperty("animation");
    }
    drag.current = null;
  }, [isClosing]);

  useEffect(() => {
    const overlay = overlayRef.current;
    if (!overlay) return;
    const previousFocus = document.activeElement;
    const scrollX = window.scrollX;
    const scrollY = window.scrollY;
    const body = document.body;
    const previousStyles = {
      overflow: body.style.overflow,
      position: body.style.position,
      top: body.style.top,
      left: body.style.left,
      width: body.style.width,
    };

    overlay.showModal();
    // iOS에서도 배경 페이지가 움직이지 않도록 현재 스크롤 위치에 고정한다.
    Object.assign(body.style, {
      overflow: "hidden",
      position: "fixed",
      top: `-${scrollY}px`,
      left: `-${scrollX}px`,
      width: "100%",
    });

    return () => {
      overlay.close();
      Object.assign(body.style, previousStyles);
      if (scrollX || scrollY) window.scrollTo(scrollX, scrollY);
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus({ preventScroll: true });
      }
    };
  }, []);

  const resetDrag = (): void => {
    drag.current = null;
    if (sheetRef.current) sheetRef.current.style.transform = "";
  };

  return createPortal(
    <Overlay
      ref={overlayRef}
      aria-labelledby={titleId}
      data-testid="bottom-sheet-overlay"
      onClick={requestClose}
      onCancel={(event) => {
        event.preventDefault();
        requestClose();
      }}
    >
      <Sheet
        ref={sheetRef}
        isClosing={isClosing}
        onAnimationEnd={(event) => {
          if (event.target === event.currentTarget && isClosing) onClose?.();
        }}
        variant={variant}
        hasHandle={Boolean(onClose)}
        onClick={(event) => event.stopPropagation()}
      >
        {onClose && (
          <>
            <Handle
              variant={variant}
              data-testid="bottom-sheet-handle"
              onPointerDown={(event) => {
                if (isClosing) return;
                if (sheetRef.current) sheetRef.current.style.animation = "none";
                drag.current = { pointerId: event.pointerId, startY: event.clientY, delta: 0 };
                event.currentTarget.setPointerCapture(event.pointerId);
              }}
              onPointerMove={(event) => {
                if (drag.current?.pointerId !== event.pointerId || !sheetRef.current) return;
                drag.current.delta = Math.max(0, event.clientY - drag.current.startY);
                sheetRef.current.style.transform = `translateY(${drag.current.delta}px)`;
              }}
              onPointerUp={(event) => {
                if (drag.current?.pointerId !== event.pointerId) return;
                if (drag.current.delta >= 80) {
                  requestClose();
                } else {
                  resetDrag();
                }
              }}
              onPointerCancel={resetDrag}
            />
            <DesktopClose type="button" aria-label="닫기" onClick={requestClose}>
              <LuX size={20} />
            </DesktopClose>
          </>
        )}
        {description || aside ? (
          <TitleGroup hasClose={Boolean(onClose)}>
            <TitleText>
              <Title id={titleId} hasClose={false} variant={variant}>
                {title}
              </Title>
              {description && <Description>{description}</Description>}
            </TitleText>
            {aside}
          </TitleGroup>
        ) : (
          <Title id={titleId} hasClose={Boolean(onClose)} variant={variant}>
            {title}
          </Title>
        )}
        <Body variant={variant}>{typeof children === "function" ? children(requestClose) : children}</Body>
      </Sheet>
    </Overlay>,
    document.body,
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
  padding: 0;
  border: 0;
  color: inherit;
  /*
   * 모달 dialog 는 기본이 overflow: auto 다. 열릴 때 아래에서 올라오는 시트가 바깥으로 넘쳐
   * 스크롤이 생기고, 첫 버튼에 포커스가 가며 그쪽으로 스크롤됐다가 되돌아와 시트가 튀어 보인다.
   * 넘치는 내용은 시트 안에서 스크롤하므로 바깥은 막는다.
   * hidden 은 포커스 이동 같은 코드 스크롤을 막지 못해 clip 을 쓴다.
   */
  overflow: clip;
  overscroll-behavior: none;

  &::backdrop {
    background: transparent;
  }
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: ${colors.overlay};
  z-index: 1000;

  @media (min-width: 768px) {
    align-items: center;
    padding: 24px;
  }
`;

const Sheet = styled.div<{ variant: "form" | "action"; hasHandle: boolean; isClosing: boolean }>`
  position: relative;
  width: 100%;
  max-height: 88dvh;
  overflow-y: auto;
  overscroll-behavior: contain;
  /* action 은 피그마 정리 시트(558:5390 · 560:177) 기준: 위 10px, 좌우·아래 20px. */
  padding: ${({ hasHandle, variant }) =>
      hasHandle ? (variant === "form" ? "12px" : "10px") : "44px"}
    ${({ variant }) => (variant === "form" ? "30px" : "20px")}
    calc(
      ${({ variant }) => (variant === "form" ? "54px" : "20px")} + env(safe-area-inset-bottom, 0px)
    );
  background: ${colors.backgroundDefault};
  border-radius: ${radius[24]} ${radius[24]} 0 0;
  box-shadow: ${shadow.sheet};
  animation: ${({ isClosing }) => (isClosing ? "sheet-down" : "sheet-up")} 220ms
    cubic-bezier(0.22, 0.61, 0.36, 1) ${({ isClosing }) => (isClosing ? "forwards" : "backwards")};
  pointer-events: ${({ isClosing }) => (isClosing ? "none" : "auto")};

  @keyframes sheet-down {
    from {
      transform: translateY(var(--sheet-exit-offset, 0px));
    }
    to {
      transform: translateY(100%);
    }
  }

  @keyframes sheet-up {
    from {
      transform: translateY(100%);
    }
    to {
      transform: translateY(0);
    }
  }

  @media (min-width: 768px) {
    max-width: 440px;
    max-height: calc(100dvh - 48px);
    padding-top: 32px;
    padding-bottom: 32px;
    border-radius: ${radius[24]};
    animation-name: ${({ isClosing }) => (isClosing ? "sheet-pop-out" : "sheet-pop")};
  }

  @keyframes sheet-pop-out {
    from {
      opacity: 1;
      transform: translateY(0);
    }
    to {
      opacity: 0;
      transform: translateY(8px);
    }
  }

  @keyframes sheet-pop {
    from {
      opacity: 0;
      transform: translateY(8px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    animation-duration: 1ms;
  }
`;

/* action 손잡이는 피그마대로 36×4, 아래 16px 띄운다. */
const Handle = styled.div<{ variant: "form" | "action" }>`
  position: relative;
  width: 100%;
  height: ${({ variant }) => (variant === "form" ? "23px" : "20px")};
  margin: 0 auto;

  &::before {
    content: "";
    position: absolute;
    top: 0;
    left: ${({ variant }) => (variant === "form" ? "calc(50% - 22px)" : "calc(50% - 18px)")};
    width: ${({ variant }) => (variant === "form" ? "44px" : "36px")};
    height: ${({ variant }) => (variant === "form" ? "5px" : "4px")};
    border-radius: ${radius.full};
    background: ${colors.borderDefault};
  }
  cursor: grab;
  touch-action: none;

  &:active {
    cursor: grabbing;
  }

  @media (min-width: 768px) {
    display: none;
  }
`;

const DesktopClose = styled.button`
  display: none;

  @media (min-width: 768px) {
    position: absolute;
    top: 26px;
    right: 16px;
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    border-radius: ${radius.full};
    color: ${colors.textSecondary};

    &:hover {
      background: ${colors.interactiveHover};
    }
  }
`;

const Title = styled.h2<{ hasClose: boolean; variant: "form" | "action" }>`
  /* action 제목은 피그마 정리 시트(558:5394 · 560:180)대로 20px 굵게. */
  font-size: ${({ variant }) => (variant === "form" ? typography.label1.fontSize : "20px")};
  font-weight: ${({ variant }) => (variant === "form" ? 600 : 700)};
  line-height: ${({ variant }) => (variant === "form" ? typography.label1.lineHeight : "28px")};
  color: ${colors.textStrong};

  @media (min-width: 768px) {
    padding-right: ${({ hasClose }) => (hasClose ? "36px" : "0")};
  }
`;

const TitleGroup = styled.div<{ hasClose: boolean }>`
  display: flex;
  gap: ${spacing[8]};
  align-items: center;

  @media (min-width: 768px) {
    padding-right: ${({ hasClose }) => (hasClose ? "36px" : "0")};
  }
`;

const TitleText = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: ${spacing[4]};
  min-width: 0;
`;

const Description = styled.p`
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  color: ${colors.textSecondary};
  word-break: keep-all;
`;

/* action 은 제목 아래 간격이 시트마다 달라(6px·24px) 내용 쪽에서 정한다. */
const Body = styled.div<{ variant: "form" | "action" }>`
  width: 100%;
  margin-top: ${({ variant }) => (variant === "form" ? spacing[20] : "0")};
`;
