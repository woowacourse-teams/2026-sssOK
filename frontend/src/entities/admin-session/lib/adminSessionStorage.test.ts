import { saveRoomSession } from "@/entities/session";
import {
  getAdminSession,
  readValidAdminSession,
  removeAdminSession,
  saveAdminSession,
} from "./adminSessionStorage";
import type { AdminSession } from "../model/types";

const session: AdminSession = {
  accessToken: "admin-token",
  adminId: 2,
  loginId: "hyeonmibap",
  name: "현미밥",
  role: "ADMIN",
  expiresAt: "9999-12-31T23:59:59Z",
};

describe("adminSessionStorage", () => {
  beforeEach(() => localStorage.clear());

  it("저장한 세션을 그대로 돌려준다", () => {
    saveAdminSession(session);

    expect(getAdminSession()).toEqual(session);
  });

  it("지우면 null 이다", () => {
    saveAdminSession(session);
    removeAdminSession();

    expect(getAdminSession()).toBeNull();
  });

  it("값이 손상되면 지우고 null 을 돌려준다", () => {
    localStorage.setItem("sssok.admin", "{깨진 값");

    expect(getAdminSession()).toBeNull();
    expect(localStorage.getItem("sssok.admin")).toBeNull();
  });

  it("만료된 세션은 지우고 null 을 돌려준다", () => {
    saveAdminSession({ ...session, expiresAt: "2000-01-01T00:00:00Z" });

    expect(readValidAdminSession()).toBeNull();
    expect(getAdminSession()).toBeNull();
  });

  it("만료 시각을 읽을 수 없으면 살아 있다고 보지 않는다", () => {
    saveAdminSession({ ...session, expiresAt: "언제까지인지 모를 값" });

    expect(readValidAdminSession()).toBeNull();
  });

  it("방 세션과 서로 덮어쓰지 않는다", () => {
    saveRoomSession("7K93QX2S", {
      accessToken: "room-token",
      userId: 10234,
      nickname: "민수",
      expiresAt: "9999-12-31T23:59:59Z",
    });
    saveAdminSession(session);

    expect(readValidAdminSession()?.accessToken).toBe("admin-token");
    expect(localStorage.getItem("sssok.auth:7K93QX2S")).toContain("room-token");

    removeAdminSession();

    // 관리자 세션을 지워도 방 세션은 남아 있어야 한다.
    expect(localStorage.getItem("sssok.auth:7K93QX2S")).toContain("room-token");
  });
});
