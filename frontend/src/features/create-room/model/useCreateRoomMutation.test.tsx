import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";

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
  expiryHours: "72",
} satisfies CreateRoomFormValues;

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
    },
  });

  return function QueryClientTestWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
};

describe("useCreateRoomMutation", () => {
  afterEach(() => {
    localStorage.clear();
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
        expiresAt: "2026-09-17T05:30:00Z",
      });
    });
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
