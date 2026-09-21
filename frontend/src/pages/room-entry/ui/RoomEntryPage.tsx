import { useEffect, useState } from "react";
import { Link, Navigate, useLocation, useParams } from "react-router-dom";

import { useRoomQuery } from "@/entities/room";
import { readValidRoomSession, removeRoomSession } from "@/entities/session";
import { NameEntryBottomSheet } from "@/features/join-room";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config";
import { type AnalyticsEntrySource, track } from "@/shared/lib";
import { useAnonymousAuth } from "../api";

const ERROR_MESSAGE: Record<string, string> = {
  INVALID_ROOM_CODE: "방 코드 형식이 올바르지 않아요.",
  ROOM_NOT_FOUND: "존재하지 않는 방이에요.",
};

const STATUS_MESSAGE = {
  EXPIRED: "만료된 방이에요.",
  DELETED: "삭제된 방이에요.",
  PURGED: "영구 삭제된 방이에요.",
} as const;

const HomeLink = () => <Link to={ROUTES.home}>홈으로 돌아가기</Link>;

/**
 * 방에 들어온 경로. 코드를 직접 입력하는 화면은 이 페이지로 보낼 때
 * `navigate(ROUTES.roomEntry(code), { state: { entrySrc: "code" } })` 로 알려준다.
 * 그 밖에는 모두 공유 링크(또는 QR)로 온 것으로 본다.
 */
const entrySourceOf = (state: unknown): AnalyticsEntrySource =>
  (state as { entrySrc?: unknown } | null)?.entrySrc === "code" ? "code" : "link";

export const RoomEntryPage = () => {
  const { code = "" } = useParams<{ code: string }>();
  const entrySrc = entrySourceOf(useLocation().state);
  const session = readValidRoomSession(code);
  const {
    data: room,
    isPending,
    error,
  } = useRoomQuery({
    code,
    token: session?.accessToken,
    userId: session?.userId,
  });

  // 방금 인증을 마친 방. 코드가 바뀌면 그 방 기준으로 다시 판단해야 해서 방 코드로 들고 있는다.
  const [authedCode, setAuthedCode] = useState<string | null>(null);
  const auth = useAnonymousAuth(code, () => {
    setAuthedCode(code);
    track("Room Joined", { room_code: code, entry_src: entrySrc });
  });

  // 세션은 방마다 따로 있다. 처음 보는 방이면 이름부터 다시 묻는다.
  const hasSession = authedCode === code || session !== null;

  const roomUnavailable = room !== undefined && room.status !== "ACTIVE";

  // 들어갈 수 없는 방이면 들고 있던 세션도 쓸 데가 없다
  useEffect(() => {
    if (roomUnavailable) {
      removeRoomSession(code);
    }
  }, [roomUnavailable, code]);

  // 이름 화면이 떴다는 건 이 방에 처음 들어오는 사람이라는 뜻이다. 입장 퍼널의 첫 단계로 센다.
  const isAskingName = room?.status === "ACTIVE" && !auth.isError && !hasSession;

  useEffect(() => {
    if (isAskingName) {
      track("Room Join Started", { room_code: code, entry_src: entrySrc });
    }
  }, [isAskingName, code, entrySrc]);

  const joinFailureReason = error
    ? isApiError(error)
      ? error.code
      : "UNKNOWN"
    : roomUnavailable
      ? room.status
      : auth.isError
        ? "JOIN_FAILED"
        : null;

  useEffect(() => {
    if (joinFailureReason !== null) {
      track("Room Join Failed", { room_code: code, reason: joinFailureReason });
    }
  }, [joinFailureReason, code]);

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

  // 이 방 세션이 이미 있으면 이름을 다시 묻지 않는다
  if (hasSession) {
    return <Navigate to={ROUTES.gallery(code)} replace />;
  }

  return (
    <NameEntryBottomSheet
      onSubmit={(nickname) => auth.mutate({ nickname, roomId: room.roomId })}
      isPending={auth.isPending}
    />
  );
};
