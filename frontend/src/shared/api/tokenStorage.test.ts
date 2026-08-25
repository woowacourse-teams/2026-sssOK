import { tokenStorage, type RoomToken } from "./tokenStorage";

const NOW = new Date("2026-08-25T00:00:00Z");
const FUTURE = "2026-09-25T00:00:00Z";
const PAST = "2026-08-24T00:00:00Z";

const ROOM_A = "7K93QX2S";
const ROOM_B = "ABCD2345";

const token = (nickname: string, expiresAt: string): RoomToken => ({
  accessToken: `token-${nickname}`,
  userId: 10234,
  nickname,
  expiresAt,
});

beforeEach(() => localStorage.clear());

describe("tokenStorage", () => {
  it("방 코드로 저장하고 같은 코드로 꺼낸다", () => {
    tokenStorage.set(ROOM_A, token("민수", FUTURE));

    expect(tokenStorage.get(ROOM_A, NOW)).toEqual(token("민수", FUTURE));
  });

  it("저장한 적 없는 방은 null 을 준다 — 다른 방 토큰을 끌어다 쓰지 않는다", () => {
    tokenStorage.set(ROOM_A, token("민수", FUTURE));

    expect(tokenStorage.get(ROOM_B, NOW)).toBeNull();
  });

  it("방마다 다른 이름을 따로 보관한다", () => {
    tokenStorage.set(ROOM_A, token("민수", FUTURE));
    tokenStorage.set(ROOM_B, token("해니", FUTURE));

    expect(tokenStorage.get(ROOM_A, NOW)?.nickname).toBe("민수");
    expect(tokenStorage.get(ROOM_B, NOW)?.nickname).toBe("해니");
  });

  it("만료된 토큰은 돌려주지 않고 그 방 항목만 지운다", () => {
    tokenStorage.set(ROOM_A, token("민수", PAST));
    tokenStorage.set(ROOM_B, token("해니", FUTURE));

    expect(tokenStorage.get(ROOM_A, NOW)).toBeNull();
    expect(tokenStorage.get(ROOM_B, NOW)?.nickname).toBe("해니");
  });

  it("만료 시각이 지금과 같으면 만료로 본다", () => {
    tokenStorage.set(ROOM_A, token("민수", NOW.toISOString()));

    expect(tokenStorage.get(ROOM_A, NOW)).toBeNull();
  });

  it("clear 는 그 방 토큰만 지운다", () => {
    tokenStorage.set(ROOM_A, token("민수", FUTURE));
    tokenStorage.set(ROOM_B, token("해니", FUTURE));

    tokenStorage.clear(ROOM_A);

    expect(tokenStorage.get(ROOM_A, NOW)).toBeNull();
    expect(tokenStorage.get(ROOM_B, NOW)).not.toBeNull();
  });

  it("깨진 값이 들어 있으면 null 을 준다", () => {
    localStorage.setItem("sssok.auth", "이건 JSON 이 아니다");

    expect(tokenStorage.get(ROOM_A, NOW)).toBeNull();
  });

  it("형식이 맞지 않는 항목은 지우고 null 을 준다", () => {
    localStorage.setItem("sssok.auth", JSON.stringify({ [ROOM_A]: { nickname: "민수" } }));

    expect(tokenStorage.get(ROOM_A, NOW)).toBeNull();
    expect(localStorage.getItem("sssok.auth")).toBe("{}");
  });
});
