import { useMutation } from "@tanstack/react-query";

import { updateRoom } from "../api/updateRoom";
import type { UpdateRoomRequest } from "../api/types";

interface UseUpdateRoomMutationParams {
  roomId: number;
  accessToken: string;
}

export const useUpdateRoomMutation = ({ roomId, accessToken }: UseUpdateRoomMutationParams) =>
  useMutation({
    mutationFn: (request: UpdateRoomRequest) => updateRoom({ roomId, accessToken, request }),
  });
