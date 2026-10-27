import { useMutation, useQueryClient } from "@tanstack/react-query";

import { myRoomsQueryKey, type MyRoom } from "@/entities/room";
import { leaveRoom } from "../api/leaveRoom";

interface UseLeaveRoomMutationParams {
  roomId: number;
  accessToken?: string;
  onSuccess: () => void;
}

export const useLeaveRoomMutation = ({
  roomId,
  accessToken,
  onSuccess,
}: UseLeaveRoomMutationParams) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => leaveRoom({ roomId, accessToken }),
    onSuccess: () => {
      // 다시 받아 오기 전에 나간 방을 먼저 화면에서 뺀다
      queryClient.setQueryData<MyRoom[]>(myRoomsQueryKey, (rooms) =>
        rooms?.filter((room) => room.roomId !== roomId),
      );
      void queryClient.invalidateQueries({ queryKey: myRoomsQueryKey });
      onSuccess();
    },
  });
};
