import { useState } from "react";

import { useMemberSessionQuery } from "@/entities/member-session";
import { useMyRoomsQuery } from "@/entities/room";
import { RoomList } from "./RoomList";
import { RoomListEmpty } from "./RoomListEmpty";
import { RoomListFilters, type RoomFilter } from "./RoomListFilters";
import { RoomListStack } from "./RoomListPage.styles";

export const RoomListContent = () => {
  const [selectedFilter, setSelectedFilter] = useState<RoomFilter>("all");
  const { data: session } = useMemberSessionQuery();
  const { data: rooms } = useMyRoomsQuery({ token: session?.accessToken });

  if (rooms.length === 0) return <RoomListEmpty />;

  const roomsByFilter = {
    all: rooms,
    host: rooms.filter((room) => room.isHost),
    member: rooms.filter((room) => !room.isHost),
  };
  const visibleRooms = roomsByFilter[selectedFilter];

  return (
    <RoomListStack $isEmpty={visibleRooms.length === 0}>
      <RoomListFilters
        counts={{
          all: roomsByFilter.all.length,
          host: roomsByFilter.host.length,
          member: roomsByFilter.member.length,
        }}
        selectedFilter={selectedFilter}
        onFilterChange={setSelectedFilter}
      />
      <RoomList rooms={visibleRooms} />
    </RoomListStack>
  );
};
