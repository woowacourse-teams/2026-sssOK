import type { UseMutationResult } from "@tanstack/react-query";
import type { DeleteRoomResponse } from "@/features/delete-room/api/deleteRoom";
import { useMutation } from "@tanstack/react-query";

import { deleteRoom } from "../api/deleteRoom";

interface UseDeleteRoomMutationParams {
  roomId: number;
  accessToken: string;
  onSuccess: () => void;
}

export const useDeleteRoomMutation = ({
  roomId,
  accessToken,
  onSuccess,
}: UseDeleteRoomMutationParams): UseMutationResult<DeleteRoomResponse, Error, void, unknown> => {
  return useMutation({
    mutationFn: () => deleteRoom({ roomId, accessToken }),
    onSuccess,
  });
};
