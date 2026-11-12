import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { http, HttpResponse } from "msw";

import { routes } from "@/app/routes";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";
import { ToastProvider } from "@/shared/ui/toast";

export const ROOM = "7K93QX2S";
export const GALLERY = ROUTES.gallery(ROOM);
export const STATE = "s".repeat(43);
export type Provider = "kakao" | "apple";
export const providers: Provider[] = ["kakao", "apple"];
export const label = (provider: Provider) =>
  provider === "kakao" ? "카카오 로그인" : "Apple로 로그인";
export const startPath = (provider: Provider) => `${API_BASE_URL}/auth/oauth/${provider}/start`;
export const completePath = (provider: Provider) => `${API_BASE_URL}/auth/oauth/${provider}/login`;
export const member = () => ({
  accessToken: "mock-token-10234",
  memberStatus: "REGISTERED",
  userId: 10234,
  nickname: "민수",
  expiresAt: "2099-01-01T00:00:00Z",
  refreshExpiresAt: "2099-01-02T00:00:00Z",
});
export const attempt = () => ({
  state: STATE,
  loginVerifier: "v".repeat(43),
  nonce: "n".repeat(43),
  expiresAt: "2099-01-01T00:00:00Z",
});
export const authorizationUrl = (provider: Provider) =>
  `${provider === "kakao" ? "https://kauth.kakao.com/oauth/authorize" : "https://appleid.apple.com/auth/authorize"}?state=${STATE}`;

/** 외부 이동만 차단한다. 시작 API·URL 검증·훅·컴포넌트는 실제 코드를 실행한다. */
export const interceptExternalNavigation = () => {
  // jsdom의 location.assign은 unforgeable이어서 공개 프로퍼티를 재정의할 수 없다.
  // jsdom 구현 객체의 assign만 대체해 브라우저 이동 경계를 관찰한다.
  const implementation = Object.getOwnPropertySymbols(window.location)
    .map((symbol) => Reflect.get(window.location, symbol) as { assign?: (url: string) => void })
    .find((value) => typeof value?.assign === "function");
  if (!implementation?.assign) throw new Error("jsdom Location의 이동 경계를 찾지 못했습니다.");
  return jest
    .spyOn(implementation as { assign: (url: string) => void }, "assign")
    .mockImplementation(() => undefined);
};

export const installApi = () => {
  const calls: string[] = [];
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => HttpResponse.json({}, { status: 401 })),
    ...providers.map((provider) =>
      http.post(startPath(provider), () => {
        calls.push(startPath(provider));
        return HttpResponse.json({
          data: { ...attempt(), authorizationUrl: authorizationUrl(provider) },
        });
      }),
    ),
    ...providers.map((provider) =>
      http.post(completePath(provider), () => {
        calls.push(completePath(provider));
        return HttpResponse.json({ data: member() });
      }),
    ),
    http.post(`${API_BASE_URL}/auth/oauth/apple/native/start`, () =>
      HttpResponse.json({ data: attempt() }),
    ),
    ...providers.map((provider) =>
      http.post(`${API_BASE_URL}/auth/oauth/${provider}/native/login`, () => {
        calls.push(`${provider}:native-login`);
        return HttpResponse.json({ data: member() });
      }),
    ),
    http.get(`${API_BASE_URL}/members/me/rooms`, () => HttpResponse.json({ data: { rooms: [] } })),
    http.get(`${API_BASE_URL}/rooms/5031/notification-setting`, () =>
      HttpResponse.json({ data: { enabled: false } }),
    ),
    http.get(`${API_BASE_URL}/rooms/${ROOM}`, () =>
      HttpResponse.json({
        data: {
          roomId: 5031,
          code: ROOM,
          name: "돌아갈 방",
          status: "ACTIVE",
          hostId: 10234,
          hostName: "민수",
          joined: true,
          myNickname: "민수",
          photoCount: 0,
          folders: [],
          uploadPolicy: "everyone",
          createdAt: "2026-01-01T00:00:00Z",
          expiresAt: "2099-01-01T00:00:00Z",
        },
      }),
    ),
  );
  return calls;
};

type NativeRequest = { type: string; provider: Provider; requestId: string; nonce?: string };
export const enableNative = () => {
  const requests: NativeRequest[] = [];
  window.sssokApp = { auth: ["kakao", "apple"] };
  window.ReactNativeWebView = {
    postMessage: (raw) => {
      const request = JSON.parse(raw) as NativeRequest;
      if (request.type === "auth/login") requests.push(request);
    },
  };
  return {
    requests,
    respond: async (status: "success" | "cancelled" | "error") => {
      const request = requests[requests.length - 1];
      if (!request) throw new Error("SDK 인증 요청이 없습니다.");
      await act(async () => {
        window.dispatchEvent(
          new CustomEvent("sssok:auth", {
            detail: { ...request, status, credential: "qa-sdk-credential" },
          }),
        );
      });
    },
  };
};

export const renderApp = (path: string = ROUTES.login) => {
  history.replaceState(null, "", path);
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const view = render(
    <StrictMode>
      <QueryClientProvider client={client}>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </QueryClientProvider>
    </StrictMode>,
  );
  return {
    client,
    router,
    user: userEvent.setup(),
    dispose: () => {
      view.unmount();
      client.clear();
      sessionStorage.clear();
      delete window.ReactNativeWebView;
      delete window.sssokApp;
    },
  };
};
export const openLogin = async () => {
  const app = renderApp();
  await screen.findByRole("button", { name: label("kakao") });
  return app;
};
export const deferred = () => {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

export const renderCallback = (
  provider: Provider = "kakao",
  query = `code=qa-code&state=${STATE}`,
) => renderApp(`/app/auth/callback/${provider}?${query}`);

export const resetLoginTestState = () => {
  cleanup();
  jest.useRealTimers();
  jest.restoreAllMocks();
  delete window.ReactNativeWebView;
  delete window.sssokApp;
  sessionStorage.clear();
  localStorage.clear();
  history.replaceState(null, "", "/");
};
