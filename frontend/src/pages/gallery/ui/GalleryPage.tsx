import { Navigate, useParams } from "react-router-dom";

import { useRoomQuery, type Room } from "@/entities/room";
import { useRoomSession } from "@/entities/member-session";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config";
import { PageSpinner } from "@/shared/ui/spinner";
import { GalleryContent } from "./GalleryContent";
import { GalleryModalProvider } from "./GalleryModalProvider";
import { PageState } from "./GalleryPage.styles";

const ERROR_MESSAGE: Record<string, string> = {
  INVALID_ROOM_CODE: "방 코드 형식이 올바르지 않아요.",
  ROOM_NOT_FOUND: "존재하지 않는 방이에요.",
};

interface ActiveGalleryProps {
  room: Room;
  accessToken: string;
  userId: number;
  nickname: string;
}

const ActiveGallery = ({ room, accessToken, userId, nickname }: ActiveGalleryProps) => {
  return (
    <GalleryModalProvider>
      <GalleryContent room={room} accessToken={accessToken} userId={userId} nickname={nickname} />
    </GalleryModalProvider>
  );
};

export const GalleryPage = () => {
  const { code = "" } = useParams<{ code: string }>();
  const { session, isPending: isSessionPending, isError: isSessionError } = useRoomSession(code);
  const roomQuery = useRoomQuery({
    code,
    token: session?.accessToken,
    userId: session?.userId,
    enabled: session !== null,
  });

  if (isSessionPending) return <PageSpinner label="로그인 정보를 불러오는 중이에요." />;
  if (isSessionError)
    return (
      <main>
        <span role="alert">로그인 정보를 확인하지 못했어요. 다시 시도해 주세요.</span>
      </main>
    );

  if (session === null) {
    return <Navigate to={ROUTES.roomEntry(code)} replace />;
  }

  if (roomQuery.isPending) {
    return <PageSpinner label="방 정보를 불러오는 중이에요." />;
  }

  if (roomQuery.isError) {
    const message = isApiError(roomQuery.error)
      ? (ERROR_MESSAGE[roomQuery.error.code] ?? roomQuery.error.message)
      : "방 정보를 불러오지 못했어요.";

    return (
      <PageState>
        <span role="alert">{message}</span>
      </PageState>
    );
  }

  if (roomQuery.data.status !== "ACTIVE") {
    return <Navigate to={ROUTES.closedRoom} replace />;
  }

  if ("memberStatus" in session && !roomQuery.data.joined) {
    return <Navigate to={ROUTES.roomEntry(code)} replace />;
  }

  return (
    <ActiveGallery
      room={roomQuery.data}
      accessToken={session.accessToken}
      userId={session.userId}
      nickname={session.nickname}
    />
  );
};
