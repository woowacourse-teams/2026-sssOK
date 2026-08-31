import { http, HttpResponse } from "msw";

import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { ApiError } from "./ApiError";
import { apiClient } from "./apiClient";

describe("apiClient", () => {
  it.each([{ data: null }, {}, { success: true }])(
    "삭제 성공 응답에 JSON 본문이 있어도 성공 처리한다: %j",
    async (body) => {
      server.use(http.delete(`${API_BASE_URL}/api-test`, () => HttpResponse.json(body)));
      await expect(
        apiClient<void>("/api-test", { method: "DELETE", responseType: "empty" }),
      ).resolves.toBeUndefined();
    },
  );

  it("삭제의 403 응답은 계속 실패로 처리한다", async () => {
    server.use(
      http.delete(`${API_BASE_URL}/api-test`, () =>
        HttpResponse.json(
          { code: "MEDIA_FORBIDDEN", message: "삭제 권한이 없어요." },
          { status: 403 },
        ),
      ),
    );
    await expect(
      apiClient<void>("/api-test", { method: "DELETE", responseType: "empty" }),
    ).rejects.toMatchObject({ status: 403, code: "MEDIA_FORBIDDEN" });
  });

  it.each([200, 204])("본문 없는 %s 삭제 응답을 처리한다", async (status) => {
    server.use(http.delete(`${API_BASE_URL}/api-test`, () => new HttpResponse(null, { status })));
    await expect(
      apiClient<void>("/api-test", { method: "DELETE", responseType: "empty" }),
    ).resolves.toBeUndefined();
  });

  it("빈 응답을 기대한 삭제 요청에 HTML이 오면 성공으로 처리하지 않는다", async () => {
    server.use(
      http.delete(`${API_BASE_URL}/api-test`, () =>
        HttpResponse.html("<!doctype html><html></html>"),
      ),
    );
    await expect(
      apiClient<void>("/api-test", { method: "DELETE", responseType: "empty" }),
    ).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  });

  it("성공 응답에서 data 만 꺼내 돌려준다", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api-test`, () => {
        return HttpResponse.json({ data: { name: "제주 여행" } });
      }),
    );

    await expect(apiClient("/api-test")).resolves.toEqual({ name: "제주 여행" });
  });

  it("200 이지만 우리 형식이 아닌 응답은 ApiError 로 던진다", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api-test`, () => HttpResponse.html("<!doctype html><html></html>")),
    );

    await expect(apiClient("/api-test")).rejects.toMatchObject({
      status: 200,
      code: "INVALID_RESPONSE",
    });
  });

  it("전달받은 token을 Authorization 헤더에 추가한다", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api-test`, ({ request }) => {
        return HttpResponse.json({ data: { authorization: request.headers.get("Authorization") } });
      }),
    );

    await expect(apiClient("/api-test", { token: "test-token" })).resolves.toEqual({
      authorization: "Bearer test-token",
    });
  });

  it("실패 응답을 ApiError로 변환한다", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api-test`, () => {
        return HttpResponse.json(
          {
            code: "ROOM_CREATE_FAILED",
            message: "방을 만들 수 없습니다.",
          },
          { status: 500 },
        );
      }),
    );

    await expect(apiClient("/api-test")).rejects.toEqual(
      expect.objectContaining<Partial<ApiError>>({
        name: "ApiError",
        status: 500,
        code: "ROOM_CREATE_FAILED",
        message: "방을 만들 수 없습니다.",
      }),
    );
  });
});
