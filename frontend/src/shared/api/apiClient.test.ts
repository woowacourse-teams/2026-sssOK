import { http, HttpResponse } from "msw";

import { API_PREFIX } from "@/mocks/config";
import { server } from "@/mocks/server";
import { ApiError } from "./ApiError";
import { apiClient } from "./apiClient";

describe("apiClient", () => {
  it("응답 JSON을 반환한다", async () => {
    server.use(
      http.get(`${API_PREFIX}/api-test`, () => {
        return HttpResponse.json({ name: "제주 여행" });
      }),
    );

    await expect(apiClient("/api-test")).resolves.toEqual({ name: "제주 여행" });
  });

  it("전달받은 token을 Authorization 헤더에 추가한다", async () => {
    server.use(
      http.get(`${API_PREFIX}/api-test`, ({ request }) => {
        return HttpResponse.json({ authorization: request.headers.get("Authorization") });
      }),
    );

    await expect(apiClient("/api-test", { token: "test-token" })).resolves.toEqual({
      authorization: "Bearer test-token",
    });
  });

  it("실패 응답을 ApiError로 변환한다", async () => {
    server.use(
      http.get(`${API_PREFIX}/api-test`, () => {
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
