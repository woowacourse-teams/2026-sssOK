import { useEffect, useState } from "react";

import { useMemberSessionQuery } from "@/entities/member-session";
import { ROUTES } from "@/shared/config";
import { PhotoRequestBanner } from "./PhotoRequestBanner";
import { isDeviceToken, registerDeviceToken } from "./deviceToken";

const NATIVE_PUSH_EVENT = "sssok:push";

export const AppPushNotifications = () => {
  const { data: session } = useMemberSessionQuery();
  const [request, setRequest] = useState<{ code: string; message: string; userId: number } | null>(
    null,
  );
  const accessToken = session?.accessToken;
  const userId = session?.userId;

  useEffect(() => {
    if (!accessToken || userId === undefined) return;

    const handle = (event: Event) => {
      const detail: unknown = (event as CustomEvent<unknown>).detail;
      if (!detail || typeof detail !== "object" || !("type" in detail)) return;

      if (detail.type === "token" && isDeviceToken(detail)) {
        void registerDeviceToken(accessToken, detail).catch(() => {});
        return;
      }
      if (
        detail.type === "upload-requested" &&
        "roomCode" in detail &&
        typeof detail.roomCode === "string" &&
        /^[a-zA-Z0-9]{1,32}$/.test(detail.roomCode)
      ) {
        const message =
          "message" in detail && typeof detail.message === "string"
            ? detail.message
            : "방에 사진을 올려 달라는 요청이 왔어요.";
        setRequest({ code: detail.roomCode, message, userId });
      }
    };

    const requestToken = () =>
      window.ReactNativeWebView?.postMessage(JSON.stringify({ type: "push/request-token" }));
    const onVisibilityChange = () => {
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

  useEffect(() => {
    if (!request) return;
    const timer = window.setTimeout(() => setRequest(null), 6500);
    return () => window.clearTimeout(timer);
  }, [request]);

  if (!request || request.userId !== userId) return null;

  const path = ROUTES.gallery(request.code);
  return (
    <PhotoRequestBanner
      message={request.message}
      onClose={() => setRequest(null)}
      roomPath={path}
    />
  );
};
