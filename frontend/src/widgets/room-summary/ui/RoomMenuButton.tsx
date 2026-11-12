import { useState, type ReactNode } from "react";
import {
  LuSlidersHorizontal,
  LuEllipsis,
  LuFolderX,
  LuFolderPlus,
  LuFolderPen,
  LuTrash2,
} from "react-icons/lu";
import styled from "@emotion/styled";

import { DropdownMenu, DropdownMenuDivider, DropdownMenuItem } from "@/shared/ui/dropdown-menu";
import { IconButton } from "@/shared/ui/icon-button";

interface RoomMenuButtonProps {
  notificationMenu?: (close: () => void) => ReactNode;
  isHost?: boolean;
  hasSelectedFolder?: boolean;
  onOpenSettings?: () => void;
  onDeleteRoom?: () => void;
  onAddFolder?: () => void;
  onEditFolder?: () => void;
  onDeleteFolder?: () => void;
}

export const RoomMenuButton = ({
  isHost = false,
  hasSelectedFolder = false,
  onOpenSettings,
  onDeleteRoom,
  onAddFolder,
  onEditFolder,
  onDeleteFolder,
  notificationMenu,
}: RoomMenuButtonProps) => {
  const [isOpen, setIsOpen] = useState(false);

  const selectMenu = (action?: () => void) => {
    setIsOpen(false);
    action?.();
  };

  return (
    <MenuContainer>
      <IconButton size="sm" aria-label="방 메뉴 열기" onClick={() => setIsOpen((open) => !open)}>
        <LuEllipsis strokeWidth={1.9} aria-hidden="true" />
      </IconButton>

      {isOpen && (
        <DropdownMenu onClose={() => setIsOpen(false)}>
          {isHost && (
            <DropdownMenuItem
              icon={<LuSlidersHorizontal strokeWidth={1.9} aria-hidden="true" />}
              onClick={() => selectMenu(onOpenSettings)}
            >
              방 설정
            </DropdownMenuItem>
          )}
          {isHost && (
            <>
              <DropdownMenuItem
                icon={<LuTrash2 strokeWidth={1.9} aria-hidden="true" />}
                tone="danger"
                onClick={() => selectMenu(onDeleteRoom)}
              >
                방 삭제
              </DropdownMenuItem>
              <DropdownMenuDivider />
            </>
          )}
          <DropdownMenuItem
            icon={<LuFolderPlus strokeWidth={1.9} aria-hidden="true" />}
            onClick={() => selectMenu(onAddFolder)}
          >
            폴더 추가
          </DropdownMenuItem>
          <DropdownMenuItem
            icon={<LuFolderPen strokeWidth={1.9} aria-hidden="true" />}
            disabled={!hasSelectedFolder || !onEditFolder}
            onClick={() => selectMenu(onEditFolder)}
          >
            폴더 설정
          </DropdownMenuItem>
          <DropdownMenuItem
            icon={<LuFolderX strokeWidth={1.9} aria-hidden="true" />}
            disabled={!hasSelectedFolder || !onDeleteFolder}
            onClick={() => selectMenu(onDeleteFolder)}
          >
            폴더 삭제
          </DropdownMenuItem>
          {notificationMenu && (
            <>
              <DropdownMenuDivider />
              {notificationMenu(() => setIsOpen(false))}
            </>
          )}
        </DropdownMenu>
      )}
    </MenuContainer>
  );
};

const MenuContainer = styled.div`
  position: relative;
  display: inline-flex;
`;
