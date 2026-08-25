import type { AnonymousSession } from "../model/types";

const getRoomSessionKey = (roomCode: string) => `room-session:${roomCode}`;

export const saveRoomSession = (roomCode: string, session: AnonymousSession) => {
  localStorage.setItem(getRoomSessionKey(roomCode), JSON.stringify(session));
};

export const getRoomSession = (roomCode: string): AnonymousSession | null => {
  const session = localStorage.getItem(getRoomSessionKey(roomCode));

  return session ? (JSON.parse(session) as AnonymousSession) : null;
};

export const removeRoomSession = (roomCode: string) => {
  localStorage.removeItem(getRoomSessionKey(roomCode));
};
