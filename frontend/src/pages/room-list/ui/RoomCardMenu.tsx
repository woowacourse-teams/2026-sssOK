import { useState, type ComponentProps, type ReactNode } from "react";
import {
  HiArrowRightOnRectangle,
  HiEllipsisHorizontal,
  HiLink,
  HiOutlineCog6Tooth,
} from "react-icons/hi2";

import { useNavigate } from "react-router-dom";

import { useMemberSessionQuery } from "@/entities/member-session";
import { useToast } from "@/shared/ui/toast";
import { ROUTES } from "@/shared/config";
import type { MyRoom } from "@/entities/room";
import { LeaveRoomModal } from "@/features/leave-room";
import { DropdownMenu, DropdownMenuItem } from "@/shared/ui/dropdown-menu";
import { MenuSlot, MoreButton } from "./RoomCard.styles";

interface RoomDropdownProps {
  isMenuOpen: boolean;
  onCloseMenu: () => void;
  onCopyInviteLink: () => void;
}

interface RoomCardMenuProps {
  room: MyRoom;
  isMenuOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

const RoomDropdown = ({
  isMenuOpen,
  onCloseMenu,
  onCopyInviteLink,
  children,
}: RoomDropdownProps & { children: ReactNode }) =>
  isMenuOpen && (
    <DropdownMenu onClose={onCloseMenu}>
      <DropdownMenuItem icon={<HiLink />} onClick={onCopyInviteLink}>
        초대 링크 복사
      </DropdownMenuItem>
      {children}
    </DropdownMenu>
  );

const HostedRoomDropdown = ({
  onSettings,
  ...props
}: RoomDropdownProps & { onSettings: () => void }) => (
  <RoomDropdown {...props}>
    <DropdownMenuItem icon={<HiOutlineCog6Tooth />} onClick={onSettings}>
      방 설정
    </DropdownMenuItem>
  </RoomDropdown>
);

interface JoinedRoomDropdownProps extends RoomDropdownProps {
  room: MyRoom;
}

const JoinedRoomDropdown = ({ room, ...dropdownProps }: JoinedRoomDropdownProps) => {
  const [isLeaveModalOpen, setIsLeaveModalOpen] = useState(false);
  const closeLeaveModal = () => setIsLeaveModalOpen(false);

  const openLeaveModal = () => {
    dropdownProps.onCloseMenu();
    setIsLeaveModalOpen(true);
  };

  return (
    <>
      <RoomDropdown {...dropdownProps}>
        <DropdownMenuItem icon={<HiArrowRightOnRectangle />} tone="danger" onClick={openLeaveModal}>
          방 나가기
        </DropdownMenuItem>
      </RoomDropdown>
      {isLeaveModalOpen && (
        <RoomLeaveConfirmation
          roomId={room.roomId}
          roomName={room.name}
          onClose={closeLeaveModal}
          onSuccess={closeLeaveModal}
        />
      )}
    </>
  );
};

const RoomLeaveConfirmation = (
  props: Omit<ComponentProps<typeof LeaveRoomModal>, "accessToken">,
) => {
  const { data: session } = useMemberSessionQuery();
  return <LeaveRoomModal {...props} accessToken={session?.accessToken} />;
};

export const RoomCardMenu = ({ room, isMenuOpen, onOpenChange }: RoomCardMenuProps) => {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const closeMenu = () => onOpenChange(false);

  const copyInviteLink = async () => {
    closeMenu();
    try {
      const url = new URL(ROUTES.roomInvite(room.code), window.location.origin);
      await navigator.clipboard.writeText(url.href);
      showToast({ message: "초대 링크를 복사했어요.", tone: "success", showClose: false });
    } catch {
      showToast({
        message: "링크를 복사하지 못했어요. 잠시 후 다시 시도해 주세요.",
        tone: "error",
        showClose: false,
      });
    }
  };

  const dropdownProps = {
    isMenuOpen,
    onCloseMenu: closeMenu,
    onCopyInviteLink: () => void copyInviteLink(),
  };

  return (
    <MenuSlot data-menu-slot>
      <MoreButton
        type="button"
        aria-label={`${room.name} 메뉴`}
        aria-expanded={isMenuOpen}
        onClick={() => onOpenChange(!isMenuOpen)}
      >
        <HiEllipsisHorizontal />
      </MoreButton>
      {room.isHost ? (
        <HostedRoomDropdown
          {...dropdownProps}
          onSettings={() => {
            closeMenu();
            navigate(ROUTES.roomSettings(room.code));
          }}
        />
      ) : (
        <JoinedRoomDropdown {...dropdownProps} room={room} />
      )}
    </MenuSlot>
  );
};
