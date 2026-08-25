import { API_PREFIX } from "../config";

describe("POST /auth/anonymous 목 핸들러", () => {
  it("닉네임을 보내면 201 과 토큰을 data 로 감싸 내려준다", async () => {
    const response = await fetch(`${API_PREFIX}/auth/anonymous`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ nickname: "윤돌" }),
    });
    const body = await response.json();

    expect(response.status).toBe(201);
    expect(body.data.nickname).toBe("윤돌");
    expect(body.data.accessToken).toBeTruthy();
    expect(body.data.expiresAt).toBeTruthy();
  });
});
