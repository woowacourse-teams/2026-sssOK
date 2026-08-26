import { apiClient } from "@/shared/api";
import type { RegisterMediaRequest, RegisterMediaResponse } from "./types";

/**
 * 완료 등록. PUT 이 끝난 미디어를 방 목록에 노출시킨다.
 *
 * 미디어 단위 실패는 응답의 `failed` 로 갈라져 온다. 그중 `UPLOAD_ALREADY_COMPLETED` 는
 * 실패가 아니라 이미 올라간 것이므로, 부르는 쪽이 성공으로 합쳐야 한다.
 */
export const registerMedia = (roomId: number, request: RegisterMediaRequest, token: string) => {
  return apiClient<RegisterMediaResponse>(`/rooms/${roomId}/media`, {
    method: "POST",
    token,
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
};
