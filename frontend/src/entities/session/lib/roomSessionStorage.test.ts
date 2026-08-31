import type { AnonymousSession } from "../model/types";
import {
  findRoomCodeByToken,
  getRoomSession,
  removeRoomSession,
  saveRoomSession,
} from "./roomSessionStorage";

const session: AnonymousSession = {
  accessToken: "mock-access-token",
  userId: 10234,
  nickname: "민수",
  expiresAt: "2026-09-17T05:30:00Z",
};

describe("roomSessionStorage", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("방 코드별 세션을 저장하고 조회한다", () => {
    saveRoomSession("7K93QX2S", session);

    expect(getRoomSession("7K93QX2S")).toEqual(session);
  });

  it("방 코드에 해당하는 세션을 제거한다", () => {
    saveRoomSession("7K93QX2S", session);

    removeRoomSession("7K93QX2S");

    expect(getRoomSession("7K93QX2S")).toBeNull();
  });

  /*
   * 401 을 받으면 그 토큰이 든 방 하나만 지운다 (#149).
   * 화면이 알려주는 방 코드로 지우지 않는 이유는, 응답이 늦게 오면 그 사이 다른 방으로
   * 넘어가 있을 수 있어서다.
   */
  describe("findRoomCodeByToken", () => {
    it("그 토큰을 들고 있는 방 코드를 찾는다", () => {
      saveRoomSession("7K93QX2S", session);
      saveRoomSession("QRST6789", { ...session, accessToken: "another-token", userId: 10235 });

      expect(findRoomCodeByToken("another-token")).toBe("QRST6789");
    });

    it("어느 방 세션에도 없는 토큰이면 null 이다", () => {
      saveRoomSession("7K93QX2S", session);

      expect(findRoomCodeByToken("이미-지운-세션의-토큰")).toBeNull();
    });

    // 우리 키만 본다. 목이 남긴 값(`sssok.mock.*`)까지 세션으로 읽으면 안 된다.
    it("세션이 아닌 저장소 항목은 보지 않는다", () => {
      localStorage.setItem("sssok.mock.nextUserId", "10234");

      expect(findRoomCodeByToken("10234")).toBeNull();
    });
  });
});
