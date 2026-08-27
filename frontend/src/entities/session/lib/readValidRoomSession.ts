import type { AnonymousSession } from "../model/types";
import { getRoomSession, removeRoomSession } from "./roomSessionStorage";

const isExpired = (expiresAt: string, now: Date) => {
  const expiry = new Date(expiresAt).getTime();

  return Number.isNaN(expiry) || expiry <= now.getTime();
};

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
