import { apiClient } from "@/shared/api";

interface DeleteMediaParams {
  roomId: number;
  mediaId: number;
  token: string;
}

export const deleteMedia = ({ roomId, mediaId, token }: DeleteMediaParams) =>
  apiClient<void>(`/rooms/${roomId}/media/${mediaId}`, {
    method: "DELETE",
    token,
    responseType: "empty",
  });
