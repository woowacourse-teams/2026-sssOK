import { Navigate, useParams } from "react-router-dom";

import { useRoomQuery, type Room } from "@/entities/room";
import { readValidRoomSession } from "@/entities/session";
import { useRoomEvents } from "@/features/subscribe-room-events";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config";
import { GalleryContent } from "./GalleryContent";
import { PageState } from "./GalleryPage.styles";

const ERROR_MESSAGE: Record<string, string> = {
  INVALID_ROOM_CODE: "방 코드 형식이 올바르지 않아요.",
  ROOM_NOT_FOUND: "존재하지 않는 방이에요.",
};

interface ActiveGalleryProps {
  room: Room;
  accessToken: string;
  userId: number;
}

const ActiveGallery = ({ room, accessToken, userId }: ActiveGalleryProps) => {
  useRoomEvents({ roomId: room.roomId, userId, token: accessToken });

  return <GalleryContent room={room} accessToken={accessToken} userId={userId} />;
};

export const GalleryPage = () => {
  const { code = "" } = useParams<{ code: string }>();
  const session = readValidRoomSession(code);
  const roomQuery = useRoomQuery({
    code,
    token: session?.accessToken,
    userId: session?.userId,
    enabled: session !== null,
  });

  if (session === null) {
    return <Navigate to={ROUTES.roomEntry(code)} replace />;
  }

  if (roomQuery.isPending) {
    return <PageState>방 정보를 불러오는 중이에요.</PageState>;
  }

  if (roomQuery.isError) {
    const message = isApiError(roomQuery.error)
      ? (ERROR_MESSAGE[roomQuery.error.code] ?? roomQuery.error.message)
      : "방 정보를 불러오지 못했어요.";

    return <PageState>{message}</PageState>;
  }

  if (roomQuery.data.status !== "ACTIVE") {
    return <Navigate to={ROUTES.roomEntry(code)} replace />;
  }

  return (
    <ActiveGallery
      room={roomQuery.data}
      accessToken={session.accessToken}
      userId={session.userId}
    />
  );
};
