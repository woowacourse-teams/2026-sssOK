const STORAGE_KEY = "sssok.auth";

export interface StoredToken {
  accessToken: string;
  userId: number;
  nickname: string;
  /** 서버가 내려준 만료 시각 (ISO 8601). AuthResponse.expiresAt 과 같은 값이다. */
  expiresAt: string;
}

/**
 * 방 코드를 키로 담지만 토큰 자체는 하나다 — 들어간 방마다 같은 토큰을 남겨 방문 기록으로 쓴다.
 * 닉네임은 계정당 하나뿐이라(member.nickname) 새 방에서 다시 인증하지 않는다.
 * 다시 인증하면 member 행이 늘어나고, DB 에는 그들을 같은 사람으로 묶을 수단이 없다.
 */
type VisitedRooms = Record<string, StoredToken>;

const isExpired = (expiresAt: string, now: Date) => {
  const expiry = new Date(expiresAt).getTime();

  // 파싱할 수 없는 값은 믿을 수 없으니 만료로 취급한다
  return Number.isNaN(expiry) || expiry <= now.getTime();
};

const isStoredToken = (value: unknown): value is StoredToken => {
  if (value === null || typeof value !== "object") return false;

  const token = value as Partial<StoredToken>;
  return typeof token.accessToken === "string" && typeof token.expiresAt === "string";
};

const readAll = (): VisitedRooms => {
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
    return parsed !== null && typeof parsed === "object" ? (parsed as VisitedRooms) : {};
  } catch {
    return {};
  }
};

const writeAll = (rooms: VisitedRooms) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(rooms));
  } catch {
    // 저장에 실패해도 이번 세션은 진행한다
  }
};

/** 만료됐거나 형식이 깨진 항목을 걸러낸 사본. 걸러낸 게 있으면 저장소도 정리한다. */
const readValid = (now: Date): VisitedRooms => {
  const rooms = readAll();
  const valid: VisitedRooms = {};

  for (const [code, token] of Object.entries(rooms)) {
    if (isStoredToken(token) && !isExpired(token.expiresAt, now)) {
      valid[code] = token;
    }
  }

  if (Object.keys(valid).length !== Object.keys(rooms).length) {
    writeAll(valid);
  }

  return valid;
};

export const tokenStorage = {
  /** 요청에 실을 토큰. 어느 방에서 받았든 같은 계정이라 아무 유효한 것이나 쓴다. */
  current(now: Date = new Date()): StoredToken | null {
    return Object.values(readValid(now))[0] ?? null;
  },

  /** 이 방에 들어와 본 적이 있는지. 이름을 물을지와는 무관하고 방문 기록일 뿐이다. */
  hasVisited(roomCode: string, now: Date = new Date()): boolean {
    return roomCode in readValid(now);
  },

  /** 이 방에 들어왔다고 기록한다. 인증 직후에도, 기존 토큰으로 입장할 때도 부른다. */
  save(roomCode: string, token: StoredToken) {
    writeAll({ ...readValid(new Date()), [roomCode]: token });
  },

  /** 이 방 기록만 지운다. 다른 방 기록과 토큰은 그대로 둔다. */
  clear(roomCode: string) {
    const rooms = readAll();

    if (!(roomCode in rooms)) return;

    delete rooms[roomCode];
    writeAll(rooms);
  },

  clearAll() {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // 지울 수 없으면 그대로 둔다
    }
  },
};
