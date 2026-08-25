import type { AnonymousSession } from "../model/types";

/**
 * 방 코드마다 키를 따로 쓴다.
 * 방마다 익명 인증을 새로 하므로 방마다 다른 member(다른 userId·nickname)가 된다.
 * 한 번에 한 항목만 건드리므로, 탭 두 개에서 서로 다른 방에 입장해도 세션이 덮이지 않는다.
 */
const getRoomSessionKey = (roomCode: string) => `sssok.auth:${roomCode}`;

export const saveRoomSession = (roomCode: string, session: AnonymousSession) => {
  localStorage.setItem(getRoomSessionKey(roomCode), JSON.stringify(session));
};

export const getRoomSession = (roomCode: string): AnonymousSession | null => {
  const raw = localStorage.getItem(getRoomSessionKey(roomCode));

  if (!raw) return null;

  try {
    return JSON.parse(raw) as AnonymousSession;
  } catch {
    // 손상된 값은 되살릴 방법이 없다. 그 방만 지우고 이름부터 다시 묻는다.
    localStorage.removeItem(getRoomSessionKey(roomCode));
    return null;
  }
};

/** 이 방 세션만 지운다. 다른 방 세션은 그대로 둔다. */
export const removeRoomSession = (roomCode: string) => {
  localStorage.removeItem(getRoomSessionKey(roomCode));
};
