import { useEffect } from "react";

import { useMemberSessionQuery } from "@/entities/member-session";
import { isDeviceToken, registerDeviceToken } from "./deviceToken";

const NATIVE_PUSH_EVENT = "sssok:push";

export const AppPushNotifications = () => {
  const { data: session } = useMemberSessionQuery();
  const accessToken = session?.accessToken;
  const userId = session?.userId;

  useEffect(() => {
    if (!accessToken || userId === undefined) return;

    const handle = (event: Event): void => {
      const detail: unknown = (event as CustomEvent<unknown>).detail;
      if (!detail || typeof detail !== "object" || !("type" in detail)) return;

      if (detail.type === "token" && isDeviceToken(detail)) {
        void registerDeviceToken(accessToken, detail).catch(() => {});
        return;
      }
    };

    const requestToken = (): void | undefined =>
      window.ReactNativeWebView?.postMessage(JSON.stringify({ type: "push/request-token" }));
    const onVisibilityChange = (): void => {
      if (document.visibilityState === "visible") requestToken();
    };
    window.addEventListener(NATIVE_PUSH_EVENT, handle);
    document.addEventListener("visibilitychange", onVisibilityChange);
    requestToken();
    return () => {
      window.removeEventListener(NATIVE_PUSH_EVENT, handle);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [accessToken, userId]);

  return null;
};
