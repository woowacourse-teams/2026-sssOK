import { AiFillCrown } from "react-icons/ai";

import { Badge } from "@/shared/ui/badge";
import { getRoomSession } from "../lib/roomSessionStorage";
import { BadgeContent } from "./RoomSessionBadge.styles";

interface RoomSessionBadgeProps {
  roomCode: string;
  hostId: number;
}

export const RoomSessionBadge = ({ roomCode, hostId }: RoomSessionBadgeProps) => {
  const session = getRoomSession(roomCode);

  if (session === null) return null;

  const isHost = session.userId === hostId;

  return (
    <Badge
      variant="soft"
      size="md"
      aria-label={isHost ? `방장 ${session.nickname}` : session.nickname}
    >
      <BadgeContent>
        {isHost && <AiFillCrown aria-hidden="true" />}
        {session.nickname}
      </BadgeContent>
    </Badge>
  );
};
