import { useMutation } from "@tanstack/react-query";

import { tokenStorage } from "@/shared/api";
import { postAnonymousAuth } from "./postAnonymousAuth";

/** 인증에 성공하면 이 방의 토큰으로 저장한다. 방마다 새 member 라 다른 방과 섞이면 안 된다. */
export const useAnonymousAuth = (roomCode: string, onSuccess: () => void) =>
  useMutation({
    mutationFn: postAnonymousAuth,
    onSuccess: ({ accessToken, userId, nickname, expiresAt }) => {
      tokenStorage.set(roomCode, { accessToken, userId, nickname, expiresAt });
      onSuccess();
    },
  });
