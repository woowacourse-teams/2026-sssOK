import type { UseMutationResult } from "@tanstack/react-query";
import type { Room } from "@/entities/room/model/types";
import { useMutation } from "@tanstack/react-query";

import { updateRoom } from "../api/updateRoom";
import type { UpdateRoomRequest } from "../api/types";

interface UseUpdateRoomMutationParams {
  roomId: number;
  accessToken: string;
}

export const useUpdateRoomMutation = ({
  roomId,
  accessToken,
}: UseUpdateRoomMutationParams): UseMutationResult<Room, Error, UpdateRoomRequest, unknown> =>
  useMutation({
    mutationFn: (request: UpdateRoomRequest) => updateRoom({ roomId, accessToken, request }),
  });
