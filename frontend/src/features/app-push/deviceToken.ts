import { apiClient } from "@/shared/api";

interface DeviceToken {
  token: string;
  platform: "ios" | "android";
}

export const isDeviceToken = (value: unknown): value is DeviceToken =>
  value !== null &&
  typeof value === "object" &&
  "token" in value &&
  typeof value.token === "string" &&
  value.token.length > 0 &&
  "platform" in value &&
  (value.platform === "ios" || value.platform === "android");

const STORAGE_KEY = "sssok:push-device-token";
let pending = Promise.resolve();
let endingAccessToken: string | undefined;

const enqueue = (run: () => Promise<void>) => {
  const result = pending.then(run);
  pending = result.catch(() => {});
  return result;
};

const readDevice = () => {
  const value: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null");
  return isDeviceToken(value) ? value : null;
};

const updateToken = (method: "POST" | "DELETE", accessToken: string, body: object) =>
  apiClient<void>("/device-tokens", {
    method,
    token: accessToken,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    responseType: "empty",
    signal: AbortSignal.timeout(8000),
  });

export const registerDeviceToken = (accessToken: string, device: DeviceToken) =>
  enqueue(async () => {
    if (accessToken === endingAccessToken) return;
    const previous = readDevice();
    if (previous && previous.token !== device.token) {
      await updateToken("DELETE", accessToken, { token: previous.token });
    }
    // 요청이 서버에 도달했지만 응답이 끊겼어도 로그아웃에서 해제할 수 있게 먼저 기록한다.
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ token: device.token, platform: device.platform }),
    );
    await updateToken("POST", accessToken, { token: device.token, platform: device.platform });
  });

export const endPushSession = (accessToken: string, endSession: () => Promise<void>) =>
  enqueue(async () => {
    const device = readDevice();
    endingAccessToken = accessToken;
    try {
      if (device) await updateToken("DELETE", accessToken, { token: device.token });
      await endSession();
    } catch (error) {
      endingAccessToken = undefined;
      if (device) await updateToken("POST", accessToken, device).catch(() => {});
      throw error;
    }
    localStorage.removeItem(STORAGE_KEY);
  });
