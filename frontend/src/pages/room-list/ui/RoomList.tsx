import { useState } from "react";

import type { MyRoom } from "@/entities/room";
import { RoomCard } from "./RoomCard";
import { RoomCardMenu } from "./RoomCardMenu";
import { RoomListEmpty } from "./RoomListEmpty";
import { List } from "./RoomListPage.styles";

export const RoomList = ({ rooms }: { rooms: MyRoom[] }) => {
  const [openMenuRoomId, setOpenMenuRoomId] = useState<number | null>(null);

  if (rooms.length === 0) {
    return <RoomListEmpty />;
  }

  return (
    <List>
      {rooms.map((room) => (
        <RoomCard key={room.roomId} room={room} isMenuOpen={openMenuRoomId === room.roomId}>
          <RoomCardMenu
            room={room}
            isMenuOpen={openMenuRoomId === room.roomId}
            onOpenChange={(open) => setOpenMenuRoomId(open ? room.roomId : null)}
          />
        </RoomCard>
      ))}
    </List>
  );
};
