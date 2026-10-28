import { useMutation, useQueryClient } from "@tanstack/react-query";

import { memberSessionQueryOptions } from "@/entities/member-session";
import { createAnonymous, saveRoomSession } from "@/entities/session";
import { joinRoom } from "./joinRoom";

interface EnterRoomVariables {
  nickname: string;
  roomId: number;
}

/** 회원은 기존 토큰으로 입장하고, 비회원만 익명 인증한다. */
export const useJoinRoomMutation = (roomCode: string, onSuccess: () => void) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ nickname, roomId }: EnterRoomVariables) => {
      const member = await queryClient.fetchQuery(memberSessionQueryOptions());
      const session = member ?? (await createAnonymous({ nickname }));
      const result = await joinRoom(roomId, session.accessToken, { code: roomCode, nickname });
      if (!member) saveRoomSession(roomCode, session);
      await queryClient.invalidateQueries({ queryKey: ["room", roomCode] });
      void queryClient.invalidateQueries({ queryKey: ["myRooms"] });
      return result;
    },
    onSuccess,
  });
};
