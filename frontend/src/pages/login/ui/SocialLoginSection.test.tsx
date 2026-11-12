import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, renderHook, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";

import { startAppleLogin } from "../api/startAppleLogin";
import { startKakaoLogin } from "../api/startKakaoLogin";
import { loginNative } from "../model/loginNative";
import { supportsNativeLogin } from "../model/nativeAuthBridge";
import { useSocialLogin } from "../model/useSocialLogin";
import { SocialLoginSection } from "./SocialLoginSection";

jest.mock("../api/startAppleLogin", () => ({ startAppleLogin: jest.fn() }));
jest.mock("../api/startKakaoLogin", () => ({ startKakaoLogin: jest.fn() }));
jest.mock("../model/loginNative", () => ({ loginNative: jest.fn() }));
jest.mock("../model/nativeAuthBridge", () => ({ supportsNativeLogin: jest.fn() }));

const nativeLogin = jest.mocked(loginNative);
const supportsNative = jest.mocked(supportsNativeLogin);
const kakaoStart = jest.mocked(startKakaoLogin);
const appleStart = jest.mocked(startAppleLogin);

const createWrapper = () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };
};

beforeEach(() => {
  jest.resetAllMocks();
  supportsNative.mockReturnValue(true);
});

afterEach(() => {
  window.history.replaceState(null, "", window.location.pathname + window.location.search);
});

it.each([
  ["카카오 로그인", kakaoStart],
  ["Apple로 로그인", appleStart],
] as const)(
  "웹 %s 이동 중에는 로딩을 유지하고 캐시에서 돌아오면 재시도할 수 있다",
  async (label, start) => {
    supportsNative.mockReturnValue(false);
    // jsdom이 지원하는 해시 이동으로 assign 이후 상태를 실제 이동 없이 확인한다.
    start.mockResolvedValue("#oauth-redirect");
    render(<SocialLoginSection />, { wrapper: createWrapper() });

    fireEvent.click(screen.getByRole("button", { name: label }));
    await waitFor(() => expect(window.location.hash).toBe("#oauth-redirect"));
    expect(screen.getByRole("status")).toHaveTextContent("로그인 중이에요.");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();

    act(() => window.dispatchEvent(new PageTransitionEvent("pageshow", { persisted: false })));
    expect(screen.queryByRole("button")).not.toBeInTheDocument();

    act(() => window.dispatchEvent(new PageTransitionEvent("pageshow", { persisted: true })));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: label })).toBeEnabled();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: label }));
    await waitFor(() => expect(start).toHaveBeenCalledTimes(2));
    expect(screen.getByRole("status")).toHaveTextContent("로그인 중이에요.");
  },
);

it("인증을 기다리는 동안 버튼 대신 스피너를 표시하고 취소 시 버튼을 복원한다", async () => {
  let cancel!: (value: null) => void;
  nativeLogin.mockImplementationOnce(() => new Promise((resolve) => (cancel = resolve)));
  render(<SocialLoginSection />, { wrapper: createWrapper() });

  fireEvent.click(screen.getByRole("button", { name: "카카오 로그인" }));
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
  expect(screen.getByRole("status")).toHaveTextContent("로그인 중이에요.");
  expect(nativeLogin).toHaveBeenCalledTimes(1);

  await act(async () => cancel(null));
  expect(screen.getByRole("button", { name: "카카오 로그인" })).toBeEnabled();
  expect(screen.getByRole("button", { name: "Apple로 로그인" })).toBeEnabled();
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

it("카카오 실패 후 Apple로 전환하면 이전 오류를 지우고 Apple의 오류를 표시한다", async () => {
  nativeLogin.mockRejectedValueOnce(new Error("카카오 인증에 실패했어요."));
  let rejectApple!: (reason: Error) => void;
  nativeLogin.mockImplementationOnce(
    () => new Promise((_resolve, reject) => (rejectApple = reject)),
  );
  render(<SocialLoginSection />, { wrapper: createWrapper() });

  fireEvent.click(screen.getByRole("button", { name: "카카오 로그인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("카카오 인증에 실패했어요.");
  fireEvent.click(screen.getByRole("button", { name: "Apple로 로그인" }));
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
  expect(screen.getByRole("status")).toHaveTextContent("로그인 중이에요.");

  await act(async () => rejectApple(new Error("Apple 인증에 실패했어요.")));
  expect(screen.getByRole("alert")).toHaveTextContent("Apple 인증에 실패했어요.");
  expect(screen.getByRole("button", { name: "카카오 로그인" })).toBeEnabled();
});

it.each([
  ["카카오 로그인", kakaoStart],
  ["Apple로 로그인", appleStart],
] as const)("웹 %s 시작 실패 후 같은 버튼으로 재시도할 수 있다", async (label, start) => {
  supportsNative.mockReturnValue(false);
  start.mockRejectedValueOnce(new Error("서버에 연결하지 못했어요."));
  start.mockRejectedValueOnce(new Error("다시 시도해 주세요."));
  render(<SocialLoginSection />, { wrapper: createWrapper() });

  fireEvent.click(screen.getByRole("button", { name: label }));
  expect(await screen.findByRole("alert")).toHaveTextContent("서버에 연결하지 못했어요.");
  fireEvent.click(screen.getByRole("button", { name: label }));
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(await screen.findByRole("alert")).toHaveTextContent("다시 시도해 주세요.");
  expect(start).toHaveBeenCalledTimes(2);
  expect(nativeLogin).not.toHaveBeenCalled();
});

it("렌더 갱신 전에 서로 다른 제공자로 로그인해도 인증은 한 번만 시작한다", async () => {
  let cancel!: (value: null) => void;
  nativeLogin.mockImplementationOnce(() => new Promise((resolve) => (cancel = resolve)));
  const { result } = renderHook(() => useSocialLogin(), { wrapper: createWrapper() });
  act(() => {
    void result.current.login("kakao");
    void result.current.login("apple");
  });
  expect(nativeLogin).toHaveBeenCalledTimes(1);
  expect(nativeLogin).toHaveBeenCalledWith("kakao", expect.any(AbortSignal));
  await act(async () => cancel(null));
});

it("인증 도중 영역을 벗어나면 요청을 취소하고 뒤늦은 결과를 반영하지 않는다", async () => {
  let cancel!: (value: null) => void;
  nativeLogin.mockImplementationOnce(() => new Promise((resolve) => (cancel = resolve)));
  const { unmount } = render(<SocialLoginSection />, { wrapper: createWrapper() });
  fireEvent.click(screen.getByRole("button", { name: "카카오 로그인" }));
  const signal = nativeLogin.mock.calls[0][1];
  unmount();
  expect(signal.aborted).toBe(true);
  await act(async () => cancel(null));
});
