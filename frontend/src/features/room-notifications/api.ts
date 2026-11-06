import { apiClient } from "@/shared/api";

interface NotificationSetting {
  enabled: boolean;
}

const settingPath = (roomId: number) => `/rooms/${roomId}/notification-setting`;

export const getNotificationSetting = (roomId: number, token: string) =>
  apiClient<NotificationSetting>(settingPath(roomId), { token });

export const updateNotificationSetting = (roomId: number, token: string, enabled: boolean) =>
  apiClient<NotificationSetting>(settingPath(roomId), {
    method: "PUT",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled }),
  });

export const requestPhotos = (roomId: number, token: string) =>
  apiClient<void>(`/rooms/${roomId}/upload-requests`, {
    method: "POST",
    token,
    responseType: "empty",
  });
