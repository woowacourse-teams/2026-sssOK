import { useState } from "react";
import {
  HiAdjustmentsHorizontal,
  HiEllipsisHorizontal,
  HiFolderMinus,
  HiFolderPlus,
  HiPencilSquare,
  HiTrash,
} from "react-icons/hi2";
import styled from "@emotion/styled";

import { DropdownMenu, DropdownMenuDivider, DropdownMenuItem } from "@/shared/ui/dropdown-menu";
import { IconButton } from "@/shared/ui/icon-button";

interface RoomMenuButtonProps {
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
}: RoomMenuButtonProps) => {
  const [isOpen, setIsOpen] = useState(false);

  const selectMenu = (action?: () => void) => {
    setIsOpen(false);
    action?.();
  };

  return (
    <MenuContainer>
      <IconButton size="sm" aria-label="방 메뉴 열기" onClick={() => setIsOpen((open) => !open)}>
        <HiEllipsisHorizontal />
      </IconButton>

      {isOpen && (
        <DropdownMenu onClose={() => setIsOpen(false)}>
          {isHost && (
            <>
              <DropdownMenuItem
                icon={<HiAdjustmentsHorizontal />}
                onClick={() => selectMenu(onOpenSettings)}
              >
                방 설정
              </DropdownMenuItem>
              <DropdownMenuItem
                icon={<HiTrash />}
                tone="danger"
                onClick={() => selectMenu(onDeleteRoom)}
              >
                방 삭제
              </DropdownMenuItem>
              <DropdownMenuDivider />
            </>
          )}
          <DropdownMenuItem icon={<HiFolderPlus />} onClick={() => selectMenu(onAddFolder)}>
            폴더 추가
          </DropdownMenuItem>
          <DropdownMenuItem
            icon={<HiPencilSquare />}
            disabled={!hasSelectedFolder || !onEditFolder}
            onClick={() => selectMenu(onEditFolder)}
          >
            폴더 설정
          </DropdownMenuItem>
          <DropdownMenuItem
            icon={<HiFolderMinus />}
            disabled={!hasSelectedFolder || !onDeleteFolder}
            onClick={() => selectMenu(onDeleteFolder)}
          >
            폴더 삭제
          </DropdownMenuItem>
        </DropdownMenu>
      )}
    </MenuContainer>
  );
};

const MenuContainer = styled.div`
  position: relative;
  display: inline-flex;
`;
