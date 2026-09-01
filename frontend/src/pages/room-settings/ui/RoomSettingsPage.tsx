import { useQueryClient } from "@tanstack/react-query";
import { HiArrowLeft } from "react-icons/hi2";
import { Navigate, useNavigate, useParams } from "react-router-dom";

import { roomQueryKey, useRoomQuery } from "@/entities/room";
import { readValidRoomSession } from "@/entities/session";
import { UpdateRoomForm } from "@/features/update-room";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config";
import { IconButton } from "@/shared/ui/icon-button";
import { Header, Page, PageState, Title } from "./RoomSettingsPage.styles";

const ERROR_MESSAGE: Record<string, string> = {
  INVALID_ROOM_CODE: "방 코드 형식이 올바르지 않아요.",
  ROOM_NOT_FOUND: "존재하지 않는 방이에요.",
};

export const RoomSettingsPage = () => {
  const { code = "" } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
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
    <Page>
      <Header>
        <IconButton aria-label="뒤로 가기" onClick={() => navigate(-1)}>
          <HiArrowLeft />
        </IconButton>
        <Title>방 설정</Title>
      </Header>

      <UpdateRoomForm
        room={roomQuery.data}
        accessToken={session.accessToken}
        onSuccess={(updatedRoom) => {
          queryClient.setQueryData(roomQueryKey(code, session.userId), updatedRoom);
          navigate(ROUTES.gallery(code), { replace: true });
        }}
      />
    </Page>
  );
};
