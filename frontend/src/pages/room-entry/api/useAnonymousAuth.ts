import { useMutation } from "@tanstack/react-query";

import { createAnonymous, saveRoomSession } from "@/entities/session";

/** 인증에 성공하면 이 방의 세션으로 저장한다. 방마다 새 member 라 다른 방과 섞이면 안 된다. */
export const useAnonymousAuth = (roomCode: string, onSuccess: () => void) =>
  useMutation({
    mutationFn: (nickname: string) => createAnonymous({ nickname }),
    onSuccess: (session) => {
      saveRoomSession(roomCode, session);
      onSuccess();
    },
  });
