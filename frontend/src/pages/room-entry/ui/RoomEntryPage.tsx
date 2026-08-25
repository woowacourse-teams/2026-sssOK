import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";

import { NameEntryBottomSheet } from "@/features/join-room";
import { isApiError, tokenStorage } from "@/shared/api";
import { ROUTES } from "@/shared/config";
import { useAnonymousAuth, useRoom } from "../api";

const ERROR_MESSAGE: Record<string, string> = {
  INVALID_ROOM_CODE: "방 코드 형식이 올바르지 않아요.",
  ROOM_NOT_FOUND: "존재하지 않는 방이에요.",
};

const STATUS_MESSAGE = {
  EXPIRED: "만료된 방이에요.",
  DELETED: "삭제된 방이에요.",
} as const;

const HomeLink = () => <Link to={ROUTES.home}>홈으로 돌아가기</Link>;

export const RoomEntryPage = () => {
  const { code = "" } = useParams<{ code: string }>();
  const { data: room, isPending, error } = useRoom(code);

  // 토큰은 방마다 따로 있다. 처음 보는 방이면 이름부터 다시 묻는다.
  const [hasToken, setHasToken] = useState(() => tokenStorage.get(code) !== null);
  const auth = useAnonymousAuth(code, () => setHasToken(true));

  const roomUnavailable = room !== undefined && room.status !== "ACTIVE";

  // 들어갈 수 없는 방이면 들고 있던 토큰도 쓸 데가 없다
  useEffect(() => {
    if (roomUnavailable) {
      tokenStorage.clear(code);
    }
  }, [roomUnavailable, code]);

  if (isPending) {
    return <main>방 정보를 불러오는 중이에요.</main>;
  }

  if (error) {
    const message = isApiError(error)
      ? (ERROR_MESSAGE[error.code] ?? error.message)
      : "방 정보를 불러오지 못했어요.";

    return (
      <main>
        <p>{message}</p>
        <HomeLink />
      </main>
    );
  }

  // 만료·삭제된 방도 조회는 성공한다. 방 이름을 보여줄 수 있어 안내가 친절해진다.
  if (room.status !== "ACTIVE") {
    return (
      <main>
        <p>
          {room.name} — {STATUS_MESSAGE[room.status]}
        </p>
        <HomeLink />
      </main>
    );
  }

  if (auth.isError) {
    return (
      <main>
        <p>입장하지 못했어요. 잠시 후 다시 시도해주세요.</p>
        <HomeLink />
      </main>
    );
  }

  // 이 방 토큰이 이미 있으면 이름을 다시 묻지 않는다
  if (hasToken) {
    return <Navigate to={ROUTES.gallery(code)} replace />;
  }

  return <NameEntryBottomSheet onSubmit={auth.mutate} isPending={auth.isPending} />;
};
