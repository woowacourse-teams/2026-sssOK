import { http, HttpResponse } from "msw";

import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { createAnonymous } from "./createAnonymous";
import { loginWithLinkCode } from "./loginWithLinkCode";

it.each([
  {
    path: "/auth/anonymous",
    payload: { nickname: "민수" },
    login: () => createAnonymous({ nickname: "민수" }),
  },
  { path: "/auth/link", payload: { linkCode: "123456" }, login: () => loginWithLinkCode("123456") },
])("$path 요청은 브라우저 쿠키 인증 계약을 따른다", async ({ path, payload, login }) => {
  let received = false;
  server.use(
    http.post(`${API_BASE_URL}${path}`, async ({ request }) => {
      received = true;
      expect(request.headers.get("X-CSRF-Protection")).toBe("1");
      expect(request.credentials).toBe("include");
      expect(await request.json()).toEqual({ ...payload, refreshTokenDelivery: "COOKIE" });
      return HttpResponse.json({ data: { accessToken: "token" } });
    }),
  );

  await expect(login()).resolves.toEqual({ accessToken: "token" });
  expect(received).toBe(true);
});
