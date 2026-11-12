import type { AnonymousSession } from "../model/types";
import { getRoomSession, removeRoomSession } from "./roomSessionStorage";

const isExpired = (expiresAt: string, now: Date): boolean => {
  const expiry = new Date(expiresAt).getTime();

  return Number.isNaN(expiry) || expiry <= now.getTime();
};

export const readValidRoomSession = (
  roomCode: string,
  now: Date = new Date(),
): AnonymousSession | null => {
  const session = getRoomSession(roomCode);

  if (session === null) return null;

  // Access가 만료돼도 refresh 세션이 남아 있으면 API의 401 복구에 맡긴다.
  const expiresAt = session.refreshExpiresAt ?? session.expiresAt;
  if (isExpired(expiresAt, now)) {
    removeRoomSession(roomCode);
    return null;
  }

  return session;
};
