const STORAGE_KEY = "sssok.auth";

export interface RoomToken {
  accessToken: string;
  userId: number;
  nickname: string;
  /** 서버가 내려준 만료 시각 (ISO 8601). AuthResponse.expiresAt 과 같은 값이다. */
  expiresAt: string;
}

/**
 * 방 코드를 키로 토큰을 나눠 담는다.
 * 방마다 익명 인증을 새로 하므로 방마다 다른 member(다른 userId·nickname)가 된다.
 * 그래서 다른 방 토큰을 끌어다 쓰면 안 된다 — 그 방에서 쓰기로 한 이름이 아니다.
 */
type TokenMap = Record<string, RoomToken>;

const isExpired = (expiresAt: string, now: Date) => {
  const expiry = new Date(expiresAt).getTime();

  // 파싱할 수 없는 값은 믿을 수 없으니 만료로 취급한다
  return Number.isNaN(expiry) || expiry <= now.getTime();
};

const isRoomToken = (value: unknown): value is RoomToken => {
  if (value === null || typeof value !== "object") return false;

  const token = value as Partial<RoomToken>;
  return typeof token.accessToken === "string" && typeof token.expiresAt === "string";
};

const readAll = (): TokenMap => {
  let raw: string | null = null;

  try {
    raw = localStorage.getItem(STORAGE_KEY);
  } catch {
    // 사파리 프라이빗 모드 등 저장소 접근이 막힌 환경
    return {};
  }

  if (!raw) return {};

  try {
    const parsed: unknown = JSON.parse(raw);
    return parsed !== null && typeof parsed === "object" ? (parsed as TokenMap) : {};
  } catch {
    return {};
  }
};

const writeAll = (tokens: TokenMap) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
  } catch {
    // 저장에 실패해도 이번 세션은 진행한다
  }
};

export const tokenStorage = {
  /** 이 방에서 쓰던 토큰. 만료됐거나 형식이 깨졌으면 그 항목만 지우고 null 을 준다. */
  get(roomCode: string, now: Date = new Date()): RoomToken | null {
    const token = readAll()[roomCode];

    if (!isRoomToken(token)) {
      if (token !== undefined) this.clear(roomCode);
      return null;
    }

    if (isExpired(token.expiresAt, now)) {
      this.clear(roomCode);
      return null;
    }

    return token;
  },

  set(roomCode: string, token: RoomToken) {
    writeAll({ ...readAll(), [roomCode]: token });
  },

  /** 이 방 토큰만 지운다. 다른 방 토큰은 그대로 둔다. */
  clear(roomCode: string) {
    const tokens = readAll();

    if (!(roomCode in tokens)) return;

    delete tokens[roomCode];
    writeAll(tokens);
  },

  clearAll() {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // 지울 수 없으면 그대로 둔다
    }
  },
};
