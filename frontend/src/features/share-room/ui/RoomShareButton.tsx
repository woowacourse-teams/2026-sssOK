import { useEffect, useId, useRef, useState } from "react";
import { HiLink, HiQrCode } from "react-icons/hi2";

import { ROUTES } from "@/shared/config";
import { IconButton } from "@/shared/ui/icon-button";
import { Toast, type ToastProps } from "@/shared/ui/toast";
import { Anchor, Description, Menu, MenuItem } from "./RoomShareButton.styles";

interface RoomShareButtonProps {
  roomCode: string;
}

export const RoomShareButton = ({ roomCode }: RoomShareButtonProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [isCopying, setIsCopying] = useState(false);
  const [notice, setNotice] = useState<Pick<ToastProps, "message" | "tone"> | null>(null);
  const anchorRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const copyRef = useRef<HTMLButtonElement>(null);
  const menuId = useId();

  const closeMenu = () => {
    setIsOpen(false);
    triggerRef.current?.focus();
  };

  useEffect(() => {
    if (!isOpen) return;

    copyRef.current?.focus();

    const handlePointerDown = (event: PointerEvent) => {
      if (event.target instanceof Node && !anchorRef.current?.contains(event.target)) {
        setIsOpen(false);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setIsOpen(false);
        triggerRef.current?.focus();
      }
    };

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  useEffect(() => {
    if (!notice) return;

    const timer = window.setTimeout(() => setNotice(null), 4000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const copyGalleryLink = async () => {
    if (isCopying) return;

    setIsCopying(true);
    setNotice(null);
    try {
      // 쿼리나 해시에 담긴 일시적인 상태는 공유하지 않는다.
      const url = new URL(ROUTES.gallery(roomCode), window.location.origin).href;
      await navigator.clipboard.writeText(url);
      setNotice({ message: "공유 링크를 복사했어요.", tone: "success" });
      closeMenu();
    } catch {
      setNotice({
        message: "링크를 복사하지 못했어요. 브라우저 권한을 확인하거나 주소창에서 복사해 주세요.",
        tone: "error",
      });
    } finally {
      setIsCopying(false);
    }
  };

  return (
    <>
      <Anchor
        ref={anchorRef}
        onBlur={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget)) setIsOpen(false);
        }}
      >
        <IconButton
          ref={triggerRef}
          size="sm"
          aria-label="방 공유 메뉴 열기"
          aria-haspopup="menu"
          aria-expanded={isOpen}
          aria-controls={isOpen ? menuId : undefined}
          onClick={() => setIsOpen((open) => !open)}
          onKeyDown={(event) => {
            if (event.key === "ArrowDown" || event.key === "ArrowUp") {
              event.preventDefault();
              setIsOpen(true);
            }
          }}
        >
          <HiLink aria-hidden="true" />
        </IconButton>
        {isOpen && (
          <Menu
            id={menuId}
            role="menu"
            aria-label="방 공유"
            onKeyDown={(event) => {
              if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
                event.preventDefault();
                copyRef.current?.focus();
              }
            }}
          >
            <MenuItem
              ref={copyRef}
              type="button"
              role="menuitem"
              tabIndex={-1}
              aria-disabled={isCopying}
              onClick={() => void copyGalleryLink()}
            >
              <HiLink aria-hidden="true" />
              <span>
                링크 공유
                <Description>앨범에 바로 참여하는 링크</Description>
              </span>
            </MenuItem>
            <MenuItem type="button" role="menuitem" disabled title="준비 중인 기능이에요">
              <HiQrCode aria-hidden="true" />
              <span>
                QR 및 코드 공유
                <Description>QR 코드 및 코드로 참여 · 준비 중</Description>
              </span>
            </MenuItem>
          </Menu>
        )}
      </Anchor>
      {notice && <Toast {...notice} onClose={() => setNotice(null)} />}
    </>
  );
};
