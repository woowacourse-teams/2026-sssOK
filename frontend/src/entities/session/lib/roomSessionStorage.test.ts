import type { AnonymousSession } from "../model/types";
import { getRoomSession, removeRoomSession, saveRoomSession } from "./roomSessionStorage";

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
});
