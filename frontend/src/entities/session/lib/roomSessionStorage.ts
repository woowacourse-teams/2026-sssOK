import type { AnonymousSession } from "../model/types";

const STORAGE_KEY = "sssok.auth";

/**
 * 방 코드를 키로 세션을 나눠 담는다.
 * 방마다 익명 인증을 새로 하므로 방마다 다른 member(다른 userId·nickname)가 된다.
 * 그래서 다른 방 세션을 끌어다 쓰면 안 된다 — 그 방에서 쓰기로 한 이름이 아니다.
 */
type SessionMap = Record<string, AnonymousSession>;

const readAll = (): SessionMap => {
  const raw = localStorage.getItem(STORAGE_KEY);

  if (!raw) return {};

  try {
    const parsed: unknown = JSON.parse(raw);
    return parsed !== null && typeof parsed === "object" ? (parsed as SessionMap) : {};
  } catch {
    // 손상된 값은 통째로 버린다. 되살릴 방법이 없으니 이름부터 다시 묻는 편이 낫다.
    return {};
  }
};

const writeAll = (sessions: SessionMap) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions));
};

export const saveRoomSession = (roomCode: string, session: AnonymousSession) => {
  writeAll({ ...readAll(), [roomCode]: session });
};

export const getRoomSession = (roomCode: string): AnonymousSession | null =>
  readAll()[roomCode] ?? null;

/** 이 방 세션만 지운다. 다른 방 세션은 그대로 둔다. */
export const removeRoomSession = (roomCode: string) => {
  const sessions = readAll();

  if (!(roomCode in sessions)) return;

  delete sessions[roomCode];
  writeAll(sessions);
};
