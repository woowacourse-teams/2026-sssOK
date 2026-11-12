import { z } from "zod";
export const DeleteRoomResponseSchema = z.object({
  code: z.string(),
  status: z.literal("DELETED"),
  deletedAt: z.string(),
  purgeAt: z.string(),
});
import { apiClient } from "@/shared/api";

interface DeleteRoomParams {
  roomId: number;
  accessToken: string;
}

export type DeleteRoomResponse = z.infer<typeof DeleteRoomResponseSchema>;

export const deleteRoom = ({
  roomId,
  accessToken,
}: DeleteRoomParams): Promise<DeleteRoomResponse> =>
  apiClient<DeleteRoomResponse>(`/rooms/${roomId}`, {
    schema: DeleteRoomResponseSchema,
    method: "DELETE",
    token: accessToken,
  });
