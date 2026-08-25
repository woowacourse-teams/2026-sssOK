import { useParams } from "react-router-dom";

import { isApiError } from "@/shared/api";
import { useRoom } from "../api";

const ERROR_MESSAGE: Record<string, string> = {
  INVALID_ROOM_CODE: "방 코드 형식이 올바르지 않아요.",
  ROOM_NOT_FOUND: "존재하지 않는 방이에요.",
};

const STATUS_MESSAGE = {
  EXPIRED: "만료된 방이에요.",
  DELETED: "삭제된 방이에요.",
} as const;

export const RoomEntryPage = () => {
  const { code = "" } = useParams<{ code: string }>();
  const { data: room, isPending, error } = useRoom(code);

  if (isPending) {
    return <main>방 정보를 불러오는 중이에요.</main>;
  }

  if (error) {
    const message = isApiError(error)
      ? (ERROR_MESSAGE[error.code] ?? error.message)
      : "방 정보를 불러오지 못했어요.";

    return <main>{message}</main>;
  }

  // 만료·삭제된 방도 조회는 성공한다. 방 이름을 보여줄 수 있어 안내가 친절해진다.
  if (room.status !== "ACTIVE") {
    return (
      <main>
        {room.name} — {STATUS_MESSAGE[room.status]}
      </main>
    );
  }

  return <main>{room.name} 방으로 들어가는 중이에요.</main>;
};
