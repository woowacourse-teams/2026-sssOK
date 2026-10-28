import { AiFillCrown } from "react-icons/ai";

import { Badge } from "@/shared/ui/badge";
import type { AnonymousSession } from "../model/types";
import { getRoomSession } from "../lib/roomSessionStorage";
import { BadgeContent } from "./RoomSessionBadge.styles";

interface RoomSessionBadgeProps {
  roomCode: string;
  hostId: number;
  session?: Pick<AnonymousSession, "userId" | "nickname"> | null;
}

export const RoomSessionBadge = ({
  roomCode,
  hostId,
  session: providedSession,
}: RoomSessionBadgeProps) => {
  const session = providedSession === undefined ? getRoomSession(roomCode) : providedSession;

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
