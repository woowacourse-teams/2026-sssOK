import { http, HttpResponse } from "msw";

import { API_PREFIX } from "@/mocks/config";
import { server } from "@/mocks/server";
import { ApiError } from "./ApiError";
import { apiClient } from "./apiClient";

describe("apiClient", () => {
  it("성공 응답에서 data 만 꺼내 돌려준다", async () => {
    server.use(
      http.get(`${API_PREFIX}/ping`, () => HttpResponse.json({ data: { pong: true } })),
    );

    await expect(apiClient("/ping")).resolves.toEqual({ pong: true });
  });

  it("토큰을 넘기면 Authorization 헤더를 붙인다", async () => {
    let authorization: string | null = null;
    server.use(
      http.get(`${API_PREFIX}/ping`, ({ request }) => {
        authorization = request.headers.get("Authorization");
        return HttpResponse.json({ data: null });
      }),
    );

    await apiClient("/ping", { token: "my-token" });

    expect(authorization).toBe("Bearer my-token");
  });

  it("토큰을 넘기지 않으면 Authorization 헤더를 붙이지 않는다", async () => {
    let authorization: string | null = "아직 확인 전";
    server.use(
      http.get(`${API_PREFIX}/ping`, ({ request }) => {
        authorization = request.headers.get("Authorization");
        return HttpResponse.json({ data: null });
      }),
    );

    await apiClient("/ping");

    expect(authorization).toBeNull();
  });

  it("실패 응답은 status 와 code 를 담은 ApiError 로 던진다", async () => {
    server.use(
      http.get(`${API_PREFIX}/ping`, () =>
        HttpResponse.json({ code: "ROOM_NOT_FOUND", message: "없는 방" }, { status: 404 }),
      ),
    );

    await expect(apiClient("/ping")).rejects.toMatchObject({
      status: 404,
      code: "ROOM_NOT_FOUND",
      message: "없는 방",
    });
    await expect(apiClient("/ping")).rejects.toBeInstanceOf(ApiError);
  });

  it("200 이지만 우리 형식이 아닌 응답도 ApiError 로 던진다", async () => {
    server.use(
      http.get(`${API_PREFIX}/ping`, () => HttpResponse.html("<!doctype html><html></html>")),
    );

    await expect(apiClient("/ping")).rejects.toMatchObject({
      status: 200,
      code: "INVALID_RESPONSE",
    });
  });
});
