import { getRoomSession, removeRoomSession, type AnonymousSession } from "@/entities/session";

const isExpired = (expiresAt: string, now: Date) => {
  const expiry = new Date(expiresAt).getTime();

  // 파싱할 수 없는 값은 믿을 수 없으니 만료로 취급한다
  return Number.isNaN(expiry) || expiry <= now.getTime();
};

/**
 * 이 방에서 쓰던 세션. 만료됐으면 그 방 것만 지우고 null 을 준다.
 * 세션은 방마다 따로 있다 — 방마다 익명 인증을 새로 해서 방마다 다른 member 가 되기 때문이다.
 */
export const readValidRoomSession = (
  roomCode: string,
  now: Date = new Date(),
): AnonymousSession | null => {
  const session = getRoomSession(roomCode);

  if (session === null) return null;

  if (isExpired(session.expiresAt, now)) {
    removeRoomSession(roomCode);
    return null;
  }

  return session;
};
