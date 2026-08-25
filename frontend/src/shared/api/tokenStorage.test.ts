import { tokenStorage, type StoredToken } from "./tokenStorage";

const NOW = new Date("2026-08-25T00:00:00Z");
const FUTURE = "2026-09-25T00:00:00Z";
const PAST = "2026-08-24T00:00:00Z";

const ROOM_A = "7K93QX2S";
const ROOM_B = "ABCD2345";

const token = (expiresAt: string): StoredToken => ({
  accessToken: "abc",
  userId: 10234,
  nickname: "민수",
  expiresAt,
});

beforeEach(() => localStorage.clear());

describe("tokenStorage", () => {
  describe("current — 요청에 실을 토큰", () => {
    it("저장된 것이 없으면 null 을 준다", () => {
      expect(tokenStorage.current(NOW)).toBeNull();
    });

    it("어느 방에서 받았든 같은 토큰을 돌려준다", () => {
      tokenStorage.save(ROOM_A, token(FUTURE));

      expect(tokenStorage.current(NOW)).toEqual(token(FUTURE));
    });

    it("만료된 것만 있으면 null 을 주고 저장소를 비운다", () => {
      tokenStorage.save(ROOM_A, token(PAST));

      expect(tokenStorage.current(NOW)).toBeNull();
      expect(localStorage.getItem("sssok.auth")).toBe("{}");
    });

    it("깨진 값이 들어 있으면 null 을 준다", () => {
      localStorage.setItem("sssok.auth", "이건 JSON 이 아니다");

      expect(tokenStorage.current(NOW)).toBeNull();
    });
  });

  describe("hasVisited — 방문 기록", () => {
    it("들어와 본 방은 true, 처음 보는 방은 false 다", () => {
      tokenStorage.save(ROOM_A, token(FUTURE));

      expect(tokenStorage.hasVisited(ROOM_A, NOW)).toBe(true);
      expect(tokenStorage.hasVisited(ROOM_B, NOW)).toBe(false);
    });

    it("여러 방 기록이 같은 토큰으로 함께 남는다", () => {
      tokenStorage.save(ROOM_A, token(FUTURE));
      tokenStorage.save(ROOM_B, token(FUTURE));

      expect(tokenStorage.hasVisited(ROOM_A, NOW)).toBe(true);
      expect(tokenStorage.hasVisited(ROOM_B, NOW)).toBe(true);
      expect(tokenStorage.current(NOW)).toEqual(token(FUTURE));
    });
  });

  it("clear 는 그 방 기록만 지우고 토큰은 남긴다", () => {
    tokenStorage.save(ROOM_A, token(FUTURE));
    tokenStorage.save(ROOM_B, token(FUTURE));

    tokenStorage.clear(ROOM_A);

    expect(tokenStorage.hasVisited(ROOM_A, NOW)).toBe(false);
    expect(tokenStorage.current(NOW)).not.toBeNull();
  });

  it("clearAll 은 전부 지운다", () => {
    tokenStorage.save(ROOM_A, token(FUTURE));
    tokenStorage.clearAll();

    expect(tokenStorage.current(NOW)).toBeNull();
  });
});
