import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";

import { MEMBER_SESSION_QUERY_KEY, type MemberSession } from "@/entities/member-session";
import { getRoomSession } from "@/entities/session";
import { server } from "@/mocks/server";
import { ApiError } from "@/shared/api";
import { API_BASE_URL } from "@/shared/config";
import { useCreateRoomMutation } from "./useCreateRoomMutation";
import { CreateRoomFormValues } from "./createRoomForm";

const formValues = {
  nickname: "민수",
  name: "제주 여행",
  uploadPolicy: "host",
} satisfies CreateRoomFormValues;

const createWrapper = (member?: MemberSession) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
    },
  });

  if (member) queryClient.setQueryData(MEMBER_SESSION_QUERY_KEY, member);

  return function QueryClientTestWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
};

describe("useCreateRoomMutation", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("회원은 익명 인증 없이 회원 토큰으로 생성하고 토큰을 로컬에 저장하지 않는다", async () => {
    const anonymous = jest.fn();
    const create = jest.fn();
    server.use(
      http.post(`${API_BASE_URL}/auth/anonymous`, () => {
        anonymous();
        return new HttpResponse(null, { status: 500 });
      }),
      http.post(`${API_BASE_URL}/rooms`, async ({ request }) => {
        create(request.headers.get("Authorization"), await request.json());
        return HttpResponse.json({ data: { code: "TESTROOM" } }, { status: 201 });
      }),
    );
    const member: MemberSession = {
      accessToken: "member-token",
      userId: 42,
      nickname: "회원",
      expiresAt: "2099-01-01T00:00:00Z",
      memberStatus: "REGISTERED",
      refreshExpiresAt: "2099-02-01T00:00:00Z",
    };
    const { result } = renderHook(() => useCreateRoomMutation(), {
      wrapper: createWrapper(member),
    });
    await act(async () => {
      await result.current.mutateAsync(formValues);
    });
    expect(anonymous).not.toHaveBeenCalled();
    expect(create).toHaveBeenCalledWith("Bearer member-token", {
      nickname: "민수",
      name: "제주 여행",
      uploadPolicy: "host",
    });
    expect(getRoomSession("TESTROOM")).toBeNull();
  });

  it("회원 세션 확인 실패 시 익명 인증으로 전환하지 않는다", async () => {
    const anonymous = jest.fn();
    server.use(
      http.post(`${API_BASE_URL}/auth/refresh`, () => new HttpResponse(null, { status: 500 })),
      http.post(`${API_BASE_URL}/auth/anonymous`, () => {
        anonymous();
        return new HttpResponse(null, { status: 500 });
      }),
    );
    const { result } = renderHook(() => useCreateRoomMutation(), { wrapper: createWrapper() });
    await act(async () => {
      await expect(result.current.mutateAsync(formValues)).rejects.toThrow();
    });
    expect(anonymous).not.toHaveBeenCalled();
  });

  it("익명 인증 후 발급받은 token으로 방을 생성한다", async () => {
    const { result } = renderHook(() => useCreateRoomMutation(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      const room = await result.current.mutateAsync(formValues);

      expect(room).toEqual(
        expect.objectContaining({
          code: "7K93QX2S",
          name: "제주 여행",
          uploadPolicy: "host",
        }),
      );
      expect(getRoomSession(room.code)).toEqual({
        accessToken: "mock-token-10234",
        userId: 10234,
        nickname: "민수",
        expiresAt: "9999-12-31T23:59:59Z",
      });
    });
  });

  it("방 생성 요청에 토큰과 입력한 방 닉네임을 함께 전달한다", async () => {
    let received = false;
    server.use(
      http.post(`${API_BASE_URL}/rooms`, async ({ request }) => {
        received = true;
        expect(request.headers.get("Authorization")).toBe("Bearer mock-token-10234");
        expect(await request.json()).toEqual({
          name: formValues.name,
          uploadPolicy: formValues.uploadPolicy,
          nickname: formValues.nickname,
        });
        return HttpResponse.json({ data: { code: "TESTROOM" } }, { status: 201 });
      }),
    );
    const { result } = renderHook(() => useCreateRoomMutation(), { wrapper: createWrapper() });
    await act(async () => {
      await result.current.mutateAsync(formValues);
    });
    expect(received).toBe(true);
  });

  it("익명 인증이 실패하면 ApiError를 반환한다", async () => {
    server.use(
      http.post(`${API_BASE_URL}/auth/anonymous`, () => {
        return HttpResponse.json(
          {
            code: "ANONYMOUS_AUTH_FAILED",
            message: "익명 인증에 실패했습니다.",
          },
          { status: 500 },
        );
      }),
    );

    const { result } = renderHook(() => useCreateRoomMutation(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await expect(result.current.mutateAsync(formValues)).rejects.toEqual(
        expect.objectContaining<Partial<ApiError>>({
          status: 500,
          code: "ANONYMOUS_AUTH_FAILED",
          message: "익명 인증에 실패했습니다.",
        }),
      );
    });
  });
});
