import { useMutation } from "@tanstack/react-query";

import { createAnonymous, removeRoomSession, saveRoomSession } from "@/entities/session";
import { joinRoom } from "./joinRoom";

/**
 * 이름을 받아 익명 인증 → 이 방 세션 저장 → 방 입장까지 한 번에 끝낸다.
 * 셋 중 하나라도 어긋나면 방에 못 들어간 것이므로, 저장한 세션을 도로 지워
 * 다음 방문 때 이름 화면부터 다시 밟게 한다.
 */
export const useAnonymousAuth = (roomCode: string, onSuccess: () => void) =>
  useMutation({
    mutationFn: async (nickname: string) => {
      const session = await createAnonymous({ nickname });

      saveRoomSession(roomCode, session);

      try {
        return await joinRoom(roomCode, session.accessToken);
      } catch (error) {
        removeRoomSession(roomCode);
        throw error;
      }
    },
    onSuccess,
  });
