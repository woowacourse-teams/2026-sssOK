import { useMutation, useQueryClient, type UseMutationResult } from "@tanstack/react-query";
import { useEffect } from "react";

import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { myRoomsQueryKey } from "@/entities/room";
import { removeRoomSession } from "@/entities/session";
import { ROUTES } from "@/shared/config";
import { takeLoginReturn } from "@/shared/lib";
import { captureLoginCallback, completeLogin } from "../api/completeLogin";

type CompleteLoginResult = UseMutationResult<string | null, Error, AbortSignal> & {
  isLoading: boolean;
  returnTo: string;
};

export const useCompleteLogin = (): CompleteLoginResult => {
  const client = useQueryClient();
  const mutation = useMutation({
    retry: false,
    mutationFn: async (signal: AbortSignal) => {
      const session = await completeLogin();
      signal.throwIfAborted();
      await client.cancelQueries({ queryKey: MEMBER_SESSION_QUERY_KEY });
      signal.throwIfAborted();
      client.setQueryData(MEMBER_SESSION_QUERY_KEY, session);
      client.removeQueries({ queryKey: myRoomsQueryKey });
      try {
        Object.keys(localStorage)
          .filter((key) => key.startsWith("sssok.auth:"))
          .forEach((key) => removeRoomSession(key.slice("sssok.auth:".length)));
      } catch {
        // 저장소가 차단돼도 서버에서 완료된 회원 로그인을 실패로 되돌리지 않는다.
      }
      return takeLoginReturn();
    },
  });
  const { mutate } = mutation;

  useEffect(() => {
    const controller = new AbortController();
    captureLoginCallback();
    // StrictMode에서 정리된 Effect는 실행하지 않도록 시작 예약도 함께 취소한다.
    const timer = window.setTimeout(() => mutate(controller.signal), 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [mutate]);

  return {
    ...mutation,
    isLoading: mutation.isIdle || mutation.isPending,
    returnTo: mutation.data ?? ROUTES.rooms,
  };
};
