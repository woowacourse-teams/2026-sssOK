import { useState } from "react";
import { HiAdjustmentsHorizontal, HiEllipsisHorizontal, HiTrash } from "react-icons/hi2";
import styled from "@emotion/styled";

import { DropdownMenu, DropdownMenuItem } from "@/shared/ui/dropdown-menu";
import { IconButton } from "@/shared/ui/icon-button";

interface RoomMenuButtonProps {
  onOpenSettings?: () => void;
  onDeleteRoom?: () => void;
}

export const RoomMenuButton = ({ onOpenSettings, onDeleteRoom }: RoomMenuButtonProps) => {
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
        </DropdownMenu>
      )}
    </MenuContainer>
  );
};

const MenuContainer = styled.div`
  position: relative;
  display: inline-flex;
`;
