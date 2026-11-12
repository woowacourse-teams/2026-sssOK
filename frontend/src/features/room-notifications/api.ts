import { z } from "zod";
export const NotificationSettingSchema = z.object({ enabled: z.boolean() });
import { apiClient } from "@/shared/api";

type NotificationSetting = z.infer<typeof NotificationSettingSchema>;

const settingPath = (roomId: number): string => `/rooms/${roomId}/notification-setting`;

export const getNotificationSetting = (
  roomId: number,
  token: string,
): Promise<NotificationSetting> =>
  apiClient<NotificationSetting>(settingPath(roomId), { schema: NotificationSettingSchema, token });

export const updateNotificationSetting = (
  roomId: number,
  token: string,
  enabled: boolean,
): Promise<NotificationSetting> =>
  apiClient<NotificationSetting>(settingPath(roomId), {
    schema: NotificationSettingSchema,
    method: "PUT",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ enabled }),
  });

export const requestPhotos = (roomId: number, token: string): Promise<void> =>
  apiClient<void>(`/rooms/${roomId}/upload-requests`, {
    method: "POST",
    token,
    responseType: "empty",
  });
