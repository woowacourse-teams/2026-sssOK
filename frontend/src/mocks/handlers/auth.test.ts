import { API_PREFIX } from "../config";
import { resetAnonymousAuth } from "./auth";

const authenticate = (nickname: string) =>
  fetch(`${API_PREFIX}/auth/anonymous`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ nickname }),
  });

beforeEach(() => resetAnonymousAuth());

describe("POST /auth/anonymous 목 핸들러", () => {
  it("닉네임을 보내면 201 과 토큰을 data 로 감싸 내려준다", async () => {
    const response = await authenticate("윤돌");
    const body = await response.json();

    expect(response.status).toBe(201);
    expect(body.data.nickname).toBe("윤돌");
    expect(body.data.accessToken).toBeTruthy();
    expect(body.data.expiresAt).toBeTruthy();
  });

  it("첫 회원은 방 목 데이터의 방장과 같은 id 다", async () => {
    const body = await (await authenticate("민수")).json();

    expect(body.data.userId).toBe(10234);
  });

  it("부를 때마다 다른 회원이 된다 — 방마다 다른 사람으로 입장할 수 있다", async () => {
    const first = await (await authenticate("해니")).json();
    const second = await (await authenticate("민수")).json();

    expect(second.data.userId).not.toBe(first.data.userId);
    expect(second.data.accessToken).not.toBe(first.data.accessToken);
    expect(first.data.nickname).toBe("해니");
    expect(second.data.nickname).toBe("민수");
  });
});
