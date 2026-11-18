import { http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";

/**
 * 방 알림 설정·사진 요청 목.
 * 목이 없으면 요청이 실제 dev 서버로 가서 목 토큰이 401 을 받고, 인증 복구가 방에서 홈으로 내보낸다.
 */
const settings = new Map<number, boolean>();

export const roomNotificationHandlers = [
  http.get(`${API_BASE_URL}/rooms/:roomId/notification-setting`, ({ params }) =>
    HttpResponse.json({ data: { enabled: settings.get(Number(params.roomId)) ?? false } }),
  ),
  http.put(`${API_BASE_URL}/rooms/:roomId/notification-setting`, async ({ params, request }) => {
    const { enabled } = (await request.json()) as { enabled: boolean };
    settings.set(Number(params.roomId), enabled);
    return HttpResponse.json({ data: { enabled } });
  }),
  http.post(
    `${API_BASE_URL}/rooms/:roomId/upload-requests`,
    () => new HttpResponse(null, { status: 204 }),
  ),
];
