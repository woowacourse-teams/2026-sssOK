import { http, HttpResponse } from "msw";

import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { ApiError } from "./ApiError";
import { apiClient } from "./apiClient";
import { setUnauthorizedHandler } from "./unauthorized";

const unauthorizedResponse = () =>
  HttpResponse.json({ code: "UNAUTHORIZED", message: "다시 접속해주세요" }, { status: 401 });

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

  /*
   * 401 은 화면이 아니라 여기서 알아본다 (#149). 인증이 깨진 요청은 어느 화면에서 나갔든
   * 같은 뒷정리가 필요해서, 화면마다 알아보게 두면 하나씩 빠뜨린다.
   */
  describe("401 알림", () => {
    let dispose: (() => void) | null = null;

    const listen = () => {
      const handler = jest.fn();

      dispose = setUnauthorizedHandler(handler);

      return handler;
    };

    // 등록은 모듈에 남는다. 지우지 않으면 다음 테스트의 요청까지 이 핸들러가 받는다.
    afterEach(() => {
      dispose?.();
      dispose = null;
    });

    it("401 을 만나면 요청에 실었던 토큰을 알린다", async () => {
      const onUnauthorized = listen();
      server.use(http.get(`${API_BASE_URL}/api-test`, unauthorizedResponse));

      await expect(apiClient("/api-test", { token: "dead-token" })).rejects.toMatchObject({
        status: 401,
      });

      expect(onUnauthorized).toHaveBeenCalledWith("dead-token");
    });

    it("401 이라도 ApiError 는 그대로 던진다 — 알림은 뒷정리일 뿐이다", async () => {
      listen();
      server.use(http.get(`${API_BASE_URL}/api-test`, unauthorizedResponse));

      await expect(apiClient("/api-test", { token: "dead-token" })).rejects.toMatchObject({
        status: 401,
        code: "UNAUTHORIZED",
        message: "다시 접속해주세요",
      });
    });

    // 토큰 없이 부른 요청의 401 은 "로그인이 필요하다" 는 뜻이라 지울 세션이 없다.
    it("토큰 없이 받은 401 은 알리지 않는다", async () => {
      const onUnauthorized = listen();
      server.use(http.get(`${API_BASE_URL}/api-test`, unauthorizedResponse));

      await expect(apiClient("/api-test")).rejects.toMatchObject({ status: 401 });

      expect(onUnauthorized).not.toHaveBeenCalled();
    });

    // 403 은 인증이 아니라 인가 문제다. 세션은 멀쩡하므로 지우면 안 된다 (#148).
    it("403 은 알리지 않는다", async () => {
      const onUnauthorized = listen();
      server.use(
        http.get(`${API_BASE_URL}/api-test`, () =>
          HttpResponse.json(
            { code: "UPLOAD_NOT_ALLOWED", message: "방장만 업로드할 수 있는 방입니다" },
            { status: 403 },
          ),
        ),
      );

      await expect(apiClient("/api-test", { token: "live-token" })).rejects.toMatchObject({
        status: 403,
      });

      expect(onUnauthorized).not.toHaveBeenCalled();
    });
  });
});
