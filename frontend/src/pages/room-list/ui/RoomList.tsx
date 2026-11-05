import { useEffect, useState } from "react";

import type { MyRoom } from "@/entities/room";
import { Toast, type ToastProps } from "@/shared/ui/toast";
import { RoomCard } from "./RoomCard";
import { RoomCardMenu } from "./RoomCardMenu";
import { RoomListEmpty } from "./RoomListEmpty";
import { List } from "./RoomListPage.styles";

export const RoomList = ({ rooms }: { rooms: MyRoom[] }) => {
  const [openMenuRoomId, setOpenMenuRoomId] = useState<number | null>(null);
  const [notice, setNotice] = useState<Pick<ToastProps, "message" | "tone"> | null>(null);

  useEffect(() => {
    if (!notice) return;

    const timer = window.setTimeout(() => setNotice(null), 4000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  if (rooms.length === 0) {
    return <RoomListEmpty />;
  }

  return (
    <>
      <List>
        {rooms.map((room) => (
          <RoomCard key={room.roomId} room={room} isMenuOpen={openMenuRoomId === room.roomId}>
            <RoomCardMenu
              room={room}
              isMenuOpen={openMenuRoomId === room.roomId}
              onOpenChange={(open) => setOpenMenuRoomId(open ? room.roomId : null)}
              onNotify={setNotice}
            />
          </RoomCard>
        ))}
      </List>
      {notice && <Toast {...notice} showClose={false} onClose={() => setNotice(null)} />}
    </>
  );
};
