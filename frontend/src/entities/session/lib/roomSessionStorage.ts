import type { AnonymousSession } from "../model/types";

/**
 * 방 코드마다 키를 따로 쓴다.
 * 방마다 익명 인증을 새로 하므로 방마다 다른 member(다른 userId·nickname)가 된다.
 * 한 번에 한 항목만 건드리므로, 탭 두 개에서 서로 다른 방에 입장해도 세션이 덮이지 않는다.
 */
const ROOM_SESSION_KEY_PREFIX = "sssok.auth:";

const getRoomSessionKey = (roomCode: string) => `${ROOM_SESSION_KEY_PREFIX}${roomCode}`;

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

/**
 * 이 토큰을 들고 있는 방을 찾는다. 어느 방 것도 아니면 null 이다.
 *
 * 401 은 "요청에 실린 토큰이 죽었다" 는 뜻이라, 지워야 할 세션도 그 토큰이 든 방 하나다.
 * 지금 보고 있는 화면의 방 코드로 지우지 않는 이유는 **응답이 늦게 올 수 있기 때문**이다 —
 * 그 사이 다른 방으로 넘어갔다면 멀쩡한 세션을 대신 지우게 된다.
 *
 * 이미 지운 뒤에 도착한 401 도 여기서 null 로 걸러진다. 한 판에 함께 나간 요청들이
 * 줄줄이 401 로 돌아오는데, 그때마다 세션을 지우고 화면을 옮기면 사용자가 다시 입장해
 * 만든 새 세션까지 뒤늦은 응답이 밀어낸다.
 */
export const findRoomCodeByToken = (token: string): string | null => {
  // 훑는 도중에 `getRoomSession` 이 손상된 항목을 지울 수 있다. 열쇠부터 떠 두지 않으면
  // 그때 뒤 항목들의 자리가 당겨져 하나씩 건너뛴다.
  const roomCodes = Object.keys(localStorage)
    .filter((key) => key.startsWith(ROOM_SESSION_KEY_PREFIX))
    .map((key) => key.slice(ROOM_SESSION_KEY_PREFIX.length));

  return roomCodes.find((roomCode) => getRoomSession(roomCode)?.accessToken === token) ?? null;
};
