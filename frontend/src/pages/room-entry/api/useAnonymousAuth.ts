import { useMutation } from "@tanstack/react-query";

import { tokenStorage } from "@/shared/api";
import { postAnonymousAuth } from "./postAnonymousAuth";

/** 인증에 성공하면 이 방에 들어왔다고 기록한다. 놓치면 다음 방문에 다른 사용자가 된다. */
export const useAnonymousAuth = (roomCode: string, onSuccess: () => void) =>
  useMutation({
    mutationFn: postAnonymousAuth,
    onSuccess: ({ accessToken, userId, nickname, expiresAt }) => {
      tokenStorage.save(roomCode, { accessToken, userId, nickname, expiresAt });
      onSuccess();
    },
  });
