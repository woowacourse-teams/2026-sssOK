import { useState, type ReactNode } from "react";
import { HiOutlinePhoto } from "react-icons/hi2";

import type { MyRoom } from "@/entities/room";
import { ROUTES } from "@/shared/config";
import { Card, Cover, HostBadge, Info, Meta, Name, NameLink } from "./RoomCard.styles";

interface RoomCardProps {
  room: MyRoom;
  isMenuOpen: boolean;
  children: ReactNode;
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

export const RoomCard = ({ room, isMenuOpen, children }: RoomCardProps) => {
  return (
    <Card data-menu-open={isMenuOpen || undefined}>
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
      {children}
    </Card>
  );
};
