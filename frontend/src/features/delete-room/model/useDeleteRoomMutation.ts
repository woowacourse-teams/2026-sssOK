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
}: UseDeleteRoomMutationParams) => {
  return useMutation({
    mutationFn: () => deleteRoom({ roomId, accessToken }),
    onSuccess,
  });
};
