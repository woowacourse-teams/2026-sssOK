import { useState, type ReactNode } from "react";
import { HiEllipsisHorizontal, HiOutlinePhoto } from "react-icons/hi2";

import type { MyRoom } from "@/entities/room";
import { ROUTES } from "@/shared/config";
import {
  Card,
  Cover,
  HostBadge,
  Info,
  MenuSlot,
  Meta,
  MoreButton,
  Name,
  NameLink,
} from "./RoomCard.styles";

interface RoomCardProps {
  room: MyRoom;
  onOpenMenu: () => void;
  menu?: ReactNode;
}

const RoomCover = ({ thumbnailUrl }: Pick<MyRoom, "thumbnailUrl">) => {
  const [hasError, setHasError] = useState(false);

  return (
    <Cover>
      {thumbnailUrl && !hasError ? (
        <img src={thumbnailUrl} alt="" loading="lazy" onError={() => setHasError(true)} />
      ) : (
        <HiOutlinePhoto aria-hidden="true" />
      )}
    </Cover>
  );
};

export const RoomCard = ({ room, onOpenMenu, menu }: RoomCardProps) => {
  return (
    <Card>
      <RoomCover thumbnailUrl={room.thumbnailUrl} />
      <Info>
        <NameLink to={ROUTES.roomEntry(room.code)}>
          <Name>{room.name}</Name>
          {room.isHost && <HostBadge>방장</HostBadge>}
        </NameLink>
        <Meta>
          사진 {room.mediaCount.toLocaleString()} · {room.memberCount.toLocaleString()}명
        </Meta>
      </Info>
      <MenuSlot data-menu-slot>
        <MoreButton type="button" aria-label={`${room.name} 메뉴`} onClick={onOpenMenu}>
          <HiEllipsisHorizontal />
        </MoreButton>
        {menu}
      </MenuSlot>
    </Card>
  );
};
