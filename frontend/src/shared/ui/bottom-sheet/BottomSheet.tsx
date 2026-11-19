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
}

export const BottomSheet = ({ title, description, aside, onClose, children }: BottomSheetProps) => {
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

    // showModal 은 안쪽 첫 입력칸에 스크롤과 함께 포커스를 준다. 시트가 아직 화면 아래에서
    // 올라오는 중이라 화면이 그쪽으로 튀었다가 돌아온다. 대화상자 자체에 먼저 포커스를 두고,
    // 입력칸은 스크롤 없이 포커스한다. 같은 클릭 처리 안에서 부르므로 키보드도 함께 열린다.
    const autoFocusTarget = sheetRef.current?.querySelector<HTMLElement>("[data-autofocus]");
    if (autoFocusTarget) {
      overlay.tabIndex = -1;
      overlay.setAttribute("autofocus", "");
    }
    overlay.showModal();
    autoFocusTarget?.focus({ preventScroll: true });
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

  // 키보드는 레이아웃 뷰포트가 아니라 보이는 영역(visual viewport)만 줄인다(iOS, Android Chrome 108+).
  // 오버레이는 화면 끝까지 두고(키보드 위 반투명 막대 뒤로 배경이 비치지 않게),
  // 키보드에 가려지는 높이만큼 시트 아래 여백을 늘려 내용을 키보드 위로 올린다.
  useEffect(() => {
    const overlay = overlayRef.current;
    const viewport = window.visualViewport;
    if (!overlay || !viewport) return;

    const sync = (): void => {
      // iOS 는 입력칸이 키보드에 가려질 것 같으면 preventScroll 을 무시하고 페이지를 끌어올린다.
      // body 를 고정해 두었으니 원래 위치는 늘 맨 위다. 확대 중이 아니면 되돌려 오버레이를 화면에 붙인다.
      if (viewport.scale <= 1.01 && (window.scrollY !== 0 || viewport.offsetTop !== 0)) {
        window.scrollTo(0, 0);
      }
      const hidden = overlay.clientHeight - viewport.offsetTop - viewport.height;
      // 주소창이 접히고 펴지는 정도의 차이는 키보드로 보지 않는다.
      const keyboardOpen = hidden > 80;
      overlay.style.setProperty("--keyboard-inset", `${keyboardOpen ? Math.round(hidden) : 0}px`);
      overlay.toggleAttribute("data-keyboard-open", keyboardOpen);
    };

    sync();
    viewport.addEventListener("resize", sync);
    viewport.addEventListener("scroll", sync);
    window.addEventListener("scroll", sync);
    return () => {
      viewport.removeEventListener("resize", sync);
      viewport.removeEventListener("scroll", sync);
      window.removeEventListener("scroll", sync);
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
        hasHandle={Boolean(onClose)}
        onClick={(event) => event.stopPropagation()}
      >
        {onClose && (
          <>
            <Handle
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
              <Title id={titleId} hasClose={false}>
                {title}
              </Title>
              {description && <Description>{description}</Description>}
            </TitleText>
            {aside}
          </TitleGroup>
        ) : (
          <Title id={titleId} hasClose={Boolean(onClose)}>
            {title}
          </Title>
        )}
        <Body>{typeof children === "function" ? children(requestClose) : children}</Body>
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

  &:focus {
    outline: none;
  }

  @media (min-width: 768px) {
    align-items: center;
    padding: 24px;

    &[data-keyboard-open] {
      padding-bottom: calc(24px + var(--keyboard-inset));
    }
  }
`;

const Sheet = styled.div<{ hasHandle: boolean; isClosing: boolean }>`
  position: relative;
  width: 100%;
  max-height: 88dvh;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: ${({ hasHandle }) => (hasHandle ? "12px" : "44px")} 30px
    calc(54px + env(safe-area-inset-bottom, 0px));
  background: ${colors.backgroundDefault};
  border-radius: ${radius[24]} ${radius[24]} 0 0;
  box-shadow: ${shadow.sheet};
  animation: ${({ isClosing }) => (isClosing ? "sheet-down" : "sheet-up")} 220ms
    cubic-bezier(0.22, 0.61, 0.36, 1) ${({ isClosing }) => (isClosing ? "forwards" : "backwards")};
  pointer-events: ${({ isClosing }) => (isClosing ? "none" : "auto")};

  /*
   * 키보드가 열리면 시트 흰 바탕은 화면 끝까지 두고, 내용만 키보드 위로 올린다.
   * 홈 인디케이터 여백은 키보드가 가리므로 뺀다.
   */
  [data-keyboard-open] > & {
    max-height: min(calc(88dvh + var(--keyboard-inset)), 100%);
    padding-bottom: calc(${spacing[20]} + var(--keyboard-inset));
    scroll-padding-bottom: var(--keyboard-inset);
  }

  @media (min-width: 768px) {
    [data-keyboard-open] > & {
      max-height: min(calc(100dvh - 48px), 100%);
      padding-bottom: 32px;
      scroll-padding-bottom: 0;
    }
  }

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

const Handle = styled.div`
  position: relative;
  width: 100%;
  height: 23px;
  margin: 0 auto;

  &::before {
    content: "";
    position: absolute;
    top: 0;
    left: calc(50% - 22px);
    width: 44px;
    height: 5px;
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

const Title = styled.h2<{ hasClose: boolean }>`
  font-size: ${typography.label1.fontSize};
  font-weight: 600;
  line-height: ${typography.label1.lineHeight};
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
  /* 문구 안의 줄바꿈(\n)을 그대로 살린다. */
  white-space: pre-line;
`;

const Body = styled.div`
  width: 100%;
  margin-top: ${spacing[20]};
`;
